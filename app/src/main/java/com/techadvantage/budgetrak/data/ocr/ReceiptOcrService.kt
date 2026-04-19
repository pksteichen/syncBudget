package com.techadvantage.budgetrak.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.techadvantage.budgetrak.BuildConfig
import com.techadvantage.budgetrak.data.Category
import com.techadvantage.budgetrak.data.sync.ReceiptManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * 2-call Gemini 2.5 Flash-Lite receipt OCR pipeline (V10, shipped 2026-04-19).
 *
 *   Call 1 (header + per-item scoring):
 *     merchant, date, amountCents +
 *     items[{description, scores[{categoryId, score, reason}]}] +
 *     multiCategoryLikely + topChoice.
 *   Call 3 (prices): priceCents per item, using multiplier/coupon cues.
 *
 * Single-cat receipts short-circuit after Call 1 (1 API call).
 * Multi-cat receipts run Call 1 + Call 3 (2 API calls). No Call 2.
 *
 * Category-agnostic: the prompt never names any category by id or name
 * except referring to "Other" as a concept. "Other" is resolved at runtime
 * via tag == "other", not by a hard-coded id — works for any user's list.
 *
 * Reference implementation: tools/ocr-harness/scripts/validate-v10-2call.js.
 * Validation on a 33-receipt subset: combined 25, 20/28 singles correct,
 * 5/5 multi routed, 3/5 multi cset, 3/3 Amazon receipts correct.
 */
object ReceiptOcrService {
    private const val TAG = "ReceiptOcrService"
    private const val TIMEOUT_MS = 90_000L

    // ── Schemas ────────────────────────────────────────────────────

    private val call1Schema = Schema.obj(
        name = "Call1Result",
        description = "Receipt header, per-item category scoring, routing",
        Schema.str("merchant", "Consumer brand on the receipt header"),
        Schema.str("merchantLegalName", "Optional legal operator entity"),
        Schema.str("date", "Transaction date in YYYY-MM-DD"),
        Schema.int("amountCents", "Final total paid, integer cents"),
        Schema.arr(
            "items",
            "Every purchased line; skip promos/discounts/subtotals; include Sales Tax as a line",
            Schema.obj(
                name = "ItemWithScores",
                description = "A line item with up to 3 scored category candidates",
                Schema.str("description", "Item text as printed on the receipt"),
                Schema.arr(
                    "scores",
                    "Up to 3 best-fit categories, descending by score",
                    Schema.obj(
                        name = "CatScore",
                        description = "A category candidate with a 0-100 match score",
                        Schema.int("categoryId", "Category id from the provided list"),
                        Schema.int("score", "Match strength 0-100"),
                        Schema.str("reason", "Brief (≤15 words) justification")
                    )
                )
            )
        ),
        Schema.bool("multiCategoryLikely", "True when items' top domains differ"),
        Schema.int("topChoice", "Best-fit category id for whole receipt when multiCategoryLikely is false"),
        Schema.str("notes", "Optional free-form note")
    )

    private val call3Schema = Schema.obj(
        name = "Call3Result",
        description = "Prices per line item",
        Schema.arr(
            "prices",
            "Parallel array to the input item list; priceCents per item",
            Schema.obj(
                name = "ItemPrice",
                description = "Price in integer cents",
                Schema.str("description", "Item text (mirrors input)"),
                Schema.int("priceCents", "Paid price in cents after line discounts")
            )
        )
    )

    // ── Models ─────────────────────────────────────────────────────

    private val call1Model by lazy { liteModel(call1Schema) }
    private val call3Model by lazy { liteModel(call3Schema) }

    private fun liteModel(schema: Schema<*>) = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
            temperature = 0f
        }
    )

    // ── Public API ─────────────────────────────────────────────────

    suspend fun extractFromReceipt(
        context: Context,
        receiptId: String,
        categories: List<Category>,
        preSelectedCategoryIds: Set<Int> = emptySet()
    ): Result<OcrResult> {
        return try {
            if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                return Result.failure(IllegalStateException("GEMINI_API_KEY missing from local.properties"))
            }
            val file = ReceiptManager.getReceiptFile(context, receiptId)
            if (!file.exists()) {
                return Result.failure(IllegalStateException("Receipt file not found: $receiptId"))
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return Result.failure(IllegalStateException("Could not decode receipt image"))

            val result = withTimeout(TIMEOUT_MS) {
                runPipeline(bitmap, categories, preSelectedCategoryIds)
            }
            bitmap.recycle()
            Result.success(result)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed for receipt $receiptId: ${e.message}")
            runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            Result.failure(e)
        }
    }

    // ── Pipeline ──────────────────────────────────────────────────

    internal data class ScoredCandidate(val categoryId: Int, val score: Int)
    internal data class ScoredItem(val description: String, val scores: List<ScoredCandidate>)

    private data class Call1Result(
        val merchant: String,
        val merchantLegalName: String?,
        val date: String,
        val amountCents: Int,
        val items: List<ScoredItem>,
        val multiCategoryLikely: Boolean?,
        val topChoice: Int?,
        val notes: String?
    )

    private suspend fun runPipeline(
        bitmap: Bitmap,
        allCategories: List<Category>,
        preSelectedCategoryIds: Set<Int>
    ): OcrResult {
        val preselected = preSelectedCategoryIds.isNotEmpty()
        val promptCats = if (preselected) {
            allCategories.filter { it.id in preSelectedCategoryIds }
        } else {
            allCategories.filter { it.tag != "supercharge" && it.tag != "recurring_income" && !it.deleted }
        }

        val c1 = runCall1(bitmap, preSelectedCategoryIds, promptCats)

        // Route:
        //   preSelect.size == 1  → trust the single preselected cat (1 call)
        //   preSelect.size >= 2  → multi path always (2 calls)
        //   preSelect.isEmpty()  → derive from items' top-1 cats (2 calls when multi)
        val multiPath = when {
            preSelectedCategoryIds.size == 1 -> false
            preSelectedCategoryIds.size >= 2 -> true
            else -> deriveMulti(c1.items, promptCats, c1.multiCategoryLikely)
        }

        return if (multiPath) {
            runMultiCat(bitmap, c1, promptCats)
        } else {
            buildSingleCatResult(c1, preSelectedCategoryIds, promptCats)
        }
    }

    private suspend fun runCall1(
        bitmap: Bitmap,
        preSelectedCategoryIds: Set<Int>,
        promptCats: List<Category>
    ): Call1Result {
        val json = JSONObject(generateWithRetry(call1Model, bitmap, buildCall1Prompt(preSelectedCategoryIds, promptCats)))
        val merchant = json.optString("merchant").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing merchant in Call 1")
        val merchantLegalName = json.optString("merchantLegalName").takeIf { it.isNotBlank() }
        val date = json.optString("date").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing date in Call 1")
        val amountCents = json.optInt("amountCents", -1).takeIf { it >= 0 }
            ?: throw IllegalStateException("Missing amountCents in Call 1")
        val items = json.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val desc = o.optString("description").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val scoresArr = o.optJSONArray("scores")
                val scores = if (scoresArr != null) {
                    (0 until scoresArr.length()).mapNotNull { j ->
                        val s = scoresArr.optJSONObject(j) ?: return@mapNotNull null
                        val cid = s.optInt("categoryId", -1).takeIf { it > 0 } ?: return@mapNotNull null
                        val score = s.optInt("score", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                        ScoredCandidate(cid, score)
                    }
                } else emptyList()
                ScoredItem(desc, scores)
            }
        } ?: emptyList()
        val multiCategoryLikely = if (json.has("multiCategoryLikely")) json.optBoolean("multiCategoryLikely") else null
        val topChoice = json.optInt("topChoice", -1).takeIf { it > 0 }
        val notes = json.optString("notes").takeIf { it.isNotBlank() }
        return Call1Result(merchant, merchantLegalName, date, amountCents, items, multiCategoryLikely, topChoice, notes)
    }

    private fun buildSingleCatResult(
        c1: Call1Result,
        preSelectedCategoryIds: Set<Int>,
        promptCats: List<Category>
    ): OcrResult {
        val validSet = promptCats.mapTo(mutableSetOf()) { it.id }
        val otherId = promptCats.firstOrNull { it.tag == "other" }?.id
        val categoryId = when {
            preSelectedCategoryIds.size == 1 -> preSelectedCategoryIds.first()
            c1.topChoice != null && c1.topChoice in validSet -> c1.topChoice
            otherId != null -> otherId
            else -> promptCats.firstOrNull()?.id
        }
        val categoryAmounts = categoryId?.let {
            listOf(OcrCategoryAmount(categoryId = it, amount = c1.amountCents / 100.0))
        }
        return OcrResult(
            merchant = c1.merchant,
            merchantLegalName = c1.merchantLegalName,
            date = c1.date,
            amount = c1.amountCents / 100.0,
            categoryAmounts = categoryAmounts,
            lineItems = null,
            notes = c1.notes
        )
    }

    private suspend fun runMultiCat(
        bitmap: Bitmap,
        c1: Call1Result,
        promptCats: List<Category>
    ): OcrResult {
        val items = collapseItemsToLineItems(c1.items, promptCats)

        val c3Json = generateWithRetry(call3Model, bitmap, buildCall3Prompt(items.map { it.description }))
        val c3 = JSONObject(c3Json)
        val priceCentsRaw = c3.optJSONArray("prices")?.let { arr ->
            (0 until arr.length()).map { i ->
                arr.optJSONObject(i)?.optInt("priceCents", 0) ?: 0
            }
        } ?: List(items.size) { 0 }
        val priceCents = List(items.size) { i -> priceCentsRaw.getOrNull(i) ?: 0 }

        val reconciled = reconcilePrices(items, priceCents, c1.amountCents)
        val categoryAmounts = aggregateCategoryAmounts(items, reconciled)

        return OcrResult(
            merchant = c1.merchant,
            merchantLegalName = c1.merchantLegalName,
            date = c1.date,
            amount = c1.amountCents / 100.0,
            categoryAmounts = categoryAmounts,
            lineItems = items.map { it.description }.ifEmpty { null },
            notes = c1.notes
        )
    }

    // ── Prompts ────────────────────────────────────────────────────

    private fun buildCall1Prompt(
        preSelectedCategoryIds: Set<Int>,
        promptCats: List<Category>
    ): String {
        val base = """Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value."""

        // When preSelect is a single category, skip item-level scoring entirely
        // — the shopper has already picked the bucket, we just need the header.
        if (preSelectedCategoryIds.size == 1) return base

        val categoryList = promptCats.joinToString("\n") { c ->
            if (c.tag.isNotEmpty()) "  - id=${c.id} name=\"${c.name}\" tag=\"${c.tag}\""
            else                    "  - id=${c.id} name=\"${c.name}\""
        }
        return base + """

For each PURCHASED item on the receipt, follow these 5 steps:
  Step 1 (ITEM): name the literal thing (e.g. "rubber disc", "leather collar").
  Step 2 (FUNCTION): describe what it's used for (e.g. "creates friction on a car wheel"; "worn by a pet").
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns (e.g. "vehicle repair part"; "pet accessory").
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100 based on how directly the name names the domain. Direct name-match = 80-100. Synonym match = 50-75. Weak fit = 20-50.

Return items[] with { description, scores: [{categoryId, score, reason}] }. Skip discounts/promos/subtotals; include "Sales Tax" as a line item.
Also return multiCategoryLikely (true if items' top-1 domains differ) and topChoice (when false, the single best-fit category id for the whole receipt).

Category-picking constraints:
  - "Other" is reserved for items that no other category name plausibly describes.
  - Do NOT invent categoryIds not in the list.

Categories:
$categoryList"""
    }

    private fun buildCall3Prompt(descriptions: List<String>): String {
        val listed = descriptions.mapIndexed { i, d -> "  ${i + 1}. $d" }.joinToString("\n")
        return """You have a receipt image and a list of items the shopper purchased. For each item, find it on the receipt and determine the ACTUAL PAID PRICE — what contributed to the subtotal — in integer cents.

Apply these clues when reading each line:
  1. Base printed price for the item's line.
  2. Quantity multiplier: lines like "2 AT 1 FOR ${'$'}X.XX", "3 @ ${'$'}X.XX ea", or "4 FOR ${'$'}X.XX" mean the actual charge is the multiplier × unit price. Use the line's total, not the unit price.
  3. Line-level coupons or manufacturer rebates (a subsequent line with a negative amount, e.g. "COUPON -${'$'}3.00") reduce that item's price.
  4. Weight-priced items: use the computed total already printed.
  5. For "Sales Tax", return the printed tax amount in cents.

Return JSON {prices: [{description, priceCents}]}, one entry per input item, preserving the order given. priceCents is a non-negative integer.

Items:
$listed"""
    }

    // ── Retry + transient handling ─────────────────────────────────

    private val transientPattern = Regex(
        "503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket",
        RegexOption.IGNORE_CASE
    )

    private fun isTransient(msg: String?): Boolean =
        msg?.let { transientPattern.containsMatchIn(it) } ?: false

    private suspend fun generateWithRetry(
        model: GenerativeModel,
        bitmap: Bitmap,
        prompt: String
    ): String {
        val maxAttempts = 4
        var lastErr: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                val response = model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
                return response.text ?: throw IllegalStateException("Empty response from model")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastErr = e
                if (!isTransient(e.message) || attempt == maxAttempts) throw e
                Log.d(TAG, "Transient error on attempt $attempt/$maxAttempts, retrying: ${e.message?.take(100)}")
                delay(500L shl (attempt - 1))
            }
        }
        throw lastErr ?: IllegalStateException("retry loop exited without result")
    }

    // ── Post-processing ────────────────────────────────────────────

    internal data class LineItem(val description: String, val categoryId: Int)

    /**
     * Derive multiCategoryLikely from per-item top-1 cats, excluding tax lines.
     * Falls back to the model's own flag when items[] is empty or unscored.
     */
    internal fun deriveMulti(
        items: List<ScoredItem>,
        cats: List<Category>,
        modelFlag: Boolean?
    ): Boolean {
        val validSet = cats.mapTo(mutableSetOf()) { it.id }
        val cids = HashSet<Int>()
        for (it in items) {
            if (isTaxLine(it.description)) continue
            val top = it.scores.filter { s -> s.categoryId in validSet }.maxByOrNull { s -> s.score } ?: continue
            cids.add(top.categoryId)
        }
        return when {
            cids.size >= 2 -> true
            cids.size == 1 -> false
            else -> modelFlag == true
        }
    }

    /**
     * Collapse Call 1's scored items to {description, categoryId} by top score.
     * Invalid/empty cats fall back to "Other" (resolved via tag, not id).
     */
    internal fun collapseItemsToLineItems(
        items: List<ScoredItem>,
        cats: List<Category>
    ): List<LineItem> {
        val validSet = cats.mapTo(mutableSetOf()) { it.id }
        val fallback = cats.firstOrNull { it.tag == "other" }?.id
            ?: cats.firstOrNull()?.id
            ?: return emptyList()
        return items.map { it ->
            val top = it.scores.filter { s -> s.categoryId in validSet }.maxByOrNull { s -> s.score }
            LineItem(it.description, top?.categoryId ?: fallback)
        }
    }

    /**
     * Scale non-tax items so Σ(prices) == amountCents exactly. Receipt-level
     * discounts (Target Circle, Walmart DISCOUNT GIVEN, loyalty savings at
     * subtotal) don't attach to any one line, so we distribute the delta
     * proportionally and keep the tax line exact. Residual from rounding is
     * absorbed into the largest non-tax item.
     */
    internal fun reconcilePrices(
        items: List<LineItem>,
        rawPriceCents: List<Int>,
        amountCents: Int
    ): List<Int> {
        if (items.isEmpty()) return emptyList()
        val taxIdx = items.indexOfFirst { isTaxLine(it.description) }
        val taxCents = if (taxIdx >= 0) rawPriceCents[taxIdx] else 0
        val targetNonTax = amountCents - taxCents
        val rawNonTaxSum = rawPriceCents.withIndex().sumOf { (i, c) -> if (i == taxIdx) 0 else c }
        if (rawNonTaxSum <= 0) return rawPriceCents.toList()

        val scale = targetNonTax.toDouble() / rawNonTaxSum
        val reconciled = rawPriceCents.mapIndexed { i, c ->
            if (i == taxIdx) c else kotlin.math.round(c * scale).toInt()
        }.toMutableList()

        val actualSum = reconciled.sum()
        val residual = amountCents - actualSum
        if (residual != 0) {
            var largestIdx = -1
            var largestVal = -1
            for (i in reconciled.indices) {
                if (i == taxIdx) continue
                if (reconciled[i] > largestVal) { largestVal = reconciled[i]; largestIdx = i }
            }
            if (largestIdx >= 0) reconciled[largestIdx] += residual
        }
        return reconciled
    }

    // Widened to match any receipt line containing the word "tax" — catches
    // both "Sales Tax" and Amazon-style "Estimated tax to be collected". A
    // narrower /sales\s*tax/i let Amazon single-item receipts be treated as
    // 2-item multi-cat receipts, over-routing to Call 3.
    private fun isTaxLine(desc: String): Boolean =
        Regex("\\btax\\b", RegexOption.IGNORE_CASE).containsMatchIn(desc)

    /**
     * Build categoryAmounts by summing reconciled line prices per categoryId.
     * Sales Tax is allocated to the dominant non-tax category so the UI sees
     * one coherent split that matches labelling conventions.
     */
    internal fun aggregateCategoryAmounts(
        items: List<LineItem>,
        reconciledPriceCents: List<Int>
    ): List<OcrCategoryAmount>? {
        if (items.isEmpty()) return null
        val byCat = LinkedHashMap<Int, Int>()
        var taxCents = 0
        for (i in items.indices) {
            val cents = reconciledPriceCents.getOrNull(i) ?: 0
            if (isTaxLine(items[i].description)) {
                taxCents += cents
            } else {
                byCat[items[i].categoryId] = (byCat[items[i].categoryId] ?: 0) + cents
            }
        }
        if (byCat.isEmpty()) {
            val cid = items.firstOrNull()?.categoryId ?: return null
            return listOf(OcrCategoryAmount(categoryId = cid, amount = taxCents / 100.0))
        }
        if (taxCents > 0) {
            val dominantCid = byCat.maxByOrNull { it.value }?.key
            if (dominantCid != null) byCat[dominantCid] = (byCat[dominantCid] ?: 0) + taxCents
        }
        return byCat.map { (cid, cents) -> OcrCategoryAmount(categoryId = cid, amount = cents / 100.0) }
    }
}

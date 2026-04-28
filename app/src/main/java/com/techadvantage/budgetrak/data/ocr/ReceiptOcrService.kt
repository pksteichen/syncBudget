package com.techadvantage.budgetrak.data.ocr

import android.content.Context
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Split-pipeline Gemini 2.5 Flash-Lite receipt OCR (V18, 2026-04-20).
 *
 *   Call 1  (image → extract):    merchant, date, amountCents, itemNames[],
 *                                 fullTranscript[] (focused lines for reconciliation).
 *   Call 1.5 (text → reconcile):  re-reads date + amountCents off the transcript
 *                                 as an independent second pass. Text-only (no
 *                                 image) so it's cheap. Runs in PARALLEL with Call 2
 *                                 after Call 1 returns, so the extra call adds no
 *                                 wall-clock latency on the common path.
 *   Call 2  (image + items → categorise): items[{description,
 *                                         scores[{categoryId, score, reason}]}]
 *                                         + multiCategoryLikely + topChoice.
 *   Call 3  (image + item list → prices): priceCents per item. Only runs for
 *                                         multi-cat receipts.
 *
 * Why the split: Call 1 does the image-reading work and emits STABLE TEXT
 * (item names). Call 2 then reasons about categories with the text as its
 * primary anchor and the image available for disambiguation. This is much
 * less sensitive to JPEG-encoder variance between test harness (ImageMagick)
 * and device (Android Bitmap.compress) — in earlier all-in-one Call 1
 * designs, the same stored receipt flipped between Transportation/Gas and
 * Home Supplies depending on which encoder produced the bytes. Splitting
 * image-reading from categorisation stabilises the ranking.
 *
 * Why Call 1.5: at temperature 0, Flash-Lite still produces single-digit OCR
 * errors on small totals and flips MM/DD vs DD/MM on soft locale signals.
 * A second independent pass over the receipt text catches both classes on
 * the harness (+6pp on amount, +2pp on date). Text-only makes it cheap; the
 * parallel launch with Call 2 hides the latency.
 *
 * Call counts (worst case; +1 vs V17 on every path, parallelized on the hot path):
 *   preSelect.size == 1: 1 call  (header only; trust the preselected cat; skip reconcile).
 *   preSelect.isEmpty(), single-cat: 3 calls (Call 1, then Call 1.5 ‖ Call 2).
 *   preSelect.size >= 2 OR multi-cat: 4 calls (Call 1, Call 1.5 ‖ Call 2, then Call 3).
 *
 * Category-agnostic: the prompts never hard-code a user's category id or
 * name except referring to "Other" as the always-present fallback concept.
 * "Other" is resolved at runtime via tag == "other", so the service works
 * for any custom category list. Test-harness reference lives at
 * tools/ocr-harness/scripts/test-v16-split-with-image.js.
 */
object ReceiptOcrService {
    private const val TAG = "ReceiptOcrService"
    private const val TIMEOUT_MS = 90_000L

    // ── Schemas ────────────────────────────────────────────────────

    // Call 1: header + item name list + focused transcript. Categorisation
    // lives in Call 2; date/amount reconciliation lives in Call 1.5.
    private val call1Schema = Schema.obj(
        name = "Call1Result",
        description = "Receipt header + purchased item name list",
        Schema.str("merchant", "Consumer brand on the receipt header"),
        Schema.str("merchantLegalName", "Optional legal operator entity"),
        Schema.str("date", "Transaction date in YYYY-MM-DD"),
        Schema.int("amountCents", "Final total paid, integer cents"),
        Schema.arr(
            "itemNames",
            "Every purchased line, as printed. Skip promos/coupons/discounts/tenders/subtotals. Include tax lines (Sales Tax, Estimated tax, etc.) as their own entry.",
            Schema.str("itemName", "One receipt line, verbatim")
        ),
        Schema.arr(
            "fullTranscript",
            "Focused transcript used by the Call 1.5 reconciliation step. Include ONLY lines with a calendar date, a monetary amount, Subtotal/Total/Tax labels, or merchant header. Skip barcodes, policy boilerplate, signatures. 10-30 lines is typical.",
            Schema.str("line", "One receipt line, verbatim")
        ),
        Schema.str("notes", "Optional free-form note")
    )

    // Call 1.5: text-only reconciliation of merchant + date + amountCents. No image.
    private val call1rSchema = Schema.obj(
        name = "Call1rResult",
        description = "Reconciled merchant + date + amount after cross-checking against the transcript",
        Schema.str("merchant", "Reconciled consumer-brand merchant name"),
        Schema.str("date", "Reconciled transaction date in YYYY-MM-DD"),
        Schema.int("amountCents", "Reconciled final total paid, integer cents"),
        Schema.str("notes", "Optional one-sentence explanation when values changed")
    )

    // Call 2: receives image + the item names from Call 1 as text, returns
    // per-item scored categories + routing hints.
    private val call2Schema = Schema.obj(
        name = "Call2Result",
        description = "Per-item category scores + routing decision",
        Schema.arr(
            "items",
            "Per-item category scoring, SAME ORDER as the input name list",
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
        Schema.bool("multiCategoryLikely", "True when items' top-1 domains differ"),
        Schema.int("topChoice", "Best-fit category id for the whole receipt when multiCategoryLikely is false")
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
    private val call1rModel by lazy { liteModel(call1rSchema) }
    private val call2Model by lazy { liteModel(call2Schema) }
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
            if (!file.exists() || file.length() == 0L) {
                return Result.failure(IllegalStateException("Receipt file not found: $receiptId"))
            }
            // Read raw bytes and pass via content { blob("image/jpeg", …) }.
            // The GenerativeAI SDK's content { image(bitmap) } path re-encodes
            // every Bitmap at JPEG quality 80 before sending (see
            // com.google.ai.client.generativeai.internal.util.ConversionsKt.
            // encodeBitmapToBase64Png), which silently degrades the q=92+
            // receipts we store. blob() bypasses the re-encode entirely.
            val bytes = file.readBytes()

            val result = withTimeout(TIMEOUT_MS) {
                runPipeline(bytes, categories, preSelectedCategoryIds)
            }
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

    private data class Call1Header(
        val merchant: String,
        val merchantLegalName: String?,
        val date: String,
        val amountCents: Int,
        val itemNames: List<String>,
        val fullTranscript: List<String>,
        val notes: String?
    )

    private data class Call1Reconciled(
        val merchant: String,
        val date: String,
        val amountCents: Int,
        val notes: String?
    )

    private data class Call2Categorization(
        val items: List<ScoredItem>,
        val multiCategoryLikely: Boolean?,
        val topChoice: Int?
    )

    private suspend fun runPipeline(
        imageBytes: ByteArray,
        allCategories: List<Category>,
        preSelectedCategoryIds: Set<Int>
    ): OcrResult {
        val preselected = preSelectedCategoryIds.isNotEmpty()
        val promptCats = if (preselected) {
            allCategories.filter { it.id in preSelectedCategoryIds }
        } else {
            allCategories.filter { it.tag != "supercharge" && it.tag != "recurring_income" && !it.deleted }
        }

        // Call 1 — image → header + item names + focused transcript. Always runs.
        val c1 = runCall1(imageBytes)

        // Shortcut: single preselected cat. No need to categorise anything.
        // Skip reconcile too — a preselected receipt is usually a quick-entry
        // flow where the user is watching latency and won't benefit from the
        // extra 1s round-trip.
        if (preSelectedCategoryIds.size == 1) {
            return buildSingleCatResult(c1, null, preSelectedCategoryIds.first())
        }

        // Empty item-name list (edge case: unreadable receipt) → put the
        // whole amount in Other or the single preselected cat (handled above).
        if (c1.itemNames.isEmpty()) {
            val otherId = promptCats.firstOrNull { it.tag == "other" }?.id
                ?: promptCats.firstOrNull()?.id
            return buildSingleCatResult(c1, null, otherId)
        }

        // Call 1.5 (text-only reconciliation) and Call 2 (image+items) run in
        // parallel. Each depends only on Call 1's output; neither depends on
        // the other. Launching concurrently hides Call 1.5's ~1s latency
        // behind Call 2's longer image-bearing round-trip.
        val (c1r, c2) = coroutineScope {
            val c1rDeferred = async { runCall1Reconcile(c1) }
            val c2Deferred = async { runCall2(imageBytes, c1.itemNames, promptCats, preselected) }
            Pair(c1rDeferred.await(), c2Deferred.await())
        }
        if (BuildConfig.DEBUG && (c1r.merchant != c1.merchant || c1r.date != c1.date || c1r.amountCents != c1.amountCents)) {
            Log.d(TAG, "Call1.5 reconciled: merchant ${c1.merchant}→${c1r.merchant}, date ${c1.date}→${c1r.date}, amountCents ${c1.amountCents}→${c1r.amountCents}${c1r.notes?.let { " — $it" } ?: ""}")
        }

        // Route:
        //   preSelect.size >= 2  → multi path always (4 calls total)
        //   preSelect.isEmpty()  → derive from Call 2's items' top-1 cats.
        val multiPath = when {
            preSelectedCategoryIds.size >= 2 -> true
            else -> deriveMulti(c2.items, promptCats, c2.multiCategoryLikely)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "runPipeline multiPath=$multiPath modelTopChoice=${c2.topChoice} modelMulti=${c2.multiCategoryLikely} itemCount=${c2.items.size}")
        }

        return if (multiPath) {
            runMultiCat(imageBytes, c1, c1r, c2, promptCats)
        } else {
            val validSet = promptCats.mapTo(mutableSetOf()) { it.id }
            val otherId = promptCats.firstOrNull { it.tag == "other" }?.id
            val cid = when {
                c2.topChoice != null && c2.topChoice in validSet -> c2.topChoice
                otherId != null -> otherId
                else -> promptCats.firstOrNull()?.id
            }
            buildSingleCatResult(c1, c1r, cid)
        }
    }

    private suspend fun runCall1(imageBytes: ByteArray): Call1Header {
        val raw = generateWithRetry(call1Model, imageBytes, buildCall1Prompt())
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Call1 imageBytes=${imageBytes.size}")
            raw.chunked(3500).forEachIndexed { i, chunk -> Log.d(TAG, "Call1 raw[$i]: $chunk") }
        }
        val json = JSONObject(raw)
        val merchant = json.optString("merchant").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing merchant in Call 1")
        val merchantLegalName = json.optString("merchantLegalName").takeIf { it.isNotBlank() }
        // Empty date is allowed — model returns "" when no calendar date is
        // visible on the receipt. TransactionDialog leaves selectedDate at
        // its current default (today) when date fails to parse.
        val date = json.optString("date")
        val amountCents = json.optInt("amountCents", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            ?: throw IllegalStateException("Missing amountCents in Call 1")
        val itemNames = json.optJSONArray("itemNames")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        } ?: emptyList()
        val fullTranscript = json.optJSONArray("fullTranscript")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        } ?: emptyList()
        val notes = json.optString("notes").takeIf { it.isNotBlank() }
        return Call1Header(merchant, merchantLegalName, date, amountCents, itemNames, fullTranscript, notes)
    }

    /**
     * Text-only second pass over the receipt transcript. Returns the
     * reconciled date + amountCents. Falls back to C1's values on any
     * parse or API error so reconciliation can never make us worse — only
     * silently-better or silently-same.
     */
    private suspend fun runCall1Reconcile(c1: Call1Header): Call1Reconciled {
        val fallback = Call1Reconciled(c1.merchant, c1.date, c1.amountCents, null)
        if (c1.fullTranscript.isEmpty()) return fallback
        return try {
            val raw = generateTextOnlyWithRetry(call1rModel, buildCall1ReconcilePrompt(c1))
            if (BuildConfig.DEBUG) {
                raw.chunked(3500).forEachIndexed { i, chunk -> Log.d(TAG, "Call1r raw[$i]: $chunk") }
            }
            val json = JSONObject(raw)
            // Merchant: empty doesn't carry meaningful signal, so fall back
            // to C1 if C1.5 returns empty.
            val merchant = json.optString("merchant").takeIf { it.isNotBlank() } ?: c1.merchant
            // Date: empty IS a meaningful signal from C1.5 — it means "no
            // calendar date was found in the transcript, don't invent one."
            // Only fall back to C1 if the field is truly absent from the
            // response (malformed JSON). An empty string "" propagates as-is
            // so the UI can show today's default instead of C1's hallucinated
            // 2023-10-27 on dateless receipts.
            val date = if (json.has("date")) json.optString("date") else c1.date
            val amountCents = json.optInt("amountCents", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } ?: c1.amountCents
            val notes = json.optString("notes").takeIf { it.isNotBlank() }
            Call1Reconciled(merchant, date, amountCents, notes)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.d(TAG, "Call1.5 reconcile failed, falling back to C1: ${e.message?.take(100)}")
            fallback
        }
    }

    private suspend fun runCall2(
        imageBytes: ByteArray,
        itemNames: List<String>,
        promptCats: List<Category>,
        preselected: Boolean
    ): Call2Categorization {
        val raw = generateWithRetry(call2Model, imageBytes, buildCall2Prompt(itemNames, promptCats, preselected))
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Call2 items=${itemNames.size} cats=${promptCats.size} preselected=$preselected")
            raw.chunked(3500).forEachIndexed { i, chunk -> Log.d(TAG, "Call2 raw[$i]: $chunk") }
        }
        val json = JSONObject(raw)
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
        return Call2Categorization(items, multiCategoryLikely, topChoice)
    }

    /**
     * Build a single-cat OcrResult using reconciled date/amount when
     * available. Pass c1r = null on paths that skipped reconciliation
     * (preselected single-cat, empty-items) — we fall back to c1 values.
     */
    private fun buildSingleCatResult(
        c1: Call1Header,
        c1r: Call1Reconciled?,
        categoryId: Int?
    ): OcrResult {
        val merchant = c1r?.merchant ?: c1.merchant
        val date = c1r?.date ?: c1.date
        val amountCents = c1r?.amountCents ?: c1.amountCents
        val categoryAmounts = categoryId?.let {
            listOf(OcrCategoryAmount(categoryId = it, amount = amountCents / 100.0))
        }
        return OcrResult(
            merchant = merchant,
            merchantLegalName = c1.merchantLegalName,
            date = date,
            amount = amountCents / 100.0,
            categoryAmounts = categoryAmounts,
            lineItems = null,
            notes = c1.notes
        )
    }

    private suspend fun runMultiCat(
        imageBytes: ByteArray,
        c1: Call1Header,
        c1r: Call1Reconciled,
        c2: Call2Categorization,
        promptCats: List<Category>
    ): OcrResult {
        val items = collapseItemsToLineItems(c2.items, promptCats)

        val c3Json = generateWithRetry(call3Model, imageBytes, buildCall3Prompt(items.map { it.description }))
        val c3 = JSONObject(c3Json)
        val priceCentsRaw = c3.optJSONArray("prices")?.let { arr ->
            (0 until arr.length()).map { i ->
                arr.optJSONObject(i)?.optInt("priceCents", 0) ?: 0
            }
        } ?: List(items.size) { 0 }
        val priceCents = List(items.size) { i -> priceCentsRaw.getOrNull(i) ?: 0 }

        val reconciled = reconcilePrices(items, priceCents, c1r.amountCents)
        val categoryAmounts = aggregateCategoryAmounts(items, reconciled)

        return OcrResult(
            merchant = c1r.merchant,
            merchantLegalName = c1.merchantLegalName,
            date = c1r.date,
            amount = c1r.amountCents / 100.0,
            categoryAmounts = categoryAmounts,
            lineItems = items.map { it.description }.ifEmpty { null },
            notes = c1.notes
        )
    }

    // ── Prompts ────────────────────────────────────────────────────

    private fun buildCall1Prompt(): String =
        """Extract receipt data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate. If the receipt contains "Sold by:" or "Seller" or "Order placed" or "Shipping & Handling", it's an online-marketplace (Amazon, eBay, Etsy, Walmart.com, Target.com). Set merchant to the platform.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. Default to US MM/DD/YYYY. Parse as DD/MM/YYYY only when the receipt shows a non-US signal: non-USD currency (€, £, ¥, HK${'$'}, SG${'$'}, RM, ₫, ₹), a non-US country or address format, non-English body text, or a known non-US merchant. A 5-digit US ZIP or US state abbreviation (CA, NY, TX, MN, …) anchors back to MM/DD. **If NO calendar date is visible anywhere on the receipt, return an empty string "" — do NOT invent or guess a date from context.** Partial dates without a year (e.g. a ship-tracker line showing only "Arriving tomorrow" or "Delivered Mon") are NOT dates — return empty in that case too.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Before finalizing, verify amountCents ≈ (subtotalCents + taxCents) within 2 cents — if not, re-examine the digits. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.
- itemNames: array of strings — the actual PURCHASED PRODUCTS on the receipt.

  INCLUDE in itemNames:
    - Every product the shopper bought, by its printed name (e.g. "BOSCH BE964H Blue Ceramic Disc Brake", "QZIIW iPhone 17 Pro Max Charger", "2% MILK 1GAL", "Tzatziki Dip"). On online-order receipts the product is usually shown alongside a thumbnail image; that's the one you want.
    - Tax lines, one per tax line (e.g. "Sales Tax", "Estimated tax to be collected", "GST"). These belong in itemNames as their own entry so downstream price-attribution can handle them.

  EXCLUDE from itemNames (these are NOT products — they're totals, shipping, fees, payment, or header info):
    - Subtotals / totals / grand totals: "Subtotal", "Item(s) Subtotal", "Total", "Grand Total", "Total before tax", "Total after tax", "Order total".
    - Shipping, handling, delivery: "Shipping", "Shipping & Handling", "Delivery", "S&H".
    - Discounts, promos, coupons, rewards: "Discount", "Savings", "Promotion", "Coupon", "Member discount", "Target Circle".
    - Payment tenders: "Visa ending in 1234", "Cash", "Credit", "Change", "Amount tendered".
    - Order metadata: order numbers, tracking numbers, addresses, "Sold by" lines.
    - Action buttons from online receipts ("Track package", "Cancel items", "Buy again", etc.).

  If the receipt is from an online order (Amazon, Target pickup, DoorDash, etc.) and you see a dedicated "Order Summary" section near the bottom with Subtotal/Shipping/Total rows, that section is SUMMARY only — do NOT list those rows as items. The actual products are usually shown earlier on the receipt next to their images and quantities.

- fullTranscript: array of strings — a FOCUSED transcript used by a downstream date/amount reconciliation step. Include ONLY these kinds of lines, in top-to-bottom order:
    - Any line containing a calendar date (transaction, bill, invoice, due date, etc.)
    - Any line containing a monetary amount (item price, subtotal, tax, total, payment tender)
    - Lines labeled Subtotal / Total / Grand Total / Balance Due / Tax / GST / VAT
    - Merchant header lines (store name + city/country — first 2-3 lines of the receipt)
  SKIP barcodes, serial/auth/terminal/cashier IDs, signature lines, return policy boilerplate, rewards-program marketing, bare UPC numbers, and ornamental footer text. Keep the transcript tight — typically 10-30 lines."""

    private fun buildCall1ReconcilePrompt(c1: Call1Header): String {
        val lines = c1.fullTranscript.mapIndexed { i, l -> "  ${i + 1}. $l" }.joinToString("\n")
        return """A receipt image was read in a first pass. You now have the first-pass merchant + date + amount AND the raw text transcript of the same receipt. Cross-check them and return reconciled values.

First-pass values:
  merchant = "${c1.merchant}"
  date = "${c1.date}"
  amountCents = ${c1.amountCents}

Raw transcript (line-numbered):
$lines

Task:
  1. Verify the merchant. If the transcript contains "Sold by:" or "Seller" or "Order placed" or "Shipping & Handling", it's an online-marketplace order (Amazon, eBay, Etsy, Walmart.com, Target.com) — the merchant should be the PLATFORM, NOT a product brand (e.g. "QZIIW iPhone Charger") or the third-party seller named after "Sold by:" (e.g. "Meigo"). If the first-pass merchant is one of those product brands or third-party sellers and the transcript contains those marketplace signals, correct the merchant to the platform inferred from the receipt layout (Amazon orders show "Order placed" + "Item(s) Subtotal" + "Grand Total"; eBay shows order-number patterns; etc.). Otherwise keep the first-pass merchant.
  2. Find the transaction date line in the transcript (a line containing a calendar date). Prefer a purchase/transaction date over print timestamps or due dates. Default to US MM/DD/YYYY unless the transcript shows a non-US signal (non-USD currency, non-US address/country, non-English body). **If NO calendar date appears anywhere in the transcript, return empty string "" — NEVER invent a date from training data or outside context. Ship-tracker phrases like "Arriving tomorrow" or "Delivered Mon" are NOT dates.**
  3. Find the final total amount line in the transcript (the line that represents what the shopper actually paid — typically labeled "TOTAL", "GRAND TOTAL", "BALANCE DUE", "AMOUNT PAID", "Total Sale"). Ignore "SUBTOTAL", "TAX", and any GST/VAT summary rows.
  4. Self-check: verify that amount ≈ subtotal + tax (±2 cents). If the first-pass amount fails that check but a different total line in the transcript passes it, use the transcript-supported value.
  5. If the transcript and first-pass agree, return the first-pass values unchanged. If they disagree, return the transcript-supported values and explain briefly in notes.

Return {merchant, date, amountCents, notes}."""
    }

    private fun buildCall2Prompt(
        itemNames: List<String>,
        promptCats: List<Category>,
        preselected: Boolean
    ): String {
        val listed = itemNames.mapIndexed { i, n -> "  ${i + 1}. $n" }.joinToString("\n")
        val categoryList = promptCats.joinToString("\n") { c ->
            if (c.tag.isNotEmpty()) "  - id=${c.id} name=\"${c.name}\" tag=\"${c.tag}\""
            else                    "  - id=${c.id} name=\"${c.name}\""
        }
        val preselectNudge = if (preselected) {
            """

  - The categories below are pre-selected by the shopper for this receipt. Try to cover as many of them as reasonably fit — the shopper expects to see items in these specific buckets. But skip a category if no item on the receipt plausibly fits it; never force-fit an item into a bucket that clearly doesn't match (the shopper may have pre-selected a category by accident)."""
        } else ""

        return """For each of the following purchased items (extracted from the receipt in the image), determine its best-fit category. Return items[] with {description, scores:[{categoryId, score, reason}]} in the SAME ORDER as the input, plus multiCategoryLikely and topChoice.

Items:
$listed

For each item, follow these 5 steps:
  Step 1 (ITEM): identify the literal thing the name refers to (e.g. "rubber disc", "leather collar").
  Step 2 (FUNCTION): describe what it's used for or who uses it.
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns (e.g. "vehicle repair part", "pet accessory", "prepared meal").
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100.
    - A category whose name DIRECTLY contains a word naming the item's domain (e.g. "Transportation" for a car part, "Pet" for a pet accessory, "Phone" for a phone charger) = 80-100.
    - A category whose name doesn't contain the domain word but is a close synonym = 50-75.
    - A weak or tangential fit = 20-50.
    - Tie-break: when a category whose name contains a generic word ("Supplies", "Goods", "Items", "General", "Miscellaneous") competes with a category whose name directly names the domain, the directly-named category scores higher.

Global rules:
  - "Other" is reserved for items that no other category name plausibly describes.
  - Tax lines (e.g. "Sales Tax", "Estimated tax to be collected") go into the category that receives the most non-tax weight from the rest of the receipt. You won't know amounts at this stage — pick the dominant category from the other items' top-1 scores.
  - Do NOT invent categoryIds not in the list.

Set multiCategoryLikely = true when the items' top-1 categories span 2+ distinct real-world domains. Set topChoice (required when multiCategoryLikely is false) = the single best-fit category id for the whole receipt.$preselectNudge

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
        imageBytes: ByteArray,
        prompt: String
    ): String {
        val maxAttempts = 4
        var lastErr: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                val response = model.generateContent(
                    content {
                        // blob(...) bypasses the SDK's Bitmap→JPEG re-encode
                        // (fixed quality 80). See the note in extractFromReceipt.
                        blob("image/jpeg", imageBytes)
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

    /**
     * Text-only retry variant for Call 1.5 — no image, just prompt. Same
     * transient regex and backoff curve.
     */
    private suspend fun generateTextOnlyWithRetry(
        model: GenerativeModel,
        prompt: String
    ): String {
        val maxAttempts = 4
        var lastErr: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                val response = model.generateContent(content { text(prompt) })
                return response.text ?: throw IllegalStateException("Empty response from model")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastErr = e
                if (!isTransient(e.message) || attempt == maxAttempts) throw e
                Log.d(TAG, "Call1.5 transient on attempt $attempt/$maxAttempts, retrying: ${e.message?.take(100)}")
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
     * Collapse Call 2's scored items to {description, categoryId} by top score.
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

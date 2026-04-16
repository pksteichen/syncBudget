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

object ReceiptOcrService {
    private const val TAG = "ReceiptOcrService"
    private const val TIMEOUT_MS = 90_000L

    enum class Tier { LITE, FLASH, PRO }

    // ── Schemas ────────────────────────────────────────────────────

    // Shared by Flash and Pro — amount as Double. `lineItems` omitted (harness
    // A/B showed ~53% fewer output tokens with no loss to accuracy).
    private val flashProSchema = Schema.obj(
        name = "OcrResult",
        description = "Receipt extraction result",
        Schema.str("merchant", "Consumer brand on the receipt header"),
        Schema.str("merchantLegalName", "Optional legal operator entity"),
        Schema.str("date", "Transaction date in YYYY-MM-DD"),
        Schema.double("amount", "Final total paid"),
        Schema.arr(
            "categoryAmounts",
            "Per-category amounts that sum to the total",
            Schema.obj(
                name = "CategoryAmount",
                description = "One category's share of the receipt",
                Schema.int("categoryId", "Category id from the user's list"),
                Schema.double("amount", "Amount for this category")
            )
        ),
        Schema.str("notes", "Optional free-form note")
    )

    // Lite-specific: uses amountCents (integer) to sidestep locale float-parse
    // issues. Includes transcription field (Lite's R11 transcribe-then-extract
    // pattern — improves downstream accuracy on complex receipts).
    private val liteSchema = Schema.obj(
        name = "OcrResult",
        description = "Receipt extraction result (Lite)",
        Schema.str("transcription", "Plain-text transcription of every line on the receipt"),
        Schema.str("merchant", "Consumer brand on the receipt header"),
        Schema.str("merchantLegalName", "Optional legal operator entity"),
        Schema.str("date", "Transaction date in YYYY-MM-DD"),
        Schema.int("amountCents", "Final total paid, integer cents"),
        Schema.arr(
            "categoryAmounts",
            "Per-category amounts that sum to the total (dollars, not cents)",
            Schema.obj(
                name = "CategoryAmount",
                description = "One category's share of the receipt",
                Schema.int("categoryId", "Category id from the user's list"),
                Schema.double("amount", "Amount for this category")
            )
        ),
        Schema.str("notes", "Optional free-form note")
    )

    // ── Models (lazy — only the tiers actually used get instantiated) ─

    private val liteModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = liteSchema
                temperature = 0f
            }
        )
    }

    private val flashModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashProSchema
                temperature = 0f
            }
        )
    }

    private val proModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-pro",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashProSchema
                temperature = 0f
            }
        )
    }

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Extract receipt data. Caller picks the preferred tier based on user
     * intent (see [Tier]). Pro automatically falls back to Flash on failure.
     */
    suspend fun extractFromReceipt(
        context: Context,
        receiptId: String,
        categories: List<Category>,
        preferredTier: Tier = Tier.FLASH
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

            // Fallback chain: PRO → FLASH (if pro fails after retries).
            // LITE and FLASH don't fall back — failures are network/auth/config,
            // not capacity, so retrying a different tier won't help.
            val chain = when (preferredTier) {
                Tier.PRO -> listOf(Tier.PRO, Tier.FLASH)
                Tier.FLASH -> listOf(Tier.FLASH)
                Tier.LITE -> listOf(Tier.LITE)
            }

            var lastErr: Exception? = null
            for (tier in chain) {
                try {
                    val jsonText = withTimeout(TIMEOUT_MS) {
                        generateWithRetry(tier, bitmap, categories)
                    }
                    bitmap.recycle()
                    return Result.success(parseOcrResult(jsonText, tier))
                } catch (ce: CancellationException) {
                    bitmap.recycle()
                    throw ce
                } catch (e: Exception) {
                    lastErr = e
                    Log.w(TAG, "Tier $tier failed: ${e.message?.take(120)}")
                    // Only fall through to next tier for transient failures; if
                    // the error is clearly config/auth, don't waste the Flash call.
                    if (!isTransientOrCapacity(e.message)) break
                }
            }
            bitmap.recycle()
            Result.failure(lastErr ?: IllegalStateException("OCR failed without error"))
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed for receipt $receiptId: ${e.message}")
            runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            Result.failure(e)
        }
    }

    // ── Retry + fallback internals ────────────────────────────────

    private val transientPattern = Regex(
        "503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket",
        RegexOption.IGNORE_CASE
    )

    private fun isTransientOrCapacity(msg: String?): Boolean =
        msg?.let { transientPattern.containsMatchIn(it) } ?: false

    private suspend fun generateWithRetry(
        tier: Tier,
        bitmap: Bitmap,
        categories: List<Category>
    ): String {
        val model = when (tier) {
            Tier.LITE -> liteModel
            Tier.FLASH -> flashModel
            Tier.PRO -> proModel
        }
        val prompt = when (tier) {
            Tier.LITE -> buildLiteOcrPrompt(categories)
            Tier.FLASH, Tier.PRO -> buildOcrPrompt(categories)
        }
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
                val transient = isTransientOrCapacity(e.message)
                if (!transient || attempt == maxAttempts) throw e
                Log.d(TAG, "Transient $tier error on attempt $attempt/$maxAttempts, retrying: ${e.message?.take(100)}")
                delay(500L shl (attempt - 1))  // 500ms, 1s, 2s
            }
        }
        throw lastErr ?: IllegalStateException("retry loop exited without result")
    }

    // ── Parsing ───────────────────────────────────────────────────

    private fun parseOcrResult(jsonText: String, tier: Tier): OcrResult {
        val json = JSONObject(jsonText)
        val merchant = json.optString("merchant").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing merchant in OCR response")
        val date = json.optString("date").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing date in OCR response")

        val amount = when (tier) {
            Tier.LITE -> {
                val cents = json.optInt("amountCents", -1).takeIf { it >= 0 }
                    ?: throw IllegalStateException("Missing amountCents in Lite OCR response")
                cents / 100.0
            }
            Tier.FLASH, Tier.PRO -> json.optDouble("amount", Double.NaN).takeIf { !it.isNaN() }
                ?: throw IllegalStateException("Missing amount in OCR response")
        }

        val cats = json.optJSONArray("categoryAmounts")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val cid = o.optInt("categoryId", -1).takeIf { it > 0 } ?: return@mapNotNull null
                val amt = o.optDouble("amount", Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null
                OcrCategoryAmount(categoryId = cid, amount = amt)
            }
        }

        return OcrResult(
            merchant = merchant,
            merchantLegalName = json.optString("merchantLegalName").takeIf { it.isNotBlank() },
            date = date,
            amount = amount,
            categoryAmounts = reconcileCategoryAmounts(cats?.takeIf { it.isNotEmpty() }, amount),
            lineItems = null,  // schema doesn't request it (see OcrPromptBuilder note)
            notes = json.optString("notes").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Guarantee Σ(categoryAmounts) == amount (to the cent) before handing the
     * result to the UI. The TransactionDialog Save button enforces this
     * invariant; if the model returns a split that drifts from the total, the
     * user would see a save-blocked dialog with no obvious fix.
     *
     * Strategy: round each amount to cents, absorb the residual delta into the
     * largest category. If the residual would push the largest category
     * negative or above the total (pathological model output), collapse to a
     * single-category entry so the user at least gets a savable pre-fill.
     */
    internal fun reconcileCategoryAmounts(
        cats: List<OcrCategoryAmount>?,
        total: Double
    ): List<OcrCategoryAmount>? {
        if (cats.isNullOrEmpty()) return cats
        val roundedTotal = kotlin.math.round(total * 100.0) / 100.0
        val rounded = cats.map { it.copy(amount = kotlin.math.round(it.amount * 100.0) / 100.0) }
        val sum = rounded.sumOf { it.amount }
        val delta = kotlin.math.round((roundedTotal - sum) * 100.0) / 100.0
        if (kotlin.math.abs(delta) < 0.005) return rounded
        val largestIdx = rounded.indices.maxByOrNull { rounded[it].amount } ?: return rounded
        val largest = rounded[largestIdx]
        val adjusted = kotlin.math.round((largest.amount + delta) * 100.0) / 100.0
        if (adjusted <= 0.0 || adjusted > roundedTotal + 0.005) {
            return listOf(OcrCategoryAmount(categoryId = largest.categoryId, amount = roundedTotal))
        }
        return rounded.toMutableList().also { it[largestIdx] = largest.copy(amount = adjusted) }
    }
}

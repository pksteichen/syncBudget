package com.techadvantage.budgetrak.data.ai

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.techadvantage.budgetrak.BuildConfig
import com.techadvantage.budgetrak.data.Category
import com.techadvantage.budgetrak.data.Transaction
import com.techadvantage.budgetrak.data.ocr.GeminiHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

object AiCategorizerService {
    private const val TAG = "AiCategorizerService"
    private const val TIMEOUT_MS = 30_000L
    private const val CHUNK_SIZE = 100
    private const val MODEL_NAME = "gemini-2.5-flash-lite"

    // Inline JSON Schema (matches the Gemini OpenAPI subset; replaces the
    // SDK's Schema.obj/Schema.int builders).
    private val schema: JSONObject = JSONObject()
        .put("type", "OBJECT")
        .put("description", "One category id per transaction index")
        .put("properties", JSONObject()
            .put("results", JSONObject()
                .put("type", "ARRAY")
                .put("description", "One entry per transaction")
                .put("items", JSONObject()
                    .put("type", "OBJECT")
                    .put("description", "Transaction index and assigned category id")
                    .put("properties", JSONObject()
                        .put("i", JSONObject()
                            .put("type", "INTEGER")
                            .put("description", "The transaction index from the input"))
                        .put("categoryId", JSONObject()
                            .put("type", "INTEGER")
                            .put("description", "Category id chosen from the provided list")))
                    .put("required", JSONArray(listOf("i", "categoryId"))))))
        .put("required", JSONArray(listOf("results")))

    /**
     * Categorize a batch of transactions in a single call (chunked at 100).
     * Returns a map from input index → categoryId. Entries missing from the map
     * (model skipped them, returned unknown id, or the whole call failed) should
     * fall back to whatever the caller had before AI ran.
     */
    suspend fun categorizeBatch(
        context: Context,
        transactions: List<Transaction>,
        categories: List<Category>
    ): Result<Map<Int, Int>> {
        if (transactions.isEmpty()) return Result.success(emptyMap())
        return try {
            if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                return Result.failure(IllegalStateException("GEMINI_API_KEY missing"))
            }
            val validIds = categories.filter { !it.deleted }.map { it.id }.toSet()
            val merged = mutableMapOf<Int, Int>()
            transactions.chunked(CHUNK_SIZE).forEach { chunk ->
                val jsonText = withTimeout(TIMEOUT_MS) {
                    generateWithRetry(context, chunk, categories)
                }
                merged.putAll(parseResults(jsonText, validIds))
            }
            Result.success(merged)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Categorization failed: ${e.message?.take(160)}")
            runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            Result.failure(e)
        }
    }

    private val transientPattern = Regex(
        "503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket",
        RegexOption.IGNORE_CASE
    )

    private suspend fun generateWithRetry(
        context: Context,
        batch: List<Transaction>,
        categories: List<Category>
    ): String {
        // We send only merchant + amount (not date) to minimise data shared
        // with Google. Merchant is the dominant categorization signal;
        // amount occasionally nudges edge cases (e.g. small vs large charges
        // at mixed-purpose retailers). Date adds almost nothing for typical
        // consumer receipts and isn't worth the extra payload.
        val arr = JSONArray()
        batch.forEachIndexed { idx, t ->
            arr.put(JSONObject().apply {
                put("i", idx)
                put("merchant", t.source)
                put("amount", t.amount)
            })
        }
        val batchJson = JSONObject().put("transactions", arr).toString()
        val prompt = buildCategorizerPrompt(categories, batchJson)

        val maxAttempts = 3
        var lastErr: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return GeminiHttpClient.generate(
                    context = context,
                    modelName = MODEL_NAME,
                    prompt = prompt,
                    schema = schema,
                    imageBytes = null,
                    temperature = 0f
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastErr = e
                val transient = (e.message?.let { transientPattern.containsMatchIn(it) } ?: false) ||
                    (e is GeminiHttpClient.HttpError && (e.status == 429 || e.status in 500..599))
                if (!transient || attempt == maxAttempts) throw e
                Log.d(TAG, "Transient categorizer error attempt $attempt/$maxAttempts: ${e.message?.take(100)}")
                delay(500L shl (attempt - 1))
            }
        }
        throw lastErr ?: IllegalStateException("retry loop exited without result")
    }

    private fun parseResults(jsonText: String, validCategoryIds: Set<Int>): Map<Int, Int> {
        val obj = JSONObject(jsonText)
        val arr = obj.optJSONArray("results") ?: return emptyMap()
        val out = mutableMapOf<Int, Int>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val idx = entry.optInt("i", -1).takeIf { it >= 0 } ?: continue
            val catId = entry.optInt("categoryId", -1).takeIf { it > 0 && it in validCategoryIds } ?: continue
            out[idx] = catId
        }
        return out
    }
}

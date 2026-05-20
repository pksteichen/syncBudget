package com.techadvantage.budgetrak.data.ai

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.techadvantage.budgetrak.BuildConfig
import com.techadvantage.budgetrak.data.HelpChatMessage
import com.techadvantage.budgetrak.data.ocr.GeminiHttpClient
import com.techadvantage.budgetrak.data.telemetry.AnalyticsEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single-turn Help Chat → Gemini service. Mirrors the
 * [AiCategorizerService] pattern: raw HTTP via [GeminiHttpClient],
 * JSON-schema response, transient-error retry.
 *
 * The model returns `{ "reply": "...", "sentiment": <int 1-10> }`.
 * `reply` is the user-visible answer (off-topic refusals come back inside
 * `reply` per the system prompt, so the caller treats every successful
 * response uniformly). `sentiment` is a behind-the-scenes 1-10 score the
 * model assigns to the LATEST user message (1 = very negative,
 * 10 = very positive) — used internally for review-prompt gating
 * (HelpChatDialog) and stored as metadata in the Firestore log so we
 * can audit how well Gemini is judging tone.
 */
object HelpChatGeminiService {
    private const val TAG = "HelpChatGeminiService"
    private const val TIMEOUT_MS = 30_000L
    private const val MODEL_NAME = "gemini-2.5-flash-lite"
    private const val TEMPERATURE = 0.2f

    /** Default sentiment when the model omits the field or returns junk. */
    private const val NEUTRAL_SENTIMENT = 5

    /** A successful Help Chat turn: the assistant's user-visible reply
     *  plus the 1-10 sentiment score the model assigned to the user
     *  message that triggered this turn. */
    data class HelpChatReply(
        val text: String,
        val sentiment: Int,
    )

    private val schema: JSONObject = JSONObject()
        .put("type", "OBJECT")
        .put("description", "Help Chat assistant reply + sentiment score")
        .put("properties", JSONObject()
            .put("reply", JSONObject()
                .put("type", "STRING")
                .put("description", "Plain-text answer in the user's language"))
            .put("sentiment", JSONObject()
                .put("type", "INTEGER")
                .put("description", "Sentiment score for the LATEST user message: 1=very negative, 5=neutral, 10=very positive")))
        .put("required", JSONArray(listOf("reply", "sentiment")))

    private val transientPattern = Regex(
        "503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket",
        RegexOption.IGNORE_CASE
    )

    /**
     * Send one user message to Gemini and return the assistant's reply.
     * [history] is the full prior transcript including the just-appended
     * user message; we extract the latest one for the prompt and pass
     * the rest as conversation context.
     */
    suspend fun reply(
        context: Context,
        history: List<HelpChatMessage>,
        latestUserMessage: String,
    ): Result<HelpChatReply> {
        return try {
            if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                return Result.failure(IllegalStateException("GEMINI_API_KEY missing"))
            }
            val priorHistory = history.dropLast(1)  // drop the just-added user message
            val prompt = buildHelpChatPrompt(context, priorHistory, latestUserMessage)
            val jsonText = withTimeout(TIMEOUT_MS) {
                generateWithRetry(context, prompt)
            }
            val obj = JSONObject(jsonText)
            val replyText = obj.optString("reply").trim()
            if (replyText.isEmpty()) {
                return Result.failure(IllegalStateException("Empty reply field"))
            }
            // Parse + clamp sentiment defensively. The model is asked for
            // 1-10; we accept anything in that range and snap out-of-range
            // values to the boundary. Missing/non-integer → neutral.
            val rawSentiment = obj.opt("sentiment")
            val sentiment = when (rawSentiment) {
                is Number -> rawSentiment.toInt().coerceIn(1, 10)
                else -> NEUTRAL_SENTIMENT
            }
            Result.success(HelpChatReply(text = replyText, sentiment = sentiment))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Help Chat call failed: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
            }
            runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            Result.failure(e)
        }
    }

    private suspend fun generateWithRetry(context: Context, prompt: String): String {
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
                    temperature = TEMPERATURE,
                    usageCallback = { usage ->
                        AnalyticsEvents.logAiCallMetrics(
                            context = context,
                            feature = "help_chat",
                            model = MODEL_NAME,
                            usage = usage,
                        )
                    },
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastErr = e
                val transient = (e.message?.let { transientPattern.containsMatchIn(it) } ?: false) ||
                    (e is GeminiHttpClient.HttpError && (e.status == 429 || e.status in 500..599))
                if (!transient || attempt == maxAttempts) throw e
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Transient help-chat error attempt $attempt/$maxAttempts: ${e.message?.take(100)}")
                }
                delay(500L shl (attempt - 1))
            }
        }
        throw lastErr ?: IllegalStateException("retry loop exited without result")
    }
}

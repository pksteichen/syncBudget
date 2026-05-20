package com.techadvantage.budgetrak.data.ocr

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.techadvantage.budgetrak.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for Gemini generateContent. Replaces
 * com.google.ai.client.generativeai:0.9.0 so we can attach the
 * X-Android-Package / X-Android-Cert headers that Google's API
 * gateway requires for the Android-app API-key restriction to
 * actually validate calls — the standalone SDK does not send them.
 */
object GeminiHttpClient {

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Cached cert SHA-1 (uppercase hex, no colons). Read once at first OCR call. */
    @Volatile private var cachedCertSha1: String? = null

    /** Thrown for non-2xx HTTP responses — message preserves Google's error body for diagnosis. */
    class HttpError(val status: Int, body: String) : IOException("HTTP $status: ${body.take(500)}")

    /**
     * Per-call token usage from Gemini's response `usageMetadata` block.
     * `cachedTokens` is present (>0) when Google's implicit prompt cache
     * fired; zero / absent means a full-price input billing for the whole
     * prompt. Callers pipe this to [com.techadvantage.budgetrak.data.telemetry.AnalyticsEvents.logAiCallMetrics]
     * so the cache hit ratio is queryable in Firebase Analytics / BigQuery.
     */
    data class UsageMetadata(
        val promptTokens: Int,
        val cachedTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
    )

    /**
     * Generate JSON content from a prompt + optional image. Returns the
     * model's `text` field as a string (the JSON document matching the
     * schema). Throws on HTTP error or empty response.
     */
    suspend fun generate(
        context: Context,
        modelName: String,
        prompt: String,
        schema: JSONObject,
        imageBytes: ByteArray? = null,
        temperature: Float = 0f,
        usageCallback: (UsageMetadata) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val parts = JSONArray()
        if (imageBytes != null) {
            parts.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                })
            })
        }
        parts.put(JSONObject().put("text", prompt))

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
                put("temperature", temperature)
            })
        }.toString()

        val url = "$ENDPOINT/$modelName:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("X-Android-Package", context.packageName)
            .header("X-Android-Cert", certSha1(context))
            .post(body.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpError(response.code, responseBody)
            }
            // Surface usage to the caller BEFORE returning so an exception
            // in the analytics callback doesn't lose the text result. We
            // wrap the callback in a try/catch — telemetry must never fail
            // the call site.
            extractUsageMetadata(responseBody)?.let { usage ->
                try { usageCallback(usage) } catch (_: Exception) { /* swallow */ }
            }
            extractText(responseBody)
        }
    }

    /**
     * Parse the optional `usageMetadata` block from a Gemini response.
     * Returns null if the block is missing (older API, unusual response
     * shapes); the caller treats null as "no usage data, skip telemetry."
     */
    private fun extractUsageMetadata(responseBody: String): UsageMetadata? {
        val root = runCatching { JSONObject(responseBody) }.getOrNull() ?: return null
        val u = root.optJSONObject("usageMetadata") ?: return null
        val prompt = u.optInt("promptTokenCount", -1)
        val output = u.optInt("candidatesTokenCount", 0)
        val cached = u.optInt("cachedContentTokenCount", 0)
        val total = u.optInt("totalTokenCount", prompt + output)
        if (prompt < 0) return null
        return UsageMetadata(
            promptTokens = prompt,
            cachedTokens = cached,
            outputTokens = output,
            totalTokens = total,
        )
    }

    private fun extractText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Missing candidates in response")
        if (candidates.length() == 0) {
            throw IllegalStateException("Empty candidates array")
        }
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("Missing content.parts in candidate")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val text = parts.getJSONObject(i).optString("text")
            if (text.isNotEmpty()) sb.append(text)
        }
        if (sb.isEmpty()) throw IllegalStateException("Empty text in response parts")
        return sb.toString()
    }

    /**
     * SHA-1 of the app's signing certificate, uppercase hex, no colons.
     * Matches the format Google's API gateway expects in X-Android-Cert.
     */
    private fun certSha1(context: Context): String {
        cachedCertSha1?.let { return it }
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.let { si ->
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: throw IllegalStateException("Unable to read signing certificates")

        if (signatures.isEmpty()) throw IllegalStateException("No signing certificates found")
        val digest = MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray())
        val hex = digest.joinToString("") { "%02X".format(it) }
        cachedCertSha1 = hex
        return hex
    }
}

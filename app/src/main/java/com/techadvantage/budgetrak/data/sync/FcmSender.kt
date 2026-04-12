package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object FcmSender {
    private const val TAG = "FcmSender"
    private const val PROJECT_ID = "sync-23ce9"
    private const val FCM_URL = "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0L

    /** Send a data-only FCM message to wake up a device for debug upload. */
    var lastError: String? = null
        private set

    suspend fun sendDebugRequest(context: Context, targetFcmToken: String): Boolean {
        if (!com.techadvantage.budgetrak.BuildConfig.DEBUG) return false
        lastError = null
        return try {
            val accessToken = getAccessToken(context)
            val message = JSONObject().apply {
                put("message", JSONObject().apply {
                    put("token", targetFcmToken)
                    put("data", JSONObject().apply {
                        put("type", "debug_request")
                        put("timestamp", System.currentTimeMillis().toString())
                    })
                    // Android config: high priority to wake device
                    put("android", JSONObject().apply {
                        put("priority", "high")
                    })
                })
            }

            val conn = (URL(FCM_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            conn.outputStream.use { it.write(message.toString().toByteArray()) }
            val code = conn.responseCode
            if (code == 200) {
                Log.d(TAG, "FCM sent successfully")
                true
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                lastError = "HTTP $code: $error"
                Log.w(TAG, "FCM send failed ($code): $error")
                false
            }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "FCM send error: ${e.message}")
            false
        }
    }

    private fun getAccessToken(context: Context): String {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry - 60_000) {
            return cachedToken!!
        }

        // Read service account from assets
        val json = context.assets.open("service-account.json").bufferedReader().readText()
        val sa = JSONObject(json)
        val clientEmail = sa.getString("client_email")
        val privateKeyPem = sa.getString("private_key")

        // Create JWT
        val now = System.currentTimeMillis() / 1000
        val header = Base64.encodeToString(
            """{"alg":"RS256","typ":"JWT"}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val claims = Base64.encodeToString(JSONObject().apply {
            put("iss", clientEmail)
            put("scope", SCOPE)
            put("aud", TOKEN_URL)
            put("iat", now)
            put("exp", now + 3600)
        }.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val signInput = "$header.$claims"

        // Parse private key
        val keyPem = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.decode(keyPem, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val key = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        // Sign
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(signInput.toByteArray())
        }.sign()
        val sig = Base64.encodeToString(
            signature, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val jwt = "$signInput.$sig"

        // Exchange for access token
        val conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
        }
        val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
        conn.outputStream.use { it.write(body.toByteArray()) }

        val response = conn.inputStream.bufferedReader().readText()
        val tokenJson = JSONObject(response)
        cachedToken = tokenJson.getString("access_token")
        tokenExpiry = System.currentTimeMillis() + (tokenJson.optInt("expires_in", 3600) * 1000L)
        return cachedToken!!
    }
}

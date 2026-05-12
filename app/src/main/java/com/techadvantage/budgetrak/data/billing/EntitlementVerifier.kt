package com.techadvantage.budgetrak.data.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

private const val TAG = "EntitlementVerifier"
private const val PREFS = "entitlement_verifier"
private const val SERVER_CACHE_TTL_MS = 24L * 60 * 60 * 1000   // 24 h
private const val CALL_TIMEOUT_MS = 15_000L

/**
 * Outcome of [EntitlementVerifier.verify]. Three states matter to callers:
 *
 *  - [Verified]   — server confirmed entitlement (purchaseState=0 for INAPP,
 *                   or active/grace/canceled-with-future-expiry for SUBS).
 *                   The local flag should be true.
 *  - [Refunded]   — server gave a definitive negative (purchaseState=1,
 *                   expired sub, 410 GONE, 404 NOT_FOUND). The local flag
 *                   should be false even if the device's BillingClient
 *                   cache still says PURCHASED.
 *  - [Unreachable]— transient failure (network, App Check, timeout).
 *                   Caller falls back to the last cached server result if
 *                   it's still fresh (≤ [SERVER_CACHE_TTL_MS]); otherwise
 *                   treats local BillingClient state as the only signal.
 */
sealed class VerifyResult {
    data class Verified(val expiryTimeMillis: Long?, val orderId: String?) : VerifyResult()
    data class Refunded(val reason: String) : VerifyResult()
    data class Unreachable(val cause: String) : VerifyResult()
}

/**
 * Calls the `verifyPurchase` Gen 2 Cloud Function to obtain a
 * server-authoritative entitlement check. Closes the refund-lag window
 * where the device's local BillingClient cache hasn't yet learned that a
 * purchase was canceled or refunded — the function reads Google's
 * authoritative ledger via the Play Developer API.
 *
 * Results are cached per purchase token in SharedPreferences with a 24h
 * TTL so an offline / App-Check-degraded device can still trust the most
 * recent positive verification.
 */
class EntitlementVerifier(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("us-central1")
    }

    suspend fun verify(
        purchaseToken: String,
        productId: String,
        productType: ProductType
    ): VerifyResult {
        val payload = hashMapOf(
            "purchaseToken" to purchaseToken,
            "productId" to productId,
            "productType" to productType.wire
        )

        val raw = try {
            kotlinx.coroutines.withTimeoutOrNull(CALL_TIMEOUT_MS) {
                functions.getHttpsCallable("verifyPurchase").call(payload).await()
            } ?: run {
                Log.w(TAG, "verify($productId): timed out after ${CALL_TIMEOUT_MS}ms")
                return VerifyResult.Unreachable("timeout")
            }
        } catch (e: Exception) {
            Log.w(TAG, "verify($productId): call failed: ${e.message}")
            return VerifyResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }

        @Suppress("UNCHECKED_CAST")
        val data = raw.getData() as? Map<String, Any?>
            ?: return VerifyResult.Unreachable("no data field")

        val verified = data["verified"] as? Boolean ?: false
        val expiry = (data["expiryTimeMillis"] as? Number)?.toLong()
        val orderId = data["orderId"] as? String
        val reason = data["reason"] as? String

        val result: VerifyResult = if (verified) {
            VerifyResult.Verified(expiry, orderId)
        } else {
            VerifyResult.Refunded(reason ?: "purchaseState!=PURCHASED")
        }

        // Cache positive AND negative server results — both are authoritative.
        // Unreachable is the only state we don't cache.
        cache(purchaseToken, productType, verified, expiry, orderId, reason)
        return result
    }

    /**
     * Returns the most recent server verification for this purchase token,
     * if one is cached and still within the 24h TTL. Used by callers that
     * receive [VerifyResult.Unreachable] from a live call.
     */
    fun lastServerVerification(purchaseToken: String): CachedVerification? {
        val key = cacheKey(purchaseToken)
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val ts = json.optLong("ts", 0L)
            if (ts == 0L) return null
            if (System.currentTimeMillis() - ts > SERVER_CACHE_TTL_MS) return null
            CachedVerification(
                verified = json.optBoolean("verified", false),
                expiryTimeMillis = if (json.isNull("expiryTimeMillis")) null
                    else json.optLong("expiryTimeMillis"),
                orderId = json.optString("orderId").takeIf { it.isNotEmpty() },
                reason = json.optString("reason").takeIf { it.isNotEmpty() },
                timestampMillis = ts
            )
        } catch (e: Exception) {
            Log.w(TAG, "lastServerVerification: corrupt cache for $key: ${e.message}")
            null
        }
    }

    private fun cache(
        purchaseToken: String,
        productType: ProductType,
        verified: Boolean,
        expiry: Long?,
        orderId: String?,
        reason: String?
    ) {
        val json = JSONObject().apply {
            put("verified", verified)
            put("productType", productType.wire)
            put("expiryTimeMillis", expiry ?: JSONObject.NULL)
            put("orderId", orderId ?: "")
            put("reason", reason ?: "")
            put("ts", System.currentTimeMillis())
        }
        prefs.edit().putString(cacheKey(purchaseToken), json.toString()).apply()
    }

    private fun cacheKey(purchaseToken: String): String {
        // Tokens can be very long; hash to keep prefs key length sane and
        // avoid accidental key-collision quirks of underlying XML store.
        val sb = StringBuilder("v_")
        val h = purchaseToken.hashCode().toString(16)
        sb.append(h)
        return sb.toString()
    }

    enum class ProductType(val wire: String) {
        INAPP("inapp"),
        SUBS("subs")
    }

    data class CachedVerification(
        val verified: Boolean,
        val expiryTimeMillis: Long?,
        val orderId: String?,
        val reason: String?,
        val timestampMillis: Long
    )
}

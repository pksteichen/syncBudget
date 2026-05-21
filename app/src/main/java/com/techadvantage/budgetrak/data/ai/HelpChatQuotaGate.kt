package com.techadvantage.budgetrak.data.ai

import android.content.Context
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.techadvantage.budgetrak.BuildConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Project-wide daily-ceiling gate for Help Chat Gemini calls. Talks to
 * the `checkChatQuota` Cloud Function (Gen 2 callable, App Check
 * enforced). The function holds a Firestore counter that resets at
 * 00:00 UTC; if the day's total exceeds the configured ceiling
 * (`quotaConfig/helpChat.dailyCeiling`, default 10,000), the gate
 * returns [Decision.Denied] and the caller surfaces a static refusal
 * to the user.
 *
 * **Fail-open by design.** Anything that goes wrong on the wire — the
 * function being down, App Check timing out, the network blip — must
 * NOT brick the chat. The in-app per-device daily caps still apply.
 * The cost ceiling is defense-in-depth: it bounds runaway-cost
 * scenarios (tampered clients, extracted API keys, future bugs,
 * coordinated emulator-farm abuse) that the in-app caps can't see,
 * but on its own it isn't load-bearing for normal operation.
 */
object HelpChatQuotaGate {
    private const val TAG = "HelpChatQuotaGate"
    private const val FUNCTION_NAME = "checkChatQuota"
    private const val CALL_TIMEOUT_MS = 4_000L

    /** Result of the gate check. */
    sealed class Decision {
        /** Server permits this call. [count] is the post-increment value. */
        data class Allowed(val count: Int, val ceiling: Int) : Decision()

        /** Server hit its daily ceiling. Caller MUST NOT call Gemini. */
        data class Denied(
            val count: Int,
            val ceiling: Int,
            val retryAfterSeconds: Long,
        ) : Decision()
    }

    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("us-central1")
    }

    /**
     * Check (and atomically increment, if allowed) the daily counter
     * for [feature]. On any wire-level failure — timeout, function
     * error, App Check failure — returns [Decision.Allowed] with
     * sentinel `count = -1` so the caller's flow continues unchanged.
     * Fail-open is intentional; see class-level doc.
     */
    suspend fun check(feature: String): Decision {
        val payload = hashMapOf<String, Any>("feature" to feature)
        val raw = try {
            withTimeoutOrNull(CALL_TIMEOUT_MS) {
                functions.getHttpsCallable(FUNCTION_NAME).call(payload).await()
            } ?: run {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "check($feature): timed out after ${CALL_TIMEOUT_MS}ms — failing open")
                }
                return Decision.Allowed(count = -1, ceiling = -1)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "check($feature) call failed: ${e.javaClass.simpleName}: ${e.message?.take(120)} — failing open")
            }
            return Decision.Allowed(count = -1, ceiling = -1)
        }

        @Suppress("UNCHECKED_CAST")
        val data = raw.getData() as? Map<String, Any?>
        if (data == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "check($feature): response missing data field — failing open")
            }
            return Decision.Allowed(count = -1, ceiling = -1)
        }

        val allowed = data["allowed"] as? Boolean ?: true  // fail-open if field missing
        val count = (data["count"] as? Number)?.toInt() ?: -1
        val ceiling = (data["ceiling"] as? Number)?.toInt() ?: -1

        return if (allowed) {
            Decision.Allowed(count = count, ceiling = ceiling)
        } else {
            val retryAfter = (data["retryAfterSeconds"] as? Number)?.toLong() ?: 0L
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "check($feature): server denied at $count/$ceiling, retry in ${retryAfter}s")
            }
            Decision.Denied(
                count = count,
                ceiling = ceiling,
                retryAfterSeconds = retryAfter,
            )
        }
    }

    /** Caller-visible feature labels. Match the Cloud Function's enum. */
    object Features {
        const val HELP_CHAT = "help_chat"
        // OCR + CSV gates can be added later by extending the function's
        // QUOTA_DEFAULT_CEILING map and calling check() from those services.
    }
}

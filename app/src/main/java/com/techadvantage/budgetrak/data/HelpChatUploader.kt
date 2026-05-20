package com.techadvantage.budgetrak.data

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.techadvantage.budgetrak.BuildConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pushes the local Help Chat transcript to Firestore for periodic
 * accuracy/abuse review. Docs are keyed by the anonymous [HelpChatStore]
 * chatId; per-user binding is intentionally NOT recorded (a Firestore TTL
 * policy on `lastUpdated` deletes docs after 7 days).
 *
 * Two entry points:
 *   - [uploadIfStale] — implicit, called on dialog dismiss. Skips when
 *     nothing has changed since the last upload OR the previous upload
 *     happened less than [MIN_UPLOAD_INTERVAL_MS] ago.
 *   - [uploadNow] — explicit, called by the Clear button. Bypasses the
 *     debounce so the user's final transcript lands before the local
 *     buffer is wiped.
 *
 * All failure paths return false silently in release builds; debug builds
 * log via [Log.w]. Solo users (no Firebase Auth user) silently skip — they
 * can still chat, their transcripts just stay local.
 */
object HelpChatUploader {
    private const val TAG = "HelpChatUploader"
    private const val COLLECTION = "helpChatLogs"
    private const val MIN_UPLOAD_INTERVAL_MS = 5L * 60 * 1000  // 5 minutes
    private const val UPLOAD_TIMEOUT_MS = 15_000L              // 15 seconds
    private const val TTL_DAYS_MS = 7L * 24 * 60 * 60 * 1000   // 7 days

    /**
     * Uploads if the local buffer has new content since the last successful
     * upload AND the previous upload was more than 5 minutes ago. Returns
     * true if the write was attempted and acknowledged; false on skip or
     * failure.
     */
    suspend fun uploadIfStale(context: Context): Boolean {
        val chatId = HelpChatStore.chatId.value ?: return false
        val messages = HelpChatStore.messages.value
        if (messages.isEmpty()) return false
        if (!HelpChatStore.isDirty()) return false
        val sinceLast = System.currentTimeMillis() - HelpChatStore.lastUploadAt.value
        if (sinceLast < MIN_UPLOAD_INTERVAL_MS) return false
        return doUpload(context, chatId, messages, markStoreOnSuccess = true)
    }

    /**
     * Uploads unconditionally. Used by Clear, where the caller wipes the
     * local buffer immediately after — we don't update the store's
     * upload-tracking state because the chatId is about to be cleared.
     */
    suspend fun uploadNow(
        context: Context,
        chatId: String,
        messages: List<HelpChatMessage>,
    ): Boolean {
        if (messages.isEmpty()) return false
        return doUpload(context, chatId, messages, markStoreOnSuccess = false)
    }

    private suspend fun doUpload(
        context: Context,
        chatId: String,
        messages: List<HelpChatMessage>,
        markStoreOnSuccess: Boolean,
    ): Boolean {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            // Solo user — sign in anonymously so the Firestore rule
            // (`request.auth != null`) is satisfied. The anonymous UID
            // persists across chat sessions on this device and is NOT
            // stored alongside the chat doc, preserving anonymity. Fails
            // silently (network down, App Check rejected) — in that case
            // the chat stays purely local.
            val signedIn = runCatching {
                withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    auth.signInAnonymously().await()
                    true
                } ?: false
            }.getOrElse { e ->
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Anonymous sign-in failed: ${e.javaClass.simpleName}: ${e.message}")
                }
                false
            }
            if (!signedIn) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Skip upload chatId=$chatId — anonymous sign-in did not complete")
                }
                return false
            }
        }
        // `expireAt` is the field Firestore's TTL policy watches — it
        // contains the moment the doc becomes eligible for deletion (now +
        // 7 days). Recomputed on every upload so an active chat keeps
        // refreshing its window. `lastUpdated` is the server-side wall
        // clock kept for human-readable diagnostics; not TTL-driving.
        val expireAt = Timestamp(java.util.Date(System.currentTimeMillis() + TTL_DAYS_MS))
        val payload = mapOf(
            // For bot messages with a sentiment score, prefix the text
            // field with `[N] ` so at-a-glance Firestore console review
            // shows the score next to the reply it produced. Also keep
            // the score as a separate field (`s`) for queryability in
            // BigQuery — "show me all 9-10 turns from the last week"
            // is exactly the kind of audit we want. The "r" field flags
            // the Play Store review-prompt bot messages so we can
            // exclude them from sentiment analysis if needed.
            "messages" to messages.map { m ->
                val displayText = if (!m.fromUser && m.sentiment != null) {
                    "[${m.sentiment}] ${m.text}"
                } else {
                    m.text
                }
                buildMap<String, Any> {
                    put("t", m.timestamp)
                    put("u", m.fromUser)
                    put("x", displayText)
                    m.sentiment?.let { put("s", it) }
                    if (m.isReviewPrompt) put("r", true)
                }
            },
            "messageCount" to messages.size,
            "lastUpdated" to FieldValue.serverTimestamp(),
            "expireAt" to expireAt,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "locale" to Locale.getDefault().toLanguageTag(),
        )
        val attempt = runCatching {
            withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                FirebaseFirestore.getInstance()
                    .collection(COLLECTION)
                    .document(chatId)
                    .set(payload, SetOptions.merge())
                    .await()
                true
            } ?: run {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Upload chatId=$chatId timed out after ${UPLOAD_TIMEOUT_MS}ms")
                }
                false
            }
        }.getOrElse { e ->
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Upload chatId=$chatId failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            false
        }
        if (attempt && markStoreOnSuccess) {
            HelpChatStore.markUploaded(context)
        }
        return attempt
    }
}

package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FcmService"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token")
        // Store token for next sync cycle to push to Firestore
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit().putString("fcm_token", token)
            .putBoolean("token_needs_upload", true).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        Log.d(TAG, "FCM message received: type=$type")
        if (type == "debug_request") {
            if (!com.syncbudget.app.BuildConfig.DEBUG) return  // Ignore debug requests in release
            // Wake up and trigger immediate sync with debug upload
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fcmPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                    // Mark that a debug request is pending so the next sync uploads files
                    fcmPrefs.edit().putBoolean("fcm_debug_requested", true).apply()
                    // Trigger an immediate one-shot debug dump upload
                    val request = androidx.work.OneTimeWorkRequestBuilder<DebugDumpWorker>()
                        .build()
                    androidx.work.WorkManager.getInstance(applicationContext)
                        .enqueue(request)
                    Log.d(TAG, "Triggered one-shot DebugDumpWorker")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to trigger debug sync: ${e.message}")
                }
            }
        }
    }
}

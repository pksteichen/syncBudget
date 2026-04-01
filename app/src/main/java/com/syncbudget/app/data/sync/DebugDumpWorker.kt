package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.tasks.await

/**
 * One-shot worker that uploads diagnostic files to Firestore when triggered
 * by an FCM debug_request push. Debug builds only — FcmService ignores
 * debug_request messages in release builds.
 */
class DebugDumpWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Ensure Firebase anonymous auth
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .signInAnonymously()
                        .await()
                } catch (e: Exception) {
                    android.util.Log.w("DebugDumpWorker", "Anonymous auth failed: ${e.message}")
                    return Result.retry()
                }
            }

            val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
            val fcmPrefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)

            if (!fcmPrefs.getBoolean("fcm_debug_requested", false)) {
                return Result.success() // Nothing to do
            }

            try {
                val groupId = syncPrefs.getString("groupId", null)
                val deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)
                val devName = GroupManager.getDeviceName(applicationContext)
                val keyBase64 = SecurePrefs.get(applicationContext).getString("encryptionKey", null)
                    ?: syncPrefs.getString("encryptionKey", null)
                if (groupId != null && keyBase64 != null) {
                    val key = Base64.decode(keyBase64, Base64.NO_WRAP)
                    val supportDir = com.syncbudget.app.data.BackupManager.getSupportDir()
                    val diagText = try {
                        val fresh = com.syncbudget.app.data.DiagDumpBuilder.build(applicationContext)
                        java.io.File(supportDir, "sync_diag.txt").writeText(fresh)
                        val diagDevName = devName.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
                        if (diagDevName.isNotEmpty()) {
                            java.io.File(supportDir, "sync_diag_${diagDevName}.txt").writeText(fresh)
                        }
                        fresh
                    } catch (e: Exception) {
                        android.util.Log.w("DebugDumpWorker", "Fresh dump failed: ${e.message}")
                        try {
                            java.io.File(supportDir, "sync_diag.txt").readText()
                        } catch (_: Exception) { "(no diag file)" }
                    }
                    val syncLogText = try {
                        java.io.File(supportDir, "native_sync_log.txt").readText()
                    } catch (_: Exception) { "" }
                    FirestoreService.uploadDebugFiles(groupId, deviceId, devName, syncLogText, diagText, key)
                }
            } catch (e: Exception) {
                android.util.Log.e("DebugDumpWorker", "Debug upload failed: ${e.message}")
            }
            fcmPrefs.edit().putBoolean("fcm_debug_requested", false).apply()

            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DebugDumpWorker", "Failed: ${e.message}", e)
            return Result.success()
        }
    }
}

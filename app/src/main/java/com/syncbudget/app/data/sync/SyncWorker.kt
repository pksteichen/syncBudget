package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Background worker for sync housekeeping.
 *
 * With Firestore-native sync, the foreground FirestoreDocSync handles all
 * real-time data sync via snapshot listeners. This worker only needs to:
 *   1. Ensure Firebase anonymous auth
 *   2. Handle FCM debug dump requests (upload diag files to Firestore)
 *   3. Update the widget periodically
 */
class SyncWorker(
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
                    android.util.Log.w("SyncWorker", "Anonymous auth failed: ${e.message}")
                    return Result.retry()
                }
            }

            val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
            val fcmPrefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)

            // Handle FCM debug dump requests
            if (fcmPrefs.getBoolean("fcm_debug_requested", false)) {
                try {
                    val groupId = syncPrefs.getString("groupId", null)
                    val deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)
                    val devName = GroupManager.getDeviceName(applicationContext)
                    val keyBase64 = SecurePrefs.get(applicationContext).getString("encryptionKey", null)
                        ?: syncPrefs.getString("encryptionKey", null)
                    if (groupId != null && keyBase64 != null) {
                        val key = Base64.decode(keyBase64, Base64.NO_WRAP)
                        val supportDir = com.syncbudget.app.data.BackupManager.getSupportDir()
                        val syncLogText = try {
                            java.io.File(supportDir, "native_sync_log.txt").readText()
                        } catch (_: Exception) { "" }
                        val diagText = try {
                            java.io.File(supportDir, "sync_diag.txt").readText()
                        } catch (_: Exception) { "(no diag file)" }
                        FirestoreService.uploadDebugFiles(groupId, deviceId, devName, syncLogText, diagText, key)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SyncWorker", "Debug upload failed: ${e.message}")
                }
                fcmPrefs.edit().putBoolean("fcm_debug_requested", false).apply()
            }

            // Update device metadata + integrity fingerprint (even when app is backgrounded)
            val groupId = syncPrefs.getString("groupId", null)
            val deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)
            if (groupId != null) {
                try {
                    // Load current data for record counts
                    val txnCount = com.syncbudget.app.data.TransactionRepository.load(applicationContext).count { !it.deleted }
                    val reCount = com.syncbudget.app.data.RecurringExpenseRepository.load(applicationContext).count { !it.deleted }
                    val isCount = com.syncbudget.app.data.IncomeSourceRepository.load(applicationContext).count { !it.deleted }
                    val sgCount = com.syncbudget.app.data.SavingsGoalRepository.load(applicationContext).count { !it.deleted }
                    val aeCount = com.syncbudget.app.data.AmortizationRepository.load(applicationContext).count { !it.deleted }
                    val catCount = com.syncbudget.app.data.CategoryRepository.load(applicationContext).count { !it.deleted }
                    val pleCount = PeriodLedgerRepository.load(applicationContext).size
                    val cash = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .let { p -> p.getString("availableCash", null)?.toDoubleOrNull() ?: 0.0 }

                    val fingerprint = org.json.JSONObject().apply {
                        put("txnCount", txnCount)
                        put("reCount", reCount)
                        put("isCount", isCount)
                        put("sgCount", sgCount)
                        put("aeCount", aeCount)
                        put("catCount", catCount)
                        put("pleCount", pleCount)
                        put("cash", cash)
                    }.toString()

                    FirestoreService.updateDeviceMetadata(
                        groupId, deviceId,
                        syncVersion = 0L,
                        fingerprintJson = fingerprint,
                        appSyncVersion = 2,
                        minSyncVersion = 2
                    )
                } catch (e: Exception) {
                    android.util.Log.w("SyncWorker", "Metadata update failed: ${e.message}")
                }
            }

            // Update widget
            com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(applicationContext)

            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Background sync failed: ${e.message}", e)
            return Result.success() // don't retry, wait for next scheduled run
        }
    }

    companion object {
        private const val WORK_NAME = "sync_budget_background_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

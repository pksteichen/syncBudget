package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FcmService"
        // Reserve 1.5s out of the FCM 10s window for service startup +
        // teardown when we MUST block the FCM thread (Tier 3 / VM dead).
        // Tier 2 / VM alive runs async on `BudgeTrakApplication.processScope`
        // so this budget doesn't apply there.
        private const val INLINE_BUDGET_MS = 8_500L
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token")
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit().putString("fcm_token", token)
            .putBoolean("token_needs_upload", true).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        Log.d(TAG, "FCM message received: type=$type")
        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("FCM received: type=$type")
        when (type) {
            "debug_request" -> handleDebugRequest()
            "sync_push", "heartbeat" -> handleWakeForSync(type)
        }
    }

    /**
     * Run the sync pipeline in response to an FCM wake. Branch by VM
     * lifecycle:
     *
     *  - **VM alive** (Tier 2 path) — launch on
     *    `BudgeTrakApplication.processScope` and return from the FCM
     *    thread immediately. The ViewModel's existence keeps the process
     *    alive past `onMessageReceived` returning, so the work completes
     *    naturally with no artificial budget. Long operations (snapshot
     *    builds, multi-photo upload bursts, App Check refresh on a cold
     *    cellular connection) all get to run to completion. No WM
     *    fallback needed because we trust the process to stay alive.
     *
     *  - **VM dead** (Tier 3 path) — block the FCM thread with
     *    `runBlocking { runFullSyncInline(..., INLINE_BUDGET_MS) }`. The
     *    blocking is load-bearing: it's the only thing keeping the
     *    process alive while WorkManager and the inline body do their
     *    work in-process. Cap at 8.5s so `onMessageReceived` returns
     *    before the OS 10s kill. WM `runOnce` fallback fires on timeout
     *    so unfinished work picks up later.
     *
     * The split avoids the Phase 1 regression where Tier 2 was
     * inadvertently capped (long Tier 2 work used to finish via
     * WorkManager's service binding pinning the process; the inline
     * refactor removed WM from the FCM path entirely).
     */
    private fun handleWakeForSync(type: String) {
        val vm = com.techadvantage.budgetrak.MainViewModel.instance?.get()
        if (vm != null) {
            com.techadvantage.budgetrak.BudgeTrakApplication.processScope.launch {
                try {
                    BackgroundSyncWorker.runFullSyncInline(
                        applicationContext, "FCM-$type", timeBudgetMs = null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Async Tier 2 inline failed: ${e.message}")
                }
            }
            return
        }
        kotlinx.coroutines.runBlocking {
            val ok = try {
                BackgroundSyncWorker.runFullSyncInline(
                    applicationContext, "FCM-$type", INLINE_BUDGET_MS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Inline sync failed: ${e.message}")
                false
            }
            if (!ok) {
                try {
                    BackgroundSyncWorker.runOnce(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enqueue fallback worker: ${e.message}")
                }
            }
        }
    }

    /**
     * Debug-build-only: trigger an immediate dump upload for remote
     * diagnostics. Same VM-alive vs VM-dead branch as `handleWakeForSync`:
     * async on `processScope` when VM keeps the process alive (no budget
     * needed for the dump's diag build + logcat capture + Storage upload),
     * blocking + budget-capped + WM fallback when VM is dead.
     */
    private fun handleDebugRequest() {
        if (!com.techadvantage.budgetrak.BuildConfig.DEBUG) return
        val vm = com.techadvantage.budgetrak.MainViewModel.instance?.get()
        if (vm != null) {
            com.techadvantage.budgetrak.BudgeTrakApplication.processScope.launch {
                try {
                    BackgroundSyncWorker.runDebugDumpInline(
                        applicationContext, timeBudgetMs = null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Async dump failed: ${e.message}")
                }
            }
            return
        }
        kotlinx.coroutines.runBlocking {
            val ok = try {
                BackgroundSyncWorker.runDebugDumpInline(
                    applicationContext, INLINE_BUDGET_MS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Inline dump failed: ${e.message}")
                false
            }
            if (!ok) {
                try {
                    val request = androidx.work.OneTimeWorkRequestBuilder<DebugDumpWorker>().build()
                    androidx.work.WorkManager.getInstance(applicationContext).enqueue(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enqueue fallback dump: ${e.message}")
                }
            }
        }
    }
}

package com.techadvantage.budgetrak.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Fallback worker for FCM `debug_request` uploads. The primary path is
 * `BackgroundSyncWorker.runDebugDumpInline`, called directly from
 * FcmService so the dump completes within the FCM 10s runtime budget.
 * This worker is enqueued only when that inline path returns false
 * (budget expired before upload completed).
 *
 * Debug builds only — release FcmService never calls handleDebugRequest.
 */
class DebugDumpWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            BackgroundSyncWorker.runDebugDumpInline(applicationContext, timeBudgetMs = null)
        } catch (e: Exception) {
            android.util.Log.e("DebugDumpWorker", "Failed: ${e.message}", e)
        }
        return Result.success()
    }
}

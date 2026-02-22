package com.syncbudget.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.TransactionRepository
import android.util.Base64
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val groupId = syncPrefs.getString("groupId", null) ?: return Result.success()
        val keyBase64 = syncPrefs.getString("encryptionKey", null) ?: return Result.success()

        // Check if app is in foreground — skip to avoid race
        val isInForeground = syncPrefs.getBoolean("isInForeground", false)
        if (isInForeground) return Result.success()

        val encryptionKey = Base64.decode(keyBase64, Base64.NO_WRAP)
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)
        val lamportClock = LamportClock(applicationContext)

        val engine = SyncEngine(applicationContext, groupId, deviceId, encryptionKey, lamportClock)

        // Load current data from JSON files
        val transactions = TransactionRepository.load(applicationContext)
        val recurringExpenses = RecurringExpenseRepository.load(applicationContext)
        val incomeSources = IncomeSourceRepository.load(applicationContext)
        val savingsGoals = SavingsGoalRepository.load(applicationContext)
        val amortizationEntries = AmortizationRepository.load(applicationContext)
        val categories = CategoryRepository.load(applicationContext)
        val sharedSettings = SharedSettingsRepository.load(applicationContext)

        val result = engine.sync(
            transactions, recurringExpenses, incomeSources,
            savingsGoals, amortizationEntries, categories,
            sharedSettings
        )

        if (result.success) {
            // Save merged data back to JSON files
            result.mergedTransactions?.let { TransactionRepository.save(applicationContext, it) }
            result.mergedRecurringExpenses?.let { RecurringExpenseRepository.save(applicationContext, it) }
            result.mergedIncomeSources?.let { IncomeSourceRepository.save(applicationContext, it) }
            result.mergedSavingsGoals?.let { SavingsGoalRepository.save(applicationContext, it) }
            result.mergedAmortizationEntries?.let { AmortizationRepository.save(applicationContext, it) }
            result.mergedCategories?.let { CategoryRepository.save(applicationContext, it) }
            result.mergedSharedSettings?.let { SharedSettingsRepository.save(applicationContext, it) }
            return Result.success()
        }

        return Result.retry()
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

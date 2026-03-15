package com.syncbudget.app.widget

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.IncomeMode
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.getDoubleCompat
import com.syncbudget.app.data.sync.LamportClock
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import com.syncbudget.app.data.sync.SecurePrefs
import com.syncbudget.app.data.sync.SyncEngine
import com.syncbudget.app.data.sync.SyncFileLock
import com.syncbudget.app.data.sync.SyncIdGenerator
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that pulls remote changes and recomputes availableCash.
 * Runs every 30 minutes even when the app is not open, ensuring the
 * widget reflects transactions from other devices and period resets.
 */
class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Pull remote changes if device is in a sync group
            pullRemoteChanges()

            // Recompute cash from (possibly updated) local data
            recomputeAndUpdateWidget()

            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("WidgetRefresh", "Refresh failed: ${e.message}")
            Result.success()  // don't retry, wait for next scheduled run
        }
    }

    private suspend fun pullRemoteChanges() {
        val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val groupId = syncPrefs.getString("groupId", null) ?: return

        // Skip if the foreground sync loop synced recently (within 5 min)
        val lastSync = syncPrefs.getLong("lastSuccessfulSync", 0L)
        if (System.currentTimeMillis() - lastSync < 5 * 60 * 1000L) return

        val keyBase64 = SecurePrefs.get(applicationContext).getString("encryptionKey", null)
            ?: syncPrefs.getString("encryptionKey", null)
            ?: return

        // Acquire lock to avoid conflicting with foreground sync
        val fileLock = SyncFileLock(applicationContext)
        if (!fileLock.tryLock()) return  // foreground sync is running, skip
        try {
            val encryptionKey = Base64.decode(keyBase64, Base64.NO_WRAP)
            val deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)
            val lamportClock = LamportClock(applicationContext)
            val engine = SyncEngine(applicationContext, groupId, deviceId, encryptionKey, lamportClock)

            val transactions = TransactionRepository.load(applicationContext)
            val recurringExpenses = RecurringExpenseRepository.load(applicationContext)
            val incomeSources = IncomeSourceRepository.load(applicationContext)
            val savingsGoals = SavingsGoalRepository.load(applicationContext)
            val amortizationEntries = AmortizationRepository.load(applicationContext)
            val categories = CategoryRepository.load(applicationContext)
            val sharedSettings = SharedSettingsRepository.load(applicationContext)
            val periodLedger = PeriodLedgerRepository.load(applicationContext)

            val remapJson = syncPrefs.getString("catIdRemap", null)
            val existingRemap = if (remapJson != null) {
                try {
                    val json = org.json.JSONObject(remapJson)
                    json.keys().asSequence().associate { it.toInt() to json.getInt(it) }
                } catch (_: Exception) { emptyMap() }
            } else emptyMap<Int, Int>()

            val result = engine.sync(
                transactions, recurringExpenses, incomeSources,
                savingsGoals, amortizationEntries, categories,
                sharedSettings,
                existingCatIdRemap = existingRemap,
                periodLedgerEntries = periodLedger
            )

            if (result.success) {
                result.mergedTransactions?.let { TransactionRepository.save(applicationContext, it) }
                result.mergedRecurringExpenses?.let { RecurringExpenseRepository.save(applicationContext, it) }
                result.mergedIncomeSources?.let { IncomeSourceRepository.save(applicationContext, it) }
                result.mergedSavingsGoals?.let { SavingsGoalRepository.save(applicationContext, it) }
                result.mergedAmortizationEntries?.let { AmortizationRepository.save(applicationContext, it) }
                result.mergedCategories?.let { CategoryRepository.save(applicationContext, it) }
                result.mergedPeriodLedgerEntries?.let { PeriodLedgerRepository.save(applicationContext, it) }
                result.mergedSharedSettings?.let { merged ->
                    SharedSettingsRepository.save(applicationContext, merged)
                    applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                        .putString("currencySymbol", merged.currency)
                        .putString("budgetPeriod", merged.budgetPeriod)
                        .putInt("resetHour", merged.resetHour)
                        .putInt("resetDayOfWeek", merged.resetDayOfWeek)
                        .putInt("resetDayOfMonth", merged.resetDayOfMonth)
                        .putBoolean("isManualBudgetEnabled", merged.isManualBudgetEnabled)
                        .putString("manualBudgetAmount", merged.manualBudgetAmount.toString())
                        .apply()
                }
                result.catIdRemap?.let { remap ->
                    val json = org.json.JSONObject(remap.mapKeys { it.key.toString() })
                    syncPrefs.edit().putString("catIdRemap", json.toString()).apply()
                }
                syncPrefs.edit().putBoolean("syncDirty", false).apply()
            }
        } finally {
            fileLock.unlock()
        }
    }

    private fun recomputeAndUpdateWidget() {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val budgetStartDate = prefs.getString("budgetStartDate", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: return

        val periodLedger = PeriodLedgerRepository.load(applicationContext)
        val transactions = TransactionRepository.load(applicationContext)
        val recurringExpenses = RecurringExpenseRepository.load(applicationContext)
        val incomeSources = IncomeSourceRepository.load(applicationContext)

        val incomeMode = try {
            IncomeMode.valueOf(prefs.getString("incomeMode", null) ?: "FIXED")
        } catch (_: Exception) { IncomeMode.FIXED }

        val activeTxns = transactions.filter { !it.deleted }
        val activeRE = recurringExpenses.filter { !it.deleted }
        val activeIS = incomeSources.filter { !it.deleted }

        val cash = BudgetCalculator.recomputeAvailableCash(
            budgetStartDate, periodLedger, activeTxns, activeRE,
            incomeMode, activeIS
        )

        val currentCash = prefs.getDoubleCompat("availableCash")
        if (cash != currentCash) {
            prefs.edit().putString("availableCash", cash.toString()).apply()
        }

        BudgetWidgetProvider.updateAllWidgets(applicationContext)
    }

    companion object {
        private const val WORK_NAME = "widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Trigger an immediate one-shot refresh (e.g., at budget reset time). */
        fun runOnce(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

package com.syncbudget.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.BudgetCalculator
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.TransactionRepository
import com.syncbudget.app.data.TransactionType
import android.util.Base64
import java.time.LocalDate
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // File-based lock works across processes (unlike ReentrantLock)
        val fileLock = SyncFileLock(applicationContext)
        if (!fileLock.tryLock()) return Result.success()
        try {
            return doSyncWork()
        } finally {
            fileLock.unlock()
        }
    }

    private suspend fun doSyncWork(): Result {
        val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val groupId = syncPrefs.getString("groupId", null) ?: return Result.success()
        // TODO(security): Move encryption key to EncryptedSharedPreferences
        val keyBase64 = syncPrefs.getString("encryptionKey", null) ?: return Result.success()

        val encryptionKey = Base64.decode(keyBase64, Base64.NO_WRAP)
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(applicationContext)
        val lamportClock = LamportClock(applicationContext)

        val engine = SyncEngine(applicationContext, groupId, deviceId, encryptionKey, lamportClock)

        // Load current data from JSON files
        var transactions = TransactionRepository.load(applicationContext)
        var recurringExpenses = RecurringExpenseRepository.load(applicationContext)
        var incomeSources = IncomeSourceRepository.load(applicationContext)
        val savingsGoals = SavingsGoalRepository.load(applicationContext)
        var amortizationEntries = AmortizationRepository.load(applicationContext)
        var categories = CategoryRepository.load(applicationContext)
        val sharedSettings = SharedSettingsRepository.load(applicationContext)

        // One-time migration: stamp tag_clock on categories with tags but tag_clock=0
        if (!syncPrefs.getBoolean("migration_tag_clock_done", false)) {
            var stamped = false
            val migClock = lamportClock.tick()
            categories = categories.mapIndexed { _, c ->
                if (c.tag.isNotEmpty() && c.tag_clock == 0L) {
                    stamped = true
                    c.copy(tag_clock = migClock)
                } else c
            }
            if (stamped) CategoryRepository.save(applicationContext, categories)
            syncPrefs.edit().putBoolean("migration_tag_clock_done", true).apply()
        }

        // One-time migration: stamp description_clock on records with description_clock=0
        if (!syncPrefs.getBoolean("migration_description_clock_done", false)) {
            val migClock = lamportClock.tick()
            var changed = false
            transactions = transactions.map { t ->
                if (t.description_clock == 0L) { changed = true; t.copy(description_clock = migClock) } else t
            }
            recurringExpenses = recurringExpenses.map { r ->
                if (r.description_clock == 0L) { changed = true; r.copy(description_clock = migClock) } else r
            }
            incomeSources = incomeSources.map { s ->
                if (s.description_clock == 0L) { changed = true; s.copy(description_clock = migClock) } else s
            }
            amortizationEntries = amortizationEntries.map { e ->
                if (e.description_clock == 0L) { changed = true; e.copy(description_clock = migClock) } else e
            }
            if (changed) {
                TransactionRepository.save(applicationContext, transactions)
                RecurringExpenseRepository.save(applicationContext, recurringExpenses)
                IncomeSourceRepository.save(applicationContext, incomeSources)
                AmortizationRepository.save(applicationContext, amortizationEntries)
            }
            syncPrefs.edit().putBoolean("migration_description_clock_done", true).apply()
        }

        // Load persisted category ID remap
        val remapJson = syncPrefs.getString("catIdRemap", null)
        val existingRemap = if (remapJson != null) {
            try {
                val json = JSONObject(remapJson)
                json.keys().asSequence().associate { it.toInt() to json.getInt(it) }
            } catch (_: Exception) { emptyMap() }
        } else emptyMap<Int, Int>()

        val result = engine.sync(
            transactions, recurringExpenses, incomeSources,
            savingsGoals, amortizationEntries, categories,
            sharedSettings,
            existingCatIdRemap = existingRemap
        )

        if (result.success) {
            // Capture pre-merge transaction IDs for cash adjustment
            val premergeLocalTxnIds = transactions.map { it.id }.toSet()

            // Save merged data back to JSON files
            result.mergedTransactions?.let { TransactionRepository.save(applicationContext, it) }
            result.mergedRecurringExpenses?.let { RecurringExpenseRepository.save(applicationContext, it) }
            result.mergedIncomeSources?.let { IncomeSourceRepository.save(applicationContext, it) }
            result.mergedSavingsGoals?.let { SavingsGoalRepository.save(applicationContext, it) }
            result.mergedAmortizationEntries?.let { AmortizationRepository.save(applicationContext, it) }
            result.mergedCategories?.let { CategoryRepository.save(applicationContext, it) }
            result.mergedSharedSettings?.let { SharedSettingsRepository.save(applicationContext, it) }
            // Persist updated category ID remap
            result.catIdRemap?.let { remap ->
                val json = JSONObject(remap.mapKeys { it.key.toString() })
                syncPrefs.edit().putString("catIdRemap", json.toString()).apply()
            }

            // Adjust availableCash for new remote transactions (matches foreground logic)
            if (result.mergedTransactions != null) {
                val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val budgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
                    try { LocalDate.parse(it) } catch (_: Exception) { null }
                }
                if (budgetStartDate != null) {
                    val mergedCats = result.mergedCategories ?: categories
                    val recurringCatId = mergedCats.find { it.tag == "recurring" }?.id
                    val amortCatId = mergedCats.find { it.tag == "amortization" }?.id
                    val newRemoteTxns = result.mergedTransactions!!.filter {
                        it.deviceId != deviceId && it.id !in premergeLocalTxnIds
                    }
                    var cash = appPrefs.getString("availableCash", null)?.toDoubleOrNull() ?: 0.0
                    var cashChanged = false
                    for (txn in newRemoteTxns) {
                        val isBudgetAccounted = txn.type == TransactionType.EXPENSE &&
                            txn.categoryAmounts.any { it.categoryId == recurringCatId || it.categoryId == amortCatId }
                        if (!txn.deleted && !txn.date.isBefore(budgetStartDate)) {
                            if (txn.type == TransactionType.EXPENSE && !isBudgetAccounted) {
                                cash -= txn.amount
                                cashChanged = true
                            } else if (txn.type == TransactionType.INCOME && !txn.isBudgetIncome) {
                                cash += txn.amount
                                cashChanged = true
                            }
                        }
                    }
                    if (cashChanged) {
                        if (cash.isNaN() || cash.isInfinite()) cash = 0.0
                        cash = BudgetCalculator.roundCents(cash)
                        appPrefs.edit().putString("availableCash", cash.toString()).apply()
                    }
                }
            }

            return Result.success()
        }

        return Result.retry()
    }

    companion object {
        private const val WORK_NAME = "sync_budget_background_sync"

        /** Create a file-based lock for preventing concurrent syncs. */
        fun createSyncLock(context: Context): SyncFileLock = SyncFileLock(context)

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

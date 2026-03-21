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
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Background sync failed: ${e.message}", e)
            return Result.retry()
        } finally {
            fileLock.unlock()
        }
    }

    private suspend fun doSyncWork(): Result {
        val syncPrefs = applicationContext.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
        val fcmPrefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val fcmDebugRequested = fcmPrefs.getBoolean("fcm_debug_requested", false)
        // Only run background sync when there are unpushed local changes
        // OR when FCM triggered a debug request (device woken up for debug upload).
        // Pull-side sync is handled by the foreground sync loop on app resume.
        if (!syncPrefs.getBoolean("syncDirty", false) && !fcmDebugRequested) return Result.success()

        val groupId = syncPrefs.getString("groupId", null) ?: return Result.success()
        // Read key from encrypted prefs (with plain fallback for pre-migration)
        val keyBase64 = SecurePrefs.get(applicationContext).getString("encryptionKey", null)
            ?: syncPrefs.getString("encryptionKey", null)
            ?: return Result.success()

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

        // One-time migration: stamp description_clock on records with non-blank descriptions.
        // Only stamps non-blank so non-admin devices don't create competing clocks for empty values.
        if (!syncPrefs.getBoolean("migration_description_clock_done_v2", false)) {
            val migClock = lamportClock.tick()
            var changed = false
            transactions = transactions.map { t ->
                if (t.description.isNotBlank() && t.description_clock == 0L) {
                    changed = true; t.copy(description_clock = migClock)
                } else t
            }
            if (changed) TransactionRepository.save(applicationContext, transactions)
            changed = false
            recurringExpenses = recurringExpenses.map { r ->
                if (r.description.isNotBlank() && r.description_clock == 0L) {
                    changed = true; r.copy(description_clock = migClock)
                } else r
            }
            if (changed) RecurringExpenseRepository.save(applicationContext, recurringExpenses)
            changed = false
            incomeSources = incomeSources.map { s ->
                if (s.description.isNotBlank() && s.description_clock == 0L) {
                    changed = true; s.copy(description_clock = migClock)
                } else s
            }
            if (changed) IncomeSourceRepository.save(applicationContext, incomeSources)
            changed = false
            amortizationEntries = amortizationEntries.map { e ->
                if (e.description.isNotBlank() && e.description_clock == 0L) {
                    changed = true; e.copy(description_clock = migClock)
                } else e
            }
            if (changed) AmortizationRepository.save(applicationContext, amortizationEntries)
            syncPrefs.edit().putBoolean("migration_description_clock_done_v2", true).apply()
        }

        // One-time migration: re-stamp linked field clocks so they get re-pushed.
        // Fixes issue where non-admin received linking deltas on an old app version
        // that didn't have linked fields, causing them to be silently dropped.
        if (!syncPrefs.getBoolean("migration_restamp_linked_clocks", false)) {
            val migClock = lamportClock.tick()
            var changed = false
            transactions = transactions.map { t ->
                val needsRecurring = t.linkedRecurringExpenseId != null && t.linkedRecurringExpenseId_clock > 0L
                val needsAmortization = t.linkedAmortizationEntryId != null && t.linkedAmortizationEntryId_clock > 0L
                if (needsRecurring || needsAmortization) {
                    changed = true
                    t.copy(
                        linkedRecurringExpenseId_clock = if (needsRecurring) migClock else t.linkedRecurringExpenseId_clock,
                        linkedAmortizationEntryId_clock = if (needsAmortization) migClock else t.linkedAmortizationEntryId_clock
                    )
                } else t
            }
            if (changed) TransactionRepository.save(applicationContext, transactions)
            syncPrefs.edit().putBoolean("migration_restamp_linked_clocks", true).apply()
        }

        // One-time cleanup: remove skeleton categories (name_clock=0, empty name)
        if (!syncPrefs.getBoolean("migration_remove_skeleton_categories", false)) {
            val before = categories.size
            categories = categories.filter { it.name.isNotEmpty() || it.name_clock > 0L }
            if (categories.size < before) CategoryRepository.save(applicationContext, categories)
            syncPrefs.edit().putBoolean("migration_remove_skeleton_categories", true).apply()
        }

        // One-time fix: non-admin icon clock inflation (see MainActivity for details)
        if (!GroupManager.isAdmin(applicationContext) &&
            !syncPrefs.getBoolean("migration_reset_nonadmin_icon_clock", false)) {
            var changed = false
            categories = categories.map { c ->
                if (c.iconName_clock > 0L && c.deviceId == deviceId) {
                    changed = true; c.copy(iconName_clock = 0L)
                } else c
            }
            if (changed) CategoryRepository.save(applicationContext, categories)
            syncPrefs.edit().putBoolean("migration_reset_nonadmin_icon_clock", true).apply()
        }

        // One-time migration: fix stale budget-start ledger entry.
        // If the period ledger entry on the budgetStartDate has a clock older
        // than the budgetStartDate_clock, it was created BEFORE the budget reset
        // and has the wrong appliedAmount.  Use the next day's appliedAmount
        // (which reflects the correct budgetAmount after the reset).
        if (!syncPrefs.getBoolean("migration_fix_stale_budgetstart_ledger", false)) {
            val bsd = sharedSettings.budgetStartDate?.let {
                try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
            }
            if (bsd != null) {
                val periodLedgerMig = PeriodLedgerRepository.load(applicationContext)
                val bsdEpochDay = bsd.toEpochDay().toInt()
                val bsdEntry = periodLedgerMig.find { it.id == bsdEpochDay }
                if (bsdEntry != null && bsdEntry.clock < sharedSettings.budgetStartDate_clock) {
                    // Entry is from before the budget reset — find correct amount
                    val nextDayEntry = periodLedgerMig.find { it.id == bsdEpochDay + 1 }
                    val correctAmount = nextDayEntry?.appliedAmount
                        ?: BudgetCalculator.computeFullBudgetAmount(
                            incomeSources, recurringExpenses, amortizationEntries,
                            SavingsGoalRepository.load(applicationContext),
                            try { com.syncbudget.app.data.BudgetPeriod.valueOf(sharedSettings.budgetPeriod) }
                            catch (_: Exception) { com.syncbudget.app.data.BudgetPeriod.DAILY },
                            sharedSettings.isManualBudgetEnabled,
                            sharedSettings.manualBudgetAmount,
                            bsd
                        )
                    val migClock = lamportClock.tick()
                    val fixed = periodLedgerMig.map { e ->
                        if (e.id == bsdEpochDay) e.copy(
                            appliedAmount = correctAmount,
                            clock = migClock,
                            deviceId = deviceId
                        ) else e
                    }
                    PeriodLedgerRepository.save(applicationContext, fixed)
                }
            }
            syncPrefs.edit().putBoolean("migration_fix_stale_budgetstart_ledger", true).apply()
        }

        // One-time migration: admin re-stamps ALL period ledger entries so they
        // get re-pushed to Firestore.  Non-admin devices may be missing entries
        // (e.g. joined after they were pushed) — this forces the admin's complete
        // ledger to propagate via sync.
        if (!syncPrefs.getBoolean("migration_restamp_all_period_ledger", false)) {
            if (GroupManager.isAdmin(applicationContext)) {
                val periodLedgerMig = PeriodLedgerRepository.load(applicationContext)
                if (periodLedgerMig.isNotEmpty()) {
                    val migClock = lamportClock.tick()
                    val restamped = periodLedgerMig.map { e ->
                        e.copy(clock = migClock, deviceId = deviceId)
                    }
                    PeriodLedgerRepository.save(applicationContext, restamped)
                }
            }
            syncPrefs.edit().putBoolean("migration_restamp_all_period_ledger", true).apply()
        }

        // One-time rescue: re-stamp locally-owned records whose max field
        // clock fell behind lastPushedClock due to a previous bug in
        // maxDeltaClock calculation.  Uses a single clock for all records
        // so they all share the same value.  Gated by a flag to prevent
        // the rescue from repeating every sync cycle.
        val lpc = syncPrefs.getLong("lastPushedClock", 0L)
        if (lpc > 0 && !syncPrefs.getBoolean("rescue_stranded_v2_done", false)) {
            val rescueClk = lamportClock.tick()
            var anyRescued = false
            transactions = transactions.map { t ->
                val maxClk = maxOf(t.source_clock, t.amount_clock, t.date_clock, t.type_clock,
                    t.categoryAmounts_clock, t.isUserCategorized_clock, t.excludeFromBudget_clock, t.isBudgetIncome_clock,
                    t.linkedRecurringExpenseId_clock, t.linkedAmortizationEntryId_clock,
                    t.linkedIncomeSourceId_clock, t.amortizationAppliedAmount_clock,
                    t.linkedRecurringExpenseAmount_clock, t.linkedIncomeSourceAmount_clock,
                    t.deviceId_clock, t.deleted_clock, t.description_clock)
                if (t.deviceId == deviceId && maxClk in 1 until lpc) {
                    anyRescued = true
                    t.copy(source_clock = rescueClk, description_clock = rescueClk, amount_clock = rescueClk,
                        date_clock = rescueClk, type_clock = rescueClk, categoryAmounts_clock = rescueClk,
                        isUserCategorized_clock = rescueClk, excludeFromBudget_clock = rescueClk, isBudgetIncome_clock = rescueClk,
                        linkedRecurringExpenseId_clock = rescueClk, linkedAmortizationEntryId_clock = rescueClk,
                        linkedIncomeSourceId_clock = rescueClk, amortizationAppliedAmount_clock = rescueClk,
                        linkedRecurringExpenseAmount_clock = rescueClk, linkedIncomeSourceAmount_clock = rescueClk,
                        deviceId_clock = rescueClk, deleted_clock = rescueClk)
                } else t
            }
            if (anyRescued) TransactionRepository.save(applicationContext, transactions)
            anyRescued = false
            recurringExpenses = recurringExpenses.map { r ->
                val maxClk = maxOf(r.source_clock, r.amount_clock, r.repeatType_clock,
                    r.repeatInterval_clock, r.startDate_clock, r.monthDay1_clock,
                    r.monthDay2_clock, r.deviceId_clock, r.deleted_clock, r.description_clock)
                if (r.deviceId == deviceId && maxClk in 1 until lpc) {
                    anyRescued = true
                    r.copy(source_clock = rescueClk, description_clock = rescueClk, amount_clock = rescueClk,
                        repeatType_clock = rescueClk, repeatInterval_clock = rescueClk, startDate_clock = rescueClk,
                        monthDay1_clock = rescueClk, monthDay2_clock = rescueClk,
                        deviceId_clock = rescueClk, deleted_clock = rescueClk)
                } else r
            }
            if (anyRescued) RecurringExpenseRepository.save(applicationContext, recurringExpenses)
            anyRescued = false
            incomeSources = incomeSources.map { s ->
                val maxClk = maxOf(s.source_clock, s.amount_clock, s.repeatType_clock,
                    s.repeatInterval_clock, s.startDate_clock, s.monthDay1_clock,
                    s.monthDay2_clock, s.deviceId_clock, s.deleted_clock, s.description_clock)
                if (s.deviceId == deviceId && maxClk in 1 until lpc) {
                    anyRescued = true
                    s.copy(source_clock = rescueClk, description_clock = rescueClk, amount_clock = rescueClk,
                        repeatType_clock = rescueClk, repeatInterval_clock = rescueClk, startDate_clock = rescueClk,
                        monthDay1_clock = rescueClk, monthDay2_clock = rescueClk,
                        deviceId_clock = rescueClk, deleted_clock = rescueClk)
                } else s
            }
            if (anyRescued) IncomeSourceRepository.save(applicationContext, incomeSources)
            anyRescued = false
            amortizationEntries = amortizationEntries.map { e ->
                val maxClk = maxOf(e.source_clock, e.amount_clock, e.totalPeriods_clock,
                    e.startDate_clock, e.isPaused_clock, e.deviceId_clock, e.deleted_clock,
                    e.description_clock)
                if (e.deviceId == deviceId && maxClk in 1 until lpc) {
                    anyRescued = true
                    e.copy(source_clock = rescueClk, description_clock = rescueClk, amount_clock = rescueClk,
                        totalPeriods_clock = rescueClk, startDate_clock = rescueClk, isPaused_clock = rescueClk,
                        deviceId_clock = rescueClk, deleted_clock = rescueClk)
                } else e
            }
            if (anyRescued) AmortizationRepository.save(applicationContext, amortizationEntries)
            syncPrefs.edit().putBoolean("rescue_stranded_v2_done", true).apply()
        }

        // One-time migration: stamp excludeFromBudget_clock and isBudgetIncome_clock
        // for transactions where the flag is set but the clock is still 0 (set before
        // clock stamping was added).
        if (!syncPrefs.getBoolean("migration_stamp_exclude_budget_clocks", false)) {
            val migClock = lamportClock.tick()
            var changed = false
            transactions = transactions.map { t ->
                val needsExclude = t.excludeFromBudget && t.excludeFromBudget_clock == 0L
                val needsBudgetIncome = t.isBudgetIncome && t.isBudgetIncome_clock == 0L
                if (needsExclude || needsBudgetIncome) {
                    changed = true
                    t.copy(
                        excludeFromBudget_clock = if (needsExclude) migClock else t.excludeFromBudget_clock,
                        isBudgetIncome_clock = if (needsBudgetIncome) migClock else t.isBudgetIncome_clock
                    )
                } else t
            }
            if (changed) TransactionRepository.save(applicationContext, transactions)
            syncPrefs.edit().putBoolean("migration_stamp_exclude_budget_clocks", true).apply()
        }

        // Load persisted category ID remap
        val remapJson = syncPrefs.getString("catIdRemap", null)
        val existingRemap = if (remapJson != null) {
            try {
                val json = JSONObject(remapJson)
                json.keys().asSequence().associate { it.toInt() to json.getInt(it) }
            } catch (_: Exception) { emptyMap() }
        } else emptyMap<Int, Int>()

        val periodLedger = PeriodLedgerRepository.load(applicationContext)

        val result = engine.sync(
            transactions, recurringExpenses, incomeSources,
            savingsGoals, amortizationEntries, categories,
            sharedSettings,
            existingCatIdRemap = existingRemap,
            periodLedgerEntries = periodLedger
        )

        if (result.success) {
            // Persist repair alert so foreground UI shows flashing magenta
            if (result.repairAttempted) {
                applicationContext.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("syncRepairAlert", true).apply()
            }
            // Save merged data back to JSON files
            result.mergedTransactions?.let { TransactionRepository.save(applicationContext, it) }
            result.mergedRecurringExpenses?.let { RecurringExpenseRepository.save(applicationContext, it) }
            result.mergedIncomeSources?.let { IncomeSourceRepository.save(applicationContext, it) }
            result.mergedSavingsGoals?.let { SavingsGoalRepository.save(applicationContext, it) }
            result.mergedAmortizationEntries?.let { AmortizationRepository.save(applicationContext, it) }
            result.mergedCategories?.let { CategoryRepository.save(applicationContext, it) }
            result.mergedPeriodLedgerEntries?.let { PeriodLedgerRepository.save(applicationContext, it) }
            result.mergedSharedSettings?.let { merged ->
                SharedSettingsRepository.save(applicationContext, merged)
                // Write merged settings to app_prefs so the UI picks them up on next launch
                applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putString("currencySymbol", merged.currency)
                    .putString("budgetPeriod", merged.budgetPeriod)
                    .putInt("resetHour", merged.resetHour)
                    .putInt("resetDayOfWeek", merged.resetDayOfWeek)
                    .putInt("resetDayOfMonth", merged.resetDayOfMonth)
                    .putBoolean("isManualBudgetEnabled", merged.isManualBudgetEnabled)
                    .putString("manualBudgetAmount", merged.manualBudgetAmount.toString())
                    .putBoolean("weekStartSunday", merged.weekStartSunday)
                    .putInt("matchDays", merged.matchDays)
                    .putString("matchPercent", merged.matchPercent.toString())
                    .putInt("matchDollar", merged.matchDollar)
                    .putInt("matchChars", merged.matchChars)
                    .apply()
            }
            // Persist updated category ID remap
            result.catIdRemap?.let { remap ->
                val json = JSONObject(remap.mapKeys { it.key.toString() })
                syncPrefs.edit().putString("catIdRemap", json.toString()).apply()
            }

            // Recompute availableCash from synced data (deterministic — all devices converge)
            val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val budgetStartDate = appPrefs.getString("budgetStartDate", null)?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }
            if (budgetStartDate != null) {
                val mergedLedger = result.mergedPeriodLedgerEntries ?: periodLedger
                val activeTxns = (result.mergedTransactions ?: transactions).filter { !it.deleted }
                val activeRE = (result.mergedRecurringExpenses ?: recurringExpenses).filter { !it.deleted }
                val activeIS = (result.mergedIncomeSources ?: incomeSources).filter { !it.deleted }
                val incomeMode = try {
                    com.syncbudget.app.data.IncomeMode.valueOf(
                        appPrefs.getString("incomeMode", null) ?: "FIXED"
                    )
                } catch (_: Exception) { com.syncbudget.app.data.IncomeMode.FIXED }
                val cash = BudgetCalculator.recomputeAvailableCash(
                    budgetStartDate, mergedLedger, activeTxns, activeRE,
                    incomeMode, activeIS
                )
                appPrefs.edit().putString("availableCash", cash.toString()).apply()
            }

            // Local changes successfully pushed — clear dirty flag
            syncPrefs.edit().putBoolean("syncDirty", false).apply()
            com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(applicationContext)
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

package com.syncbudget.app.data

import android.content.Context
import android.util.Log
import com.syncbudget.app.data.sync.PeriodLedgerEntry
import com.syncbudget.app.data.sync.PeriodLedgerRepository
import com.syncbudget.app.data.sync.active
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Extracted period refresh logic, shared by both foreground (MainActivity) and
 * background (PeriodRefreshWorker).
 *
 * Loads ALL data from disk, performs accrual updates, saves changed data back
 * to disk, and returns only the records that changed so the caller can push
 * them to Firestore and update in-memory state.
 */
object PeriodRefreshService {

    private const val TAG = "PeriodRefreshService"

    data class RefreshConfig(
        val budgetStartDate: LocalDate,
        val lastRefreshDate: LocalDate,
        val budgetPeriod: BudgetPeriod,
        val resetHour: Int,
        val resetDayOfWeek: Int,
        val resetDayOfMonth: Int,
        val familyTimezone: String,       // empty = device default
        val localDeviceId: String,
        val incomeMode: IncomeMode,
        val isManualBudgetEnabled: Boolean,
        val manualBudgetAmount: Double
    )

    data class RefreshResult(
        val newLedgerEntries: List<PeriodLedgerEntry>,
        val updatedSavingsGoals: List<SavingsGoal>,
        val updatedRecurringExpenses: List<RecurringExpense>,
        val newLastRefreshDate: LocalDate,
        val newCash: Double
    )

    /**
     * Check for missed periods and apply catch-up accruals.
     *
     * @return [RefreshResult] containing only the records that changed, or
     *         `null` if no periods were missed (missedPeriods <= 0).
     */
    @Synchronized
    fun refreshIfNeeded(context: Context, config: RefreshConfig): RefreshResult? {
        // ── Determine "today" respecting resetHour for DAILY budgets ──
        // Before resetHour we're still in yesterday's period.
        val zone = if (config.familyTimezone.isNotEmpty())
            ZoneId.of(config.familyTimezone) else ZoneId.systemDefault()
        val now = java.time.Instant.now().atZone(zone)
        val today = if (config.budgetPeriod == BudgetPeriod.DAILY &&
            config.resetHour > 0 && now.hour < config.resetHour
        ) now.toLocalDate().minusDays(1) else now.toLocalDate()

        // ── Aligned period start ──
        val refreshTz = if (config.familyTimezone.isNotEmpty()) zone else null
        val currentPeriod = BudgetCalculator.currentPeriodStart(
            config.budgetPeriod, config.resetDayOfWeek, config.resetDayOfMonth,
            refreshTz, config.resetHour
        )

        val missedPeriods = BudgetCalculator.countPeriodsCompleted(
            config.lastRefreshDate, currentPeriod, config.budgetPeriod
        )
        if (missedPeriods <= 0) return null

        // ── Load all data from disk ──
        val periodLedger = PeriodLedgerRepository.load(context).toMutableList()
        val savingsGoals = SavingsGoalRepository.load(context).toMutableList()
        val recurringExpenses = RecurringExpenseRepository.load(context).toMutableList()
        val incomeSources = IncomeSourceRepository.load(context)
        val amortizationEntries = AmortizationRepository.load(context)
        val transactions = TransactionRepository.load(context)

        // ── Compute budgetAmount (same formula as MainActivity derivedStateOf) ──
        val activeIS = incomeSources.active
        val activeRE = recurringExpenses.active
        val activeAE = amortizationEntries.active
        val activeSG = savingsGoals.active
        val budgetAmount = BudgetCalculator.computeFullBudgetAmount(
            activeIS, activeRE, activeAE, activeSG,
            config.budgetPeriod, config.isManualBudgetEnabled,
            config.manualBudgetAmount, today
        )

        // ── Create one ledger entry per missed period ──
        val addedLedgerEntries = mutableListOf<PeriodLedgerEntry>()
        for (period in 0 until missedPeriods) {
            val periodsBack = (missedPeriods - 1 - period).toLong()
            val periodDate = subtractPeriods(currentPeriod, config.budgetPeriod, periodsBack)
            val alreadyRecorded = periodLedger.any {
                it.periodStartDate.toLocalDate() == periodDate
            }
            if (!alreadyRecorded) {
                val entry = PeriodLedgerEntry(
                    periodStartDate = periodDate.atStartOfDay(),
                    appliedAmount = budgetAmount,
                    deviceId = config.localDeviceId
                )
                periodLedger.add(entry)
                addedLedgerEntries.add(entry)
            }
        }
        PeriodLedgerRepository.save(context, periodLedger)

        // ── Update savings goals totalSavedSoFar for non-paused, non-complete items ──
        // Use the correct date for each catch-up period so periodsLeft
        // decreases properly (instead of using today for all iterations).
        // NO lamportClock.tick() -- savings goal accrual is deterministic.
        // Both devices compute the same value from the same data, so
        // clock advancement would create unnecessary divergence.
        val sgOriginals = savingsGoals.map { it }
        for (period in 0 until missedPeriods) {
            val periodsBack = (missedPeriods - 1 - period).toLong()
            val periodDate = subtractPeriods(currentPeriod, config.budgetPeriod, periodsBack)
            savingsGoals.forEachIndexed { idx, goal ->
                if (!goal.isPaused && !goal.deleted) {
                    val remaining = goal.targetAmount - goal.totalSavedSoFar
                    if (remaining > 0) {
                        if (goal.targetDate != null) {
                            if (periodDate.isBefore(goal.targetDate)) {
                                val periods = when (config.budgetPeriod) {
                                    BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(periodDate, goal.targetDate)
                                    BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(periodDate, goal.targetDate)
                                    BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(periodDate, goal.targetDate)
                                }
                                if (periods > 0) {
                                    val deduction = BudgetCalculator.roundCents(
                                        minOf(remaining / periods.toDouble(), remaining)
                                    )
                                    savingsGoals[idx] = goal.copy(
                                        totalSavedSoFar = goal.totalSavedSoFar + deduction
                                    )
                                }
                            }
                        } else {
                            val contribution = BudgetCalculator.roundCents(
                                minOf(goal.contributionPerPeriod, remaining)
                            )
                            if (contribution > 0) {
                                savingsGoals[idx] = goal.copy(
                                    totalSavedSoFar = goal.totalSavedSoFar + contribution
                                )
                            }
                        }
                    }
                }
            }
        }
        SavingsGoalRepository.save(context, savingsGoals)

        // Collect only savings goals that actually changed
        val changedSG = savingsGoals.filterIndexed { idx, goal ->
            goal != sgOriginals[idx]
        }

        // ── Update RE set-aside tracking for each catch-up period ──
        // NO lamportClock.tick() -- set-aside accrual is deterministic.
        // Both devices compute the same value from the same RE data.
        val reOriginals = recurringExpenses.map { it }
        var reChanged = false
        for (period in 0 until missedPeriods) {
            val periodsBack = (missedPeriods - 1 - period).toLong()
            val periodDate = subtractPeriods(currentPeriod, config.budgetPeriod, periodsBack)
            val periodEnd = addOnePeriod(periodDate, config.budgetPeriod)
            recurringExpenses.forEachIndexed { idx, re ->
                if (re.deleted) return@forEachIndexed
                val occurrences = BudgetCalculator.generateOccurrences(
                    re.repeatType, re.repeatInterval, re.startDate,
                    re.monthDay1, re.monthDay2, periodDate, periodEnd.minusDays(1)
                )
                if (occurrences.isNotEmpty()) {
                    // Due date reached: reset set-aside, deactivate accelerated
                    recurringExpenses[idx] = re.copy(
                        setAsideSoFar = 0.0,
                        isAccelerated = if (re.isAccelerated) false else re.isAccelerated
                    )
                    reChanged = true
                } else {
                    val increment = if (re.isAccelerated) {
                        val periodsLeft = BudgetCalculator.periodsUntilNextOccurrence(
                            re, config.budgetPeriod, periodDate
                        )
                        if (periodsLeft > 0) BudgetCalculator.roundCents(
                            (re.amount - re.setAsideSoFar) / periodsLeft
                        ) else 0.0
                    } else {
                        BudgetCalculator.normalPerPeriodDeduction(
                            re, config.budgetPeriod, periodDate
                        )
                    }
                    if (increment > 0) {
                        recurringExpenses[idx] = re.copy(
                            setAsideSoFar = minOf(re.setAsideSoFar + increment, re.amount)
                        )
                        reChanged = true
                    }
                }
            }
        }
        if (reChanged) RecurringExpenseRepository.save(context, recurringExpenses)

        // Collect only recurring expenses that actually changed
        val changedRE = recurringExpenses.filterIndexed { idx, re ->
            re != reOriginals[idx]
        }

        // ── Recompute available cash ──
        val activeTxn = transactions.active
        // Use updated lists for active filters (SG/RE may have changed above)
        val activeREAfter = recurringExpenses.active
        val newCash = BudgetCalculator.recomputeAvailableCash(
            config.budgetStartDate, periodLedger,
            activeTxn, activeREAfter,
            config.incomeMode, activeIS
        )

        // ── Persist availableCash and lastRefreshDate to SharedPrefs ──
        val safeCash = if (newCash.isNaN() || newCash.isInfinite()) 0.0
                       else BudgetCalculator.roundCents(newCash)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("availableCash", safeCash.toString())
            .putString("lastRefreshDate", currentPeriod.toString())
            .apply()

        // Update widget to reflect new cash value
        try {
            com.syncbudget.app.widget.BudgetWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            Log.w(TAG, "Widget update failed: ${e.message}")
        }

        Log.d(TAG, "Period refresh: $missedPeriods periods caught up, " +
            "${addedLedgerEntries.size} ledger entries, " +
            "${changedSG.size} SG updated, ${changedRE.size} RE updated, " +
            "cash=$safeCash")

        return RefreshResult(
            newLedgerEntries = addedLedgerEntries,
            updatedSavingsGoals = changedSG,
            updatedRecurringExpenses = changedRE,
            newLastRefreshDate = currentPeriod,
            newCash = safeCash
        )
    }

    // ── Helpers ──

    /** Subtract N budget periods from a date. */
    private fun subtractPeriods(
        date: LocalDate, budgetPeriod: BudgetPeriod, count: Long
    ): LocalDate = when (budgetPeriod) {
        BudgetPeriod.DAILY -> date.minusDays(count)
        BudgetPeriod.WEEKLY -> date.minusWeeks(count)
        BudgetPeriod.MONTHLY -> date.minusMonths(count)
    }

    /** Add one budget period to a date. */
    private fun addOnePeriod(
        date: LocalDate, budgetPeriod: BudgetPeriod
    ): LocalDate = when (budgetPeriod) {
        BudgetPeriod.DAILY -> date.plusDays(1)
        BudgetPeriod.WEEKLY -> date.plusWeeks(1)
        BudgetPeriod.MONTHLY -> date.plusMonths(1)
    }
}

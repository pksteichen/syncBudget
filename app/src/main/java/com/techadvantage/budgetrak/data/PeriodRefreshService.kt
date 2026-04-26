package com.techadvantage.budgetrak.data

import android.content.Context
import android.util.Log
import com.techadvantage.budgetrak.data.sync.PeriodLedgerEntry
import com.techadvantage.budgetrak.data.sync.PeriodLedgerRepository
import com.techadvantage.budgetrak.data.sync.active
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Extracted period refresh logic, shared by both foreground (MainViewModel) and
 * background (BackgroundSyncWorker).
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
        val manualBudgetAmount: Double,
        val carryForwardBalance: Double = 0.0,
        val archiveCutoffDate: LocalDate? = null
    )

    /**
     * Wall-clock instant of the next period boundary. Used by
     * `BackgroundSyncWorker.scheduleNextBoundary` to enqueue a one-shot worker
     * precisely at the next period change instead of polling every 15 min.
     *
     * For DAILY budgets, the boundary is `resetHour:00:00` of the next day.
     * For WEEKLY and MONTHLY, it's midnight of the next aligned period start
     * (no UI for sub-day reset on those modes).
     */
    fun nextBoundaryAt(
        budgetPeriod: BudgetPeriod,
        resetHour: Int,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        familyTimezone: String
    ): java.time.Instant {
        val zone = if (familyTimezone.isNotEmpty()) ZoneId.of(familyTimezone) else ZoneId.systemDefault()
        val currentStart = BudgetCalculator.currentPeriodStart(
            budgetPeriod, resetDayOfWeek, resetDayOfMonth, zone, resetHour
        )
        val nextStartDate = when (budgetPeriod) {
            BudgetPeriod.DAILY -> currentStart.plusDays(1)
            BudgetPeriod.WEEKLY -> currentStart.plusWeeks(1)
            BudgetPeriod.MONTHLY -> {
                val nextMonth = currentStart.plusMonths(1)
                val day = resetDayOfMonth.coerceAtMost(nextMonth.lengthOfMonth())
                nextMonth.withDayOfMonth(day)
            }
        }
        val hour = if (budgetPeriod == BudgetPeriod.DAILY) resetHour else 0
        return nextStartDate.atTime(hour, 0).atZone(zone).toInstant()
    }

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

        // ── Per-period catch-up: ledger + SG + RE in one loop ──
        // budgetAmount recomputed each period so SG/RE changes are reflected
        val addedLedgerEntries = mutableListOf<PeriodLedgerEntry>()
        val activeIS = incomeSources.active
        val activeAE = amortizationEntries.active
        val sgOriginals = savingsGoals.map { it }
        val reOriginals = recurringExpenses.map { it }
        var reChanged = false
        for (period in 0 until missedPeriods) {
            val periodsBack = (missedPeriods - 1 - period).toLong()
            val periodDate = subtractPeriods(currentPeriod, config.budgetPeriod, periodsBack)

            // Ledger entry with budgetAmount from current (updated) SG/RE state
            val alreadyRecorded = periodLedger.any {
                it.periodStartDate.toLocalDate() == periodDate
            }
            if (!alreadyRecorded) {
                val budgetAmount = BudgetCalculator.computeFullBudgetAmount(
                    activeIS, recurringExpenses.active, activeAE, savingsGoals.active,
                    config.budgetPeriod, config.isManualBudgetEnabled,
                    config.manualBudgetAmount, periodDate
                )
                val entry = PeriodLedgerEntry(
                    periodStartDate = periodDate.atStartOfDay(),
                    appliedAmount = budgetAmount,
                    deviceId = config.localDeviceId
                )
                periodLedger.add(entry)
                addedLedgerEntries.add(entry)
            }

            // Update savings goals
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

            // Update RE set-aside tracking
            val periodEnd = addOnePeriod(periodDate, config.budgetPeriod)
            recurringExpenses.forEachIndexed { idx, re ->
                if (re.deleted) return@forEachIndexed
                val occurrences = BudgetCalculator.generateOccurrences(
                    re.repeatType, re.repeatInterval, re.startDate,
                    re.monthDay1, re.monthDay2, periodDate, periodEnd.minusDays(1)
                )
                if (occurrences.isNotEmpty()) {
                    recurringExpenses[idx] = re.copy(
                        setAsideSoFar = 0.0,
                        isAccelerated = if (re.isAccelerated) false else re.isAccelerated
                    )
                    reChanged = true
                } else {
                    val increment = if (re.isAccelerated) {
                        val periodsLeft = BudgetCalculator.periodsUntilNextOccurrence(
                            re, config.budgetPeriod, periodDate, config.resetDayOfWeek
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
        PeriodLedgerRepository.save(context, periodLedger)
        SavingsGoalRepository.save(context, savingsGoals)
        if (reChanged) RecurringExpenseRepository.save(context, recurringExpenses)

        // Collect changed records
        val changedSG = savingsGoals.filterIndexed { idx, goal ->
            goal != sgOriginals[idx]
        }

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
            config.incomeMode, activeIS,
            config.carryForwardBalance, config.archiveCutoffDate
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
            com.techadvantage.budgetrak.widget.BudgetWidgetProvider.updateAllWidgets(context)
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

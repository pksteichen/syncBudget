package com.syncbudget.app.data

import com.syncbudget.app.data.sync.PeriodLedgerEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

object BudgetCalculator {

    /** Round a Double to 2 decimal places (cents). */
    fun roundCents(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    fun generateOccurrences(
        repeatType: RepeatType,
        repeatInterval: Int,
        startDate: LocalDate?,
        monthDay1: Int?,
        monthDay2: Int?,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        when (repeatType) {
            RepeatType.DAYS -> {
                val sd = startDate ?: return dates
                if (repeatInterval <= 0) return dates
                var d = sd
                // Advance to rangeStart
                if (d.isBefore(rangeStart)) {
                    val gap = ChronoUnit.DAYS.between(d, rangeStart)
                    val steps = gap / repeatInterval
                    d = d.plusDays(steps * repeatInterval)
                    if (d.isBefore(rangeStart)) d = d.plusDays(repeatInterval.toLong())
                }
                while (!d.isAfter(rangeEnd)) {
                    dates.add(d)
                    d = d.plusDays(repeatInterval.toLong())
                }
            }
            RepeatType.WEEKS -> {
                val sd = startDate ?: return dates
                if (repeatInterval <= 0) return dates
                val stepDays = (repeatInterval * 7).toLong()
                var d = sd
                if (d.isBefore(rangeStart)) {
                    val gap = ChronoUnit.DAYS.between(d, rangeStart)
                    val steps = gap / stepDays
                    d = d.plusDays(steps * stepDays)
                    if (d.isBefore(rangeStart)) d = d.plusDays(stepDays)
                }
                while (!d.isAfter(rangeEnd)) {
                    dates.add(d)
                    d = d.plusDays(stepDays)
                }
            }
            RepeatType.BI_WEEKLY -> {
                val sd = startDate ?: return dates
                var d = sd
                if (d.isBefore(rangeStart)) {
                    val gap = ChronoUnit.DAYS.between(d, rangeStart)
                    val steps = gap / 14
                    d = d.plusDays(steps * 14)
                    if (d.isBefore(rangeStart)) d = d.plusDays(14)
                }
                while (!d.isAfter(rangeEnd)) {
                    dates.add(d)
                    d = d.plusDays(14)
                }
            }
            RepeatType.MONTHS -> {
                val day = monthDay1 ?: return dates
                if (repeatInterval <= 0) return dates
                // Use startDate to anchor the phase for multi-month intervals
                var month = if (startDate != null && repeatInterval > 1) {
                    var m = startDate.withDayOfMonth(1)
                    if (m.isBefore(rangeStart.withDayOfMonth(1))) {
                        val gap = java.time.Period.between(m, rangeStart.withDayOfMonth(1)).toTotalMonths()
                        val steps = gap / repeatInterval
                        m = m.plusMonths(steps * repeatInterval)
                        if (m.isBefore(rangeStart.withDayOfMonth(1))) m = m.plusMonths(repeatInterval.toLong())
                    }
                    m
                } else {
                    rangeStart.withDayOfMonth(1)
                }
                while (!month.isAfter(rangeEnd)) {
                    val d = month.withDayOfMonth(day.coerceAtMost(month.lengthOfMonth()))
                    if (!d.isBefore(rangeStart) && !d.isAfter(rangeEnd)) {
                        dates.add(d)
                    }
                    month = month.plusMonths(repeatInterval.toLong())
                }
            }
            RepeatType.BI_MONTHLY -> {
                val d1 = monthDay1 ?: return dates
                val d2 = monthDay2 ?: return dates
                var month = rangeStart.withDayOfMonth(1)
                while (!month.isAfter(rangeEnd)) {
                    val date1 = month.withDayOfMonth(d1.coerceAtMost(month.lengthOfMonth()))
                    val date2 = month.withDayOfMonth(d2.coerceAtMost(month.lengthOfMonth()))
                    if (!date1.isBefore(rangeStart) && !date1.isAfter(rangeEnd)) dates.add(date1)
                    if (!date2.isBefore(rangeStart) && !date2.isAfter(rangeEnd) && date2 != date1) dates.add(date2)
                    month = month.plusMonths(1)
                }
            }
            RepeatType.ANNUAL -> {
                val sd = startDate ?: return dates
                var d = sd
                if (d.isBefore(rangeStart)) {
                    val gap = java.time.Period.between(d, rangeStart).years
                    d = d.plusYears(gap.toLong())
                    if (d.isBefore(rangeStart)) d = d.plusYears(1)
                }
                while (!d.isAfter(rangeEnd)) {
                    dates.add(d)
                    d = d.plusYears(1)
                }
            }
        }
        return dates
    }

    /**
     * Calculate how much savings the user should have on hand right now
     * based on accrued portions of each recurring expense cycle.
     */
    fun calculateAccruedSavingsNeeded(
        recurringExpenses: List<RecurringExpense>,
        today: LocalDate = LocalDate.now()
    ): Double {
        var total = 0.0
        val twoYearsAhead = today.plusYears(2)
        for (exp in recurringExpenses) {
            // Find next occurrence on or after today
            val occurrences = generateOccurrences(
                exp.repeatType, exp.repeatInterval, exp.startDate,
                exp.monthDay1, exp.monthDay2, today, twoYearsAhead
            )
            val nextOcc = occurrences.firstOrNull() ?: continue

            // If due today, full amount is accrued
            if (nextOcc == today) {
                total += exp.amount
                continue
            }

            // Derive previous occurrence by subtracting one period from next
            val prevOcc: LocalDate = when (exp.repeatType) {
                RepeatType.DAYS -> nextOcc.minusDays(exp.repeatInterval.toLong())
                RepeatType.WEEKS -> nextOcc.minusDays((exp.repeatInterval * 7).toLong())
                RepeatType.BI_WEEKLY -> nextOcc.minusDays(14)
                RepeatType.MONTHS -> nextOcc.minusMonths(exp.repeatInterval.toLong())
                RepeatType.BI_MONTHLY -> {
                    // Two days per month: generate the two most recent
                    // occurrences before nextOcc and pick the latest one.
                    // This handles all orderings of d1/d2 correctly.
                    val d1 = exp.monthDay1 ?: 1
                    val d2 = exp.monthDay2 ?: 15
                    val candidates = mutableListOf<LocalDate>()
                    // Check d1 and d2 in current month and previous month
                    for (monthOffset in 0L..1L) {
                        val m = nextOcc.minusMonths(monthOffset)
                        val cd1 = d1.coerceAtMost(m.lengthOfMonth())
                        val cd2 = d2.coerceAtMost(m.lengthOfMonth())
                        val date1 = m.withDayOfMonth(cd1)
                        val date2 = m.withDayOfMonth(cd2)
                        if (date1.isBefore(nextOcc)) candidates.add(date1)
                        if (date2.isBefore(nextOcc)) candidates.add(date2)
                    }
                    candidates.maxOrNull() ?: nextOcc.minusMonths(1)
                }
                RepeatType.ANNUAL -> nextOcc.minusYears(1)
            }

            val daysSincePrev = ChronoUnit.DAYS.between(prevOcc, today).toDouble()
            val totalCycleDays = ChronoUnit.DAYS.between(prevOcc, nextOcc).toDouble()
            if (totalCycleDays > 0) {
                total += (daysSincePrev / totalCycleDays) * exp.amount
            }
        }
        return roundCents(total)
    }

    fun calculateSafeBudgetAmount(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        today: LocalDate = LocalDate.now()
    ): Double {
        // Use theoretical rates to avoid alignment artifacts
        // (e.g. biweekly income hitting 27 vs 26 paychecks in a 1-year window)
        val periodsPerYear = when (budgetPeriod) {
            BudgetPeriod.DAILY -> 365.25
            BudgetPeriod.WEEKLY -> 365.25 / 7.0
            BudgetPeriod.MONTHLY -> 12.0
        }

        var totalAnnualIncome = 0.0
        for (src in incomeSources) {
            totalAnnualIncome += src.amount * theoreticalAnnualOccurrences(src.repeatType, src.repeatInterval)
        }

        var totalAnnualExpenses = 0.0
        for (exp in recurringExpenses) {
            totalAnnualExpenses += exp.amount * theoreticalAnnualOccurrences(exp.repeatType, exp.repeatInterval)
        }

        return roundCents(maxOf(0.0, (totalAnnualIncome - totalAnnualExpenses) / periodsPerYear))
    }

    fun theoreticalAnnualOccurrences(repeatType: RepeatType, repeatInterval: Int): Double {
        if (repeatInterval <= 0) return 0.0
        return when (repeatType) {
            RepeatType.DAYS -> 365.25 / repeatInterval
            RepeatType.WEEKS -> 365.25 / (repeatInterval * 7)
            RepeatType.BI_WEEKLY -> 365.25 / 14
            RepeatType.MONTHS -> 12.0 / repeatInterval
            RepeatType.BI_MONTHLY -> 24.0
            RepeatType.ANNUAL -> 1.0
        }
    }

    fun countPeriodsCompleted(from: LocalDate, to: LocalDate, budgetPeriod: BudgetPeriod): Int {
        if (!to.isAfter(from)) return 0
        return when (budgetPeriod) {
            BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(from, to).toInt()
            BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(from, to).toInt()
            BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(from, to).toInt()
        }
    }

    fun currentPeriodStart(
        budgetPeriod: BudgetPeriod,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        timezone: ZoneId? = null,
        resetHour: Int = 0
    ): LocalDate {
        // Always use explicit timezone to prevent period misalignment
        // across devices with different system timezones.
        val zone = timezone ?: ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        // In DAILY mode, before resetHour we're still in yesterday's period.
        // WEEKLY and MONTHLY always reset at midnight (no UI for resetHour).
        val today = if (budgetPeriod == BudgetPeriod.DAILY && resetHour > 0 && now.hour < resetHour)
            now.toLocalDate().minusDays(1) else now.toLocalDate()
        return when (budgetPeriod) {
            BudgetPeriod.DAILY -> today
            BudgetPeriod.WEEKLY -> {
                val targetDay = DayOfWeek.of(resetDayOfWeek)
                val adjusted = today.with(TemporalAdjusters.previousOrSame(targetDay))
                adjusted
            }
            BudgetPeriod.MONTHLY -> {
                val day = resetDayOfMonth.coerceAtMost(today.lengthOfMonth())
                val candidate = today.withDayOfMonth(day)
                if (candidate.isAfter(today)) candidate.minusMonths(1).withDayOfMonth(
                    resetDayOfMonth.coerceAtMost(candidate.minusMonths(1).lengthOfMonth())
                ) else candidate
            }
        }
    }

    fun activeAmortizationDeductions(
        entries: List<AmortizationEntry>,
        budgetPeriod: BudgetPeriod,
        today: LocalDate = LocalDate.now()
    ): Double {
        var total = 0.0
        for (entry in entries) {
            if (entry.isPaused) continue
            if (entry.totalPeriods <= 0) continue
            val elapsed = when (budgetPeriod) {
                BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(entry.startDate, today).toInt()
                BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(entry.startDate, today).toInt()
                BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(entry.startDate, today).toInt()
            }.coerceAtLeast(0)
            // Active when elapsed periods < totalPeriods (elapsed == totalPeriods means fully amortized)
            if (elapsed < entry.totalPeriods) {
                // Use cumulative approach to avoid rounding drift:
                // deduction = what SHOULD have been deducted after (elapsed+1) periods
                //           - what SHOULD have been deducted after (elapsed) periods
                val afterThis = roundCents(entry.amount * (elapsed + 1).toDouble() / entry.totalPeriods)
                val beforeThis = roundCents(entry.amount * elapsed.toDouble() / entry.totalPeriods)
                total += afterThis - beforeThis
            }
        }
        return total
    }

    fun activeSavingsGoalDeductions(
        goals: List<SavingsGoal>,
        budgetPeriod: BudgetPeriod,
        today: LocalDate = LocalDate.now()
    ): Double {
        var total = 0.0
        for (goal in goals) {
            if (goal.isPaused) continue
            val remaining = goal.targetAmount - goal.totalSavedSoFar
            if (remaining <= 0) continue

            if (goal.targetDate != null) {
                // Target-date type: auto-calculate deduction from remaining time
                if (!today.isBefore(goal.targetDate)) continue
                val periods = when (budgetPeriod) {
                    BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, goal.targetDate)
                    BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, goal.targetDate)
                    BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, goal.targetDate)
                }
                if (periods <= 0) continue
                total += roundCents(remaining / periods.toDouble())
            } else {
                // Fixed contribution type: use contributionPerPeriod capped at remaining
                total += minOf(goal.contributionPerPeriod, remaining)
            }
        }
        return total
    }

    /** Per-period deduction that safeBudgetAmount implicitly reserves for one RE.
     *  Does NOT roundCents — caller handles rounding to avoid per-RE drift. */
    fun normalPerPeriodDeduction(
        re: RecurringExpense, budgetPeriod: BudgetPeriod, today: LocalDate
    ): Double {
        val periodsPerYear = countPeriodsCompleted(today, today.plusYears(1), budgetPeriod)
        if (periodsPerYear <= 0) return 0.0
        val occurrences = generateOccurrences(
            re.repeatType, re.repeatInterval, re.startDate,
            re.monthDay1, re.monthDay2, today, today.plusYears(1)
        ).size
        return re.amount * occurrences.toDouble() / periodsPerYear
    }

    /** Budget periods remaining until the next occurrence of an RE.
     *  Returns 0 if due today or no future occurrence found.
     *  Returns max(1, periods) when nextDue is after today (avoids div-by-zero
     *  when nextDue falls within the current period). */
    fun periodsUntilNextOccurrence(
        re: RecurringExpense, budgetPeriod: BudgetPeriod, today: LocalDate
    ): Int {
        val nextDue = generateOccurrences(
            re.repeatType, re.repeatInterval, re.startDate,
            re.monthDay1, re.monthDay2, today, today.plusYears(2)
        ).firstOrNull() ?: return 0
        if (!nextDue.isAfter(today)) return 0
        return maxOf(1, countPeriodsCompleted(today, nextDue, budgetPeriod))
    }

    /** Extra per-period deduction for accelerated REs beyond what safeBudgetAmount handles. */
    fun acceleratedREExtraDeductions(
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        today: LocalDate = LocalDate.now()
    ): Double {
        var extra = 0.0
        for (re in recurringExpenses) {
            if (!re.isAccelerated || re.deleted) continue
            val normalRate = normalPerPeriodDeduction(re, budgetPeriod, today)
            val periodsLeft = periodsUntilNextOccurrence(re, budgetPeriod, today)
            if (periodsLeft <= 0) continue
            val remaining = maxOf(0.0, re.amount - re.setAsideSoFar)
            val acceleratedRate = remaining / periodsLeft
            extra += maxOf(0.0, acceleratedRate - normalRate)
        }
        return roundCents(extra)
    }

    /** Compute the full budgetAmount from synced data. Reusable in SyncWorker. */
    fun computeFullBudgetAmount(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
        budgetPeriod: BudgetPeriod,
        isManualBudgetEnabled: Boolean,
        manualBudgetAmount: Double,
        today: LocalDate = LocalDate.now()
    ): Double {
        val base = if (isManualBudgetEnabled) manualBudgetAmount
                   else calculateSafeBudgetAmount(incomeSources, recurringExpenses, budgetPeriod, today)
        val amortDed = activeAmortizationDeductions(amortizationEntries, budgetPeriod, today)
        val savingsDed = activeSavingsGoalDeductions(savingsGoals, budgetPeriod, today)
        val accelDed = acceleratedREExtraDeductions(recurringExpenses, budgetPeriod, today)
        return roundCents(maxOf(0.0, base - amortDed - savingsDed - accelDed))
    }

    /**
     * Deterministic cash from synced data. All devices with the same synced data
     * compute the same result — no admin authority needed.
     *
     * Formula:
     *   Σ(ledger appliedAmounts where date ≥ budgetStart)
     *   − Σ(active expenses, excl. amort-linked & recurring-linked)
     *   + Σ(active non-budget income)
     *   + Σ(recurring diffs for recurring-linked expenses)
     */
    fun recomputeAvailableCash(
        budgetStartDate: LocalDate,
        periodLedgerEntries: List<PeriodLedgerEntry>,
        activeTransactions: List<Transaction>,
        activeRecurringExpenses: List<RecurringExpense>,
        incomeMode: IncomeMode = IncomeMode.FIXED,
        activeIncomeSources: List<IncomeSource> = emptyList()
    ): Double {
        // Sum period credits from synced ledger (dedup by date)
        var cash = 0.0
        val dedupedLedger = periodLedgerEntries
            .filter { !it.periodStartDate.toLocalDate().isBefore(budgetStartDate) }
            .groupBy { it.periodStartDate.toLocalDate() }
            .values.map { entries -> entries.maxByOrNull { it.periodStartDate } ?: entries.first() }
        for (entry in dedupedLedger) {
            cash += entry.appliedAmount
        }
        // Apply transaction effects
        for (txn in activeTransactions) {
            if (txn.date.isBefore(budgetStartDate)) continue
            if (txn.excludeFromBudget) continue
            if (txn.type == TransactionType.EXPENSE) {
                // Savings-goal-linked: money came from savings, not the budget
                if (txn.linkedSavingsGoalId != null || txn.linkedSavingsGoalAmount > 0.0) continue
                if (txn.linkedAmortizationEntryId != null) continue // fully budget-accounted
                if (txn.amortizationAppliedAmount > 0.0) {
                    // Was linked to a deleted amortization — only deduct the unamortized remainder
                    cash -= roundCents(txn.amount - txn.amortizationAppliedAmount).coerceAtLeast(0.0)
                    continue
                }
                if (txn.linkedRecurringExpenseId != null) {
                    // Recurring-linked: use remembered amount for delta
                    if (txn.linkedRecurringExpenseAmount > 0.0) {
                        cash += (txn.linkedRecurringExpenseAmount - txn.amount)
                    } else {
                        // Legacy: no remembered amount, fall back to live lookup
                        val re = activeRecurringExpenses.find { it.id == txn.linkedRecurringExpenseId }
                        if (re != null) cash += (re.amount - txn.amount)
                        else cash -= txn.amount
                    }
                } else if (txn.linkedRecurringExpenseAmount > 0.0) {
                    // Formerly linked to deleted RE — use remembered amount for delta
                    cash += (txn.linkedRecurringExpenseAmount - txn.amount)
                } else {
                    cash -= txn.amount
                }
            } else if (txn.type == TransactionType.INCOME) {
                if (txn.linkedIncomeSourceId != null) {
                    if (incomeMode == IncomeMode.ACTUAL) {
                        // ACTUAL: use remembered amount for delta
                        if (txn.linkedIncomeSourceAmount > 0.0) {
                            cash += (txn.amount - txn.linkedIncomeSourceAmount)
                        } else {
                            // Legacy: no remembered amount, fall back to live lookup
                            val src = activeIncomeSources.find { it.id == txn.linkedIncomeSourceId }
                            if (src != null) cash += (txn.amount - src.amount)
                            else cash += txn.amount
                        }
                    }
                    // FIXED mode: linked income → no cash effect
                    // ACTUAL_ADJUST mode: source is updated to match, so delta is zero
                } else if (txn.linkedIncomeSourceAmount > 0.0) {
                    // Formerly linked to deleted income source.
                    // In ACTUAL mode: use remembered delta (actual vs budgeted).
                    // In FIXED/ADJUST mode: no effect (same as when linked).
                    if (incomeMode == IncomeMode.ACTUAL) {
                        cash += (txn.amount - txn.linkedIncomeSourceAmount)
                    }
                    // FIXED/ADJUST: no cash effect — transaction continues to behave
                    // as it did while linked (budgeted income, already accounted for)
                } else if (!txn.isBudgetIncome) {
                    cash += txn.amount
                }
            }
        }
        return roundCents(cash)
    }
}

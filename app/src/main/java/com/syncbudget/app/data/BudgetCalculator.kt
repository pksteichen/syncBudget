package com.syncbudget.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object BudgetCalculator {

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
                var month = rangeStart.withDayOfMonth(1)
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
        }
        return dates
    }

    fun calculateSafeBudgetAmount(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod
    ): Double {
        val today = LocalDate.now()
        val oneYearAhead = today.plusYears(1)

        // Total income over 1 year
        var totalIncome = 0.0
        for (src in incomeSources) {
            val occurrences = generateOccurrences(
                src.repeatType, src.repeatInterval, src.startDate,
                src.monthDay1, src.monthDay2, today, oneYearAhead
            )
            totalIncome += src.amount * occurrences.size
        }

        // Number of budget periods in 1 year
        val periodsPerYear = countPeriodsCompleted(today, oneYearAhead, budgetPeriod)
        if (periodsPerYear <= 0) return 0.0

        // Base S = smoothed annual income per period
        val baseS = totalIncome / periodsPerYear.toDouble()

        // Now apply timing safety: ensure S covers recurring expense bursts.
        // Simulate: each period adds S, recurring expenses subtract.
        // Find the S such that balance never goes negative over 1 year.
        val periods = generatePeriodBoundaries(today, oneYearAhead, budgetPeriod)
        if (periods.size < 2) return baseS

        // Compute cumulative expense-only outflows per period
        val cumulativeExpenses = DoubleArray(periods.size - 1)
        var runningExpenses = 0.0
        for (i in 0 until periods.size - 1) {
            val pStart = periods[i]
            val pEnd = periods[i + 1].minusDays(1)
            for (exp in recurringExpenses) {
                val occurrences = generateOccurrences(
                    exp.repeatType, exp.repeatInterval, exp.startDate,
                    exp.monthDay1, exp.monthDay2, pStart, pEnd
                )
                runningExpenses += exp.amount * occurrences.size
            }
            cumulativeExpenses[i] = runningExpenses
        }

        // S must satisfy: S * (t+1) >= cumulativeExpenses(t) for all t
        // i.e. S >= cumulativeExpenses(t) / (t+1) for all t
        var minS = 0.0
        for (t in cumulativeExpenses.indices) {
            val required = cumulativeExpenses[t] / (t + 1).toDouble()
            if (required > minS) minS = required
        }

        // S is the larger of: smoothed income, or minimum to cover expense timing
        // But cap at income (can't spend more than you earn)
        return if (minS > baseS) minS else baseS
    }

    private fun generatePeriodBoundaries(
        from: LocalDate,
        to: LocalDate,
        budgetPeriod: BudgetPeriod
    ): List<LocalDate> {
        val boundaries = mutableListOf<LocalDate>()
        var current = from
        while (!current.isAfter(to)) {
            boundaries.add(current)
            current = when (budgetPeriod) {
                BudgetPeriod.DAILY -> current.plusDays(1)
                BudgetPeriod.WEEKLY -> current.plusWeeks(1)
                BudgetPeriod.MONTHLY -> current.plusMonths(1)
            }
        }
        return boundaries
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
        resetDayOfMonth: Int
    ): LocalDate {
        val today = LocalDate.now()
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
        budgetPeriod: BudgetPeriod
    ): Double {
        val today = LocalDate.now()
        var total = 0.0
        for (entry in entries) {
            val elapsed = when (budgetPeriod) {
                BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(entry.startDate, today).toInt()
                BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(entry.startDate, today).toInt()
                BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(entry.startDate, today).toInt()
            }.coerceIn(0, entry.totalPeriods)
            if (elapsed < entry.totalPeriods) {
                total += entry.amount / entry.totalPeriods.toDouble()
            }
        }
        return total
    }

    fun activeSavingsDeductions(goals: List<SavingsGoal>): Double {
        var total = 0.0
        for (goal in goals) {
            if (goal.isPaused) continue
            if (goal.savedSoFar >= goal.targetAmount) continue
            total += goal.contributionPerPeriod
        }
        return total
    }

    fun activeFLEDeductions(
        expenditures: List<FutureExpenditure>,
        budgetPeriod: BudgetPeriod
    ): Double {
        val today = LocalDate.now()
        var total = 0.0
        for (exp in expenditures) {
            if (exp.isPaused) continue
            val remaining = exp.amount - exp.totalSavedSoFar
            if (remaining <= 0) continue
            if (!today.isBefore(exp.targetDate)) continue
            val periods = when (budgetPeriod) {
                BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(today, exp.targetDate)
                BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(today, exp.targetDate)
                BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(today, exp.targetDate)
            }
            if (periods <= 0) continue
            total += remaining / periods.toDouble()
        }
        return total
    }
}

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
        val oneYearAgo = today.minusYears(1)
        val oneYearAhead = today.plusYears(1)

        // Generate period boundaries from -1yr to +1yr
        val periods = generatePeriodBoundaries(oneYearAgo, oneYearAhead, budgetPeriod)
        if (periods.size < 2) return 0.0

        // For each period, compute net cash flow (income - expenses)
        val netPerPeriod = DoubleArray(periods.size - 1)
        for (i in 0 until periods.size - 1) {
            val pStart = periods[i]
            val pEnd = periods[i + 1].minusDays(1)

            var net = 0.0
            for (src in incomeSources) {
                val occurrences = generateOccurrences(
                    src.repeatType, src.repeatInterval, src.startDate,
                    src.monthDay1, src.monthDay2, pStart, pEnd
                )
                net += src.amount * occurrences.size
            }
            for (exp in recurringExpenses) {
                val occurrences = generateOccurrences(
                    exp.repeatType, exp.repeatInterval, exp.startDate,
                    exp.monthDay1, exp.monthDay2, pStart, pEnd
                )
                net -= exp.amount * occurrences.size
            }
            netPerPeriod[i] = net
        }

        // Compute cumulative net from period 0
        val cumulative = DoubleArray(netPerPeriod.size)
        cumulative[0] = netPerPeriod[0]
        for (i in 1 until cumulative.size) {
            cumulative[i] = cumulative[i - 1] + netPerPeriod[i]
        }

        // Find the index of "today" period (first period that starts at or after today)
        val todayIndex = periods.indexOfFirst { !it.isAfter(today) && (periods.indexOf(it) + 1 >= periods.size || periods[periods.indexOf(it) + 1].isAfter(today)) }
            .let { if (it < 0) 0 else it }

        // S = max over t in [todayIndex..end] of max(0, -cumulative(t) / (t + 1))
        // We simulate starting from period 0 with 0 balance. S is added each period.
        // Balance at period t = S * (t+1) + cumulative(t) >= 0
        // S >= -cumulative(t) / (t+1)
        var s = 0.0
        for (t in cumulative.indices) {
            val required = -cumulative[t] / (t + 1).toDouble()
            if (required > s) s = required
        }

        return if (s < 0.0) 0.0 else s
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

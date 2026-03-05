package com.syncbudget.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object SavingsSimulator {

    data class SimResult(
        val savingsRequired: Double,
        val lowPointDate: LocalDate?
    )

    private data class CashEvent(
        val date: LocalDate,
        val amount: Double,
        val priority: Int   // 0 = income, 1 = period deduction, 2 = expense
    )

    /**
     * Forward-looking cash-flow simulation.
     *
     * Walks from today to the furthest upcoming income/expense occurrence,
     * applying actual income deposits, expense withdrawals, and per-period
     * discretionary spending.  Returns how much savings buffer the user
     * needs so the simulated balance never goes negative.
     */
    fun calculateSavingsRequired(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        budgetAmount: Double,
        availableCash: Double,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        today: LocalDate = LocalDate.now()
    ): SimResult {
        val twoYearsAhead = today.plusYears(2)

        // --- 1. Determine horizon (furthest next occurrence) ---
        var horizon = today
        for (src in incomeSources) {
            val occ = BudgetCalculator.generateOccurrences(
                src.repeatType, src.repeatInterval, src.startDate,
                src.monthDay1, src.monthDay2, today, twoYearsAhead
            ).firstOrNull()
            if (occ != null && occ.isAfter(horizon)) horizon = occ
        }
        for (exp in recurringExpenses) {
            val occ = BudgetCalculator.generateOccurrences(
                exp.repeatType, exp.repeatInterval, exp.startDate,
                exp.monthDay1, exp.monthDay2, today, twoYearsAhead
            ).firstOrNull()
            if (occ != null && occ.isAfter(horizon)) horizon = occ
        }
        // Nothing upcoming beyond today — just need availableCash for today
        if (horizon == today) return SimResult(BudgetCalculator.roundCents(maxOf(0.0, availableCash)), today)

        // --- 2. Build events ---
        val events = mutableListOf<CashEvent>()

        // Day-0: user needs availableCash for today's spending
        events.add(CashEvent(today, -availableCash, priority = 1))

        // Income occurrences
        for (src in incomeSources) {
            for (date in BudgetCalculator.generateOccurrences(
                src.repeatType, src.repeatInterval, src.startDate,
                src.monthDay1, src.monthDay2, today, horizon
            )) {
                events.add(CashEvent(date, +src.amount, priority = 0))
            }
        }

        // Expense occurrences
        for (exp in recurringExpenses) {
            for (date in BudgetCalculator.generateOccurrences(
                exp.repeatType, exp.repeatInterval, exp.startDate,
                exp.monthDay1, exp.monthDay2, today, horizon
            )) {
                events.add(CashEvent(date, -exp.amount, priority = 2))
            }
        }

        // Period-boundary deductions (after today)
        for (date in generatePeriodBoundaries(today, horizon, budgetPeriod, resetDayOfWeek, resetDayOfMonth)) {
            events.add(CashEvent(date, -budgetAmount, priority = 1))
        }

        // --- 3. Sort: by date, then priority (income first) ---
        events.sortWith(compareBy<CashEvent> { it.date }.thenBy { it.priority })

        // --- 4. Walk timeline, track minimum balance ---
        var balance = 0.0
        var minBalance = 0.0
        var minDate: LocalDate = today
        for (event in events) {
            balance += event.amount
            if (balance < minBalance) {
                minBalance = balance
                minDate = event.date
            }
        }

        // --- 5. Savings required = depth of the trough ---
        val required = BudgetCalculator.roundCents(maxOf(0.0, -minBalance))
        return SimResult(required, if (required > 0.0) minDate else null)
    }

    /**
     * Generate period-start dates strictly after [after] up to [horizon].
     */
    private fun generatePeriodBoundaries(
        after: LocalDate,
        horizon: LocalDate,
        budgetPeriod: BudgetPeriod,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int
    ): List<LocalDate> {
        val boundaries = mutableListOf<LocalDate>()
        var d = when (budgetPeriod) {
            BudgetPeriod.DAILY -> after.plusDays(1)
            BudgetPeriod.WEEKLY -> {
                val target = DayOfWeek.of(resetDayOfWeek)
                after.with(TemporalAdjusters.next(target))
            }
            BudgetPeriod.MONTHLY -> {
                val day = resetDayOfMonth.coerceAtMost(after.lengthOfMonth())
                val candidate = after.withDayOfMonth(day)
                if (candidate.isAfter(after)) candidate
                else {
                    val nextMonth = after.plusMonths(1)
                    nextMonth.withDayOfMonth(resetDayOfMonth.coerceAtMost(nextMonth.lengthOfMonth()))
                }
            }
        }
        while (!d.isAfter(horizon)) {
            boundaries.add(d)
            d = when (budgetPeriod) {
                BudgetPeriod.DAILY -> d.plusDays(1)
                BudgetPeriod.WEEKLY -> d.plusDays(7)
                BudgetPeriod.MONTHLY -> {
                    val next = d.plusMonths(1)
                    next.withDayOfMonth(resetDayOfMonth.coerceAtMost(next.lengthOfMonth()))
                }
            }
        }
        return boundaries
    }
}

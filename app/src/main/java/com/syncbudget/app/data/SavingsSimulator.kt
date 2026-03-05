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

    /**
     * Produces a human-readable trace of the simulation for debugging.
     */
    fun traceSimulation(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        budgetAmount: Double,
        availableCash: Double,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        currencySymbol: String = "$",
        today: LocalDate = LocalDate.now()
    ): String {
        val sb = StringBuilder()
        val fmt = { v: Double -> "%,.2f".format(v) }

        sb.appendLine("=== SAVINGS SIMULATION TRACE ===")
        sb.appendLine("Run date: $today")
        sb.appendLine()

        // --- Inputs ---
        sb.appendLine("── INPUTS ──")
        sb.appendLine("Budget period:     $budgetPeriod")
        sb.appendLine("Budget amount:     ${currencySymbol}${fmt(budgetAmount)} per period")
        sb.appendLine("Available cash:    ${currencySymbol}${fmt(availableCash)} (today's remaining)")
        sb.appendLine("Reset day (week):  $resetDayOfWeek")
        sb.appendLine("Reset day (month): $resetDayOfMonth")
        sb.appendLine()

        sb.appendLine("Income Sources (${incomeSources.size}):")
        if (incomeSources.isEmpty()) sb.appendLine("  (none)")
        incomeSources.forEach { src ->
            sb.appendLine("  • ${src.source}: ${currencySymbol}${fmt(src.amount)}  repeat=${src.repeatType}/${src.repeatInterval}  start=${src.startDate}  m1=${src.monthDay1} m2=${src.monthDay2}")
        }
        sb.appendLine()

        sb.appendLine("Recurring Expenses (${recurringExpenses.size}):")
        if (recurringExpenses.isEmpty()) sb.appendLine("  (none)")
        recurringExpenses.forEach { exp ->
            sb.appendLine("  • ${exp.source}: ${currencySymbol}${fmt(exp.amount)}  repeat=${exp.repeatType}/${exp.repeatInterval}  start=${exp.startDate}  m1=${exp.monthDay1} m2=${exp.monthDay2}")
        }
        sb.appendLine()

        // --- Horizon ---
        val twoYearsAhead = today.plusYears(2)
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

        sb.appendLine("── HORIZON ──")
        sb.appendLine("Horizon date: $horizon")
        if (horizon == today) {
            sb.appendLine("Nothing upcoming. Savings required = ${currencySymbol}${fmt(maxOf(0.0, availableCash))}")
            return sb.toString()
        }
        sb.appendLine()

        // --- Build events ---
        val events = mutableListOf<CashEvent>()
        events.add(CashEvent(today, -availableCash, priority = 1))

        for (src in incomeSources) {
            for (date in BudgetCalculator.generateOccurrences(
                src.repeatType, src.repeatInterval, src.startDate,
                src.monthDay1, src.monthDay2, today, horizon
            )) {
                events.add(CashEvent(date, +src.amount, priority = 0))
            }
        }
        for (exp in recurringExpenses) {
            for (date in BudgetCalculator.generateOccurrences(
                exp.repeatType, exp.repeatInterval, exp.startDate,
                exp.monthDay1, exp.monthDay2, today, horizon
            )) {
                events.add(CashEvent(date, -exp.amount, priority = 2))
            }
        }
        for (date in generatePeriodBoundaries(today, horizon, budgetPeriod, resetDayOfWeek, resetDayOfMonth)) {
            events.add(CashEvent(date, -budgetAmount, priority = 1))
        }

        sb.appendLine("── EVENTS (${events.size} total) ──")
        sb.appendLine()

        // --- Sort ---
        events.sortWith(compareBy<CashEvent> { it.date }.thenBy { it.priority })

        // --- Walk timeline ---
        sb.appendLine(String.format("%-12s  %-8s  %14s  %14s  %s", "Date", "Type", "Amount", "Balance", "Description"))
        sb.appendLine("-".repeat(80))

        var balance = 0.0
        var minBalance = 0.0
        var minDate = today

        for (event in events) {
            balance += event.amount
            if (balance < minBalance) {
                minBalance = balance
                minDate = event.date
            }
            val typeLabel = when (event.priority) {
                0 -> "INCOME"
                1 -> "BUDGET"
                2 -> "EXPENSE"
                else -> "?"
            }
            val sign = if (event.amount >= 0) "+" else ""
            val marker = if (balance == minBalance && minBalance < 0) " ◄ LOW" else ""
            sb.appendLine(String.format(
                "%-12s  %-8s  %14s  %14s%s",
                event.date, typeLabel,
                "$sign${currencySymbol}${fmt(event.amount)}",
                "${currencySymbol}${fmt(balance)}",
                marker
            ))
        }

        sb.appendLine()
        sb.appendLine("── RESULT ──")
        val required = BudgetCalculator.roundCents(maxOf(0.0, -minBalance))
        sb.appendLine("Minimum balance:   ${currencySymbol}${fmt(minBalance)}  on $minDate")
        sb.appendLine("Savings required:  ${currencySymbol}${fmt(required)}")
        if (required <= 0.0) {
            sb.appendLine("No savings buffer needed — income covers all obligations.")
        }

        return sb.toString()
    }
}

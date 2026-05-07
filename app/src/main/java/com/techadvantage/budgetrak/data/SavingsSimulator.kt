package com.techadvantage.budgetrak.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object SavingsSimulator {

    data class SimResult(
        val savingsRequired: Double,
        val lowPointDate: LocalDate?
    )

    data class SimulationPoint(val date: LocalDate, val balance: Double)

    private data class CashEvent(
        val date: LocalDate,
        val amount: Double,
        val priority: Int,  // 0 = income, 1 = period deduction, 2 = expense
        val label: String = ""
    )

    /**
     * Build and sort all simulation events. Returns null if horizon == today
     * (nothing to simulate).
     */
    private fun buildSortedEvents(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        baseBudget: Double,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
        availableCash: Double,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        today: LocalDate,
        floorOut: MutableList<SimulationPoint>? = null
    ): MutableList<CashEvent>? {
        val horizon = today.plusMonths(18)

        // Check that at least one income or expense exists to simulate
        val hasIncome = incomeSources.any { BudgetCalculator.generateOccurrences(
            it.repeatType, it.repeatInterval, it.startDate,
            it.monthDay1, it.monthDay2, today, horizon
        ).isNotEmpty() }
        val hasExpenses = recurringExpenses.any { BudgetCalculator.generateOccurrences(
            it.repeatType, it.repeatInterval, it.startDate,
            it.monthDay1, it.monthDay2, today, horizon
        ).isNotEmpty() }
        if (!hasIncome && !hasExpenses) return null

        val events = mutableListOf<CashEvent>()
        // Neutralize the current period's SG deduction in today's draw —
        // adding/removing a goal should affect the floor + future periods,
        // not artificially shift today's spendable. AE and accelerated-RE
        // deductions are intentionally NOT neutralized: those represent real
        // upcoming outflows already shaping this period's budget.
        val currentSGDed = BudgetCalculator.activeSavingsGoalDeductions(
            savingsGoals, budgetPeriod, today, resetDayOfWeek
        )
        events.add(CashEvent(today, -(availableCash + currentSGDed), priority = 1))

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

        val boundaries = generatePeriodBoundaries(today, horizon, budgetPeriod, resetDayOfWeek, resetDayOfMonth)
        addDynamicBudgetEvents(events, boundaries, baseBudget, budgetPeriod,
            amortizationEntries, savingsGoals, recurringExpenses, today, floorOut)

        events.sortWith(compareBy<CashEvent> { it.date }.thenBy { it.priority })
        return events
    }

    fun calculateSavingsRequired(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        baseBudget: Double,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
        availableCash: Double,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        today: LocalDate = LocalDate.now()
    ): SimResult {
        // Need = the cash you must hold so that, at every point in the
        // 18-month projection, your cash balance never dips below the
        // savings-goal floor (which itself rises as goals accrue). Computed
        // as max over t of (floor_at_t − balance_at_t).
        val initialFloor = savingsGoals
            .filter { !it.deleted }
            .sumOf { it.totalSavedSoFar }
        val floorTimeline = mutableListOf(SimulationPoint(today, initialFloor))

        val events = buildSortedEvents(
            incomeSources, recurringExpenses, budgetPeriod, baseBudget,
            amortizationEntries, savingsGoals, availableCash,
            resetDayOfWeek, resetDayOfMonth, today, floorTimeline
        ) ?: return SimResult(
            BudgetCalculator.roundCents(maxOf(0.0, availableCash) + initialFloor),
            if (initialFloor > 0.0 || availableCash > 0.0) today else null
        )

        val gap = walkEventsForMaxGap(events, floorTimeline, initialFloor, today)
        val required = BudgetCalculator.roundCents(maxOf(0.0, gap.maxGap))
        return SimResult(required, if (required > 0.0) gap.maxGapDate else null)
    }

    private data class GapResult(val maxGap: Double, val maxGapDate: LocalDate)

    /** Walk events alongside the rising floor; return the worst (floor − balance) point. */
    private fun walkEventsForMaxGap(
        events: List<CashEvent>,
        floorTimeline: List<SimulationPoint>,
        initialFloor: Double,
        today: LocalDate
    ): GapResult {
        var balance = 0.0
        var floor = initialFloor
        // Initial gap at today, before any events.
        var maxGap = initialFloor
        var maxGapDate = today
        // floorTimeline[0] is (today, initialFloor); subsequent entries are
        // (boundary, prevFloor) and (boundary, newFloor) staircase pairs.
        var floorIdx = 1

        for (event in events) {
            // Advance floor through any step at-or-before the event's date.
            while (floorIdx < floorTimeline.size &&
                   !floorTimeline[floorIdx].date.isAfter(event.date)) {
                floor = floorTimeline[floorIdx].balance
                floorIdx++
            }
            balance += event.amount
            val gap = floor - balance
            if (gap > maxGap) {
                maxGap = gap
                maxGapDate = event.date
            }
        }
        return GapResult(maxGap, maxGapDate)
    }

    /**
     * Run the simulation and return both the result and a timeline of
     * (date, balance) points for graphing.
     */
    fun simulateTimeline(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        baseBudget: Double,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
        availableCash: Double,
        resetDayOfWeek: Int,
        resetDayOfMonth: Int,
        today: LocalDate = LocalDate.now()
    ): Triple<SimResult, List<SimulationPoint>, List<SimulationPoint>> {
        val initialFloor = savingsGoals
            .filter { !it.deleted }
            .sumOf { it.totalSavedSoFar }
        val floorTimeline = mutableListOf(SimulationPoint(today, initialFloor))

        val events = buildSortedEvents(
            incomeSources, recurringExpenses, budgetPeriod, baseBudget,
            amortizationEntries, savingsGoals, availableCash,
            resetDayOfWeek, resetDayOfMonth, today, floorTimeline
        ) ?: return Triple(
            SimResult(
                BudgetCalculator.roundCents(maxOf(0.0, availableCash) + initialFloor),
                if (initialFloor > 0.0 || availableCash > 0.0) today else null
            ),
            listOf(SimulationPoint(today, 0.0)),
            floorTimeline
        )

        val timeline = mutableListOf(SimulationPoint(today, 0.0))
        var balance = 0.0
        for (event in events) {
            balance += event.amount
            timeline.add(SimulationPoint(event.date, balance))
        }

        // Need = max over t of (floor_at_t − balance_at_t). Sizes Need so
        // cash stays at-or-above the rising floor at every point.
        val gap = walkEventsForMaxGap(events, floorTimeline, initialFloor, today)
        val required = BudgetCalculator.roundCents(maxOf(0.0, gap.maxGap))
        return Triple(
            SimResult(required, if (required > 0.0) gap.maxGapDate else null),
            timeline,
            floorTimeline
        )
    }

    private fun addDynamicBudgetEvents(
        events: MutableList<CashEvent>,
        boundaries: List<LocalDate>,
        baseBudget: Double,
        budgetPeriod: BudgetPeriod,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
        recurringExpenses: List<RecurringExpense>,
        today: LocalDate,
        floorOut: MutableList<SimulationPoint>? = null
    ) {
        if (boundaries.isEmpty()) return

        val simGoalSaved = savingsGoals.map { it.totalSavedSoFar }.toDoubleArray()
        val simRESetAside = recurringExpenses.map { it.setAsideSoFar }.toDoubleArray()
        val simREAccelerated = recurringExpenses.map { it.isAccelerated }.toBooleanArray()

        fun nonDeletedFloor(): Double = savingsGoals.indices
            .filter { !savingsGoals[it].deleted }
            .sumOf { simGoalSaved[it] }
        var prevFloor = nonDeletedFloor()

        var prevDate = today
        for (boundary in boundaries) {
            // Carry the previous floor flat up to this boundary (step shape)
            floorOut?.add(SimulationPoint(boundary, prevFloor))

            recurringExpenses.forEachIndexed { i, re ->
                if (re.deleted) return@forEachIndexed
                val dueDates = BudgetCalculator.generateOccurrences(
                    re.repeatType, re.repeatInterval, re.startDate,
                    re.monthDay1, re.monthDay2,
                    prevDate.plusDays(1), boundary
                )
                if (dueDates.isNotEmpty()) {
                    simRESetAside[i] = 0.0
                    simREAccelerated[i] = false
                }
            }

            val amortDed = BudgetCalculator.activeAmortizationDeductions(
                amortizationEntries, budgetPeriod, boundary
            )

            var savingsDed = 0.0
            savingsGoals.forEachIndexed { i, goal ->
                if (goal.deleted || goal.isPaused) return@forEachIndexed
                if (goal.totalSavedSoFar >= goal.targetAmount) return@forEachIndexed
                val remaining = goal.targetAmount - simGoalSaved[i]
                if (remaining <= 0) return@forEachIndexed
                val ded = if (goal.targetDate != null) {
                    if (!boundary.isBefore(goal.targetDate)) return@forEachIndexed
                    val periods = BudgetCalculator.countPeriodsCompleted(boundary, goal.targetDate, budgetPeriod)
                    if (periods <= 0) return@forEachIndexed
                    BudgetCalculator.roundCents(remaining / periods.toDouble())
                } else {
                    minOf(goal.contributionPerPeriod, remaining)
                }
                savingsDed += ded
                simGoalSaved[i] = minOf(simGoalSaved[i] + ded, goal.targetAmount)
            }

            var accelDed = 0.0
            recurringExpenses.forEachIndexed { i, re ->
                if (!simREAccelerated[i] || re.deleted) return@forEachIndexed
                val normalRate = BudgetCalculator.normalPerPeriodDeduction(re, budgetPeriod, boundary)
                val nextDue = BudgetCalculator.generateOccurrences(
                    re.repeatType, re.repeatInterval, re.startDate,
                    re.monthDay1, re.monthDay2, boundary, boundary.plusYears(2)
                ).firstOrNull()
                if (nextDue == null || !nextDue.isAfter(boundary)) return@forEachIndexed
                val periodsLeft = maxOf(1, BudgetCalculator.countPeriodsCompleted(boundary, nextDue, budgetPeriod))
                val remaining = maxOf(0.0, re.amount - simRESetAside[i])
                val acceleratedRate = remaining / periodsLeft
                val extra = maxOf(0.0, acceleratedRate - normalRate)
                accelDed += extra
                simRESetAside[i] = minOf(simRESetAside[i] + acceleratedRate, re.amount)
            }
            accelDed = BudgetCalculator.roundCents(accelDed)

            val effectiveBudget = BudgetCalculator.roundCents(
                maxOf(0.0, baseBudget - amortDed - savingsDed - accelDed)
            )
            events.add(CashEvent(boundary, -effectiveBudget, priority = 1))

            // Step up to the new floor at the same boundary date
            val newFloor = nonDeletedFloor()
            if (newFloor != prevFloor) {
                floorOut?.add(SimulationPoint(boundary, newFloor))
                prevFloor = newFloor
            }

            prevDate = boundary
        }
    }

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

    fun traceSimulation(
        incomeSources: List<IncomeSource>,
        recurringExpenses: List<RecurringExpense>,
        budgetPeriod: BudgetPeriod,
        baseBudget: Double,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
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

        sb.appendLine("── INPUTS ──")
        sb.appendLine("Budget period:     $budgetPeriod")
        sb.appendLine("Base budget:       ${currencySymbol}${fmt(baseBudget)} per period (before deductions)")
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
            val accelTag = if (exp.isAccelerated) "  [ACCEL setAside=${fmt(exp.setAsideSoFar)}]" else ""
            sb.appendLine("  • ${exp.source}: ${currencySymbol}${fmt(exp.amount)}  repeat=${exp.repeatType}/${exp.repeatInterval}  start=${exp.startDate}  m1=${exp.monthDay1} m2=${exp.monthDay2}$accelTag")
        }
        sb.appendLine()

        sb.appendLine("Amortization Entries (${amortizationEntries.size}):")
        if (amortizationEntries.isEmpty()) sb.appendLine("  (none)")
        amortizationEntries.forEach { ae ->
            val pauseTag = if (ae.isPaused) " [PAUSED]" else ""
            sb.appendLine("  • ${ae.source}: ${currencySymbol}${fmt(ae.amount)}  periods=${ae.totalPeriods}  start=${ae.startDate}$pauseTag")
        }
        sb.appendLine()

        sb.appendLine("Savings Goals (${savingsGoals.size}):")
        if (savingsGoals.isEmpty()) sb.appendLine("  (none)")
        savingsGoals.forEach { goal ->
            val pauseTag = if (goal.isPaused) " [PAUSED]" else ""
            val typeTag = if (goal.targetDate != null) "by ${goal.targetDate}" else "${currencySymbol}${fmt(goal.contributionPerPeriod)}/period"
            sb.appendLine("  • ${goal.name}: ${currencySymbol}${fmt(goal.targetAmount)} ($typeTag)  saved=${currencySymbol}${fmt(goal.totalSavedSoFar)}$pauseTag")
        }
        sb.appendLine()

        val horizon = today.plusMonths(18)

        val hasIncome = incomeSources.any { BudgetCalculator.generateOccurrences(
            it.repeatType, it.repeatInterval, it.startDate,
            it.monthDay1, it.monthDay2, today, horizon
        ).isNotEmpty() }
        val hasExpenses = recurringExpenses.any { BudgetCalculator.generateOccurrences(
            it.repeatType, it.repeatInterval, it.startDate,
            it.monthDay1, it.monthDay2, today, horizon
        ).isNotEmpty() }

        val initialFloor = savingsGoals
            .filter { !it.deleted }
            .sumOf { it.totalSavedSoFar }

        sb.appendLine("── HORIZON ──")
        sb.appendLine("Horizon date: $horizon")
        if (!hasIncome && !hasExpenses) {
            val fallback = BudgetCalculator.roundCents(maxOf(0.0, availableCash) + initialFloor)
            sb.appendLine("Nothing upcoming. Savings required = ${currencySymbol}${fmt(fallback)}")
            return sb.toString()
        }
        sb.appendLine()

        val currentSGDed = BudgetCalculator.activeSavingsGoalDeductions(
            savingsGoals, budgetPeriod, today, resetDayOfWeek
        )
        val events = mutableListOf<CashEvent>()
        events.add(CashEvent(today, -(availableCash + currentSGDed), priority = 1, label = "Today's spending"))

        for (src in incomeSources) {
            for (date in BudgetCalculator.generateOccurrences(
                src.repeatType, src.repeatInterval, src.startDate,
                src.monthDay1, src.monthDay2, today, horizon
            )) {
                events.add(CashEvent(date, +src.amount, priority = 0, label = src.source))
            }
        }
        for (exp in recurringExpenses) {
            for (date in BudgetCalculator.generateOccurrences(
                exp.repeatType, exp.repeatInterval, exp.startDate,
                exp.monthDay1, exp.monthDay2, today, horizon
            )) {
                events.add(CashEvent(date, -exp.amount, priority = 2, label = exp.source))
            }
        }

        val boundaries = generatePeriodBoundaries(today, horizon, budgetPeriod, resetDayOfWeek, resetDayOfMonth)
        val floorTimeline = mutableListOf(SimulationPoint(today, initialFloor))
        val budgetBreakdowns = addDynamicBudgetEventsWithTrace(
            events, boundaries, baseBudget, budgetPeriod,
            amortizationEntries, savingsGoals, recurringExpenses, today, floorTimeline
        )

        sb.appendLine("── EVENTS (${events.size} total) ──")
        sb.appendLine()

        events.sortWith(compareBy<CashEvent> { it.date }.thenBy { it.priority })

        sb.appendLine(String.format("%-12s  %-8s  %14s  %14s  %14s  %s",
            "Date", "Type", "Amount", "Balance", "Floor", "Description"))
        sb.appendLine("-".repeat(110))

        var balance = 0.0
        var floor = initialFloor
        var maxGap = initialFloor
        var maxGapDate = today
        var floorIdx = 1

        for (event in events) {
            while (floorIdx < floorTimeline.size &&
                   !floorTimeline[floorIdx].date.isAfter(event.date)) {
                floor = floorTimeline[floorIdx].balance
                floorIdx++
            }
            balance += event.amount
            val gap = floor - balance
            val isWorst = gap > maxGap
            if (isWorst) {
                maxGap = gap
                maxGapDate = event.date
            }
            val typeLabel = when (event.priority) {
                0 -> "INCOME"
                1 -> "BUDGET"
                2 -> "EXPENSE"
                else -> "?"
            }
            val sign = if (event.amount >= 0) "+" else ""
            val marker = if (gap == maxGap && maxGap > 0) " << GAP" else ""
            val desc = if (event.label.isNotEmpty()) "  ${event.label}" else ""
            val breakdown = if (event.priority == 1 && event.label.isEmpty()) {
                budgetBreakdowns[event.date]?.let { "  $it" } ?: ""
            } else desc
            sb.appendLine(String.format(
                "%-12s  %-8s  %14s  %14s  %14s%s%s",
                event.date, typeLabel,
                "$sign${currencySymbol}${fmt(event.amount)}",
                "${currencySymbol}${fmt(balance)}",
                "${currencySymbol}${fmt(floor)}",
                marker, breakdown
            ))
        }

        sb.appendLine()
        sb.appendLine("── RESULT ──")
        val required = BudgetCalculator.roundCents(maxOf(0.0, maxGap))
        sb.appendLine("Worst (floor − balance): ${currencySymbol}${fmt(maxGap)}  on $maxGapDate")
        sb.appendLine("Initial SG floor: ${currencySymbol}${fmt(initialFloor)}")
        sb.appendLine("Savings required:  ${currencySymbol}${fmt(required)}")
        if (required <= 0.0) {
            sb.appendLine("No savings buffer needed — income covers all obligations and floors.")
        }

        return sb.toString()
    }

    private fun addDynamicBudgetEventsWithTrace(
        events: MutableList<CashEvent>,
        boundaries: List<LocalDate>,
        baseBudget: Double,
        budgetPeriod: BudgetPeriod,
        amortizationEntries: List<AmortizationEntry>,
        savingsGoals: List<SavingsGoal>,
        recurringExpenses: List<RecurringExpense>,
        today: LocalDate,
        floorOut: MutableList<SimulationPoint>? = null
    ): Map<LocalDate, String> {
        val breakdowns = mutableMapOf<LocalDate, String>()
        if (boundaries.isEmpty()) return breakdowns

        val fmt = { v: Double -> "%,.2f".format(v) }
        val simGoalSaved = savingsGoals.map { it.totalSavedSoFar }.toDoubleArray()
        val simRESetAside = recurringExpenses.map { it.setAsideSoFar }.toDoubleArray()
        val simREAccelerated = recurringExpenses.map { it.isAccelerated }.toBooleanArray()

        fun nonDeletedFloor(): Double = savingsGoals.indices
            .filter { !savingsGoals[it].deleted }
            .sumOf { simGoalSaved[it] }
        var prevFloor = nonDeletedFloor()

        var prevDate = today
        for (boundary in boundaries) {
            floorOut?.add(SimulationPoint(boundary, prevFloor))
            recurringExpenses.forEachIndexed { i, re ->
                if (re.deleted) return@forEachIndexed
                val dueDates = BudgetCalculator.generateOccurrences(
                    re.repeatType, re.repeatInterval, re.startDate,
                    re.monthDay1, re.monthDay2,
                    prevDate.plusDays(1), boundary
                )
                if (dueDates.isNotEmpty()) {
                    simRESetAside[i] = 0.0
                    simREAccelerated[i] = false
                }
            }

            val amortDed = BudgetCalculator.activeAmortizationDeductions(
                amortizationEntries, budgetPeriod, boundary
            )

            var savingsDed = 0.0
            savingsGoals.forEachIndexed { i, goal ->
                if (goal.deleted || goal.isPaused) return@forEachIndexed
                if (goal.totalSavedSoFar >= goal.targetAmount) return@forEachIndexed
                val remaining = goal.targetAmount - simGoalSaved[i]
                if (remaining <= 0) return@forEachIndexed
                val ded = if (goal.targetDate != null) {
                    if (!boundary.isBefore(goal.targetDate)) return@forEachIndexed
                    val periods = BudgetCalculator.countPeriodsCompleted(boundary, goal.targetDate, budgetPeriod)
                    if (periods <= 0) return@forEachIndexed
                    BudgetCalculator.roundCents(remaining / periods.toDouble())
                } else {
                    minOf(goal.contributionPerPeriod, remaining)
                }
                savingsDed += ded
                simGoalSaved[i] = minOf(simGoalSaved[i] + ded, goal.targetAmount)
            }

            var accelDed = 0.0
            recurringExpenses.forEachIndexed { i, re ->
                if (!simREAccelerated[i] || re.deleted) return@forEachIndexed
                val normalRate = BudgetCalculator.normalPerPeriodDeduction(re, budgetPeriod, boundary)
                val nextDue = BudgetCalculator.generateOccurrences(
                    re.repeatType, re.repeatInterval, re.startDate,
                    re.monthDay1, re.monthDay2, boundary, boundary.plusYears(2)
                ).firstOrNull()
                if (nextDue == null || !nextDue.isAfter(boundary)) return@forEachIndexed
                val periodsLeft = maxOf(1, BudgetCalculator.countPeriodsCompleted(boundary, nextDue, budgetPeriod))
                val remaining = maxOf(0.0, re.amount - simRESetAside[i])
                val acceleratedRate = remaining / periodsLeft
                val extra = maxOf(0.0, acceleratedRate - normalRate)
                accelDed += extra
                simRESetAside[i] = minOf(simRESetAside[i] + acceleratedRate, re.amount)
            }
            accelDed = BudgetCalculator.roundCents(accelDed)

            val effectiveBudget = BudgetCalculator.roundCents(
                maxOf(0.0, baseBudget - amortDed - savingsDed - accelDed)
            )
            events.add(CashEvent(boundary, -effectiveBudget, priority = 1))

            val parts = mutableListOf<String>()
            parts.add("base=${fmt(baseBudget)}")
            if (amortDed > 0) parts.add("amort=-${fmt(amortDed)}")
            if (savingsDed > 0) parts.add("savings=-${fmt(savingsDed)}")
            if (accelDed > 0) parts.add("accel=-${fmt(accelDed)}")
            breakdowns[boundary] = "[${parts.joinToString(" ")}]"

            val newFloor = nonDeletedFloor()
            if (newFloor != prevFloor) {
                floorOut?.add(SimulationPoint(boundary, newFloor))
                prevFloor = newFloor
            }

            prevDate = boundary
        }
        return breakdowns
    }
}

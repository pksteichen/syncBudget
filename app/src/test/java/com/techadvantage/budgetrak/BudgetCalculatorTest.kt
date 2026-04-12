package com.techadvantage.budgetrak

import com.techadvantage.budgetrak.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BudgetCalculatorTest {

    // ── generateOccurrences: DAYS ───────────────────────────────────

    @Test
    fun occurrences_days_interval1_daily() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.DAYS, 1,
            startDate = LocalDate.of(2026, 1, 1), null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 1, 5)
        )
        assertEquals(5, dates.size)
        assertEquals(LocalDate.of(2026, 1, 1), dates[0])
        assertEquals(LocalDate.of(2026, 1, 5), dates[4])
    }

    @Test
    fun occurrences_days_interval3_everyThirdDay() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.DAYS, 3,
            startDate = LocalDate.of(2026, 1, 1), null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 1, 10)
        )
        // Jan 1, 4, 7, 10
        assertEquals(4, dates.size)
        assertEquals(LocalDate.of(2026, 1, 1), dates[0])
        assertEquals(LocalDate.of(2026, 1, 4), dates[1])
        assertEquals(LocalDate.of(2026, 1, 10), dates[3])
    }

    @Test
    fun occurrences_days_startDateAfterRange_empty() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.DAYS, 1,
            startDate = LocalDate.of(2026, 6, 1), null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 1, 31)
        )
        assertTrue(dates.isEmpty())
    }

    @Test
    fun occurrences_days_nullStartDate_empty() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.DAYS, 1,
            startDate = null, null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 1, 31)
        )
        assertTrue(dates.isEmpty())
    }

    // ── generateOccurrences: WEEKS ──────────────────────────────────

    @Test
    fun occurrences_weeks_interval1_weekly() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.WEEKS, 1,
            startDate = LocalDate.of(2026, 1, 5), null, null, // Monday
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 1, 31)
        )
        // Jan 5, 12, 19, 26
        assertEquals(4, dates.size)
    }

    @Test
    fun occurrences_weeks_interval2_biweekly() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.WEEKS, 2,
            startDate = LocalDate.of(2026, 1, 1), null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 2, 28)
        )
        // Every 14 days from Jan 1: Jan 1, 15, 29, Feb 12, 26
        assertEquals(5, dates.size)
    }

    // ── generateOccurrences: BI_WEEKLY ──────────────────────────────

    @Test
    fun occurrences_biWeekly_generates() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.BI_WEEKLY, 1,
            startDate = LocalDate.of(2026, 1, 1), null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 2, 28)
        )
        // Every 14 days: Jan 1, 15, 29, Feb 12, 26
        assertEquals(5, dates.size)
    }

    // ── generateOccurrences: MONTHS ─────────────────────────────────

    @Test
    fun occurrences_months_interval1_monthly() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.MONTHS, 1,
            startDate = null, monthDay1 = 15, monthDay2 = null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 6, 30)
        )
        // Jan 15, Feb 15, Mar 15, Apr 15, May 15, Jun 15
        assertEquals(6, dates.size)
        assertEquals(LocalDate.of(2026, 1, 15), dates[0])
        assertEquals(LocalDate.of(2026, 6, 15), dates[5])
    }

    @Test
    fun occurrences_months_interval3_quarterly() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.MONTHS, 3,
            startDate = LocalDate.of(2026, 1, 15), monthDay1 = 15, monthDay2 = null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 12, 31)
        )
        // Jan 15, Apr 15, Jul 15, Oct 15
        assertEquals(4, dates.size)
    }

    @Test
    fun occurrences_months_nullMonthDay_empty() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.MONTHS, 1,
            startDate = null, monthDay1 = null, monthDay2 = null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 12, 31)
        )
        assertTrue(dates.isEmpty())
    }

    @Test
    fun occurrences_months_day31_februaryClamped() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.MONTHS, 1,
            startDate = null, monthDay1 = 31, monthDay2 = null,
            rangeStart = LocalDate.of(2026, 2, 1),
            rangeEnd = LocalDate.of(2026, 2, 28)
        )
        // Feb has 28 days, so clamped to Feb 28
        assertEquals(1, dates.size)
        assertEquals(LocalDate.of(2026, 2, 28), dates[0])
    }

    // ── generateOccurrences: BI_MONTHLY ─────────────────────────────

    @Test
    fun occurrences_biMonthly_twoPerMonth() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.BI_MONTHLY, 1,
            startDate = null, monthDay1 = 1, monthDay2 = 15,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 3, 31)
        )
        // Jan: 1, 15; Feb: 1, 15; Mar: 1, 15
        assertEquals(6, dates.size)
    }

    @Test
    fun occurrences_biMonthly_nullMonthDay2_empty() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.BI_MONTHLY, 1,
            startDate = null, monthDay1 = 1, monthDay2 = null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 3, 31)
        )
        assertTrue(dates.isEmpty())
    }

    // ── generateOccurrences: ANNUAL ─────────────────────────────────

    @Test
    fun occurrences_annual_yearly() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.ANNUAL, 1,
            startDate = LocalDate.of(2024, 6, 15), null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2028, 12, 31)
        )
        // Jun 15 2026, Jun 15 2027, Jun 15 2028
        assertEquals(3, dates.size)
        assertEquals(LocalDate.of(2026, 6, 15), dates[0])
    }

    @Test
    fun occurrences_annual_nullStartDate_empty() {
        val dates = BudgetCalculator.generateOccurrences(
            RepeatType.ANNUAL, 1,
            startDate = null, null, null,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2028, 12, 31)
        )
        assertTrue(dates.isEmpty())
    }

    // ── countPeriodsCompleted ────────────────────────────────────────

    @Test
    fun countPeriods_daily() {
        val count = BudgetCalculator.countPeriodsCompleted(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BudgetPeriod.DAILY
        )
        assertEquals(30, count)
    }

    @Test
    fun countPeriods_weekly() {
        val count = BudgetCalculator.countPeriodsCompleted(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), BudgetPeriod.WEEKLY
        )
        // 31 days / 7 = 4 weeks
        assertEquals(4, count)
    }

    @Test
    fun countPeriods_monthly() {
        val count = BudgetCalculator.countPeriodsCompleted(
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), BudgetPeriod.MONTHLY
        )
        assertEquals(12, count)
    }

    @Test
    fun countPeriods_sameDate_zero() {
        val count = BudgetCalculator.countPeriodsCompleted(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), BudgetPeriod.DAILY
        )
        assertEquals(0, count)
    }

    // ── calculateSafeBudgetAmount ───────────────────────────────────

    @Test
    fun safeBudget_daily_singleMonthlyIncome_noExpenses() {
        val income = listOf(IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            income, emptyList(), BudgetPeriod.DAILY
        )
        // ~$3000*12 / 365 ≈ $98.63
        assertTrue(result > 90.0 && result < 110.0)
    }

    @Test
    fun safeBudget_daily_incomeMinusExpenses() {
        val income = listOf(IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val expenses = listOf(RecurringExpense(id = 1, source = "Rent", amount = 900.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            income, expenses, BudgetPeriod.DAILY
        )
        // ~($3000-$900)*12 / 365 ≈ $69.04
        assertTrue(result > 60.0 && result < 80.0)
    }

    @Test
    fun safeBudget_weekly() {
        val income = listOf(IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            income, emptyList(), BudgetPeriod.WEEKLY
        )
        // ~$3000*12 / 52 ≈ $692
        assertTrue(result > 650.0 && result < 750.0)
    }

    @Test
    fun safeBudget_monthly() {
        val income = listOf(IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val expenses = listOf(RecurringExpense(id = 1, source = "Rent", amount = 900.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            income, expenses, BudgetPeriod.MONTHLY
        )
        // ~($3000-$900)*12 / 12 = $2100
        assertTrue(result > 2000.0 && result < 2200.0)
    }

    @Test
    fun safeBudget_multipleIncomeDifferentRepeatTypes() {
        val income = listOf(
            IncomeSource(id = 1, source = "Salary", amount = 3000.0,
                repeatType = RepeatType.MONTHS, monthDay1 = 1),
            IncomeSource(id = 2, source = "Freelance", amount = 500.0,
                repeatType = RepeatType.BI_WEEKLY,
                startDate = LocalDate.now())
        )
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            income, emptyList(), BudgetPeriod.DAILY
        )
        // Income should be > salary alone
        assertTrue(result > 100.0)
    }

    @Test
    fun safeBudget_noIncomeNoExpenses_zero() {
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            emptyList(), emptyList(), BudgetPeriod.DAILY
        )
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun safeBudget_expensesExceedIncome_zero() {
        val income = listOf(IncomeSource(id = 1, source = "Salary", amount = 1000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val expenses = listOf(RecurringExpense(id = 1, source = "Rent", amount = 2000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val result = BudgetCalculator.calculateSafeBudgetAmount(
            income, expenses, BudgetPeriod.DAILY
        )
        // maxOf(0.0, ...) so result is 0
        assertEquals(0.0, result, 0.001)
    }

    // ── activeAmortizationDeductions ────────────────────────────────

    @Test
    fun amortization_activeEntry_deducted() {
        val entries = listOf(AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = LocalDate.now().minusMonths(3)))
        val result = BudgetCalculator.activeAmortizationDeductions(entries, BudgetPeriod.MONTHLY)
        assertEquals(100.0, result, 0.001)  // 1200/12 = 100
    }

    @Test
    fun amortization_pausedEntry_notDeducted() {
        val entries = listOf(AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = LocalDate.now().minusMonths(3), isPaused = true))
        val result = BudgetCalculator.activeAmortizationDeductions(entries, BudgetPeriod.MONTHLY)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun amortization_fullyAmortized_notDeducted() {
        val entries = listOf(AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = LocalDate.now().minusMonths(13)))
        val result = BudgetCalculator.activeAmortizationDeductions(entries, BudgetPeriod.MONTHLY)
        assertEquals(0.0, result, 0.001)
    }

    // ── activeSavingsGoalDeductions ─────────────────────────────────

    @Test
    fun savings_fixedContribution_deducted() {
        val goals = listOf(SavingsGoal(id = 1, name = "Vacation", targetAmount = 5000.0,
            totalSavedSoFar = 1000.0, contributionPerPeriod = 100.0))
        val result = BudgetCalculator.activeSavingsGoalDeductions(goals, BudgetPeriod.MONTHLY)
        assertEquals(100.0, result, 0.001)
    }

    @Test
    fun savings_pausedGoal_notDeducted() {
        val goals = listOf(SavingsGoal(id = 1, name = "Vacation", targetAmount = 5000.0,
            contributionPerPeriod = 100.0, isPaused = true))
        val result = BudgetCalculator.activeSavingsGoalDeductions(goals, BudgetPeriod.MONTHLY)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun savings_fullyFunded_notDeducted() {
        val goals = listOf(SavingsGoal(id = 1, name = "Vacation", targetAmount = 5000.0,
            totalSavedSoFar = 5000.0, contributionPerPeriod = 100.0))
        val result = BudgetCalculator.activeSavingsGoalDeductions(goals, BudgetPeriod.MONTHLY)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun savings_contributionCappedAtRemaining() {
        val goals = listOf(SavingsGoal(id = 1, name = "Vacation", targetAmount = 5000.0,
            totalSavedSoFar = 4950.0, contributionPerPeriod = 100.0))
        val result = BudgetCalculator.activeSavingsGoalDeductions(goals, BudgetPeriod.MONTHLY)
        assertEquals(50.0, result, 0.001) // min(100, 50) = 50
    }
}

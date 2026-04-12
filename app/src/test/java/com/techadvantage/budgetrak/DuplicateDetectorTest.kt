package com.techadvantage.budgetrak

import com.techadvantage.budgetrak.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DuplicateDetectorTest {

    private val today = LocalDate.of(2026, 1, 15)

    // ── amountMatches ───────────────────────────────────────────────

    @Test
    fun amountMatches_exactMatch() {
        assertTrue(amountMatches(100.0, 100.0))
    }

    @Test
    fun amountMatches_withinPercentTolerance() {
        // 100 vs 100.5 → diff=0.5, maxVal=100.5, ratio=0.00497 < 0.01
        assertTrue(amountMatches(100.0, 100.5))
    }

    @Test
    fun amountMatches_withinDollarTolerance() {
        // 100 vs 101 → rounded diff = 1 ≤ 1
        assertTrue(amountMatches(100.0, 101.0))
    }

    @Test
    fun amountMatches_outsideBothTolerances() {
        // 100 vs 105 → percent = 5% > 1%, dollar diff = 5 > 1
        assertFalse(amountMatches(100.0, 105.0))
    }

    @Test
    fun amountMatches_zeroAmounts() {
        assertTrue(amountMatches(0.0, 0.0))
    }

    @Test
    fun amountMatches_negativeAmounts() {
        // -100 vs -100.5 → diff=0.5, maxVal=100.5, ratio < 0.01
        assertTrue(amountMatches(-100.0, -100.5))
    }

    // ── merchantMatches ─────────────────────────────────────────────

    @Test
    fun merchantMatches_exactMatch() {
        assertTrue(merchantMatches("AMAZON MARKETPLACE", "AMAZON MARKETPLACE"))
    }

    @Test
    fun merchantMatches_caseInsensitive() {
        assertTrue(merchantMatches("Amazon Marketplace", "AMAZON MARKETPLACE"))
    }

    @Test
    fun merchantMatches_partialOverlapMeetsMinChars() {
        // "AMAZON" shares "amazo" (5 chars) with "AMAZON MARKETPLACE"
        assertTrue(merchantMatches("AMAZON", "AMAZON MARKETPLACE"))
    }

    @Test
    fun merchantMatches_shortStringsBelowMinChars_exactOnly() {
        // Both shorter than 5 chars
        assertFalse(merchantMatches("ABC", "ABD"))
        assertTrue(merchantMatches("ABC", "ABC"))
    }

    @Test
    fun merchantMatches_completelyDifferent() {
        assertFalse(merchantMatches("NETFLIX SUBSCRIPTION", "WALMART GROCERY"))
    }

    @Test
    fun merchantMatches_substringOverlap() {
        // "NETFLIX" and "NETFLIX INC" share "netfl", "etfli", "tflix"
        assertTrue(merchantMatches("NETFLIX", "NETFLIX INC"))
    }

    @Test
    fun merchantMatches_oneShortOneLong() {
        // "ABC" is < 5 chars, so exact match only
        assertFalse(merchantMatches("ABC", "ABCDEF"))
    }

    // ── findDuplicate ───────────────────────────────────────────────

    @Test
    fun findDuplicate_sameSourceAmountDate_found() {
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE, date = today,
            source = "Walmart", amount = 42.99)
        val existing = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Walmart", amount = 42.99)
        )
        val result = findDuplicate(incoming, existing)
        assertNotNull(result)
        assertEquals(1, result!!.id)
    }

    @Test
    fun findDuplicate_sameSourceAmount_dateWithinWindow_found() {
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE, date = today,
            source = "Walmart", amount = 42.99)
        val existing = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today.plusDays(3),
                source = "Walmart", amount = 42.99)
        )
        val result = findDuplicate(incoming, existing)
        assertNotNull(result)
    }

    @Test
    fun findDuplicate_dateOutsideWindow_notFound() {
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE, date = today,
            source = "Walmart", amount = 42.99)
        val existing = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today.plusDays(10),
                source = "Walmart", amount = 42.99)
        )
        val result = findDuplicate(incoming, existing)
        assertNull(result)
    }

    @Test
    fun findDuplicate_differentSource_notFound() {
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE, date = today,
            source = "Walmart", amount = 42.99)
        val existing = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Target Store", amount = 42.99)
        )
        val result = findDuplicate(incoming, existing)
        assertNull(result)
    }

    @Test
    fun findDuplicate_differentAmount_notFound() {
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE, date = today,
            source = "Walmart", amount = 42.99)
        val existing = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Walmart", amount = 99.99)
        )
        val result = findDuplicate(incoming, existing)
        assertNull(result)
    }

    @Test
    fun findDuplicate_differentDescription_stillMatches() {
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE, date = today,
            source = "Walmart", description = "new note", amount = 42.99)
        val existing = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Walmart", description = "old note", amount = 42.99)
        )
        val result = findDuplicate(incoming, existing)
        assertNotNull(result) // description is ignored for duplicate detection
    }

    // ── findRecurringExpenseMatch ────────────────────────────────────

    @Test
    fun findRecurringMatch_matchingSourceAmountOnSchedule_found() {
        val recurring = listOf(RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, monthDay1 = 15,
            startDate = LocalDate.of(2025, 1, 15)))
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, // Jan 15
            source = "NETFLIX", amount = 15.99)
        val result = findRecurringExpenseMatch(incoming, recurring)
        assertNotNull(result)
        assertEquals(1, result!!.id)
    }

    @Test
    fun findRecurringMatch_wrongAmount_notFound() {
        val recurring = listOf(RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, monthDay1 = 15))
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "NETFLIX", amount = 25.99)
        val result = findRecurringExpenseMatch(incoming, recurring)
        assertNull(result)
    }

    @Test
    fun findRecurringMatch_offScheduleDate_notFound() {
        val recurring = listOf(RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, monthDay1 = 1,
            startDate = LocalDate.of(2025, 1, 1)))
        // today is Jan 15, schedule is 1st of month → not near
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "NETFLIX", amount = 15.99)
        val result = findRecurringExpenseMatch(incoming, recurring)
        assertNull(result)
    }

    @Test
    fun findRecurringMatch_multipleRecurring_picksCorrect() {
        val recurring = listOf(
            RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
                repeatType = RepeatType.MONTHS, monthDay1 = 15),
            RecurringExpense(id = 2, source = "Spotify", amount = 9.99,
                repeatType = RepeatType.MONTHS, monthDay1 = 15)
        )
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "SPOTIFY", amount = 9.99)
        val result = findRecurringExpenseMatch(incoming, recurring)
        assertNotNull(result)
        assertEquals(2, result!!.id)
    }

    // ── findAmortizationMatch ───────────────────────────────────────

    @Test
    fun findAmortizationMatch_matchingSourceAmount_found() {
        val entries = listOf(AmortizationEntry(id = 1, source = "Laptop Purchase",
            amount = 1200.0, totalPeriods = 12, startDate = today.minusMonths(3)))
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "LAPTOP PURCHASE", amount = 1200.0)
        val result = findAmortizationMatch(incoming, entries)
        assertNotNull(result)
    }

    @Test
    fun findAmortizationMatch_wrongSource_notFound() {
        val entries = listOf(AmortizationEntry(id = 1, source = "Laptop Purchase",
            amount = 1200.0, totalPeriods = 12, startDate = today))
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "Phone Bill", amount = 1200.0)
        val result = findAmortizationMatch(incoming, entries)
        assertNull(result)
    }

    // ── findBudgetIncomeMatch ───────────────────────────────────────

    @Test
    fun findBudgetIncomeMatch_incomeTransactionMatchesSource_found() {
        val sources = listOf(IncomeSource(id = 1, source = "Employer Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 15))
        val incoming = Transaction(id = 100, type = TransactionType.INCOME,
            date = today, source = "EMPLOYER SALARY", amount = 3000.0)
        val result = findBudgetIncomeMatch(incoming, sources)
        assertNotNull(result)
    }

    @Test
    fun findBudgetIncomeMatch_expenseTransaction_returnsNull() {
        val sources = listOf(IncomeSource(id = 1, source = "Employer Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 15))
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "EMPLOYER SALARY", amount = 3000.0)
        val result = findBudgetIncomeMatch(incoming, sources)
        assertNull(result)
    }

    // ── filterAlreadyLoadedDays ─────────────────────────────────────

    @Test
    fun filterAlreadyLoadedDays_newDay_kept() {
        val file = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Store", amount = 10.0)
        )
        val app = emptyList<Transaction>()
        val result = filterAlreadyLoadedDays(file, app)
        assertEquals(1, result.size)
    }

    @Test
    fun filterAlreadyLoadedDays_sameDay_allMatched_filtered() {
        val file = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Store", amount = 10.0),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "Shop", amount = 20.0)
        )
        val app = listOf(
            Transaction(id = 10, type = TransactionType.EXPENSE, date = today,
                source = "Store", amount = 10.0),
            Transaction(id = 11, type = TransactionType.EXPENSE, date = today,
                source = "Shop", amount = 20.0)
        )
        val result = filterAlreadyLoadedDays(file, app)
        assertTrue(result.isEmpty())
    }

    @Test
    fun filterAlreadyLoadedDays_partialMatch_smallDay_allKept() {
        // ≤5 transactions: require 100% match
        val file = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Store", amount = 10.0),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "Shop", amount = 20.0)
        )
        val app = listOf(
            Transaction(id = 10, type = TransactionType.EXPENSE, date = today,
                source = "Store", amount = 10.0)
        )
        val result = filterAlreadyLoadedDays(file, app)
        assertEquals(2, result.size) // Not 100% matched, so all kept
    }

    // ── nearestOccurrenceDistance ────────────────────────────────────

    @Test
    fun nearestOccurrenceDistance_onSchedule_zero() {
        val distance = nearestOccurrenceDistance(
            LocalDate.of(2026, 1, 15),
            RepeatType.MONTHS, 1, null, 15, null
        )
        assertNotNull(distance)
        assertEquals(0L, distance!!)
    }

    @Test
    fun nearestOccurrenceDistance_nullStartDate_monthlyWithDay() {
        val distance = nearestOccurrenceDistance(
            LocalDate.of(2026, 1, 20),
            RepeatType.MONTHS, 1, null, 15, null
        )
        assertNotNull(distance)
        assertEquals(5L, distance!!)
    }

    // ── isRecurringDateCloseEnough ───────────────────────────────────

    @Test
    fun isRecurringDateCloseEnough_onSchedule_true() {
        val re = RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, monthDay1 = 15)
        assertTrue(isRecurringDateCloseEnough(today, re))
    }

    @Test
    fun isRecurringDateCloseEnough_farFromSchedule_false() {
        val re = RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, monthDay1 = 1)
        // today is Jan 15, schedule is 1st → distance = 14 > 2
        assertFalse(isRecurringDateCloseEnough(today, re))
    }
}

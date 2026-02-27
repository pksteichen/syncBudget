package com.syncbudget.app

import com.syncbudget.app.data.*
import com.syncbudget.app.data.sync.DeltaBuilder
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DeltaBuilderTest {

    private val today = LocalDate.of(2026, 1, 15)

    // ── Transaction deltas ──────────────────────────────────────────

    @Test
    fun buildTransactionDelta_allClocksAboveThreshold_allFieldsIncluded() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            categoryAmounts = listOf(CategoryAmount(10, 42.0)),
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5,
            categoryAmounts_clock = 5, isUserCategorized_clock = 5,
            isBudgetIncome_clock = 5, deleted_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 0)
        assertNotNull(delta)
        assertEquals("transaction", delta!!.type)
        assertEquals("upsert", delta.action)
        assertEquals(1, delta.id)
        assertEquals("dev1", delta.deviceId)
        assertTrue(delta.fields.containsKey("source"))
        assertTrue(delta.fields.containsKey("amount"))
        assertTrue(delta.fields.containsKey("date"))
        assertTrue(delta.fields.containsKey("type"))
        assertTrue(delta.fields.containsKey("categoryAmounts"))
        assertTrue(delta.fields.containsKey("isUserCategorized"))
        assertTrue(delta.fields.containsKey("isBudgetIncome"))
        assertTrue(delta.fields.containsKey("deleted"))
    }

    @Test
    fun buildTransactionDelta_allClocksBelowThreshold_returnsNull() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 10)
        assertNull(delta)
    }

    @Test
    fun buildTransactionDelta_mixedClocks_criticalFieldsPiggybacked() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            source_clock = 10, amount_clock = 3, date_clock = 10, type_clock = 1)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 5)
        assertNotNull(delta)
        // source and date qualify normally (clock > 5)
        assertTrue(delta!!.fields.containsKey("source"))
        assertTrue(delta.fields.containsKey("date"))
        // amount and type are piggybacked (clocks > 0, even though below threshold)
        assertTrue(delta.fields.containsKey("amount"))
        assertTrue(delta.fields.containsKey("type"))
        // Piggybacked fields keep their original clocks
        assertEquals(3L, delta.fields["amount"]?.clock)
        assertEquals(1L, delta.fields["type"]?.clock)
    }

    @Test
    fun buildTransactionDelta_categoryAmountsSerializedAsJsonString() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            categoryAmounts = listOf(CategoryAmount(10, 25.0), CategoryAmount(20, 17.0)),
            categoryAmounts_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 0)
        assertNotNull(delta)
        val catValue = delta!!.fields["categoryAmounts"]?.value as String
        assertTrue(catValue.contains("\"categoryId\""))
        assertTrue(catValue.contains("\"amount\""))
    }

    @Test
    fun buildTransactionDelta_deletedFieldIncludedWhenClockAboveThreshold() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            deleted = true, deleted_clock = 8)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 5)
        assertNotNull(delta)
        assertEquals(true, delta!!.fields["deleted"]?.value)
    }

    // ── deviceId field in deltas ─────────────────────────────────────

    @Test
    fun buildTransactionDelta_deviceIdFieldIncluded() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5,
            deviceId_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("deviceId"))
        assertEquals("dev1", delta.fields["deviceId"]?.value)
        assertEquals(5L, delta.fields["deviceId"]?.clock)
    }

    @Test
    fun buildTransactionDelta_deviceIdPiggybackedWhenOtherFieldQualifies() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            source_clock = 10, amount_clock = 3, deviceId_clock = 2)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 5)
        assertNotNull(delta)
        // deviceId_clock (2) < threshold (5), but piggybacked via ensureField
        assertTrue(delta!!.fields.containsKey("deviceId"))
        assertEquals(2L, delta.fields["deviceId"]?.clock)
    }

    @Test
    fun buildCategoryDelta_deviceIdFieldIncluded() {
        val cat = Category(id = 1, name = "Food", iconName = "Restaurant",
            deviceId = "dev1", name_clock = 5, iconName_clock = 5, deviceId_clock = 5)

        val delta = DeltaBuilder.buildCategoryDelta(cat, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("deviceId"))
        assertEquals("dev1", delta.fields["deviceId"]?.value)
    }

    // ── Category deltas ─────────────────────────────────────────────

    @Test
    fun buildCategoryDelta_tagFieldIncludedWhenClockAboveThreshold() {
        val cat = Category(id = 1, name = "Food", iconName = "Restaurant",
            tag = "groceries", deviceId = "dev1",
            name_clock = 5, iconName_clock = 5, tag_clock = 5)

        val delta = DeltaBuilder.buildCategoryDelta(cat, 0)
        assertNotNull(delta)
        assertEquals("category", delta!!.type)
        assertTrue(delta.fields.containsKey("tag"))
        assertEquals("groceries", delta.fields["tag"]?.value)
    }

    @Test
    fun buildCategoryDelta_tagClockZero_tagNotIncluded() {
        val cat = Category(id = 1, name = "Food", iconName = "Restaurant",
            tag = "groceries", deviceId = "dev1",
            name_clock = 5, iconName_clock = 5, tag_clock = 0)

        val delta = DeltaBuilder.buildCategoryDelta(cat, 1)
        assertNotNull(delta)
        assertFalse(delta!!.fields.containsKey("tag"))
    }

    // ── SharedSettings deltas ───────────────────────────────────────

    @Test
    fun buildSharedSettingsDelta_idIsZero_deviceIdIsLastChangedBy() {
        val settings = SharedSettings(currency = "€", lastChangedBy = "dev1",
            currency_clock = 5)

        val delta = DeltaBuilder.buildSharedSettingsDelta(settings, 0)
        assertNotNull(delta)
        assertEquals("shared_settings", delta!!.type)
        assertEquals(0, delta.id)
        assertEquals("dev1", delta.deviceId)
    }

    // ── RecurringExpense deltas ──────────────────────────────────────

    @Test
    fun buildRecurringExpenseDelta_includesScheduleFields() {
        val re = RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, repeatInterval = 1,
            startDate = today, monthDay1 = 15, deviceId = "dev1",
            source_clock = 5, amount_clock = 5, repeatType_clock = 5,
            repeatInterval_clock = 5, startDate_clock = 5, monthDay1_clock = 5)

        val delta = DeltaBuilder.buildRecurringExpenseDelta(re, 0)
        assertNotNull(delta)
        assertEquals("recurring_expense", delta!!.type)
        assertTrue(delta.fields.containsKey("repeatType"))
        assertTrue(delta.fields.containsKey("repeatInterval"))
        assertTrue(delta.fields.containsKey("startDate"))
        assertTrue(delta.fields.containsKey("monthDay1"))
    }

    // ── AmortizationEntry deltas ────────────────────────────────────

    @Test
    fun buildAmortizationEntryDelta_includesIsPausedField() {
        val entry = AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = today, deviceId = "dev1",
            isPaused = true, isPaused_clock = 5,
            source_clock = 5, amount_clock = 5, totalPeriods_clock = 5,
            startDate_clock = 5)

        val delta = DeltaBuilder.buildAmortizationEntryDelta(entry, 0)
        assertNotNull(delta)
        assertEquals("amortization_entry", delta!!.type)
        assertTrue(delta.fields.containsKey("isPaused"))
        assertEquals(true, delta.fields["isPaused"]?.value)
    }

    // ── SavingsGoal deltas ──────────────────────────────────────────

    @Test
    fun buildSavingsGoalDelta_includesAllFields() {
        val goal = SavingsGoal(id = 1, name = "Vacation", targetAmount = 5000.0,
            targetDate = today.plusMonths(6), totalSavedSoFar = 500.0,
            contributionPerPeriod = 100.0, isPaused = false, deviceId = "dev1",
            name_clock = 5, targetAmount_clock = 5, targetDate_clock = 5,
            totalSavedSoFar_clock = 5, contributionPerPeriod_clock = 5,
            isPaused_clock = 5, deleted_clock = 5)

        val delta = DeltaBuilder.buildSavingsGoalDelta(goal, 0)
        assertNotNull(delta)
        assertEquals("savings_goal", delta!!.type)
        assertTrue(delta.fields.containsKey("name"))
        assertTrue(delta.fields.containsKey("targetAmount"))
        assertTrue(delta.fields.containsKey("targetDate"))
        assertTrue(delta.fields.containsKey("totalSavedSoFar"))
        assertTrue(delta.fields.containsKey("contributionPerPeriod"))
        assertTrue(delta.fields.containsKey("isPaused"))
    }

    // ── description field in deltas ──────────────────────────────────

    @Test
    fun buildTransactionDelta_descriptionIncludedWhenClockAboveThreshold() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "weekly groceries", amount = 42.0, deviceId = "dev1",
            source_clock = 5, description_clock = 5, amount_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("description"))
        assertEquals("weekly groceries", delta.fields["description"]?.value)
        assertEquals(5L, delta.fields["description"]?.clock)
    }

    @Test
    fun buildTransactionDelta_descriptionExcludedWhenClockBelowThreshold() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "note", amount = 42.0, deviceId = "dev1",
            source_clock = 10, description_clock = 3, amount_clock = 10)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 5)
        assertNotNull(delta)
        // description_clock (3) < threshold (5) and it's not a critical field → not piggybacked
        assertFalse(delta!!.fields.containsKey("description"))
    }

    @Test
    fun buildRecurringExpenseDelta_descriptionIncluded() {
        val re = RecurringExpense(id = 1, source = "Netflix", description = "family plan",
            amount = 15.99, deviceId = "dev1",
            source_clock = 5, description_clock = 5, amount_clock = 5)

        val delta = DeltaBuilder.buildRecurringExpenseDelta(re, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("description"))
        assertEquals("family plan", delta.fields["description"]?.value)
    }

    @Test
    fun buildIncomeSourceDelta_descriptionIncluded() {
        val src = IncomeSource(id = 1, source = "Salary", description = "base pay",
            amount = 3000.0, deviceId = "dev1",
            source_clock = 5, description_clock = 5, amount_clock = 5)

        val delta = DeltaBuilder.buildIncomeSourceDelta(src, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("description"))
    }

    @Test
    fun buildAmortizationEntryDelta_descriptionIncluded() {
        val entry = AmortizationEntry(id = 1, source = "Laptop", description = "work laptop",
            amount = 1200.0, totalPeriods = 12, startDate = today, deviceId = "dev1",
            source_clock = 5, description_clock = 5, amount_clock = 5,
            totalPeriods_clock = 5, startDate_clock = 5)

        val delta = DeltaBuilder.buildAmortizationEntryDelta(entry, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("description"))
        assertEquals("work laptop", delta.fields["description"]?.value)
    }

    // ── Edge: lastPushedClock = 0 ───────────────────────────────────

    @Test
    fun buildTransactionDelta_lastPushedClockZero_allNonZeroFieldsIncluded() {
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 42.0, deviceId = "dev1",
            source_clock = 1, amount_clock = 1)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("source"))
        assertTrue(delta.fields.containsKey("amount"))
    }

    // ── IncomeSource deltas ─────────────────────────────────────────

    @Test
    fun buildIncomeSourceDelta_nullStartDate_serializedAsNull() {
        val src = IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            startDate = null, monthDay1 = 1, deviceId = "dev1",
            source_clock = 5, amount_clock = 5, startDate_clock = 5,
            monthDay1_clock = 5)

        val delta = DeltaBuilder.buildIncomeSourceDelta(src, 0)
        assertNotNull(delta)
        assertNull(delta!!.fields["startDate"]?.value)
    }
}

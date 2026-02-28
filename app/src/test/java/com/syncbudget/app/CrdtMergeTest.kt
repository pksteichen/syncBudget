package com.syncbudget.app

import com.syncbudget.app.data.*
import com.syncbudget.app.data.sync.CrdtMerge
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CrdtMergeTest {

    private val deviceA = "aaaa1111"
    private val deviceB = "bbbb2222"
    private val today = LocalDate.of(2026, 1, 15)

    // ── shouldAcceptRemote ──────────────────────────────────────────

    @Test
    fun shouldAcceptRemote_higherRemoteClock_returnsTrue() {
        assertTrue(CrdtMerge.shouldAcceptRemote(3, 5, deviceA, deviceB))
    }

    @Test
    fun shouldAcceptRemote_lowerRemoteClock_returnsFalse() {
        assertFalse(CrdtMerge.shouldAcceptRemote(5, 3, deviceA, deviceB))
    }

    @Test
    fun shouldAcceptRemote_equalClocks_higherDeviceIdWins() {
        // deviceB > deviceA lexicographically
        assertTrue(CrdtMerge.shouldAcceptRemote(5, 5, deviceA, deviceB))
    }

    @Test
    fun shouldAcceptRemote_equalClocks_lowerDeviceIdLoses() {
        assertFalse(CrdtMerge.shouldAcceptRemote(5, 5, deviceB, deviceA))
    }

    @Test
    fun shouldAcceptRemote_equalClocks_sameDeviceId_returnsFalse() {
        assertFalse(CrdtMerge.shouldAcceptRemote(5, 5, deviceA, deviceA))
    }

    // ── mergeTransaction ────────────────────────────────────────────

    @Test
    fun mergeTransaction_remoteHigherSourceClock_takesRemoteSource() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Local Store", amount = 50.0, deviceId = deviceA,
            source_clock = 3, amount_clock = 5)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Remote Store", amount = 50.0, deviceId = deviceB,
            source_clock = 7, amount_clock = 2)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals("Remote Store", merged.source)
        assertEquals(50.0, merged.amount, 0.001) // local amount wins (clock 5 > 2)
    }

    @Test
    fun mergeTransaction_localHigherAmountClock_keepsLocalAmount() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 100.0, deviceId = deviceA, amount_clock = 10)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 200.0, deviceId = deviceB, amount_clock = 5)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(100.0, merged.amount, 0.001)
    }

    @Test
    fun mergeTransaction_mixedFieldWinners() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Local", amount = 50.0, deviceId = deviceA,
            source_clock = 2, amount_clock = 8, date_clock = 1, type_clock = 6)
        val remote = Transaction(id = 1, type = TransactionType.INCOME, date = today.plusDays(1),
            source = "Remote", amount = 99.0, deviceId = deviceB,
            source_clock = 5, amount_clock = 3, date_clock = 4, type_clock = 1)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals("Remote", merged.source)       // remote clock 5 > 2
        assertEquals(50.0, merged.amount, 0.001)     // local clock 8 > 3
        assertEquals(today.plusDays(1), merged.date)  // remote clock 4 > 1
        assertEquals(TransactionType.EXPENSE, merged.type) // local clock 6 > 1
    }

    @Test
    fun mergeTransaction_deleteWins_remoteDeletedHigherClock() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceA,
            deleted = false, deleted_clock = 3)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceB,
            deleted = true, deleted_clock = 7)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertTrue(merged.deleted)
    }

    @Test
    fun mergeTransaction_deleteLoses_remoteDeletedLowerClock() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceA,
            deleted = false, deleted_clock = 10)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceB,
            deleted = true, deleted_clock = 5)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertFalse(merged.deleted)
    }

    @Test
    fun mergeTransaction_clocksAreMaxOfBoth() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA,
            source_clock = 3, amount_clock = 8, date_clock = 1)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "R", amount = 20.0, deviceId = deviceB,
            source_clock = 5, amount_clock = 2, date_clock = 4)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(5L, merged.source_clock)
        assertEquals(8L, merged.amount_clock)
        assertEquals(4L, merged.date_clock)
    }

    @Test
    fun mergeTransaction_allZeroLocalClocks_remoteTakesAll() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Old", amount = 1.0, deviceId = deviceA)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today.plusDays(3),
            source = "New", amount = 99.0, deviceId = deviceB,
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals("New", merged.source)
        assertEquals(99.0, merged.amount, 0.001)
        assertEquals(today.plusDays(3), merged.date)
    }

    @Test
    fun mergeTransaction_allZeroRemoteClocks_localKeepsAll() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Local", amount = 42.0, deviceId = deviceA,
            source_clock = 3, amount_clock = 3)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today.plusDays(1),
            source = "Remote", amount = 99.0, deviceId = deviceB)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals("Local", merged.source)
        assertEquals(42.0, merged.amount, 0.001)
    }

    @Test
    fun mergeTransaction_deviceIdLWW_higherClockWins() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA, deviceId_clock = 3)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceB, source_clock = 5, deviceId_clock = 7)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(deviceB, merged.deviceId) // remote deviceId_clock 7 > 3
        assertEquals(7L, merged.deviceId_clock)
    }

    @Test
    fun mergeTransaction_deviceIdLWW_localHigherClockKeepsLocal() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA, deviceId_clock = 10)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceB, deviceId_clock = 3)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(deviceA, merged.deviceId) // local deviceId_clock 10 > 3
        assertEquals(10L, merged.deviceId_clock)
    }

    @Test
    fun mergeTransaction_deviceIdLWW_zeroClockRemoteWinsWithPositive() {
        // Backward compat: local has no deviceId_clock (0), remote has it stamped
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA, deviceId_clock = 0)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceB, deviceId_clock = 5)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(deviceB, merged.deviceId) // remote clock 5 > 0
    }

    @Test
    fun mergeTransaction_deviceIdLWW_bothZero_tiebreakByDeviceId() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA, deviceId_clock = 0)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceB, deviceId_clock = 0)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        // deviceB > deviceA lexicographically, so remote wins tiebreak
        assertEquals(deviceB, merged.deviceId)
    }

    @Test
    fun mergeTransaction_undelete_remoteNotDeletedHigherClock() {
        // With standard LWW, undelete is possible: remote says deleted=false
        // with higher clock overrides local deleted=true
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceA,
            deleted = true, deleted_clock = 3)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceB,
            deleted = false, deleted_clock = 7)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertFalse(merged.deleted) // remote's false wins with higher clock
    }

    @Test
    fun mergeTransaction_commutativity_deleteField() {
        // merge(A, B) must equal merge(B, A)
        val a = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceA,
            deleted = true, deleted_clock = 5,
            source_clock = 3, amount_clock = 8)
        val b = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Remote", amount = 99.0, deviceId = deviceB,
            deleted = false, deleted_clock = 7,
            source_clock = 6, amount_clock = 2)

        val mergeAB = CrdtMerge.mergeTransaction(a, b, deviceA)
        val mergeBA = CrdtMerge.mergeTransaction(b, a, deviceB)

        assertEquals(mergeAB.deleted, mergeBA.deleted)
        assertEquals(mergeAB.source, mergeBA.source)
        assertEquals(mergeAB.amount, mergeBA.amount, 0.001)
        assertEquals(mergeAB.deleted_clock, mergeBA.deleted_clock)
        assertEquals(mergeAB.source_clock, mergeBA.source_clock)
        assertEquals(mergeAB.amount_clock, mergeBA.amount_clock)
    }

    @Test
    fun mergeTransaction_deletedClockUsesMax() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA,
            deleted = false, deleted_clock = 10)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceB,
            deleted = true, deleted_clock = 5)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        // deleted_clock should be max(10, 5) = 10
        assertEquals(10L, merged.deleted_clock)
    }

    // ── mergeCategory ───────────────────────────────────────────────

    @Test
    fun mergeCategory_nameAndIconMergeIndependently() {
        val local = Category(id = 1, name = "Food", iconName = "Restaurant",
            deviceId = deviceA, name_clock = 3, iconName_clock = 7)
        val remote = Category(id = 1, name = "Groceries", iconName = "ShoppingCart",
            deviceId = deviceB, name_clock = 5, iconName_clock = 2)

        val merged = CrdtMerge.mergeCategory(local, remote, deviceA)
        assertEquals("Groceries", merged.name)      // remote clock 5 > 3
        assertEquals("Restaurant", merged.iconName)  // local clock 7 > 2
    }

    @Test
    fun mergeCategory_tagMerges() {
        val local = Category(id = 1, name = "Food", iconName = "Icon",
            tag = "", deviceId = deviceA, tag_clock = 0)
        val remote = Category(id = 1, name = "Food", iconName = "Icon",
            tag = "groceries", deviceId = deviceB, tag_clock = 5)

        val merged = CrdtMerge.mergeCategory(local, remote, deviceA)
        assertEquals("groceries", merged.tag)
    }

    @Test
    fun mergeCategory_deviceIdLWW() {
        val local = Category(id = 1, name = "Food", iconName = "Icon",
            deviceId = deviceA, deviceId_clock = 3)
        val remote = Category(id = 1, name = "Food", iconName = "Icon",
            deviceId = deviceB, deviceId_clock = 7)

        val merged = CrdtMerge.mergeCategory(local, remote, deviceA)
        assertEquals(deviceB, merged.deviceId) // remote clock 7 > 3
        assertEquals(7L, merged.deviceId_clock)
    }

    @Test
    fun mergeCategory_deleteSemantics() {
        val local = Category(id = 1, name = "Food", iconName = "Icon",
            deviceId = deviceA, deleted = false, deleted_clock = 2)
        val remote = Category(id = 1, name = "Food", iconName = "Icon",
            deviceId = deviceB, deleted = true, deleted_clock = 5)

        val merged = CrdtMerge.mergeCategory(local, remote, deviceA)
        assertTrue(merged.deleted)
    }

    // ── mergeRecurringExpense ───────────────────────────────────────

    @Test
    fun mergeRecurringExpense_scheduleFieldsMergePerField() {
        val local = RecurringExpense(id = 1, source = "Netflix", amount = 15.99,
            repeatType = RepeatType.MONTHS, repeatInterval = 1,
            startDate = today, monthDay1 = 15,
            deviceId = deviceA,
            source_clock = 2, amount_clock = 2, repeatType_clock = 5,
            repeatInterval_clock = 5, startDate_clock = 2, monthDay1_clock = 2)
        val remote = RecurringExpense(id = 1, source = "Netflix Premium", amount = 22.99,
            repeatType = RepeatType.WEEKS, repeatInterval = 2,
            startDate = today.plusDays(5), monthDay1 = 20,
            deviceId = deviceB,
            source_clock = 6, amount_clock = 6, repeatType_clock = 1,
            repeatInterval_clock = 1, startDate_clock = 4, monthDay1_clock = 4)

        val merged = CrdtMerge.mergeRecurringExpense(local, remote, deviceA)
        assertEquals("Netflix Premium", merged.source)   // remote 6 > 2
        assertEquals(22.99, merged.amount, 0.001)         // remote 6 > 2
        assertEquals(RepeatType.MONTHS, merged.repeatType) // local 5 > 1
        assertEquals(1, merged.repeatInterval)             // local 5 > 1
        assertEquals(today.plusDays(5), merged.startDate)  // remote 4 > 2
        assertEquals(20, merged.monthDay1)                 // remote 4 > 2
    }

    // ── mergeIncomeSource ───────────────────────────────────────────

    @Test
    fun mergeIncomeSource_amountFieldMerge() {
        val local = IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            deviceId = deviceA, amount_clock = 8)
        val remote = IncomeSource(id = 1, source = "Salary", amount = 3500.0,
            deviceId = deviceB, amount_clock = 10)

        val merged = CrdtMerge.mergeIncomeSource(local, remote, deviceA)
        assertEquals(3500.0, merged.amount, 0.001)
    }

    // ── mergeSharedSettings ─────────────────────────────────────────

    @Test
    fun mergeSharedSettings_usesLastChangedByAsRemoteDeviceId() {
        val local = SharedSettings(currency = "$", budgetPeriod = "DAILY",
            currency_clock = 3, budgetPeriod_clock = 5)
        val remote = SharedSettings(currency = "€", budgetPeriod = "WEEKLY",
            lastChangedBy = deviceB,
            currency_clock = 7, budgetPeriod_clock = 2)

        val merged = CrdtMerge.mergeSharedSettings(local, remote, deviceA)
        assertEquals("€", merged.currency)       // remote 7 > 3
        assertEquals("DAILY", merged.budgetPeriod) // local 5 > 2
    }

    @Test
    fun mergeSharedSettings_perFieldIndependence() {
        val local = SharedSettings(
            matchDays = 7, matchPercent = 1.0f, matchDollar = 1,
            matchDays_clock = 5, matchPercent_clock = 2, matchDollar_clock = 8)
        val remote = SharedSettings(
            matchDays = 14, matchPercent = 0.5f, matchDollar = 5,
            lastChangedBy = deviceB,
            matchDays_clock = 3, matchPercent_clock = 6, matchDollar_clock = 4)

        val merged = CrdtMerge.mergeSharedSettings(local, remote, deviceA)
        assertEquals(7, merged.matchDays)           // local 5 > 3
        assertEquals(0.5f, merged.matchPercent)      // remote 6 > 2
        assertEquals(1, merged.matchDollar)          // local 8 > 4
    }

    @Test
    fun mergeSharedSettings_lastChangedBy_commutativity() {
        // merge(A, B).lastChangedBy must equal merge(B, A).lastChangedBy
        val settingsA = SharedSettings(
            currency = "$", budgetPeriod = "DAILY", lastChangedBy = deviceA,
            currency_clock = 3, budgetPeriod_clock = 8)
        val settingsB = SharedSettings(
            currency = "€", budgetPeriod = "WEEKLY", lastChangedBy = deviceB,
            currency_clock = 7, budgetPeriod_clock = 2)

        val mergeAB = CrdtMerge.mergeSharedSettings(settingsA, settingsB, deviceA)
        val mergeBA = CrdtMerge.mergeSharedSettings(settingsB, settingsA, deviceB)

        assertEquals(mergeAB.lastChangedBy, mergeBA.lastChangedBy)
        assertEquals(mergeAB.currency, mergeBA.currency)
        assertEquals(mergeAB.budgetPeriod, mergeBA.budgetPeriod)
    }

    @Test
    fun mergeSharedSettings_lastChangedBy_highestClockWins() {
        val settingsA = SharedSettings(
            currency = "$", lastChangedBy = deviceA,
            currency_clock = 10, budgetPeriod_clock = 1)
        val settingsB = SharedSettings(
            currency = "€", lastChangedBy = deviceB,
            currency_clock = 3, budgetPeriod_clock = 5)

        val merged = CrdtMerge.mergeSharedSettings(settingsA, settingsB, deviceA)
        // deviceA has maxClock=10, deviceB has maxClock=5 → deviceA wins
        assertEquals(deviceA, merged.lastChangedBy)
    }

    // ── mergeSavingsGoal ────────────────────────────────────────────

    @Test
    fun mergeSavingsGoal_allFieldsMerge() {
        val local = SavingsGoal(id = 1, name = "Vacation", targetAmount = 5000.0,
            contributionPerPeriod = 100.0, deviceId = deviceA,
            name_clock = 3, targetAmount_clock = 7, contributionPerPeriod_clock = 2)
        val remote = SavingsGoal(id = 1, name = "Trip", targetAmount = 3000.0,
            contributionPerPeriod = 200.0, deviceId = deviceB,
            name_clock = 5, targetAmount_clock = 4, contributionPerPeriod_clock = 6)

        val merged = CrdtMerge.mergeSavingsGoal(local, remote, deviceA)
        assertEquals("Trip", merged.name)                    // remote 5 > 3
        assertEquals(5000.0, merged.targetAmount, 0.001)     // local 7 > 4
        assertEquals(200.0, merged.contributionPerPeriod, 0.001) // remote 6 > 2
    }

    // ── mergeAmortizationEntry ──────────────────────────────────────

    @Test
    fun mergeAmortizationEntry_pausedFieldMerges() {
        val local = AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = today, deviceId = deviceA,
            isPaused = false, isPaused_clock = 3)
        val remote = AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = today, deviceId = deviceB,
            isPaused = true, isPaused_clock = 5)

        val merged = CrdtMerge.mergeAmortizationEntry(local, remote, deviceA)
        assertTrue(merged.isPaused)
    }

    // ── description LWW merge ────────────────────────────────────────

    @Test
    fun mergeTransaction_descriptionLWW_remoteHigherClock() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "local note", amount = 50.0, deviceId = deviceA,
            description_clock = 3)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "remote note", amount = 50.0, deviceId = deviceB,
            description_clock = 7)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals("remote note", merged.description)
        assertEquals(7L, merged.description_clock)
    }

    @Test
    fun mergeTransaction_descriptionLWW_localHigherClock() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "local note", amount = 50.0, deviceId = deviceA,
            description_clock = 10)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "remote note", amount = 50.0, deviceId = deviceB,
            description_clock = 3)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals("local note", merged.description)
        assertEquals(10L, merged.description_clock)
    }

    @Test
    fun mergeRecurringExpense_descriptionLWW() {
        val local = RecurringExpense(id = 1, source = "Netflix", description = "old",
            amount = 15.99, deviceId = deviceA, description_clock = 2)
        val remote = RecurringExpense(id = 1, source = "Netflix", description = "new",
            amount = 15.99, deviceId = deviceB, description_clock = 5)

        val merged = CrdtMerge.mergeRecurringExpense(local, remote, deviceA)
        assertEquals("new", merged.description)
        assertEquals(5L, merged.description_clock)
    }

    @Test
    fun mergeIncomeSource_descriptionLWW() {
        val local = IncomeSource(id = 1, source = "Salary", description = "old",
            amount = 3000.0, deviceId = deviceA, description_clock = 2)
        val remote = IncomeSource(id = 1, source = "Salary", description = "new",
            amount = 3000.0, deviceId = deviceB, description_clock = 5)

        val merged = CrdtMerge.mergeIncomeSource(local, remote, deviceA)
        assertEquals("new", merged.description)
        assertEquals(5L, merged.description_clock)
    }

    @Test
    fun mergeAmortizationEntry_descriptionLWW() {
        val local = AmortizationEntry(id = 1, source = "Laptop", description = "old",
            amount = 1200.0, totalPeriods = 12, startDate = today, deviceId = deviceA,
            description_clock = 2)
        val remote = AmortizationEntry(id = 1, source = "Laptop", description = "new",
            amount = 1200.0, totalPeriods = 12, startDate = today, deviceId = deviceB,
            description_clock = 5)

        val merged = CrdtMerge.mergeAmortizationEntry(local, remote, deviceA)
        assertEquals("new", merged.description)
        assertEquals(5L, merged.description_clock)
    }

    @Test
    fun mergeAmortizationEntry_sourceAndAmountMerge() {
        val local = AmortizationEntry(id = 1, source = "Old Laptop", amount = 1000.0,
            totalPeriods = 10, startDate = today, deviceId = deviceA,
            source_clock = 2, amount_clock = 8)
        val remote = AmortizationEntry(id = 1, source = "New Laptop", amount = 1500.0,
            totalPeriods = 10, startDate = today, deviceId = deviceB,
            source_clock = 5, amount_clock = 3)

        val merged = CrdtMerge.mergeAmortizationEntry(local, remote, deviceA)
        assertEquals("New Laptop", merged.source) // remote 5 > 2
        assertEquals(1000.0, merged.amount, 0.001) // local 8 > 3
    }

    // ── Linked ID fields ──

    @Test
    fun `mergeTransaction — linkedRecurringExpenseId LWW higher clock wins`() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Netflix", amount = 15.0, deviceId = deviceA,
            linkedRecurringExpenseId = null, linkedRecurringExpenseId_clock = 2,
            source_clock = 1, amount_clock = 1)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Netflix", amount = 15.0, deviceId = deviceB,
            linkedRecurringExpenseId = 42, linkedRecurringExpenseId_clock = 5,
            source_clock = 1, amount_clock = 1)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(42, merged.linkedRecurringExpenseId)
        assertEquals(5L, merged.linkedRecurringExpenseId_clock)
    }

    @Test
    fun `mergeTransaction — linkedAmortizationEntryId null to value`() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Laptop", amount = 100.0, deviceId = deviceA,
            linkedAmortizationEntryId = null, linkedAmortizationEntryId_clock = 0,
            source_clock = 1, amount_clock = 1)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Laptop", amount = 100.0, deviceId = deviceB,
            linkedAmortizationEntryId = 7, linkedAmortizationEntryId_clock = 3,
            source_clock = 1, amount_clock = 1)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertEquals(7, merged.linkedAmortizationEntryId)
        assertEquals(3L, merged.linkedAmortizationEntryId_clock)
    }

    @Test
    fun `mergeTransaction — unlink (value to null) with higher clock`() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Netflix", amount = 15.0, deviceId = deviceA,
            linkedRecurringExpenseId = 42, linkedRecurringExpenseId_clock = 3,
            source_clock = 1, amount_clock = 1)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Netflix", amount = 15.0, deviceId = deviceB,
            linkedRecurringExpenseId = null, linkedRecurringExpenseId_clock = 5,
            source_clock = 1, amount_clock = 1)

        val merged = CrdtMerge.mergeTransaction(local, remote, deviceA)
        assertNull(merged.linkedRecurringExpenseId)
        assertEquals(5L, merged.linkedRecurringExpenseId_clock)
    }
}

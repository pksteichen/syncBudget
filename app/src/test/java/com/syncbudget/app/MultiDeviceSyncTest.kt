package com.syncbudget.app

import com.syncbudget.app.data.*
import com.syncbudget.app.data.sync.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * End-to-end multi-device sync simulations.
 * Tests full pipeline: create records → build deltas → serialize → deserialize → merge.
 */
class MultiDeviceSyncTest {

    private val deviceA = "aaaa1111"
    private val deviceB = "bbbb2222"
    private val today = LocalDate.of(2026, 1, 15)
    private val now = Instant.parse("2026-01-15T12:00:00Z")

    // ── Helper: simulate sending deltas from one device to another ──

    private fun buildAndTransmit(
        transactions: List<Transaction> = emptyList(),
        categories: List<Category> = emptyList(),
        recurringExpenses: List<RecurringExpense> = emptyList(),
        incomeSources: List<IncomeSource> = emptyList(),
        savingsGoals: List<SavingsGoal> = emptyList(),
        amortizationEntries: List<AmortizationEntry> = emptyList(),
        settings: SharedSettings? = null,
        sourceDeviceId: String,
        lastPushedClock: Long = 0
    ): DeltaPacket {
        val changes = mutableListOf<RecordDelta>()
        transactions.forEach { DeltaBuilder.buildTransactionDelta(it, lastPushedClock)?.let(changes::add) }
        categories.forEach { DeltaBuilder.buildCategoryDelta(it, lastPushedClock)?.let(changes::add) }
        recurringExpenses.forEach { DeltaBuilder.buildRecurringExpenseDelta(it, lastPushedClock)?.let(changes::add) }
        incomeSources.forEach { DeltaBuilder.buildIncomeSourceDelta(it, lastPushedClock)?.let(changes::add) }
        savingsGoals.forEach { DeltaBuilder.buildSavingsGoalDelta(it, lastPushedClock)?.let(changes::add) }
        amortizationEntries.forEach { DeltaBuilder.buildAmortizationEntryDelta(it, lastPushedClock)?.let(changes::add) }
        settings?.let { DeltaBuilder.buildSharedSettingsDelta(it, lastPushedClock)?.let(changes::add) }

        val packet = DeltaPacket(sourceDeviceId, now, changes)
        // Simulate network: serialize → deserialize
        val json = DeltaSerializer.serialize(packet)
        return DeltaSerializer.deserialize(json)
    }

    // ── Scenario 1: Basic transaction sync ──────────────────────────

    @Test
    fun scenario1_basicTransactionSync() {
        val txns = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Grocery Store", amount = 50.0, deviceId = deviceA,
                source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "Gas Station", amount = 25.0, deviceId = deviceA,
                source_clock = 2, amount_clock = 2, date_clock = 2, type_clock = 2),
            Transaction(id = 3, type = TransactionType.INCOME, date = today,
                source = "Freelance", amount = 100.0, deviceId = deviceA,
                source_clock = 3, amount_clock = 3, date_clock = 3, type_clock = 3)
        )

        val packet = buildAndTransmit(transactions = txns, sourceDeviceId = deviceA)
        assertEquals(3, packet.changes.size)

        // Device B receives: verify all transactions arrived
        val receivedIds = packet.changes.map { it.id }.toSet()
        assertEquals(setOf(1, 2, 3), receivedIds)

        // Verify field values survived serialization
        val txn1 = packet.changes.find { it.id == 1 }!!
        assertEquals("Grocery Store", txn1.fields["source"]?.value)
    }

    // ── Scenario 2: Concurrent edits to same transaction ────────────

    @Test
    fun scenario2_concurrentEdits_higherClockWins() {
        val localTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Local Edit", amount = 50.0, deviceId = deviceA,
            source_clock = 3, amount_clock = 5)
        val remoteTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Remote Edit", amount = 99.0, deviceId = deviceB,
            source_clock = 7, amount_clock = 2)

        val merged = CrdtMerge.mergeTransaction(localTxn, remoteTxn, deviceA)
        assertEquals("Remote Edit", merged.source)   // remote clock 7 > 3
        assertEquals(50.0, merged.amount, 0.001)      // local clock 5 > 2
        assertEquals(deviceA, merged.deviceId)         // preserves local deviceId
    }

    @Test
    fun scenario2_equalClocks_lexicographicTiebreak() {
        val localTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Device A Edit", amount = 50.0, deviceId = deviceA,
            source_clock = 5)
        val remoteTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Device B Edit", amount = 50.0, deviceId = deviceB,
            source_clock = 5)

        val merged = CrdtMerge.mergeTransaction(localTxn, remoteTxn, deviceA)
        // deviceB > deviceA lexicographically, so remote wins
        assertEquals("Device B Edit", merged.source)
    }

    // ── Scenario 3: Concurrent category creation ────────────────────

    @Test
    fun scenario3_categoriesWithSameTag_mergedByTag() {
        // Simulating SyncEngine behavior: match by tag
        val localCats = mutableListOf(
            Category(id = 1, name = "Food", iconName = "Restaurant",
                tag = "groceries", deviceId = deviceA,
                name_clock = 3, iconName_clock = 3, tag_clock = 3)
        )
        val remoteCat = Category(id = 100, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceB,
            name_clock = 5, iconName_clock = 5, tag_clock = 5)

        // Match by tag (simulating SyncEngine logic)
        val existingIdx = localCats.indexOfFirst { it.tag == remoteCat.tag }
        assertTrue(existingIdx >= 0) // Found match

        val merged = CrdtMerge.mergeCategory(localCats[existingIdx], remoteCat, deviceA)
        localCats[existingIdx] = merged

        // Should merge, not duplicate
        assertEquals(1, localCats.size)
        assertEquals("Groceries", merged.name) // remote clock 5 > 3
    }

    @Test
    fun scenario3_categoriesDifferentIds_noTag_noUnintendedMerge() {
        val localCats = mutableListOf(
            Category(id = 1, name = "Custom A", iconName = "Icon", deviceId = deviceA)
        )
        val remoteCat = Category(id = 100, name = "Custom B", iconName = "Icon", deviceId = deviceB)

        // No tag match, different IDs → should NOT merge
        val existingIdx = localCats.indexOfFirst { it.tag.isNotEmpty() && it.tag == remoteCat.tag }
        assertEquals(-1, existingIdx)

        // Add as new
        localCats.add(remoteCat)
        assertEquals(2, localCats.size)
    }

    // ── Scenario 4: Delete on one device, edit on another ───────────

    @Test
    fun scenario4_deleteVsEdit_higherDeleteClockWins() {
        val localTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Edited Source", amount = 75.0, deviceId = deviceA,
            source_clock = 3, amount_clock = 3, deleted = false, deleted_clock = 1)
        val remoteTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Original", amount = 50.0, deviceId = deviceB,
            deleted = true, deleted_clock = 5)

        val merged = CrdtMerge.mergeTransaction(localTxn, remoteTxn, deviceA)
        assertTrue(merged.deleted) // deleted_clock 5 > 1
        assertEquals("Edited Source", merged.source) // source_clock 3 > 0
    }

    @Test
    fun scenario4_deleteVsEdit_editWinsWhenDeleteClockLower() {
        val localTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceA,
            deleted = false, deleted_clock = 10)
        val remoteTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceB,
            deleted = true, deleted_clock = 3)

        val merged = CrdtMerge.mergeTransaction(localTxn, remoteTxn, deviceA)
        assertFalse(merged.deleted) // local deleted_clock 10 > 3
    }

    // ── Scenario 5: Budget mode variations ──────────────────────────

    @Test
    fun scenario5_budgetCalculation_dailyWeeklyMonthly_consistent() {
        val income = listOf(IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))
        val expenses = listOf(RecurringExpense(id = 1, source = "Rent", amount = 900.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1))

        val daily = BudgetCalculator.calculateSafeBudgetAmount(income, expenses, BudgetPeriod.DAILY)
        val weekly = BudgetCalculator.calculateSafeBudgetAmount(income, expenses, BudgetPeriod.WEEKLY)
        val monthly = BudgetCalculator.calculateSafeBudgetAmount(income, expenses, BudgetPeriod.MONTHLY)

        // Weekly ≈ daily * 7
        assertTrue(weekly > daily * 6 && weekly < daily * 8)
        // Monthly ≈ daily * 30
        assertTrue(monthly > daily * 28 && monthly < daily * 32)
        // All positive
        assertTrue(daily > 0)
        assertTrue(weekly > 0)
        assertTrue(monthly > 0)
    }

    // ── Scenario 6: Recurring expense duplicate detection ───────────

    @Test
    fun scenario6_recurringExpenseDetection() {
        val recurring = listOf(
            RecurringExpense(id = 1, source = "Netflix Subscription", amount = 15.99,
                repeatType = RepeatType.MONTHS, monthDay1 = 15)
        )
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, // Jan 15 matches monthDay1 = 15
            source = "NETFLIX SUBSCRIPTION", amount = 15.99)

        val match = findRecurringExpenseMatch(incoming, recurring)
        assertNotNull(match)
        assertEquals("Netflix Subscription", match!!.source)
    }

    @Test
    fun scenario6_recurringExpenseDetection_weeklySchedule() {
        val startDate = today.minusWeeks(4) // 4 weeks ago
        val recurring = listOf(
            RecurringExpense(id = 1, source = "Cleaning Service", amount = 50.0,
                repeatType = RepeatType.WEEKS, repeatInterval = 1,
                startDate = startDate)
        )
        // Transaction on today (which is 4 weeks after start, should be on schedule)
        val incoming = Transaction(id = 100, type = TransactionType.EXPENSE,
            date = today, source = "CLEANING SERVICE", amount = 50.0)

        val match = findRecurringExpenseMatch(incoming, recurring)
        assertNotNull(match)
    }

    // ── Scenario 7: Delta push filtering ────────────────────────────

    @Test
    fun scenario7_deltaBuilder_buildsForAnyDevice() {
        // DeltaBuilder itself doesn't filter by deviceId; that's the sync engine's job
        val txnFromB = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceB,
            source_clock = 5, amount_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txnFromB, 0)
        assertNotNull(delta) // DeltaBuilder doesn't care about deviceId
        assertEquals(deviceB, delta!!.deviceId)
    }

    @Test
    fun scenario7_onlyLocalRecordsShouldBePushed() {
        // Simulate the sync engine's filter: only push local device records
        val allTxns = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Local", amount = 10.0, deviceId = deviceA, source_clock = 5),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "Remote", amount = 20.0, deviceId = deviceB, source_clock = 5)
        )

        val localOnly = allTxns.filter { it.deviceId == deviceA }
        val deltas = localOnly.mapNotNull { DeltaBuilder.buildTransactionDelta(it, 0) }
        assertEquals(1, deltas.size)
        assertEquals(deviceA, deltas[0].deviceId)
    }

    // ── Scenario 8: LamportClock inflation protection ───────────────

    @Test
    fun scenario8_staleClockBelowLastPushed_deltaDropped() {
        // Simulates the old bug: foreground clock stayed at 5 while
        // lastPushedClock got inflated to 100 by SyncWorker
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "New Purchase", amount = 42.0, deviceId = deviceA,
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 100)
        assertNull(delta) // All clocks (5) < lastPushedClock (100) → dropped!
    }

    @Test
    fun scenario8_fixedClock_aboveLastPushed_deltaIncluded() {
        // With the fix: clock re-reads from shared state, so new transactions
        // get clocks > lastPushedClock
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "New Purchase", amount = 42.0, deviceId = deviceA,
            source_clock = 105, amount_clock = 105, date_clock = 105, type_clock = 105)

        val delta = DeltaBuilder.buildTransactionDelta(txn, 100)
        assertNotNull(delta)
    }

    // ── Scenario 9: Full state round-trip ───────────────────────────

    @Test
    fun scenario9_fullStateRoundTrip() {
        val txns = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Store", amount = 50.0, deviceId = deviceA,
                source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1),
            Transaction(id = 2, type = TransactionType.INCOME, date = today.plusDays(1),
                source = "Salary", amount = 3000.0, deviceId = deviceA,
                source_clock = 2, amount_clock = 2, date_clock = 2, type_clock = 2,
                isBudgetIncome = true, isBudgetIncome_clock = 2)
        )
        val cats = listOf(
            Category(id = 10, name = "Food", iconName = "Restaurant",
                tag = "groceries", deviceId = deviceA,
                name_clock = 1, iconName_clock = 1, tag_clock = 1)
        )
        val recurringExpenses = listOf(
            RecurringExpense(id = 20, source = "Netflix", amount = 15.99,
                repeatType = RepeatType.MONTHS, monthDay1 = 15, deviceId = deviceA,
                source_clock = 1, amount_clock = 1, repeatType_clock = 1, monthDay1_clock = 1)
        )

        // Build deltas from device A
        val packet = buildAndTransmit(
            transactions = txns, categories = cats,
            recurringExpenses = recurringExpenses,
            sourceDeviceId = deviceA
        )

        // Verify all record types survived
        val types = packet.changes.map { it.type }.toSet()
        assertTrue(types.contains("transaction"))
        assertTrue(types.contains("category"))
        assertTrue(types.contains("recurring_expense"))
        assertEquals(4, packet.changes.size) // 2 txns + 1 cat + 1 recurring
    }

    // ── Scenario 10: SharedSettings sync ────────────────────────────

    @Test
    fun scenario10_sharedSettings_perFieldMerge() {
        val settingsA = SharedSettings(currency = "€", budgetPeriod = "DAILY",
            lastChangedBy = deviceA, currency_clock = 3, budgetPeriod_clock = 1)
        val settingsB = SharedSettings(currency = "$", budgetPeriod = "WEEKLY",
            lastChangedBy = deviceB, currency_clock = 1, budgetPeriod_clock = 4)

        val merged = CrdtMerge.mergeSharedSettings(settingsA, settingsB, deviceA)
        assertEquals("€", merged.currency)           // A clock 3 > B clock 1
        assertEquals("WEEKLY", merged.budgetPeriod)   // B clock 4 > A clock 1
    }

    @Test
    fun scenario10_sharedSettings_fullPipeline() {
        val settings = SharedSettings(currency = "€", budgetPeriod = "WEEKLY",
            matchDays = 14, lastChangedBy = deviceA,
            currency_clock = 5, budgetPeriod_clock = 5, matchDays_clock = 5)

        val packet = buildAndTransmit(settings = settings, sourceDeviceId = deviceA)
        assertEquals(1, packet.changes.size)
        val change = packet.changes[0]
        assertEquals("shared_settings", change.type)
        assertEquals("€", change.fields["currency"]?.value)
        assertEquals("WEEKLY", change.fields["budgetPeriod"]?.value)
    }

    // ── Scenario 11: Category tag_clock propagation ─────────────────

    @Test
    fun scenario11_tagClockPropagation() {
        // With tag_clock stamped properly, tag should be included in delta
        val cat = Category(id = 1, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceA,
            name_clock = 5, iconName_clock = 5, tag_clock = 5)

        val delta = DeltaBuilder.buildCategoryDelta(cat, 0)
        assertNotNull(delta)
        assertTrue(delta!!.fields.containsKey("tag"))
        assertEquals("groceries", delta.fields["tag"]?.value)

        // Round-trip through serialization
        val packet = DeltaPacket(deviceA, now, listOf(delta))
        val json = DeltaSerializer.serialize(packet)
        val received = DeltaSerializer.deserialize(json)
        assertEquals("groceries", received.changes[0].fields["tag"]?.value)
    }

    @Test
    fun scenario11_tagClockZero_tagNotPushed_theBug() {
        // Demonstrates the old bug: tag_clock=0 means tag never pushed
        val cat = Category(id = 1, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceA,
            name_clock = 5, iconName_clock = 5, tag_clock = 0)

        val delta = DeltaBuilder.buildCategoryDelta(cat, 1)
        assertNotNull(delta)
        // tag_clock (0) is NOT > lastPushedClock (1), so tag is omitted
        assertFalse(delta!!.fields.containsKey("tag"))
    }

    // ── Scenario 12: Large-scale stress test ────────────────────────

    @Test
    fun scenario12_stressTest_100transactions() {
        val sources = listOf("Walmart", "Amazon", "Target", "Costco", "Kroger",
            "Netflix", "Spotify", "Gas Station", "Restaurant", "Coffee Shop")
        val txnsA = (1..50).map { i ->
            Transaction(id = i, type = TransactionType.EXPENSE,
                date = today.plusDays((i % 30).toLong()),
                source = sources[i % sources.size], amount = 10.0 + i,
                deviceId = deviceA,
                source_clock = i.toLong(), amount_clock = i.toLong(),
                date_clock = i.toLong(), type_clock = i.toLong())
        }
        val txnsB = (51..100).map { i ->
            Transaction(id = i, type = TransactionType.EXPENSE,
                date = today.plusDays((i % 30).toLong()),
                source = sources[i % sources.size], amount = 10.0 + i,
                deviceId = deviceB,
                source_clock = i.toLong(), amount_clock = i.toLong(),
                date_clock = i.toLong(), type_clock = i.toLong())
        }

        // Build deltas from both devices
        val packetA = buildAndTransmit(transactions = txnsA, sourceDeviceId = deviceA)
        val packetB = buildAndTransmit(transactions = txnsB, sourceDeviceId = deviceB)

        assertEquals(50, packetA.changes.size)
        assertEquals(50, packetB.changes.size)

        // Simulate device C receiving both packets — no ID collisions
        val allIds = (packetA.changes + packetB.changes).map { it.id }.toSet()
        assertEquals(100, allIds.size) // No duplicates
    }

    @Test
    fun scenario12_stressTest_mergeMultipleEdits() {
        // Same transaction edited 10 times on each device with increasing clocks
        var localTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Original", amount = 10.0, deviceId = deviceA)

        // Device A makes 10 edits
        for (i in 1..10) {
            localTxn = localTxn.copy(
                source = "Edit A $i",
                amount = 10.0 + i,
                source_clock = i.toLong(),
                amount_clock = i.toLong()
            )
        }

        // Device B makes 10 edits independently
        var remoteTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Original", amount = 10.0, deviceId = deviceB)
        for (i in 1..10) {
            remoteTxn = remoteTxn.copy(
                source = "Edit B $i",
                amount = 100.0 + i,
                source_clock = (i + 5).toLong(),  // B starts from clock 6
                amount_clock = (i + 5).toLong()
            )
        }

        // Merge: B's last edit (clock 15) should win over A's last (clock 10)
        val merged = CrdtMerge.mergeTransaction(localTxn, remoteTxn, deviceA)
        assertEquals("Edit B 10", merged.source)
        assertEquals(110.0, merged.amount, 0.001)
        assertEquals(15L, merged.source_clock) // max(10, 15) = 15
    }

    // ── Extra: Bidirectional sync ───────────────────────────────────

    @Test
    fun bidirectionalSync_bothDevicesGetEachOthersData() {
        // Device A creates transaction
        val txnA = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "A's Purchase", amount = 50.0, deviceId = deviceA,
            source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1)
        // Device B creates transaction
        val txnB = Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
            source = "B's Purchase", amount = 75.0, deviceId = deviceB,
            source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1)

        // A sends to B, B sends to A
        val packetA = buildAndTransmit(transactions = listOf(txnA), sourceDeviceId = deviceA)
        val packetB = buildAndTransmit(transactions = listOf(txnB), sourceDeviceId = deviceB)

        // Both packets have 1 transaction each
        assertEquals(1, packetA.changes.size)
        assertEquals(1, packetB.changes.size)
        assertEquals("A's Purchase", packetA.changes[0].fields["source"]?.value)
        assertEquals("B's Purchase", packetB.changes[0].fields["source"]?.value)
    }

    // ── Extra: Income source sync ───────────────────────────────────

    @Test
    fun incomeSourceSync_mergesCorrectly() {
        val localIncome = IncomeSource(id = 1, source = "Salary", amount = 3000.0,
            repeatType = RepeatType.MONTHS, monthDay1 = 1, deviceId = deviceA,
            source_clock = 3, amount_clock = 3, repeatType_clock = 3, monthDay1_clock = 3)
        val remoteIncome = IncomeSource(id = 1, source = "Updated Salary", amount = 3500.0,
            repeatType = RepeatType.BI_WEEKLY, startDate = today, deviceId = deviceB,
            source_clock = 5, amount_clock = 5, repeatType_clock = 1, startDate_clock = 5)

        val merged = CrdtMerge.mergeIncomeSource(localIncome, remoteIncome, deviceA)
        assertEquals("Updated Salary", merged.source)  // remote 5 > 3
        assertEquals(3500.0, merged.amount, 0.001)       // remote 5 > 3
        assertEquals(RepeatType.MONTHS, merged.repeatType) // local 3 > 1
        assertEquals(today, merged.startDate)             // remote 5 > 0
    }

    // ── Extra: Amortization entry sync ──────────────────────────────

    @Test
    fun amortizationEntrySync_pauseToggle() {
        val local = AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = today, deviceId = deviceA,
            isPaused = false, isPaused_clock = 2)
        val remote = AmortizationEntry(id = 1, source = "Laptop", amount = 1200.0,
            totalPeriods = 12, startDate = today, deviceId = deviceB,
            isPaused = true, isPaused_clock = 5)

        val merged = CrdtMerge.mergeAmortizationEntry(local, remote, deviceA)
        assertTrue(merged.isPaused) // remote 5 > 2

        // Now local unpauses with higher clock
        val local2 = merged.copy(isPaused = false, isPaused_clock = 8, deviceId = deviceA)
        val remote2 = merged.copy(deviceId = deviceB) // still paused at clock 5
        val merged2 = CrdtMerge.mergeAmortizationEntry(local2, remote2, deviceA)
        assertFalse(merged2.isPaused) // local 8 > 5
    }
}

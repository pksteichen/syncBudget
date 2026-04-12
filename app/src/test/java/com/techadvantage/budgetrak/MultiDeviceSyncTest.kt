package com.techadvantage.budgetrak

import com.techadvantage.budgetrak.data.*
import com.techadvantage.budgetrak.data.sync.*
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
            source_clock = 3, amount_clock = 5, deviceId_clock = 1)
        val remoteTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Remote Edit", amount = 99.0, deviceId = deviceB,
            source_clock = 7, amount_clock = 2, deviceId_clock = 1)

        val merged = CrdtMerge.mergeTransaction(localTxn, remoteTxn, deviceA)
        assertEquals("Remote Edit", merged.source)   // remote clock 7 > 3
        assertEquals(50.0, merged.amount, 0.001)      // local clock 5 > 2
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
    fun scenario7_allRecordsPushedRegardlessOfOwnership() {
        // After removing the push filter, ALL records are pushed — DeltaBuilder
        // threshold (field_clock > lastPushedClock) is the only gate.
        val allTxns = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "Local", amount = 10.0, deviceId = deviceA, source_clock = 5),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "Remote", amount = 20.0, deviceId = deviceB, source_clock = 5)
        )

        val deltas = allTxns.mapNotNull { DeltaBuilder.buildTransactionDelta(it, 0) }
        assertEquals(2, deltas.size)  // Both pushed, regardless of deviceId
        assertEquals(deviceA, deltas[0].deviceId)
        assertEquals(deviceB, deltas[1].deviceId)
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

    // ── Scenario 13: deviceId converges to original creator ─────────

    @Test
    fun scenario13_deviceIdConvergesToOriginalCreator() {
        // Admin creates a transaction with deviceId_clock stamped
        val adminTxn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Admin Purchase", amount = 50.0, deviceId = deviceA,
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5,
            deviceId_clock = 5)

        // Non-admin receives it. With LWW, remote's deviceId_clock (5) > local's (0),
        // so deviceId converges to admin's identity
        val nonAdminLocal = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Admin Purchase", amount = 50.0, deviceId = deviceB,
            source_clock = 5, amount_clock = 5, date_clock = 5, type_clock = 5,
            deviceId_clock = 0)

        val merged = CrdtMerge.mergeTransaction(nonAdminLocal, adminTxn, deviceB)
        assertEquals(deviceA, merged.deviceId) // converges to admin's deviceId
        assertEquals(5L, merged.deviceId_clock)
    }

    @Test
    fun scenario13_deviceIdStableOnEdits() {
        // Original creator is deviceA with deviceId_clock=5
        val original = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", amount = 50.0, deviceId = deviceA,
            source_clock = 5, amount_clock = 5, deviceId_clock = 5)

        // deviceB edits the source (higher source_clock), but does NOT re-stamp deviceId_clock
        val editedByB = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Updated Store", amount = 50.0, deviceId = deviceA,
            source_clock = 10, amount_clock = 5, deviceId_clock = 5)

        val merged = CrdtMerge.mergeTransaction(original, editedByB, deviceA)
        assertEquals("Updated Store", merged.source) // edit wins
        assertEquals(deviceA, merged.deviceId) // deviceId unchanged
    }

    @Test
    fun scenario13_deviceIdLWW_commutativity() {
        val a = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceA, deviceId_clock = 5,
            source_clock = 3, amount_clock = 3)
        val b = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "S", amount = 10.0, deviceId = deviceB, deviceId_clock = 3,
            source_clock = 5, amount_clock = 5)

        val mergeAB = CrdtMerge.mergeTransaction(a, b, deviceA)
        val mergeBA = CrdtMerge.mergeTransaction(b, a, deviceB)

        // Both orderings must produce the same deviceId
        assertEquals(mergeAB.deviceId, mergeBA.deviceId)
        assertEquals(mergeAB.deviceId_clock, mergeBA.deviceId_clock)
    }

    // ── Scenario 14: Three-device category remap ───────────────────

    @Test
    fun scenario14_threeDevice_categoryRemap_allIdsReachAllDevices() {
        // Three devices each have "Groceries" with different random IDs
        val deviceC = "cccc3333"
        val catA = Category(id = 100, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceA,
            name_clock = 5, iconName_clock = 5, tag_clock = 5, deviceId_clock = 5)
        val catB = Category(id = 200, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceB,
            name_clock = 3, iconName_clock = 3, tag_clock = 3, deviceId_clock = 3)
        val catC = Category(id = 300, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceC,
            name_clock = 3, iconName_clock = 3, tag_clock = 3, deviceId_clock = 3)

        // Device C creates a transaction with its own category ID (300)
        val txnFromC = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Sams Club", amount = 426.65, deviceId = deviceC,
            categoryAmounts = listOf(CategoryAmount(300, 426.65)),
            source_clock = 10, amount_clock = 10, date_clock = 10, type_clock = 10,
            categoryAmounts_clock = 10, deviceId_clock = 10)

        // ---- Simulate device B receiving all three category deltas ----
        // (This is the key: ALL devices push ALL categories, so B gets C's IDs)
        val localCats = mutableListOf(catB) // B's local category
        val catIdRemap = mutableMapOf<Int, Int>()

        // Process catA delta (admin's)
        val byTagA = localCats.indexOfFirst { it.tag == catA.tag }
        assertTrue(byTagA >= 0)
        catIdRemap[catA.id] = localCats[byTagA].id  // 100 → 200
        localCats[byTagA] = CrdtMerge.mergeCategory(localCats[byTagA], catA, deviceB)

        // Process catC delta (device3's)
        val byTagC = localCats.indexOfFirst { it.tag == catC.tag }
        assertTrue(byTagC >= 0)
        catIdRemap[catC.id] = localCats[byTagC].id  // 300 → 200
        localCats[byTagC] = CrdtMerge.mergeCategory(localCats[byTagC], catC, deviceB)

        // Verify remaps
        assertEquals(200, catIdRemap[100])  // admin's → B's
        assertEquals(200, catIdRemap[300])  // device3's → B's

        // ---- Remap transaction from device C ----
        val remapped = txnFromC.categoryAmounts.map { ca ->
            val newId = catIdRemap[ca.categoryId]
            if (newId != null) ca.copy(categoryId = newId) else ca
        }
        assertEquals(1, remapped.size)
        assertEquals(200, remapped[0].categoryId)  // 300 → 200 ✓
    }

    @Test
    fun scenario14_missingCategoryDelta_orphansTransaction() {
        // Demonstrates the bug: if device C's category delta never arrives,
        // B can't remap C's category IDs and the transaction is orphaned
        val catA = Category(id = 100, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceA,
            name_clock = 5, iconName_clock = 5, tag_clock = 5)
        val catB = Category(id = 200, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceB,
            name_clock = 3, iconName_clock = 3, tag_clock = 3)

        val localCats = mutableListOf(catB)
        val catIdRemap = mutableMapOf<Int, Int>()

        // Only admin's category delta arrives (device C's never pushed)
        val byTag = localCats.indexOfFirst { it.tag == catA.tag }
        catIdRemap[catA.id] = localCats[byTag].id  // 100 → 200

        // Transaction from device C with category 300 — no remap exists!
        val txnFromC = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Sams Club", amount = 426.65, deviceId = "cccc3333",
            categoryAmounts = listOf(CategoryAmount(300, 426.65)),
            source_clock = 10, amount_clock = 10, date_clock = 10, type_clock = 10,
            categoryAmounts_clock = 10)

        val remapped = txnFromC.categoryAmounts.map { ca ->
            val newId = catIdRemap[ca.categoryId]
            if (newId != null) ca.copy(categoryId = newId) else ca
        }
        // Orphaned! categoryId stays as 300, which doesn't exist locally
        assertEquals(300, remapped[0].categoryId)
        assertFalse(localCats.any { it.id == 300 }) // Not in local list → UI shows no category
    }

    @Test
    fun scenario15_proactiveRemapRebuildsFromMergedCategories() {
        // Tests the proactive remap rebuild: when a transaction references
        // a foreign category ID, the remap is built by matching the foreign
        // category (from incoming deltas) by tag to local categories.
        val deviceC = "cccc3333"
        val localCats = listOf(
            Category(id = 100, name = "Groceries", iconName = "ShoppingCart",
                tag = "groceries", deviceId = deviceA,
                name_clock = 5, iconName_clock = 5, tag_clock = 5, deviceId_clock = 5)
        )

        // Device C has category 300 with same tag "groceries"
        val catFromC = Category(id = 300, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceC,
            name_clock = 3, iconName_clock = 3, tag_clock = 3, deviceId_clock = 3)

        // Transaction from C references category 300
        val txnFromC = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Sams Club", amount = 426.65, deviceId = deviceC,
            categoryAmounts = listOf(CategoryAmount(300, 426.65)),
            source_clock = 10, amount_clock = 10, date_clock = 10, type_clock = 10,
            categoryAmounts_clock = 10, deviceId_clock = 10)

        // Simulate: C's category delta arrives in a packet
        val packet = buildAndTransmit(
            categories = listOf(catFromC),
            transactions = listOf(txnFromC),
            sourceDeviceId = deviceC
        )

        // Merge on device A
        val mergedCat = localCats.toMutableList()
        val mergedTxns = mutableListOf<Transaction>()
        val catIdRemap = mutableMapOf<Int, Int>()

        for (change in packet.changes) {
            when (change.type) {
                "category" -> {
                    val remoteCat = Category(
                        id = change.id,
                        name = change.fields["name"]?.value as? String ?: "",
                        iconName = change.fields["iconName"]?.value as? String ?: "label",
                        tag = change.fields["tag"]?.value as? String ?: "",
                        deviceId = change.fields["deviceId"]?.value as? String ?: change.deviceId,
                        name_clock = change.fields["name"]?.clock ?: 0L,
                        iconName_clock = change.fields["iconName"]?.clock ?: 0L,
                        tag_clock = change.fields["tag"]?.clock ?: 0L,
                        deviceId_clock = change.fields["deviceId"]?.clock ?: 0L
                    )
                    val byTag = mergedCat.indexOfFirst { it.tag == remoteCat.tag }
                    if (byTag >= 0) {
                        catIdRemap[change.id] = mergedCat[byTag].id
                        mergedCat[byTag] = CrdtMerge.mergeCategory(mergedCat[byTag], remoteCat, deviceA)
                    } else {
                        mergedCat.add(remoteCat)
                    }
                }
                "transaction" -> {
                    val f = change.fields
                    val catAmountsStr = f["categoryAmounts"]?.value as? String
                    val categoryAmounts = if (catAmountsStr != null) {
                        val arr = org.json.JSONArray(catAmountsStr)
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            CategoryAmount(obj.getInt("categoryId"), obj.getDouble("amount"))
                        }
                    } else emptyList()
                    mergedTxns.add(Transaction(
                        id = change.id,
                        type = TransactionType.EXPENSE,
                        date = today,
                        source = f["source"]?.value as? String ?: "",
                        categoryAmounts = categoryAmounts,
                        amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
                        deviceId = change.deviceId,
                        source_clock = f["source"]?.clock ?: 0L,
                        amount_clock = f["amount"]?.clock ?: 0L,
                        categoryAmounts_clock = f["categoryAmounts"]?.clock ?: 0L
                    ))
                }
            }
        }

        // Remap is built during category merge: 300 → 100
        assertEquals(100, catIdRemap[300])

        // Apply remap to transaction
        for (i in mergedTxns.indices) {
            val txn = mergedTxns[i]
            val remapped = txn.categoryAmounts.map { ca ->
                val newId = catIdRemap[ca.categoryId]
                if (newId != null) ca.copy(categoryId = newId) else ca
            }
            if (remapped != txn.categoryAmounts) {
                mergedTxns[i] = txn.copy(categoryAmounts = remapped)
            }
        }

        // Transaction now uses local category ID 100
        assertEquals(100, mergedTxns[0].categoryAmounts[0].categoryId)
        assertTrue(mergedCat.any { it.id == 100 }) // Exists in local list
    }

    @Test
    fun scenario15_remapRebuiltFromExistingWhenNoDeltasArrive() {
        // When no new category deltas arrive but existingCatIdRemap has
        // the mapping and transactions have orphaned IDs, the remap is
        // still applied and the remapChangedTxns flag is set.
        val existingRemap = mapOf(300 to 100)
        val txn = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Sams Club", amount = 426.65, deviceId = "cccc3333",
            categoryAmounts = listOf(CategoryAmount(300, 426.65)),
            source_clock = 10, amount_clock = 10, categoryAmounts_clock = 10)

        val mergedTxns = mutableListOf(txn)
        var remapChangedTxns = false
        val catIdRemap = existingRemap.toMutableMap()

        if (catIdRemap.isNotEmpty()) {
            for (i in mergedTxns.indices) {
                val t = mergedTxns[i]
                val remapped = t.categoryAmounts.map { ca ->
                    val newId = catIdRemap[ca.categoryId]
                    if (newId != null) ca.copy(categoryId = newId) else ca
                }
                if (remapped != t.categoryAmounts) {
                    mergedTxns[i] = t.copy(categoryAmounts = remapped)
                    remapChangedTxns = true
                }
            }
        }

        assertTrue(remapChangedTxns)
        assertEquals(100, mergedTxns[0].categoryAmounts[0].categoryId)

        // With remapChangedTxns = true, hasChanges check passes
        val hasChanges = false // no new packets
        val txnsChanged = hasChanges || remapChangedTxns
        assertTrue(txnsChanged) // transactions WILL be saved
    }

    @Test
    fun scenario15_onUpdateCategory_preservesDeviceIdClock() {
        // Editing a category should NOT overwrite deviceId or deviceId_clock
        val original = Category(id = 100, name = "Groceries", iconName = "ShoppingCart",
            tag = "groceries", deviceId = deviceA, deviceId_clock = 50,
            name_clock = 10, iconName_clock = 10, tag_clock = 10)

        // Simulate onUpdateCategory: user changes the name
        val updated = original.copy(name = "Food & Groceries")
        val clock = 60L
        val result = updated.copy(
            deviceId = original.deviceId,         // preserve
            deviceId_clock = original.deviceId_clock,  // preserve
            deleted = original.deleted,
            deleted_clock = original.deleted_clock,
            tag_clock = original.tag_clock,
            name_clock = if (updated.name != original.name) clock else original.name_clock,
            iconName_clock = if (updated.iconName != original.iconName) clock else original.iconName_clock
        )

        assertEquals(deviceA, result.deviceId)  // Not overwritten
        assertEquals(50L, result.deviceId_clock) // Not overwritten
        assertEquals(60L, result.name_clock)    // Updated
        assertEquals(10L, result.iconName_clock) // Unchanged
    }

    @Test
    fun scenario16_staleRemapReversedToFixOrphanedTransactions() {
        // When catIdRemap has stale entries (target doesn't exist in merged
        // categories but source does), the stale entry should be reversed
        // so that transactions already remapped to extinct IDs get fixed.
        val adminCatGroceries = Category(id = 22695, name = "Groceries", iconName = "Cart",
            tag = "groceries", deviceId = deviceA, name_clock = 5, tag_clock = 5,
            deviceId_clock = 5)
        val adminCatHome = Category(id = 30186, name = "Home Supplies", iconName = "Home",
            tag = "home_supplies", deviceId = deviceA, name_clock = 5, tag_clock = 5,
            deviceId_clock = 5)
        // Local defaults that got regenerated (different IDs from the old ones)
        val localDefaultGroceries = Category(id = 13888, name = "", iconName = "label",
            tag = "groceries", deviceId = deviceB, name_clock = 0, tag_clock = 0)
        val localDefaultHome = Category(id = 62725, name = "", iconName = "label",
            tag = "home_supplies", deviceId = deviceB, name_clock = 0, tag_clock = 0)

        val mergedCat = mutableListOf(
            adminCatGroceries, adminCatHome,
            localDefaultGroceries, localDefaultHome
        )

        // Stale remap: admin IDs → OLD local IDs (45542, 51526) that no longer exist
        val catIdRemap = mutableMapOf(22695 to 45542, 30186 to 51526)

        // Transactions were already remapped to stale targets
        val txn1 = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Amazon", amount = 9.65, deviceId = deviceA,
            categoryAmounts = listOf(CategoryAmount(51526, 9.65)), // stale!
            source_clock = 10, amount_clock = 10, categoryAmounts_clock = 10)
        val txn2 = Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
            source = "Sams Club", amount = 226.65, deviceId = deviceA,
            categoryAmounts = listOf(CategoryAmount(45542, 226.65)), // stale!
            source_clock = 10, amount_clock = 10, categoryAmounts_clock = 10)

        val mergedTxns = mutableListOf(txn1, txn2)
        val mergedCatById = mergedCat.associateBy { it.id }

        // Step 5a: Validate stale remap entries
        val staleKeys = mutableListOf<Int>()
        val reverseRemaps = mutableMapOf<Int, Int>()
        for ((sourceId, targetId) in catIdRemap) {
            if (targetId !in mergedCatById) {
                staleKeys.add(sourceId)
                if (sourceId in mergedCatById) {
                    reverseRemaps[targetId] = sourceId
                }
            }
        }
        for (key in staleKeys) { catIdRemap.remove(key) }
        catIdRemap.putAll(reverseRemaps)

        // Verify: stale forwards removed, reverse remaps added
        assertFalse(22695 in catIdRemap) // stale forward removed
        assertFalse(30186 in catIdRemap) // stale forward removed
        assertEquals(22695, catIdRemap[45542]) // reverse: extinct → admin
        assertEquals(30186, catIdRemap[51526]) // reverse: extinct → admin

        // Step 5d: Apply remap to transactions
        for (i in mergedTxns.indices) {
            val txn = mergedTxns[i]
            val remapped = txn.categoryAmounts.map { ca ->
                val newId = catIdRemap[ca.categoryId]
                if (newId != null) ca.copy(categoryId = newId) else ca
            }
            if (remapped != txn.categoryAmounts) {
                mergedTxns[i] = txn.copy(categoryAmounts = remapped)
            }
        }

        // Transactions now point to valid admin category IDs
        assertEquals(30186, mergedTxns[0].categoryAmounts[0].categoryId) // Home Supplies
        assertEquals(22695, mergedTxns[1].categoryAmounts[0].categoryId) // Groceries
        // Both exist in merged categories
        assertTrue(mergedCatById.containsKey(30186))
        assertTrue(mergedCatById.containsKey(22695))
    }

    @Test
    fun scenario16_tagDedupConsolidatesDuplicateCategories() {
        // After tag propagation, two categories may share the same tag.
        // The dedup should keep the most authoritative one (highest name_clock)
        // and remap the other.
        val adminCat = Category(id = 22695, name = "Groceries", iconName = "Cart",
            tag = "groceries", deviceId = deviceA, name_clock = 5, tag_clock = 5,
            deviceId_clock = 5)
        val localDefault = Category(id = 13888, name = "", iconName = "label",
            tag = "groceries", deviceId = deviceB, name_clock = 0, tag_clock = 0)

        val mergedCat = mutableListOf(adminCat, localDefault)
        val catIdRemap = mutableMapOf<Int, Int>()

        // Step 5b: Tag dedup
        val catIndicesByTag = mutableMapOf<String, MutableList<Int>>()
        for (i in mergedCat.indices) {
            val cat = mergedCat[i]
            if (cat.tag.isNotEmpty() && !cat.deleted) {
                catIndicesByTag.getOrPut(cat.tag) { mutableListOf() }.add(i)
            }
        }
        for ((_, indices) in catIndicesByTag) {
            if (indices.size <= 1) continue
            val sorted = indices.sortedByDescending { mergedCat[it].name_clock }
            val keepCat = mergedCat[sorted[0]]
            for (j in 1 until sorted.size) {
                val dupCat = mergedCat[sorted[j]]
                if (dupCat.id != keepCat.id) {
                    catIdRemap[dupCat.id] = keepCat.id
                }
            }
        }

        // Admin category kept (higher name_clock), local default remapped
        assertEquals(22695, catIdRemap[13888]) // local default → admin
        assertFalse(22695 in catIdRemap) // admin not remapped
    }

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

    // ── Scenario: description field syncs across devices ──────────────

    @Test
    fun scenario_descriptionSyncsAcrossDevices() {
        // Device A creates a transaction with a description
        val txnA = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Grocery Store", description = "weekly groceries", amount = 50.0,
            deviceId = deviceA,
            source_clock = 1, description_clock = 1, amount_clock = 1,
            date_clock = 1, type_clock = 1, deviceId_clock = 1)

        // Build and transmit to Device B
        val packet = buildAndTransmit(transactions = listOf(txnA), sourceDeviceId = deviceA)
        assertEquals(1, packet.changes.size)

        // Verify description survived serialization
        val change = packet.changes[0]
        assertEquals("weekly groceries", change.fields["description"]?.value)
        assertEquals(1L, change.fields["description"]?.clock)

        // Device B merges with empty local
        val localB = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Grocery Store", description = "", amount = 50.0,
            deviceId = deviceB, description_clock = 0)

        // Simulate deserialization on Device B
        val remoteB = Transaction(
            id = change.id,
            type = TransactionType.EXPENSE,
            date = today,
            source = change.fields["source"]?.value as String,
            description = change.fields["description"]?.value as? String ?: "",
            amount = (change.fields["amount"]?.value as Number).toDouble(),
            deviceId = change.fields["deviceId"]?.value as? String ?: deviceA,
            source_clock = change.fields["source"]?.clock ?: 0L,
            description_clock = change.fields["description"]?.clock ?: 0L,
            amount_clock = change.fields["amount"]?.clock ?: 0L,
            date_clock = change.fields["date"]?.clock ?: 0L,
            type_clock = change.fields["type"]?.clock ?: 0L,
            deviceId_clock = change.fields["deviceId"]?.clock ?: 0L
        )

        val merged = CrdtMerge.mergeTransaction(localB, remoteB, deviceB)
        assertEquals("weekly groceries", merged.description)
        assertEquals(1L, merged.description_clock)
    }

    @Test
    fun scenario_descriptionConflict_higherClockWins() {
        val local = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "local note", amount = 50.0,
            deviceId = deviceA, description_clock = 3)
        val remote = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Store", description = "remote note", amount = 50.0,
            deviceId = deviceB, description_clock = 7)

        val mergedAB = CrdtMerge.mergeTransaction(local, remote, deviceA)
        val mergedBA = CrdtMerge.mergeTransaction(remote, local, deviceB)

        // Both merge orders must produce the same result (commutativity)
        assertEquals("remote note", mergedAB.description)
        assertEquals("remote note", mergedBA.description)
        assertEquals(mergedAB.description_clock, mergedBA.description_clock)
    }

    @Test
    fun scenario_linkedRecurringExpenseId_syncsAcrossDevices() {
        // Device A links a transaction to recurring expense id=42
        val txnA = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Netflix", amount = 15.0, deviceId = deviceA,
            linkedRecurringExpenseId = 42, linkedRecurringExpenseId_clock = 5,
            source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1,
            deviceId_clock = 1)

        // Device B has the same transaction unlinked
        val txnB = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Netflix", amount = 15.0, deviceId = deviceA,
            linkedRecurringExpenseId = null, linkedRecurringExpenseId_clock = 0,
            source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1,
            deviceId_clock = 1)

        // Build delta from A, merge on B
        val delta = DeltaBuilder.buildTransactionDelta(txnA, 0)
        assertNotNull(delta)
        assertEquals(42, delta!!.fields["linkedRecurringExpenseId"]?.value)

        // Merge
        val merged = CrdtMerge.mergeTransaction(txnB, txnA, deviceB)
        assertEquals(42, merged.linkedRecurringExpenseId)
        assertEquals(5L, merged.linkedRecurringExpenseId_clock)
    }

    @Test
    fun scenario_linkedAmortizationEntryId_concurrentConflict() {
        // Device A links to amortization 10 at clock 3
        val txnA = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Laptop", amount = 100.0, deviceId = deviceA,
            linkedAmortizationEntryId = 10, linkedAmortizationEntryId_clock = 3,
            source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1,
            deviceId_clock = 1)

        // Device B links to amortization 20 at clock 3 (same clock, tie-break by deviceId)
        val txnB = Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
            source = "Laptop", amount = 100.0, deviceId = deviceB,
            linkedAmortizationEntryId = 20, linkedAmortizationEntryId_clock = 3,
            source_clock = 1, amount_clock = 1, date_clock = 1, type_clock = 1,
            deviceId_clock = 1)

        val mergedAB = CrdtMerge.mergeTransaction(txnA, txnB, deviceA)
        val mergedBA = CrdtMerge.mergeTransaction(txnB, txnA, deviceB)

        // Commutativity: both orders produce same result
        assertEquals(mergedAB.linkedAmortizationEntryId, mergedBA.linkedAmortizationEntryId)
        assertEquals(mergedAB.linkedAmortizationEntryId_clock, mergedBA.linkedAmortizationEntryId_clock)
        // deviceB > deviceA lexicographically, so B wins at equal clock
        assertEquals(20, mergedAB.linkedAmortizationEntryId)
    }
}

package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Base64
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.BudgetPeriod
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CryptoHelper
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.RepeatType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class SyncResult(
    val success: Boolean,
    val deltasReceived: Int = 0,
    val deltasPushed: Int = 0,
    val budgetRecalcNeeded: Boolean = false,
    val mergedTransactions: List<Transaction>? = null,
    val mergedRecurringExpenses: List<RecurringExpense>? = null,
    val mergedIncomeSources: List<IncomeSource>? = null,
    val mergedSavingsGoals: List<SavingsGoal>? = null,
    val mergedAmortizationEntries: List<AmortizationEntry>? = null,
    val mergedCategories: List<Category>? = null,
    val mergedSharedSettings: SharedSettings? = null,
    val pendingAdminClaim: AdminClaim? = null,
    val error: String? = null
)

class SyncEngine(
    private val context: Context,
    private val groupId: String,
    private val deviceId: String,
    private val encryptionKey: ByteArray,
    private val lamportClock: LamportClock
) {

    private val prefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)

    private var lastSyncVersion: Long
        get() = prefs.getLong("lastSyncVersion", 0L)
        set(value) = prefs.edit().putLong("lastSyncVersion", value).apply()

    private var lastPushedClock: Long
        get() = prefs.getLong("lastPushedClock", 0L)
        set(value) = prefs.edit().putLong("lastPushedClock", value).apply()

    suspend fun sync(
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>,
        sharedSettings: SharedSettings = SharedSettingsRepository.load(context)
    ): SyncResult {
        try {
            // Step 0: Stale check — block sync if too many days without success
            val lastSync = prefs.getLong("lastSuccessfulSync", 0L)
            if (lastSync > 0L) {
                val daysSinceSync = (System.currentTimeMillis() - lastSync) / (24 * 60 * 60 * 1000L)
                if (daysSinceSync >= 90) {
                    return SyncResult(success = false, error = "sync_blocked_stale")
                }
            }

            // Step 1: Check stale — if too far behind, use snapshot
            val snapshot = FirestoreService.getSnapshot(groupId)
            val deviceRecord = FirestoreService.getDeviceRecord(groupId, deviceId)
            if (deviceRecord == null && snapshot == null) {
                // Device not registered and no snapshot — possibly removed from group
                return SyncResult(success = false, error = "removed_from_group")
            }
            if (deviceRecord == null && snapshot != null) {
                // New device joining — apply snapshot first
                return applySnapshot(snapshot)
            }

            // Step 2: Fetch remote deltas
            val remoteDeltas = FirestoreService.fetchDeltas(groupId, lastSyncVersion)

            // Step 3: Decrypt and deserialize remote deltas
            val packets = mutableListOf<DeltaPacket>()
            for (delta in remoteDeltas) {
                if (delta.sourceDeviceId == deviceId) continue // skip own deltas
                try {
                    val encrypted = Base64.decode(delta.encryptedPayload, Base64.NO_WRAP)
                    val decrypted = CryptoHelper.decryptWithKey(encrypted, encryptionKey)
                    val json = JSONObject(String(decrypted))
                    packets.add(DeltaSerializer.deserialize(json))
                } catch (e: Exception) {
                    continue // skip unreadable deltas
                }
            }

            // Step 4: Per-field CRDT merge
            var mergedTxns = transactions.toMutableList()
            var mergedRe = recurringExpenses.toMutableList()
            var mergedIs = incomeSources.toMutableList()
            var mergedSg = savingsGoals.toMutableList()
            var mergedAe = amortizationEntries.toMutableList()
            var mergedCat = categories.toMutableList()
            var mergedSettings = sharedSettings
            var settingsChanged = false
            var budgetRecalcNeeded = false

            for (packet in packets) {
                lamportClock.merge(packet.timestamp.epochSecond)
                for (change in packet.changes) {
                    when (change.type) {
                        "transaction" -> mergedTxns = mergeRecordIntoList(
                            mergedTxns, change, deviceId
                        ) { local, remote -> CrdtMerge.mergeTransaction(local, remote, deviceId) }
                        "recurring_expense" -> {
                            mergedRe = mergeRecordIntoList(
                                mergedRe, change, deviceId
                            ) { local, remote -> CrdtMerge.mergeRecurringExpense(local, remote, deviceId) }
                            budgetRecalcNeeded = true
                        }
                        "income_source" -> {
                            mergedIs = mergeRecordIntoList(
                                mergedIs, change, deviceId
                            ) { local, remote -> CrdtMerge.mergeIncomeSource(local, remote, deviceId) }
                            budgetRecalcNeeded = true
                        }
                        "savings_goal" -> mergedSg = mergeRecordIntoList(
                            mergedSg, change, deviceId
                        ) { local, remote -> CrdtMerge.mergeSavingsGoal(local, remote, deviceId) }
                        "amortization_entry" -> {
                            mergedAe = mergeRecordIntoList(
                                mergedAe, change, deviceId
                            ) { local, remote -> CrdtMerge.mergeAmortizationEntry(local, remote, deviceId) }
                            budgetRecalcNeeded = true
                        }
                        "category" -> mergedCat = mergeRecordIntoList(
                            mergedCat, change, deviceId
                        ) { local, remote -> CrdtMerge.mergeCategory(local, remote, deviceId) }
                        "shared_settings" -> {
                            val remoteSettings = deserializeSharedSettings(change, mergedSettings)
                            val before = mergedSettings
                            mergedSettings = CrdtMerge.mergeSharedSettings(mergedSettings, remoteSettings, deviceId)
                            if (mergedSettings != before) {
                                settingsChanged = true
                                if (mergedSettings.budgetPeriod != before.budgetPeriod ||
                                    mergedSettings.resetHour != before.resetHour ||
                                    mergedSettings.resetDayOfWeek != before.resetDayOfWeek ||
                                    mergedSettings.resetDayOfMonth != before.resetDayOfMonth ||
                                    mergedSettings.isManualBudgetEnabled != before.isManualBudgetEnabled ||
                                    mergedSettings.manualBudgetAmount != before.manualBudgetAmount) {
                                    budgetRecalcNeeded = true
                                }
                            }
                        }
                    }
                }
            }

            // Step 6: Push local changes
            val localDeltas = mutableListOf<RecordDelta>()
            val pushClock = lastPushedClock
            for (txn in transactions) {
                DeltaBuilder.buildTransactionDelta(txn, pushClock)?.let { localDeltas.add(it) }
            }
            for (re in recurringExpenses) {
                DeltaBuilder.buildRecurringExpenseDelta(re, pushClock)?.let { localDeltas.add(it) }
            }
            for (src in incomeSources) {
                DeltaBuilder.buildIncomeSourceDelta(src, pushClock)?.let { localDeltas.add(it) }
            }
            for (goal in savingsGoals) {
                DeltaBuilder.buildSavingsGoalDelta(goal, pushClock)?.let { localDeltas.add(it) }
            }
            for (entry in amortizationEntries) {
                DeltaBuilder.buildAmortizationEntryDelta(entry, pushClock)?.let { localDeltas.add(it) }
            }
            for (cat in categories) {
                DeltaBuilder.buildCategoryDelta(cat, pushClock)?.let { localDeltas.add(it) }
            }
            DeltaBuilder.buildSharedSettingsDelta(sharedSettings, pushClock)?.let { localDeltas.add(it) }

            var deltasPushed = 0
            if (localDeltas.isNotEmpty()) {
                val packet = DeltaPacket(
                    sourceDeviceId = deviceId,
                    timestamp = Instant.now(),
                    changes = localDeltas
                )
                val serialized = DeltaSerializer.serialize(packet).toString().toByteArray()
                val encrypted = CryptoHelper.encryptWithKey(serialized, encryptionKey)
                val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)

                val version = FirestoreService.getNextDeltaVersion(groupId)
                FirestoreService.pushDelta(groupId, deviceId, encoded, version)
                deltasPushed = localDeltas.size
                lastPushedClock = lamportClock.value
            }

            // Step 7: Update metadata
            val newSyncVersion = if (remoteDeltas.isNotEmpty()) {
                remoteDeltas.maxOf { it.version }
            } else {
                lastSyncVersion
            }
            lastSyncVersion = newSyncVersion
            FirestoreService.updateDeviceMetadata(groupId, deviceId, newSyncVersion)

            // Save merged settings if changed
            if (settingsChanged) {
                SharedSettingsRepository.save(context, mergedSettings)
            }

            // Step 8: Snapshot maintenance (every 50 deltas)
            if (newSyncVersion > 0 && newSyncVersion % 50 == 0L) {
                val snapshotJson = SnapshotManager.serializeFullState(
                    context, mergedTxns, mergedRe, mergedIs, mergedSg, mergedAe, mergedCat, mergedSettings
                )
                val snapshotBytes = snapshotJson.toString().toByteArray()
                val encryptedSnapshot = CryptoHelper.encryptWithKey(snapshotBytes, encryptionKey)
                val encodedSnapshot = Base64.encodeToString(encryptedSnapshot, Base64.NO_WRAP)
                FirestoreService.writeSnapshot(groupId, newSyncVersion, deviceId, encodedSnapshot)
            }

            // Step 9: Record successful sync time & update group activity for TTL
            prefs.edit().putLong("lastSuccessfulSync", System.currentTimeMillis()).apply()
            FirestoreService.updateGroupActivity(groupId)

            // Step 10: Check admin claim status
            var adminClaim: AdminClaim? = null
            try {
                val claim = FirestoreService.getAdminClaim(groupId)
                if (claim != null && claim.status == "pending") {
                    if (System.currentTimeMillis() > claim.expiresAt) {
                        if (claim.objections.isEmpty()) {
                            // Find current admin from devices
                            val devices = FirestoreService.getDevices(groupId)
                            val currentAdmin = devices.find { it.isAdmin }
                            if (currentAdmin != null) {
                                FirestoreService.transferAdmin(groupId, currentAdmin.deviceId, claim.claimantDeviceId)
                            }
                            FirestoreService.resolveAdminClaim(groupId, "approved")
                        } else {
                            FirestoreService.resolveAdminClaim(groupId, "rejected")
                        }
                    } else {
                        adminClaim = claim
                    }
                }
            } catch (_: Exception) {}

            val hasRemoteChanges = packets.isNotEmpty()
            return SyncResult(
                success = true,
                deltasReceived = packets.sumOf { it.changes.size },
                deltasPushed = deltasPushed,
                budgetRecalcNeeded = budgetRecalcNeeded,
                mergedTransactions = if (hasRemoteChanges) mergedTxns else null,
                mergedRecurringExpenses = if (hasRemoteChanges) mergedRe else null,
                mergedIncomeSources = if (hasRemoteChanges) mergedIs else null,
                mergedSavingsGoals = if (hasRemoteChanges) mergedSg else null,
                mergedAmortizationEntries = if (hasRemoteChanges) mergedAe else null,
                mergedCategories = if (hasRemoteChanges) mergedCat else null,
                mergedSharedSettings = if (settingsChanged) mergedSettings else null,
                pendingAdminClaim = adminClaim
            )
        } catch (e: Exception) {
            val errorCode = when {
                e.message?.contains("NOT_FOUND") == true -> "group_deleted"
                e.message?.contains("PERMISSION_DENIED") == true -> "removed_from_group"
                e is javax.crypto.AEADBadTagException -> "encryption_error"
                else -> e.message
            }
            return SyncResult(success = false, error = errorCode)
        }
    }

    private suspend fun applySnapshot(snapshot: SnapshotRecord): SyncResult {
        return try {
            val encrypted = Base64.decode(snapshot.encryptedData, Base64.NO_WRAP)
            val decrypted = CryptoHelper.decryptWithKey(encrypted, encryptionKey)
            val json = JSONObject(String(decrypted))
            val state = SnapshotManager.deserializeFullState(context, json)
            lastSyncVersion = snapshot.snapshotVersion
            FirestoreService.updateDeviceMetadata(groupId, deviceId, snapshot.snapshotVersion)
            SyncResult(
                success = true,
                deltasReceived = 0,
                budgetRecalcNeeded = true,
                mergedTransactions = state.transactions,
                mergedRecurringExpenses = state.recurringExpenses,
                mergedIncomeSources = state.incomeSources,
                mergedSavingsGoals = state.savingsGoals,
                mergedAmortizationEntries = state.amortizationEntries,
                mergedCategories = state.categories,
                mergedSharedSettings = state.sharedSettings
            )
        } catch (e: Exception) {
            SyncResult(success = false, error = "Failed to apply snapshot: ${e.message}")
        }
    }

    private fun deserializeSharedSettings(change: RecordDelta, base: SharedSettings): SharedSettings {
        var s = base.copy(lastChangedBy = change.deviceId)
        for ((key, fd) in change.fields) {
            when (key) {
                "currency" -> s = s.copy(currency = fd.value as? String ?: s.currency, currency_clock = fd.clock)
                "budgetPeriod" -> s = s.copy(budgetPeriod = fd.value as? String ?: s.budgetPeriod, budgetPeriod_clock = fd.clock)
                "budgetStartDate" -> s = s.copy(budgetStartDate = fd.value as? String, budgetStartDate_clock = fd.clock)
                "isManualBudgetEnabled" -> s = s.copy(isManualBudgetEnabled = fd.value as? Boolean ?: s.isManualBudgetEnabled, isManualBudgetEnabled_clock = fd.clock)
                "manualBudgetAmount" -> s = s.copy(manualBudgetAmount = (fd.value as? Number)?.toDouble() ?: s.manualBudgetAmount, manualBudgetAmount_clock = fd.clock)
                "weekStartSunday" -> s = s.copy(weekStartSunday = fd.value as? Boolean ?: s.weekStartSunday, weekStartSunday_clock = fd.clock)
                "resetDayOfWeek" -> s = s.copy(resetDayOfWeek = (fd.value as? Number)?.toInt() ?: s.resetDayOfWeek, resetDayOfWeek_clock = fd.clock)
                "resetDayOfMonth" -> s = s.copy(resetDayOfMonth = (fd.value as? Number)?.toInt() ?: s.resetDayOfMonth, resetDayOfMonth_clock = fd.clock)
                "resetHour" -> s = s.copy(resetHour = (fd.value as? Number)?.toInt() ?: s.resetHour, resetHour_clock = fd.clock)
                "familyTimezone" -> s = s.copy(familyTimezone = fd.value as? String ?: s.familyTimezone, familyTimezone_clock = fd.clock)
                "matchDays" -> s = s.copy(matchDays = (fd.value as? Number)?.toInt() ?: s.matchDays, matchDays_clock = fd.clock)
                "matchPercent" -> s = s.copy(matchPercent = (fd.value as? Number)?.toFloat() ?: s.matchPercent, matchPercent_clock = fd.clock)
                "matchDollar" -> s = s.copy(matchDollar = (fd.value as? Number)?.toInt() ?: s.matchDollar, matchDollar_clock = fd.clock)
                "matchChars" -> s = s.copy(matchChars = (fd.value as? Number)?.toInt() ?: s.matchChars, matchChars_clock = fd.clock)
                "showAttribution" -> s = s.copy(showAttribution = fd.value as? Boolean ?: s.showAttribution, showAttribution_clock = fd.clock)
            }
        }
        return s
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T : Any> mergeRecordIntoList(
        list: MutableList<T>,
        change: RecordDelta,
        localDeviceId: String,
        mergeFn: (T, T) -> T
    ): MutableList<T> {
        val remote = when (change.type) {
            "transaction" -> deserializeTransaction(change)
            "recurring_expense" -> deserializeRecurringExpense(change)
            "income_source" -> deserializeIncomeSource(change)
            "savings_goal" -> deserializeSavingsGoal(change)
            "amortization_entry" -> deserializeAmortizationEntry(change)
            "category" -> deserializeCategory(change)
            else -> return list
        } as? T ?: return list

        val existingIndex = list.indexOfFirst { getId(it) == change.id }
        if (existingIndex >= 0) {
            list[existingIndex] = mergeFn(list[existingIndex], remote)
        } else {
            list.add(remote)
        }
        return list
    }

    private fun getId(record: Any): Int = when (record) {
        is Transaction -> record.id
        is RecurringExpense -> record.id
        is IncomeSource -> record.id
        is SavingsGoal -> record.id
        is AmortizationEntry -> record.id
        is Category -> record.id
        else -> -1
    }

    private fun deserializeTransaction(change: RecordDelta): Transaction {
        val f = change.fields
        val catAmountsStr = f["categoryAmounts"]?.value as? String
        val categoryAmounts = if (catAmountsStr != null) {
            try {
                val arr = JSONArray(catAmountsStr)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    CategoryAmount(obj.getInt("categoryId"), obj.getDouble("amount"))
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList<CategoryAmount>()

        return Transaction(
            id = change.id,
            type = try { TransactionType.valueOf(f["type"]?.value as? String ?: "EXPENSE") } catch (_: Exception) { TransactionType.EXPENSE },
            date = try { LocalDate.parse(f["date"]?.value as? String) } catch (_: Exception) { LocalDate.now() },
            source = f["source"]?.value as? String ?: "",
            categoryAmounts = categoryAmounts,
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            isUserCategorized = f["isUserCategorized"]?.value as? Boolean ?: true,
            isBudgetIncome = f["isBudgetIncome"]?.value as? Boolean ?: false,
            deviceId = change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            date_clock = f["date"]?.clock ?: 0L,
            type_clock = f["type"]?.clock ?: 0L,
            categoryAmounts_clock = f["categoryAmounts"]?.clock ?: 0L,
            isUserCategorized_clock = f["isUserCategorized"]?.clock ?: 0L,
            isBudgetIncome_clock = f["isBudgetIncome"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L
        )
    }

    private fun deserializeRecurringExpense(change: RecordDelta): RecurringExpense {
        val f = change.fields
        return RecurringExpense(
            id = change.id,
            source = f["source"]?.value as? String ?: "",
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            repeatType = try { RepeatType.valueOf(f["repeatType"]?.value as? String ?: "MONTHS") } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = (f["repeatInterval"]?.value as? Number)?.toInt() ?: 1,
            startDate = try { LocalDate.parse(f["startDate"]?.value as? String) } catch (_: Exception) { null },
            monthDay1 = (f["monthDay1"]?.value as? Number)?.toInt(),
            monthDay2 = (f["monthDay2"]?.value as? Number)?.toInt(),
            deviceId = change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            repeatType_clock = f["repeatType"]?.clock ?: 0L,
            repeatInterval_clock = f["repeatInterval"]?.clock ?: 0L,
            startDate_clock = f["startDate"]?.clock ?: 0L,
            monthDay1_clock = f["monthDay1"]?.clock ?: 0L,
            monthDay2_clock = f["monthDay2"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L
        )
    }

    private fun deserializeIncomeSource(change: RecordDelta): IncomeSource {
        val f = change.fields
        return IncomeSource(
            id = change.id,
            source = f["source"]?.value as? String ?: "",
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            repeatType = try { RepeatType.valueOf(f["repeatType"]?.value as? String ?: "MONTHS") } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = (f["repeatInterval"]?.value as? Number)?.toInt() ?: 1,
            startDate = try { LocalDate.parse(f["startDate"]?.value as? String) } catch (_: Exception) { null },
            monthDay1 = (f["monthDay1"]?.value as? Number)?.toInt(),
            monthDay2 = (f["monthDay2"]?.value as? Number)?.toInt(),
            deviceId = change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            repeatType_clock = f["repeatType"]?.clock ?: 0L,
            repeatInterval_clock = f["repeatInterval"]?.clock ?: 0L,
            startDate_clock = f["startDate"]?.clock ?: 0L,
            monthDay1_clock = f["monthDay1"]?.clock ?: 0L,
            monthDay2_clock = f["monthDay2"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L
        )
    }

    private fun deserializeSavingsGoal(change: RecordDelta): SavingsGoal {
        val f = change.fields
        return SavingsGoal(
            id = change.id,
            name = f["name"]?.value as? String ?: "",
            targetAmount = (f["targetAmount"]?.value as? Number)?.toDouble() ?: 0.0,
            targetDate = try { LocalDate.parse(f["targetDate"]?.value as? String) } catch (_: Exception) { null },
            totalSavedSoFar = (f["totalSavedSoFar"]?.value as? Number)?.toDouble() ?: 0.0,
            contributionPerPeriod = (f["contributionPerPeriod"]?.value as? Number)?.toDouble() ?: 0.0,
            isPaused = f["isPaused"]?.value as? Boolean ?: false,
            deviceId = change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            name_clock = f["name"]?.clock ?: 0L,
            targetAmount_clock = f["targetAmount"]?.clock ?: 0L,
            targetDate_clock = f["targetDate"]?.clock ?: 0L,
            totalSavedSoFar_clock = f["totalSavedSoFar"]?.clock ?: 0L,
            contributionPerPeriod_clock = f["contributionPerPeriod"]?.clock ?: 0L,
            isPaused_clock = f["isPaused"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L
        )
    }

    private fun deserializeAmortizationEntry(change: RecordDelta): AmortizationEntry {
        val f = change.fields
        return AmortizationEntry(
            id = change.id,
            source = f["source"]?.value as? String ?: "",
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            totalPeriods = (f["totalPeriods"]?.value as? Number)?.toInt() ?: 1,
            startDate = try { LocalDate.parse(f["startDate"]?.value as? String) } catch (_: Exception) { LocalDate.now() },
            deviceId = change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            isPaused = f["isPaused"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            totalPeriods_clock = f["totalPeriods"]?.clock ?: 0L,
            startDate_clock = f["startDate"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            isPaused_clock = f["isPaused"]?.clock ?: 0L
        )
    }

    private fun deserializeCategory(change: RecordDelta): Category {
        val f = change.fields
        return Category(
            id = change.id,
            name = f["name"]?.value as? String ?: "",
            iconName = f["iconName"]?.value as? String ?: "label",
            tag = f["tag"]?.value as? String ?: "",
            deviceId = change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            name_clock = f["name"]?.clock ?: 0L,
            iconName_clock = f["iconName"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L
        )
    }
}

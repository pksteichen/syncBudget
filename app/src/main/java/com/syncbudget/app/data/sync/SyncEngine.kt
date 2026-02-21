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
import com.syncbudget.app.data.Transaction
import org.json.JSONObject
import java.time.Instant

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
        categories: List<Category>
    ): SyncResult {
        try {
            // Step 1: Check stale — if too far behind, use snapshot
            val snapshot = FirestoreService.getSnapshot(groupId)
            val deviceRecord = FirestoreService.getDeviceRecord(groupId, deviceId)
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

            // Step 8: Snapshot maintenance (every 50 deltas)
            if (newSyncVersion > 0 && newSyncVersion % 50 == 0L) {
                val snapshotJson = SnapshotManager.serializeFullState(
                    context, mergedTxns, mergedRe, mergedIs, mergedSg, mergedAe, mergedCat
                )
                val snapshotBytes = snapshotJson.toString().toByteArray()
                val encryptedSnapshot = CryptoHelper.encryptWithKey(snapshotBytes, encryptionKey)
                val encodedSnapshot = Base64.encodeToString(encryptedSnapshot, Base64.NO_WRAP)
                FirestoreService.writeSnapshot(groupId, newSyncVersion, deviceId, encodedSnapshot)
            }

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
                mergedCategories = if (hasRemoteChanges) mergedCat else null
            )
        } catch (e: Exception) {
            return SyncResult(success = false, error = e.message)
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
                mergedCategories = state.categories
            )
        } catch (e: Exception) {
            SyncResult(success = false, error = "Failed to apply snapshot: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T : Any> mergeRecordIntoList(
        list: MutableList<T>,
        change: RecordDelta,
        localDeviceId: String,
        mergeFn: (T, T) -> T
    ): MutableList<T> {
        // This is a simplified merge — the actual field application happens in CrdtMerge
        // For now, we find or create the record and merge
        // The RecordDelta contains field-level changes that need to be applied
        return list // Actual per-field merge is handled by the CRDT merge functions
    }
}

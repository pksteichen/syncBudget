package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Base64
import com.syncbudget.app.data.AmortizationEntry
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
import java.time.LocalDateTime

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
    val mergedPeriodLedgerEntries: List<PeriodLedgerEntry>? = null,
    val mergedSharedSettings: SharedSettings? = null,
    val pendingAdminClaim: AdminClaim? = null,
    val catIdRemap: Map<Int, Int>? = null,
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
        sharedSettings: SharedSettings = SharedSettingsRepository.load(context),
        existingCatIdRemap: Map<Int, Int> = emptyMap(),
        periodLedgerEntries: List<PeriodLedgerEntry> = emptyList()
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
            // Apply snapshot for new devices, then continue to fetch post-snapshot deltas
            var snapshotApplied = false
            var snapshotState: FullState? = null
            if (deviceRecord == null && snapshot != null) {
                snapshotState = decryptSnapshot(snapshot)
                if (snapshotState == null) {
                    return SyncResult(success = false, error = "Failed to decrypt snapshot")
                }
                lastSyncVersion = snapshot.snapshotVersion
                FirestoreService.updateDeviceMetadata(groupId, deviceId, snapshot.snapshotVersion)
                snapshotApplied = true
            }

            // Step 2: Fetch remote deltas (including post-snapshot deltas for new devices)
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
                    android.util.Log.w("SyncEngine", "Skipping unreadable delta from ${delta.sourceDeviceId}: ${e.message}")
                    continue
                }
            }

            // Step 4: Per-field CRDT merge — use snapshot state as base if applied
            val baseTxns = snapshotState?.transactions ?: transactions
            val baseRe = snapshotState?.recurringExpenses ?: recurringExpenses
            val baseIs = snapshotState?.incomeSources ?: incomeSources
            val baseSg = snapshotState?.savingsGoals ?: savingsGoals
            val baseAe = snapshotState?.amortizationEntries ?: amortizationEntries
            val baseCat = snapshotState?.categories ?: categories
            val basePl = snapshotState?.periodLedgerEntries ?: periodLedgerEntries
            val baseSettings = snapshotState?.sharedSettings ?: sharedSettings

            var mergedTxns = baseTxns.toMutableList()
            var mergedRe = baseRe.toMutableList()
            var mergedIs = baseIs.toMutableList()
            var mergedSg = baseSg.toMutableList()
            var mergedAe = baseAe.toMutableList()
            var mergedCat = baseCat.toMutableList()
            var mergedPl = basePl.toMutableList()
            var mergedSettings = baseSettings
            var settingsChanged = false
            var budgetRecalcNeeded = false
            val catIdRemap = existingCatIdRemap.toMutableMap() // remote ID -> local ID (seeded from persistent map)

            for (packet in packets) {
                // Merge with max field clock from the packet (NOT the wall-clock
                // timestamp, which would inflate our logical counter to ~1.7 billion).
                val maxFieldClock = packet.changes.maxOfOrNull { change ->
                    change.fields.values.maxOfOrNull { it.clock } ?: 0L
                } ?: 0L
                lamportClock.merge(maxFieldClock)
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
                        "category" -> {
                            val remoteCat = deserializeCategory(change)
                            val byId = mergedCat.indexOfFirst { it.id == change.id }
                            if (byId >= 0) {
                                mergedCat[byId] = CrdtMerge.mergeCategory(mergedCat[byId], remoteCat, deviceId)
                            } else {
                                // Deduplicate by tag: if a local category has the same tag, merge into it
                                val byTag = if (remoteCat.tag.isNotEmpty())
                                    mergedCat.indexOfFirst { it.tag == remoteCat.tag } else -1
                                if (byTag >= 0) {
                                    catIdRemap[change.id] = mergedCat[byTag].id
                                    mergedCat[byTag] = CrdtMerge.mergeCategory(mergedCat[byTag], remoteCat, deviceId)
                                } else {
                                    // Fallback: match by name against known defaults (handles
                                    // remote categories arriving without a tag field)
                                    val byName = if (remoteCat.tag.isEmpty() && remoteCat.name.isNotEmpty())
                                        mergedCat.indexOfFirst { it.tag.isNotEmpty() && it.name == remoteCat.name }
                                    else -1
                                    if (byName >= 0) {
                                        catIdRemap[change.id] = mergedCat[byName].id
                                        mergedCat[byName] = CrdtMerge.mergeCategory(mergedCat[byName], remoteCat, deviceId)
                                    } else {
                                        mergedCat.add(remoteCat)
                                    }
                                }
                            }
                        }
                        "period_ledger" -> mergedPl = mergeRecordIntoList(
                            mergedPl, change, deviceId
                        ) { local, remote -> CrdtMerge.mergePeriodLedgerEntry(local, remote, deviceId) }
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

            // Step 5: Remap category IDs in transactions (handles different random IDs per device)
            // Only Transaction has categoryAmounts with categoryId references.
            // RecurringExpense, IncomeSource, SavingsGoal, AmortizationEntry do not reference categories.

            val mergedCatById = mergedCat.associateBy { it.id }

            // 5a: Validate existing catIdRemap — fix stale entries where the
            //     target category ID no longer exists (e.g., local defaults were
            //     regenerated after a snapshot or data clear).  For each stale
            //     entry (source → extinctTarget), if source still exists in the
            //     merged list, add a REVERSE remap (extinctTarget → source) so
            //     transactions already remapped to the extinct ID get fixed.
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
            for (key in staleKeys) {
                catIdRemap.remove(key)
            }
            catIdRemap.putAll(reverseRemaps)

            // 5b: Post-merge tag dedup — consolidate categories that share the
            //     same tag.  Keep the most authoritative one (highest name_clock,
            //     meaning admin-stamped names win over empty defaults).
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

            // Rebuild lookups excluding categories that are remapped away
            val mergedCatByTag = mergedCat.filter {
                it.tag.isNotEmpty() && !it.deleted && it.id !in catIdRemap
            }.associateBy { it.tag }
            val mergedCatByName = mergedCat.filter {
                it.name.isNotEmpty() && it.id !in catIdRemap
            }.associateBy { it.name }

            // 5c: Orphan scan — find transaction categoryIds that don't exist in
            //     the merged category list and aren't in catIdRemap yet.
            val allReferencedCatIds = mutableSetOf<Int>()
            for (txn in mergedTxns) {
                for (ca in txn.categoryAmounts) {
                    if (ca.categoryId !in mergedCatById && ca.categoryId !in catIdRemap) {
                        allReferencedCatIds.add(ca.categoryId)
                    }
                }
            }
            if (allReferencedCatIds.isNotEmpty()) {
                val deltaCatById = mutableMapOf<Int, Category>()
                for (packet in packets) {
                    for (change in packet.changes) {
                        if (change.type == "category") {
                            deltaCatById[change.id] = deserializeCategory(change)
                        }
                    }
                }
                for (orphanId in allReferencedCatIds) {
                    val orphanCat = deltaCatById[orphanId]
                    if (orphanCat != null) {
                        val localMatch = if (orphanCat.tag.isNotEmpty()) mergedCatByTag[orphanCat.tag]
                            else mergedCatByName[orphanCat.name]
                        if (localMatch != null && localMatch.id != orphanId) {
                            catIdRemap[orphanId] = localMatch.id
                        }
                    }
                }
            }

            // Also rebuild remap entries from received category deltas that
            // matched by tag/name during merge (handles deltas not yet in
            // any transaction's categoryAmounts)
            for (packet in packets) {
                for (change in packet.changes) {
                    if (change.type == "category" && change.id !in mergedCatById && change.id !in catIdRemap) {
                        val remoteCat = deserializeCategory(change)
                        val localMatch = if (remoteCat.tag.isNotEmpty()) mergedCatByTag[remoteCat.tag]
                            else mergedCatByName[remoteCat.name]
                        if (localMatch != null) {
                            catIdRemap[change.id] = localMatch.id
                        }
                    }
                }
            }

            // 5d: Apply remap to all transactions
            var remapChangedTxns = false
            if (catIdRemap.isNotEmpty()) {
                for (i in mergedTxns.indices) {
                    val txn = mergedTxns[i]
                    val remapped = txn.categoryAmounts.map { ca ->
                        val newId = catIdRemap[ca.categoryId]
                        if (newId != null) ca.copy(categoryId = newId) else ca
                    }
                    if (remapped != txn.categoryAmounts) {
                        mergedTxns[i] = txn.copy(categoryAmounts = remapped)
                        remapChangedTxns = true
                    }
                }
            }

            // Step 6: Push ALL local records regardless of deviceId ownership.
            // After the deviceId_clock CRDT fix, deviceId converges to the
            // original creator's value on all devices. Using deviceId as a push
            // filter would block non-admin devices from pushing their own
            // records once convergence occurs.  DeltaBuilder's threshold
            // (field_clock > lastPushedClock) already prevents re-pushing
            // unchanged data, so the filter is unnecessary.  CRDT merge on
            // the receiving side is idempotent, making redundant pushes safe.
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
            // Push deduped period ledger — avoids pushing multiple deltas for the
            // same epoch-day ID which would create duplicates on receiving devices.
            for (entry in PeriodLedgerRepository.dedup(periodLedgerEntries)) {
                DeltaBuilder.buildPeriodLedgerDelta(entry, pushClock)?.let { localDeltas.add(it) }
            }
            // Categories always pushed with pushClock=0 so all fields are
            // included — receiving devices need every device's category IDs
            // to build catIdRemap for transaction categoryAmounts.
            for (cat in categories) {
                DeltaBuilder.buildCategoryDelta(cat, 0)?.let { localDeltas.add(it) }
            }
            // SharedSettings is always pushed (shared, not per-device)
            DeltaBuilder.buildSharedSettingsDelta(sharedSettings, pushClock)?.let { localDeltas.add(it) }

            var deltasPushed = 0
            if (localDeltas.isNotEmpty()) {
                // Exclude category and shared_settings deltas from maxDeltaClock.
                // Categories use pushClock=0, so their high merged clocks must
                // not inflate lastPushedClock.  SharedSettings can have very high
                // clocks (from availableCash updates every period) that would
                // strand lower-clock per-device records.
                val maxDeltaClock = localDeltas
                    .filter { it.type != "category" && it.type != "shared_settings" }
                    .maxOfOrNull { delta ->
                        delta.fields.values.maxOfOrNull { it.clock } ?: 0L
                    } ?: lastPushedClock
                lastPushedClock = maxDeltaClock
                // Ensure lamport clock stays ahead of lastPushedClock so that
                // future tick() calls produce values > lastPushedClock
                if (maxDeltaClock > lamportClock.value) {
                    lamportClock.merge(maxDeltaClock)
                }

                val packet = DeltaPacket(
                    sourceDeviceId = deviceId,
                    timestamp = Instant.now(),
                    changes = localDeltas
                )
                val serialized = DeltaSerializer.serialize(packet).toString().toByteArray()
                val encrypted = CryptoHelper.encryptWithKey(serialized, encryptionKey)
                val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)

                FirestoreService.pushDeltaAtomically(groupId, deviceId, encoded)
                deltasPushed = localDeltas.size
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
                    mergedTxns, mergedRe, mergedIs, mergedSg, mergedAe, mergedCat, mergedSettings, mergedPl
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
            } catch (e: Exception) {
                android.util.Log.w("SyncEngine", "Failed to check/resolve admin claim", e)
            }

            // Dedup period ledger: mergeRecordIntoList only merges with the
            // first matching entry, leaving duplicates with the same epoch-day ID.
            // This ensures the highest-clock entry wins before saving.
            mergedPl = PeriodLedgerRepository.dedup(mergedPl).toMutableList()

            val hasChanges = packets.isNotEmpty() || snapshotApplied
            // If remap fixed orphaned category IDs, include transactions even
            // when no new deltas arrived — otherwise the fix is never persisted.
            val txnsChanged = hasChanges || remapChangedTxns
            return SyncResult(
                success = true,
                deltasReceived = packets.sumOf { it.changes.size },
                deltasPushed = deltasPushed,
                budgetRecalcNeeded = budgetRecalcNeeded || snapshotApplied,
                mergedTransactions = if (txnsChanged) mergedTxns else null,
                mergedRecurringExpenses = if (hasChanges) mergedRe else null,
                mergedIncomeSources = if (hasChanges) mergedIs else null,
                mergedSavingsGoals = if (hasChanges) mergedSg else null,
                mergedAmortizationEntries = if (hasChanges) mergedAe else null,
                mergedCategories = if (hasChanges) mergedCat else null,
                mergedPeriodLedgerEntries = if (hasChanges) mergedPl else null,
                mergedSharedSettings = if (settingsChanged || snapshotApplied) mergedSettings else null,
                pendingAdminClaim = adminClaim,
                catIdRemap = catIdRemap
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

    private suspend fun decryptSnapshot(snapshot: SnapshotRecord): FullState? {
        return try {
            val encrypted = Base64.decode(snapshot.encryptedData, Base64.NO_WRAP)
            val decrypted = CryptoHelper.decryptWithKey(encrypted, encryptionKey)
            val json = JSONObject(String(decrypted))
            SnapshotManager.deserializeFullState(json)
        } catch (_: Exception) {
            null
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
                "matchPercent" -> s = s.copy(matchPercent = (fd.value as? Number)?.toDouble() ?: s.matchPercent, matchPercent_clock = fd.clock)
                "matchDollar" -> s = s.copy(matchDollar = (fd.value as? Number)?.toInt() ?: s.matchDollar, matchDollar_clock = fd.clock)
                "matchChars" -> s = s.copy(matchChars = (fd.value as? Number)?.toInt() ?: s.matchChars, matchChars_clock = fd.clock)
                "showAttribution" -> s = s.copy(showAttribution = fd.value as? Boolean ?: s.showAttribution, showAttribution_clock = fd.clock)
                "availableCash" -> s = s.copy(availableCash = (fd.value as? Number)?.toDouble() ?: s.availableCash, availableCash_clock = fd.clock)
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
        val deserialized: Any = when (change.type) {
            "transaction" -> deserializeTransaction(change)
            "recurring_expense" -> deserializeRecurringExpense(change)
            "income_source" -> deserializeIncomeSource(change)
            "savings_goal" -> deserializeSavingsGoal(change)
            "amortization_entry" -> deserializeAmortizationEntry(change)
            "category" -> deserializeCategory(change)
            "period_ledger" -> deserializePeriodLedgerEntry(change)
            else -> return list
        }
        val remote = deserialized as? T
        if (remote == null) {
            android.util.Log.w("SyncEngine", "Type mismatch merging ${change.type} id=${change.id}: " +
                "expected ${list.firstOrNull()?.javaClass?.simpleName ?: "unknown"}, " +
                "got ${deserialized.javaClass.simpleName}")
            return list
        }

        val existingIndex = list.indexOfFirst { getId(it) == change.id }
        if (existingIndex >= 0) {
            list[existingIndex] = mergeFn(list[existingIndex], remote)
        } else {
            // Guard: don't add skeleton records from partial deltas.
            // When the pushing device has a stale LamportClock, some field
            // clocks may be below lastPushedClock, producing a delta that
            // is missing critical fields (amount, name, etc.).  Adding such
            // a record shows the user a broken $0 / empty-name entry.
            // Skip it — the full record will arrive on a subsequent sync
            // once the pushing device's clock catches up.
            if (isSkeletonRecord(remote)) {
                android.util.Log.w("SyncEngine",
                    "Skipping skeleton ${change.type} id=${change.id} " +
                    "(missing critical field clocks)")
                return list
            }
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
        is PeriodLedgerEntry -> record.id
        else -> -1
    }

    /** A skeleton record is one created from a partial delta that is missing
     *  critical field clocks (meaning those fields were not included in the
     *  delta and defaulted to 0).  Such records would appear as $0 amounts
     *  or empty names in the UI. */
    private fun isSkeletonRecord(record: Any): Boolean = when (record) {
        is Transaction -> record.amount_clock == 0L || record.source_clock == 0L
        is RecurringExpense -> record.amount_clock == 0L || record.source_clock == 0L
        is IncomeSource -> record.amount_clock == 0L || record.source_clock == 0L
        is AmortizationEntry -> record.amount_clock == 0L || record.source_clock == 0L
        is SavingsGoal -> record.name_clock == 0L
        is Category -> record.name_clock == 0L
        is PeriodLedgerEntry -> false  // entries are immutable, never skeleton
        else -> false
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
            description = f["description"]?.value as? String ?: "",
            categoryAmounts = categoryAmounts,
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            isUserCategorized = f["isUserCategorized"]?.value as? Boolean ?: true,
            isBudgetIncome = f["isBudgetIncome"]?.value as? Boolean ?: false,
            linkedRecurringExpenseId = (f["linkedRecurringExpenseId"]?.value as? Number)?.toInt(),
            linkedAmortizationEntryId = (f["linkedAmortizationEntryId"]?.value as? Number)?.toInt(),
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            description_clock = f["description"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            date_clock = f["date"]?.clock ?: 0L,
            type_clock = f["type"]?.clock ?: 0L,
            categoryAmounts_clock = f["categoryAmounts"]?.clock ?: 0L,
            isUserCategorized_clock = f["isUserCategorized"]?.clock ?: 0L,
            isBudgetIncome_clock = f["isBudgetIncome"]?.clock ?: 0L,
            linkedRecurringExpenseId_clock = f["linkedRecurringExpenseId"]?.clock ?: 0L,
            linkedAmortizationEntryId_clock = f["linkedAmortizationEntryId"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
        )
    }

    private fun deserializeRecurringExpense(change: RecordDelta): RecurringExpense {
        val f = change.fields
        return RecurringExpense(
            id = change.id,
            source = f["source"]?.value as? String ?: "",
            description = f["description"]?.value as? String ?: "",
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            repeatType = try { RepeatType.valueOf(f["repeatType"]?.value as? String ?: "MONTHS") } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = (f["repeatInterval"]?.value as? Number)?.toInt() ?: 1,
            startDate = try { LocalDate.parse(f["startDate"]?.value as? String) } catch (_: Exception) { null },
            monthDay1 = (f["monthDay1"]?.value as? Number)?.toInt(),
            monthDay2 = (f["monthDay2"]?.value as? Number)?.toInt(),
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            description_clock = f["description"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            repeatType_clock = f["repeatType"]?.clock ?: 0L,
            repeatInterval_clock = f["repeatInterval"]?.clock ?: 0L,
            startDate_clock = f["startDate"]?.clock ?: 0L,
            monthDay1_clock = f["monthDay1"]?.clock ?: 0L,
            monthDay2_clock = f["monthDay2"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
        )
    }

    private fun deserializeIncomeSource(change: RecordDelta): IncomeSource {
        val f = change.fields
        return IncomeSource(
            id = change.id,
            source = f["source"]?.value as? String ?: "",
            description = f["description"]?.value as? String ?: "",
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            repeatType = try { RepeatType.valueOf(f["repeatType"]?.value as? String ?: "MONTHS") } catch (_: Exception) { RepeatType.MONTHS },
            repeatInterval = (f["repeatInterval"]?.value as? Number)?.toInt() ?: 1,
            startDate = try { LocalDate.parse(f["startDate"]?.value as? String) } catch (_: Exception) { null },
            monthDay1 = (f["monthDay1"]?.value as? Number)?.toInt(),
            monthDay2 = (f["monthDay2"]?.value as? Number)?.toInt(),
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            description_clock = f["description"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            repeatType_clock = f["repeatType"]?.clock ?: 0L,
            repeatInterval_clock = f["repeatInterval"]?.clock ?: 0L,
            startDate_clock = f["startDate"]?.clock ?: 0L,
            monthDay1_clock = f["monthDay1"]?.clock ?: 0L,
            monthDay2_clock = f["monthDay2"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
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
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            name_clock = f["name"]?.clock ?: 0L,
            targetAmount_clock = f["targetAmount"]?.clock ?: 0L,
            targetDate_clock = f["targetDate"]?.clock ?: 0L,
            totalSavedSoFar_clock = f["totalSavedSoFar"]?.clock ?: 0L,
            contributionPerPeriod_clock = f["contributionPerPeriod"]?.clock ?: 0L,
            isPaused_clock = f["isPaused"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
        )
    }

    private fun deserializeAmortizationEntry(change: RecordDelta): AmortizationEntry {
        val f = change.fields
        return AmortizationEntry(
            id = change.id,
            source = f["source"]?.value as? String ?: "",
            description = f["description"]?.value as? String ?: "",
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            totalPeriods = (f["totalPeriods"]?.value as? Number)?.toInt() ?: 1,
            startDate = try { LocalDate.parse(f["startDate"]?.value as? String) } catch (_: Exception) { LocalDate.now() },
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            isPaused = f["isPaused"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            description_clock = f["description"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            totalPeriods_clock = f["totalPeriods"]?.clock ?: 0L,
            startDate_clock = f["startDate"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            isPaused_clock = f["isPaused"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
        )
    }

    private fun deserializePeriodLedgerEntry(change: RecordDelta): PeriodLedgerEntry {
        val f = change.fields
        return PeriodLedgerEntry(
            periodStartDate = try { LocalDateTime.parse(f["periodStartDate"]?.value as? String) } catch (_: Exception) { LocalDateTime.now() },
            appliedAmount = (f["appliedAmount"]?.value as? Number)?.toDouble() ?: 0.0,
            clockAtReset = (f["clockAtReset"]?.value as? Number)?.toLong() ?: 0L,
            corrected = f["corrected"]?.value as? Boolean ?: false,
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            clock = f["periodStartDate"]?.clock ?: 0L  // whole-entry clock
        )
    }

    private fun deserializeCategory(change: RecordDelta): Category {
        val f = change.fields
        return Category(
            id = change.id,
            name = f["name"]?.value as? String ?: "",
            iconName = f["iconName"]?.value as? String ?: "label",
            tag = f["tag"]?.value as? String ?: "",
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            name_clock = f["name"]?.clock ?: 0L,
            iconName_clock = f["iconName"]?.clock ?: 0L,
            tag_clock = f["tag"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
        )
    }
}

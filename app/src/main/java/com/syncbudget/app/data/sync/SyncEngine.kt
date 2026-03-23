package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.tasks.await
import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CryptoHelper
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.BackupManager
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.SharedSettingsRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionType
import com.syncbudget.app.data.CategoryAmount
import com.syncbudget.app.data.RepeatType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
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
    val error: String? = null,
    val repairAttempted: Boolean = false
)

class SyncEngine(
    private val context: Context,
    private val groupId: String,
    private val deviceId: String,
    private val encryptionKey: ByteArray,
    private val lamportClock: LamportClock
) {
    companion object {
        const val SNAPSHOT_CATCHUP_THRESHOLD = 100
        const val SNAPSHOT_WRITE_THRESHOLD = 100
        const val INTEGRITY_CHECK_INTERVAL_MS = 30 * 60 * 1000L  // 30 minutes
        /** Increment this when a release introduces breaking sync changes
         *  (new synced data types, changed serialization formats, etc.).
         *  Non-breaking changes (UI, bug fixes) do NOT require incrementing.
         *  See .claude-memory/project_min_sync_version.md for history.
         *  v2: encrypted pairing keys, auth-required Firestore, fingerprint
         *  dedup, skeleton filter changes, category tag-hash fingerprint. */
        const val MIN_SYNC_VERSION = 2
        const val APP_SYNC_VERSION = 2
    }

    private val prefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)

    private val syncLogFile: java.io.File by lazy {
        val dir = BackupManager.getSupportDir()
        java.io.File(dir, "sync_log.txt")
    }

    private var progressCallback: ((String) -> Unit)? = null

    private fun syncLog(msg: String) {
        val ts = LocalDateTime.now().toString()
        val line = "[$ts] $msg\n"
        android.util.Log.d("SyncEngine", msg)
        if (!com.syncbudget.app.BuildConfig.DEBUG) return  // No file logging in release
        try {
            // Rotate log at 1MB to prevent unbounded growth
            if (syncLogFile.exists() && syncLogFile.length() > 1_000_000L) {
                val rotated = java.io.File(syncLogFile.parent, "sync_log_prev.txt")
                rotated.delete()
                syncLogFile.renameTo(rotated)
            }
            syncLogFile.appendText(line)
        } catch (_: Exception) {}
    }

    private fun reportProgress(msg: String) {
        syncLog(msg)
        progressCallback?.invoke(msg)
    }

    private var lastSyncVersion: Long
        get() = prefs.getLong("lastSyncVersion", 0L)
        set(value) = prefs.edit().putLong("lastSyncVersion", value).apply()

    private var lastPushedClock: Long
        get() = prefs.getLong("lastPushedClock", 0L)
        set(value) = prefs.edit().putLong("lastPushedClock", value).apply()

    private var lastSnapshotVersion: Long
        get() = prefs.getLong("lastSnapshotVersion", 0L)
        set(value) = prefs.edit().putLong("lastSnapshotVersion", value).apply()

    private var lastIntegrityCheckTime: Long
        get() = prefs.getLong("lastIntegrityCheckTime", 0L)
        set(value) = prefs.edit().putLong("lastIntegrityCheckTime", value).apply()

    private var lastRepairSignature: String
        get() = prefs.getString("lastRepairSignature", "") ?: ""
        set(value) = prefs.edit().putString("lastRepairSignature", value).apply()

    private var consecutiveRepairCount: Int
        get() = prefs.getInt("consecutiveRepairCount", 0)
        set(value) = prefs.edit().putInt("consecutiveRepairCount", value).apply()

    /** Create a snapshot of current data for new device bootstrapping.
     *  Call after a successful sync to ensure the snapshot is up-to-date. */
    suspend fun writeSnapshot(
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>,
        sharedSettings: SharedSettings,
        periodLedgerEntries: List<PeriodLedgerEntry>,
        catIdRemap: Map<Int, Int> = emptyMap()
    ) {
        val snapshotJson = SnapshotManager.serializeFullState(
            transactions, recurringExpenses, incomeSources, savingsGoals,
            amortizationEntries, categories, sharedSettings, periodLedgerEntries,
            catIdRemap
        )
        val snapshotBytes = snapshotJson.toString().toByteArray()
        val encryptedSnapshot = CryptoHelper.encryptWithKey(snapshotBytes, encryptionKey)
        val encodedSnapshot = Base64.encodeToString(encryptedSnapshot, Base64.NO_WRAP)
        FirestoreService.writeSnapshot(groupId, lastSyncVersion, deviceId, encodedSnapshot)
    }

    private inline fun <T : Any> overlayRecords(
        base: List<T>, local: List<T>, mergeFn: (T, T) -> T
    ): List<T> {
        val result = base.toMutableList()
        val baseById = base.withIndex().associate { (i, r) -> getId(r) to i }
        for (localRecord in local) {
            val localId = getId(localRecord)
            val baseIndex = baseById[localId]
            if (baseIndex != null) {
                result[baseIndex] = mergeFn(result[baseIndex], localRecord)
            } else {
                result.add(localRecord)
            }
        }
        return result
    }

    suspend fun sync(
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>,
        sharedSettings: SharedSettings = SharedSettingsRepository.load(context),
        existingCatIdRemap: Map<Int, Int> = emptyMap(),
        periodLedgerEntries: List<PeriodLedgerEntry> = emptyList(),
        onProgress: ((String) -> Unit)? = null
    ): SyncResult {
        try {
            progressCallback = onProgress
            val inputClockSum = transactions.sumOf { IntegrityChecker.maxClock(it) }
            syncLog("=== Sync started (device=$deviceId, syncVer=$lastSyncVersion, pushClock=$lastPushedClock, inputTxnClockSum=$inputClockSum) ===")

            val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val isPaidUser = appPrefs.getBoolean("isPaidUser", false) || appPrefs.getBoolean("isSubscriber", false)

            // Step 0: Stale check — block sync if too many days without success
            val lastSync = prefs.getLong("lastSuccessfulSync", 0L)
            if (lastSync > 0L) {
                val daysSinceSync = (System.currentTimeMillis() - lastSync) / (24 * 60 * 60 * 1000L)
                if (daysSinceSync >= 90) {
                    return SyncResult(success = false, error = "sync_blocked_stale")
                }
            }

            // Step 0b: Version compatibility check — block sync if any device
            // in the group requires a higher sync version than we support.
            try {
                val maxMinVersion = FirestoreService.getMaxMinSyncVersion(groupId)
                if (maxMinVersion > APP_SYNC_VERSION) {
                    syncLog("Sync blocked: a device requires sync version $maxMinVersion but we are version $APP_SYNC_VERSION")
                    return SyncResult(success = false, error = "update_required")
                }
            } catch (_: Exception) {}

            // Step 1: Check for explicit removal/dissolution signals first
            if (FirestoreService.isGroupDissolved(groupId)) {
                syncLog("Group has been dissolved by admin")
                return SyncResult(success = false, error = "group_deleted")
            }
            if (FirestoreService.isDeviceRemoved(groupId, deviceId)) {
                syncLog("Device has been removed from group by admin")
                return SyncResult(success = false, error = "removed_from_group")
            }

            // Step 1b: Check device registration; bootstrap from snapshot if new
            var deviceRecord = FirestoreService.getDeviceRecord(groupId, deviceId)
            var snapshotApplied = false
            var snapshotCatchUp = false
            var snapshotState: FullState? = null
            var catchUpSnapshotVersion = 0L
            if (deviceRecord == null) {
                // Retry once after a short delay to rule out transient Firestore errors
                delay(2000)
                deviceRecord = FirestoreService.getDeviceRecord(groupId, deviceId)
            }
            if (deviceRecord == null) {
                // No device record and no explicit removal — likely a new device
                // or transient Firestore cache issue. Try to bootstrap from snapshot.
                syncLog("No device record found — bootstrapping from snapshot")
                reportProgress("Loading family data…")
                val snapshot = FirestoreService.getSnapshot(groupId)
                if (snapshot == null) {
                    // No snapshot available — return a non-fatal error so the
                    // sync loop retries without triggering auto-leave.
                    syncLog("No snapshot available and no device record — will retry")
                    return SyncResult(success = false, error = "device_not_found")
                }
                snapshotState = decryptSnapshot(snapshot)
                if (snapshotState == null) {
                    return SyncResult(success = false, error = "Failed to decrypt snapshot")
                }
                lastSyncVersion = snapshot.snapshotVersion
                FirestoreService.updateDeviceMetadata(groupId, deviceId, snapshot.snapshotVersion)
                snapshotApplied = true
            } else {
                val lastSync = prefs.getLong("lastSuccessfulSync", 0L)
                if (lastSync == 0L && lastSyncVersion == 0L) {
                    // Device record exists (created during join) but has never
                    // completed a sync.  Bootstrap from snapshot just like a new device.
                    syncLog("Device record exists but never synced — bootstrapping from snapshot")
                    reportProgress("Loading family data…")
                    val snapshot = FirestoreService.getSnapshot(groupId)
                    if (snapshot != null) {
                        val decrypted = decryptSnapshot(snapshot)
                        if (decrypted != null) {
                            snapshotState = decrypted
                            lastSyncVersion = snapshot.snapshotVersion
                            FirestoreService.updateDeviceMetadata(groupId, deviceId, snapshot.snapshotVersion)
                            snapshotApplied = true
                            syncLog("Snapshot applied (version ${snapshot.snapshotVersion})")
                        } else {
                            syncLog("Snapshot decrypt failed — encryption key may be wrong")
                            return SyncResult(success = false, error = "snapshot_decrypt_failed")
                        }
                    } else {
                        syncLog("No snapshot available — falling through to delta fetch from 0")
                    }
                    // else: fall through to normal delta fetch from version 0
                } else if (lastSync > 0L) {
                    // Existing device — check if far enough behind to benefit
                    // from snapshot catch-up.  Uses delta count only (no time
                    // gate) so devices behind due to push loops, long offline
                    // periods, or slow networks always get the fast path.
                    val nextVersion = FirestoreService.getGroupNextVersion(groupId)
                    val deltasBehind = nextVersion - 1 - lastSyncVersion
                    if (deltasBehind > SNAPSHOT_CATCHUP_THRESHOLD) {
                        syncLog("Device is $deltasBehind deltas behind — attempting snapshot catch-up")
                        val snapshot = FirestoreService.getSnapshot(groupId)
                        if (snapshot != null && snapshot.snapshotVersion > lastSyncVersion) {
                            val decrypted = decryptSnapshot(snapshot)
                            if (decrypted != null) {
                                snapshotState = decrypted
                                lastSyncVersion = snapshot.snapshotVersion
                                catchUpSnapshotVersion = snapshot.snapshotVersion
                                snapshotCatchUp = true
                            }
                        }
                        // else: fall through to normal paginated fetch
                    }
                }
            }

            // Step 2: Fetch remote deltas (including post-snapshot deltas for new devices)
            syncLog("Fetching deltas from version $lastSyncVersion")
            reportProgress("Checking for updates…")
            val remoteDeltas = FirestoreService.fetchDeltas(groupId, lastSyncVersion)
            syncLog("Fetched ${remoteDeltas.size} deltas")
            if (remoteDeltas.size > 10) {
                reportProgress("Processing ${remoteDeltas.size} updates…")
            }

            // Step 3: Decrypt and deserialize remote deltas
            val packets = mutableListOf<DeltaPacket>()
            for ((idx, delta) in remoteDeltas.withIndex()) {
                if (delta.sourceDeviceId == deviceId) continue // skip own deltas
                try {
                    val encrypted = Base64.decode(delta.encryptedPayload, Base64.NO_WRAP)
                    val decrypted = CryptoHelper.decryptWithKey(encrypted, encryptionKey)
                    val json = JSONObject(String(decrypted))
                    val packet = DeltaSerializer.deserialize(json)
                    // Validate payload deviceId matches Firestore metadata
                    if (packet.sourceDeviceId != delta.sourceDeviceId) {
                        syncLog("WARNING: Delta v${delta.version} payload deviceId=${packet.sourceDeviceId} != metadata deviceId=${delta.sourceDeviceId}")
                    }
                    packets.add(packet)
                } catch (e: Exception) {
                    android.util.Log.w("SyncEngine", "Skipping unreadable delta from ${delta.sourceDeviceId}: ${e.message}")
                    continue
                }
                if (idx % 100 == 99) {
                    reportProgress("Decrypting ${idx + 1}/${remoteDeltas.size}…")
                    yield() // cooperate with cancellation during heavy decrypt
                }
            }
            syncLog("Decrypted ${packets.size} packets (skipped ${remoteDeltas.size - packets.size} own/unreadable)")

            // Step 4: Per-field CRDT merge — use snapshot state as base if applied
            val baseTxns: List<Transaction>
            val baseRe: List<RecurringExpense>
            val baseIs: List<IncomeSource>
            val baseSg: List<SavingsGoal>
            val baseAe: List<AmortizationEntry>
            val baseCat: List<Category>
            val basePl: List<PeriodLedgerEntry>
            val baseSettings: SharedSettings
            if (snapshotCatchUp && snapshotState != null) {
                // Overlay local dirty records onto snapshot using CRDT merge
                baseTxns = overlayRecords(snapshotState.transactions, transactions) { a, b -> CrdtMerge.mergeTransaction(a, b, deviceId) }
                baseRe = overlayRecords(snapshotState.recurringExpenses, recurringExpenses) { a, b -> CrdtMerge.mergeRecurringExpense(a, b, deviceId) }
                baseIs = overlayRecords(snapshotState.incomeSources, incomeSources) { a, b -> CrdtMerge.mergeIncomeSource(a, b, deviceId) }
                baseSg = overlayRecords(snapshotState.savingsGoals, savingsGoals) { a, b -> CrdtMerge.mergeSavingsGoal(a, b, deviceId) }
                baseAe = overlayRecords(snapshotState.amortizationEntries, amortizationEntries) { a, b -> CrdtMerge.mergeAmortizationEntry(a, b, deviceId) }
                baseCat = overlayRecords(snapshotState.categories, categories) { a, b -> CrdtMerge.mergeCategory(a, b, deviceId) }
                basePl = overlayRecords(snapshotState.periodLedgerEntries, periodLedgerEntries) { a, b -> CrdtMerge.mergePeriodLedgerEntry(a, b, deviceId) }
                baseSettings = CrdtMerge.mergeSharedSettings(snapshotState.sharedSettings, sharedSettings, deviceId)
            } else if (snapshotState != null) {
                // New device bootstrap — snapshot is the sole base
                baseTxns = snapshotState.transactions
                baseRe = snapshotState.recurringExpenses
                baseIs = snapshotState.incomeSources
                baseSg = snapshotState.savingsGoals
                baseAe = snapshotState.amortizationEntries
                baseCat = snapshotState.categories
                basePl = snapshotState.periodLedgerEntries
                baseSettings = snapshotState.sharedSettings
            } else {
                baseTxns = transactions
                baseRe = recurringExpenses
                baseIs = incomeSources
                baseSg = savingsGoals
                baseAe = amortizationEntries
                baseCat = categories
                basePl = periodLedgerEntries
                baseSettings = sharedSettings
            }

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
            // Seed remap from snapshot so new devices inherit dedup context
            if (snapshotState != null && snapshotState.catIdRemap.isNotEmpty()) {
                for ((k, v) in snapshotState.catIdRemap) {
                    catIdRemap.putIfAbsent(k, v)
                }
            }

            // Build index maps for O(1) lookups during merge (avoids O(n²) indexOfFirst)
            val txnIndex = buildIndexMap(mergedTxns)
            val reIndex = buildIndexMap(mergedRe)
            val isIndex = buildIndexMap(mergedIs)
            val sgIndex = buildIndexMap(mergedSg)
            val aeIndex = buildIndexMap(mergedAe)
            val plIndex = buildIndexMap(mergedPl)

            syncLog("Merging ${packets.size} delta packets")

            // Build category index maps for O(1) lookups
            val catIndexById = mutableMapOf<Int, Int>()
            val catIndexByTag = mutableMapOf<String, Int>()
            val catIndexByName = mutableMapOf<String, Int>()
            for (i in mergedCat.indices) {
                val cat = mergedCat[i]
                catIndexById[cat.id] = i
                if (cat.tag.isNotEmpty()) catIndexByTag[cat.tag] = i
                if (cat.name.isNotEmpty() && cat.tag.isNotEmpty()) catIndexByName[cat.name] = i
            }

            for ((packetIdx, packet) in packets.withIndex()) {
                if (packetIdx % 50 == 0 && packetIdx > 0) {
                    reportProgress("Merging $packetIdx/${packets.size}…")
                    yield() // cooperate with coroutine cancellation
                }
                // Merge with max field clock from the packet (NOT the wall-clock
                // timestamp, which would inflate our logical counter to ~1.7 billion).
                val maxFieldClock = packet.changes.maxOfOrNull { change ->
                    change.fields.values.maxOfOrNull { it.clock } ?: 0L
                } ?: 0L
                lamportClock.merge(maxFieldClock)
                for (change in packet.changes) {
                    when (change.type) {
                        "transaction" -> mergedTxns = mergeRecordIntoList(
                            mergedTxns, change, deviceId, txnIndex
                        ) { local, remote -> CrdtMerge.mergeTransaction(local, remote, deviceId) }
                        "recurring_expense" -> {
                            mergedRe = mergeRecordIntoList(
                                mergedRe, change, deviceId, reIndex
                            ) { local, remote -> CrdtMerge.mergeRecurringExpense(local, remote, deviceId) }
                            budgetRecalcNeeded = true
                        }
                        "income_source" -> {
                            mergedIs = mergeRecordIntoList(
                                mergedIs, change, deviceId, isIndex
                            ) { local, remote -> CrdtMerge.mergeIncomeSource(local, remote, deviceId) }
                            budgetRecalcNeeded = true
                        }
                        "savings_goal" -> mergedSg = mergeRecordIntoList(
                            mergedSg, change, deviceId, sgIndex
                        ) { local, remote -> CrdtMerge.mergeSavingsGoal(local, remote, deviceId) }
                        "amortization_entry" -> {
                            mergedAe = mergeRecordIntoList(
                                mergedAe, change, deviceId, aeIndex
                            ) { local, remote -> CrdtMerge.mergeAmortizationEntry(local, remote, deviceId) }
                            budgetRecalcNeeded = true
                        }
                        "category" -> {
                            val remoteCat = deserializeCategory(change)
                            val byId = catIndexById[change.id]
                            if (byId != null) {
                                mergedCat[byId] = CrdtMerge.mergeCategory(mergedCat[byId], remoteCat, deviceId)
                            } else {
                                // Deduplicate by tag: if a local category has the same tag, merge into it
                                val byTag = if (remoteCat.tag.isNotEmpty()) catIndexByTag[remoteCat.tag] else null
                                if (byTag != null) {
                                    catIdRemap[change.id] = mergedCat[byTag].id
                                    mergedCat[byTag] = CrdtMerge.mergeCategory(mergedCat[byTag], remoteCat, deviceId)
                                    catIndexById[change.id] = byTag // cache for future lookups
                                } else {
                                    // Fallback: match by name against known defaults (handles
                                    // remote categories arriving without a tag field)
                                    val byName = if (remoteCat.tag.isEmpty() && remoteCat.name.isNotEmpty())
                                        catIndexByName[remoteCat.name] else null
                                    if (byName != null) {
                                        catIdRemap[change.id] = mergedCat[byName].id
                                        mergedCat[byName] = CrdtMerge.mergeCategory(mergedCat[byName], remoteCat, deviceId)
                                        catIndexById[change.id] = byName
                                    } else {
                                        val newIdx = mergedCat.size
                                        mergedCat.add(remoteCat)
                                        catIndexById[remoteCat.id] = newIdx
                                        if (remoteCat.tag.isNotEmpty()) catIndexByTag[remoteCat.tag] = newIdx
                                        if (remoteCat.name.isNotEmpty()) catIndexByName[remoteCat.name] = newIdx
                                    }
                                }
                            }
                        }
                        "period_ledger" -> mergedPl = mergeRecordIntoList(
                            mergedPl, change, deviceId, plIndex
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
            // Dedup period ledger immediately after merge so downstream
            // consumers (BudgetCalculator, simAvailableCash) never see
            // duplicate entries for the same epoch day.
            mergedPl = PeriodLedgerRepository.dedup(mergedPl).toMutableList()

            val postMergeClockSum = mergedTxns.sumOf { IntegrityChecker.maxClock(it) }
            syncLog("Merge complete: ${mergedTxns.size} txns, ${mergedRe.size} RE, ${mergedIs.size} IS, ${mergedCat.size} cats (txnClockSum=$postMergeClockSum)")
            // One-time: dump per-transaction maxClock to identify exactly which differ
            if (!prefs.getBoolean("clock_dump_done", false)) {
                val clockDump = mergedTxns.sortedBy { it.id }.joinToString("\n") { t ->
                    "id=${t.id} max=${IntegrityChecker.maxClock(t)} src=${t.source_clock} amt=${t.amount_clock} dev=${t.deviceId.take(8)}"
                }
                try {
                    java.io.File(BackupManager.getSupportDir(), "clock_dump.txt")
                        .writeText("=== Clock dump ${java.time.LocalDateTime.now()} device=$deviceId ===\n$clockDump\n")
                } catch (_: Exception) {}
                prefs.edit().putBoolean("clock_dump_done", true).apply()
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

            // 5d: Resolve transitive remap chains (A→B→C becomes A→C, B→C)
            // before applying to transactions.  Prevents orphaned references
            // when multiple dedup passes create chained remaps.
            // Cycle detection: if A→B→A is found, resolve both to whichever
            // ID actually exists in merged categories (prefer higher name_clock).
            if (catIdRemap.isNotEmpty()) {
                val resolved = mutableMapOf<Int, Int>()
                for ((source, target) in catIdRemap) {
                    var final = target
                    val visited = mutableSetOf(source)
                    while (final in catIdRemap && final !in visited) {
                        visited.add(final)
                        final = catIdRemap[final]!!
                    }
                    if (final in visited) {
                        // Cycle detected — resolve to the member that exists in
                        // merged categories with the highest name_clock (most
                        // authoritative).  If neither exists, drop the remap.
                        val candidates = visited.mapNotNull { id ->
                            mergedCatById[id]?.let { id to it.name_clock }
                        }
                        final = if (candidates.isNotEmpty()) {
                            candidates.maxByOrNull { it.second }!!.first
                        } else {
                            source // no valid target — leave unmapped (identity → removed below)
                        }
                        syncLog("WARNING: category remap cycle detected involving IDs $visited, resolved to $final")
                    }
                    resolved[source] = final
                }
                // Remove identity mappings (A→A) — they waste cycles and
                // can arise from cycle resolution when no valid target exists.
                resolved.entries.removeAll { it.key == it.value }
                catIdRemap.clear()
                catIdRemap.putAll(resolved)
            }

            // Apply remap to all transactions
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

            // Step 5e: Advance lastPushedClock past the max clock in received
            // remote deltas BEFORE building push deltas.  The merged data (with
            // remote clocks via maxOf) gets saved to the repo by SyncWorker.
            // Without this pre-advancement, DeltaBuilder sees merged clocks as
            // "dirty" (field_clock > lastPushedClock) and re-pushes them back
            // to the sender (echo problem / push loop).
            if (packets.isNotEmpty()) {
                var maxReceivedClock = 0L
                for (packet in packets) {
                    for (change in packet.changes) {
                        for ((_, field) in change.fields) {
                            if (field.clock > maxReceivedClock) maxReceivedClock = field.clock
                        }
                    }
                }
                if (maxReceivedClock > lastPushedClock) {
                    lastPushedClock = maxReceivedClock
                    if (maxReceivedClock > lamportClock.value) {
                        lamportClock.merge(maxReceivedClock)
                    }
                }
            }

            // Step 6: Push records.  After a snapshot bootstrap, use the MERGED
            // data instead of the original local data.  The local data may contain
            // stale/wrong values (e.g., period ledger entries with applied=0.0 from
            // join stamping) that would poison the admin's data via CRDT.  Using
            // merged data ensures correct snapshot values are propagated.
            // DeltaBuilder's threshold (field_clock > lastPushedClock) still
            // prevents re-pushing unchanged data, and CRDT merge on the receiving
            // side is idempotent, making redundant pushes safe.
            val pushTxns = if (snapshotApplied || snapshotCatchUp) mergedTxns else transactions
            val pushRe = if (snapshotApplied || snapshotCatchUp) mergedRe else recurringExpenses
            val pushIs = if (snapshotApplied || snapshotCatchUp) mergedIs else incomeSources
            val pushSg = if (snapshotApplied || snapshotCatchUp) mergedSg else savingsGoals
            val pushAe = if (snapshotApplied || snapshotCatchUp) mergedAe else amortizationEntries
            val pushPl = if (snapshotApplied || snapshotCatchUp) mergedPl else periodLedgerEntries
            val pushCat = if (snapshotApplied || snapshotCatchUp) mergedCat else categories
            val pushSettings = if (snapshotApplied || snapshotCatchUp) mergedSettings else sharedSettings

            val localDeltas = mutableListOf<RecordDelta>()
            val pushClock = lastPushedClock
            val pendingReceiptUploads = ReceiptManager.loadPendingUploads(context)
            for (txn in pushTxns) {
                DeltaBuilder.buildTransactionDelta(txn, pushClock, pendingReceiptUploads)?.let { localDeltas.add(it) }
            }
            for (re in pushRe) {
                DeltaBuilder.buildRecurringExpenseDelta(re, pushClock)?.let { localDeltas.add(it) }
            }
            for (src in pushIs) {
                DeltaBuilder.buildIncomeSourceDelta(src, pushClock)?.let { localDeltas.add(it) }
            }
            for (goal in pushSg) {
                DeltaBuilder.buildSavingsGoalDelta(goal, pushClock)?.let { localDeltas.add(it) }
            }
            for (entry in pushAe) {
                DeltaBuilder.buildAmortizationEntryDelta(entry, pushClock)?.let { localDeltas.add(it) }
            }
            // Push deduped period ledger — avoids pushing multiple deltas for the
            // same epoch-day ID which would create duplicates on receiving devices.
            for (entry in PeriodLedgerRepository.dedup(pushPl)) {
                DeltaBuilder.buildPeriodLedgerDelta(entry, pushClock)?.let { localDeltas.add(it) }
            }
            for (cat in pushCat) {
                DeltaBuilder.buildCategoryDelta(cat, pushClock)?.let { localDeltas.add(it) }
            }
            // SharedSettings is always pushed (shared, not per-device)
            DeltaBuilder.buildSharedSettingsDelta(pushSettings, pushClock)?.let { localDeltas.add(it) }

            if (localDeltas.size > 10) {
                // Diagnostic: log types and sample max clocks to debug push loops
                val byType = localDeltas.groupBy { it.type }
                val typeSummary = byType.entries.joinToString(", ") { "${it.key}=${it.value.size}" }
                val sampleClocks = localDeltas.take(3).joinToString(", ") { d ->
                    "${d.type}#${d.id}:maxClk=${d.fields.values.maxOfOrNull { it.clock } ?: 0}"
                }
                syncLog("Built ${localDeltas.size} local deltas to push ($typeSummary) pushClock=$pushClock samples=[$sampleClocks]")
            } else {
                syncLog("Built ${localDeltas.size} local deltas to push")
            }
            var deltasPushed = 0
            if (localDeltas.isNotEmpty()) {
                reportProgress("Sending local changes…")

                // Chunk large deltas to stay under Firestore's 1MB document
                // limit.  200 records ≈ 200-400KB encoded, well within limits.
                // If an encoded chunk still exceeds 900KB (e.g. records with
                // large photo URIs), pushChunk splits it in half and retries.
                var isFirstPush = true
                suspend fun pushChunk(records: List<RecordDelta>, chunkLabel: String) {
                    val packet = DeltaPacket(
                        sourceDeviceId = deviceId,
                        timestamp = Instant.now(),
                        changes = records
                    )
                    val serialized = DeltaSerializer.serialize(packet).toString().toByteArray()
                    val encrypted = CryptoHelper.encryptWithKey(serialized, encryptionKey)
                    val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)

                    if (encoded.length > 900_000 && records.size > 1) {
                        // Approaching 1MB limit — split in half and retry
                        val mid = records.size / 2
                        syncLog("Chunk $chunkLabel too large (${encoded.length} chars, ${records.size} records) — splitting")
                        pushChunk(records.subList(0, mid), "${chunkLabel}a")
                        pushChunk(records.subList(mid, records.size), "${chunkLabel}b")
                        return
                    }

                    val pushTimeout = if (isFirstPush) 30_000L else 60_000L
                    syncLog("Pushing delta chunk $chunkLabel (${encoded.length} chars, ${records.size} records)")
                    FirestoreService.pushDeltaAtomically(groupId, deviceId, encoded, pushTimeout)
                    isFirstPush = false
                }

                val chunks = localDeltas.chunked(200)
                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    if (chunks.size > 1) {
                        reportProgress("Sending batch ${chunkIdx + 1}/${chunks.size}…")
                    }
                    pushChunk(chunk, "${chunkIdx + 1}/${chunks.size}")
                }
                deltasPushed = localDeltas.size
                syncLog("Push complete ($deltasPushed records in ${chunks.size} chunk(s))")

                // Advance lastPushedClock AFTER successful push.  This ensures
                // that if the push fails (e.g. network error), the delta will
                // be retried on the next sync cycle.
                val maxDeltaClock = localDeltas
                    .maxOfOrNull { delta ->
                        delta.fields.values.maxOfOrNull { it.clock } ?: 0L
                    } ?: lastPushedClock
                lastPushedClock = maxDeltaClock
                // Ensure lamport clock stays ahead of lastPushedClock so that
                // future tick() calls produce values > lastPushedClock
                if (maxDeltaClock > lamportClock.value) {
                    lamportClock.merge(maxDeltaClock)
                }
            }

            // (lastPushedClock already advanced past received remote clocks
            // in Step 5e above, before DeltaBuilder ran.)

            // Step 7: Update metadata (with optional integrity fingerprint)
            val newSyncVersion = if (remoteDeltas.isNotEmpty()) {
                remoteDeltas.maxOf { it.version }
            } else {
                lastSyncVersion
            }
            lastSyncVersion = newSyncVersion
            val now = System.currentTimeMillis()
            // One-time cooldown reset after rescue gating fix (v3).
            // Previous continuous rescue caused clock divergences that put
            // repair into permanent cooldown. Reset so repair can try again.
            // Clear any saved repair-wait state on first run of this build
            // so the new fingerprint logic (excluding clock=0 fields) gets
            // a fresh comparison immediately.
            if (!prefs.getBoolean("repair_structural_fix_v8", false)) {
                prefs.edit()
                    .putString("lastRemoteFpSignature", "")
                    .putBoolean("repair_structural_fix_v8", true)
                    .apply()
                syncLog("Structural integrity fix applied (v8: clock=0 excluded from fingerprint)")
            }
            // Run integrity check every cycle UNLESS:
            // 1. We just pushed normal deltas (other device hasn't merged)
            // 2. We're waiting for remote to process our last repair
            //    (tracked by lastRemoteFpSignature — cleared when their fp changes)
            var lastRemoteFpSig = prefs.getString("lastRemoteFpSignature", "") ?: ""
            // If waiting for remote to process our repair, check if their
            // fingerprint has changed (meaning they synced).
            if (lastRemoteFpSig.isNotEmpty() && deltasPushed == 0) {
                try {
                    val devices = FirestoreService.getDevices(groupId)
                    for (device in devices) {
                        if (device.deviceId == deviceId || device.fingerprintData == null) continue
                        val currentSig = "${device.deviceId}:${device.fingerprintSyncVersion}:${device.fingerprintData.hashCode()}"
                        if (currentSig != lastRemoteFpSig) {
                            syncLog("Remote fingerprint changed — re-enabling integrity check")
                            lastRemoteFpSig = ""
                            prefs.edit().putString("lastRemoteFpSignature", "").apply()
                        }
                        break
                    }
                } catch (_: Exception) {}
            }
            val waitingForRemoteRepair = lastRemoteFpSig.isNotEmpty()
            val runIntegrityCheck = deltasPushed == 0 && !waitingForRemoteRepair
            // Always publish fingerprint so other devices see current state
            // after merging repair deltas.  The 30min gate only controls
            // whether we COMPARE fingerprints, not whether we publish ours.
            // For category fingerprint: dedup by tag (keep one per tag,
            // highest name_clock wins) and use tag hash as stable ID.
            // This is independent of catIdRemap — both devices produce
            // the same fingerprint regardless of which random IDs they
            // assigned or which remap direction they have.
            val fpCategories = run {
                val tagged = mergedCat.filter { it.tag.isNotEmpty() && !it.deleted }
                    .groupBy { it.tag }
                    .map { (tag, group) ->
                        val best = group.maxByOrNull { it.name_clock } ?: group.first()
                        best.copy(id = tag.hashCode() and 0x7FFFFFFF)
                    }
                val untagged = mergedCat.filter { it.tag.isEmpty() && !it.deleted }
                tagged + untagged
            }
            val fpJson = try {
                val fp = IntegrityChecker.computeFingerprint(
                    deviceId, newSyncVersion,
                    mergedTxns, mergedRe, mergedIs, mergedSg,
                    mergedAe, fpCategories, mergedPl, mergedSettings
                )
                IntegrityChecker.toJson(fp).toString()
            } catch (_: Exception) { null }
            // Read upload speed from receipt sync prefs for reporting
            val receiptPrefs = context.getSharedPreferences("receipt_sync_prefs", android.content.Context.MODE_PRIVATE)
            val uploadSpeedBps = receiptPrefs.getLong("lastUploadSpeedBps", 0L)
            val uploadSpeedMeasuredAt = receiptPrefs.getLong("lastSpeedMeasuredAt", 0L)
            FirestoreService.updateDeviceMetadata(groupId, deviceId, newSyncVersion, fpJson,
                appSyncVersion = APP_SYNC_VERSION, minSyncVersion = MIN_SYNC_VERSION,
                photoCapable = isPaidUser, uploadSpeedBps = uploadSpeedBps,
                uploadSpeedMeasuredAt = uploadSpeedMeasuredAt)

            // Save merged settings if changed
            if (settingsChanged) {
                SharedSettingsRepository.save(context, mergedSettings)
            }

            // Step 8: Snapshot version bookkeeping & decentralized snapshot writing
            // Seed lastSnapshotVersion on first run to prevent all devices racing
            if (lastSnapshotVersion == 0L && newSyncVersion > 0L) {
                lastSnapshotVersion = newSyncVersion
            }
            // During catch-up, seed from the snapshot we loaded
            if (snapshotCatchUp) {
                lastSnapshotVersion = maxOf(lastSnapshotVersion, catchUpSnapshotVersion)
            }
            // Write a snapshot if enough deltas have accumulated since last snapshot
            if (newSyncVersion - lastSnapshotVersion >= SNAPSHOT_WRITE_THRESHOLD) {
                try {
                    writeSnapshot(
                        mergedTxns, mergedRe, mergedIs, mergedSg,
                        mergedAe, mergedCat, mergedSettings, mergedPl,
                        catIdRemap
                    )
                    lastSnapshotVersion = newSyncVersion
                    // Prune old deltas that are fully covered by the snapshot.
                    // Keep a buffer of SNAPSHOT_CATCHUP_THRESHOLD deltas so devices
                    // slightly behind can still fetch deltas without needing the
                    // snapshot.  Devices further behind use snapshot catch-up.
                    val pruneBelow = newSyncVersion - SNAPSHOT_CATCHUP_THRESHOLD
                    if (pruneBelow > 0) {
                        try {
                            val pruned = FirestoreService.pruneDeltas(groupId, pruneBelow)
                            if (pruned > 0) syncLog("Pruned $pruned old deltas (version < $pruneBelow)")
                        } catch (e: Exception) {
                            syncLog("Delta pruning failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SyncEngine", "Failed to write snapshot", e)
                }
            }

            // Step 9: Record successful sync time & update group activity for TTL
            prefs.edit().putLong("lastSuccessfulSync", System.currentTimeMillis()).apply()
            FirestoreService.updateGroupActivity(groupId)

            // Step 9b: Check if admin requested debug files. If so, upload
            // fresh logs immediately so admin gets current data within ~60s.
            // Also check if FCM push triggered a debug request (device woken up).
            // Debug-only: not included in release builds.
            if (com.syncbudget.app.BuildConfig.DEBUG) {
                try {
                    val requestedAt = FirestoreService.getDebugRequestTime(groupId)
                    val lastUploaded = prefs.getLong("lastDebugUploadTime", 0L)
                    val fcmPrefs = context.getSharedPreferences("fcm_prefs", android.content.Context.MODE_PRIVATE)
                    val fcmRequested = fcmPrefs.getBoolean("fcm_debug_requested", false)
                    if (requestedAt > lastUploaded || fcmRequested) {
                        val deviceName = context.getSharedPreferences("sync_device", Context.MODE_PRIVATE)
                            .getString("deviceName", null) ?: android.os.Build.MODEL
                        val logText = try { syncLogFile.readText() } catch (_: Exception) { "" }
                        val diagFile = java.io.File(BackupManager.getSupportDir(), "sync_diag.txt")
                        val diagText = try { if (diagFile.exists()) diagFile.readText() else "" } catch (_: Exception) { "" }
                        if (logText.isNotEmpty() || diagText.isNotEmpty()) {
                            FirestoreService.uploadDebugFiles(groupId, deviceId, deviceName, logText, diagText, encryptionKey)
                            prefs.edit().putLong("lastDebugUploadTime", now).apply()
                            syncLog("Debug files uploaded (admin requested, fcm=$fcmRequested)")
                        }
                        // Clear FCM flag after upload
                        if (fcmRequested) {
                            fcmPrefs.edit().putBoolean("fcm_debug_requested", false).apply()
                        }
                    }
                } catch (e: Exception) {
                    syncLog("Debug file upload check failed (non-fatal): ${e.message}")
                }
            }

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

            // Step 10.5: Integrity check — compare fingerprints every cycle
            // Skip only when we just pushed deltas (other device hasn't merged).
            // After a repair, wait until the remote's fingerprint changes (proves
            // they synced) before re-checking — replaces the old cooldown timer.
            var didRepair = false
            if (runIntegrityCheck && fpJson != null) {
                try {
                    val localFp = IntegrityChecker.fromJson(JSONObject(fpJson))
                    val devices = FirestoreService.getDevices(groupId)
                    val allReports = mutableListOf<IntegrityChecker.DivergenceReport>()
                    var anyOk = false
                    for (device in devices) {
                        if (device.deviceId == deviceId) continue
                        val name = device.deviceName.ifEmpty { device.deviceId.take(8) }
                        if (device.fingerprintData == null) continue
                        // Build a signature of this device's remote fingerprint
                        // to detect when they've synced since our last repair.
                        val remoteFpSig = "${device.deviceId}:${device.fingerprintSyncVersion}:${device.fingerprintData.hashCode()}"
                        // Allow comparison if versions are close (within 10).
                        // Strict equality almost never holds when both devices
                        // are actively syncing.  The fingerprint reflects post-merge
                        // state regardless of exact version number.
                        val versionGap = kotlin.math.abs(newSyncVersion - device.fingerprintSyncVersion)
                        if (versionGap > 10) {
                            syncLog("Integrity skip $name: syncVer too far apart " +
                                "(local=$newSyncVersion, remote=${device.fingerprintSyncVersion}, gap=$versionGap)")
                            continue
                        }
                        val remoteFp = IntegrityChecker.fromJson(JSONObject(device.fingerprintData))
                        val report = IntegrityChecker.compare(localFp, remoteFp)
                        if (report.diverged) {
                            syncLog("INTEGRITY DIVERGENCE with $name:")
                            for (detail in report.details) syncLog("  · $detail")
                            allReports.add(report)
                        } else {
                            syncLog("Integrity OK with $name")
                            anyOk = true
                            // Clear saved signature — divergence resolved
                            prefs.edit().putString("lastRemoteFpSignature", "").apply()
                        }
                    }

                    // Execute surgical repair if any divergence was found
                    val merged = IntegrityChecker.mergeRepairs(allReports)
                    if (merged.isNotEmpty()) {
                            val repairDeltas = mutableListOf<RecordDelta>()
                            for (action in merged) {
                                when (action.collection) {
                                    "transactions" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedTxns) { it.id }
                                        for (txn in mergedTxns) {
                                            if (txn.id in ids) {
                                                DeltaBuilder.buildTransactionDelta(txn, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR transactions: ${ids.size} records (${action.reason})")
                                    }
                                    "recurringExpenses" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedRe) { it.id }
                                        for (re in mergedRe) {
                                            if (re.id in ids) {
                                                DeltaBuilder.buildRecurringExpenseDelta(re, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR recurringExpenses: ${ids.size} records (${action.reason})")
                                    }
                                    "incomeSources" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedIs) { it.id }
                                        for (src in mergedIs) {
                                            if (src.id in ids) {
                                                DeltaBuilder.buildIncomeSourceDelta(src, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR incomeSources: ${ids.size} records (${action.reason})")
                                    }
                                    "savingsGoals" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedSg) { it.id }
                                        for (g in mergedSg) {
                                            if (g.id in ids) {
                                                DeltaBuilder.buildSavingsGoalDelta(g, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR savingsGoals: ${ids.size} records (${action.reason})")
                                    }
                                    "amortizationEntries" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedAe) { it.id }
                                        for (e in mergedAe) {
                                            if (e.id in ids) {
                                                DeltaBuilder.buildAmortizationEntryDelta(e, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR amortizationEntries: ${ids.size} records (${action.reason})")
                                    }
                                    "categories" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedCat) { it.id }
                                        for (cat in mergedCat) {
                                            if (cat.id in ids) {
                                                DeltaBuilder.buildCategoryDelta(cat, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR categories: ${ids.size} records (${action.reason})")
                                    }
                                    "periodLedger" -> {
                                        val ids = IntegrityChecker.resolveIds(action, mergedPl) { it.id }
                                        for (pl in mergedPl) {
                                            if (pl.id in ids) {
                                                DeltaBuilder.buildPeriodLedgerDelta(pl, 0L)?.let { repairDeltas.add(it) }
                                            }
                                        }
                                        syncLog("  REPAIR periodLedger: ${ids.size} records (${action.reason})")
                                    }
                                }
                            }
                            if (repairDeltas.isNotEmpty()) {
                                // No cap — chunk into 200-record batches
                                syncLog("  REPAIR pushing ${repairDeltas.size} records in ${(repairDeltas.size + 199) / 200} chunk(s)")
                                val repairChunks = repairDeltas.chunked(200)
                                for ((ci, chunk) in repairChunks.withIndex()) {
                                    val packet = DeltaPacket(
                                        sourceDeviceId = deviceId,
                                        timestamp = java.time.Instant.now(),
                                        changes = chunk
                                    )
                                    val serialized = DeltaSerializer.serialize(packet).toString().toByteArray()
                                    val encrypted = CryptoHelper.encryptWithKey(serialized, encryptionKey)
                                    val encoded = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
                                    syncLog("  REPAIR chunk ${ci + 1}/${repairChunks.size} (${encoded.length} chars, ${chunk.size} records)")
                                    FirestoreService.pushDeltaAtomically(groupId, deviceId, encoded)
                                }
                                syncLog("  REPAIR push complete")
                                deltasPushed += repairDeltas.size
                                didRepair = true

                                // Save remote fingerprint signature so we don't
                                // re-check until they've actually synced and updated.
                                for (device in devices) {
                                    if (device.deviceId != deviceId && device.fingerprintData != null) {
                                        val sig = "${device.deviceId}:${device.fingerprintSyncVersion}:${device.fingerprintData.hashCode()}"
                                        prefs.edit().putString("lastRemoteFpSignature", sig).apply()
                                        break
                                    }
                                }

                                // Advance lastPushedClock after repair
                                val maxRepairClock = repairDeltas
                                    .maxOfOrNull { delta ->
                                        delta.fields.values.maxOfOrNull { it.clock } ?: 0L
                                    } ?: lastPushedClock
                                if (maxRepairClock > lastPushedClock) {
                                    lastPushedClock = maxRepairClock
                                }
                                if (maxRepairClock > lamportClock.value) {
                                    lamportClock.merge(maxRepairClock)
                                }
                            }
                    } else {
                        // No divergence — clear saved fingerprint signature
                        if (lastRemoteFpSig.isNotEmpty()) {
                            syncLog("  Divergence resolved")
                            prefs.edit().putString("lastRemoteFpSignature", "").apply()
                        }
                    }
                } catch (e: Exception) {
                    syncLog("Integrity check failed: ${e.message}")
                }
            }

            // Dedup period ledger: mergeRecordIntoList only merges with the
            // first matching entry, leaving duplicates with the same epoch-day ID.
            // This ensures the highest-clock entry wins before saving.
            mergedPl = PeriodLedgerRepository.dedup(mergedPl).toMutableList()

            // ── Receipt merge-time cleanup ──────────────────────────
            // If a remote merge set any receiptIdN from non-null to null,
            // delete the corresponding local file + thumbnail.
            if (packets.isNotEmpty()) {
                for (txn in mergedTxns) {
                    val prev = transactions.find { it.id == txn.id } ?: continue
                    val removals = listOf(
                        prev.receiptId1 to txn.receiptId1,
                        prev.receiptId2 to txn.receiptId2,
                        prev.receiptId3 to txn.receiptId3,
                        prev.receiptId4 to txn.receiptId4,
                        prev.receiptId5 to txn.receiptId5
                    )
                    for ((oldId, newId) in removals) {
                        if (oldId != null && newId == null) {
                            ReceiptManager.deleteLocalReceipt(context, oldId)
                            syncLog("Receipt $oldId: removed (slot cleared by remote merge)")
                        }
                    }
                }
            }

            // ── Receipt photo sync ──────────────────────────────────
            // Only run for paid users in a family group
            if (groupId.isNotEmpty() && isPaidUser) {
                try {
                    val receiptSync = ReceiptSyncManager(context, groupId, deviceId, encryptionKey) { msg -> syncLog(msg) }
                    val devices = FirestoreService.getDevices(groupId)
                    val updatedTxns = receiptSync.syncReceipts(mergedTxns, devices)
                    if (updatedTxns !== mergedTxns) {
                        mergedTxns = updatedTxns.toMutableList()
                    }
                } catch (e: Exception) {
                    syncLog("Receipt sync error (non-fatal): ${e.message}")
                }
            }

            // ── Receipt orphan cleanup ──────────────────────────────
            // Periodically clean local files not referenced by any transaction
            val allReceiptIds = ReceiptManager.collectAllReceiptIds(mergedTxns)
            ReceiptManager.cleanOrphans(context, allReceiptIds)

            val hasChanges = packets.isNotEmpty() || snapshotApplied || snapshotCatchUp
            // If remap fixed orphaned category IDs, include transactions even
            // when no new deltas arrived — otherwise the fix is never persisted.
            val txnsChanged = hasChanges || remapChangedTxns
            syncLog("=== Sync complete: received=${packets.sumOf { it.changes.size }}, pushed=$deltasPushed, snapshot=${snapshotApplied} ===")
            progressCallback = null
            return SyncResult(
                success = true,
                deltasReceived = packets.sumOf { it.changes.size },
                deltasPushed = deltasPushed,
                budgetRecalcNeeded = budgetRecalcNeeded || snapshotApplied || snapshotCatchUp,
                mergedTransactions = if (txnsChanged) mergedTxns else null,
                mergedRecurringExpenses = if (hasChanges) mergedRe else null,
                mergedIncomeSources = if (hasChanges) mergedIs else null,
                mergedSavingsGoals = if (hasChanges) mergedSg else null,
                mergedAmortizationEntries = if (hasChanges) mergedAe else null,
                mergedCategories = if (hasChanges) mergedCat else null,
                mergedPeriodLedgerEntries = if (hasChanges) mergedPl else null,
                mergedSharedSettings = if (settingsChanged || snapshotApplied || snapshotCatchUp) mergedSettings else null,
                pendingAdminClaim = adminClaim,
                catIdRemap = catIdRemap,
                repairAttempted = didRepair
            )
        } catch (e: Exception) {
            // Don't infer removal from Firestore exceptions — only explicit
            // flags (checked in Step 1) trigger auto-leave.  PERMISSION_DENIED
            // and NOT_FOUND are treated as transient/config errors.
            val errorCode = when {
                e is javax.crypto.AEADBadTagException -> "encryption_error"
                e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    // Could be: (a) old app without auth, or (b) transient auth token expiry.
                    // Try re-authenticating — if we CAN auth but Firestore still denies,
                    // it's a rules mismatch (old app). If auth fails, it's transient.
                    val hasAuth = try {
                        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        if (user == null) {
                            com.google.firebase.auth.FirebaseAuth.getInstance()
                                .signInAnonymously().await()
                        }
                        true
                    } catch (_: Exception) { false }
                    if (hasAuth) "update_required" else e.message
                }
                else -> e.message
            }
            syncLog("=== Sync FAILED: $errorCode — ${e.javaClass.simpleName}: ${e.message} ===")
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
                "incomeMode" -> s = s.copy(incomeMode = fd.value as? String ?: s.incomeMode, incomeMode_clock = fd.clock)
                "deviceRoster" -> s = s.copy(deviceRoster = fd.value as? String ?: s.deviceRoster, deviceRoster_clock = fd.clock)
                "receiptPruneAgeDays" -> s = s.copy(receiptPruneAgeDays = (fd.value as? Number)?.toInt(), receiptPruneAgeDays_clock = fd.clock)
            }
        }
        return s
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T : Any> mergeRecordIntoList(
        list: MutableList<T>,
        change: RecordDelta,
        localDeviceId: String,
        indexById: MutableMap<Int, Int>? = null,
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

        // Use index map for O(1) lookup when available, fall back to linear scan
        val existingIndex = indexById?.get(change.id) ?: list.indexOfFirst { getId(it) == change.id }
        if (existingIndex >= 0) {
            list[existingIndex] = mergeFn(list[existingIndex], remote)
        } else {
            // Accept skeleton records instead of dropping them — future
            // deltas will merge into the existing record and complete it.
            // Silently dropping violated CRDT delivery guarantees.
            if (isSkeletonRecord(remote)) {
                syncLog("WARNING: Accepted skeleton ${change.type} id=${change.id} " +
                    "(missing critical field clocks — awaiting completion)")
            }
            val newIdx = list.size
            list.add(remote)
            indexById?.put(change.id, newIdx)
        }
        return list
    }

    private fun <T : Any> buildIndexMap(list: List<T>): MutableMap<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        for (i in list.indices) {
            map[getId(list[i])] = i
        }
        return map
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
     *  critical fields.  Checks both clocks (field not included in delta)
     *  AND values (field included but deserialized to default/empty).
     *  Such records would appear as $0 amounts or empty names in the UI. */
    private fun isSkeletonRecord(record: Any): Boolean = when (record) {
        is Transaction -> record.amount_clock == 0L || record.source_clock == 0L
            || record.source.isEmpty() || (record.amount == 0.0 && record.amount_clock > 0L)
        is RecurringExpense -> record.amount_clock == 0L || record.source_clock == 0L
            || record.source.isEmpty()
        is IncomeSource -> record.amount_clock == 0L || record.source_clock == 0L
            || record.source.isEmpty()
        is AmortizationEntry -> record.amount_clock == 0L || record.source_clock == 0L
            || record.source.isEmpty()
        is SavingsGoal -> record.name_clock == 0L || record.name.isEmpty()
        is Category -> record.name_clock == 0L || record.name.isEmpty()
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

        val type = try { TransactionType.valueOf(f["type"]?.value as? String ?: "EXPENSE") } catch (e: Exception) {
            android.util.Log.w("SyncEngine", "Delta txn id=${change.id}: bad type '${f["type"]?.value}', defaulting to EXPENSE")
            TransactionType.EXPENSE
        }
        val date = try { LocalDate.parse(f["date"]?.value as? String) } catch (e: Exception) {
            android.util.Log.w("SyncEngine", "Delta txn id=${change.id}: bad date '${f["date"]?.value}', defaulting to today")
            LocalDate.now()
        }
        return Transaction(
            id = change.id,
            type = type,
            date = date,
            source = f["source"]?.value as? String ?: "",
            description = f["description"]?.value as? String ?: "",
            categoryAmounts = categoryAmounts,
            amount = (f["amount"]?.value as? Number)?.toDouble() ?: 0.0,
            isUserCategorized = f["isUserCategorized"]?.value as? Boolean ?: true,
            excludeFromBudget = f["excludeFromBudget"]?.value as? Boolean ?: false,
            isBudgetIncome = f["isBudgetIncome"]?.value as? Boolean ?: false,
            linkedRecurringExpenseId = (f["linkedRecurringExpenseId"]?.value as? Number)?.toInt(),
            linkedAmortizationEntryId = (f["linkedAmortizationEntryId"]?.value as? Number)?.toInt(),
            linkedIncomeSourceId = (f["linkedIncomeSourceId"]?.value as? Number)?.toInt(),
            amortizationAppliedAmount = (f["amortizationAppliedAmount"]?.value as? Number)?.toDouble() ?: 0.0,
            linkedRecurringExpenseAmount = (f["linkedRecurringExpenseAmount"]?.value as? Number)?.toDouble() ?: 0.0,
            linkedIncomeSourceAmount = (f["linkedIncomeSourceAmount"]?.value as? Number)?.toDouble() ?: 0.0,
            linkedSavingsGoalId = (f["linkedSavingsGoalId"]?.value as? Number)?.toInt(),
            linkedSavingsGoalAmount = (f["linkedSavingsGoalAmount"]?.value as? Number)?.toDouble() ?: 0.0,
            receiptId1 = f["receiptId1"]?.value as? String,
            receiptId2 = f["receiptId2"]?.value as? String,
            receiptId3 = f["receiptId3"]?.value as? String,
            receiptId4 = f["receiptId4"]?.value as? String,
            receiptId5 = f["receiptId5"]?.value as? String,
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            source_clock = f["source"]?.clock ?: 0L,
            description_clock = f["description"]?.clock ?: 0L,
            amount_clock = f["amount"]?.clock ?: 0L,
            date_clock = f["date"]?.clock ?: 0L,
            type_clock = f["type"]?.clock ?: 0L,
            categoryAmounts_clock = f["categoryAmounts"]?.clock ?: 0L,
            isUserCategorized_clock = f["isUserCategorized"]?.clock ?: 0L,
            excludeFromBudget_clock = f["excludeFromBudget"]?.clock ?: 0L,
            isBudgetIncome_clock = f["isBudgetIncome"]?.clock ?: 0L,
            linkedRecurringExpenseId_clock = f["linkedRecurringExpenseId"]?.clock ?: 0L,
            linkedAmortizationEntryId_clock = f["linkedAmortizationEntryId"]?.clock ?: 0L,
            linkedIncomeSourceId_clock = f["linkedIncomeSourceId"]?.clock ?: 0L,
            amortizationAppliedAmount_clock = f["amortizationAppliedAmount"]?.clock ?: 0L,
            linkedRecurringExpenseAmount_clock = f["linkedRecurringExpenseAmount"]?.clock ?: 0L,
            linkedIncomeSourceAmount_clock = f["linkedIncomeSourceAmount"]?.clock ?: 0L,
            linkedSavingsGoalId_clock = f["linkedSavingsGoalId"]?.clock ?: 0L,
            linkedSavingsGoalAmount_clock = f["linkedSavingsGoalAmount"]?.clock ?: 0L,
            receiptId1_clock = f["receiptId1"]?.clock ?: 0L,
            receiptId2_clock = f["receiptId2"]?.clock ?: 0L,
            receiptId3_clock = f["receiptId3"]?.clock ?: 0L,
            receiptId4_clock = f["receiptId4"]?.clock ?: 0L,
            receiptId5_clock = f["receiptId5"]?.clock ?: 0L,
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
            deviceId_clock = f["deviceId"]?.clock ?: 0L,
            setAsideSoFar = (f["setAsideSoFar"]?.value as? Number)?.toDouble() ?: 0.0,
            isAccelerated = f["isAccelerated"]?.value as? Boolean ?: false,
            setAsideSoFar_clock = f["setAsideSoFar"]?.clock ?: 0L,
            isAccelerated_clock = f["isAccelerated"]?.clock ?: 0L
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
            charted = f["charted"]?.value as? Boolean ?: true,
            deviceId = f["deviceId"]?.value as? String ?: change.deviceId,
            deleted = f["deleted"]?.value as? Boolean ?: false,
            name_clock = f["name"]?.clock ?: 0L,
            iconName_clock = f["iconName"]?.clock ?: 0L,
            tag_clock = f["tag"]?.clock ?: 0L,
            charted_clock = f["charted"]?.clock ?: 0L,
            deleted_clock = f["deleted"]?.clock ?: 0L,
            deviceId_clock = f["deviceId"]?.clock ?: 0L
        )
    }
}

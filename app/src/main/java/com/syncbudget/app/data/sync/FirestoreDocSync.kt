package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.syncbudget.app.data.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime

/**
 * Firestore-native sync coordinator.
 *
 * Replaces the old CRDT-based SyncEngine. Each record is a separate Firestore
 * document with an encrypted blob. Firestore handles sync, offline queue, retry,
 * and conflict resolution (last-write-wins via server timestamp).
 *
 * Usage:
 *   1. Create instance with group credentials
 *   2. Set onDataChanged callback
 *   3. Call startListeners() — real-time sync begins
 *   4. Call pushRecord() on local edits
 *   5. Call stopListeners() on leave/dispose
 */
class FirestoreDocSync(
    private val context: Context,
    private val groupId: String,
    private val deviceId: String,
    private val encryptionKey: ByteArray
) {
    companion object {
        private const val TAG = "FirestoreDocSync"
        /** How long to suppress echo callbacks after a local write (ms). */
        private const val ECHO_SUPPRESS_MS = 5_000L
        private const val LOG_FILE = "native_sync_log.txt"
        private const val MAX_LOG_SIZE = 512_000L // 512KB
    }

    /** File-based sync log for debugging (readable via dump button). */
    private fun syncLog(msg: String) {
        Log.i(TAG, msg)
        try {
            val dir = BackupManager.getSupportDir()
            val file = java.io.File(dir, LOG_FILE)
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                val prev = java.io.File(dir, "native_sync_log_prev.txt")
                file.renameTo(prev)
            }
            java.io.File(dir, LOG_FILE).appendText("[${LocalDateTime.now()}] $msg\n")
        } catch (_: Exception) {}
    }

    // Active snapshot listeners — keyed by collection name, detached on stop
    private val listeners = ConcurrentHashMap<String, ListenerRegistration>()

    // Echo prevention: track recently-pushed doc keys ("collection:docId")
    // Value = timestamp when pushed
    private val recentPushes = ConcurrentHashMap<String, Long>()

    // Enc hash skip: track the last-seen enc blob per document.
    // If unchanged since last callback, skip expensive decryption.
    // Key = "collection:docId", Value = composite hash of enc_* fields.
    // Persisted to disk so cold starts can skip decryption of unchanged docs.
    private val lastSeenEnc = ConcurrentHashMap<String, String>()
    private val encCacheFile = java.io.File(context.filesDir, "enc_hash_cache.json")

    // Last known record state per doc, for diff-based field updates.
    // Populated from listener (incoming) and after pushes (outgoing).
    private val lastKnownState = ConcurrentHashMap<String, Any>()

    // Records with unpushed local edits, for conflict detection.
    // Key = "collection:docId", Value = epoch millis of local edit.
    // Persisted to SharedPreferences so conflicts survive app restart/reboot.
    private val localPendingEdits = ConcurrentHashMap<String, Long>()
    private val pendingEditsPrefs = context.getSharedPreferences("pending_edits", Context.MODE_PRIVATE)

    init {
        // Restore persisted enc hash cache (survives process death)
        try {
            if (encCacheFile.exists()) {
                val json = org.json.JSONObject(encCacheFile.readText())
                for (key in json.keys()) {
                    lastSeenEnc[key] = json.getString(key)
                }
                syncLog("Restored enc hash cache: ${lastSeenEnc.size} entries from disk")
            }
        } catch (e: Exception) {
            syncLog("Failed to restore enc hash cache: ${e.message}")
        }
        // Restore persisted pending edits
        try {
            val json = pendingEditsPrefs.getString("edits", null)
            if (json != null) {
                val obj = org.json.JSONObject(json)
                for (key in obj.keys()) {
                    localPendingEdits[key] = obj.getLong(key)
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistEncCache() {
        synchronized(lastSeenEnc) {
            try {
                val json = org.json.JSONObject()
                for ((key, value) in lastSeenEnc) {
                    json.put(key, value)
                }
                encCacheFile.writeText(json.toString())
            } catch (_: Exception) {}
        }
    }

    private fun persistPendingEdits() {
        try {
            val obj = org.json.JSONObject()
            for ((key, value) in localPendingEdits) {
                obj.put(key, value)
            }
            pendingEditsPrefs.edit().putString("edits", obj.toString()).apply()
        } catch (_: Exception) {}
    }

    // Background scope for heavy deserialization (decrypt + JSON parse)
    // so the main thread stays responsive during large syncs.
    private val deserializeScope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    /** Callback fired with a batch of incoming remote changes per collection.
     *  Always invoked on the MAIN thread. */
    var onBatchChanged: ((List<DataChangeEvent>) -> Unit)? = null

    /** Whether listeners are currently active. */
    @Volatile var isListening: Boolean = false
        private set

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Start real-time snapshot listeners for all synced collections.
     * Fires immediately with current Firestore state (initial snapshot),
     * then on every remote change.
     */
    fun startListeners() {
        if (isListening) {
            syncLog("startListeners called but already listening — skipped")
            return
        }
        isListening = true
        syncLog("Starting listeners for group $groupId (encCache=${lastSeenEnc.size} entries, stateCache=${lastKnownState.size} entries)")

        // Collection listeners for the 7 standard types
        for (collection in EncryptedDocSerializer.ALL_COLLECTIONS) {
            attachCollectionListener(collection)
        }

        // Singleton listener for SharedSettings
        attachSettingsListener()
    }

    private fun attachCollectionListener(collection: String) {
        val reg = FirestoreDocService.listenToCollection(
            groupId, collection,
            onDocumentChange = { changes -> handleCollectionChanges(collection, changes) },
            onError = { e ->
                syncLog("Listener error: $collection — ${e.message}")
                // Auto-reconnect after a delay
                deserializeScope.launch {
                    kotlinx.coroutines.delay(5_000)
                    if (isListening) {
                        syncLog("Reconnecting listener: $collection")
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            attachCollectionListener(collection)
                        }
                    }
                }
            }
        )
        listeners[collection] = reg
    }

    private fun attachSettingsListener() {
        val reg = FirestoreDocService.listenToDocument(
            groupId,
            EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS,
            EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID,
            onChange = { doc -> handleSharedSettingsChange(doc) },
            onError = { e ->
                syncLog("Listener error: sharedSettings — ${e.message}")
                deserializeScope.launch {
                    kotlinx.coroutines.delay(5_000)
                    if (isListening) {
                        syncLog("Reconnecting listener: sharedSettings")
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            attachSettingsListener()
                        }
                    }
                }
            }
        )
        listeners[EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS] = reg
    }

    /**
     * Stop all listeners. Call on group leave or activity dispose.
     */
    fun stopListeners() {
        syncLog("Stopping ${listeners.size} listeners")
        for (reg in listeners.values) {
            reg.remove()
        }
        listeners.clear()
        recentPushes.clear()
        // Preserve lastSeenEnc and lastKnownState across restarts so the
        // enc hash skip works if listeners are quickly reattached (e.g.
        // DisposableEffect recomposition). These caches are scoped to
        // this FirestoreDocSync instance, which is scoped to the group —
        // so they're naturally cleared when a new group is joined.
        deserializeScope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
        isListening = false
    }

    /**
     * Full cleanup including caches. Called only when leaving a group
     * (not on listener restart).
     */
    fun dispose() {
        stopListeners()
        lastSeenEnc.clear()
        lastKnownState.clear()
        localPendingEdits.clear()
        pendingEditsPrefs.edit().remove("edits").apply()
        encCacheFile.delete()
    }

    /**
     * Reattach a single collection's listener (integrity repair).
     * Detaches the old listener, clears its enc cache so all docs get
     * reprocessed, then attaches a fresh listener.
     */
    fun reattachListener(collection: String) {
        syncLog("Reattaching listener: $collection (integrity repair)")
        listeners[collection]?.remove()
        // Clear enc cache and state for this collection so all docs get reprocessed
        lastSeenEnc.keys.removeAll { it.startsWith("$collection:") }
        lastKnownState.keys.removeAll { it.startsWith("$collection:") }
        if (collection == EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS) {
            attachSettingsListener()
        } else {
            attachCollectionListener(collection)
        }
    }

    /**
     * Push a single record to Firestore. Called on local edits.
     * Uses diff-based field updates when the last known state is available,
     * falling back to full set() for new records or NOT_FOUND errors.
     */
    suspend fun pushRecord(record: Any) {
        val collection = EncryptedDocSerializer.collectionName(record)
        val docId = when (record) {
            is SharedSettings -> EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID
            else -> EncryptedDocSerializer.docId(record)
        }
        val stateKey = "$collection:$docId"

        recentPushes[stateKey] = System.currentTimeMillis()

        try {
            val lastKnown = lastKnownState[stateKey]
            if (lastKnown != null && lastKnown::class == record::class) {
                // Existing record — diff and use update()
                val changedFields = EncryptedDocSerializer.diffFields(lastKnown, record)
                if (changedFields.isEmpty()) {
                    recentPushes.remove(stateKey)
                    return // nothing changed
                }
                val data = EncryptedDocSerializer.fieldUpdate(record, changedFields, encryptionKey, deviceId)
                FirestoreDocService.updateFields(groupId, collection, docId, data)
                recentPushes[stateKey] = System.currentTimeMillis()  // refresh after write
                syncLog("Updated $stateKey: ${changedFields.size} fields [${changedFields.joinToString()}]")
            } else if (record is PeriodLedgerEntry) {
                // Period ledger: create-if-absent (first writer wins).
                // Prevents offline devices from overwriting correct entries
                // created by online devices with fresher budget configuration.
                val data = EncryptedDocSerializer.toFieldMap(record, encryptionKey, deviceId)
                val created = FirestoreDocService.createDocIfAbsent(groupId, collection, docId, data)
                if (created) {
                    syncLog("Created (new) $stateKey")
                } else {
                    syncLog("Skipped $stateKey (already exists in Firestore)")
                    recentPushes.remove(stateKey)
                    return // don't update local state — listener will deliver the correct entry
                }
            } else {
                // New record — use set() with all fields
                val data = EncryptedDocSerializer.toFieldMap(record, encryptionKey, deviceId)
                FirestoreDocService.writeDoc(groupId, collection, docId, data)
                recentPushes[stateKey] = System.currentTimeMillis()  // refresh after write
                syncLog("Set (new) $stateKey")
            }
            localPendingEdits[stateKey] = System.currentTimeMillis()
            persistPendingEdits()
            lastKnownState[stateKey] = record
        } catch (e: Exception) {
            syncLog("Push failed: $stateKey — ${e.message}")
            recentPushes.remove(stateKey)
            // If update() fails with NOT_FOUND, fall back to set()
            if (e.message?.contains("NOT_FOUND") == true || e.message?.contains("No document to update") == true) {
                try {
                    val data = EncryptedDocSerializer.toFieldMap(record, encryptionKey, deviceId)
                    FirestoreDocService.writeDoc(groupId, collection, docId, data)
                    recentPushes[stateKey] = System.currentTimeMillis()  // refresh after write
                    lastKnownState[stateKey] = record
                    localPendingEdits[stateKey] = System.currentTimeMillis()
                    persistPendingEdits()
                    syncLog("Fallback set() succeeded for $stateKey")
                } catch (e2: Exception) {
                    syncLog("Fallback set() also failed for $stateKey: ${e2.message}")
                    throw e2
                }
            } else {
                throw e
            }
        }
    }

    /**
     * Push all local records to Firestore. Used for one-time migration
     * from old CRDT data to native docs.
     */
    suspend fun pushAllRecords(
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>,
        periodLedgerEntries: List<PeriodLedgerEntry>,
        sharedSettings: SharedSettings
    ) {
        // Filter out tombstoned records — no need to push old deletions to a new group
        val liveTxns = transactions.filter { !it.deleted }
        val liveRe = recurringExpenses.filter { !it.deleted }
        val liveIs = incomeSources.filter { !it.deleted }
        val liveSg = savingsGoals.filter { !it.deleted }
        val liveAe = amortizationEntries.filter { !it.deleted }
        val liveCats = categories.filter { !it.deleted }

        syncLog("Migration: pushing ${liveTxns.size} txns (${transactions.size - liveTxns.size} tombstones skipped), " +
                "${liveRe.size} RE, ${liveIs.size} IS, ${liveSg.size} SG, ${liveAe.size} AE, " +
                "${liveCats.size} cats, ${periodLedgerEntries.size} PLE")

        pushBatch(EncryptedDocSerializer.COLLECTION_TRANSACTIONS, liveTxns) { t ->
            t.id.toString() to EncryptedDocSerializer.transactionToFieldMap(t, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES, liveRe) { re ->
            re.id.toString() to EncryptedDocSerializer.recurringExpenseToFieldMap(re, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_INCOME_SOURCES, liveIs) { src ->
            src.id.toString() to EncryptedDocSerializer.incomeSourceToFieldMap(src, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS, liveSg) { sg ->
            sg.id.toString() to EncryptedDocSerializer.savingsGoalToFieldMap(sg, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES, liveAe) { ae ->
            ae.id.toString() to EncryptedDocSerializer.amortizationEntryToFieldMap(ae, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_CATEGORIES, liveCats) { cat ->
            cat.id.toString() to EncryptedDocSerializer.categoryToFieldMap(cat, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER, periodLedgerEntries) { ple ->
            ple.id.toString() to EncryptedDocSerializer.periodLedgerToFieldMap(ple, encryptionKey, deviceId)
        }

        // SharedSettings is a single doc, not batched
        val ssData = EncryptedDocSerializer.sharedSettingsToFieldMap(sharedSettings, encryptionKey, deviceId)
        FirestoreDocService.writeDoc(
            groupId,
            EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS,
            EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID,
            ssData
        )

        // Populate lastKnownState so subsequent pushRecord() calls use diffs
        for (t in liveTxns) lastKnownState["${EncryptedDocSerializer.COLLECTION_TRANSACTIONS}:${t.id}"] = t
        for (re in liveRe) lastKnownState["${EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES}:${re.id}"] = re
        for (src in liveIs) lastKnownState["${EncryptedDocSerializer.COLLECTION_INCOME_SOURCES}:${src.id}"] = src
        for (sg in liveSg) lastKnownState["${EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS}:${sg.id}"] = sg
        for (ae in liveAe) lastKnownState["${EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES}:${ae.id}"] = ae
        for (cat in liveCats) lastKnownState["${EncryptedDocSerializer.COLLECTION_CATEGORIES}:${cat.id}"] = cat
        for (ple in periodLedgerEntries) lastKnownState["${EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER}:${ple.id}"] = ple
        lastKnownState["${EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS}:${EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID}"] = sharedSettings

        syncLog("Migration complete: pushed ${liveTxns.size} txns, " +
                "${liveRe.size} RE, ${liveIs.size} IS, " +
                "${liveSg.size} SG, ${liveAe.size} AE, " +
                "${liveCats.size} cats, ${periodLedgerEntries.size} PLE")
    }

    // ── internal: listener handlers ─────────────────────────────────────

    private fun handleCollectionChanges(collection: String, changes: List<DocumentChange>) {
        pruneExpiredEchoKeys()

        // Filter out echoes from our own recent writes (lightweight, main thread OK)
        val toProcess = changes.filter { change ->
            !recentPushes.containsKey("$collection:${change.document.id}")
        }
        if (toProcess.isEmpty()) return

        // Heavy work (decrypt + JSON parse) on background thread,
        // then deliver batch to UI on main thread.
        deserializeScope.launch {
            // Expire stale pending edits (>1 hour old — device was offline too long for reliable conflict detection)
            val expiryMs = 60 * 60 * 1000L
            val now = System.currentTimeMillis()
            localPendingEdits.entries.removeAll { now - it.value > expiryMs }

            var skippedUnchanged = 0
            val events = mutableListOf<DataChangeEvent>()
            for (change in toProcess) {
                val docId = change.document.id
                val stateKey = "$collection:$docId"
                try {
                    // Composite hash of all enc_* fields for skip detection
                    val encComposite = change.document.data
                        ?.filterKeys { it.startsWith("enc_") }
                        ?.entries
                        ?.sortedBy { it.key }
                        ?.joinToString("|") { "${it.key}=${it.value}" }
                        ?.hashCode()?.toString()
                        ?: change.document.getString("enc") // fallback for old single-blob format

                    if (change.type != DocumentChange.Type.REMOVED) {
                        if (encComposite != null && lastSeenEnc[stateKey] == encComposite) {
                            skippedUnchanged++
                            continue
                        }
                    } else {
                        // Document removed — clear from caches
                        lastSeenEnc.remove(stateKey)
                        lastKnownState.remove(stateKey)
                    }

                    val record = deserializeDoc(collection, change.document) ?: continue
                    val action = when (change.type) {
                        DocumentChange.Type.ADDED -> "added"
                        DocumentChange.Type.MODIFIED -> "modified"
                        DocumentChange.Type.REMOVED -> "removed"
                    }

                    // Conflict detection
                    val lastEditBy = change.document.getString("lastEditBy")
                    var isConflict = false
                    if (lastEditBy != null && lastEditBy != deviceId && localPendingEdits.containsKey(stateKey)) {
                        // Another device edited this record while we have pending local edits
                        isConflict = true
                        syncLog("Conflict detected: $stateKey (lastEditBy=$lastEditBy, we have pending edit)")
                    }
                    // Clear pending if our edit confirmed
                    if (lastEditBy == deviceId) {
                        localPendingEdits.remove(stateKey)
                        persistPendingEdits()
                    }

                    events.add(DataChangeEvent(collection, action, record, docId, isConflict))

                    // Update enc cache and last known state after successful deserialization
                    if (encComposite != null && change.type != DocumentChange.Type.REMOVED) {
                        lastSeenEnc[stateKey] = encComposite
                    }
                    lastKnownState[stateKey] = record
                } catch (e: Exception) {
                    val hint = if (e.message?.contains("Tag mismatch") == true || e.message?.contains("AEADBadTag") == true)
                        " (possible wrong encryption key)" else ""
                    syncLog("Failed to deserialize $collection/$docId: ${e.message}$hint")
                }
            }
            if (events.isNotEmpty()) {
                syncLog("Received ${events.size} changes in $collection" +
                    if (skippedUnchanged > 0) " (skipped $skippedUnchanged unchanged)" else "")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onBatchChanged?.invoke(events)
                }
            } else if (skippedUnchanged > 0) {
                syncLog("Skipped $skippedUnchanged unchanged docs in $collection")
            }
            // Persist enc hash cache after processing each collection batch
            if (events.isNotEmpty() || skippedUnchanged > 0) {
                persistEncCache()
            }
        }
    }

    private fun handleSharedSettingsChange(doc: DocumentSnapshot) {
        pruneExpiredEchoKeys()

        val stateKey = "${EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS}:${EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID}"
        if (recentPushes.containsKey(stateKey)) return

        // Composite hash of all enc_* fields for skip detection
        val encComposite = doc.data
            ?.filterKeys { it.startsWith("enc_") }
            ?.entries
            ?.sortedBy { it.key }
            ?.joinToString("|") { "${it.key}=${it.value}" }
            ?.hashCode()?.toString()
            ?: doc.getString("enc") // fallback for old single-blob format

        if (encComposite != null && lastSeenEnc[stateKey] == encComposite) {
            syncLog("Skipped 1 unchanged doc in sharedSettings")
            return
        }

        deserializeScope.launch {
            try {
                val settings = EncryptedDocSerializer.sharedSettingsFromDoc(doc, encryptionKey)
                // Update enc cache and last known state after successful decryption
                if (encComposite != null) lastSeenEnc[stateKey] = encComposite
                lastKnownState[stateKey] = settings
                syncLog("Received 1 changes in sharedSettings")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onBatchChanged?.invoke(listOf(
                        DataChangeEvent(
                            collection = EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS,
                            action = "modified",
                            record = settings,
                            docId = EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID
                        )
                    ))
                }
            } catch (e: Exception) {
                val hint = if (e.message?.contains("Tag mismatch") == true || e.message?.contains("AEADBadTag") == true)
                    " (possible wrong encryption key)" else ""
                syncLog("Failed to deserialize sharedSettings: ${e.message}$hint")
            }
        }
    }

    // ── internal: deserialization dispatch ───────────────────────────────

    private fun deserializeDoc(collection: String, doc: DocumentSnapshot): Any? {
        // Accept docs with either old single-blob "enc" field or new per-field "enc_*" fields
        val hasEncBlob = doc.contains("enc") && doc.getString("enc") != null
        val hasEncFields = doc.data?.keys?.any { it.startsWith("enc_") } == true
        if (!hasEncBlob && !hasEncFields) return null

        return when (collection) {
            EncryptedDocSerializer.COLLECTION_TRANSACTIONS ->
                EncryptedDocSerializer.transactionFromDoc(doc, encryptionKey)
            EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES ->
                EncryptedDocSerializer.recurringExpenseFromDoc(doc, encryptionKey)
            EncryptedDocSerializer.COLLECTION_INCOME_SOURCES ->
                EncryptedDocSerializer.incomeSourceFromDoc(doc, encryptionKey)
            EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS ->
                EncryptedDocSerializer.savingsGoalFromDoc(doc, encryptionKey)
            EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES ->
                EncryptedDocSerializer.amortizationEntryFromDoc(doc, encryptionKey)
            EncryptedDocSerializer.COLLECTION_CATEGORIES ->
                EncryptedDocSerializer.categoryFromDoc(doc, encryptionKey)
            EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER ->
                EncryptedDocSerializer.periodLedgerFromDoc(doc, encryptionKey)
            else -> {
                Log.w(TAG, "Unknown collection: $collection")
                null
            }
        }
    }

    // ── internal: batch push helper ─────────────────────────────────────

    private suspend fun <T> pushBatch(
        collection: String,
        records: List<T>,
        transform: (T) -> Pair<String, Map<String, Any>>
    ) {
        if (records.isEmpty()) return
        val docs = records.map { transform(it) }

        // Mark all as recently pushed for echo suppression
        for ((docId, _) in docs) {
            recentPushes["$collection:$docId"] = System.currentTimeMillis()
        }

        FirestoreDocService.writeBatch(groupId, collection, docs)
    }

    // ── internal: echo key maintenance ──────────────────────────────────

    private fun pruneExpiredEchoKeys() {
        val cutoff = System.currentTimeMillis() - ECHO_SUPPRESS_MS
        recentPushes.entries.removeAll { it.value < cutoff }
    }
}

/**
 * Event emitted when a remote document change arrives via snapshot listener.
 */
data class DataChangeEvent(
    /** Collection name (e.g. "transactions", "categories"). */
    val collection: String,
    /** Change type: "added", "modified", or "removed". */
    val action: String,
    /** The deserialized data class (Transaction, Category, etc.). */
    val record: Any,
    /** Firestore document ID. */
    val docId: String,
    /** True if another device edited this record while we have pending local edits. */
    val isConflict: Boolean = false
)

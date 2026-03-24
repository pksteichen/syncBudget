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

    // Active snapshot listeners — detached on stop
    private val listeners = mutableListOf<ListenerRegistration>()

    // Echo prevention: track recently-pushed doc keys ("collection:docId")
    // Value = timestamp when pushed
    private val recentPushes = ConcurrentHashMap<String, Long>()

    // Background scope for heavy deserialization (decrypt + JSON parse)
    // so the main thread stays responsive during large syncs.
    private val deserializeScope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    /** Callback fired with a batch of incoming remote changes per collection.
     *  Always invoked on the MAIN thread. */
    var onBatchChanged: ((List<DataChangeEvent>) -> Unit)? = null

    /** Whether listeners are currently active. */
    var isListening: Boolean = false
        private set

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Start real-time snapshot listeners for all synced collections.
     * Fires immediately with current Firestore state (initial snapshot),
     * then on every remote change.
     */
    fun startListeners() {
        if (isListening) return
        isListening = true
        syncLog("Starting listeners for group $groupId")

        // Collection listeners for the 7 standard types
        for (collection in EncryptedDocSerializer.ALL_COLLECTIONS) {
            val reg = FirestoreDocService.listenToCollection(
                groupId, collection,
                onDocumentChange = { changes -> handleCollectionChanges(collection, changes) },
                onError = { e -> syncLog("Listener error: $collection — ${e.message}") }
            )
            listeners.add(reg)
        }

        // Singleton listener for SharedSettings
        val ssReg = FirestoreDocService.listenToDocument(
            groupId,
            EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS,
            EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID,
            onChange = { doc -> handleSharedSettingsChange(doc) },
            onError = { e -> Log.e(TAG, "Listener error: sharedSettings", e) }
        )
        listeners.add(ssReg)
    }

    /**
     * Stop all listeners. Call on group leave or activity dispose.
     */
    fun stopListeners() {
        syncLog("Stopping ${listeners.size} listeners")
        for (reg in listeners) {
            reg.remove()
        }
        listeners.clear()
        recentPushes.clear()
        deserializeScope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
        isListening = false
    }

    /**
     * Push a single record to Firestore. Called on local edits.
     * Records the push for echo suppression.
     */
    suspend fun pushRecord(record: Any) {
        val collection = EncryptedDocSerializer.collectionName(record)
        val docId = when (record) {
            is SharedSettings -> EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID
            else -> EncryptedDocSerializer.docId(record)
        }
        val data = EncryptedDocSerializer.toDoc(record, encryptionKey, deviceId)

        // Mark as recently pushed for echo suppression
        val echoKey = "$collection:$docId"
        recentPushes[echoKey] = System.currentTimeMillis()

        try {
            FirestoreDocService.writeDoc(groupId, collection, docId, data)
        } catch (e: Exception) {
            syncLog("Push failed: $echoKey — ${e.message}")
            recentPushes.remove(echoKey)
            throw e
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
        syncLog("Migration: pushing all records to Firestore")

        pushBatch(EncryptedDocSerializer.COLLECTION_TRANSACTIONS, transactions) { t ->
            t.id.toString() to EncryptedDocSerializer.transactionToDoc(t, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES, recurringExpenses) { re ->
            re.id.toString() to EncryptedDocSerializer.recurringExpenseToDoc(re, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_INCOME_SOURCES, incomeSources) { src ->
            src.id.toString() to EncryptedDocSerializer.incomeSourceToDoc(src, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_SAVINGS_GOALS, savingsGoals) { sg ->
            sg.id.toString() to EncryptedDocSerializer.savingsGoalToDoc(sg, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_AMORTIZATION_ENTRIES, amortizationEntries) { ae ->
            ae.id.toString() to EncryptedDocSerializer.amortizationEntryToDoc(ae, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_CATEGORIES, categories) { cat ->
            cat.id.toString() to EncryptedDocSerializer.categoryToDoc(cat, encryptionKey, deviceId)
        }
        pushBatch(EncryptedDocSerializer.COLLECTION_PERIOD_LEDGER, periodLedgerEntries) { ple ->
            ple.id.toString() to EncryptedDocSerializer.periodLedgerToDoc(ple, encryptionKey, deviceId)
        }

        // SharedSettings is a single doc, not batched
        val ssData = EncryptedDocSerializer.sharedSettingsToDoc(sharedSettings, encryptionKey, deviceId)
        FirestoreDocService.writeDoc(
            groupId,
            EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS,
            EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID,
            ssData
        )

        syncLog("Migration complete: pushed ${transactions.size} txns, " +
                "${recurringExpenses.size} RE, ${incomeSources.size} IS, " +
                "${savingsGoals.size} SG, ${amortizationEntries.size} AE, " +
                "${categories.size} cats, ${periodLedgerEntries.size} PLE")
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
            val events = mutableListOf<DataChangeEvent>()
            for (change in toProcess) {
                val docId = change.document.id
                try {
                    val record = deserializeDoc(collection, change.document) ?: continue
                    val action = when (change.type) {
                        DocumentChange.Type.ADDED -> "added"
                        DocumentChange.Type.MODIFIED -> "modified"
                        DocumentChange.Type.REMOVED -> "removed"
                    }
                    events.add(DataChangeEvent(collection, action, record, docId))
                } catch (e: Exception) {
                    syncLog("Failed to deserialize $collection/$docId: ${e.message}")
                }
            }
            if (events.isNotEmpty()) {
                syncLog("Received ${events.size} changes in $collection")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onBatchChanged?.invoke(events)
                }
            }
        }
    }

    private fun handleSharedSettingsChange(doc: DocumentSnapshot) {
        pruneExpiredEchoKeys()

        val echoKey = "${EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS}:${EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID}"
        if (recentPushes.containsKey(echoKey)) return

        deserializeScope.launch {
            try {
                val settings = EncryptedDocSerializer.sharedSettingsFromDoc(doc, encryptionKey)
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
                syncLog("Failed to deserialize sharedSettings: ${e.message}")
            }
        }
    }

    // ── internal: deserialization dispatch ───────────────────────────────

    private fun deserializeDoc(collection: String, doc: DocumentSnapshot): Any? {
        if (!doc.contains("enc") || doc.getString("enc") == null) return null

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
    val docId: String
)

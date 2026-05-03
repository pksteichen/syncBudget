package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.techadvantage.budgetrak.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
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
        /** How long to suppress echo callbacks after a foreground write (ms). */
        private const val ECHO_SUPPRESS_MS = 5_000L
        /** How long to suppress echoes from background worker pushes (ms).
         *  Longer because background workers run every 15 min. */
        private const val BG_ECHO_SUPPRESS_MS = 20 * 60 * 1000L
        private const val LOG_FILE = "native_sync_log.txt"
        // ~200KB per rotation = typically ≥ 2 days of sync-event history at
        // observed rates (2-3 KB/hr), with the rotated _prev file preserving
        // an additional window. Total retention ~4-6 days.
        private const val MAX_LOG_SIZE = 200_000L
        /** Minimum gap between full PERMISSION_DENIED restarts (ms). */
        private const val PERMISSION_DENIED_RESTART_COOLDOWN_MS = 30_000L

        /**
         * Set all per-collection sync cursors from a snapshot timestamp.
         * Called at join time before FirestoreDocSync is instantiated, so listeners
         * start filtered from the snapshot point instead of reading all docs.
         */
        fun setCursorsFromTimestamp(context: Context, timestampMillis: Long) {
            val prefs = context.getSharedPreferences("sync_cursor", Context.MODE_PRIVATE)
            val seconds = timestampMillis / 1000
            val nanos = ((timestampMillis % 1000) * 1_000_000).toInt()
            val editor = prefs.edit()
            for (collection in EncryptedDocSerializer.ALL_COLLECTIONS) {
                editor.putLong("cursor_${collection}_seconds", seconds)
                editor.putInt("cursor_${collection}_nanos", nanos)
            }
            editor.putLong("cursor_${EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS}_seconds", seconds)
            editor.putInt("cursor_${EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS}_nanos", nanos)
            editor.apply()
        }
    }

    /** Sync log — always logs to logcat; file output only in debug builds. */
    private fun syncLog(msg: String) {
        Log.i(TAG, msg)
        if (com.techadvantage.budgetrak.BuildConfig.DEBUG) {
            try {
                val dir = BackupManager.getSupportDir()
                val file = java.io.File(dir, LOG_FILE)
                com.techadvantage.budgetrak.BudgeTrakApplication.rotateLogToPrev(file, MAX_LOG_SIZE)
                file.appendText("[${LocalDateTime.now()}] $msg\n")
            } catch (_: Exception) {}
        }
    }

    // Active snapshot listeners — keyed by collection name, detached on stop
    private val listeners = ConcurrentHashMap<String, ListenerRegistration>()

    // Track listener error state for recovery detection
    @Volatile private var hasListenerError = false
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()

    /** Called when listeners recover from errors (e.g., PERMISSION_DENIED → success). */
    var onListenerRecovered: (() -> Unit)? = null

    /**
     * Called when the group has been dissolved (or this device's membership
     * has gone missing) and the local sync state must reset to non-SYNC.
     *
     * Triggered from `triggerFullRestart()` when a `groups/{gid}` `get`
     * returns a non-existent doc, or when `groups/{gid}/members/{uid}` is
     * missing — both of which cause every listener to fail with
     * PERMISSION_DENIED in an unrecoverable retry loop. The callback
     * receiver is expected to dispose listeners + clear local prefs +
     * surface a user-visible message ("your group is no longer available").
     */
    var onGroupDissolved: (() -> Unit)? = null

    // Track which collections have delivered their initial snapshot (even if empty).
    // Completes when all 8 (7 data + sharedSettings) have reported.
    private val deliveredCollections = ConcurrentHashMap.newKeySet<String>()
    private val allDelivered = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val expectedCollections = EncryptedDocSerializer.ALL_COLLECTIONS.size + 1 // +1 for sharedSettings

    /** Suspend until all collection listeners have delivered their initial snapshot. */
    suspend fun awaitInitialSync(timeoutMs: Long = 30_000): Boolean {
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { allDelivered.await() } != null
    }

    private fun markCollectionDelivered(collection: String) {
        reconnectAttempts.remove(collection) // reset backoff on successful delivery
        if (deliveredCollections.add(collection) && deliveredCollections.size >= expectedCollections) {
            if (!allDelivered.isCompleted) allDelivered.complete(Unit)
        }
    }

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

    // Debounce: when PERMISSION_DENIED triggers a full listener restart,
    // don't restart again for at least PERMISSION_DENIED_RESTART_COOLDOWN_MS.
    @Volatile private var lastPermDeniedRestart = 0L

    // Per-collection sync cursors: the latest updatedAt timestamp processed per collection.
    // Each collection tracks independently so a partial delivery (app killed mid-sync)
    // never advances one collection's cursor past another's undelivered changes.
    private val cursorPrefs = context.getSharedPreferences("sync_cursor", Context.MODE_PRIVATE)

    private fun loadCursor(collection: String): com.google.firebase.Timestamp? {
        val seconds = cursorPrefs.getLong("cursor_${collection}_seconds", -1L)
        if (seconds < 0) return null
        val nanos = cursorPrefs.getInt("cursor_${collection}_nanos", 0)
        return com.google.firebase.Timestamp(seconds, nanos)
    }

    private fun saveCursor(collection: String, ts: com.google.firebase.Timestamp) {
        cursorPrefs.edit()
            .putLong("cursor_${collection}_seconds", ts.seconds)
            .putInt("cursor_${collection}_nanos", ts.nanoseconds)
            .apply()
    }

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
        // Load persisted background push keys (from BackgroundSyncWorker.pushRefreshResults)
        // so we can filter echoes of our own period refresh writes.
        try {
            val syncPrefs = context.getSharedPreferences("sync_engine", android.content.Context.MODE_PRIVATE)
            val bgJson = syncPrefs.getString("bgPushKeys", null)
            if (bgJson != null) {
                val obj = org.json.JSONObject(bgJson)
                val cutoff = System.currentTimeMillis() - 20 * 60 * 1000L
                var loaded = 0
                for (key in obj.keys()) {
                    val ts = obj.getLong(key)
                    if (ts > cutoff) {
                        recentPushes[key] = ts
                        loaded++
                    }
                }
                if (loaded > 0) syncLog("Loaded $loaded background push keys for echo suppression")
                // Clear persisted keys — they're now in recentPushes
                syncPrefs.edit().remove("bgPushKeys").apply()
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

    /**
     * Compute the enc hash for a field map we're about to push to Firestore.
     * Must match the hash logic in handleCollectionChanges/handleSharedSettingsChange
     * so that listener deliveries for the same doc skip decryption.
     */
    private fun computeEncHash(fields: Map<String, Any>): String? {
        val enc = fields.filterKeys { it.startsWith("enc_") }
        if (enc.isEmpty()) return null
        return enc.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }
            .hashCode().toString()
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
    var onBatchChanged: (suspend (List<DataChangeEvent>) -> Unit)? = null

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
        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("Listeners started: encCache=${lastSeenEnc.size}")

        // Collection listeners for the 7 standard types
        for (collection in EncryptedDocSerializer.ALL_COLLECTIONS) {
            attachCollectionListener(collection)
        }

        // Singleton listener for SharedSettings
        attachSettingsListener()
    }

    private fun attachCollectionListener(collection: String) {
        val cursor = loadCursor(collection)
        val errorHandler = { e: Exception ->
            val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            syncLog("Listener error: $collection — ${e.message} (authUid=$authUid)")
            if (e.message?.contains("PERMISSION_DENIED") == true) {
                com.techadvantage.budgetrak.BudgeTrakApplication.recordNonFatal("PERMISSION_DENIED", "$collection (authUid=$authUid)", e)
                com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog("PERMISSION_DENIED: $collection (authUid=$authUid)")
                hasListenerError = true
                // Instead of reconnecting individual listeners (which reuse the
                // same SDK session and its cached expired auth), stop everything,
                // force-refresh the App Check token, and restart all listeners
                // fresh. Debounced to avoid 8 concurrent restart triggers.
                triggerFullRestart()
            } else {
                hasListenerError = true
                val attempts = reconnectAttempts.merge(collection, 1) { old, _ -> old + 1 } ?: 1
                if (attempts <= 10) {
                    val delayMs = minOf(5_000L * (1L shl minOf(attempts - 1, 5)), 300_000L)
                    deserializeScope.launch {
                        syncLog("Reconnecting $collection in ${delayMs/1000}s (attempt $attempts)")
                        kotlinx.coroutines.delay(delayMs)
                        if (isListening) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                attachCollectionListener(collection)
                            }
                        }
                    }
                } else {
                    syncLog("Listener $collection failed $attempts times, giving up")
                }
            }
            Unit
        }
        val reg = if (cursor != null) {
            syncLog("Attaching filtered listener: $collection (cursor=${cursor.seconds})")
            FirestoreDocService.listenToCollectionSince(
                groupId, collection, cursor,
                onDocumentChange = { changes -> handleCollectionChanges(collection, changes) },
                onError = errorHandler
            )
        } else {
            syncLog("Attaching full listener: $collection (no cursor)")
            FirestoreDocService.listenToCollection(
                groupId, collection,
                onDocumentChange = { changes -> handleCollectionChanges(collection, changes) },
                onError = errorHandler
            )
        }
        listeners[collection] = reg
    }

    private fun attachSettingsListener() {
        val reg = FirestoreDocService.listenToDocument(
            groupId,
            EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS,
            EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID,
            onChange = { doc -> handleSharedSettingsChange(doc) },
            onError = { e ->
                val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                syncLog("Listener error: sharedSettings — ${e.message} (authUid=$authUid)")
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog("PERMISSION_DENIED: sharedSettings (authUid=$authUid)")
                    hasListenerError = true
                    triggerFullRestart()
                } else {
                    hasListenerError = true
                    val attempts = reconnectAttempts.merge("sharedSettings", 1) { old, _ -> old + 1 } ?: 1
                    if (attempts <= 10) {
                        val delayMs = minOf(5_000L * (1L shl minOf(attempts - 1, 5)), 300_000L)
                        deserializeScope.launch {
                            syncLog("Reconnecting sharedSettings in ${delayMs/1000}s (attempt $attempts)")
                            kotlinx.coroutines.delay(delayMs)
                            if (isListening) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    attachSettingsListener()
                                }
                            }
                        }
                    } else {
                        syncLog("Listener sharedSettings failed $attempts times, giving up")
                    }
                }
            }
        )
        listeners[EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS] = reg
    }

    /**
     * Stop all listeners. Call on group leave or activity dispose.
     */
    /** Wait for all in-flight deserialization coroutines to finish (up to 5s). */
    suspend fun awaitDeserializationComplete(timeoutMs: Long = 5_000) {
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            deserializeScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }
    }

    /**
     * Stop all listeners. [graceful] = true skips cancelChildren so any in-flight
     * Firestore callbacks that fire after remove() can still complete their
     * deserialization. The caller is responsible for calling awaitDeserializationComplete()
     * afterwards to drain late callbacks before processing events.
     */
    fun stopListeners(graceful: Boolean = false) {
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
        if (!graceful) {
            deserializeScope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
        }
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
     * When any listener gets PERMISSION_DENIED, stop ALL listeners,
     * force-refresh the App Check token, and restart everything fresh.
     * Debounced to 30s to avoid 8 listeners triggering 8 concurrent restarts.
     */
    private fun triggerFullRestart() {
        val now = System.currentTimeMillis()
        if (now - lastPermDeniedRestart < PERMISSION_DENIED_RESTART_COOLDOWN_MS) return
        lastPermDeniedRestart = now

        deserializeScope.launch {
            syncLog("PERMISSION_DENIED → checking group + membership before listener restart")
            // Stop all existing listeners (stale connections with expired auth)
            kotlinx.coroutines.withContext(Dispatchers.Main) { stopListeners() }

            // Distinguish recoverable (token-stale) from non-recoverable
            // (group dissolved / membership missing) PERMISSION_DENIED before
            // burning an App Check refresh + reattach cycle. Firestore rules
            // explicitly allow `get groups/{gid}` for any authenticated user
            // and `get groups/{gid}/members/{uid}` for the owning UID — both
            // are designed for exactly this dissolution-detection path.
            //
            // Three outcomes:
            //   1. group doc missing                → group dissolved (admin
            //      called dissolveGroup → cleanupGroupData cascade) OR
            //      group was never created (silent failure during onCreate/
            //      onJoinGroup leaving phantom local prefs).
            //   2. group exists but member doc missing → this device was
            //      evicted, OR the admin reinstalled and dissolved+recreated
            //      under a new UID, OR a partial join wrote local prefs but
            //      not the Firestore member record.
            //   3. both exist                       → token is stale, fall
            //      through to the App Check refresh + reattach (existing
            //      behavior).
            // Cases 1+2 fire onGroupDissolved so the receiver can clear
            // local state. Case 3 continues to the standard restart.
            try {
                val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val groupSnap = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    db.document("groups/$groupId")
                        .get(com.google.firebase.firestore.Source.SERVER)
                        .await()
                }
                if (groupSnap == null) {
                    syncLog("Group existence check timed out (10s) — falling through to token refresh")
                } else if (!groupSnap.exists()) {
                    syncLog("Group $groupId no longer exists in Firestore — firing onGroupDissolved")
                    com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
                        "Group dissolved (group doc missing) — resetting local sync state"
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onGroupDissolved?.invoke()
                    }
                    return@launch
                } else if (authUid != null) {
                    val memberSnap = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                        db.document("groups/$groupId/members/$authUid")
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .await()
                    }
                    if (memberSnap != null && !memberSnap.exists()) {
                        syncLog("Group $groupId exists but members/$authUid is missing — firing onGroupDissolved (evicted, or partial join/create)")
                        com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent(
                            "Membership missing — resetting local sync state"
                        )
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            onGroupDissolved?.invoke()
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                // Probe failed for some other reason (offline, unrelated
                // App Check error). Don't conclude dissolution from a probe
                // error — fall through to the standard token-refresh path.
                syncLog("Group existence/membership probe failed: ${e.message} — falling through to token refresh")
            }

            syncLog("PERMISSION_DENIED → full listener restart (stop all, refresh token, restart)")

            // Force-refresh App Check token with timeout (can hang in Doze/network loss)
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                        .getAppCheckToken(true)
                        .await()
                }
                if (result == null) {
                    syncLog("AppCheck token refresh timed out during full restart (15s)")
                    com.techadvantage.budgetrak.BudgeTrakApplication.recordNonFatal(
                        "TOKEN_REFRESH_TIMEOUT", "Full restart token refresh hung for 15s")
                    return@launch
                }
                val expiresIn = (result.expireTimeMillis - System.currentTimeMillis()) / 1000
                com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
                    "AppCheck token force-refreshed for full restart: expires in ${expiresIn}s (${expiresIn/60}m)"
                )
            } catch (e: Exception) {
                syncLog("AppCheck token refresh failed during full restart: ${e.message}")
                // Don't restart listeners with a bad token — next BackgroundSyncWorker will try
                return@launch
            }

            // Small delay so Firestore SDK picks up the new token
            kotlinx.coroutines.delay(500)

            // Restart all listeners fresh with the new token
            reconnectAttempts.clear()
            kotlinx.coroutines.withContext(Dispatchers.Main) { startListeners() }
            syncLog("Full listener restart complete")
            com.techadvantage.budgetrak.BudgeTrakApplication.syncEvent("Full listener restart complete")
            onListenerRecovered?.invoke()
        }
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
    /** Batch push multiple records of the same type. Uses set() for all (no diff). */
    suspend fun pushRecordsBatch(records: List<Any>) {
        if (records.isEmpty()) return
        val collection = EncryptedDocSerializer.collectionName(records.first())
        val docs = records.map { record ->
            val docId = when (record) {
                is SharedSettings -> EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID
                else -> EncryptedDocSerializer.docId(record)
            }
            val stateKey = "$collection:$docId"
            recentPushes[stateKey] = System.currentTimeMillis()
            localPendingEdits[stateKey] = System.currentTimeMillis()
            lastKnownState[stateKey] = record
            docId to EncryptedDocSerializer.toFieldMap(record, encryptionKey, deviceId)
        }
        FirestoreDocService.writeBatch(groupId, collection, docs)
        // After a successful full-doc batch write we know exactly what's on the server.
        // Populate lastSeenEnc so later listener deliveries (e.g., on background worker
        // cold start) skip decryption of these unchanged docs.
        for ((docId, fields) in docs) {
            val hash = computeEncHash(fields) ?: continue
            lastSeenEnc["$collection:$docId"] = hash
        }
        persistEncCache()
        persistPendingEdits()
        syncLog("Batch pushed ${docs.size} records to $collection")
    }

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
                    computeEncHash(data)?.let { lastSeenEnc[stateKey] = it; persistEncCache() }
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
                computeEncHash(data)?.let { lastSeenEnc[stateKey] = it; persistEncCache() }
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
                    computeEncHash(data)?.let { lastSeenEnc[stateKey] = it; persistEncCache() }
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
        computeEncHash(ssData)?.let {
            lastSeenEnc["${EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS}:${EncryptedDocSerializer.SHARED_SETTINGS_DOC_ID}"] = it
        }
        persistEncCache()

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
        // Detect recovery from listener errors (e.g., PERMISSION_DENIED resolved)
        if (hasListenerError) {
            hasListenerError = false
            syncLog("Listener recovered — notifying callback")
            onListenerRecovered?.invoke()
        }
        markCollectionDelivered(collection)
        pruneExpiredEchoKeys()

        // Filter out echoes from our own recent writes (lightweight, main thread OK).
        // For background push keys (persisted from BackgroundSyncWorker), also verify
        // lastEditBy matches our deviceId — if another device edited the same doc
        // since our push, we should process their update, not filter it as our echo.
        val toProcess = changes.filter { change ->
            val key = "$collection:${change.document.id}"
            if (!recentPushes.containsKey(key)) return@filter true
            // Check lastEditBy: if someone else edited since our push, keep the change
            val lastEditBy = change.document.getString("lastEditBy")
            lastEditBy != null && lastEditBy != deviceId
        }
        if (toProcess.isEmpty()) {
            // Pure echo batch — still advance the cursor so a fresh listener
            // later (e.g., background worker cold start) doesn't re-deliver
            // these docs. Echoes are server-confirmed; we already have the data.
            val echoLatest = changes.mapNotNull { it.document.getTimestamp("updatedAt") }.maxOrNull()
            if (echoLatest != null) {
                val currentCursor = loadCursor(collection)
                if (currentCursor == null || echoLatest > currentCursor) {
                    saveCursor(collection, echoLatest)
                }
            }
            return
        }

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
            // Update per-collection cursor AFTER onBatchChanged has applied the data.
            // Each collection's cursor is independent, so partial delivery (app killed
            // before all collections report) only re-reads the incomplete collections.
            // Use full `changes` (not toProcess) so echoes in a mixed batch also
            // advance the cursor past themselves — preventing re-delivery on a
            // fresh listener (e.g., background worker cold start).
            val latestTimestamp = changes.mapNotNull { change ->
                change.document.getTimestamp("updatedAt")
            }.maxOrNull()
            if (latestTimestamp != null) {
                val currentCursor = loadCursor(collection)
                if (currentCursor == null || latestTimestamp > currentCursor) {
                    saveCursor(collection, latestTimestamp)
                }
            }
        }
    }

    private fun handleSharedSettingsChange(doc: DocumentSnapshot) {
        markCollectionDelivered(EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS)
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
                persistEncCache()
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
                // Advance sharedSettings cursor AFTER onBatchChanged has applied the data
                val settingsTimestamp = doc.getTimestamp("updatedAt")
                val settingsCollection = EncryptedDocSerializer.COLLECTION_SHARED_SETTINGS
                if (settingsTimestamp != null) {
                    val currentCursor = loadCursor(settingsCollection)
                    if (currentCursor == null || settingsTimestamp > currentCursor) {
                        saveCursor(settingsCollection, settingsTimestamp)
                    }
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
        // Populate lastSeenEnc so later listener deliveries skip decryption.
        for ((docId, fields) in docs) {
            val hash = computeEncHash(fields) ?: continue
            lastSeenEnc["$collection:$docId"] = hash
        }
        persistEncCache()
    }

    // ── internal: echo key maintenance ──────────────────────────────────

    private fun pruneExpiredEchoKeys() {
        // Use the longer background window so persisted push keys survive
        val cutoff = System.currentTimeMillis() - BG_ECHO_SUPPRESS_MS
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

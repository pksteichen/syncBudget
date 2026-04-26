package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.util.Log
import com.techadvantage.budgetrak.data.CryptoHelper
import com.techadvantage.budgetrak.data.Transaction
import com.techadvantage.budgetrak.data.TransactionRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Coordinates receipt photo sync: upload-first flow, recovery with snapshot
 * support, speed-based upload assignment, download, and 14-day pruning.
 * Only runs on paid (photo-capable) devices.
 */
class ReceiptSyncManager(
    private val context: Context,
    private val groupId: String,
    private val deviceId: String,
    private val encryptionKey: ByteArray,
    private val syncLog: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "ReceiptSyncManager"
        private const val STALE_ASSIGNMENT_MS = 5 * 60 * 1000L    // 5 minutes
        private const val FOURTEEN_DAYS_MS = 14L * 24 * 60 * 60 * 1000
        private const val PREFS_NAME = "receipt_sync_prefs"
        private const val KEY_LAST_SEEN_FLAG_CLOCK = "lastSeenFlagClock"
        private const val KEY_LAST_UPLOAD_TIME = "lastUploadTime"
        private const val KEY_RETRY_PREFIX = "download_retry_"
        private const val KEY_LAST_UPLOAD_SPEED_BPS = "lastUploadSpeedBps"
        private const val KEY_LAST_SPEED_MEASURED_AT = "lastSpeedMeasuredAt"
        private const val KEY_CONTENT_VERSION_PREFIX = "content_version_"
        private const val KEY_INITIAL_SYNC_COMPLETE = "initial_sync_complete"
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val SPEED_STALENESS_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val SNAPSHOT_THRESHOLD = 50
        private const val SNAPSHOT_STALE_MS = 2 * 60 * 60 * 1000L    // 2 hours
        private const val BATCH_RECOVERY_CAP = 50
        private const val SNAPSHOT_GRACE_PERIOD_MS = 5 * 60 * 1000L   // 5 min before cleanup
        private val SNAPSHOT_MAGIC = byteArrayOf(0x53, 0x4E, 0x41, 0x50) // "SNAP"
        private const val SNAPSHOT_FORMAT_VERSION = 1
        private const val SNAPSHOT_MAX_MANIFEST_BYTES = 10 * 1024 * 1024  // 10 MB — manifest is JSON index, plenty of slack
        private const val SNAPSHOT_MAX_ENTRY_BYTES = 10 * 1024 * 1024     // 10 MB — single encrypted receipt; typical is ~200 KB

        /**
         * Bump the local content version for a receipt by 1. Called from
         * `ReceiptManager.replaceReceipt` (rotation path) immediately after
         * the new file content is written and before the receipt is added
         * back to the pending-upload queue. The upload pipeline reads this
         * value to distinguish "rotation pending — bump cloud version" from
         * "resume of a partial commit — already at desired state". Without
         * the pre-bump, a worker cancellation between createLedgerEntry /
         * incrementContentVersion success and queue removal would cause the
         * next cycle to false-rotate, fanning out a flag-clock bump and
         * forcing every peer to re-download identical content.
         */
        fun bumpLocalContentVersionForRotation(context: Context, receiptId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getLong(KEY_CONTENT_VERSION_PREFIX + receiptId, 0L)
            prefs.edit().putLong(KEY_CONTENT_VERSION_PREFIX + receiptId, current + 1).apply()
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Main Sync Entry Point ───────────────────────────────────

    /**
     * Run receipt sync operations during a sync cycle.
     * Only called for paid (photo-capable) devices.
     */
    suspend fun syncReceipts(
        transactions: List<Transaction>,
        allDevices: List<DeviceInfo>
    ): List<Transaction> {
        val photoCapableDeviceIds = allDevices.filter { it.photoCapable }.map { it.deviceId }.toSet()
        var txns = transactions.toMutableList()
        val startNs = System.nanoTime()

        try {
            syncLog("syncReceipts START (txns=${transactions.size} photoCapable=${photoCapableDeviceIds.size})")

            // Step 1: Process pending uploads (upload-first, then create ledger)
            val uploaded = processPendingUploads()
            syncLog("step1 processPendingUploads done (uploaded=$uploaded)")

            // Step 2: Check flag clock and handle ledger-driven operations
            val ledgerCache = processLedgerOperations(txns, photoCapableDeviceIds, allDevices)
            txns = ledgerCache.first
            syncLog("step2 processLedgerOperations done (ledgerRead=${ledgerCache.second != null})")

            // Step 3: Handle recovery for missing local files (with snapshot support)
            txns = processRecovery(txns, photoCapableDeviceIds, allDevices)
            syncLog("step3 processRecovery done")

            // Step 3b: Process snapshot lifecycle (build/download if requested)
            processSnapshotLifecycle(txns, photoCapableDeviceIds, allDevices)
            syncLog("step3b processSnapshotLifecycle done")

            // Step 4: Check for stale pruning duty (14-day cleanup, reuses ledger if cached)
            processStalePruning(photoCapableDeviceIds, allDevices, ledgerCache.second)
            syncLog("step4 processStalePruning done")

            // Mark this device as "caught up" so future `processLedgerOperations`
            // can safely contribute non-possession markers to photo-lost detection.
            if (!prefs.getBoolean(KEY_INITIAL_SYNC_COMPLETE, false)) {
                prefs.edit().putBoolean(KEY_INITIAL_SYNC_COMPLETE, true).apply()
                syncLog("initial sync complete — non-possession gate lifted")
            }

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            syncLog("syncReceipts END (completed in ${elapsedMs}ms)")
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Tier 3 worker may be cancelled mid-flight by Samsung's power
            // management (Standby bucket constraints) before WorkManager's
            // 10-min limit. Log which phase we reached so tomorrow's dump
            // can quantify cancellation rate; then rethrow so the coroutine
            // unwinds cleanly.
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            syncLog("syncReceipts CANCELLED after ${elapsedMs}ms: ${ce.message}")
            throw ce
        }

        return txns
    }

    private fun hasCompletedInitialSync(): Boolean =
        prefs.getBoolean(KEY_INITIAL_SYNC_COMPLETE, false)

    // ── Step 1: Process Pending Uploads ─────────────────────────

    /**
     * Drain the pending-upload queue. Up to 5 concurrent uploads per chunk.
     * Public so the foreground upload drainer and BG Tier 2/3 workers can call
     * it directly (without going through full `syncReceipts`). Returns the
     * number of receipts successfully resolved — uploaded + ledger-entry or
     * dropped because the local file was already deleted.
     *
     * Completed ids are removed from the queue individually (not batched) so
     * concurrent `addToPendingQueue` calls (from `saveTransactions`) don't
     * get clobbered by a stale batch save. Two invocations overlapping (e.g.
     * foreground drainer + BG Tier 2) upload the same files twice at worst —
     * Cloud Storage overwrites are idempotent; `createLedgerEntry` is a
     * `.set()` so last-writer-wins on the same content is benign.
     */
    suspend fun processPendingUploads(): Int {
        val pending = ReceiptManager.loadPendingUploads(context)
        if (pending.isEmpty()) return 0

        syncLog("Receipt sync: ${pending.size} pending uploads")

        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        for (chunk in pending.toList().chunked(5)) {
            coroutineScope {
                chunk.map { receiptId ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val encrypted = ReceiptManager.encryptForUpload(context, receiptId, encryptionKey)
                            if (encrypted == null) {
                                syncLog("Receipt $receiptId: no local file, removing from queue")
                                ReceiptManager.removeFromPendingQueue(context, receiptId)
                                completedCount.incrementAndGet()
                                return@async
                            }

                            val startNanos = System.nanoTime()
                            val uploaded = ImageLedgerService.uploadToCloud(groupId, receiptId, encrypted)
                            if (uploaded) {
                                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                                if (elapsedMs > 0) {
                                    val bps = (encrypted.size.toLong() * 1000) / elapsedMs
                                    prefs.edit()
                                        .putLong(KEY_LAST_UPLOAD_SPEED_BPS, bps)
                                        .putLong(KEY_LAST_SPEED_MEASURED_AT, System.currentTimeMillis())
                                        .apply()
                                }

                                // Rotation-aware ledger write. Three cases:
                                //   1. No entry yet → fresh upload (createLedgerEntry).
                                //   2. Entry exists, uploadedAt > 0, and our last
                                //      committed local version equals the cloud
                                //      version with us as `lastEditBy` → resume of
                                //      a previous cycle that was cancelled between
                                //      the ledger write and queue removal. Storage
                                //      upload is idempotent; ledger already at the
                                //      desired state; just clean up the queue.
                                //      Skipping the bump here avoids a false rotation
                                //      that would force every peer to re-download.
                                //   3. Entry exists, uploadedAt > 0, but versions
                                //      diverge or a peer wrote last → rotation /
                                //      conflict resolution: incrementContentVersion
                                //      to bump cloud past our current local view.
                                val existing = ImageLedgerService.getLedgerEntry(groupId, receiptId)
                                val ledgerWritten = when {
                                    existing == null || existing.uploadedAt == 0L -> {
                                        val ok = ImageLedgerService.createLedgerEntry(
                                            groupId, receiptId, deviceId
                                        )
                                        if (ok) {
                                            setLocalContentVersion(receiptId, 0L)
                                            syncLog("Receipt $receiptId: uploaded + ledger created")
                                        }
                                        ok
                                    }
                                    existing.lastEditBy == deviceId &&
                                            existing.contentVersion == getLocalContentVersion(receiptId) -> {
                                        // Resume of our own commit — Storage was
                                        // re-uploaded (idempotent), ledger already
                                        // matches local, no rotation pending.
                                        syncLog("Receipt $receiptId: cloud already at v${existing.contentVersion} by us (resume), skipping bump")
                                        true
                                    }
                                    else -> {
                                        val ok = ImageLedgerService.incrementContentVersion(
                                            groupId, receiptId, deviceId
                                        )
                                        if (ok) {
                                            setLocalContentVersion(receiptId, existing.contentVersion + 1)
                                            syncLog("Receipt $receiptId: re-uploaded (content v${existing.contentVersion + 1})")
                                        }
                                        ok
                                    }
                                }
                                if (ledgerWritten) {
                                    ReceiptManager.removeFromPendingQueue(context, receiptId)
                                    completedCount.incrementAndGet()
                                    prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()).apply()
                                } else {
                                    syncLog("Receipt $receiptId: uploaded but ledger write failed, will retry")
                                }
                            } else {
                                syncLog("Receipt $receiptId: upload failed (${ImageLedgerService.lastUploadError}), will retry next cycle")
                            }
                        } catch (e: Exception) {
                            syncLog("Receipt $receiptId: upload exception: ${e.message}")
                        }
                    }
                }.forEach { it.await() }
            }
        }

        return completedCount.get()
    }

    // ── Step 2: Ledger-Driven Operations ────────────────────────

    /** Returns (updated transactions, ledger if read — null if flag clock unchanged). */
    private suspend fun processLedgerOperations(
        transactions: MutableList<Transaction>,
        photoCapableDeviceIds: Set<String>,
        allDevices: List<DeviceInfo>
    ): Pair<MutableList<Transaction>, List<ImageLedgerEntry>?> {
        val remoteFlagClock = ImageLedgerService.getFlagClock(groupId)
        val localFlagClock = prefs.getLong(KEY_LAST_SEEN_FLAG_CLOCK, 0L)

        if (remoteFlagClock <= localFlagClock) return transactions to null

        syncLog("Receipt sync: flag clock changed ($localFlagClock -> $remoteFlagClock), pulling ledger")
        val ledger = ImageLedgerService.getFullLedger(groupId)

        for (entry in ledger) {
            when {
                entry.uploadedAt == 0L && ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    // A peer is asking for this photo and we have it — maybe re-upload
                    handleReuploadRequest(entry, photoCapableDeviceIds, allDevices)
                }
                entry.uploadedAt == 0L && !ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    // Recovery request and we don't have the file. We contribute
                    // to the all-devices-report-false → photo-confirmed-lost
                    // tally — but ONLY after our first completed syncReceipts.
                    // Before that, a newly-joined or long-offline device hasn't
                    // had a chance to pull its own photos via processRecovery,
                    // and contributing a premature `false` could drive
                    // `checkPhotoLost` to delete a still-recoverable entry.
                    if (!hasCompletedInitialSync()) {
                        syncLog("Receipt ${entry.receiptId}: skipping non-possession mark (initial sync not complete)")
                    } else {
                        if (entry.possessions[deviceId] != false) {
                            ImageLedgerService.markNonPossession(groupId, entry.receiptId, deviceId)
                        }
                        val lost = ImageLedgerService.checkPhotoLost(groupId, entry.receiptId, photoCapableDeviceIds)
                        if (lost) {
                            clearLostReceiptSlot(transactions, entry.receiptId)
                            syncLog("Receipt ${entry.receiptId}: confirmed lost, cleared slot")
                        }
                    }
                }
                entry.uploadedAt > 0L && ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    // Rotation detection: if the ledger's contentVersion
                    // advanced past what we last downloaded, our local copy
                    // is stale — delete it so `processRecovery` (same cycle)
                    // re-downloads the new content.
                    val localVersion = getLocalContentVersion(entry.receiptId)
                    if (entry.contentVersion > localVersion) {
                        syncLog("Receipt ${entry.receiptId}: content v$localVersion → v${entry.contentVersion}, invalidating local")
                        ReceiptManager.deleteLocalReceipt(context, entry.receiptId)
                        clearLocalContentVersion(entry.receiptId)
                        // Possession for us is now stale — drop it so the
                        // ledger reflects our actual (empty) state.
                        ImageLedgerService.markNonPossession(groupId, entry.receiptId, deviceId)
                    } else {
                        // We have the current content — mark possession if absent and check prune
                        if (!entry.possessions.containsKey(deviceId)) {
                            ImageLedgerService.markPossession(groupId, entry.receiptId, deviceId)
                        }
                        ImageLedgerService.pruneCheckTransaction(groupId, entry.receiptId, photoCapableDeviceIds)
                    }
                }
                // Intentionally no "uploadedAt > 0 && !hasLocalFile" branch —
                // downloads for receipts we reference are handled by processRecovery
                // (with the 3-retry-then-recovery-request self-healing logic).
                // Ledger entries for receipts we don't reference are ignored.
            }
        }

        prefs.edit().putLong(KEY_LAST_SEEN_FLAG_CLOCK, remoteFlagClock).apply()
        return transactions to ledger
    }

    private suspend fun handleReuploadRequest(
        entry: ImageLedgerEntry,
        photoCapableDeviceIds: Set<String>,
        allDevices: List<DeviceInfo>
    ) {
        // Candidates: photo-capable devices that have the file
        val candidatesWithFile = entry.possessions.keys
            .filter { it in photoCapableDeviceIds }
            .toMutableSet()
        candidatesWithFile.add(deviceId) // we have the file

        val selectedBuilder = selectBuilder(entry.receiptId, candidatesWithFile, allDevices)
        if (selectedBuilder != deviceId) {
            if (!entry.possessions.containsKey(deviceId)) {
                ImageLedgerService.markPossession(groupId, entry.receiptId, deviceId)
            }
            return
        }

        // Check own queue first, cooldown
        val pending = ReceiptManager.loadPendingUploads(context)
        if (pending.isNotEmpty()) {
            syncLog("Receipt ${entry.receiptId}: own queue not empty, skipping re-upload")
            return
        }
        val lastUpload = prefs.getLong(KEY_LAST_UPLOAD_TIME, 0L)
        if (System.currentTimeMillis() - lastUpload < STALE_ASSIGNMENT_MS) {
            syncLog("Receipt ${entry.receiptId}: cooldown active, skipping re-upload")
            return
        }

        val claimed = ImageLedgerService.claimUploadAssignment(
            groupId, entry.receiptId, deviceId,
            entry.uploadAssignee, entry.assignedAt
        )
        if (!claimed) return

        val encrypted = ReceiptManager.encryptForUpload(context, entry.receiptId, encryptionKey)
        if (encrypted != null) {
            val startNanos = System.nanoTime()
            val uploaded = ImageLedgerService.uploadToCloud(groupId, entry.receiptId, encrypted)
            if (uploaded) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                if (elapsedMs > 0) {
                    val bps = (encrypted.size.toLong() * 1000) / elapsedMs
                    prefs.edit()
                        .putLong(KEY_LAST_UPLOAD_SPEED_BPS, bps)
                        .putLong(KEY_LAST_SPEED_MEASURED_AT, System.currentTimeMillis())
                        .apply()
                }
                ImageLedgerService.markReuploadComplete(groupId, entry.receiptId, deviceId)
                prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()).apply()
                syncLog("Receipt ${entry.receiptId}: re-upload complete")
            }
        }
    }

    /**
     * Download a single receipt with 3-retry-then-recovery-request semantics.
     * Shared by `processRecovery`, `MainViewModel.onBatchChanged` fast-path, and
     * the foreground retry coroutine.
     *
     * Returns true on successful download + save. Returns false for transient
     * failures (caller may retry later); on the 3rd real failure the ledger
     * entry gets replaced with a recovery request and a re-uploader takes over.
     *
     * If no ledger entry exists at all (possible race on a freshly-added
     * transaction whose originator hasn't uploaded yet), caller should just
     * wait and retry later — we don't create a recovery request in that case,
     * since the originator may still be queued to upload.
     */
    suspend fun downloadReceiptWithRetry(
        receiptId: String,
        photoCapableDeviceIds: Set<String>
    ): Boolean {
        val data = ImageLedgerService.downloadFromCloud(groupId, receiptId)
        if (data != null) {
            val saved = ReceiptManager.decryptAndSave(context, receiptId, data, encryptionKey)
            if (saved) {
                ImageLedgerService.markPossession(groupId, receiptId, deviceId)
                ImageLedgerService.pruneCheckTransaction(groupId, receiptId, photoCapableDeviceIds)
                // Record the contentVersion we just downloaded so later
                // rotation-detection compares correctly.
                val entry = ImageLedgerService.getLedgerEntry(groupId, receiptId)
                if (entry != null) {
                    setLocalContentVersion(receiptId, entry.contentVersion)
                }
                prefs.edit().remove(KEY_RETRY_PREFIX + receiptId).apply()
                syncLog("Receipt $receiptId: downloaded + saved")
                return true
            }
        }

        // Download failed — decide whether to retry or create a recovery request
        val existing = ImageLedgerService.getLedgerEntry(groupId, receiptId)
        if (existing == null) {
            // No ledger entry — originator probably hasn't uploaded yet. Wait.
            syncLog("Receipt $receiptId: no ledger entry yet, will retry later")
            return false
        }
        if (existing.uploadedAt == 0L) {
            // Already a recovery request — another device's in-flight re-upload will land.
            return false
        }

        // Ledger claims uploaded but download failed — track retries
        val retryKey = KEY_RETRY_PREFIX + receiptId
        val retries = prefs.getInt(retryKey, 0) + 1
        prefs.edit().putInt(retryKey, retries).apply()

        if (retries >= MAX_DOWNLOAD_RETRIES) {
            syncLog("Receipt $receiptId: $retries download failures, converting to recovery request")
            // Atomic replacement inside a Firestore transaction: preserves
            // contentVersion + createdAt, resets to recovery-request state,
            // bumps flag clock. Avoids the delete-then-create race window
            // where a concurrent re-uploader could duplicate the entry or
            // reset the version counter.
            ImageLedgerService.resetEntryToRecoveryRequest(
                groupId, receiptId, deviceId,
                fallbackContentVersion = existing.contentVersion
            )
            prefs.edit().remove(retryKey).apply()
        } else {
            syncLog("Receipt $receiptId: download failed (retry $retries/$MAX_DOWNLOAD_RETRIES)")
        }
        return false
    }

    // ── Local content-version tracking (for rotation detection) ──

    /**
     * Record the contentVersion of a receipt we've just saved locally. On
     * future sync cycles, if the ledger's contentVersion exceeds this value,
     * we know our local copy is stale and needs re-download.
     */
    fun setLocalContentVersion(receiptId: String, version: Long) {
        prefs.edit().putLong(KEY_CONTENT_VERSION_PREFIX + receiptId, version).apply()
    }

    fun getLocalContentVersion(receiptId: String): Long =
        prefs.getLong(KEY_CONTENT_VERSION_PREFIX + receiptId, 0L)

    fun clearLocalContentVersion(receiptId: String) {
        prefs.edit().remove(KEY_CONTENT_VERSION_PREFIX + receiptId).apply()
    }

    // ── Step 3: Recovery (with snapshot support + batch cap) ─────

    private suspend fun processRecovery(
        transactions: MutableList<Transaction>,
        photoCapableDeviceIds: Set<String>,
        allDevices: List<DeviceInfo>
    ): MutableList<Transaction> {
        val allReceiptIds = ReceiptManager.collectAllReceiptIds(transactions)
        val pending = ReceiptManager.loadPendingUploads(context)

        val missingIds = allReceiptIds.filter { receiptId ->
            !ReceiptManager.hasLocalFile(context, receiptId) && receiptId !in pending
        }

        if (missingIds.isEmpty()) return transactions

        // If many missing, try snapshot path first
        if (missingIds.size > SNAPSHOT_THRESHOLD) {
            val snapshotEntry = ImageLedgerService.getSnapshotEntry(groupId)
            when {
                snapshotEntry == null -> {
                    ImageLedgerService.createSnapshotRequest(groupId, deviceId)
                    syncLog("Receipt recovery: ${missingIds.size} missing, requested snapshot")
                    return transactions
                }
                snapshotEntry.status in listOf("requested", "building", "uploading") -> {
                    syncLog("Receipt recovery: snapshot ${snapshotEntry.status}, waiting")
                    return transactions
                }
                snapshotEntry.status == "ready" -> {
                    processSnapshotDownload(snapshotEntry)
                    return transactions
                }
                snapshotEntry.status == "error" -> {
                    val now = System.currentTimeMillis()
                    if (now - snapshotEntry.lastProgressUpdate > SNAPSHOT_STALE_MS) {
                        // Check if only one device — fall through to batch
                        if (photoCapableDeviceIds.size <= 1) {
                            syncLog("Receipt recovery: snapshot failed, only device — falling back to batch")
                            ImageLedgerService.deleteSnapshotEntry(groupId)
                        } else {
                            syncLog("Receipt recovery: snapshot error stale, resetting to requested")
                            ImageLedgerService.updateSnapshotStatus(groupId, "requested")
                            return transactions
                        }
                    } else {
                        syncLog("Receipt recovery: snapshot error, waiting for retry")
                        return transactions
                    }
                }
            }
        }

        // Batch recovery with 50/cycle cap, 5 concurrent downloads
        val capped = missingIds.take(BATCH_RECOVERY_CAP)
        if (capped.size < missingIds.size) {
            syncLog("Receipt recovery: capped at $BATCH_RECOVERY_CAP, ${missingIds.size - capped.size} remaining")
        }

        capped.chunked(5).forEach { chunk ->
            coroutineScope {
                chunk.map { receiptId ->
                    async {
                        // downloadReceiptWithRetry handles: download, save, mark possession,
                        // prune check, retry counter, and 3rd-failure → recovery request.
                        // If there's no ledger entry at all (originator may not have
                        // uploaded yet), it returns false without creating a request —
                        // we only want to issue recovery requests when someone claims to
                        // have uploaded already.
                        val ok = downloadReceiptWithRetry(receiptId, photoCapableDeviceIds)
                        if (!ok) {
                            // If no ledger entry exists, this receipt is brand-new and
                            // the originator's upload is likely still queued. If the
                            // ledger entry does exist but download failed, the retry
                            // counter in downloadReceiptWithRetry handles escalation.
                            val existing = ImageLedgerService.getLedgerEntry(groupId, receiptId)
                            if (existing == null) {
                                // Entry truly missing — this is a "we reference a
                                // photo that nobody has uploaded yet" case. Create a
                                // recovery request so peers help (originator will
                                // notice if asked and re-upload from its queue).
                                val created = ImageLedgerService.createRecoveryRequest(groupId, receiptId, deviceId)
                                if (created) {
                                    ImageLedgerService.markNonPossession(groupId, receiptId, deviceId)
                                    syncLog("Receipt $receiptId: no ledger entry, created recovery request")
                                }
                            }
                        }
                    }
                }
            }
        }

        return transactions
    }

    // ── Step 3b: Snapshot Lifecycle ──────────────────────────────

    private suspend fun processSnapshotLifecycle(
        transactions: List<Transaction>,
        photoCapableDeviceIds: Set<String>,
        allDevices: List<DeviceInfo>
    ) {
        val entry = ImageLedgerService.getSnapshotEntry(groupId) ?: return
        val now = System.currentTimeMillis()

        when (entry.status) {
            "requested" -> {
                // Am I the best builder? (exclude the requester from candidates)
                val candidates = photoCapableDeviceIds - entry.requestedBy
                if (candidates.isEmpty() && deviceId != entry.requestedBy) return
                val effectiveCandidates = if (candidates.isNotEmpty()) candidates
                    else photoCapableDeviceIds // requester builds own snapshot as last resort
                val builder = selectBuilder("__snapshot__", effectiveCandidates, allDevices)
                if (builder == deviceId) {
                    val claimed = ImageLedgerService.claimSnapshotBuilder(
                        groupId, deviceId, entry.builderId, entry.builderAssignedAt
                    )
                    if (claimed) buildSnapshot(transactions)
                }
            }
            "building", "uploading" -> {
                if (entry.builderId == deviceId) {
                    // We were building — app may have restarted. Rebuild.
                    buildSnapshot(transactions)
                } else if (now - entry.lastProgressUpdate > SNAPSHOT_STALE_MS) {
                    syncLog("Snapshot: builder stale for 2h, taking over")
                    val claimed = ImageLedgerService.claimSnapshotBuilder(
                        groupId, deviceId, entry.builderId, entry.builderAssignedAt
                    )
                    if (claimed) buildSnapshot(transactions)
                }
            }
            "ready" -> {
                // Cleanup: if requester has consumed + grace period
                if (entry.consumedBy.containsKey(entry.requestedBy) &&
                    now - entry.readyAt > SNAPSHOT_GRACE_PERIOD_MS
                ) {
                    ImageLedgerService.deleteSnapshotArchive(groupId)
                    ImageLedgerService.deleteSnapshotEntry(groupId)
                    syncLog("Snapshot: cleaned up after consumption")
                }
            }
            "error" -> {
                if (now - entry.lastProgressUpdate > SNAPSHOT_STALE_MS) {
                    if (photoCapableDeviceIds.size <= 1) {
                        ImageLedgerService.deleteSnapshotEntry(groupId)
                        syncLog("Snapshot: single device failed, removed request (batch fallback)")
                    } else {
                        ImageLedgerService.updateSnapshotStatus(groupId, "requested")
                        syncLog("Snapshot: error stale, reset to requested for another device")
                    }
                }
            }
        }
    }

    private suspend fun buildSnapshot(transactions: List<Transaction>) {
        syncLog("Snapshot: building archive")
        ImageLedgerService.updateSnapshotStatus(groupId, "building", progressPercent = 0)

        val allReceiptIds = ReceiptManager.collectAllReceiptIds(transactions)
        val localReceipts = allReceiptIds.filter { ReceiptManager.hasLocalFile(context, it) }

        if (localReceipts.isEmpty()) {
            ImageLedgerService.updateSnapshotStatus(
                groupId, "error", errorMessage = "No local receipts available"
            )
            return
        }

        val tempEntries = File(context.cacheDir, "snapshot_entries.tmp")
        val archiveFile = File(context.cacheDir, "snapshot_archive.bin")
        try {
            // Write encrypted entries to temp file, build manifest
            val manifestEntries = JSONArray()
            var currentOffset = 0L

            tempEntries.outputStream().buffered().use { out ->
                for ((index, receiptId) in localReceipts.withIndex()) {
                    val encrypted = ReceiptManager.encryptForUpload(context, receiptId, encryptionKey)
                        ?: continue

                    val entryJson = JSONObject().apply {
                        put("receiptId", receiptId)
                        put("offset", currentOffset)
                        put("length", encrypted.size)
                    }
                    manifestEntries.put(entryJson)
                    out.write(encrypted)
                    currentOffset += encrypted.size

                    // Progress update every 10%
                    val pct = ((index + 1) * 100) / localReceipts.size
                    if (index > 0 && pct % 10 == 0 && pct != ((index) * 100) / localReceipts.size) {
                        ImageLedgerService.updateSnapshotStatus(groupId, "building", progressPercent = pct)
                    }
                }
            }

            // Build encrypted manifest
            val manifest = JSONObject().apply {
                put("builtAt", System.currentTimeMillis())
                put("builtBy", deviceId)
                put("receiptCount", manifestEntries.length())
                put("entries", manifestEntries)
            }
            val manifestBytes = CryptoHelper.encryptWithKey(
                manifest.toString().toByteArray(Charsets.UTF_8), encryptionKey
            )

            // Assemble final archive: header + manifest + entries
            archiveFile.outputStream().buffered().use { out ->
                out.write(SNAPSHOT_MAGIC)
                out.write(intToBytes(SNAPSHOT_FORMAT_VERSION))
                out.write(intToBytes(manifestBytes.size))
                out.write(manifestBytes)
                tempEntries.inputStream().buffered().use { it.copyTo(out) }
            }
            tempEntries.delete()

            // Upload
            ImageLedgerService.updateSnapshotStatus(groupId, "uploading", progressPercent = 0)
            val uploaded = ImageLedgerService.uploadSnapshotArchive(groupId, archiveFile)
            archiveFile.delete()

            if (uploaded) {
                ImageLedgerService.updateSnapshotStatus(
                    groupId, "ready",
                    snapshotReceiptCount = manifestEntries.length(),
                    readyAt = System.currentTimeMillis()
                )
                syncLog("Snapshot: uploaded (${manifestEntries.length()} receipts)")
            } else {
                ImageLedgerService.updateSnapshotStatus(
                    groupId, "error", errorMessage = "Upload failed"
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Worker was cancelled (Doze, App Standby, FCM time budget). Clean
            // up partial files but DO NOT mark the snapshot as "error" — that
            // would lock the lifecycle into the 2h staleness gate before any
            // device retries. Leave status as "building" so a later cycle (or
            // another device) sees it stale by `lastProgressUpdate` and can
            // claim. Rethrow so the parent (runFullSyncInline) sees the cancel
            // and returns `false`, triggering the WorkManager fallback.
            tempEntries.delete()
            archiveFile.delete()
            syncLog("Snapshot: build CANCELLED (${ce.message}) — partial files cleaned, status left as building")
            throw ce
        } catch (e: Exception) {
            tempEntries.delete()
            archiveFile.delete()
            ImageLedgerService.updateSnapshotStatus(
                groupId, "error", errorMessage = "Build failed: ${e.message}"
            )
            syncLog("Snapshot: build failed: ${e.message}")
        }
    }

    private suspend fun processSnapshotDownload(entry: SnapshotLedgerEntry) {
        syncLog("Snapshot: downloading archive")
        val archiveFile = File(context.cacheDir, "snapshot_download.bin")

        try {
            val downloaded = ImageLedgerService.downloadSnapshotArchive(groupId, archiveFile)
            if (!downloaded) {
                syncLog("Snapshot: download failed")
                return
            }

            archiveFile.inputStream().buffered().use { inp ->
                // Read header
                val dis = java.io.DataInputStream(inp)
                val magic = ByteArray(4)
                dis.readFully(magic)
                if (!magic.contentEquals(SNAPSHOT_MAGIC)) {
                    throw IllegalStateException("Invalid snapshot magic")
                }
                // Reject unsupported format versions. A future build that
                // writes v2 would be silently mis-parsed by this reader,
                // potentially saving garbage bytes as local receipts. Fall
                // back to batch recovery (one file at a time) instead.
                val version = readInt(inp)
                if (version != SNAPSHOT_FORMAT_VERSION) {
                    throw IllegalStateException(
                        "Unsupported snapshot version $version (this build reads v$SNAPSHOT_FORMAT_VERSION)"
                    )
                }
                // Bounds-check the manifest length before allocating. A
                // corrupted or maliciously-crafted archive could declare
                // Int.MAX_VALUE and OOM the process on the allocation.
                val manifestLen = readInt(inp)
                if (manifestLen <= 0 || manifestLen > SNAPSHOT_MAX_MANIFEST_BYTES) {
                    throw IllegalStateException(
                        "Snapshot manifest length $manifestLen out of allowed range [1, $SNAPSHOT_MAX_MANIFEST_BYTES]"
                    )
                }
                val manifestEncrypted = ByteArray(manifestLen)
                dis.readFully(manifestEncrypted)
                val manifestJson = String(
                    CryptoHelper.decryptWithKey(manifestEncrypted, encryptionKey),
                    Charsets.UTF_8
                )
                val manifest = JSONObject(manifestJson)
                val entries = manifest.getJSONArray("entries")
                var extracted = 0

                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    val receiptId = e.getString("receiptId")
                    val length = e.getInt("length")

                    // Per-entry bounds: ≤10 MB. Typical encrypted receipt is
                    // ~200 KB; 10 MB is ~50× headroom. A larger value
                    // indicates a malformed or hostile archive.
                    if (length <= 0 || length > SNAPSHOT_MAX_ENTRY_BYTES) {
                        throw IllegalStateException(
                            "Snapshot entry[$i] length $length out of allowed range [1, $SNAPSHOT_MAX_ENTRY_BYTES]"
                        )
                    }

                    if (ReceiptManager.hasLocalFile(context, receiptId)) {
                        // Skip — already have this file. Must still read past it.
                        var skipped = 0L
                        while (skipped < length) {
                            val n = inp.skip((length - skipped).toLong())
                            if (n <= 0) { inp.read(); skipped++ } // fallback: read-and-discard
                            else skipped += n
                        }
                        continue
                    }

                    val encrypted = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = inp.read(encrypted, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    if (ReceiptManager.decryptAndSave(context, receiptId, encrypted, encryptionKey)) {
                        extracted++
                    }
                }

                syncLog("Snapshot: extracted $extracted receipts")
            }

            ImageLedgerService.markSnapshotConsumed(groupId, deviceId)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            syncLog("Snapshot: download CANCELLED (${ce.message})")
            throw ce
        } catch (e: Exception) {
            syncLog("Snapshot: extraction failed: ${e.message}")
        } finally {
            archiveFile.delete()
        }
    }

    // ── Helper: Clear receipt slot when photo is confirmed lost ──

    private fun clearLostReceiptSlot(transactions: MutableList<Transaction>, receiptId: String) {
        for (i in transactions.indices) {
            val t = transactions[i]
            val updated = ReceiptManager.clearReceiptSlot(t, receiptId)
            if (updated !== t) {
                transactions[i] = updated
                // Persist + push to Firestore
                try {
                    TransactionRepository.save(context, transactions.toList())
                    if (SyncWriteHelper.isInitialized()) {
                        SyncWriteHelper.pushTransaction(updated)
                    }
                } catch (e: Exception) {
                    syncLog("Failed to persist cleared receipt slot for $receiptId: ${e.message}")
                }
                return
            }
        }
    }

    // ── Step 4: 14-Day Stale Pruning ────────────────────────────

    private suspend fun processStalePruning(
        photoCapableDeviceIds: Set<String>,
        allDevices: List<DeviceInfo>,
        cachedLedger: List<ImageLedgerEntry>? = null
    ) {
        val now = System.currentTimeMillis()

        // Skip if we checked within the last 24 hours
        val lastLocalPrune = prefs.getLong("lastStalePruneRun", 0L)
        if (now - lastLocalPrune < 24 * 60 * 60 * 1000L) return

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val cleanupState = ImageLedgerService.getCleanupState(groupId)

        if (cleanupState.lastCleanupDate == today) {
            // Another device already cleaned up today
            prefs.edit().putLong("lastStalePruneRun", now).apply()
            return
        }

        // Perform cleanup (idempotent — safe if two devices race)
        syncLog("Receipt sync: performing 14-day stale cleanup")
        val ledger = cachedLedger ?: ImageLedgerService.getFullLedger(groupId)
        var pruned = 0

        for (entry in ledger) {
            if (now - entry.createdAt > FOURTEEN_DAYS_MS) {
                ImageLedgerService.deleteFromCloud(groupId, entry.receiptId)
                ImageLedgerService.deleteLedgerEntry(groupId, entry.receiptId)
                pruned++
            }
        }

        if (pruned > 0) {
            ImageLedgerService.bumpFlagClock(groupId)
            syncLog("Receipt sync: pruned $pruned stale ledger entries")
        }

        // Mark done in Firestore (so other devices skip) and locally
        try {
            ImageLedgerService.markCleanupDone(groupId, today)
        } catch (e: Exception) {
            syncLog("Receipt sync: failed to mark cleanup done: ${e.message}")
        }

        // Clean up stale snapshot archives (>7 days in any state)
        try {
            val snapshotEntry = ImageLedgerService.getSnapshotEntry(groupId)
            if (snapshotEntry != null && now - snapshotEntry.requestedAt > 7L * 24 * 60 * 60 * 1000) {
                ImageLedgerService.deleteSnapshotArchive(groupId)
                ImageLedgerService.deleteSnapshotEntry(groupId)
                syncLog("Receipt sync: cleaned up stale snapshot (>7 days)")
            }
        } catch (e: Exception) {
            syncLog("Receipt sync: snapshot cleanup failed: ${e.message}")
        }

        // Clean up stale join snapshot (>7 days)
        try {
            val joinSnapshotAge = FirestoreService.getJoinSnapshotAge(groupId)
            if (joinSnapshotAge > 7L * 24 * 60 * 60 * 1000) {
                ImageLedgerService.deleteJoinSnapshot(groupId)
                FirestoreService.clearJoinSnapshotTimestamp(groupId)
                syncLog("Receipt sync: cleaned up stale join snapshot (>7 days)")
            }
        } catch (e: Exception) {
            syncLog("Receipt sync: join snapshot cleanup failed: ${e.message}")
        }

        prefs.edit().putLong("lastStalePruneRun", now).apply()
    }

    // ── Builder Selection (speed priority + hash fallback) ──────

    /**
     * Select the best device for a task. Prefers fastest uploader
     * (measured within 24h), falls back to hash-based scoring.
     */
    private fun selectBuilder(
        taskId: String,
        candidates: Set<String>,
        allDevices: List<DeviceInfo>
    ): String? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val now = System.currentTimeMillis()
        val candidateRecords = allDevices.filter { it.deviceId in candidates && it.photoCapable }

        // Prefer devices with recent speed data
        val withSpeed = candidateRecords.filter {
            it.uploadSpeedBps > 0 && (now - it.uploadSpeedMeasuredAt) < SPEED_STALENESS_MS
        }

        if (withSpeed.isNotEmpty()) {
            // Pick fastest, tie-break by most recent measurement
            return withSpeed.maxWithOrNull(compareBy({ it.uploadSpeedBps }, { it.uploadSpeedMeasuredAt }))?.deviceId
        }

        // Fallback: hash-based scoring
        return candidates.maxByOrNull { hashScore(taskId, it) }
    }

    private fun hashScore(taskId: String, deviceId: String): Int {
        val combined = "$taskId$deviceId"
        return abs(combined.hashCode()) % 1000
    }

    // ── Binary Helpers ──────────────────────────────────────────

    private fun intToBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

    private fun readInt(inp: InputStream): Int {
        val buf = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = inp.read(buf, read, 4 - read)
            if (n < 0) throw java.io.EOFException("Unexpected end of stream reading int")
            read += n
        }
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
    }
}

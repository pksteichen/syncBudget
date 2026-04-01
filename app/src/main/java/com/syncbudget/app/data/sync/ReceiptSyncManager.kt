package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Log
import com.syncbudget.app.data.CryptoHelper
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
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
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val SPEED_STALENESS_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val SNAPSHOT_THRESHOLD = 50
        private const val SNAPSHOT_STALE_MS = 2 * 60 * 60 * 1000L    // 2 hours
        private const val BATCH_RECOVERY_CAP = 50
        private const val SNAPSHOT_GRACE_PERIOD_MS = 5 * 60 * 1000L   // 5 min before cleanup
        private val SNAPSHOT_MAGIC = byteArrayOf(0x53, 0x4E, 0x41, 0x50) // "SNAP"
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

        // Step 1: Process pending uploads (upload-first, then create ledger)
        txns = processPendingUploads(txns)

        // Step 2: Check flag clock and handle ledger-driven operations
        txns = processLedgerOperations(txns, photoCapableDeviceIds, allDevices)

        // Step 3: Handle recovery for missing local files (with snapshot support)
        txns = processRecovery(txns, photoCapableDeviceIds, allDevices)

        // Step 3b: Process snapshot lifecycle (build/download if requested)
        processSnapshotLifecycle(txns, photoCapableDeviceIds, allDevices)

        // Step 4: Check for stale pruning duty (14-day cleanup)
        processStalePruning(photoCapableDeviceIds, allDevices)

        return txns
    }

    // ── Step 1: Process Pending Uploads ─────────────────────────

    private suspend fun processPendingUploads(transactions: MutableList<Transaction>): MutableList<Transaction> {
        val pending = ReceiptManager.loadPendingUploads(context)
        if (pending.isEmpty()) return transactions

        syncLog("Receipt sync: ${pending.size} pending uploads")

        val completed = mutableSetOf<String>()
        for (receiptId in pending.toList()) {
            val encrypted = ReceiptManager.encryptForUpload(context, receiptId, encryptionKey)
            if (encrypted == null) {
                syncLog("Receipt $receiptId: no local file, removing from queue")
                completed.add(receiptId)
                continue
            }

            val startNanos = System.nanoTime()
            val uploaded = ImageLedgerService.uploadToCloud(groupId, receiptId, encrypted)
            if (uploaded) {
                // Measure upload speed piggybacked on this upload
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                if (elapsedMs > 0) {
                    val bps = (encrypted.size.toLong() * 1000) / elapsedMs
                    prefs.edit()
                        .putLong(KEY_LAST_UPLOAD_SPEED_BPS, bps)
                        .putLong(KEY_LAST_SPEED_MEASURED_AT, System.currentTimeMillis())
                        .apply()
                }

                val ledgerCreated = ImageLedgerService.createLedgerEntry(groupId, receiptId, deviceId)
                if (ledgerCreated) {
                    syncLog("Receipt $receiptId: uploaded + ledger created")
                    completed.add(receiptId)
                    prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()).apply()
                } else {
                    syncLog("Receipt $receiptId: uploaded but ledger creation failed, will retry")
                }
            } else {
                syncLog("Receipt $receiptId: upload failed (${ImageLedgerService.lastUploadError}), will retry next sync")
            }
        }

        if (completed.isNotEmpty()) {
            val remaining = pending - completed
            ReceiptManager.savePendingUploads(context, remaining)
        }

        return transactions
    }

    // ── Step 2: Ledger-Driven Operations ────────────────────────

    private suspend fun processLedgerOperations(
        transactions: MutableList<Transaction>,
        photoCapableDeviceIds: Set<String>,
        allDevices: List<DeviceInfo>
    ): MutableList<Transaction> {
        val remoteFlagClock = ImageLedgerService.getFlagClock(groupId)
        val localFlagClock = prefs.getLong(KEY_LAST_SEEN_FLAG_CLOCK, 0L)

        if (remoteFlagClock <= localFlagClock) return transactions

        syncLog("Receipt sync: flag clock changed ($localFlagClock -> $remoteFlagClock), pulling ledger")
        val ledger = ImageLedgerService.getFullLedger(groupId)

        for (entry in ledger) {
            when {
                entry.uploadedAt == 0L && ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    handleReuploadRequest(entry, photoCapableDeviceIds, allDevices)
                }
                entry.uploadedAt == 0L && !ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    // Recovery request and we don't have the file — mark non-possession
                    if (entry.possessions[deviceId] != false) {
                        ImageLedgerService.markNonPossession(groupId, entry.receiptId, deviceId)
                    }
                    // Check if all devices have reported non-possession (photo confirmed lost)
                    val lost = ImageLedgerService.checkPhotoLost(groupId, entry.receiptId, photoCapableDeviceIds)
                    if (lost) {
                        clearLostReceiptSlot(transactions, entry.receiptId)
                        syncLog("Receipt ${entry.receiptId}: confirmed lost, cleared slot")
                    }
                }
                entry.uploadedAt > 0L && !ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    handleDownload(entry, photoCapableDeviceIds)
                }
                entry.uploadedAt > 0L && ReceiptManager.hasLocalFile(context, entry.receiptId) -> {
                    if (!entry.possessions.containsKey(deviceId)) {
                        ImageLedgerService.markPossession(groupId, entry.receiptId, deviceId)
                    }
                    ImageLedgerService.pruneCheckTransaction(groupId, entry.receiptId, photoCapableDeviceIds)
                }
            }
        }

        prefs.edit().putLong(KEY_LAST_SEEN_FLAG_CLOCK, remoteFlagClock).apply()
        return transactions
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
                ImageLedgerService.markReuploadComplete(groupId, entry.receiptId)
                prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()).apply()
                syncLog("Receipt ${entry.receiptId}: re-upload complete")
            }
        }
    }

    private suspend fun handleDownload(entry: ImageLedgerEntry, photoCapableDeviceIds: Set<String>) {
        val data = ImageLedgerService.downloadFromCloud(groupId, entry.receiptId)
        if (data != null) {
            val saved = ReceiptManager.decryptAndSave(context, entry.receiptId, data, encryptionKey)
            if (saved) {
                ImageLedgerService.markPossession(groupId, entry.receiptId, deviceId)
                ImageLedgerService.pruneCheckTransaction(groupId, entry.receiptId, photoCapableDeviceIds)
                prefs.edit().remove(KEY_RETRY_PREFIX + entry.receiptId).apply()
                syncLog("Receipt ${entry.receiptId}: downloaded + saved")
            }
        } else {
            val freshEntry = ImageLedgerService.getLedgerEntry(groupId, entry.receiptId)
            if (freshEntry != null && freshEntry.uploadedAt > 0L) {
                val retryKey = KEY_RETRY_PREFIX + entry.receiptId
                val retries = prefs.getInt(retryKey, 0) + 1
                prefs.edit().putInt(retryKey, retries).apply()

                if (retries >= MAX_DOWNLOAD_RETRIES) {
                    syncLog("Receipt ${entry.receiptId}: $retries download failures, creating new request")
                    ImageLedgerService.deleteLedgerEntry(groupId, entry.receiptId)
                    ImageLedgerService.createRecoveryRequest(groupId, entry.receiptId, deviceId)
                    prefs.edit().remove(retryKey).apply()
                } else {
                    syncLog("Receipt ${entry.receiptId}: download failed (retry $retries/$MAX_DOWNLOAD_RETRIES)")
                }
            }
        }
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
                        val cloudData = ImageLedgerService.downloadFromCloud(groupId, receiptId)
                        if (cloudData != null) {
                            val saved = ReceiptManager.decryptAndSave(context, receiptId, cloudData, encryptionKey)
                            if (saved) {
                                ImageLedgerService.markPossession(groupId, receiptId, deviceId)
                                ImageLedgerService.pruneCheckTransaction(groupId, receiptId, photoCapableDeviceIds)
                                syncLog("Receipt $receiptId: recovered from cloud")
                                return@async
                            }
                        }

                        val existing = ImageLedgerService.getLedgerEntry(groupId, receiptId)
                        if (existing != null) return@async

                        val created = ImageLedgerService.createRecoveryRequest(groupId, receiptId, deviceId)
                        if (created) {
                            // Immediately mark self as non-possession (we're the one requesting)
                            ImageLedgerService.markNonPossession(groupId, receiptId, deviceId)
                            syncLog("Receipt $receiptId: created recovery request")
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
                out.write(intToBytes(1)) // version
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
                val version = readInt(inp)
                val manifestLen = readInt(inp)
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
        allDevices: List<DeviceInfo>
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
        val ledger = ImageLedgerService.getFullLedger(groupId)
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
        inp.read(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
    }
}

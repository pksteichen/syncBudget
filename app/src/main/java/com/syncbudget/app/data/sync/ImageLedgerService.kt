package com.syncbudget.app.data.sync

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Firestore CRUD operations for the image ledger and Cloud Storage
 * upload/download for encrypted receipt photos.
 */
object ImageLedgerService {

    private const val TAG = "ImageLedgerService"
    private const val TIMEOUT_MS = 30_000L

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    // ── Cloud Storage ───────────────────────────────────────────

    /**
     * Upload encrypted receipt bytes to Cloud Storage.
     * Path: groups/{groupId}/receipts/{receiptId}.enc
     */
    /** Last upload error message, readable by callers for diagnostic logging. */
    var lastUploadError: String? = null
        private set

    suspend fun uploadToCloud(groupId: String, receiptId: String, encryptedData: ByteArray): Boolean {
        lastUploadError = null
        return try {
            withTimeout(60_000L) {
                val ref = storage.reference.child("groups/$groupId/receipts/$receiptId.enc")
                ref.putBytes(encryptedData).await()
            }
            true
        } catch (e: Exception) {
            lastUploadError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, "Upload failed for $receiptId: $lastUploadError")
            false
        }
    }

    /**
     * Download encrypted receipt bytes from Cloud Storage.
     * Returns null if not found or on failure.
     */
    suspend fun downloadFromCloud(groupId: String, receiptId: String): ByteArray? {
        return try {
            withTimeout(60_000L) {
                val ref = storage.reference.child("groups/$groupId/receipts/$receiptId.enc")
                // Max 2MB per receipt (generous limit for ~200KB encrypted)
                ref.getBytes(2 * 1024 * 1024).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for $receiptId: ${e.message}")
            null
        }
    }

    /**
     * Check if a receipt file exists in Cloud Storage.
     */
    suspend fun existsInCloud(groupId: String, receiptId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = storage.reference.child("groups/$groupId/receipts/$receiptId.enc")
                ref.metadata.await()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Delete a receipt file from Cloud Storage.
     */
    suspend fun deleteFromCloud(groupId: String, receiptId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = storage.reference.child("groups/$groupId/receipts/$receiptId.enc")
                ref.delete().await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cloud delete failed for $receiptId: ${e.message}")
            false
        }
    }

    /**
     * Delete orphaned Cloud Storage files that have no matching ledger entry.
     * Run by admin on app startup to clean up files left behind when
     * ledger deletion succeeded but cloud deletion failed.
     */
    suspend fun purgeOrphanedCloudFiles(groupId: String): Int {
        return try {
            val storageRef = storage.reference.child("groups/$groupId/receipts")
            val cloudFiles = withTimeout(TIMEOUT_MS) {
                storageRef.listAll().await()
            }
            if (cloudFiles.items.isEmpty()) return 0

            // Get all receipt IDs currently in the ledger
            val ledger = getFullLedger(groupId)
            val ledgerIds = ledger.map { it.receiptId }.toSet()

            var purged = 0
            for (item in cloudFiles.items) {
                // File name is "{receiptId}.enc"
                val receiptId = item.name.removeSuffix(".enc")
                if (receiptId !in ledgerIds) {
                    try {
                        item.delete().await()
                        purged++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete orphaned file ${item.name}: ${e.message}")
                    }
                }
            }
            if (purged > 0) Log.i(TAG, "Purged $purged orphaned Cloud Storage files")
            purged
        } catch (e: Exception) {
            Log.w(TAG, "Orphan scan failed: ${e.message}")
            0
        }
    }

    // ── Image Ledger CRUD ───────────────────────────────────────

    private fun ledgerRef(groupId: String) =
        firestore.collection("groups").document(groupId).collection("imageLedger")

    private fun groupRef(groupId: String) =
        firestore.collection("groups").document(groupId)

    /**
     * Create a ledger entry after successful upload (normal flow).
     * Does NOT bump flag clock — CRDT sync handles notification.
     */
    suspend fun createLedgerEntry(
        groupId: String,
        receiptId: String,
        originatorDeviceId: String
    ): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val data = mapOf(
                    "receiptId" to receiptId,
                    "originatorDeviceId" to originatorDeviceId,
                    "createdAt" to System.currentTimeMillis(),
                    "possessions" to mapOf(originatorDeviceId to true),
                    "uploadAssignee" to null,
                    "assignedAt" to 0L,
                    "uploadedAt" to System.currentTimeMillis()
                )
                ledgerRef(groupId).document(receiptId).set(data).await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Create ledger entry failed for $receiptId: ${e.message}")
            false
        }
    }

    /**
     * Create a recovery request entry (file not in cloud, need re-upload).
     * DOES bump flag clock so other devices see the request.
     */
    suspend fun createRecoveryRequest(
        groupId: String,
        receiptId: String,
        originatorDeviceId: String
    ): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                firestore.runBatch { batch ->
                    val data = mapOf(
                        "receiptId" to receiptId,
                        "originatorDeviceId" to originatorDeviceId,
                        "createdAt" to System.currentTimeMillis(),
                        "possessions" to emptyMap<String, Boolean>(),
                        "uploadAssignee" to null,
                        "assignedAt" to 0L,
                        "uploadedAt" to 0L
                    )
                    batch.set(ledgerRef(groupId).document(receiptId), data)
                    batch.update(groupRef(groupId), "imageLedgerFlagClock", FieldValue.increment(1))
                }.await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Create recovery request failed for $receiptId: ${e.message}")
            false
        }
    }

    /**
     * Mark that this device has the file locally (dot-notation update).
     */
    suspend fun markPossession(groupId: String, receiptId: String, deviceId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                ledgerRef(groupId).document(receiptId)
                    .update("possessions.$deviceId", true)
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Mark possession failed for $receiptId: ${e.message}")
            false
        }
    }

    /**
     * Prune check: if all group devices have the file, delete ledger entry.
     * Returns true if pruned, false otherwise.
     */
    suspend fun pruneCheckTransaction(
        groupId: String,
        receiptId: String,
        allDeviceIds: Set<String>
    ): Boolean {
        return try {
            val allHaveIt = withTimeout(TIMEOUT_MS) {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ledgerRef(groupId).document(receiptId))
                    @Suppress("UNCHECKED_CAST")
                    val possessions = snap.get("possessions") as? Map<String, Any> ?: emptyMap()
                    if (possessions.keys.containsAll(allDeviceIds)) {
                        tx.delete(ledgerRef(groupId).document(receiptId))
                        true
                    } else {
                        false
                    }
                }.await()
            }
            // All devices have the photo — also delete from Cloud Storage
            if (allHaveIt) {
                deleteFromCloud(groupId, receiptId)
            }
            allHaveIt
        } catch (e: Exception) {
            Log.w(TAG, "Prune check failed for $receiptId: ${e.message}")
            false
        }
    }

    /**
     * Read a single ledger entry.
     */
    suspend fun getLedgerEntry(groupId: String, receiptId: String): ImageLedgerEntry? {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snap = ledgerRef(groupId).document(receiptId).get().await()
                if (!snap.exists()) return@withTimeout null
                parseLedgerEntry(snap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get ledger entry failed for $receiptId: ${e.message}")
            null
        }
    }

    /**
     * Read the full image ledger.
     */
    suspend fun getFullLedger(groupId: String): List<ImageLedgerEntry> {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snapshot = ledgerRef(groupId).get().await()
                snapshot.documents.mapNotNull { parseLedgerEntry(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get full ledger failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Delete a ledger entry (for deletion flow or stale pruning).
     */
    suspend fun deleteLedgerEntry(groupId: String, receiptId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                ledgerRef(groupId).document(receiptId).delete().await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Delete ledger entry failed for $receiptId: ${e.message}")
            false
        }
    }

    // ── Flag Clock ──────────────────────────────────────────────

    /**
     * Read the current imageLedgerFlagClock from the group document.
     * Returns 0 if not set.
     */
    suspend fun getFlagClock(groupId: String): Long {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snap = groupRef(groupId).get().await()
                snap.getLong("imageLedgerFlagClock") ?: 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get flag clock failed: ${e.message}")
            0L
        }
    }

    /**
     * Bump the flag clock (increment by 1).
     */
    suspend fun bumpFlagClock(groupId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                groupRef(groupId).update("imageLedgerFlagClock", FieldValue.increment(1)).await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Bump flag clock failed: ${e.message}")
            false
        }
    }

    // ── Upload Assignment (CAS) ─────────────────────────────────

    /**
     * Claim re-upload assignment via CAS transaction.
     * Only succeeds if the current assignee/assignedAt matches expected values.
     */
    suspend fun claimUploadAssignment(
        groupId: String,
        receiptId: String,
        myDeviceId: String,
        expectedAssignee: String?,
        expectedAssignedAt: Long
    ): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ledgerRef(groupId).document(receiptId))
                    val currentAssignee = snap.getString("uploadAssignee")
                    val currentAssignedAt = snap.getLong("assignedAt") ?: 0L

                    if (currentAssignee == expectedAssignee && currentAssignedAt == expectedAssignedAt) {
                        tx.update(
                            ledgerRef(groupId).document(receiptId), mapOf(
                                "uploadAssignee" to myDeviceId,
                                "assignedAt" to System.currentTimeMillis()
                            )
                        )
                        true
                    } else {
                        false
                    }
                }.await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Claim upload assignment failed for $receiptId: ${e.message}")
            false
        }
    }

    /**
     * Mark re-upload complete: set uploadedAt and bump flag clock.
     */
    suspend fun markReuploadComplete(groupId: String, receiptId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                firestore.runBatch { batch ->
                    batch.update(
                        ledgerRef(groupId).document(receiptId),
                        "uploadedAt", System.currentTimeMillis()
                    )
                    batch.update(groupRef(groupId), "imageLedgerFlagClock", FieldValue.increment(1))
                }.await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Mark reupload complete failed for $receiptId: ${e.message}")
            false
        }
    }

    // ── 14-Day Cleanup ──────────────────────────────────────────

    /**
     * Read cleanup assignment fields from group document.
     */
    suspend fun getCleanupState(groupId: String): CleanupState {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snap = groupRef(groupId).get().await()
                CleanupState(
                    assignee = snap.getString("imageCleanupAssignee"),
                    assignedAt = snap.getLong("imageCleanupAssignedAt") ?: 0L,
                    lastCleanupDate = snap.getString("imageLastCleanupDate")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get cleanup state failed: ${e.message}")
            CleanupState()
        }
    }

    /**
     * Claim cleanup duty for today via CAS.
     */
    suspend fun claimCleanupDuty(
        groupId: String,
        myDeviceId: String,
        todayDate: String,
        expectedAssignee: String?,
        expectedAssignedAt: Long
    ): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                firestore.runTransaction { tx ->
                    val snap = tx.get(groupRef(groupId))
                    val currentAssignee = snap.getString("imageCleanupAssignee")
                    val currentAssignedAt = snap.getLong("imageCleanupAssignedAt") ?: 0L

                    if (currentAssignee == expectedAssignee && currentAssignedAt == expectedAssignedAt) {
                        tx.update(
                            groupRef(groupId), mapOf(
                                "imageCleanupAssignee" to myDeviceId,
                                "imageCleanupAssignedAt" to System.currentTimeMillis(),
                                "imageLastCleanupDate" to todayDate
                            )
                        )
                        true
                    } else {
                        false
                    }
                }.await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Claim cleanup duty failed: ${e.message}")
            false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseLedgerEntry(snap: com.google.firebase.firestore.DocumentSnapshot): ImageLedgerEntry? {
        return try {
            ImageLedgerEntry(
                receiptId = snap.getString("receiptId") ?: return null,
                originatorDeviceId = snap.getString("originatorDeviceId") ?: "",
                createdAt = snap.getLong("createdAt") ?: 0L,
                possessions = (snap.get("possessions") as? Map<String, Boolean>) ?: emptyMap(),
                uploadAssignee = snap.getString("uploadAssignee"),
                assignedAt = snap.getLong("assignedAt") ?: 0L,
                uploadedAt = snap.getLong("uploadedAt") ?: 0L
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse ledger entry failed: ${e.message}")
            null
        }
    }

    data class CleanupState(
        val assignee: String? = null,
        val assignedAt: Long = 0L,
        val lastCleanupDate: String? = null
    )

    // ── Snapshot Archive ────────────────────────────────────────

    private const val SNAPSHOT_DOC_ID = "__snapshot_request__"

    suspend fun getSnapshotEntry(groupId: String): SnapshotLedgerEntry? {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snap = ledgerRef(groupId).document(SNAPSHOT_DOC_ID).get().await()
                if (!snap.exists()) return@withTimeout null
                parseSnapshotEntry(snap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get snapshot entry failed: ${e.message}")
            null
        }
    }

    suspend fun createSnapshotRequest(groupId: String, requestedBy: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                firestore.runBatch { batch ->
                    val data = mapOf(
                        "type" to "snapshot_request",
                        "requestedBy" to requestedBy,
                        "requestedAt" to System.currentTimeMillis(),
                        "status" to "requested",
                        "progressPercent" to 0,
                        "lastProgressUpdate" to System.currentTimeMillis(),
                        "snapshotReceiptCount" to 0,
                        "readyAt" to 0L,
                        "consumedBy" to emptyMap<String, Boolean>()
                    )
                    batch.set(ledgerRef(groupId).document(SNAPSHOT_DOC_ID), data)
                    batch.update(groupRef(groupId), "imageLedgerFlagClock", FieldValue.increment(1))
                }.await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Create snapshot request failed: ${e.message}")
            false
        }
    }

    suspend fun claimSnapshotBuilder(
        groupId: String,
        myDeviceId: String,
        expectedBuilder: String?,
        expectedAssignedAt: Long
    ): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ledgerRef(groupId).document(SNAPSHOT_DOC_ID))
                    val currentBuilder = snap.getString("builderId")
                    val currentAssignedAt = snap.getLong("builderAssignedAt") ?: 0L

                    if (currentBuilder == expectedBuilder && currentAssignedAt == expectedAssignedAt) {
                        tx.update(
                            ledgerRef(groupId).document(SNAPSHOT_DOC_ID), mapOf(
                                "builderId" to myDeviceId,
                                "builderAssignedAt" to System.currentTimeMillis(),
                                "lastProgressUpdate" to System.currentTimeMillis()
                            )
                        )
                        true
                    } else {
                        false
                    }
                }.await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Claim snapshot builder failed: ${e.message}")
            false
        }
    }

    suspend fun updateSnapshotStatus(
        groupId: String,
        status: String,
        progressPercent: Int = -1,
        errorMessage: String? = null,
        snapshotReceiptCount: Int = -1,
        readyAt: Long = 0L
    ): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val data = mutableMapOf<String, Any?>(
                    "status" to status,
                    "lastProgressUpdate" to System.currentTimeMillis()
                )
                if (progressPercent >= 0) data["progressPercent"] = progressPercent
                if (errorMessage != null) data["errorMessage"] = errorMessage
                if (snapshotReceiptCount >= 0) data["snapshotReceiptCount"] = snapshotReceiptCount
                if (readyAt > 0L) data["readyAt"] = readyAt
                ledgerRef(groupId).document(SNAPSHOT_DOC_ID)
                    .update(data.filterValues { it != null } as Map<String, Any>)
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Update snapshot status failed: ${e.message}")
            false
        }
    }

    suspend fun markSnapshotConsumed(groupId: String, deviceId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                ledgerRef(groupId).document(SNAPSHOT_DOC_ID)
                    .update("consumedBy.$deviceId", true)
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Mark snapshot consumed failed: ${e.message}")
            false
        }
    }

    suspend fun deleteSnapshotEntry(groupId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                ledgerRef(groupId).document(SNAPSHOT_DOC_ID).delete().await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Delete snapshot entry failed: ${e.message}")
            false
        }
    }

    suspend fun uploadSnapshotArchive(groupId: String, localFile: java.io.File): Boolean {
        return try {
            withTimeout(600_000L) { // 10 min for large archives
                val ref = storage.reference.child("groups/$groupId/photoSnapshot.enc")
                val uri = android.net.Uri.fromFile(localFile)
                ref.putFile(uri).await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot upload failed: ${e.message}")
            false
        }
    }

    suspend fun downloadSnapshotArchive(groupId: String, localFile: java.io.File): Boolean {
        return try {
            withTimeout(600_000L) {
                val ref = storage.reference.child("groups/$groupId/photoSnapshot.enc")
                ref.getFile(localFile).await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot download failed: ${e.message}")
            false
        }
    }

    suspend fun deleteSnapshotArchive(groupId: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = storage.reference.child("groups/$groupId/photoSnapshot.enc")
                ref.delete().await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot archive delete failed: ${e.message}")
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSnapshotEntry(snap: com.google.firebase.firestore.DocumentSnapshot): SnapshotLedgerEntry? {
        return try {
            SnapshotLedgerEntry(
                requestedBy = snap.getString("requestedBy") ?: return null,
                requestedAt = snap.getLong("requestedAt") ?: 0L,
                builderId = snap.getString("builderId"),
                builderAssignedAt = snap.getLong("builderAssignedAt") ?: 0L,
                status = snap.getString("status") ?: "requested",
                progressPercent = (snap.getLong("progressPercent") ?: 0L).toInt(),
                errorMessage = snap.getString("errorMessage"),
                lastProgressUpdate = snap.getLong("lastProgressUpdate") ?: 0L,
                snapshotReceiptCount = (snap.getLong("snapshotReceiptCount") ?: 0L).toInt(),
                readyAt = snap.getLong("readyAt") ?: 0L,
                consumedBy = (snap.get("consumedBy") as? Map<String, Boolean>) ?: emptyMap()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse snapshot entry failed: ${e.message}")
            null
        }
    }
}

package com.syncbudget.app.data.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class FirestoreDelta(
    val version: Long,
    val sourceDeviceId: String,
    val encryptedPayload: String,
    val timestamp: Long
)

data class DeviceRecord(
    val deviceId: String,
    val deviceName: String = "",
    val isAdmin: Boolean = false,
    val lastSyncVersion: Long = 0L,
    val lastSeen: Long = 0L,
    val fingerprintData: String? = null,
    val fingerprintSyncVersion: Long = 0L
)

data class PairingData(
    val groupId: String,
    val encryptedKey: String
)

data class SnapshotRecord(
    val snapshotVersion: Long,
    val createdBy: String,
    val encryptedData: String,
    val timestamp: Long
)

data class AdminClaim(
    val claimantDeviceId: String,
    val claimantName: String,
    val claimedAt: Long,
    val expiresAt: Long,
    val objections: List<String> = emptyList(),
    val status: String = "pending"
)

object FirestoreService {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** Base timeout for individual Firestore operations (ms). */
    private const val OP_TIMEOUT_MS = 30_000L
    /** Extended timeout used after a successful operation proves the
     *  connection is working (just slow). */
    private const val OP_TIMEOUT_EXTENDED_MS = 60_000L

    suspend fun getGroupNextVersion(groupId: String): Long = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups")
            .document(groupId)
            .get()
            .await()
        doc.getLong("nextDeltaVersion") ?: 1L
    }

    suspend fun fetchDeltas(groupId: String, lastSyncVersion: Long, pageSize: Int = 50): List<FirestoreDelta> {
        val allDeltas = mutableListOf<FirestoreDelta>()
        var cursor = lastSyncVersion
        // First page gets base timeout; subsequent pages get extended
        // timeout since a successful first page proves the connection works.
        var timeout = OP_TIMEOUT_MS

        while (true) {
            val snapshot = withTimeout(timeout) {
                db.collection("groups")
                    .document(groupId)
                    .collection("deltas")
                    .whereGreaterThan("version", cursor)
                    .orderBy("version", Query.Direction.ASCENDING)
                    .limit(pageSize.toLong())
                    .get()
                    .await()
            }

            val page = snapshot.documents.map { doc ->
                FirestoreDelta(
                    version = doc.getLong("version") ?: 0L,
                    sourceDeviceId = doc.getString("sourceDeviceId") ?: "",
                    encryptedPayload = doc.getString("encryptedPayload") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
            allDeltas.addAll(page)
            // Page succeeded — extend timeout for next page
            timeout = OP_TIMEOUT_EXTENDED_MS

            if (page.size < pageSize) break
            cursor = page.last().version
        }

        return allDeltas
    }

    /**
     * Delete deltas with version < [belowVersion].  Runs in batches
     * to avoid downloading large payloads — only fetches document IDs.
     * Returns the number of deltas pruned.
     */
    suspend fun pruneDeltas(groupId: String, belowVersion: Long, batchSize: Int = 100): Int {
        var totalPruned = 0
        val deltasRef = db.collection("groups").document(groupId).collection("deltas")
        while (true) {
            val snapshot = withTimeout(OP_TIMEOUT_EXTENDED_MS) {
                deltasRef
                    .whereLessThan("version", belowVersion)
                    .orderBy("version", Query.Direction.ASCENDING)
                    .limit(batchSize.toLong())
                    .get()
                    .await()
            }
            if (snapshot.isEmpty) break
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            withTimeout(OP_TIMEOUT_EXTENDED_MS) { batch.commit().await() }
            totalPruned += snapshot.size()
            if (snapshot.size() < batchSize) break
        }
        return totalPruned
    }

    suspend fun updateDeviceMetadata(
        groupId: String,
        deviceId: String,
        syncVersion: Long,
        fingerprintJson: String? = null
    ) = withTimeout(OP_TIMEOUT_MS) {
        val data = mutableMapOf<String, Any>(
            "lastSyncVersion" to syncVersion,
            "lastSeen" to System.currentTimeMillis()
        )
        if (fingerprintJson != null) {
            data["fingerprintData"] = fingerprintJson
            data["fingerprintSyncVersion"] = syncVersion
        }
        db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun getDeviceRecord(groupId: String, deviceId: String): DeviceRecord? = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .get()
            .await()

        if (!doc.exists()) null
        else if (doc.getBoolean("removed") == true) null  // treat removed as absent
        else DeviceRecord(
            deviceId = doc.getString("deviceId") ?: deviceId,
            deviceName = doc.getString("deviceName") ?: "",
            isAdmin = doc.getBoolean("isAdmin") ?: false,
            lastSyncVersion = doc.getLong("lastSyncVersion") ?: 0L,
            lastSeen = doc.getLong("lastSeen") ?: 0L
        )
    }

    /** Check if the device has been explicitly removed by the admin. */
    suspend fun isDeviceRemoved(groupId: String, deviceId: String): Boolean = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .get()
            .await()
        doc.exists() && doc.getBoolean("removed") == true
    }

    /** Check if the group has been dissolved by the admin. */
    suspend fun isGroupDissolved(groupId: String): Boolean = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups")
            .document(groupId)
            .get()
            .await()
        doc.exists() && doc.getString("status") == "dissolved"
    }

    /** Max encoded chars per snapshot chunk.  600K chars ≈ 600KB, well
     *  under the 1MB Firestore document limit after field overhead. */
    private const val SNAPSHOT_CHUNK_SIZE = 600_000

    suspend fun getSnapshot(groupId: String): SnapshotRecord? {
        val metaDoc = withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("snapshots")
                .document("latest")
                .get()
                .await()
        }

        if (!metaDoc.exists()) return null
        val chunkCount = metaDoc.getLong("chunkCount")?.toInt() ?: 0

        val encryptedData: String
        if (chunkCount > 0) {
            // Multi-chunk snapshot: reassemble from chunk documents.
            // Metadata fetch proved connectivity; use extended timeout
            // for chunk reads on slow connections.
            val sb = StringBuilder()
            for (i in 0 until chunkCount) {
                val chunkDoc = withTimeout(OP_TIMEOUT_EXTENDED_MS) {
                    db.collection("groups")
                        .document(groupId)
                        .collection("snapshots")
                        .document("chunk_$i")
                        .get()
                        .await()
                }
                sb.append(chunkDoc.getString("data") ?: "")
            }
            encryptedData = sb.toString()
        } else {
            // Legacy single-document snapshot (backward compatible)
            encryptedData = metaDoc.getString("encryptedData") ?: ""
        }

        return SnapshotRecord(
            snapshotVersion = metaDoc.getLong("snapshotVersion") ?: 0L,
            createdBy = metaDoc.getString("createdBy") ?: "",
            encryptedData = encryptedData,
            timestamp = metaDoc.getLong("timestamp") ?: 0L
        )
    }

    suspend fun writeSnapshot(
        groupId: String,
        snapshotVersion: Long,
        createdBy: String,
        encryptedData: String
    ) {
        val snapshotRef = db.collection("groups")
            .document(groupId)
            .collection("snapshots")

        if (encryptedData.length <= SNAPSHOT_CHUNK_SIZE) {
            // Small enough for a single document (backward compatible)
            withTimeout(OP_TIMEOUT_MS) {
                snapshotRef.document("latest").set(mapOf(
                    "snapshotVersion" to snapshotVersion,
                    "createdBy" to createdBy,
                    "encryptedData" to encryptedData,
                    "chunkCount" to 0,
                    "timestamp" to System.currentTimeMillis()
                )).await()
            }
        } else {
            // Split into chunks, write chunks first, then metadata
            val chunks = encryptedData.chunked(SNAPSHOT_CHUNK_SIZE)
            for ((i, chunk) in chunks.withIndex()) {
                withTimeout(OP_TIMEOUT_MS) {
                    snapshotRef.document("chunk_$i").set(mapOf(
                        "data" to chunk
                    )).await()
                }
            }
            // Write metadata last so readers don't see partial data
            withTimeout(OP_TIMEOUT_MS) {
                snapshotRef.document("latest").set(mapOf(
                    "snapshotVersion" to snapshotVersion,
                    "createdBy" to createdBy,
                    "chunkCount" to chunks.size,
                    "timestamp" to System.currentTimeMillis()
                )).await()
            }
            // Clean up any stale chunks beyond current count
            for (i in chunks.size until chunks.size + 10) {
                try {
                    val stale = withTimeout(OP_TIMEOUT_MS) {
                        snapshotRef.document("chunk_$i").get().await()
                    }
                    if (stale.exists()) {
                        withTimeout(OP_TIMEOUT_MS) {
                            snapshotRef.document("chunk_$i").delete().await()
                        }
                    } else break
                } catch (_: Exception) { break }
            }
        }
    }

    /**
     * Atomically allocate the next delta version AND write the delta document
     * in a single Firestore transaction. Prevents version gaps if the app
     * crashes between getNextDeltaVersion() and pushDelta().
     */
    suspend fun pushDeltaAtomically(
        groupId: String,
        sourceDeviceId: String,
        encryptedPayload: String,
        timeoutMs: Long = OP_TIMEOUT_MS
    ): Long {
        require(groupId.isNotBlank()) { "Group ID required" }
        require(sourceDeviceId.isNotBlank()) { "Device ID required" }
        val groupRef = db.collection("groups").document(groupId)
        return withTimeout(timeoutMs) {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(groupRef)
                val version = snapshot.getLong("nextDeltaVersion") ?: 1L
                // Increment version counter
                transaction.set(groupRef, mapOf(
                    "nextDeltaVersion" to version + 1,
                    "lastActivity" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
                // Write the delta document in the same transaction
                val deltaRef = groupRef.collection("deltas").document("v$version")
                transaction.set(deltaRef, mapOf(
                    "version" to version,
                    "sourceDeviceId" to sourceDeviceId,
                    "encryptedPayload" to encryptedPayload,
                    "timestamp" to System.currentTimeMillis()
                ))
                version
            }.await()
        }
    }

    suspend fun updateGroupActivity(groupId: String) = withTimeout(OP_TIMEOUT_MS) {
        db.collection("groups").document(groupId)
            .set(mapOf("lastActivity" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    suspend fun createPairingCode(groupId: String, code: String, encryptedKey: String, expiresAt: Long) {
        require(groupId.matches(Regex("^[a-f0-9]{12}$"))) { "Invalid group ID format" }
        require(code.matches(Regex("^[A-Z2-9]{6}$"))) { "Invalid pairing code format" }
        require(encryptedKey.isNotBlank()) { "Encryption key required" }
        require(expiresAt > System.currentTimeMillis()) { "Expiry must be in the future" }
        val data = mapOf(
            "groupId" to groupId,
            "encryptedKey" to encryptedKey,
            "expiresAt" to expiresAt,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("pairing_codes")
            .document(code)
            .set(data)
            .await()
    }

    suspend fun redeemPairingCode(code: String): PairingData? {
        val normalized = code.uppercase().trim()
        if (!normalized.matches(Regex("^[A-Z2-9]{6}$"))) return null
        val doc = db.collection("pairing_codes")
            .document(normalized)
            .get()
            .await()

        if (!doc.exists()) return null

        val expiresAt = doc.getLong("expiresAt") ?: 0L
        if (System.currentTimeMillis() > expiresAt) return null

        val groupId = doc.getString("groupId") ?: return null
        val key = doc.getString("encryptedKey") ?: return null

        // Delete the code after redemption (one-time use)
        db.collection("pairing_codes").document(normalized).delete().await()

        return PairingData(groupId, key)
    }

    suspend fun getDevices(groupId: String): List<DeviceRecord> = withTimeout(OP_TIMEOUT_MS) {
        val snapshot = db.collection("groups")
            .document(groupId)
            .collection("devices")
            .get()
            .await()

        snapshot.documents
            .filter { doc -> doc.getBoolean("removed") != true }
            .map { doc ->
                DeviceRecord(
                    deviceId = doc.getString("deviceId") ?: doc.id,
                    deviceName = doc.getString("deviceName") ?: "",
                    isAdmin = doc.getBoolean("isAdmin") ?: false,
                    lastSyncVersion = doc.getLong("lastSyncVersion") ?: 0L,
                    lastSeen = doc.getLong("lastSeen") ?: 0L,
                    fingerprintData = doc.getString("fingerprintData"),
                    fingerprintSyncVersion = doc.getLong("fingerprintSyncVersion") ?: 0L
                )
            }
    }

    suspend fun updateDeviceName(groupId: String, deviceId: String, newName: String) {
        db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .set(mapOf("deviceName" to newName), SetOptions.merge())
            .await()
    }

    suspend fun removeDevice(groupId: String, deviceId: String) {
        // Mark as removed rather than deleting — gives the non-admin an
        // affirmative signal so it can auto-leave reliably instead of
        // guessing from document absence (which has false positives from
        // Firestore cache staleness and network issues).
        db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .set(mapOf("removed" to true), SetOptions.merge())
            .await()
    }

    suspend fun registerDevice(groupId: String, deviceId: String, deviceName: String, isAdmin: Boolean = false) {
        val data = mapOf(
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "isAdmin" to isAdmin,
            "removed" to false,
            "lastSyncVersion" to 0L,
            "lastSeen" to System.currentTimeMillis()
        )
        db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .set(data)
            .await()
        // Ensure group document exists with lastActivity for TTL
        updateGroupActivity(groupId)
    }

    suspend fun deleteGroup(groupId: String, onProgress: ((String) -> Unit)? = null) {
        val groupRef = db.collection("groups").document(groupId)

        // Write dissolved flag BEFORE deleting anything — gives non-admin
        // devices an affirmative signal to auto-leave, even if they check
        // before the subcollection cleanup finishes.
        onProgress?.invoke("Notifying devices…")
        groupRef.set(mapOf("status" to "dissolved"), SetOptions.merge()).await()

        // Delete subcollections in paginated batches to avoid downloading
        // huge payloads (e.g. 600+ encrypted delta documents).
        val labels = mapOf("deltas" to "sync history", "devices" to "devices", "snapshots" to "snapshots")
        for (subCollection in listOf("deltas", "devices", "snapshots")) {
            onProgress?.invoke("Removing ${labels[subCollection]}…")
            deleteSubcollection(groupRef.collection(subCollection), onProgress = onProgress)
        }

        // Delete the group document itself
        onProgress?.invoke("Finalizing…")
        groupRef.delete().await()
    }

    /** Paginated subcollection delete — fetches only document IDs in
     *  batches to avoid downloading large payload fields. */
    private suspend fun deleteSubcollection(
        collection: com.google.firebase.firestore.CollectionReference,
        batchSize: Int = 200,
        onProgress: ((String) -> Unit)? = null
    ) {
        var totalDeleted = 0
        while (true) {
            val snapshot = collection
                .limit(batchSize.toLong())
                .get()
                .await()
            if (snapshot.isEmpty) break
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            totalDeleted += snapshot.size()
            if (totalDeleted > batchSize) {
                onProgress?.invoke("Removed $totalDeleted records…")
            }
        }
    }

    suspend fun createAdminClaim(groupId: String, claim: AdminClaim) {
        val data = mapOf(
            "claimantDeviceId" to claim.claimantDeviceId,
            "claimantName" to claim.claimantName,
            "claimedAt" to claim.claimedAt,
            "expiresAt" to claim.expiresAt,
            "objections" to claim.objections,
            "status" to claim.status
        )
        db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
            .set(data)
            .await()
    }

    suspend fun getAdminClaim(groupId: String): AdminClaim? = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
            .get()
            .await()

        if (!doc.exists()) null
        else AdminClaim(
            claimantDeviceId = doc.getString("claimantDeviceId") ?: "",
            claimantName = doc.getString("claimantName") ?: "",
            claimedAt = doc.getLong("claimedAt") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            objections = (doc.get("objections") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            status = doc.getString("status") ?: "pending"
        )
    }

    suspend fun addObjection(groupId: String, deviceId: String) {
        val ref = db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val objections = (snapshot.get("objections") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
            if (!objections.contains(deviceId)) {
                objections.add(deviceId)
            }
            transaction.update(ref, "objections", objections)
        }.await()
    }

    suspend fun resolveAdminClaim(groupId: String, status: String) {
        db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
            .update("status", status)
            .await()
    }

    suspend fun deleteAdminClaim(groupId: String) {
        db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
            .delete()
            .await()
    }

    suspend fun transferAdmin(groupId: String, fromDeviceId: String, toDeviceId: String) {
        val devicesRef = db.collection("groups").document(groupId).collection("devices")
        devicesRef.document(fromDeviceId).update("isAdmin", false).await()
        devicesRef.document(toDeviceId).update("isAdmin", true).await()
        deleteAdminClaim(groupId)
    }
}

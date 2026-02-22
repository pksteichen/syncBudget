package com.syncbudget.app.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

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
    val lastSeen: Long = 0L
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

    suspend fun fetchDeltas(groupId: String, lastSyncVersion: Long): List<FirestoreDelta> {
        val snapshot = db.collection("groups")
            .document(groupId)
            .collection("deltas")
            .whereGreaterThan("version", lastSyncVersion)
            .orderBy("version", Query.Direction.ASCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            FirestoreDelta(
                version = doc.getLong("version") ?: 0L,
                sourceDeviceId = doc.getString("sourceDeviceId") ?: "",
                encryptedPayload = doc.getString("encryptedPayload") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L
            )
        }
    }

    suspend fun pushDelta(
        groupId: String,
        sourceDeviceId: String,
        encryptedPayload: String,
        version: Long
    ) {
        val data = mapOf(
            "version" to version,
            "sourceDeviceId" to sourceDeviceId,
            "encryptedPayload" to encryptedPayload,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("groups")
            .document(groupId)
            .collection("deltas")
            .document("v$version")
            .set(data)
            .await()
    }

    suspend fun updateDeviceMetadata(groupId: String, deviceId: String, syncVersion: Long) {
        val data = mapOf(
            "deviceId" to deviceId,
            "lastSyncVersion" to syncVersion,
            "lastSeen" to System.currentTimeMillis()
        )
        db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .set(data)
            .await()
    }

    suspend fun getDeviceRecord(groupId: String, deviceId: String): DeviceRecord? {
        val doc = db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .get()
            .await()

        if (!doc.exists()) return null
        return DeviceRecord(
            deviceId = doc.getString("deviceId") ?: deviceId,
            lastSyncVersion = doc.getLong("lastSyncVersion") ?: 0L,
            lastSeen = doc.getLong("lastSeen") ?: 0L
        )
    }

    suspend fun getSnapshot(groupId: String): SnapshotRecord? {
        val doc = db.collection("groups")
            .document(groupId)
            .collection("snapshots")
            .document("latest")
            .get()
            .await()

        if (!doc.exists()) return null
        return SnapshotRecord(
            snapshotVersion = doc.getLong("snapshotVersion") ?: 0L,
            createdBy = doc.getString("createdBy") ?: "",
            encryptedData = doc.getString("encryptedData") ?: "",
            timestamp = doc.getLong("timestamp") ?: 0L
        )
    }

    suspend fun writeSnapshot(
        groupId: String,
        snapshotVersion: Long,
        createdBy: String,
        encryptedData: String
    ) {
        val data = mapOf(
            "snapshotVersion" to snapshotVersion,
            "createdBy" to createdBy,
            "encryptedData" to encryptedData,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("groups")
            .document(groupId)
            .collection("snapshots")
            .document("latest")
            .set(data)
            .await()
    }

    suspend fun getNextDeltaVersion(groupId: String): Long {
        val groupRef = db.collection("groups").document(groupId)
        return db.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val current = snapshot.getLong("nextDeltaVersion") ?: 1L
            transaction.update(groupRef, "nextDeltaVersion", current + 1)
            current
        }.await()
    }

    suspend fun createPairingCode(groupId: String, code: String, encryptedKey: String, expiresAt: Long) {
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
        val doc = db.collection("pairing_codes")
            .document(code.uppercase())
            .get()
            .await()

        if (!doc.exists()) return null

        val expiresAt = doc.getLong("expiresAt") ?: 0L
        if (System.currentTimeMillis() > expiresAt) return null

        val groupId = doc.getString("groupId") ?: return null
        val key = doc.getString("encryptedKey") ?: return null

        // Delete the code after redemption (one-time use)
        db.collection("pairing_codes").document(code.uppercase()).delete().await()

        return PairingData(groupId, key)
    }

    suspend fun getDevices(groupId: String): List<DeviceRecord> {
        val snapshot = db.collection("groups")
            .document(groupId)
            .collection("devices")
            .get()
            .await()

        return snapshot.documents.map { doc ->
            DeviceRecord(
                deviceId = doc.getString("deviceId") ?: doc.id,
                deviceName = doc.getString("deviceName") ?: "",
                isAdmin = doc.getBoolean("isAdmin") ?: false,
                lastSyncVersion = doc.getLong("lastSyncVersion") ?: 0L,
                lastSeen = doc.getLong("lastSeen") ?: 0L
            )
        }
    }

    suspend fun registerDevice(groupId: String, deviceId: String, deviceName: String, isAdmin: Boolean = false) {
        val data = mapOf(
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "isAdmin" to isAdmin,
            "lastSyncVersion" to 0L,
            "lastSeen" to System.currentTimeMillis()
        )
        db.collection("groups")
            .document(groupId)
            .collection("devices")
            .document(deviceId)
            .set(data)
            .await()
    }

    suspend fun deleteGroup(groupId: String) {
        // Delete all subcollections
        val groupRef = db.collection("groups").document(groupId)

        // Delete deltas
        val deltas = groupRef.collection("deltas").get().await()
        for (doc in deltas.documents) {
            doc.reference.delete().await()
        }

        // Delete devices
        val devices = groupRef.collection("devices").get().await()
        for (doc in devices.documents) {
            doc.reference.delete().await()
        }

        // Delete snapshots
        val snapshots = groupRef.collection("snapshots").get().await()
        for (doc in snapshots.documents) {
            doc.reference.delete().await()
        }

        // Delete group document
        groupRef.delete().await()
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

    suspend fun getAdminClaim(groupId: String): AdminClaim? {
        val doc = db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
            .get()
            .await()

        if (!doc.exists()) return null
        return AdminClaim(
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

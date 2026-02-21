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
    val lastSyncVersion: Long,
    val lastSeen: Long
)

data class SnapshotRecord(
    val snapshotVersion: Long,
    val createdBy: String,
    val encryptedData: String,
    val timestamp: Long
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
}

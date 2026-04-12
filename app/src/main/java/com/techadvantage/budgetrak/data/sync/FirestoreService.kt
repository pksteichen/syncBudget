package com.techadvantage.budgetrak.data.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class DeviceRecord(
    val deviceId: String,
    val deviceName: String = "",
    val isAdmin: Boolean = false,
    val lastSyncVersion: Long = 0L,
    val lastSeen: Long = 0L,
    val fingerprintData: String? = null,
    val fingerprintSyncVersion: Long = 0L,
    val photoCapable: Boolean = false,
    val uploadSpeedBps: Long = 0L,
    val uploadSpeedMeasuredAt: Long = 0L
)

data class PairingData(
    val groupId: String,
    val encryptedKey: String
)

data class AdminClaim(
    val claimantDeviceId: String,
    val claimantName: String,
    val claimedAt: Long,
    val expiresAt: Long,
    val votes: Map<String, String> = emptyMap(), // deviceId → "accept"|"reject"
    val status: String = "pending" // "pending"|"approved"|"rejected"
)

object FirestoreService {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** Base timeout for individual Firestore operations (ms). */
    private const val OP_TIMEOUT_MS = 30_000L

    /** Update the admin subscription expiration date on the group doc. */
    suspend fun updateSubscriptionExpiry(groupId: String, expiryTimestamp: Long) = withTimeout(OP_TIMEOUT_MS) {
        val elapsedMs = System.currentTimeMillis() - expiryTimestamp
        val elapsedDays = elapsedMs / (24 * 60 * 60 * 1000.0)
        com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
            "updateSubscriptionExpiry: group=$groupId expiry=$expiryTimestamp elapsedDays=${"%.1f".format(elapsedDays)} " +
            "(${if (elapsedMs > 0) "PAST" else "FUTURE"}, grace triggers at >7d)"
        )
        db.collection("groups").document(groupId)
            .set(mapOf("subscriptionExpiry" to expiryTimestamp), SetOptions.merge())
            .await()
    }

    suspend fun updateDeviceMetadata(
        groupId: String,
        deviceId: String,
        deviceName: String = "",
        syncVersion: Long,
        fingerprintJson: String? = null,
        appSyncVersion: Int = 0,
        minSyncVersion: Int = 0,
        photoCapable: Boolean = false,
        uploadSpeedBps: Long = 0L,
        uploadSpeedMeasuredAt: Long = 0L
    ) = withTimeout(OP_TIMEOUT_MS) {
        val data = mutableMapOf<String, Any>(
            "deviceId" to deviceId,
            "lastSyncVersion" to syncVersion,
            "photoCapable" to photoCapable
        )
        if (deviceName.isNotEmpty()) data["deviceName"] = deviceName
        if (appSyncVersion > 0) {
            data["appSyncVersion"] = appSyncVersion
            data["minSyncVersion"] = minSyncVersion
        }
        if (fingerprintJson != null) {
            data["fingerprintData"] = fingerprintJson
            data["fingerprintSyncVersion"] = syncVersion
        }
        if (uploadSpeedBps > 0) {
            data["uploadSpeedBps"] = uploadSpeedBps
            data["uploadSpeedMeasuredAt"] = uploadSpeedMeasuredAt
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
            lastSeen = doc.getLong("lastSeen") ?: 0L,
            photoCapable = doc.getBoolean("photoCapable") ?: false,
            uploadSpeedBps = doc.getLong("uploadSpeedBps") ?: 0L,
            uploadSpeedMeasuredAt = doc.getLong("uploadSpeedMeasuredAt") ?: 0L
        )
    }

    /** Read group document once for all startup health checks. */
    data class GroupHealthStatus(
        val isDissolved: Boolean,
        val subscriptionExpiry: Long
    )

    suspend fun getGroupHealthStatus(groupId: String): GroupHealthStatus = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups").document(groupId).get().await()
        val exists = doc.exists()
        val status = doc.getString("status")
        val fromCache = doc.metadata.isFromCache
        // Only trust "not exists" from server — cache miss is not dissolution
        val isDissolved = if (!exists) !fromCache else status == "dissolved"
        val subExpiry = doc.getLong("subscriptionExpiry") ?: 0L
        com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
            "getGroupHealthStatus: isDissolved=$isDissolved exists=$exists status=$status fromCache=$fromCache subExpiry=$subExpiry groupId=$groupId"
        )
        GroupHealthStatus(
            isDissolved = isDissolved,
            subscriptionExpiry = doc.getLong("subscriptionExpiry") ?: 0L
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



    suspend fun updateGroupActivity(groupId: String) = withTimeout(OP_TIMEOUT_MS) {
        val expiresAt = com.google.firebase.Timestamp(
            (System.currentTimeMillis() / 1000) + (90L * 24 * 60 * 60), 0
        )
        db.collection("groups").document(groupId)
            .set(mapOf(
                "lastActivity" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt
            ), SetOptions.merge())
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
            // Convert epoch millis to Firestore Timestamp so TTL policy works correctly.
            // TTL requires a Timestamp type; storing as raw millis would be interpreted
            // as a date in year ~50,000 and TTL would never fire.
            "expiresAt" to Timestamp(expiresAt / 1000, 0),
            "timestamp" to System.currentTimeMillis()
        )
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("pairing_codes")
                .document(code)
                .set(data)
                .await()
        }
    }

    suspend fun redeemPairingCode(code: String): PairingData? {
        val normalized = code.uppercase().trim()
        if (!normalized.matches(Regex("^[A-Z2-9]{6}$"))) return null
        return withTimeout(OP_TIMEOUT_MS) {
            val doc = db.collection("pairing_codes")
                .document(normalized)
                .get()
                .await()

            if (!doc.exists()) return@withTimeout null

            // expiresAt may be a Firestore Timestamp (new) or Long (legacy)
            val expiresAt = (doc.getTimestamp("expiresAt")?.toDate()?.time)
                ?: doc.getLong("expiresAt")
                ?: 0L
            if (System.currentTimeMillis() > expiresAt) return@withTimeout null

            val groupId = doc.getString("groupId") ?: return@withTimeout null
            val key = doc.getString("encryptedKey") ?: return@withTimeout null

            // Delete the code after redemption (one-time use)
            db.collection("pairing_codes").document(normalized).delete().await()

            PairingData(groupId, key)
        }
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
                    fingerprintSyncVersion = doc.getLong("fingerprintSyncVersion") ?: 0L,
                    photoCapable = doc.getBoolean("photoCapable") ?: false,
                    uploadSpeedBps = doc.getLong("uploadSpeedBps") ?: 0L,
                    uploadSpeedMeasuredAt = doc.getLong("uploadSpeedMeasuredAt") ?: 0L
                )
            }
    }

    suspend fun updateDeviceName(groupId: String, deviceId: String, newName: String) {
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("devices")
                .document(deviceId)
                .set(mapOf("deviceName" to newName), SetOptions.merge())
                .await()
        }
    }

    suspend fun removeDevice(groupId: String, deviceId: String) {
        // Mark as removed rather than deleting — gives the non-admin an
        // affirmative signal so it can auto-leave reliably instead of
        // guessing from document absence (which has false positives from
        // Firestore cache staleness and network issues).
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("devices")
                .document(deviceId)
                .set(mapOf("removed" to true), SetOptions.merge())
                .await()
        }
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
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("devices")
                .document(deviceId)
                .set(data)
                .await()
        }
        // Ensure group document exists with lastActivity for TTL
        updateGroupActivity(groupId)
    }

    /** Register auth.uid as a group member (for security rules). */
    suspend fun registerMembership(groupId: String, authUid: String, deviceId: String) {
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups").document(groupId)
                .collection("members").document(authUid)
                .set(mapOf("deviceId" to deviceId, "joinedAt" to System.currentTimeMillis()))
                .await()
        }
    }

    /** Remove auth.uid membership (on leave/eviction). */
    suspend fun removeMembership(groupId: String, authUid: String) {
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups").document(groupId)
                .collection("members").document(authUid)
                .delete()
                .await()
        }
    }

    suspend fun deleteGroup(groupId: String, onProgress: ((String) -> Unit)? = null) {
        val caller = Thread.currentThread().stackTrace.drop(2).take(5)
            .joinToString(" → ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog("deleteGroup CALLED for group=$groupId caller=$caller")
        val groupRef = db.collection("groups").document(groupId)

        // Write dissolved flag BEFORE deleting anything — gives non-admin
        // devices an affirmative signal to auto-leave, even if they check
        // before the subcollection cleanup finishes.
        onProgress?.invoke("Notifying devices…")
        groupRef.set(mapOf("status" to "dissolved"), SetOptions.merge()).await()

        // Delete all subcollections in paginated batches
        val allSubCollections = listOf(
            // Firestore-native sync collections
            "transactions", "recurringExpenses", "incomeSources",
            "savingsGoals", "amortizationEntries", "categories",
            "periodLedger", "sharedSettings",
            // Group management
            "devices", "members", "imageLedger", "adminClaim",
            // Legacy CRDT (may still exist from old groups)
            "deltas", "snapshots"
        )
        for (subCollection in allSubCollections) {
            onProgress?.invoke("Removing $subCollection…")
            deleteSubcollection(groupRef.collection(subCollection), onProgress = onProgress)
        }

        // Delete Cloud Storage receipt files + snapshot archive
        try {
            onProgress?.invoke("Removing receipt photos…")
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance()
                .reference.child("groups/$groupId/receipts")
            val items = storageRef.listAll().await()
            for (item in items.items) {
                try { item.delete().await() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        try {
            com.google.firebase.storage.FirebaseStorage.getInstance()
                .reference.child("groups/$groupId/photoSnapshot.enc").delete().await()
        } catch (_: Exception) {}

        // Delete RTDB presence nodes for entire group
        try {
            RealtimePresenceService.deleteGroupPresence(groupId)
        } catch (_: Exception) {}

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
            "votes" to claim.votes,
            "status" to claim.status
        )
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("adminClaim")
                .document("current")
                .set(data)
                .await()
        }
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
            votes = (doc.get("votes") as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k is String && v is String) k to v else null
            }?.toMap() ?: emptyMap(),
            status = doc.getString("status") ?: "pending"
        )
    }

    suspend fun castVote(groupId: String, deviceId: String, vote: String) {
        val ref = db.collection("groups")
            .document(groupId)
            .collection("adminClaim")
            .document("current")
        withTimeout(OP_TIMEOUT_MS) {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                if (snapshot.getString("status") != "pending") return@runTransaction // already resolved
                @Suppress("UNCHECKED_CAST")
                val votes = (snapshot.get("votes") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
                votes[deviceId] = vote
                val updates = mutableMapOf<String, Any>("votes" to votes)
                // Immediate rejection on any reject vote
                if (vote == "reject") updates["status"] = "rejected"
                transaction.update(ref, updates)
            }.await()
        }
    }

    suspend fun resolveAdminClaim(groupId: String, status: String) {
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("adminClaim")
                .document("current")
                .update("status", status)
                .await()
        }
    }

    suspend fun deleteAdminClaim(groupId: String) {
        withTimeout(OP_TIMEOUT_MS) {
            db.collection("groups")
                .document(groupId)
                .collection("adminClaim")
                .document("current")
                .delete()
                .await()
        }
    }

    suspend fun transferAdmin(groupId: String, fromDeviceId: String, toDeviceId: String) = withTimeout(OP_TIMEOUT_MS) {
        val devicesRef = db.collection("groups").document(groupId).collection("devices")
        val claimRef = db.collection("groups").document(groupId)
            .collection("adminClaim").document("current")
        db.runTransaction { transaction ->
            transaction.update(devicesRef.document(fromDeviceId), "isAdmin", false)
            transaction.update(devicesRef.document(toDeviceId), "isAdmin", true)
            transaction.delete(claimRef)
        }.await()
    }

    // ── FCM Token Management ────────────────────────────────────

    /** Store this device's FCM token in Firestore for push notifications. */
    suspend fun storeFcmToken(groupId: String, deviceId: String, fcmToken: String) = withTimeout(OP_TIMEOUT_MS) {
        db.collection("groups").document(groupId)
            .collection("devices").document(deviceId)
            .set(mapOf("fcmToken" to fcmToken), SetOptions.merge()).await()
    }

    /** Get FCM tokens for all active devices except the given one. */
    suspend fun getFcmTokens(groupId: String, excludeDeviceId: String): List<String> = withTimeout(OP_TIMEOUT_MS) {
        val snapshot = db.collection("groups").document(groupId)
            .collection("devices").get().await()
        snapshot.documents
            .filter { it.id != excludeDeviceId && it.getBoolean("removed") != true }
            .mapNotNull { it.getString("fcmToken") }
    }

    // ── Debug File Sync ─────────────────────────────────────────

    suspend fun uploadDebugFiles(
        groupId: String, deviceId: String, deviceName: String,
        syncLog: String, syncDiag: String,
        encryptionKey: ByteArray? = null
    ) = withTimeout(OP_TIMEOUT_MS) {
        // Encrypt debug data before storing in Firestore.
        // Even in debug builds, financial data should not be plaintext.
        val shortId = deviceId.take(8)
        val logData = if (encryptionKey != null) {
            android.util.Base64.encodeToString(
                com.techadvantage.budgetrak.data.CryptoHelper.encryptWithKey(
                    syncLog.takeLast(50_000).toByteArray(), encryptionKey
                ), android.util.Base64.NO_WRAP
            )
        } else syncLog.takeLast(50_000)
        val diagData = if (encryptionKey != null) {
            android.util.Base64.encodeToString(
                com.techadvantage.budgetrak.data.CryptoHelper.encryptWithKey(
                    syncDiag.takeLast(50_000).toByteArray(), encryptionKey
                ), android.util.Base64.NO_WRAP
            )
        } else syncDiag.takeLast(50_000)
        val data = mapOf(
            "debug_${shortId}_name" to deviceName,
            "debug_${shortId}_log" to logData,
            "debug_${shortId}_diag" to diagData,
            "debug_${shortId}_enc" to (encryptionKey != null),
            "debug_${shortId}_at" to System.currentTimeMillis()
        )
        db.collection("groups").document(groupId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadDebugFiles(
        groupId: String, myDeviceId: String,
        encryptionKey: ByteArray? = null
    ): List<DebugFileSet> = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups").document(groupId).get().await()
        val myShortId = myDeviceId.take(8)
        val results = mutableListOf<DebugFileSet>()
        val data = doc.data ?: return@withTimeout emptyList()
        val deviceIds = data.keys.filter { it.startsWith("debug_") && it.endsWith("_name") }
            .map { it.removePrefix("debug_").removeSuffix("_name") }
            .filter { it != myShortId }
        for (id in deviceIds) {
            val name = doc.getString("debug_${id}_name") ?: id
            var log = doc.getString("debug_${id}_log") ?: ""
            var diag = doc.getString("debug_${id}_diag") ?: ""
            val encrypted = doc.getBoolean("debug_${id}_enc") ?: false
            val at = doc.getLong("debug_${id}_at") ?: 0L
            // Decrypt if encrypted and we have the key
            if (encrypted && encryptionKey != null) {
                try {
                    if (log.isNotEmpty()) log = String(
                        com.techadvantage.budgetrak.data.CryptoHelper.decryptWithKey(
                            android.util.Base64.decode(log, android.util.Base64.NO_WRAP), encryptionKey
                        )
                    )
                    if (diag.isNotEmpty()) diag = String(
                        com.techadvantage.budgetrak.data.CryptoHelper.decryptWithKey(
                            android.util.Base64.decode(diag, android.util.Base64.NO_WRAP), encryptionKey
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w("FirestoreService", "Debug file decrypt failed: ${e.message}")
                    continue
                }
            }
            if (log.isNotEmpty() || diag.isNotEmpty()) {
                results.add(DebugFileSet(name, log, diag, at))
            }
        }
        results
    }

    /** Read the debug request timestamp from the group document. */
    suspend fun getDebugRequestTime(groupId: String): Long = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups").document(groupId).get().await()
        doc.getLong("debugRequestedAt") ?: 0L
    }

    /** Admin sets a timestamp so all devices upload fresh debug files on next sync. */
    suspend fun requestDebugDump(groupId: String) = withTimeout(OP_TIMEOUT_MS) {
        db.collection("groups").document(groupId)
            .set(mapOf("debugRequestedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    /** Returns age of join snapshot in millis, or Long.MAX_VALUE if none exists. */
    suspend fun getJoinSnapshotAge(groupId: String): Long = withTimeout(OP_TIMEOUT_MS) {
        val doc = db.collection("groups").document(groupId).get().await()
        val ts = doc.getLong("joinSnapshotAt") ?: return@withTimeout Long.MAX_VALUE
        System.currentTimeMillis() - ts
    }

    /** Record when a join snapshot was uploaded, for TTL reuse. */
    suspend fun setJoinSnapshotTimestamp(groupId: String) = withTimeout(OP_TIMEOUT_MS) {
        db.collection("groups").document(groupId)
            .set(mapOf("joinSnapshotAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    /** Remove the join snapshot timestamp after cleanup. */
    suspend fun clearJoinSnapshotTimestamp(groupId: String) = withTimeout(OP_TIMEOUT_MS) {
        db.collection("groups").document(groupId)
            .update(mapOf("joinSnapshotAt" to FieldValue.delete()))
            .await()
    }
}

data class DebugFileSet(val deviceName: String, val syncLog: String, val syncDiag: String, val updatedAt: Long = 0L)

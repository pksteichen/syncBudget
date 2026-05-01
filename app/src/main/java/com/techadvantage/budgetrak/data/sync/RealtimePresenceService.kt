package com.techadvantage.budgetrak.data.sync

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

data class PresenceRecord(
    val deviceId: String,
    val online: Boolean,
    val deviceName: String,
    val lastSeen: Long,
    val photoCapable: Boolean = false,
    val uploadSpeedBps: Long = 0L,
    val uploadSpeedMeasuredAt: Long = 0L
)

/**
 * Firebase Realtime Database presence service.
 *
 * Manages online/offline status for devices using RTDB's server-side
 * disconnect detection (onDisconnect). Replaces Firestore device polling
 * with instant presence updates.
 *
 * RTDB path: groups/{groupId}/presence/{deviceId}
 */
object RealtimePresenceService {

    private const val TAG = "RTDBPresence"

    private var database: FirebaseDatabase? = null
    private var connectedListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null
    private var presenceListener: ValueEventListener? = null
    private var presenceRef: DatabaseReference? = null
    private var myPresenceRef: DatabaseReference? = null

    private fun getDatabase(): FirebaseDatabase? {
        if (database == null) {
            database = try {
                FirebaseDatabase.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "RTDB not configured (missing firebase_url in google-services.json): ${e.message}")
                null
            }
        }
        return database
    }

    /**
     * Set up presence for this device. Writes online=true on connect,
     * registers onDisconnect to write online=false + lastSeen on disconnect.
     */
    fun setupPresence(
        groupId: String, deviceId: String, deviceName: String,
        photoCapable: Boolean = false, uploadSpeedBps: Long = 0L, uploadSpeedMeasuredAt: Long = 0L
    ) {
        val db = getDatabase() ?: return

        // Idempotent re-arm: callers (configureSyncGroup, onListenerRecovered,
        // onResume) all call this on every wake. Without tearing down the
        // previous listener first, each call STACKS another ValueEventListener
        // on .info/connected — every connectivity event then fires N writes
        // to myRef and registers N onDisconnect handlers. Observed as 3-4
        // "Presence online for ..." log lines within a few ms of each wake.
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        myPresenceRef?.onDisconnect()?.cancel()

        val myRef = db.reference.child("groups/$groupId/presence/$deviceId")
        myPresenceRef = myRef
        val connRef = db.getReference(".info/connected")
        connectedRef = connRef

        val data = mapOf(
            "online" to true,
            "deviceName" to deviceName,
            "lastSeen" to ServerValue.TIMESTAMP,
            "photoCapable" to photoCapable,
            "uploadSpeedBps" to uploadSpeedBps,
            "uploadSpeedMeasuredAt" to uploadSpeedMeasuredAt
        )
        val disconnectData = mapOf(
            "online" to false,
            "deviceName" to deviceName,
            "lastSeen" to ServerValue.TIMESTAMP,
            "photoCapable" to photoCapable,
            "uploadSpeedBps" to uploadSpeedBps,
            "uploadSpeedMeasuredAt" to uploadSpeedMeasuredAt
        )

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    myRef.onDisconnect().setValue(disconnectData)
                    myRef.setValue(data)
                    Log.i(TAG, "Presence online for $deviceId in group $groupId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Connection listener cancelled: ${error.message}")
            }
        }

        connRef.addValueEventListener(listener)
        connectedListener = listener
    }

    /**
     * Listen to all devices' presence in a group. Fires callback with
     * the full list whenever any device's status changes.
     */
    fun listenToGroupPresence(
        groupId: String,
        callback: (List<PresenceRecord>) -> Unit
    ) {
        val db = getDatabase() ?: return

        val ref = db.reference.child("groups/$groupId/presence")
        presenceRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = snapshot.children.mapNotNull { child ->
                    val deviceId = child.key ?: return@mapNotNull null
                    val online = child.child("online").getValue(Boolean::class.java) ?: false
                    val name = child.child("deviceName").getValue(String::class.java) ?: ""
                    val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                    val photoCapable = child.child("photoCapable").getValue(Boolean::class.java) ?: false
                    val speed = child.child("uploadSpeedBps").getValue(Long::class.java) ?: 0L
                    val speedAt = child.child("uploadSpeedMeasuredAt").getValue(Long::class.java) ?: 0L
                    PresenceRecord(deviceId, online, name, lastSeen, photoCapable, speed, speedAt)
                }
                callback(records)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Group presence listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        presenceListener = listener
    }

    /**
     * One-shot read of all devices' presence (for background workers without persistent listeners).
     * Returns empty list if RTDB is not configured.
     */
    suspend fun getDevices(groupId: String): List<DeviceInfo> {
        val db = getDatabase() ?: return emptyList()
        return try {
            val snapshot = db.reference.child("groups/$groupId/presence")
                .get().await()
            snapshot.children.mapNotNull { snap ->
                val id = snap.key ?: return@mapNotNull null
                val name = snap.child("deviceName").getValue(String::class.java) ?: ""
                val lastSeen = snap.child("lastSeen").getValue(Long::class.java) ?: 0L
                val online = snap.child("online").getValue(Boolean::class.java) ?: false
                val photo = snap.child("photoCapable").getValue(Boolean::class.java) ?: false
                val speed = snap.child("uploadSpeedBps").getValue(Long::class.java) ?: 0L
                val speedAt = snap.child("uploadSpeedMeasuredAt").getValue(Long::class.java) ?: 0L
                DeviceInfo(id, name, isAdmin = false, lastSeen = lastSeen,
                    online = online, photoCapable = photo,
                    uploadSpeedBps = speed, uploadSpeedMeasuredAt = speedAt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "One-shot presence read failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Delete a single device's presence node (called on group leave).
     */
    fun deletePresenceNode(groupId: String, deviceId: String) {
        val db = getDatabase() ?: return
        db.reference.child("groups/$groupId/presence/$deviceId").removeValue()
    }

    /**
     * Delete all presence nodes for a group (called on group dissolution).
     */
    fun deleteGroupPresence(groupId: String) {
        val db = getDatabase() ?: return
        db.reference.child("groups/$groupId").removeValue()
    }

    /**
     * Clean up all RTDB listeners and references.
     */
    fun cleanup() {
        // Cancel onDisconnect handler so it doesn't re-write after deletion
        myPresenceRef?.onDisconnect()?.cancel()
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        presenceListener?.let { presenceRef?.removeEventListener(it) }
        connectedListener = null
        connectedRef = null
        presenceListener = null
        presenceRef = null
        myPresenceRef = null
    }
}

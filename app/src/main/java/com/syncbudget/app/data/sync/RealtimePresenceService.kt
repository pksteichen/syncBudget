package com.syncbudget.app.data.sync

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

data class PresenceRecord(
    val deviceId: String,
    val online: Boolean,
    val deviceName: String,
    val lastSeen: Long
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
    fun setupPresence(groupId: String, deviceId: String, deviceName: String) {
        val db = getDatabase() ?: return

        val myRef = db.reference.child("groups/$groupId/presence/$deviceId")
        myPresenceRef = myRef
        val connRef = db.getReference(".info/connected")
        connectedRef = connRef

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    // Register what to write on disconnect BEFORE marking online
                    myRef.onDisconnect().setValue(mapOf(
                        "online" to false,
                        "deviceName" to deviceName,
                        "lastSeen" to ServerValue.TIMESTAMP
                    ))
                    // Mark as online
                    myRef.setValue(mapOf(
                        "online" to true,
                        "deviceName" to deviceName,
                        "lastSeen" to ServerValue.TIMESTAMP
                    ))
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
                    PresenceRecord(deviceId, online, name, lastSeen)
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
     * Clean up all RTDB listeners and references.
     */
    fun cleanup() {
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        presenceListener?.let { presenceRef?.removeEventListener(it) }
        connectedListener = null
        connectedRef = null
        presenceListener = null
        presenceRef = null
        myPresenceRef = null
    }
}

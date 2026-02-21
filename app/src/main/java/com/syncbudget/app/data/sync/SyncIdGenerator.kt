package com.syncbudget.app.data.sync

import android.content.Context
import java.util.UUID

object SyncIdGenerator {

    private const val PREFS_NAME = "sync_device"
    private const val KEY_DEVICE_ID = "deviceId"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun deviceHash(deviceId: String): Int {
        // 16-bit hash from UUID
        return deviceId.hashCode() and 0xFFFF
    }

    fun generateId(deviceId: String, existingIds: Set<Int>): Int {
        val upper = deviceHash(deviceId) shl 16
        var id: Int
        do {
            val lower = (0..0xFFFF).random()
            id = upper or lower
        } while (id in existingIds)
        return id
    }
}

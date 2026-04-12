package com.techadvantage.budgetrak.data.sync

import android.content.Context
import java.util.UUID

object SyncIdGenerator {

    private const val PREFS_NAME = "sync_device"
    private const val KEY_DEVICE_ID = "deviceId"

    @Synchronized
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).commit()
        return id
    }
}

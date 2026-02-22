package com.syncbudget.app.data.sync

import android.content.Context
import android.util.Base64
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.SharedSettingsRepository
import java.security.SecureRandom
import java.util.TimeZone

data class GroupInfo(
    val groupId: String,
    val encryptionKey: ByteArray,
    val isAdmin: Boolean
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val isAdmin: Boolean,
    val lastSeen: Long
)

object GroupManager {

    private const val PREFS_NAME = "sync_engine"

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("groupId", null) != null
    }

    fun getGroupId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("groupId", null)
    }

    fun getEncryptionKey(context: Context): ByteArray? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyStr = prefs.getString("encryptionKey", null) ?: return null
        return Base64.decode(keyStr, Base64.NO_WRAP)
    }

    fun isAdmin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("isAdmin", false)
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
    }

    fun setDeviceName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("deviceName", name).apply()
    }

    fun createGroup(context: Context): GroupInfo {
        val random = SecureRandom()

        // Generate 12-char hex group ID
        val groupIdBytes = ByteArray(6)
        random.nextBytes(groupIdBytes)
        val groupId = groupIdBytes.joinToString("") { "%02x".format(it) }

        // Generate 256-bit encryption key
        val key = ByteArray(32)
        random.nextBytes(key)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("groupId", groupId)
            .putString("encryptionKey", Base64.encodeToString(key, Base64.NO_WRAP))
            .putBoolean("isAdmin", true)
            .apply()

        // Set familyTimezone to device's default timezone in SharedSettings
        val currentSettings = SharedSettingsRepository.load(context)
        SharedSettingsRepository.save(context, currentSettings.copy(
            familyTimezone = TimeZone.getDefault().id
        ))

        return GroupInfo(groupId, key, isAdmin = true)
    }

    suspend fun joinGroup(context: Context, pairingCode: String): Boolean {
        val pairingData = FirestoreService.redeemPairingCode(pairingCode) ?: return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("groupId", pairingData.groupId)
            .putString("encryptionKey", pairingData.encryptedKey)
            .putBoolean("isAdmin", false)
            .apply()

        // Register this device in the group
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
        val deviceName = getDeviceName(context)
        FirestoreService.registerDevice(pairingData.groupId, deviceId, deviceName)

        return true
    }

    fun leaveGroup(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("groupId")
            .remove("encryptionKey")
            .remove("isAdmin")
            .remove("lastSyncVersion")
            .remove("lastPushedClock")
            .apply()
        SyncWorker.cancel(context)
    }

    suspend fun dissolveGroup(context: Context, groupId: String) {
        FirestoreService.deleteGroup(groupId)
        leaveGroup(context)
    }

    suspend fun generatePairingCode(context: Context, groupId: String, encryptionKey: ByteArray): String {
        val random = SecureRandom()
        val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous chars
        val code = (1..6).map { codeChars[random.nextInt(codeChars.length)] }.joinToString("")

        val keyBase64 = Base64.encodeToString(encryptionKey, Base64.NO_WRAP)
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes

        FirestoreService.createPairingCode(groupId, code, keyBase64, expiresAt)

        return code
    }

    suspend fun getDevices(groupId: String): List<DeviceInfo> {
        return FirestoreService.getDevices(groupId).map { record ->
            DeviceInfo(
                deviceId = record.deviceId,
                deviceName = record.deviceName,
                isAdmin = record.isAdmin,
                lastSeen = record.lastSeen
            )
        }
    }
}

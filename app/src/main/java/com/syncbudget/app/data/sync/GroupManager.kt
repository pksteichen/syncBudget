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
    val lastSeen: Long,
    val online: Boolean = false,
    val photoCapable: Boolean = false,
    val uploadSpeedBps: Long = 0L,
    val uploadSpeedMeasuredAt: Long = 0L
)

object GroupManager {

    private const val PREFS_NAME = "sync_engine"

    /** Normalize pairing code for consistent encrypt/decrypt. */
    private fun normalizeCode(code: String): CharArray =
        code.uppercase().trim().toCharArray()

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("groupId", null) != null
    }

    fun getGroupId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("groupId", null)
    }

    fun getEncryptionKey(context: Context): ByteArray? {
        // Try encrypted prefs first, then fall back to plain (pre-migration)
        val securePrefs = SecurePrefs.get(context)
        val keyStr = securePrefs.getString("encryptionKey", null)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("encryptionKey", null)
            ?: return null
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
            .putBoolean("isAdmin", true)
            .apply()
        // Store encryption key in encrypted prefs
        SecurePrefs.get(context).edit()
            .putString("encryptionKey", Base64.encodeToString(key, Base64.NO_WRAP))
            .commit()

        // Set familyTimezone to device's default timezone in SharedSettings
        val currentSettings = SharedSettingsRepository.load(context)
        SharedSettingsRepository.save(context, currentSettings.copy(
            familyTimezone = TimeZone.getDefault().id
        ))

        return GroupInfo(groupId, key, isAdmin = true)
    }

    suspend fun joinGroup(context: Context, pairingCode: String): Boolean {
        val pairingData = FirestoreService.redeemPairingCode(pairingCode) ?: return false

        // Decrypt the sync encryption key using the pairing code as password
        val encryptedKeyBytes = Base64.decode(pairingData.encryptedKey, Base64.NO_WRAP)
        val decryptedKey = try {
            com.syncbudget.app.data.CryptoHelper.decrypt(encryptedKeyBytes, normalizeCode(pairingCode))
        } catch (e: Exception) {
            android.util.Log.w("GroupManager", "Pairing code decrypt failed: ${e.message}")
            return false
        }
        val keyBase64 = Base64.encodeToString(decryptedKey, Base64.NO_WRAP)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("groupId", pairingData.groupId)
            .putBoolean("isAdmin", false)
            .apply()
        // Store decrypted encryption key in encrypted prefs
        SecurePrefs.get(context).edit()
            .putString("encryptionKey", keyBase64)
            .commit()

        // Register this device in the group
        val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
        val deviceName = getDeviceName(context)
        FirestoreService.registerDevice(pairingData.groupId, deviceId, deviceName)

        return true
    }

    suspend fun leaveGroup(context: Context, localOnly: Boolean = false) {
        // Remove device document from Firestore so admin roster updates
        // localOnly=true skips Firestore removal (used for auto-leave on transient errors
        // to avoid permanently deleting the device doc)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val groupId = prefs.getString("groupId", null)
        if (groupId != null && !localOnly) {
            val deviceId = SyncIdGenerator.getOrCreateDeviceId(context)
            try {
                FirestoreService.removeDevice(groupId, deviceId)
            } catch (_: Exception) {}
            try {
                RealtimePresenceService.deletePresenceNode(groupId, deviceId)
            } catch (_: Exception) {}
        }
        prefs.edit()
            .remove("groupId")
            .remove("encryptionKey")
            .remove("isAdmin")
            .remove("lastSnapshotVersion")
            .remove("lastSuccessfulSync")
            .remove("lastIntegrityCheckTime")
            .remove("lastPushedClock")
            .remove("lastSyncVersion")
            .remove("catIdRemap")
            .remove("syncDirty")
            .remove("migration_native_docs_done")
            .remove("migration_per_field_enc_done")
            .apply()
        // Also clear from encrypted prefs
        try { SecurePrefs.get(context).edit().remove("encryptionKey").commit() } catch (_: Exception) {}
        SyncWorker.cancel(context)
    }

    suspend fun dissolveGroup(context: Context, groupId: String, onProgress: ((String) -> Unit)? = null) {
        FirestoreService.deleteGroup(groupId, onProgress)
        leaveGroup(context)
    }

    suspend fun generatePairingCode(context: Context, groupId: String, encryptionKey: ByteArray): String {
        val random = SecureRandom()
        val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous chars
        val code = (1..6).map { codeChars[random.nextInt(codeChars.length)] }.joinToString("")

        // Encrypt the sync key with the pairing code as password.
        // The code is never stored in Firestore — only the encrypted key is.
        // The joining device must know the code to decrypt the key.
        val encryptedKeyBytes = com.syncbudget.app.data.CryptoHelper.encrypt(encryptionKey, normalizeCode(code))
        val encryptedKeyBase64 = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes

        FirestoreService.createPairingCode(groupId, code, encryptedKeyBase64, expiresAt)

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

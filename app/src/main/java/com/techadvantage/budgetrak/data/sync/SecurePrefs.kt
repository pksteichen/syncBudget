package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Provides encrypted SharedPreferences for sensitive sync data (encryption keys).
 */
object SecurePrefs {

    private const val TAG = "SecurePrefs"
    private const val ENCRYPTED_PREFS_NAME = "sync_engine_secure"

    private var cached: SharedPreferences? = null

    /**
     * Get encrypted SharedPreferences, creating if needed.
     * Falls back to plain prefs if encryption is unavailable
     * (e.g., older devices without KeyStore support).
     */
    @Synchronized
    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        val prefs = try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            // KeyStore corruption: delete the encrypted prefs file and retry once
            Log.w(TAG, "EncryptedSharedPreferences failed, retrying after cleanup: ${e.message}")
            try {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                createEncryptedPrefs(context)
            } catch (e2: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences unavailable after retry: ${e2.message}")
                throw IllegalStateException("Secure storage unavailable — re-pairing required", e2)
            }
        }
        cached = prefs
        return prefs
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

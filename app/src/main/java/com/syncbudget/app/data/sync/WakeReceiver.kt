package com.syncbudget.app.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Opportunistic wake receiver that triggers a background sync when the user
 * interacts with the device in ways Android lets us detect via manifest.
 *
 * Rationale: Kim's process gets killed by Samsung's aggressive process
 * management. Once killed, WorkManager runs at most every 15 min. If the
 * user unlocks their phone and uses other apps, the widget can show stale
 * data for up to 15 min. We can't register USER_PRESENT in the manifest
 * (Android restriction), but we CAN listen for charger/headset/power
 * events which often correlate with user activity.
 *
 * Rate-limited to 5 min minimum between wakes to avoid battery drain.
 */
class WakeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WakeReceiver"
        private const val PREFS_NAME = "wake_receiver"
        private const val KEY_LAST_WAKE = "last_wake"
        private const val RATE_LIMIT_MS = 5 * 60 * 1000L  // 5 min
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastWake = prefs.getLong(KEY_LAST_WAKE, 0L)
        val now = System.currentTimeMillis()
        if (now - lastWake < RATE_LIMIT_MS) return

        prefs.edit().putLong(KEY_LAST_WAKE, now).apply()
        Log.i(TAG, "Wake triggered by ${intent.action}")
        BackgroundSyncWorker.runOnce(context)
    }
}

package com.syncbudget.app

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

class BudgeTrakApplication : Application() {

    companion object {
        private val crashlytics: FirebaseCrashlytics? get() = try { FirebaseCrashlytics.getInstance() } catch (_: Exception) { null }

        /** Log to Crashlytics custom log (attached to next crash/non-fatal).
         *  File output only in debug builds. */
        fun tokenLog(msg: String) {
            Log.i("TokenDebug", msg)
            crashlytics?.log(msg)
            if (BuildConfig.DEBUG) {
                try {
                    val dir = com.syncbudget.app.data.BackupManager.getSupportDir()
                    val file = java.io.File(dir, "token_log.txt")
                    if (file.exists() && file.length() > 100_000) file.writeText("")
                    val ts = java.time.LocalDateTime.now().toString()
                    file.appendText("[$ts] $msg\n")
                } catch (_: Exception) {}
            }
        }

        /** Record a non-fatal exception in Crashlytics (shows in dashboard without crash). */
        fun recordNonFatal(tag: String, message: String, exception: Exception? = null) {
            tokenLog("$tag: $message")
            crashlytics?.recordException(exception ?: RuntimeException("$tag: $message"))
        }

        /** Log a sync event to Crashlytics custom log (production) + logcat.
         *  Use for key sync lifecycle events (listener start/stop, recovery, period refresh). */
        fun syncEvent(msg: String) {
            Log.i("SyncEvent", msg)
            crashlytics?.log(msg)
        }

        /** Update Crashlytics diagnostic keys (attached to every future crash/non-fatal). */
        fun updateDiagKeys(keys: Map<String, String>) {
            val c = crashlytics ?: return
            for ((k, v) in keys) c.setCustomKey(k, v)
        }
    }

    override fun onCreate() {
        super.onCreate()

        tokenLog("=== Process started ===")

        // Honor user's Crashlytics opt-out before any Firebase service calls so
        // disabled users never send data, even from this very startup.
        try {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val crashlyticsEnabled = prefs.getBoolean("crashlyticsEnabled", true)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(crashlyticsEnabled)
        } catch (_: Exception) {}

        // Install App Check provider factory early — before any Firebase service calls.
        // Must be in Application.onCreate() (not ViewModel) so it runs even when
        // the process is started by WorkManager without a foreground Activity.
        try {
            val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            appCheck.addAppCheckListener { token ->
                val expiresIn = (token.expireTimeMillis - System.currentTimeMillis()) / 1000
                tokenLog("AppCheck token refreshed: expires in ${expiresIn}s (${expiresIn / 60}m)")
                crashlytics?.setCustomKey("lastTokenExpiry", token.expireTimeMillis)
            }
        } catch (e: Exception) {
            tokenLog("AppCheck init failed: ${e.message}")
        }
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .addAuthStateListener { auth ->
                    val user = auth.currentUser
                    tokenLog("Auth state: uid=${user?.uid ?: "null"} anon=${user?.isAnonymous}")
                    crashlytics?.setUserId(user?.uid ?: "none")
                    crashlytics?.setCustomKey("authAnonymous", user?.isAnonymous == true)
                }
        } catch (e: Exception) {
            tokenLog("Auth listener failed: ${e.message}")
        }
    }
}

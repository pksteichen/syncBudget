package com.techadvantage.budgetrak

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

class BudgeTrakApplication : Application() {

    companion object {
        private val crashlytics: FirebaseCrashlytics? get() = try { FirebaseCrashlytics.getInstance() } catch (_: Exception) { null }

        /**
         * Rotate a debug log file when it exceeds `maxBytes`: current file
         * becomes `<name>_prev.<ext>`, previous `_prev` is discarded. Preserves
         * ~2× the cap worth of history with no in-band "wipe" that loses
         * everything older than the most recent write.
         *
         * Replaces the ad-hoc `if (len > N) writeText("")` pattern that used
         * to live in `tokenLog` / `syncEvent`. Caps are sized so the primary
         * file typically holds ≥ 2 days of history at observed write rates.
         */
        fun rotateLogToPrev(file: java.io.File, maxBytes: Long) {
            try {
                if (!file.exists() || file.length() <= maxBytes) return
                val dir = file.parentFile ?: return
                val base = file.nameWithoutExtension
                val ext = file.extension.ifEmpty { "txt" }
                val prev = java.io.File(dir, "${base}_prev.${ext}")
                if (prev.exists()) prev.delete()
                file.renameTo(prev)
            } catch (_: Exception) {}
        }

        private const val TOKEN_LOG_MAX_BYTES = 128_000L   // ~3 days typical
        private const val FCM_DEBUG_MAX_BYTES = 64_000L    // events are rare; cap for safety

        /** Log to Crashlytics custom log (attached to next crash/non-fatal).
         *  File output only in debug builds. */
        fun tokenLog(msg: String) {
            Log.i("TokenDebug", msg)
            crashlytics?.log(msg)
            if (BuildConfig.DEBUG) {
                try {
                    val dir = com.techadvantage.budgetrak.data.BackupManager.getSupportDir()
                    val file = java.io.File(dir, "token_log.txt")
                    rotateLogToPrev(file, TOKEN_LOG_MAX_BYTES)
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

        /** Log a sync event to Crashlytics custom log (production) + logcat + token_log.txt (debug).
         *  Use for key sync lifecycle events (listener start/stop, recovery, period refresh,
         *  FCM arrivals, RTDB pings, wake events). File output in debug only. */
        fun syncEvent(msg: String) {
            Log.i("SyncEvent", msg)
            crashlytics?.log(msg)
            if (BuildConfig.DEBUG) {
                try {
                    val dir = com.techadvantage.budgetrak.data.BackupManager.getSupportDir()
                    val file = java.io.File(dir, "token_log.txt")
                    rotateLogToPrev(file, TOKEN_LOG_MAX_BYTES)
                    val ts = java.time.LocalDateTime.now().toString()
                    file.appendText("[$ts] $msg\n")
                } catch (_: Exception) {}
            }
        }

        /** Update Crashlytics diagnostic keys (attached to every future crash/non-fatal). */
        fun updateDiagKeys(keys: Map<String, String>) {
            val c = crashlytics ?: return
            for ((k, v) in keys) c.setCustomKey(k, v)
        }

        /**
         * Process-scoped CoroutineScope for fire-and-forget background work
         * that must outlive the calling thread but stay tied to process
         * lifetime. Used by `FcmService` to launch Tier 2 work
         * asynchronously: the FCM-service thread returns immediately so
         * the OS doesn't 10s-kill the service, and the ViewModel's
         * existence keeps the process alive long enough for the work
         * (potentially long — receipt uploads, snapshot building) to
         * complete naturally with no artificial budget.
         *
         * `SupervisorJob` so one failed launch doesn't take down siblings;
         * `Dispatchers.Default` because the body internally switches to
         * `Dispatchers.IO` / `Dispatchers.Main` as needed. Cancelled
         * implicitly when Android kills the process; no explicit teardown.
         */
        val processScope: kotlinx.coroutines.CoroutineScope =
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
            )
    }

    override fun onCreate() {
        super.onCreate()

        tokenLog("=== Process started ===")

        // Honor user's opt-out before any Firebase service calls so disabled
        // users never send data, even from this very startup. The setting is
        // shown as "Send crash reports and anonymous usage data" — one toggle
        // controls both Crashlytics (crashes + consistency non-fatals) and
        // Analytics (ocr_feedback, health_beacon — see data/telemetry/).
        try {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val crashlyticsEnabled = prefs.getBoolean("crashlyticsEnabled", true)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(crashlyticsEnabled)
            com.google.firebase.analytics.FirebaseAnalytics.getInstance(this)
                .setAnalyticsCollectionEnabled(crashlyticsEnabled)
        } catch (_: Exception) {}

        // Stamp build identity on every future crash/non-fatal so BigQuery
        // queries can isolate the latest build's data when many devices in
        // the wild are still running older APKs. versionName is intentionally
        // marketing-only ("2.8") — finer-grained separation lives here.
        try {
            crashlytics?.setCustomKey("buildTime", BuildConfig.BUILD_TIME)
            crashlytics?.setCustomKey("versionCode", BuildConfig.VERSION_CODE)
        } catch (_: Exception) {}

        // Install App Check provider factory early — before any Firebase service calls.
        // Must be in Application.onCreate() (not ViewModel) so it runs even when
        // the process is started by WorkManager without a foreground Activity.
        try {
            val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            // Seed a pinned debug-token UUID into the firebase-appcheck-debug
            // SharedPreferences before installing the factory. The SDK consults this
            // file (template "com.google.firebase.appcheck.debug.store.<persistenceKey>")
            // and reuses any token found there instead of auto-generating a per-install
            // UUID. Lets one Console-registered token cover every debug install on
            // every dev/test device — no re-registration on reinstall or clear-data.
            // Caveat: empty/missing token => SDK falls back to per-install UUID.
            if (BuildConfig.DEBUG && BuildConfig.APP_CHECK_DEBUG_TOKEN.isNotEmpty()) {
                try {
                    val key = com.google.firebase.FirebaseApp.getInstance().persistenceKey
                    val prefs = getSharedPreferences(
                        "com.google.firebase.appcheck.debug.store.$key",
                        MODE_PRIVATE
                    )
                    val existing = prefs.getString(
                        "com.google.firebase.appcheck.debug.DEBUG_SECRET", null
                    )
                    if (existing != BuildConfig.APP_CHECK_DEBUG_TOKEN) {
                        prefs.edit().putString(
                            "com.google.firebase.appcheck.debug.DEBUG_SECRET",
                            BuildConfig.APP_CHECK_DEBUG_TOKEN
                        ).apply()
                        tokenLog("AppCheck debug token seeded from BuildConfig")
                    }
                } catch (e: Exception) {
                    tokenLog("AppCheck debug-token seed failed: ${e.message}")
                }
            }
            val providerFactory = if (BuildConfig.DEBUG)
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            else
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            appCheck.installAppCheckProviderFactory(providerFactory)
            appCheck.addAppCheckListener { token ->
                val expiresIn = (token.expireTimeMillis - System.currentTimeMillis()) / 1000
                tokenLog("AppCheck token refreshed: expires in ${expiresIn}s (${expiresIn / 60}m)")
                crashlytics?.setCustomKey("lastTokenExpiry", token.expireTimeMillis)
            }
            if (BuildConfig.DEBUG) {
                // Capture the debug token from logcat so it's available via
                // FCM dump (token_log.txt) without needing physical access
                try {
                    val process = Runtime.getRuntime().exec(arrayOf(
                        "logcat", "-d", "-s",
                        "com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider:D"
                    ))
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    val match = Regex("debug secret.*: ([a-f0-9-]+)", RegexOption.IGNORE_CASE).find(output)
                    if (match != null) {
                        tokenLog("APP_CHECK_DEBUG_TOKEN: ${match.groupValues[1]}")
                    }
                } catch (_: Exception) {}
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

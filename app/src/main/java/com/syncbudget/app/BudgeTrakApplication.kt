package com.syncbudget.app

import android.app.Application
import android.util.Log

class BudgeTrakApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install App Check provider factory early — before any Firebase service calls.
        // Must be in Application.onCreate() (not ViewModel) so it runs even when
        // the process is started by WorkManager without a foreground Activity.
        try {
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
        } catch (e: Exception) {
            Log.w("AppCheck", "App Check init failed: ${e.message}")
        }
    }
}

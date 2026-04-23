package com.techadvantage.budgetrak.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Firebase Analytics event logging.
 *
 * Anonymous usage telemetry, gated on the same `crashlyticsEnabled`
 * SharedPref as Crashlytics opt-out. The setting is presented to users as
 * "Send crash reports and anonymous usage data" so one toggle controls both.
 *
 * Why Analytics (not Crashlytics non-fatals):
 *  - Analytics is free, Crashlytics non-fatals are rate-limited (10 per session).
 *  - Analytics dashboard is the right tool for per-user-action events;
 *    Crashlytics non-fatals clutter the crash dashboard with fake-crashes.
 *  - BigQuery export is free on Blaze.
 *
 * Events currently logged:
 *  - ocr_feedback       — fires on save of an OCR-populated transaction;
 *                         measures how much the user had to correct.
 *  - health_beacon      — daily heartbeat with sync + inventory diagnostics;
 *                         migrated from the HEALTH_BEACON Crashlytics non-fatal.
 *
 * Actual crash reports, consistency non-fatals, and other abnormal-state
 * signals stay in Crashlytics — that's still the right tool for those.
 */
object AnalyticsEvents {

    private fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("crashlyticsEnabled", true)

    private fun logIfEnabled(context: Context, name: String, bundle: Bundle) {
        if (!isEnabled(context)) return
        try {
            Firebase.analytics.logEvent(name, bundle)
        } catch (_: Exception) {
            // Never let telemetry fail the call site.
        }
    }

    /**
     * Fires when the user saves a transaction that was populated by OCR.
     * Measures how much the user corrected each OCR-provided field.
     *
     * Params (all anonymous; no merchant/amount values, only deltas/booleans):
     *  - merchant_changed:    true if user changed the merchant text
     *  - date_changed:        true if user changed the date
     *  - amount_delta_cents:  signed int, (finalCents - ocrCents)
     *  - cats_added:          count of category ids in final but not in OCR
     *  - cats_removed:        count of category ids in OCR but not in final
     *  - had_multi_cat:       true if OCR returned ≥2 categoryAmounts
     */
    fun logOcrFeedback(
        context: Context,
        merchantChanged: Boolean,
        dateChanged: Boolean,
        amountDeltaCents: Int,
        catsAdded: Int,
        catsRemoved: Int,
        hadMultiCat: Boolean,
    ) {
        val b = Bundle().apply {
            putBoolean("merchant_changed", merchantChanged)
            putBoolean("date_changed", dateChanged)
            putLong("amount_delta_cents", amountDeltaCents.toLong())
            putLong("cats_added", catsAdded.toLong())
            putLong("cats_removed", catsRemoved.toLong())
            putBoolean("had_multi_cat", hadMultiCat)
        }
        logIfEnabled(context, "ocr_feedback", b)
    }

    /**
     * Daily sync-user heartbeat. Previously a Crashlytics non-fatal;
     * migrated to Analytics to keep the crash dashboard clean and to
     * lift the 10-per-session cap (not actually an issue for a daily
     * event, but the right long-term channel).
     *
     * Diagnostic custom keys are still set on the Crashlytics side via
     * BudgeTrakApplication.updateDiagKeys() so they attach to any real
     * crash that does occur.
     */
    fun logHealthBeacon(
        context: Context,
        listenerUp: Boolean,
        activeDevices: Int,
        txnCount: Int,
        reCount: Int,
        plCount: Int,
    ) {
        val b = Bundle().apply {
            putBoolean("listener_up", listenerUp)
            putLong("active_devices", activeDevices.toLong())
            putLong("txn_count", txnCount.toLong())
            putLong("re_count", reCount.toLong())
            putLong("pl_count", plCount.toLong())
        }
        logIfEnabled(context, "health_beacon", b)
    }
}

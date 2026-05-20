package com.techadvantage.budgetrak.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.techadvantage.budgetrak.data.ocr.GeminiHttpClient

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
 *  - ai_call_metrics    — fires on every successful Gemini call; lets us
 *                         track implicit-cache hit ratio + per-feature
 *                         token cost in Firebase Analytics / BigQuery.
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

    /**
     * Fires on every successful Gemini API call across all features
     * (Help Chat, receipt OCR, CSV auto-categorize). Lets us track the
     * implicit-cache hit ratio per feature, per day, per region in
     * Firebase Analytics + BigQuery — without which "did caching
     * actually help us this month?" is a guess.
     *
     * Params (anonymous; no merchant text, no transaction values, no
     * chat content — just counts):
     *  - feature:           "help_chat" / "ocr" / "csv_categorize" — which
     *                       call site fired. Used to slice the hit ratio
     *                       per feature (Help Chat will dominate hits;
     *                       OCR has no stable preamble so should hit near
     *                       0% — that's expected, not a problem).
     *  - model:             the Gemini model name (e.g.
     *                       "gemini-2.5-flash-lite"). Future-proofs the
     *                       data if we ever swap models per feature.
     *  - prompt_tokens:     total input tokens billed to the call.
     *  - cached_tokens:     subset of `prompt_tokens` that hit Google's
     *                       implicit cache (priced at $0.01/M vs $0.10/M).
     *                       Zero means a full-price miss.
     *  - output_tokens:     completion tokens billed at $0.40/M.
     *  - cache_hit_pct:     `cached_tokens * 100 / prompt_tokens`, stored
     *                       as an integer 0-100 for easy bucketing in
     *                       Analytics dashboards.
     *
     * See `feedback_gemini_prompt_caching.md` for monitoring ritual.
     */
    fun logAiCallMetrics(
        context: Context,
        feature: String,
        model: String,
        usage: GeminiHttpClient.UsageMetadata,
    ) {
        val hitPct = if (usage.promptTokens > 0) {
            (usage.cachedTokens * 100L) / usage.promptTokens
        } else {
            0L
        }
        val b = Bundle().apply {
            putString("feature", feature)
            putString("model", model)
            putLong("prompt_tokens", usage.promptTokens.toLong())
            putLong("cached_tokens", usage.cachedTokens.toLong())
            putLong("output_tokens", usage.outputTokens.toLong())
            putLong("cache_hit_pct", hitPct)
        }
        logIfEnabled(context, "ai_call_metrics", b)
    }
}

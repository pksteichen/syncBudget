---
name: Diagnostics Specification
description: How BudgeTrak captures and exports diagnostic state — DiagDumpBuilder, tokenLog, FCM debug dump, Crashlytics custom keys
type: reference
originSessionId: 2ae43715-e466-4f34-8cb2-c1df4c388ef5
---
# Diagnostics Specification

## Goals
- Production observability of sync health + crashes without an active connection to the user.
- Zero cost to healthy devices; only paid users sync, and non-sync users still benefit from local crash capture.
- Debug-only, log-heavy instrumentation that **does not** run in release builds and doesn't bloat release logs.

## Dump button (`Settings → Dump & Sync Debug`, debug builds only)
Triggers the full local + remote diagnostic pipeline:

1. `DiagDumpBuilder.build()` assembles a text state dump (see below).
2. Dump is written to `Download/BudgeTrak/support/sync_diag.txt` via `PublicDownloadWriter` (orphan-safe — see `reference_public_download_writes.md`).
3. If a sync group is configured: encrypted and uploaded to Firestore under a well-known document.
4. An FCM `debug_request` message is sent to other group devices.
5. Each remote device's `FcmService` kicks off `DebugDumpWorker.runOnce()`, which produces its own dump and uploads it.
6. The originating device polls for ~90 s and pulls all available dumps to the support directory.

## `DiagDumpBuilder` — `data/DiagDumpBuilder.kt` (241 lines)

Builds from **disk** (not live state) so a background worker can produce a correct snapshot when the ViewModel is dead. Contents:

- Record counts — active / total per collection, plus tombstone counts.
- Sync metadata — group ID, admin flag, last cursors, enc_hash cache size, pending edits count.
- Settings snapshot — every field in SharedSettings + relevant SharedPreferences (budgetPeriod, resetHour, reset day, manual budget, income mode, match tolerances, etc.).
- Transaction digest — per transaction: id, date, source slug, amount, category, linked IDs, deviceId, deleted flag.
- Period ledger — every entry with epoch-day id, timestamp, appliedAmount, deviceId.
- Cash verification — re-run `recomputeAvailableCash` from the on-disk state and compare to the cached `availableCash`.
- `native_sync_log.txt` tail — last 50 lines of `FirestoreDocSync`'s file log (listener events, pushes, field updates, conflicts, errors).

Also exposes `sanitizeDeviceName()` (used to produce safe device slug prefixes) and `writeDiagToMediaStore()` — since v2.10.03 a thin delegate to `PublicDownloadWriter.writeBytes(relSubdir = "BudgeTrak/support", mimeType = "text/plain", ...)`. Function name kept for backward-compat with existing call sites; no longer touches MediaStore directly.

## `FirestoreDocSync` log file
`Download/BudgeTrak/support/native_sync_log.txt` — rolling file log written by `FirestoreDocSync` on every listener event, push, field update, conflict, or error. Truncates at ~100 KB. Available even when Crashlytics isn't.

## Debug token capture — `BudgeTrakApplication.onCreate`
For debug builds only:
- Installs `DebugAppCheckProviderFactory`.
- **Pinned UUID seed (v2.10.03+)**: before installing the factory, writes `BuildConfig.APP_CHECK_DEBUG_TOKEN` (sourced from `local.properties:APP_CHECK_DEBUG_TOKEN`) into the SDK's `com.google.firebase.appcheck.debug.store.<persistenceKey>` SharedPreferences under key `com.google.firebase.appcheck.debug.DEBUG_SECRET`. The SDK reuses that token instead of auto-generating a fresh UUID per install. One Console-registered token covers every dev/test device — no re-registration on reinstall or clear-data. See `project_token_debug.md` for the registration UUID and rationale.
- Logcat scrape (legacy fallback) still runs with regex `debug secret.*: ([a-f0-9-]+)` and writes any captured token to `support/token_log.txt`. Useful when the seed is empty (BuildConfig.APP_CHECK_DEBUG_TOKEN unset) so per-install UUIDs still get surfaced.

## `token_log.txt`
- Path: `Download/BudgeTrak/support/token_log.txt`.
- Author: `BudgeTrakApplication.tokenLog()`. Also called from many sync code paths to persist event traces across process death.
- Capped at 100 KB (auto-truncates).
- Contains: process starts, App Check token refreshes, auth state changes, PERMISSION_DENIED events, eviction triggers, step-by-step logs in instrumented functions (e.g., `deleteGroup`), dissolve step log.
- **File writes gated by `BuildConfig.DEBUG` since v2.6.** `Crashlytics.log(...)` still runs in release.

## Crashlytics integration — `BudgeTrakApplication`

### Custom keys (`updateDiagKeys`)
Set on every `onResume` + inside daily maintenance beacon:
- `cashDigest` — digest of `availableCash` (hex).
- `listenerStatus` — which listeners are currently connected.
- `lastRefreshDate` — timestamp of last period refresh.
- `activeDevices` — device-roster count.
- `txnCount`, `reCount`, `plCount` — record counts.
- `lastTokenExpiry` — epoch millis of last App Check token expiry.
- `authAnonymous` — whether Firebase Auth is anonymous.

`isSyncConfigured`, `isSyncAdmin`, `syncGroupId` are referenced in conditional logic but are not currently pushed as Crashlytics keys — they come through in custom log lines.

### Non-fatal reports
- `recordNonFatal(tag, Throwable)` for PERMISSION_DENIED in listeners (every occurrence tagged with collection name + auth uid), unexpected exceptions in sync lifecycle, or scheduled `HEALTH_BEACON`.
- `syncEvent(msg)` — Crashlytics custom log line for sync lifecycle (listener start/stop, recovery, worker tiers). Also written to `tokenLog` in debug builds.

### Daily HEALTH_BEACON (now Analytics, see below)
Was originally a Crashlytics non-fatal fired from `runPeriodicMaintenance()` on sync users. **Migrated to Firebase Analytics as the `health_beacon` event** to keep the crash dashboard clean and lift the 10-non-fatals-per-session cap. Diagnostic custom keys are still set on the Crashlytics side via `BudgeTrakApplication.updateDiagKeys()` so they attach to any real crash that does occur. See "Firebase Analytics integration" below for params.

## Firebase Analytics integration — `data/telemetry/AnalyticsEvents.kt`

The `firebase-analytics-ktx` SDK is on the classpath (`app/build.gradle.kts:157`) and **actively used** for two custom event streams. Gated by the **same** `crashlyticsEnabled` SharedPref as Crashlytics — `BudgeTrakApplication.onCreate` (line ~117) calls both `setCrashlyticsCollectionEnabled` and `setAnalyticsCollectionEnabled` with the same value, before any Firebase service is touched. The user-facing setting is "Send crash reports and anonymous usage data" — **single toggle covers both**. `AnalyticsEvents.logIfEnabled` re-checks the pref before every `logEvent` call as belt-and-suspenders.

### Custom events
- **`ocr_feedback`** — fires on save of an OCR-populated transaction. Params: `merchant_changed` (Bool), `date_changed` (Bool), `amount_delta_cents` (signed Long, `finalCents - ocrCents`), `cats_added` / `cats_removed` (Long), `had_multi_cat` (Bool). Production OCR accuracy signal — feeds prompt-iteration decisions. **No raw merchant strings or dollar values sent — only deltas and booleans.**
- **`health_beacon`** — daily sync-user heartbeat. Params: `listener_up` (Bool), `active_devices` / `txn_count` / `re_count` / `pl_count` (Long). Replaces the old Crashlytics `HEALTH_BEACON` non-fatal; lets the crash dashboard stay focused on actual crashes.

### Auto-collected defaults
`first_open`, `session_start`, `screen_view` (auto), `app_remove`, `app_update`, `os_update`, plus `user_pseudo_id` for sessionization. Two opt-outs are configured to keep the auto-collection narrow:
- **IP-derived geo is disabled** at the GA4 property level: GA4 Admin → Data collection and modification → Data collection → "Granular location and device data collection" toggled OFF (set 2026-05-05 alongside the Play Console Data Safety form work). So no Approximate location data lands in the export — keeps the privacy-policy "no location collected" line accurate.
- **Advertising ID linking is disabled** via `<meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />` in `AndroidManifest.xml` (set 2026-05-05). AdMob still collects the ad ID for ad serving on the free tier — that remains the only declared use. Analytics events are no longer linked to the ad ID, so the Device-or-other-IDs declaration on the Data Safety form is justified by AdMob + FCM only.

### Why Analytics, not more Crashlytics non-fatals
- Analytics is free; Crashlytics non-fatals are rate-limited at 10/session.
- Analytics dashboard is the right tool for per-user-action events; non-fatals would clutter the crash dashboard with "fake crashes."
- BigQuery export is free on Blaze.

## BigQuery streaming export

### Crashlytics tables
- Streaming: `com_securesync_app_ANDROID_REALTIME`.
- Batch: `com_securesync_app_ANDROID`.
- Table names still carry the legacy `com_securesync_app` applicationId; Firebase doesn't rename existing export tables after a package rename. Future events continue to land in the same tables.

### Analytics tables
- Dataset: `analytics_534603748`.
- Tables: `events_*` (date-suffixed, populated daily) and `events_intraday_*` (current-day streaming).
- Currently scoped queries pull `event_name IN ('ocr_feedback', 'health_beacon')` — auto-collected defaults are exported but not queried by tooling.

### Query tooling
- `tools/query-crashlytics.js` — Firebase CLI refresh-token based auth (with BigQuery service-account fallback per `reference_bigquery_service_account.md`). Flags: `--nonfatals`, `--crashes`, `--keys`, `--analytics`, `--days N`, `--build YYYY-MM`, `--query "SQL"`.

## FCM

### Service — `data/sync/FcmService.kt`
Single message type today:
- `type = "debug_request"` — triggers a one-shot `DebugDumpWorker.runOnce()` on debug builds only. Release builds ignore.

FCM token is stored in `fcm_prefs` SharedPreferences and flagged for Firestore push on the next sync cycle (so the FCM-sender Cloud Function or local `FcmSender.kt` can target a specific device).

### Sender — `data/sync/FcmSender.kt`
Helper for sending FCM messages from the originating device (debug builds) — used by the Dump button to request dumps from peers. In production the same role could move to a Cloud Function if ever needed.

## Local file map (inside `Download/BudgeTrak/support/`)
- `sync_diag.txt` — full state dump (latest).
- `native_sync_log.txt` — rolling sync log.
- `token_log.txt` — rolling App Check + lifecycle log.
- `logcat_*.txt` — captured logcat, debug builds only.
- `sync_diag_*.txt`, `sync_log_*.txt` — per-device dumps fetched via FCM debug request.

Non-admin dumps also copy to `Download/Quick Share/` so non-admin users can share their dumps without admin access to the support folder.

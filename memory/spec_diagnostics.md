---
name: Diagnostics Specification
description: How BudgeTrak captures and exports diagnostic state — DiagDumpBuilder, tokenLog, FCM debug dump, Crashlytics custom keys
type: reference
---

# Diagnostics Specification

## Goals
- Production observability of sync health + crashes without an active connection to the user.
- Zero cost to healthy devices; only paid users sync, and non-sync users still benefit from local crash capture.
- Debug-only, log-heavy instrumentation that **does not** run in release builds and doesn't bloat release logs.

## Dump button (`Settings → Dump & Sync Debug`, debug builds only)
Triggers the full local + remote diagnostic pipeline:

1. `DiagDumpBuilder.build()` assembles a text state dump (see below).
2. Dump is written to `Download/BudgeTrak/support/sync_diag.txt`.
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

Also exposes `sanitizeDeviceName()` (used to produce safe device slug prefixes) and `writeDiagToMediaStore()` (SAF fallback when direct file write fails).

## `FirestoreDocSync` log file
`Download/BudgeTrak/support/native_sync_log.txt` — rolling file log written by `FirestoreDocSync` on every listener event, push, field update, conflict, or error. Truncates at ~100 KB. Available even when Crashlytics isn't.

## Debug token capture — `BudgeTrakApplication.onCreate`
For debug builds only:
- Installs `DebugAppCheckProviderFactory`.
- Reads logcat with the regex `debug secret.*: ([a-f0-9-]+)` to extract the Firebase-generated debug token.
- Writes the token to `support/token_log.txt` on startup, which is included in any FCM dump upload.
- This means a **new debug token** on a remote tester's device can be retrieved without physical access — request a dump, pull the token out, register it in Firebase Console, done.

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

### Daily HEALTH_BEACON
Inside `runPeriodicMaintenance()` on sync users: `recordNonFatal("HEALTH_BEACON", ...)`. This is why **healthy** production devices show up in the Crashlytics dashboard daily — the dashboard would otherwise only ever reflect broken devices.

## BigQuery streaming export (when enabled in Firebase Console)
- Streaming table: `com_securesync_app_ANDROID_REALTIME`.
- Batch table: `com_securesync_app_ANDROID`.
- Table names still carry the legacy `com_securesync_app` applicationId; Firebase doesn't rename existing export tables after a package rename. Future events continue to land in the same tables.
- Query via `tools/query-crashlytics.js` (Firebase CLI refresh-token based auth). Flags: `--nonfatals`, `--crashes`, `--keys`, `--days N`, `--query "SQL"`.

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

---
name: App Check debug-token management
description: Pinned UUID approach (v2.10.03+) for stable debug tokens across reinstalls; plus the resolved overnight dissolution + PERMISSION_DENIED root causes
type: project
originSessionId: 2ae43715-e466-4f34-8cb2-c1df4c388ef5
---
# App Check debug-token management

## Pinned UUID (v2.10.03 and later)

**Registered token**: `8dc731bc-5213-4b09-bb31-b0382af175f1` — registered in Firebase Console → App Check → Apps → BudgeTrak Android (`com.techadvantage.budgetrak`) → Manage debug tokens, name "BudgeTrak Dev (shared)" (set 2026-05-02).

**Wiring path**:
- `local.properties:APP_CHECK_DEBUG_TOKEN=<uuid>` (gitignored).
- `app/build.gradle.kts` reads it and emits `BuildConfig.APP_CHECK_DEBUG_TOKEN`.
- `BudgeTrakApplication.onCreate` (debug builds only): before `installAppCheckProviderFactory`, writes the UUID into the SDK's prefs at `com.google.firebase.appcheck.debug.store.<FirebaseApp.persistenceKey>` under key `com.google.firebase.appcheck.debug.DEBUG_SECRET`. Skip-if-already-equal so no churn on every cold start.

**Why this is safe**: the pinned UUID is baked into every debug APK, so anyone who decompiles a debug build can extract it. That's fine because (a) debug APKs aren't shipped to end users (release uses Play Integrity, per-install attestation), (b) the UUID only authenticates "BudgeTrak debug" — Firestore/RTDB/Storage rules still enforce per-user/per-group access. Revoke by deleting the Console entry and picking a new UUID.

**When the SDK falls back to per-install UUIDs**:
- Empty `APP_CHECK_DEBUG_TOKEN` in `local.properties` → seed is skipped, SDK auto-generates per install (the v2.6 → v2.10.02 behavior).
- The legacy logcat-scrape fallback in `BudgeTrakApplication.onCreate` (regex `debug secret.*: ([a-f0-9-]+)`) still runs and writes any captured token to `support/token_log.txt`, so per-install UUIDs are still surfaceable via FCM dump for ad-hoc registration.

**Reinstall behavior**:
- Install-over (same debug keystore, no "Uninstall first") → SharedPreferences preserved → SDK reuses existing token, seed is no-op. No Console action.
- Uninstall + install → prefs wiped → seed runs from BuildConfig → SDK uses our pinned UUID. No Console action.
- Clear app data → same as uninstall+install.

**Active registered tokens (Firebase Console)** as of 2026-05-02:
- "BudgeTrak Dev (shared)" — the pinned UUID above. Covers every device built off this repo.
- "Paul's Debug", "Kim's Debug" — leftover per-device tokens. Safe to delete once both devices have run a v2.10.03+ debug build.

---

# Historic root causes (resolved 2026-04-04)

## Group dissolution — TTL misconfiguration
Firestore TTL on `groups/lastActivity` was treating the server timestamp as an absolute expiry time. Since `lastActivity` was always in the past (set to `serverTimestamp()`), every group document was immediately eligible for TTL deletion. Firestore's background TTL job would delete them within 24–72 hours of creation.

**Fix (2026-04-04)**:
- Removed TTL from `lastActivity`.
- Added new `expiresAt` field set to `now + 90 days` (Firestore Timestamp); TTL policy on `expiresAt`.
- Refreshed on every app launch via `updateGroupActivity()`.

## PERMISSION_DENIED — token refresh gaps
App Check tokens (1 h on debug, 40 h on Play Integrity post-2026-04-26) expire during Doze. SDK auto-refresh is deferred. Snapshot listeners die permanently with PERMISSION_DENIED.

**Fixes (2026-04-03 through 2026-04-09)**:
- Token refresh on `onResume`, `onAvailable`, `BackgroundSyncWorker` Tier 2 (proactive — 35-min threshold; later dropped to 16 min on 2026-04-25).
- Force-refresh on PERMISSION_DENIED in listener error handlers (collection + device doc + sharedSettings).
- `triggerFullRestart()` stops all + reconnects (2026-04-06). Since v2.10.02 it also probes group + member existence before token refresh and fires `onGroupDissolved` if either is gone — see `spec_group_management.md`.
- ViewModel keep-alive coroutine: 45-min check, 16-min force-refresh threshold (2026-04-09 / dropped from 35→16 on 2026-04-26).
- All `getAppCheckToken()` calls wrapped with `withTimeoutOrNull(10–15 s)`.
- `BackgroundSyncWorker` Tier 3: proactive App Check refresh before Firestore sync + RTDB ping (2026-04-09). Was missing entirely — root cause of stale device roster on devices where Android kills the ViewModel.

## Diagnostic logging added (2026-04-04)
- `deleteGroup()` logs full call stack when invoked.
- `getGroupHealthStatus` logs every call (not just dissolution) with subscriptionExpiry.
- `updateSubscriptionExpiry` logs elapsed days and PAST/FUTURE status.
- `updateDeviceMetadata` success/failure to tokenLog.
- All eviction paths have unique `(path=...)` identifiers.
- PERMISSION_DENIED on all listeners logged to tokenLog.
- Dump button visible in debug builds regardless of sync state.

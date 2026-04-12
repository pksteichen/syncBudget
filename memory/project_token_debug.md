---
name: Overnight dissolution and PERMISSION_DENIED — RESOLVED
description: Root cause found for overnight group dissolution (TTL misconfiguration) and PERMISSION_DENIED (token refresh gaps). Both fixed 2026-04-04.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
## Root Cause: TTL Misconfiguration (Group Dissolution)
**Firestore TTL on `groups/lastActivity` was treating the server timestamp as an absolute expiry time.** Since `lastActivity` was always in the past (set to `serverTimestamp()`), every group document was immediately eligible for TTL deletion. Firestore's background TTL job would delete them within 24-72 hours of creation.

**Fix (2026-04-04):**
- Removed TTL from `lastActivity` field
- Added new `expiresAt` field set to `now + 90 days` (Firestore Timestamp)
- Added TTL policy on `expiresAt` — Firestore only deletes when this future timestamp passes
- `expiresAt` is refreshed on every app launch via `updateGroupActivity()`

## Root Cause: PERMISSION_DENIED (Token Refresh Gaps)
App Check tokens (1hr lifetime) expire during Doze. SDK auto-refresh is deferred. Snapshot listeners die permanently with PERMISSION_DENIED.

**Fixes deployed (2026-04-03 through 2026-04-09):**
- Token refresh on onResume, onAvailable (network), BackgroundSyncWorker Tier 2 (proactive, 35-min threshold)
- Force-refresh on PERMISSION_DENIED in listener error handlers (collection + device doc + sharedSettings)
- Listener reconnect with exponential backoff (was already present)
- `triggerFullRestart()` stops all + fresh connections on PERMISSION_DENIED (2026-04-06)
- ViewModel keep-alive coroutine: checks every 45 min, force-refreshes if within 35 min of expiry (2026-04-09)
- All `getAppCheckToken()` calls wrapped with `withTimeoutOrNull(10-15s)` to prevent hangs in Doze/network loss (2026-04-09)
- BackgroundSyncWorker Tier 3: proactive App Check refresh before Firestore sync + RTDB ping (2026-04-09). Was missing entirely — root cause of stale device roster on devices where Android kills the ViewModel.

## Diagnostic Logging Added (2026-04-04)
- `deleteGroup()` logs full call stack when invoked
- `getGroupHealthStatus` logs every call (not just dissolution) with subscriptionExpiry value
- `updateSubscriptionExpiry` logs elapsed days and PAST/FUTURE status
- `updateDeviceMetadata` success/failure logged to tokenLog
- All eviction paths have unique `(path=...)` identifiers
- PERMISSION_DENIED on all listeners logged to tokenLog (was only logcat for some)
- Dump button visible in debug builds regardless of sync state

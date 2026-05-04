---
name: In-app update prompts via Play Core
description: Surface "new version available" inside BudgeTrak instead of relying on Play Store's silent background-update model
type: project
originSessionId: 2ae43715-e466-4f34-8cb2-c1df4c388ef5
---
# In-app update prompts — Google Play Core In-App Updates API

## Problem this solves

Play Store doesn't push notifications when a new app version is available. Auto-update happens silently in the background (Wi-Fi + charging, batched), so testers and end users may run an outdated version for hours or days after a release goes live. No built-in mechanism tells a stale-build tester "your version is out of date" without contacting them out-of-band (text, email).

Identified during v2.10.05 release rollout when testers had no signal that build 20 was available — chat log 2026-05-04.

## Solution sketch

Google's `com.google.android.play:app-update-ktx` library exposes an `AppUpdateManager` API that:

1. **Queries Play Store** at app launch (or on `onResume`) to ask whether a newer version of the same app is available on whichever track the user is on (production / open / closed / internal — automatically scoped to the user's eligibility).
2. **Returns metadata** including the available `versionCode`, the update priority (a 0–5 integer the developer sets per release), and the staleness in days.
3. **Triggers one of two update flows** at our choice:
   - **Flexible update** — shows a non-blocking dialog ("New version available — Update?"). User can choose Update, Update Later, or Dismiss. If they update, the new APK downloads in the background while they keep using the current app, then prompts to restart when ready.
   - **Immediate update** — fullscreen blocking flow ("Update required to continue"). Use only when the new version is critical (security, breaking sync change). Locks the user out of the app until the update finishes.

Both flows hand off the actual download + install to Play Store; we never see the bytes. No special permissions needed beyond what we already have. Works for installs sourced from Play Store; sideloaded debug builds get a graceful no-op (the API returns `UPDATE_NOT_AVAILABLE`).

## Implementation outline

- Add dependency: `implementation("com.google.android.play:app-update-ktx:2.1.0")` (or current version).
- New class `data/InAppUpdateManager.kt` (~150 lines) wrapping `AppUpdateManager`. Methods: `checkForUpdate(context)`, `requestFlexibleUpdate(activity)`, `completeFlexibleUpdate()`, `onResume(activity)` (re-checks for resumed updates).
- Wire into `MainActivity.onCreate` — single call after VM init, fires once per app start.
- For flexible updates, observe download state via `InstallStateUpdatedListener` and surface a snackbar ("Update downloaded — Restart now") when ready. Snackbar uses existing `LocalAppToast` system in `Theme.kt`.
- For priority assignment: pass `inAppUpdatePriority` value at CI dispatch time (already a parameter on `r0adkll/upload-google-play@v1` — see `release.yml`, currently hardcoded 0). 0–2 = flexible, 3–4 = flexible-with-stronger-hint, 5 = immediate. Default 0 (purely opt-in via dialog).

## When to ship this

**Not yet.** Skip until production-track launch + ~100 users on production. Reasons:

- During Internal Testing, you have 4–12 testers you can text directly. Building this is overkill for that audience.
- The API requires a *real* version-mismatch with what's on Play Store to test end-to-end. Hard to dogfood until production has at least two non-trivial release iterations.
- Until production launches, version-staleness is monitorable via Crashlytics `versionCode` custom key + the `--list-devices` flag in `tools/query-crashlytics.js` — works fine for the small tester pool.

After production launches (and once the tester pool grows past where individual texts are practical), this becomes high-value infrastructure for keeping users current — especially if a critical bug fix needs broad distribution.

## Estimated scope

- Code: 1 new file (~150 lines), 1 new dependency, ~20 lines of integration in MainActivity.
- Testing: 1 release iteration to dogfood (build N, install, then build N+1, observe the prompt firing on the N install).
- UX: decide whether the prompt appears as a Compose dialog or a Snackbar; what copy reads ("New version available" vs "BudgeTrak v2.X.X is ready"); whether to skip the prompt for opted-out (`crashlyticsEnabled = false`) users (probably not — updates aren't telemetry).

## Reference

- Official guide: https://developer.android.com/guide/playcore/in-app-updates
- Sample (Kotlin): https://github.com/android/play-app-update
- API reference: `com.google.android.play.core.appupdate.AppUpdateManager`

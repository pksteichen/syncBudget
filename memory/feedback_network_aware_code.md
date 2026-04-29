---
name: Network-touching code must be network-aware
description: Every code path that calls Firebase / Cloud Storage / Gemini should fail-fast when offline rather than burn the SDK timeout. Auto-resume on network return is required to avoid 30-min lag windows. Use vm.isNetworkAvailable in foreground; use NetworkUtils.isOnline(context) in background where the VM is dead.
type: feedback
---
Every code path that hits the network must check network state before attempting the call. Firebase / Cloud Storage / Gemini SDKs do not fast-fail offline — they wait the full per-call timeout (typically 30-60 s), and a queue of N items burns N × timeout while doing zero useful work. We've also seen this produce user-visible bugs (queue drainer "no local file" reports during airplane mode, FCM heartbeats stamping a slim-path window with no real work done).

**Why:** Hit during 2026-04-28 dialog testing. User in airplane mode created 12 photo-attached transactions. Drainer started, 12 × 60 s timeouts burned, queued items marked failure, queue persisted but the drainer's exponential backoff sleep meant uploads didn't resume promptly when network returned. Concurrent: BackgroundSyncWorker FCM heartbeats stamped `lastInlineSyncCompletedAt` on offline-skipped runs, suppressing the next 30 min of legitimate sync.

**How to apply:**

1. **At every network-call site, check network state up front.**
   - **Foreground UI/VM code:** read `vm.isNetworkAvailable` (`MainViewModel.kt:265`). It's a Compose `mutableStateOf(true)` kept fresh by `ConnectivityManager.NetworkCallback`. Reads are synchronous and lock-free.
   - **Background code (BackgroundSyncWorker Tier 3, ReceiptSyncManager, etc.) where the VM may be dead:** call `NetworkUtils.isOnline(context)` (`data/sync/NetworkUtils.kt`). Fresh syscall, ~microseconds. Fail-open on permission/exception.

2. **For each kind of operation, decide the offline behavior:**
   - **User-triggered action (Sync Now button, AI OCR sparkle):** show an immediate toast (`syncNowOffline`, `aiOcrOffline`) and early-return. Don't burn the timeout.
   - **Background drainer / sync cycle:** `syncLog("...skipping, offline")` and early-return. Persistent queue stays intact; auto-resume on network recovery does the work.
   - **Periodic refresher (App Check keep-alive, etc.):** `continue` the loop body when offline; the next NetworkCallback recovery will trigger an immediate refresh.

3. **Auto-resume on network recovery.** `MainViewModel.networkCallback.onAvailable` (`:2661`) is the single hook. From there:
   - App Check token refresh fires (existing).
   - `syncStatus` flips back from "offline" (existing).
   - Upload drainer is **cancelAndJoin'd + restarted** if there are pending uploads (added 2026-04-28). The cancel breaks the drainer's exponential backoff sleep so uploads resume immediately rather than waiting out the backoff. **Use `cancelAndJoin` not `cancel`** — the latter is non-blocking and lets two drainers race briefly. Wrap in `viewModelScope.launch(Dispatchers.IO)` so disk I/O (loadPendingUploads) doesn't run on the binder thread.
   - Firestore + RTDB SDKs auto-reconnect; no extra hook needed.

4. **Don't over-trust positive signals.** If a function early-returns on offline, **make sure callers don't treat the early return as "real work done."** The slim-path stamp (`KEY_LAST_INLINE_AT`) was extending its window after offline-skipped FCM runs because `runFullSyncBody` returned `Unit` regardless of what happened inside. Fix: thread a `Boolean` through the chain (`runTier2`/`runTier3` → `runFullSyncBody` → `runFullSyncInline`) so the caller knows whether to stamp.

5. **Don't string-match outcomes.** When an offline gate sets a state to communicate to the UI, use a **typed sentinel** (e.g. `OcrState.Offline` data object) rather than `Failed("OFFLINE")`. The string approach has a small but real collision risk against arbitrary SDK exception messages.

6. **Mind the network-detection capability flag.** Both `MainViewModel.isNetworkAvailable` and `NetworkUtils.isOnline` use `NET_CAPABILITY_INTERNET`, not `NET_CAPABILITY_VALIDATED`. Acceptable trade-off — VALIDATED briefly drops during normal validation cycles — but on a captive portal, we'll false-positive and burn a Firebase timeout. If captive-portal hits become a real-world complaint, revisit.

**The audit recipe:** when shipping a new network-aware code path, run an audit looking for:
- Race conditions on network state (cancel + immediate restart, stale flag reads)
- Auto-resume gaps (anything queued during offline that doesn't drain on recovery)
- Slim-path / timestamp races (work-done flags that get set on early-return)
- String sentinels that should be typed
- Pre-existing network calls that escaped this pass

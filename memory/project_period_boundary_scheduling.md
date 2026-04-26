---
name: Period-boundary scheduling (Phase 3 + Phase 4)
description: Plan to replace the 15-min BackgroundSyncWorker periodic with one-shots scheduled at period boundaries; sync users rely on FCM-inline (Phase 1) for sync events
type: project
originSessionId: ea9e173a-ca3d-4f87-b67a-ceac73953250
---
**Why:** Phase 1 (shipped 2026-04-25) made FCM `sync_push` execute its work
inline in the FcmService 10s window via `BackgroundSyncWorker.runFullSyncInline`,
removing the dependency on WorkManager dispatching the worker before Doze /
App Standby kills the process. Once we confirm FCM-inline is reliable
overnight on Samsung + Pixel, the 15-min periodic worker becomes redundant
for sync events — the only remaining reason to wake the worker is the
period boundary itself (advance period, recompute cash, refresh widget,
~25 ms). Solo users have always had no sync to do, so they're the cleaner
first migration.

**How to apply:** This is the next architectural simplification after Phase 1
overnight verification. Don't ship Phase 3 or Phase 4 until we have one
overnight on real Samsung + Pixel showing FCM-inline closes the Tier-3
cancellation gap (target: ≥ 95% of overnight FCM wakes complete inline).

---

## Phase 3 — Solo user period-boundary scheduling

**Goal:** solo users go from ~96 worker runs/day to ~4 (one per period
boundary). Battery + CPU savings, zero functional loss.

**Implementation:**
1. **New companion** `BackgroundSyncWorker.scheduleNextBoundary(context)`:
   - Reads `budgetPeriod`, `resetHour`, `resetDayOfWeek`,
     `resetDayOfMonth`, `familyTimezone`, `lastRefreshDate` from
     `app_prefs`.
   - Computes next-boundary instant (reuse the boundary-calc logic in
     `PeriodRefreshService` — extract to a shared helper if needed).
   - Enqueues `OneTimeWorkRequestBuilder<BackgroundSyncWorker>` with
     `setInitialDelay(deltaMs)` + `setExpedited` (API 31+).
   - Unique work name `period_boundary_oneshot`, `REPLACE` policy so
     settings changes re-arm cleanly.
2. **New "slim path"** in `runFullSyncBody` (file-private in
   `BackgroundSyncWorker.kt`): when `!isSyncConfigured`, skip everything
   except `runPeriodRefresh` + `recomputeCashFromDisk` +
   `BudgetWidgetProvider.updateAllWidgets`. Already roughly ~25 ms.
3. **Worker re-arms itself**: at the end of every solo run, call
   `scheduleNextBoundary(context)` so the chain perpetuates.
4. **Re-arm on settings changes**: in MainViewModel, anywhere the period
   config changes (`resetHour`, `budgetPeriod`, `resetDayOfWeek`,
   `resetDayOfMonth`, `familyTimezone`, `isSyncConfigured`), call
   `scheduleNextBoundary`. Also call once on
   `BudgeTrakApplication.onCreate` to handle install/upgrade.
5. **Drop the 15-min periodic** for solo users. `schedule(context)` checks
   `isSyncConfigured` and either enqueues the periodic (sync) or
   `scheduleNextBoundary` (solo).

**Risk:** one-shots can be deferred ~10–30 min under deep Doze even with
expedited. Solo users miss a period boundary by minutes — acceptable;
foreground `onResume` runs `runPeriodicMaintenance()` and catches up.

---

## Phase 4 — Sync user period-boundary scheduling

**Goal:** with FCM `runFullSyncInline` reliably handling sync events
(Phase 1), sync users no longer need the 15-min periodic to catch peer
changes. Same one-shot approach + slim BG body.

**Implementation:**
1. **Reuse `scheduleNextBoundary`** from Phase 3 — same one-shot, same
   self-re-arm.
2. **Slim Tier 3 body for boundary-triggered runs**: when `runFullSyncBody`
   is called from a period-boundary one-shot (not from FCM), skip the heavy
   Firestore listener bring-up and just do period refresh + recompute +
   widget. The `sourceLabel` parameter already distinguishes "Boundary"
   from "FCM-sync_push". Add a "Boundary" label that routes to the slim
   body.
3. **Optional 4-hour safety-net periodic** for sync users. Catches FCM
   token-expired-silently, long offline → online transitions, listener
   drift. If FCM has been working, mostly a no-op (cursors current).
4. **WakeReceiver** (POWER_CONNECTED/DISCONNECTED) keeps firing `runOnce`
   — free recovery signal already.

**Risk:** if FCM is silently broken on a peer device (e.g., Play Services
issue), changes don't propagate until next boundary or app open. The 4 h
safety net mitigates.

---

## Phase 4 alternative — keep periodic, slim it

Lighter-touch option: keep the 15-min periodic but make it a no-op for
sync users when:
- Last successful FCM-inline < 30 min ago AND
- No period boundary crossed AND
- No `checksumMismatchAt` flag

Worker exits in <5 ms. Same battery / CPU savings without losing the
"every 15 min something runs" insurance. Easier rollback if FCM proves
unreliable on some devices.

**Recommendation:** ship Phase 1 → observe overnight → decide between full
Phase 3+4 vs Phase 4-alternative based on FCM reliability data. If FCM
inline succeeds ≥ 95% of overnight wakes on both Samsung + Pixel, full
Phase 3+4 is safe. Below that, do Phase 3 (solo) only and Phase 4-alternative
(keep periodic, slim it) for sync users.

---

## Sequencing checklist

- [x] **Phase 1** (2026-04-25): `runFullSyncInline` + `runDebugDumpInline`
      on `BackgroundSyncWorker` companion. **FcmService branches on VM
      lifecycle**: VM alive → async on `BudgeTrakApplication.processScope`
      with no budget (VM keeps the process alive); VM dead → `runBlocking`
      with 8.5 s budget + WM `runOnce` fallback. App Check refresh
      threshold reduced 35→16 min so most inline runs skip the proactive
      refresh path. Initial 7 s / unconditional-budget design (also
      shipped 2026-04-25 then revised same day) was over-broad: it capped
      Tier 2 work (snapshot builds, long upload bursts) that previously
      had ~10 min via WM service binding; the VM-alive async launch
      restores that runtime.
- [x] **Phase 3** (2026-04-25): solo users get one-shot at next period
      boundary via `BackgroundSyncWorker.scheduleNextBoundary`; the
      boundary worker self-rearms at end of every solo run. `schedule()`
      branches on `isSyncConfigured`. `cancel()` cancels both
      periodic + boundary work names. `GroupManager.leaveGroup` calls
      `schedule()` instead of `cancel()` so solo path arms cleanly.
      Boundary instant computed by new `PeriodRefreshService.nextBoundaryAt`.
- [x] **Phase 4-alt** (2026-04-25): sync users keep the 15-min periodic
      but `runTier3` takes a slim path (period refresh + recompute cash +
      widget, ~25 ms) when sourceLabel == "Worker", a fresh FCM-inline
      ran in the last 30 min, and no consistency mismatch is pending.
      `lastInlineSyncCompletedAt` pref written in `runFullSyncInline`
      tail when sourceLabel starts with "FCM-".
- [ ] **Verification**: one week on Samsung S22 + Pixel comparing
      "Worker Tier 3 SLIM" vs "Worker Tier 3" log frequency. If FCM-inline
      proves ≥ 95% reliable, optionally upgrade to full Phase 4 (replace
      periodic with boundary one-shot for sync users too).
- [ ] **Optional follow-on**: gate Tier 3 from claiming snapshot builder
      role — defer to foreground/Tier 2 unless 24 h stale or sole
      photo-capable device. Promote to `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
      when escalated. Not urgent; current snapshot lifecycle's 2 h
      staleness gate covers most failure modes.

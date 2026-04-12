---
name: Period Refresh Specification
description: How BudgeTrak detects period boundaries, creates ledger entries, runs multi-period catch-up, and triggers cash recomputation
type: reference
---

# Period Refresh Specification

## Boundary Detection — calculated sleep (not 30 s polling)

The period refresh loop (in `MainViewModel.kt`, viewModelScope) **sleeps until the next period boundary + 60 s buffer** instead of polling every 30 s.

```
boundaryMs = BudgetCalculator.nextBoundaryMs(budgetPeriod, resetDayOfWeek, resetDayOfMonth, resetHour, timezone)
sleepMs    = (boundaryMs - nowMs + 60_000).coerceIn(60_000, 15 * 60_000L)
delay(sleepMs)
```

`MainViewModel.kt:~2585-2595`. Minimum 60 s, maximum 15 min. Recalculates after every refresh. Loop starts only after `dataLoaded` flips and waits up to 60 s for `initialSyncReceived`.

Timezone rule: when `isSyncConfigured && familyTimezone.isNotEmpty()`, all period calculations use `sharedSettings.familyTimezone`. Solo users use device default.

## currentPeriodStart() — `BudgetCalculator.kt:252-282`

- **DAILY**: today, or yesterday if before `resetHour`.
- **WEEKLY**: most recent `resetDayOfWeek` (1=Mon…7=Sun), using `previousOrSame()`.
- **MONTHLY**: `resetDayOfMonth` of current month; if that date is after today, use previous month.

## Ledger Entry Creation

When `missedPeriods > 0`, `PeriodRefreshService` creates one `PeriodLedgerEntry` per missed period with:

- `periodStartDate = periodDate.atStartOfDay()`
- `appliedAmount = BudgetCalculator.computeFullBudgetAmount(...)` — **live** budgetAmount at the moment the entry is written (reflects current amortization / SG / accelerated-RE deductions).
- `deviceId = ourDeviceId`

Duplicate prevention:
- In-memory check against existing `periodLedger` by `periodStartDate.toLocalDate()`.
- On-disk `dedup()` groups by epoch-day, keeps the entry with the latest `periodStartDate` timestamp per day.

In a sync group the `@Synchronized` `PeriodRefreshService.runOnce()` is called from both foreground and `BackgroundSyncWorker` Tier 3, so the annotation is critical.

## Multi-Period Catch-Up

If a device was offline for N periods:

1. Loop `for period in 0 until missedPeriods`:
   - compute `periodDate`
   - append ledger entry with live `appliedAmount`
   - update savings goals **per period** (each uses its own `periodDate` for remaining calculation, not "today")
   - update RE `setAsideSoFar` **per period** (reset to 0 if an occurrence falls in the period, otherwise add normal-rate or accelerated-rate)
2. Save repos.
3. Call `recomputeCash()`.

### Savings goal update during catch-up
- Target-date: `deduction = roundCents(remaining / periodsUntil(periodDate, targetDate))`, add to `totalSavedSoFar`.
- Fixed-contribution: `deduction = min(contributionPerPeriod, remaining)`.

### RE update during catch-up
- If period contains the next occurrence: reset `setAsideSoFar = 0`, deactivate any temporary acceleration.
- Otherwise: `setAsideSoFar += rate` where `rate = acceleratedRate` if `isAccelerated` else `normalRate`.

## Cash Recomputation Triggers

`recomputeCash()` is **synchronous** (no `launch`, no `withContext`). Called from:

- Data-loaded initialization (once)
- `onResume` (after `dataLoaded` gate)
- `runPeriodicMaintenance()` (sync users)
- Sync merge (`SyncMergeProcessor.applyBatch` delivered to Main)
- Period refresh catch-up
- Manual add / edit / delete of transaction, RE, IS, AE, SG
- Manual unlink / link via edit dialog
- Settings change that affects calc (currency, period, reset, manual budget)
- Budget reset
- Consistency-check recovery (cashHash mismatch)
- Integrity check (after local-only records pushed)

## simAvailableCash

Derived state that replaces the current period's ledger `appliedAmount` with the live `budgetAmount`, so mid-period edits to RE / SG / AE show up immediately in the Solari display. `MainViewModel.kt:377-407`.

## Period refresh defers to initial sync

In a sync group, the refresh loop waits for `FirestoreDocSync.awaitInitialSync()` before running the first time (`MainViewModel.kt:~2530`, 60 s timeout). This prevents offline devices from writing ledger entries with stale budget data before the latest SharedSettings / REs arrive.

The period ledger itself uses create-if-absent per period (first writer wins). Concurrent writers from different devices converge because Firestore `set()` with a stable doc ID is idempotent at the protocol level — later writers see the existing entry and skip the insert.

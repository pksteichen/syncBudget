---
name: Background Sync & Period Refresh
description: How foreground and background share sync/merge/period-refresh logic; history of why this refactor happened
type: project
---

Background sync + period refresh consolidation implemented 2026-03-29 (supersedes `WidgetRefreshWorker` and the old `SyncWorker`).

**Problem the refactor solved.** Period refresh and Firestore sync only ran in the foreground, so the widget showed stale cash after period boundaries or after remote transactions landed while the app was closed.

## Shared components

1. **`SyncMergeProcessor`** (`data/sync/`) — extracted merge logic from MainActivity's `onBatchChanged`. Handles category tag dedup, conflict detection, SharedSettings application. Foreground and background call it identically.

2. **`PeriodRefreshService`** (`data/`) — extracted period refresh logic from MainActivity. Loads from disk, creates ledger entries, accrues SG / RE, computes `budgetAmount` via `BudgetCalculator.computeFullBudgetAmount()`, saves to disk. `@Synchronized` prevents foreground/background race.

3. **`BackgroundSyncWorker`** (`data/sync/`) — every 15 min via WorkManager. Three tiers:
   - App visible (`isAppActive`) → skip.
   - App stopped, ViewModel alive → App Check refresh + listener health + RTDB ping.
   - ViewModel dead → full sync: opens short-lived `FirestoreDocSync` listener (~5–10 s, served largely from Firestore offline cache) → `SyncMergeProcessor` → `PeriodRefreshService` → push pending via `FirestoreDocService` directly (not `SyncWriteHelper` — that requires foreground init) → `recomputeCash()` (catches remote transactions even without a period boundary) → widget update + device metadata (including cash fingerprint).

4. **`DiagDumpBuilder`** (`data/`) — background-capable diagnostic dump generator. `BackgroundSyncWorker` generates fresh dumps on FCM debug requests instead of uploading stale files. Also houses `writeDiagToMediaStore()` and `sanitizeDeviceName()`.

## Widget throttle

`BudgetWidgetProvider.updateAllWidgets()` debounces to once per 5 seconds, preventing frame drops from rapid listener callbacks during period-refresh RE/SG accrual pushes.

## Firestore offline cache

Enabled by default in the Firebase Android SDK. The short-lived background listener gets its initial snapshot from the local cache (free, instant) and only fetches server-side changes.

## JIT lesson (2026-03-30)

Extracting Compose screen branches into wrapper functions creates lambda overhead at the call site — each `onSetFoo = { foo = it }` parameter generates a lambda object (~5 DEX instructions). For branches with many state setters (like `BudgetConfigScreen` with 18 lambdas), the overhead can exceed the instruction savings, making the parent method larger. Pure function extraction (no lambdas) is always safe. See `feedback_jit_extraction.md`.

## How to apply

When modifying sync merge logic, edit `SyncMergeProcessor` — foreground and background both use it. When modifying period refresh, edit `PeriodRefreshService`. `BackgroundSyncWorker` orchestrates both but has no business logic of its own.

There is no longer a `WidgetRefreshWorker` — it was fully absorbed into `BackgroundSyncWorker`.

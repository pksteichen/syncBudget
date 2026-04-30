# BudgeTrak — System Specification Document (SSD)

| | |
|---|---|
| Document / App Version | 2.8 (in development) |
| Date | April 2026 |
| Publisher | Tech Advantage LLC |
| Application ID / Package / Namespace | `com.techadvantage.budgetrak` |
| Platform | Android 9.0+ (minSdk 28, compile/target SDK 34) |
| Language / UI | Kotlin 2.0.21, Jetpack Compose + Material 3 |
| Build | Gradle 8.9 (Kotlin DSL), JVM 17 |
| Code Size | ~100 Kotlin files, ~51,500 lines (v2.8 dev; refreshed at release tags — see §26) |
| Status | Dev — Internal / Technical Reference |

> Rebranded 2026-04-11 from `com.securesync.app` / `com.syncbudget.app`. v2.7 (versionCode=4) is the production release on Google Play; dev branch is v2.8 (versionCode=5).
>
> **What's new in 2.8 (vs 2.7, in dev):** Unified TransactionDialog with header EXPENSE/INCOME pill toggle (replaces three separate Add Income / Add Expense / Edit dialog entry points — same Composable was shared, but title and `sourceLabel` are now derived inside the dialog rather than passed in); single layered Add Transaction icon (deep-blue pulsing plus circle over a static receipt body) replaces the prior +/- IconButton pair on both the Transactions toolbar and the Dashboard nav row, and the standalone Dashboard +/- "transaction bar" was deleted (the pulsing icon now sits as the leftmost item in the existing nav-icon Row); legacy widget intents `ACTION_ADD_INCOME` + `ACTION_ADD_EXPENSE` route to the unified `dashboardShowAddTransaction` state — widget tile UX preserved without breaking older home-screen layouts; refund-receipt auto-flip in OCR prefill (negative `amountCents` → `typeIsExpense=false` + `abs()` for amount fields); preselect-help banner relocated under the photo thumbnail bar (shown only when photos are attached) and now opens the Transactions Help screen as a fullscreen Compose `Dialog` overlay above the AdAwareDialog window (preserves in-progress entries + photos via the dialog staying mounted underneath); OCR parser fix for negative amounts (was silently aborting refund-receipt pipelines because `optInt(..., -1).takeIf { it >= 0 }` rejected legitimate negatives — now uses `Int.MIN_VALUE` sentinel); **network-awareness pass** across receipt sync / AI OCR / Sync Now / BackgroundSyncWorker / App Check refresh / Firebase anonymous auth — each path now fails fast offline rather than burning per-call timeouts, queued receipt uploads + Firebase anonymous auth + `onResume` App Check refresh auto-resume on network recovery via the `networkCallback.onAvailable` hook (drainer restart uses `cancelAndJoin`; auth uses the new idempotent `attemptAnonymousAuth()` helper), and `runFullSyncBody` / `runTier2` / `runTier3` thread a `Boolean` work-done signal so offline-skipped FCM heartbeats don't stamp the slim-path window; Tier 2 receipt-sync `catch` now rethrows `CancellationException` (parity with v2.7 ImageLedgerService fix) and surfaces other exceptions with full stack traces to Crashlytics via `syncEvent`; Tier 2 receipt-sync state propagation (cleared receiptIds now flow back to `vm.transactions` so phantom photo frames don't persist until app restart); **debug-build receipt forensics** — `ReceiptManager.deleteLocalReceipt` / `addToPendingQueue` / `removeFromPendingQueue` log to `token_log.txt` with caller stack, drainer distinguishes concurrent user-delete from genuine file loss, `sync_diag.txt` gains a Receipt Files Audit section listing files-on-disk vs active/tombstone refs vs queue with orphan/missing/queue-without-file/queue-without-ref divergence sets.
>
> **What's new in 2.7 (vs 2.6):** AI Receipt OCR (3-call Lite pipeline, Subscriber-only); AI CSV Categorization (Paid+Subscriber); photo-bar long-press + drag-to-reorder gestures; PDF receipt import; pending-download placeholder handling with explanatory toasts; Cash Flow Simulation promoted from Subscriber-only to Paid+Subscriber; background worker double-fire guard + FCM busy-wait; photo-pipeline hardening (dedupe, orphan cleanup, queue-on-save).

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Architecture](#2-system-architecture)
3. [Solari Flip Display System](#3-solari-flip-display-system)
4. [Budget Period and Calculation System](#4-budget-period-and-calculation-system)
5. Savings Goals and Supercharge System
6. Amortization System
7. Recurring Expenses System
8. Transaction Management System
9. Import / Export Pipeline
10. Encryption System
11. Auto-Categorization System
12. Category Management System
13. Spending Charts System
14. PDF Expense Reports
15. Localization System
16. Theme System
17. SYNC System (Firestore-Native)
18. Receipt Photo System
19. Home Screen Widget
20. Data Models
21. Persistence Strategy
22. PieChartEditor Component
23. Help System
24. Error Handling
25. Android Manifest Configuration
26. Code Statistics
27. Build Configuration
28. Document Revision History

---

## 1. Executive Summary

BudgeTrak is a single-Activity Jetpack Compose Android app for personal budget management, featuring a Solari split-flap display with procedurally generated clack audio, configurable DAILY/WEEKLY/MONTHLY budget periods, multi-device Firestore sync (up to 5 devices) with per-field ChaCha20-Poly1305 encryption, receipt photo sync via Cloud Storage, and local JSON persistence via SafeIO atomic writes. English/Spanish via CompositionLocal; encrypted CSV export/import; home-screen widget.

### Key Features

- Solari-style animated flip display with procedural mechanical audio
- DAILY / WEEKLY / MONTHLY budget periods with auto-refresh and missed-period catch-up
- SYNC: up to 5 devices, per-field encrypted Firestore docs, filtered listeners with per-collection cursors, RTDB presence
- Receipt photos + PDFs (up to 5 per transaction), encrypted Cloud Storage sync, 14-day pruning
- Long-press + drag photo reordering in both the transaction-list swipe panel and the Add/Edit dialog; photos can be repositioned among occupied slots with real-time reshuffle animation
- **AI Receipt OCR** (Subscriber) — Gemini 2.5 Flash-Lite 3-call pipeline auto-fills merchant, date, amount, and category split from a receipt photo; explicit-tap via sparkle icon; long-press-to-pick-target selection
- **AI CSV Categorization** (Paid + Subscriber) — on-device matcher first, Gemini Flash-Lite fallback for unfamiliar merchants; sends only merchant + amount
- Savings Goals with Supercharge surplus allocation (Reduce Contributions / Achieve Goal Sooner)
- Cash-flow simulation with low-point detection (Paid + Subscriber — was Subscriber-only pre-2.7)
- Amortization of large purchases across budget periods
- Recurring expenses with 6 repeat types and auto-matching
- Full transaction CRUD with multi-category pie-chart allocation (drag / calculator / percentage)
- Bank CSV import (generic, US Bank, BudgeTrak, encrypted) with duplicate detection and auto-match chain
- ChaCha20-Poly1305 encrypted export with PBKDF2 key derivation
- Home-screen widget with flip display and quick-add buttons
- Budget calendar and simulation graph screens
- 7 currencies, English / Spanish localization, 4 chart palettes
- 120+ Material icon user-defined categories
- Auto-categorization from merchant history
- Ad-aware dialogs, pulsing scroll arrows, 6-step QuickStart onboarding
- Async data load with learned-timing progress bar (EMA-smoothed, 60 fps)
- Transaction archiving with carry-forward balance

### Technology Stack

| Component | Technology / Version |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.09.03), Material 3, Material Icons Extended |
| Architecture | Single-Activity + AndroidViewModel, Composable navigation |
| SDK | minSdk 28 (Android 9), compile/target SDK 34 |
| Persistence | JSON via SafeIO atomic writes; SharedPreferences for settings |
| Encryption | ChaCha20-Poly1305 AEAD (export: + PBKDF2; sync: direct 256-bit key) |
| Audio | SoundPool + procedurally generated WAV |
| Cloud | Firebase Firestore (BOM 32.7.0), Cloud Storage, Realtime Database |
| Presence | RTDB with server-side `onDisconnect()` |
| Background | WorkManager 2.9.1 |
| Push | Firebase Cloud Messaging |
| Build | Gradle 8.9 (Kotlin DSL), JVM 17, Compose Plugin 2.0.21 |
| AndroidX | activity-compose 1.9.2, core-ktx 1.13.1, lifecycle 2.8.6 |

---

## 2. System Architecture

### 2.1 High-Level Architecture

Single-Activity + AndroidViewModel + Composable navigation.

1. **MainActivity** (`MainActivity.kt`, 2,438 lines) — UI-only shell. Obtains `MainViewModel` via `viewModel()`, observes state, renders screen Composables. No business logic, no saves, no mutable state beyond nav + dialog visibility. `LoadingScreen` gates rendering until `dataLoaded`. Back on main calls `moveTaskToBack(true)` to keep ViewModel + listeners alive.
2. **MainViewModel** (`MainViewModel.kt`, 2,650 lines) — holds ~80 state vars, save functions, sync lifecycle, background loops. Loads data on `Dispatchers.IO` with learned-timing progress, then populates SnapshotStateLists on Main. Companion holds `WeakReference<MainViewModel>` for `BackgroundSyncWorker` listener-health checks.
3. **Navigation** — string-based `mutableStateOf<String>` in ViewModel; keys like `"main"`, `"settings"`, `"transactions"`, etc. (see 2.3).
4. **In-memory data** — loaded from JSON into SnapshotStateLists at init, mutated in memory, saved via SafeIO (temp + rename). `LoadingScreen` uses `LinearProgressIndicator` with EMA segment timings + 60 fps ticker and a 500 ms min display.
5. **Period refresh** — calculates time until next period boundary and sleeps until boundary + 60 s buffer (clamped 60 s – 15 min; see `MainViewModel.kt:2592`). Background refresh every 15 min via `BackgroundSyncWorker`. Shared logic in `PeriodRefreshService` with `@Synchronized`. In sync groups, deferred until `awaitInitialSync()` completes.
6. **System categories** — Other, Recurring, Amortization, Recurring Income auto-provisioned at startup from `DefaultCategories.kt`.
7. **Theme** — `CompositionLocalProvider` injects `SyncBudgetColors` and `AppStrings`.
8. **Sync metadata** — all data classes carry `deviceId` + `deleted` only. No `_clock` fields. Tombstones kept with `deleted=true`; UI filters via `.active`. Conflict detection uses Firestore field-level updates with `lastEditBy`.
9. **Deterministic cash** — `BudgetCalculator.recomputeAvailableCash()` rebuilds cash from period ledger + transactions; `recomputeCash()` runs synchronously for startup ordering.
10. **Auto-push** — save functions push via `SyncWriteHelper`; local-only changes are impossible when sync is configured.
11. **Lifecycle** — `MainActivity.isAppActive` is `@Volatile` set on `ON_START` / cleared on `ON_STOP`. Observer registered after the loading-screen gate (initial `ON_RESUME` missed by design). `onResume()` guards with `if (!dataLoaded) return`. `BackgroundSyncWorker.doWork()` is three-tier: (1) skip if active, (2) check listener health if VM alive via WeakReference, (3) full sync only when VM dead.
12. **Back = Home** — Back on main calls `moveTaskToBack(true)`; ViewModel and listeners stay alive. Sub-screens back-navigate normally.
13. **Consolidated maintenance** — `runPeriodicMaintenance()` called from `onResume`, 24-hour time-gate in SharedPreferences (`lastMaintenanceCheck`). Runs backup check, integrity check, receipt orphan cleanup, receipt storage pruning, admin tombstone cleanup (30-day sub-gate).
14. **Transaction archiving** — `archiveThreshold` in SharedPreferences (default 10,000). When active count exceeds threshold, oldest 25% moved to `transactions_archive.json` with carry-forward balance from `recomputeAvailableCash()`. Archive is view-only; metadata stored in SharedSettings and synced.

### 2.2 Component Architecture

> **Note on line counts.** Per-file line counts were enumerated through v2.7 release; the columns are kept for *relative* magnitude (e.g. `MainViewModel` and `TransactionsScreen` are the dominant single files; most data-layer files are <1 K lines) but no longer maintained per dev cycle. For exact current sizes, run `find app/src/main/java -name "*.kt" | xargs wc -l` against the working tree. v2.8 dev branch totals ~100 Kotlin files / ~51,500 lines (vs v2.7's 94 / 47,000).

| Component | File | Purpose |
|---|---|---|
| MainActivity | MainActivity.kt | UI shell: observes VM, renders screens, `isAppActive` flag, `LoadingScreen`, `moveTaskToBack` back-handler |
| MainViewModel | MainViewModel.kt | State + saves + sync lifecycle + background loops + archiving + maintenance |
| MainScreen | MainScreen.kt | Dashboard: flip display, charts, Supercharge dialog, quick-add, sync indicators |
| TransactionsScreen | TransactionsScreen.kt | Transactions CRUD, import/export, bulk, receipts, archive view |
| BudgetConfigScreen | BudgetConfigScreen.kt | Income sources, period, reset timing, manual override |
| SettingsScreen | SettingsScreen.kt | Currency, categories, matching, language, palettes, widget, archive threshold |
| SavingsGoalsScreen | SavingsGoalsScreen.kt | Target-date and fixed-contribution goals |
| AmortizationScreen | AmortizationScreen.kt | Amortization entries with progress and pause |
| RecurringExpensesScreen | RecurringExpensesScreen.kt | Recurring bills + savings-required simulation |
| SyncScreen | SyncScreen.kt | Group management, device list, sync status |
| BudgetCalendarScreen | BudgetCalendarScreen.kt | Monthly calendar of recurring events |
| SimulationGraphScreen | SimulationGraphScreen.kt | Cash-flow projection timeline |
| QuickStartGuide | QuickStartGuide.kt | 6-step onboarding overlay |
| EncryptedDocSerializer | EncryptedDocSerializer.kt | Per-field encrypt/decrypt, `toFieldMap`, `fieldUpdate`, `fromDoc`, `diffFields` |
| FirestoreDocSync | FirestoreDocSync.kt | Listener lifecycle, per-collection cursors, diff push, conflict detection, `awaitInitialSync()` |
| FirestoreDocService | FirestoreDocService.kt | Low-level `writeDoc`, `updateFields`, `listenToCollection`, cache reads |
| FirestoreService | FirestoreService.kt | Device / group / pairing / admin / subscriptions |
| RealtimePresenceService | RealtimePresenceService.kt | RTDB presence with `onDisconnect()`, device capabilities |
| SyncWriteHelper | SyncWriteHelper.kt | Singleton IO-thread push dispatcher |
| DebugDumpWorker | DebugDumpWorker.kt | One-shot FCM debug dump (debug builds) |
| GroupManager | GroupManager.kt | Group create / join / leave / dissolve |
| ImageLedgerService | ImageLedgerService.kt | Firestore image ledger, event-driven receipt sync |
| ReceiptSyncManager | ReceiptSyncManager.kt | Cloud Storage upload/download, encryption, recovery |
| ReceiptManager | ReceiptManager.kt | Local capture, compression, thumbnails, delete chain |
| ReceiptOcrService | data/ocr/ReceiptOcrService.kt | V18 split-pipeline AI OCR via Gemini 2.5 Flash-Lite (§11.3) |
| NetworkUtils | data/sync/NetworkUtils.kt | Static `isOnline(context)` for code paths without VM access (§17.13 + §18.5 offline gate) |
| SwipeablePhotoRow | SwipeablePhotoRow.kt | Transaction receipt photo UI |
| BudgetWidgetProvider | BudgetWidgetProvider.kt | Widget lifecycle, alarms, debounce throttle |
| WidgetRenderer | WidgetRenderer.kt | Canvas bitmap renderer for flip cards |
| WidgetTransactionActivity | WidgetTransactionActivity.kt | Quick add from widget |
| BackgroundSyncWorker | BackgroundSyncWorker.kt | Three-tier `doWork()` with `Boolean workDone` propagation; publishes `availableCash` in fingerprintJson |
| PeriodRefreshService | PeriodRefreshService.kt | Shared period refresh (`@Synchronized`) |
| SyncMergeProcessor | SyncMergeProcessor.kt | Incoming sync merge: dedup, conflict, settings, add-or-replace |
| FlipDisplay | FlipDisplay.kt | Currency-aware flip-display compositor |
| FlipChar | FlipChar.kt | Animated flip card for strings (sign + currency) |
| FlipDigit | FlipDigit.kt | Animated flip card for digits 0–9 + blank |
| PieChartEditor | PieChartEditor.kt | Interactive pie with drag / 3 entry modes / 6 palettes |
| FlipSoundPlayer | FlipSoundPlayer.kt | Procedural 45 ms mechanical clack at 44.1 kHz |
| BudgetCalculator | BudgetCalculator.kt | Occurrence-based budget calc + deterministic cash recompute |
| SavingsSimulator | SavingsSimulator.kt | Cash-flow simulation for savings-required |
| DuplicateDetector | DuplicateDetector.kt | Amount / date / merchant tolerance matching |
| CsvParser | CsvParser.kt | Generic / US Bank / BudgeTrak / encrypted CSV |
| CryptoHelper | CryptoHelper.kt | ChaCha20-Poly1305 password + direct-key modes |
| AutoCategorizer | AutoCategorizer.kt | Merchant-based category from 6-month history |
| BackupManager | BackupManager.kt | Full app backup + restore |
| BudgeTrakApplication | BudgeTrakApplication.kt | App Check (Play Integrity) init; `tokenLog` / `syncEvent` / `recordNonFatal` Crashlytics+file logging helpers |
| DiagDumpBuilder | DiagDumpBuilder.kt | Diagnostic dump from disk, `writeDiagToMediaStore()`, Receipt Files Audit (debug) |
| ExpenseReportGenerator | ExpenseReportGenerator.kt | Expense reports |
| CategoryIcons | CategoryIcons.kt | 120+ Material icon map |
| AppStrings | AppStrings.kt | String-group interfaces (~800+ fields) |
| EnglishStrings / SpanishStrings | *.kt | Localized implementations |
| TranslationContext | TranslationContext.kt | Translator notes |
| Theme | Theme.kt | Colors, AdAwareDialog, PulsingScrollArrow |

### 2.3 Screen / Navigation Map

```
MainScreen (Dashboard)
├── SettingsScreen ── SettingsHelpScreen
│   └── BudgetConfigScreen ── BudgetConfigHelpScreen
├── TransactionsScreen ── TransactionsHelpScreen
├── SavingsGoalsScreen ── SavingsGoalsHelpScreen
├── AmortizationScreen ── AmortizationHelpScreen
├── RecurringExpensesScreen ── RecurringExpensesHelpScreen
├── SyncScreen ── SyncHelpScreen
├── BudgetCalendarScreen ── BudgetCalendarHelpScreen
├── SimulationGraphScreen ── SimulationGraphHelpScreen
├── DashboardHelpScreen
└── QuickStartGuide (overlay)
```

11 main screens + 11 help screens. All help screens share `HelpComponents.kt`.

#### Navigation Strings

| Key | Screen |
|---|---|
| `main` | MainScreen |
| `settings` | SettingsScreen |
| `budget_config` | BudgetConfigScreen |
| `transactions` | TransactionsScreen |
| `savings_goals` | SavingsGoalsScreen |
| `amortization` | AmortizationScreen |
| `recurring_expenses` | RecurringExpensesScreen |
| `sync` | SyncScreen |
| `budget_calendar` | BudgetCalendarScreen |
| `simulation_graph` | SimulationGraphScreen |
| `dashboard_help` … `simulation_graph_help` | Corresponding 10 help screens |

---

## 3. Solari Flip Display System

### 3.1 Overview

Solari-style mechanical split-flap display for the dashboard budget amount. Three Composables + procedural audio engine.

### 3.2 FlipDisplay (`FlipDisplay.kt`, 258 lines)

Top-level compositor arranging flip cards into the budget readout. Handles:

- Currency-aware formatting (correct decimal places per currency)
- Responsive card sizing via `BoxWithConstraints`
- Leading-zero suppression (blank cards)
- Sign + currency combination card (symbol alone for positive, `-` + symbol for negative)
- Decimal point rendered as bare text between whole and decimal cards
- Optional bottom label; proportional font scaling

Layout: `[Sign+Currency] [Digit] … [Digit] [.] [Digit] [Digit]`

| Constant | Value | Description |
|---|---|---|
| CARD_ASPECT | 1.5 | Height / width ratio |
| GAP | 5 dp | Between cards |
| DOT_WIDTH | 10 dp | Decimal point |
| FRAME_H_PAD | 16 dp | Horizontal frame padding |
| FRAME_V_PAD | 20 dp | Vertical frame padding |
| MAX_CARD_WIDTH | 72 dp | Maximum card width |

### 3.3 Currency Options

| Symbol | Name | Decimals |
|---|---|---|
| $ | US Dollar | 2 |
| EUR | Euro | 2 |
| GBP | British Pound | 2 |
| JPY | Japanese Yen | 0 |
| INR | Indian Rupee | 2 |
| KRW | South Korean Won | 0 |
| Fr | Swiss Franc | 2 |

### 3.4 FlipChar (`FlipChar.kt`, 293 lines)

Animated flip card for arbitrary strings (used for sign + currency card).

- 250 ms flip (`FLIP_DURATION_MS`)
- 3D rotation around horizontal center axis via `graphicsLayer`
- 4 visual layers: static top / bottom halves, flipping top-away / bottom-reveal panels
- Gradient overlays for depth; `LinearOutSlowInEasing`
- Sound callback fires at animation midpoint
- Multi-char symbols (e.g. `Fr`) split across two lines: sign on top, symbol on bottom

### 3.5 FlipDigit (`FlipDigit.kt`, 316 lines)

Animated flip card for digits 0–9 with blank state (digit = -1). Same 250 ms flip mechanics as `FlipChar`, but integer-valued and uses `FlipFontFamily` (defined in `ui/theme/Type.kt` as `FontFamily.Monospace`) for consistent digit widths.

### 3.6 FlipSoundPlayer (`FlipSoundPlayer.kt`, 134 lines)

Synthesizes a mechanical clack at init; writes a RIFF WAV to cache; loads into a SoundPool (max 6 concurrent streams) for low-latency playback.

| Parameter | Value |
|---|---|
| Sample rate | 44,100 Hz |
| Duration | 45 ms |
| Bit depth | 16-bit signed PCM |
| Channels | Mono |
| Max streams | 6 (SoundPool) |
| Volume | 0.8 L/R |

**Synthesis:**

1. Fast-attack exponential decay envelope: `exp(-t * 120)`
2. Secondary mechanical bounce at t=10 ms: `0.3 * exp(-(dt^2) / 0.000002)`
3. Band-limited noise: sines at 800, 1200, 2400 Hz with random phase + 40 % white noise
4. Envelope × signal, scaled to 70 % of `Short.MAX_VALUE`

---

## 4. Budget Period and Calculation System

### 4.1 Budget Period Configuration

| Period | Description | Reset Timing |
|---|---|---|
| DAILY | Resets each day | Reset hour 0–23 |
| WEEKLY | Resets each week | Day-of-week 1–7 (Mon–Sun) |
| MONTHLY | Resets each month | Day-of-month 1–31 |

Stored as `BudgetPeriod` enum in SharedPreferences under `"budgetPeriod"`.

### 4.2 BudgetCalculator (`BudgetCalculator.kt`, 504 lines)

Singleton object normalizing income sources and recurring expenses to the configured period via occurrence-based projection.

| Function | Purpose |
|---|---|
| `generateOccurrences()` | Occurrence dates for a repeat schedule within a date range; supports all 6 `RepeatType` values with fast-forward for early starts |
| `calculateSafeBudgetAmount()` | 1-year projection of income/expense; per-period discretionary budget |
| `countPeriodsCompleted()` | `ChronoUnit` between two dates (DAYS / WEEKS / MONTHS) |
| `currentPeriodStart()` | Current period start via `TemporalAdjusters` and reset-day config |
| `activeAmortizationDeductions()` | Sum per-period deductions for active, unpaused amortization entries |
| `activeSavingsGoalDeductions()` | Sum per-period deductions for active, unpaused goals |
| `calculateAccruedSavingsNeeded()` | Pro-rata accrued savings needed for recurring expenses by cycle position |
| `recomputeAvailableCash()` | Deterministic cash rebuild from ledger + transactions |
| `roundCents()` | Round to 2 decimal places |

`safeBudgetAmount` and `budgetAmount` are `derivedStateOf` in `MainViewModel` (auto-recalculate on income / expense changes).

#### Safe Budget Formula

```
periodsPerYear = DAILY:365.25, WEEKLY:365.25/7, MONTHLY:12.0
totalAnnualIncome   = SUM(src.amount × theoreticalAnnualOccurrences(src.repeatType, src.repeatInterval))
totalAnnualExpenses = SUM(re.amount  × theoreticalAnnualOccurrences(re.repeatType,  re.repeatInterval))
safeBudgetAmount    = max(0, (totalAnnualIncome - totalAnnualExpenses) / periodsPerYear)
```

#### Effective Budget Amount

```
base = if (isManualBudgetEnabled) manualBudgetAmount else safeBudgetAmount
budgetAmount = max(0, base - amortDeductions - savingsDeductions - accelDeductions)
```

#### Amortization Deductions

```
elapsed   = countPeriodsCompleted(startDate, today, budgetPeriod)
deduction = roundCents(amount × (elapsed+1) / totalPeriods)
          - roundCents(amount ×  elapsed    / totalPeriods)
```

#### Savings Goal Deductions

- **Target-date:** `remaining / periodsUntilTarget`
- **Fixed-contribution:** `min(contributionPerPeriod, remaining)`

### 4.3 Budget Period Refresh

Refresh logic is in `PeriodRefreshService` (261 lines), shared by the foreground loop and `BackgroundSyncWorker` (every 15 min). A `@Synchronized` annotation prevents foreground/background races.

**Foreground loop** (`MainViewModel.kt:~2592`) computes the next period boundary and sleeps until `boundary + 60 s` buffer, clamped to `[60 s, 15 min]`. This is **not** a 30-second polling loop.

```kotlin
val sleepMs = (boundaryMs - nowMs + 60_000).coerceIn(60_000, 15 * 60_000L)
```

```
timezone      = if (syncConfigured && familyTimezone.isNotEmpty()) ZoneId.of(familyTimezone) else null
currentPeriod = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth, timezone, resetHour)
missedPeriods = countPeriodsCompleted(lastRefreshDate, currentPeriod, budgetPeriod)
```

When `missedPeriods > 0`:

1. Compute `budgetAmount` via `BudgetCalculator.computeFullBudgetAmount()`
2. Create one ledger entry per missed period with `appliedAmount = budgetAmount`
3. Update each non-paused savings goal's `totalSavedSoFar` per-period
4. Update RE `setAsideSoFar` per-period (reset if occurrence reached, else increment)
5. Update `lastRefreshDate` to today
6. Call `recomputeCash()`
7. Save to SharedPreferences + repos

#### Foreground vs Background

- **Foreground** (viewModelScope) — sleep-to-boundary loop; writes directly to Compose SnapshotStateLists.
- **Background** (`BackgroundSyncWorker`) — three-tier `doWork()`:
  1. App visible → skip.
  2. App stopped, VM alive (via `WeakReference`) → check listener health only, restart dead listeners.
  3. VM dead (process killed) → full sync (auth, Firestore, period refresh, receipts, cash, widget). Pushes ledger entries via `FirestoreDocService.createDocIfAbsent()` and SG/RE updates via `updateFields()`; saves via repos. Publishes `availableCash` in device `fingerprintJson`.

> **Timezone rule:** In a sync group, ALL period calcs use `sharedSettings.familyTimezone` so all devices see the same boundaries.

> **Sync deferral:** When sync is configured, refresh is deferred until `awaitInitialSync()` completes (waits for all 8 collection listeners to deliver initial snapshots — instant for filtered sessions with no new data, up to 30 s for large datasets). Solo users skip this wait.

### 4.4 Manual Budget Override

- `isManualBudgetEnabled` (Boolean) — toggle.
- `manualBudgetAmount` (Float) — user-specified per-period amount.

When enabled, `manualBudgetAmount` replaces `safeBudgetAmount` in all refresh calcs. If manual > safe, the savings-required calc on Recurring Expenses is disabled with a warning.

### 4.5 PeriodLedgerEntry Schema

```kotlin
data class PeriodLedgerEntry(
    val periodStartDate: LocalDateTime,
    val appliedAmount:   Double,
    val corrected:       Boolean = false,  // unused — JSON backward compat
    val deviceId:        String  = ""
) {
    val id: Int get() = periodStartDate.toLocalDate().toEpochDay().toInt()
}
```

No `clock`, `clockAtReset`, or `_clock` fields — removed with the Firestore migration. Only sync metadata is `deviceId`.

**Dedup** (`PeriodLedgerRepository.dedup`):

```kotlin
entries.groupBy { it.id }
       .map { (_, group) -> group.maxByOrNull { it.periodStartDate } ?: group.first() }
```

Load path calls `dedup(...)` and persists back if duplicates were found.

### 4.6 Deterministic Cash Recomputation

`BudgetCalculator.recomputeAvailableCash()` rebuilds available cash from synced data.

**Step 1 — Sum Period Ledger Credits**

```
cash = carryForwardBalance
     + SUM(entry.appliedAmount) for entries where date >= archiveCutoffDate (or budgetStartDate if no archive)
     dedup by epoch day (one entry per date)
```

**Step 2 — Apply Transaction Effects**

*EXPENSE (first match wins):*

1. `linkedSavingsGoalId != null OR linkedSavingsGoalAmount > 0` → **SKIP** (from savings)
2. `linkedAmortizationEntryId != null` → **SKIP** (budgeted via AE deduction)
3. `amortizationAppliedAmount > 0` → `cash -= max(0, amount - amortizationAppliedAmount)` (deleted AE remainder)
4. `linkedRecurringExpenseId != null` with `linkedRecurringExpenseAmount > 0` → `cash += (rememberedAmount - amount)`
5. `linkedRecurringExpenseAmount > 0` with no ID (deleted RE) → `cash += (rememberedAmount - amount)`
6. Regular expense → `cash -= amount`

*INCOME:*

1. `linkedIncomeSourceId != null`: FIXED → no-op; ACTUAL → `cash += (amount - rememberedAmount)`; ACTUAL_ADJUST → no-op
2. `linkedIncomeSourceAmount > 0` (deleted source): ACTUAL → `cash += (amount - rememberedAmount)`; FIXED / ADJUST → no-op
3. `isBudgetIncome == true` → no-op (already budgeted)
4. Non-budget income → `cash += amount`

**Step 3 — Return `roundCents(cash)`**

> **Invariant:** All devices with identical synced data compute identical `availableCash`. The formula is deterministic given the same inputs.

---
## 5. Savings Goals & Supercharge

### 5.1 SavingsGoal Data Model

| Property | Type | Description |
|---|---|---|
| id | Int | random `(1..Int.MAX_VALUE)` rejected against existing ids (was `0..65535` pre-2026-04-27) |
| name | String | Goal name |
| targetAmount | Double | Total to save |
| targetDate | LocalDate? | Deadline (null = fixed-contribution) |
| totalSavedSoFar | Double | Running total (default 0.0) |
| contributionPerPeriod | Double | Per-period amount when targetDate null |
| isPaused | Boolean | Suspends deductions |
| deviceId | String | Sync origin |
| deleted | Boolean | Tombstone |

### 5.2 Goal Types

| Type | Behavior |
|---|---|
| Target-Date | `remaining / periodsUntilTargetDate`; escalates if behind |
| Fixed-Contribution | `min(contributionPerPeriod, remaining)` |

### 5.3 Supercharge

Allocates surplus cash from the current period to goals. Mode is a UI-side selection (enum `SuperchargeMode { REDUCE_CONTRIBUTIONS, ACHIEVE_SOONER }`); it is NOT persisted on the SavingsGoal data class. Dialog shows active unpaused goals with per-goal toggle, mode selector, and preview. On confirm: `totalSavedSoFar` updated, `availableCash` debited, budget recalculated.

### 5.4 Persistence

File: `future_expenditures.json` (legacy name). Repository migrates legacy fields `description → name`, `amount → targetAmount`.

## 6. Amortization

### 6.1 AmortizationEntry Data Model

| Property | Type | Description |
|---|---|---|
| id | Int | random `(1..Int.MAX_VALUE)` rejected against existing ids (was `0..65535` pre-2026-04-27) |
| source | String | Merchant |
| description | String | Optional |
| amount | Double | Total purchase |
| totalPeriods | Int | Spread count |
| startDate | LocalDate | Begin date |
| isPaused | Boolean | Suspends deductions |
| deviceId | String | Sync origin |
| deleted | Boolean | Tombstone |

### 6.2 Logic

- Per-period deduction = `amount / totalPeriods`
- Active iff `elapsed < totalPeriods AND NOT isPaused`
- UI shows "X of Y periods complete" + progress bar

### 6.3 Import Auto-Match

`DuplicateDetector.findAmortizationMatches()` matches imported transactions by amount (percent OR dollar tolerance) AND merchant substring. User prompted "Yes, Amortization" or "No, Regular Expense". On yes: `linkedAmortizationEntryId` set.

## 7. Recurring Expenses

### 7.1 RecurringExpense Data Model

| Property | Type | Description |
|---|---|---|
| id | Int | random `(1..Int.MAX_VALUE)` rejected against existing ids (was `0..65535` pre-2026-04-27) |
| source | String | Merchant |
| description | String | Optional |
| amount | Double | Per-occurrence |
| repeatType | RepeatType | Default MONTHS |
| repeatInterval | Int | Default 1 |
| startDate | LocalDate? | For DAYS/WEEKS/BI_WEEKLY |
| monthDay1 | Int? | For MONTHS/BI_MONTHLY |
| monthDay2 | Int? | Second day for BI_MONTHLY |
| setAsideSoFar | Double | Tracked contributions |
| isAccelerated | Boolean | Per-RE acceleration flag |
| deviceId | String | Sync origin |
| deleted | Boolean | Tombstone |

`isAccelerated` is a per-RE boolean distinct from SavingsGoal Supercharge (per-goal UI selection).

### 7.2 RepeatType (enum in `IncomeSource.kt`)

| Value | Config | Example |
|---|---|---|
| DAYS | startDate + repeatInterval | Every 3 days from Jan 1 |
| WEEKS | startDate + repeatInterval | Every 2 weeks |
| BI_WEEKLY | startDate (14-day gap) | Every other week |
| MONTHS | monthDay1 + repeatInterval | 15th of each month |
| BI_MONTHLY | monthDay1 + monthDay2 | 1st and 15th |
| ANNUAL | monthDay1 + startDate | Yearly |

### 7.3 Budget Impact

`BudgetCalculator.generateOccurrences()` projects over 1-year horizon; total subtracted from income before dividing by period count for safe budget.

### 7.4 SavingsSimulator (517 lines)

Forward-looking cash-flow simulation to size the savings buffer.

**Algorithm:**
1. Horizon = `today + 18 months`
2. Build events:
   - Day-0: `-availableCash` (priority 1)
   - Income occurrences: `+amount` (priority 0)
   - Period deductions: `-budgetAmount` (priority 1)
   - Expense occurrences: `-amount` (priority 2)
3. Sort `compareBy(date).thenBy(priority)` — same-day order is income > period > expense
4. Walk timeline from balance=0, track `(minBalance, minDate)`
5. `savingsRequired = max(0, -minBalance)`

Returns `SimResult(savingsRequired, lowPointDate)`.

### 7.5 Timing Safety

For MONTHS/BI_MONTHLY, `isRecurringDateCloseEnough()` applies +/- 2 day tolerance beyond the user `matchDays` window.

## 8. Transaction Management

### 8.1 Transaction Data Model

| Property | Type | Default | Description |
|---|---|---|---|
| id | Int | req | random `(1..Int.MAX_VALUE)` rejected against local existing ids (was `0..65535` pre-2026-04-27; full positive Int range drops cross-device collision probability to ≈1 in 2.1B per concurrent pair) |
| type | TransactionType | req | EXPENSE or INCOME |
| date | LocalDate | req | Transaction date |
| source | String | req | Merchant |
| description | String | "" | Optional |
| categoryAmounts | List\<CategoryAmount\> | empty | Multi-category allocations |
| amount | Double | req | Total (always positive) |
| isUserCategorized | Boolean | true | False if auto-categorized |
| isBudgetIncome | Boolean | false | Matched as budget income |
| excludeFromBudget | Boolean | false | Skip in cash calc |
| linkedRecurringExpenseId | Int? | null | RE link |
| linkedRecurringExpenseAmount | Double | 0.0 | RE amount remembered at link |
| linkedAmortizationEntryId | Int? | null | AE link |
| amortizationAppliedAmount | Double | 0.0 | Cumulative AE deduction at AE-delete |
| linkedIncomeSourceId | Int? | null | IS link |
| linkedIncomeSourceAmount | Double | 0.0 | IS amount remembered at link |
| linkedSavingsGoalId | Int? | null | SG link |
| linkedSavingsGoalAmount | Double | 0.0 | Amount saved to goal |
| receiptId1–5 | String? | null | Cloud Storage photo IDs |
| deviceId | String | "" | Sync origin |
| deleted | Boolean | false | Tombstone |

`CategoryAmount = { categoryId: Int, amount: Double }`.

### 8.2 Multi-Category Transactions

2-7 categories via `PieChartEditor.kt` (~470 lines). Three entry modes:

| Mode | Behavior |
|---|---|
| Pie Chart | Drag-to-resize handles, min 2 categories |
| Calculator | Dollar inputs; last category auto-fills remainder |
| Percentage | Percent inputs summing to 100%; amounts derived |

### 8.3 Operations

Add / Edit / Delete (tombstone) / Bulk Category Change / Bulk Merchant Edit / Select All / Search by Date, Text, Amount / Filter by Category, Type.

**Save-path invariants (2026-04-27 audit):**

- **Atomic add** — `addTransactionWithBudgetEffect` gates ALL side effects (savings-goal deduction, local list mutation, disk write, Firestore push) behind one `transactions.none { it.id == stamped.id }` check. Double-tap or recomposition replay is a complete no-op rather than partial side effects (pre-fix the SG deduction and Firestore push happened outside the guard).
- **Duplicate dialog non-dismissable** — `DuplicateResolutionDialog` (`TransactionsScreen.kt`) and the widget's inline duplicate dialog (`WidgetTransactionActivity.kt`) require an explicit Keep Existing / Keep New / Keep Both choice; tap-outside and back are no-ops. Pre-fix dismiss silently dropped the new transaction.
- **Multi-category save validation surfaces** — every silent `return@DialogPrimaryButton` in the multi-category branch sets `showValidation = true` and shows `S.transactions.multiCategoryAmountsInvalid` so the dialog never looks dead on bad input.
- **Edit no-op surfaced** — when `onUpdateTransaction`'s `indexOfFirst` returns -1 (target archived or tombstone-purged mid-edit) the user gets a 5 s toast (`editFailedTransactionMissing`) instead of a successful-looking dialog close.
- **`onResume` add-only merge** — `MainViewModel.onResume` reloads disk and ADDs disk-only ids to memory; never overwrites in-memory state. Avoids races against in-flight saves (which write to disk synchronously on Main while the IO read is suspended) and pending sync-merge disk writes (which update memory synchronously and disk via `withContext(IO)` separately).

### 8.4 Linked Transaction Lifecycle

**KEY RULE:** Delete preserves remembered amounts (expense already occurred). Manual unlink clears them (linked-in-error). Applies to ALL four link types: RE, IS, AE, SG.

| Link Type | Link Sets | Cash Effect | On Parent Delete | On Manual Unlink |
|---|---|---|---|---|
| Recurring Expense | id, amount=re.amount | `cash += (remembered - txn.amount)` | id=null, amount PRESERVED | id=null, amount=0.0 |
| Income Source | id, amount=src.amount | FIXED: none; ACTUAL: `cash += (txn.amount - remembered)`; ADJUST: none | id=null, amount PRESERVED | id=null, amount=0.0 |
| Amortization | id only | excluded from cash while linked | id=null, `amortizationAppliedAmount=cumulative` → `cash -= max(0, amount - amortizationAppliedAmount)` | id=null, amortizationAppliedAmount=0.0 |
| Savings Goal | id, amount; `goal.totalSavedSoFar += amount` | excluded (money in savings) | id=null, amount PRESERVED | id=null, amount=0.0, `goal.totalSavedSoFar += amount` (restore) |

### 8.5 Archiving

Triggered when active transaction count > `archiveThreshold` (SharedSettings, default 10000; 0=off).

1. Sort active transactions by date ascending
2. Archive oldest 25% (`archiveThreshold * 0.25`)
3. Compute carry-forward via `BudgetCalculator.recomputeAvailableCash()` for archived subset
4. Append to `transactions_archive.json` (dedup by ID)
5. Remove from active list
6. Update SharedSettings: `archiveCutoffDate`, `carryForwardBalance`, `lastArchiveInfo` (JSON)
7. Recompute available cash from `carryForwardBalance`

Archived view: read via `loadArchivedTransactionsAsync()` on `Dispatchers.IO`; edits update archive file and push to Firestore. Archive metadata syncs; carry-forward yields deterministic cash across devices regardless of archive split. Trigger points: period refresh, import completion, `checkAndTriggerArchive()`; skipped if threshold=0 or pre-initial-sync.

### 8.6 Dashboard Quick-Add

MainScreen quick-add runs the full auto-match chain (duplicate, RE, AE, budget income) then returns to dashboard.

### 8.7 TransactionDialog (Unified Add / Edit, 2.8)

Single Composable used for **all three** transaction-dialog entry points — Add Income, Add Expense, and Edit — across the dashboard quick-add and the Transactions screen. The 2.8 consolidation:

- **Removed parameters:** `title: String` and `sourceLabel: String`. Both are now derived inside the dialog. Title comes from `if (isEdit) S.transactions.editTransaction else S.common.addTransaction` ("Edit Transaction" / "Add Transaction"). `sourceLabel` comes from `if (typeIsExpense) S.common.merchantLabel else S.common.sourceLabel` ("Merchant" / "Source"). Five caller sites updated (3 in TransactionsScreen.kt, 2 in MainActivity dashboard).
- **Internal `typeIsExpense` state:** `var typeIsExpense by remember(isExpense) { mutableStateOf(isExpense) }`. The `isExpense` parameter is still accepted (seeds the initial value), but the *current* type is now mutable. All in-dialog references (validation, save, linked-RE-only sections, source-field label, autocategorize temp Transaction) use `typeIsExpense`, not `isExpense`.
- **Header type pill:** compact two-segment toggle in the dialog header next to the title. EXPENSE side is red (`#F44336`); INCOME side is green (`#4CAF50`). Tapping flips `typeIsExpense`. The label of the source/merchant field flips with the toggle. The dialog header sits in the upper-left half of the title bar; the right half holds the existing AI OCR icon + camera/photo bar entry.
- **Refund auto-flip in OCR prefill:** `LaunchedEffect(ocrState)` `OcrState.Success` handler at `TransactionsScreen.kt:~3493`. When `r.amount < 0` (Call 1's `amountCents` is negative — return / refund receipt), the prefill sets `typeIsExpense = false` and uses `kotlin.math.abs(...)` for `singleAmountText`, `totalAmountText`, and per-category `categoryAmountTexts[..]`. The user sees the type pill swap to INCOME and a positive amount populated. Save validation (`amount < 0`) stays unchanged because the model invariant is "amount always positive, type carries polarity." `CsvParser.kt:869` follows the same convention for bank-import polarity mapping.
- **i18n:** removed `addNewIncomeTransaction` and `addNewExpenseTransaction` strings; added `addTransaction` ("Add Transaction" / "Agregar transacción"). Spanish and English variants + TranslationContext entry updated.
- **Editing existing transactions:** the type pill is also live in Edit mode. A user who realizes a transaction was miscategorized (saved as EXPENSE but actually INCOME) can flip the type without opening a different dialog. There's no dedicated UI to manually clean up linked-recurring-expense state if the type is flipped post-save (linking UI hides itself when `typeIsExpense=false`); current linked state remains in the data model but isn't visible until type flips back.

### 8.8 Help-from-Dialog Overlay (2.8)

The AI preselect-help banner inside an open transaction dialog used to call `vm.currentScreen = "transactions_help"`, which produced two different broken behaviors depending on entry point:

| Entry point | Dialog rendered at | Behavior on `currentScreen` change |
|---|---|---|
| Dashboard quick-add | `MainActivity.DashboardDialogs(...)` outside the screen `when` | Help screen rendered; dialog *stayed* on top because `dashboardShowAddIncome/Expense` flag never cleared. Help was visible only behind the dialog. |
| Transactions page | Inside `TransactionsScreenBranch` | Whole branch stopped composing → dialog disposed → user lost in-progress entries + photos. |

The 2.8 fix routes both paths through a new state, `vm.transactionsHelpOverlayShowing: Boolean`. Tapping the banner sets it `true`. At the top level of `setContent` (after `DashboardDialogs` and the `QuickStartOverlay`), if true, MainActivity renders:

```kotlin
androidx.compose.ui.window.Dialog(
    onDismissRequest = { vm.transactionsHelpOverlayShowing = false },
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
) {
    Surface(modifier = Modifier.fillMaxSize(), ...) {
        TransactionsHelpScreen(
            onBack = { vm.transactionsHelpOverlayShowing = false },
            scrollTarget = vm.transactionsHelpScrollTo,
            onScrollTargetConsumed = { vm.transactionsHelpScrollTo = null }
        )
    }
}
```

Why a `Dialog` and not just a `Surface` overlay: `AdAwareDialog` (used by `TransactionDialog`) creates its own platform Dialog window. A non-Dialog overlay rendered in the main composition window is *behind* that Dialog window in the Window Manager z-order. Putting the help overlay inside its own `Dialog` makes it stack as a sibling Dialog window, and Android Window Manager renders the most recently added Dialog window on top.

The transaction dialog never disposes during the round-trip — its `remember { mutableStateOf(...) }` state survives, so source/amount/date/photos all come back on overlay dismissal. Back arrow on the help screen's TopAppBar and the system back button both call `onDismissRequest` on the overlay Dialog, closing only the overlay.

The other use of `currentScreen = "transactions_help"` (the Transactions screen's top-bar help icon at `MainActivity.kt:2224`) is unchanged — that's a normal screen navigation, not an overlay.

## 9. Import / Export Pipeline

### 9.1 Formats

| Direction | Format | Notes |
|---|---|---|
| Export | BudgeTrak CSV | Header `id,type,date,source,amount,categoryAmounts,isUserCategorized`; categoryAmounts as `catId:amount;...` |
| Export | BudgeTrak Encrypted | Above CSV + ChaCha20-Poly1305 (password). Wire: [16 salt][12 nonce][ct+tag] |
| Import | GENERIC_CSV | Auto-detects delimiter, columns, date format; scores columns; tries 3 mappings |
| Import | US_BANK | Fixed schema: Date, Transaction, Name, Memo, Amount. CREDIT→INCOME. Falls back to generic on failure |
| Import | SECURESYNC_CSV | Native format (name preserved across rebrand). Header must start `id,type,date,` |
| Import | BudgeTrak Encrypted | Decrypted with password then parsed as native CSV |

Enum: `BankFormat { GENERIC_CSV, US_BANK, SECURESYNC_CSV }`.

### 9.2 Import Stages

FORMAT_SELECTION → PARSING (password prompt if encrypted) → PARSE_ERROR (optional keep-partial) → DUPLICATE_CHECK (per-txn chain) → COMPLETE (summary).

### 9.3 Auto-Match Chain (per transaction)

| # | Matcher | Behavior |
|---|---|---|
| 1 | `filterAlreadyLoadedDays` | Pre-filter. ≤5 txns/day → 100% amount match; >5 → ≥80% match, keep unmatched |
| 2 | `findDuplicates` | Amount + date + merchant. Dialog: Ignore / Keep New / Keep Existing / Ignore All. **Radio-button candidate list** (DuplicateDetector actively drives UI) |
| 3 | `findRecurringExpenseMatches` | Amount + merchant + date-near. Prompt: Yes RE / No. Sets `linkedRecurringExpenseId` |
| 4 | `findAmortizationMatches` | Amount + merchant. Prompt: Yes AE / No. Sets `linkedAmortizationEntryId` |
| 5 | `findBudgetIncomeMatches` | INCOME only; merchant + date. Prompt: Yes / No. Sets `isBudgetIncome` |
| 6 | `autoCategorize` | **CSV bank imports only** (US_BANK, GENERIC_CSV). Assigns category from 6-month, 10-most-recent merchant history; falls back to "Other" |
| 7 | Save | Push via `SyncWriteHelper` to Firestore |

**Auto-categorize scope:** `autoCategorize()` runs on parsed bank-format CSVs only. It does NOT run on native BudgeTrak CSV (categories preserved) and does NOT run on manual entry in TransactionDialog.

### 9.4 Match Settings (SharedSettings, synced)

| Key | Default | Purpose |
|---|---|---|
| matchDays | 7 | Date +/- days for duplicate match |
| matchPercent | 1.0 | Percent amount tolerance |
| matchDollar | 1 | Dollar amount tolerance |
| matchChars | 5 | Min common merchant substring |

### 9.5 Amount Match

`amountMatches(a1, a2)` returns true if EITHER:
- `|a1 - a2| / max(|a1|, |a2|) ≤ percentTolerance`
- `|round(a1) - round(a2)| ≤ dollarTolerance`

### 9.6 Merchant Match

Both `source` strings lowercased, stripped to alphanumeric (regex `[^a-z0-9]`) so "Wal-Mart"/"Walmart" match. Then:
- Both < `minChars`: require exact equality
- Else: any common substring of length ≥ `minChars` (sliding window + HashSet)

## 10. Encryption

### 10.1 CryptoHelper.kt (93 lines)

Singleton `object`. Cipher: ChaCha20-Poly1305 (AEAD).

| Param | Value |
|---|---|
| Salt | 16 bytes |
| Nonce | 12 bytes |
| Key | 256 bits |
| PBKDF2 iterations | 100,000 (SHA-256) |

### 10.2 Two Modes

| Mode | Fns | Usage | Wire |
|---|---|---|---|
| Password | `encrypt` / `decrypt` | Export files, backups | `[16 salt][12 nonce][ciphertext+tag]`; PBKDF2 derives key |
| Direct-Key | `encryptWithKey` / `decryptWithKey` | Sync data (raw 256-bit key, bypasses PBKDF2) | `[12 nonce][ciphertext+tag]` |

### 10.3 Key Storage

Sync key lives in SecurePrefs (~59 lines) on Android KeyStore-backed AES256-GCM. Generated at group creation; distributed to joining devices via pairing code (§17.17 Group Management).

## 11. Auto-Categorization

Two layers cooperate on CSV import: a deterministic on-device matcher and an optional AI fallback.

### 11.1 On-Device Matcher (all tiers) — `AutoCategorizer.kt` (53 lines)

Runs only on imported CSV bank-format transactions (§9.3 step 6) and NOT on TransactionDialog manual entry.

**Algorithm:**
1. Look back 6 months from transaction date
2. Find existing transactions where merchant sources share a 5-char common substring (after alphanumeric strip)
3. Take 10 most recent (by date desc)
4. Group by `categoryId`, pick most frequent
5. Fallback: category with tag `"other"` (system category)
6. Return `copy(categoryAmounts = [CategoryAmount(catId, full amount)], isUserCategorized = false)`

### 11.2 AI CSV Categorization (Paid + Subscriber, opt-in) — `AiCategorizerService.kt`

Runs only when the deterministic matcher's confidence is low (fewer than 5 historical matches OR <80% category agreement across those matches) AND the user has enabled "AI categorization" in Settings.

- Model: Gemini 2.5 Flash-Lite via Firebase AI Logic SDK
- Payload per transaction: `{i, merchant, amount}` — index, merchant name, and amount **only**. The transaction date is NOT sent (trimmed in 2.7 for a smaller privacy footprint; merchant is the dominant categorization signal and date adds negligible value).
- Batched at 100 transactions per call; schema-constrained JSON response
- On receipt: maps `i → categoryId`, drops any invalid IDs, merges back into the import. `isUserCategorized = false` so the user sees an "unverified" flag until they review.
- Retry: 3 attempts with exponential backoff on transient errors; total timeout 30 s per call.

Privacy: data is encrypted in transit (HTTPS); Google's Gemini Developer API terms (Blaze tier) provide no-training-use and brief abuse-detection-only logging. No account identifiers, balances, other transactions, or receipt photos are sent.

### 11.3 AI Receipt OCR Pipeline (Subscriber, opt-in trigger) — `data/ocr/ReceiptOcrService.kt`

Triggered by an explicit tap on the AI sparkle in the transaction dialog header. The user long-presses one of the photo slots first to mark it as the OCR target. All four calls use **Gemini 2.5 Flash-Lite** via Firebase AI Logic.

**V18 split-pipeline (1 / 3 / 4 calls depending on path):**

| Path | Conditions | Calls |
|---|---|---|
| Single-call shortcut | `preSelectedCategoryIds.size == 1` OR `c1.itemNames.isEmpty()` | Call 1 only |
| Single-cat | After Call 1, model returns one dominant category | Call 1 → (Call 1.5 ‖ Call 2) → `buildSingleCatResult` |
| Multi-cat | `preSelect.size >= 2` OR `deriveMulti(...)` from Call 2 returns true | Call 1 → (Call 1.5 ‖ Call 2) → Call 3 (per-item prices) → reconcile |

**Call 1 — image → header.** Returns `{merchant, merchantLegalName, date, amountCents, itemNames[], fullTranscript[], notes}`. Marketplace rule (Amazon/eBay/etc. with "Sold by:") sets merchant to the platform, not the seller. No-hallucinated-date rule: empty string when no calendar date is visible. Date defaults to MM/DD/YYYY (US); DD/MM only on explicit non-US signal.

**Call 1.5 — text-only reconciliation.** Re-reads `fullTranscript` to validate `date` and `amountCents` from Call 1 (catches digit-OCR errors and locale date swaps). Falls back silently to Call 1's values on parse error, API error, or **timeout**. Runs in PARALLEL with Call 2; capped at `CALL1R_TIMEOUT_PAST_C2_MS = 2_000` ms past Call 2 completion (v2.8 — refund receipts with multiple negative numbers in the transcript were observed taking ~9 s for the model to reason through, blocking the pipeline; the cap converts that tail into a bounded ~2 s wait).

**Call 2 — image + item names → category scores.** For each item, returns top-K category candidates with scores 0-100. `topChoice` field at top level and `multiCategoryLikely` boolean drive the multi-vs-single routing.

**Call 3 — image + line items → per-item prices.** Multi-cat only. Cents per item; reconciled against Call 1's `amountCents` total (drift ≤ $0.05 acceptable; salesTax preserved exactly).

**Refund-receipt support (v2.8).** `runCall1` and `runCall1Reconcile` use `Int.MIN_VALUE` as the "missing amountCents" sentinel instead of `-1`. Negative cents flow through unmodified. The dialog prefill detects `r.amount < 0` and flips `typeIsExpense = false` + `kotlin.math.abs(...)` for the amount field — refunds become INCOME with positive amount (model invariant: amount always positive, type carries polarity).

**Per-call timing logs (debug only, v2.8).** Every call emits `Call N dispatch (...)` at start and `Call N response after Nms (...)` on response. Plus `Call1.5: timed out 2000ms past C2 — using C1 values` when the cap fires. Logs land in `logcat_Paul.txt` (or per-device dump) for forensic latency analysis.

**Cost model.** ≤ 4 API calls per OCR; Lite tier; no separate fallback model. Caller is `MainViewModel.runOcrOnSlot1(receiptId, preSelectedCategoryIds)`. Single entry point: `ReceiptOcrService.extractFromReceipt`.

## 12. Category Management

### 12.1 Category Data Model

| Property | Type | Description |
|---|---|---|
| id | Int | Unique |
| name | String | Display |
| iconName | String | Key into CATEGORY_ICON_MAP |
| tag | String | System tag (empty for user categories) |
| charted | Boolean | Included in spending charts (default true) |
| widgetVisible | Boolean | Shown in home widget (default true) |
| deviceId | String | Sync origin |
| deleted | Boolean | Tombstone |

### 12.2 Protected System Categories

Tags `"other"`, `"recurring_income"`, `"supercharge"` are protected — cannot be renamed or deleted in UI. Supercharge is `charted=true, widgetVisible=false`.

### 12.3 Icons

`CategoryIcons.kt` (327 lines) — 120+ Material icons grouped thematically (Food & Drink, Transport, Home & Utilities, Health, Education, Entertainment, Photography, Shopping, Pets & Nature, Finance, Travel, Security, Construction, Miscellaneous).

### 12.4 Deletion with Reassignment

- Zero references → delete immediately (tombstone)
- Non-zero references → reassignment dialog; user picks target; all affected transactions updated; then tombstone

## 13. Spending Charts

### 13.1 Chart Types (MainScreen dashboard)

Toggle between Pie (donut) and Bar chart via header icon. State stored in `SharedPreferences.showBarChart`.

### 13.2 Time Ranges

Enum `SpendingRange`:

| Value | Label |
|---|---|
| TODAY | Today |
| THIS_WEEK | This Week |
| ROLLING_7 | 7 Days |
| THIS_MONTH | This Month |
| ROLLING_30 | 30 Days |
| THIS_YEAR | This Year |
| ROLLING_365 | 365 Days |

Persisted in SharedPreferences key `chartRange`.

### 13.3 Color Palettes

Three palettes (selected in Settings, key `chartPalette`): `Bright`, `Pastel`, `Sunset` (default). Each has separate light/dark variants.

## 14. PDF Expense Reports

`data/ExpenseReportGenerator.kt` (365 lines). Called from `TransactionsScreen.kt:1849` via `ExpenseReportGenerator.generateReports(context, toSave, categories, currencySymbol)`.

- One PDF per transaction (multi-page)
- Page 1: Expense Report Form (merchant, date, amount, category breakdown)
- Pages 2-N: Full-size receipt photos (up to 5 slots, via `ReceiptManager.getReceiptFile()`)
- Paper: US Letter (612 x 792 pts), margin 40pt
- Output dir: `BackupManager.getBudgetrakDir()/PDF/`

## 15. Localization

### 15.1 Architecture

Uses Jetpack Compose `CompositionLocal` (bypasses Android XML resources) so language switches live without Activity recreation.

| File | Lines | Role |
|---|---|---|
| `AppStrings.kt` | 1,498 | 22 data classes, 1,393 string field definitions |
| `EnglishStrings.kt` | 1,896 | English impl |
| `SpanishStrings.kt` | 1,882 | Spanish impl |
| `TranslationContext.kt` | 1,477 | Translator notes per field |
| `LocalStrings.kt` | 5 | `CompositionLocal<AppStrings>` provider |

### 15.2 String Groups

CommonStrings, DashboardStrings, SettingsStrings, BudgetConfigStrings, TransactionsStrings, SavingsGoalsStrings, AmortizationStrings, RecurringExpensesStrings, SyncStrings plus matching *HelpStrings for each major screen (DashboardHelp, SettingsHelp, TransactionsHelp, BudgetConfigHelp, SavingsGoalsHelp, AmortizationHelp, RecurringExpensesHelp).

### 15.3 Dynamic Strings

Many fields are lambdas accepting parameters, e.g. `val budgetLabel: (String, String) -> String`, `val superchargeRemaining: (String) -> String`, `val selectedCount: (Int) -> String`. Enables locale-specific argument ordering.

### 15.4 Auto-Capitalize

`TitleCaseUtil.toApaTitleCase()` implements APA title case (capitalize all words except articles/prepositions/conjunctions). Applied in TransactionDialog source + description fields when `autoCapitalize` enabled.

### 15.5 Language Switching

SharedPreferences key `"appLanguage"` (default `"en"`). On change: prefs updated → `AppStrings` reference swapped → `CompositionLocalProvider` re-provides → Composables recompose. No Activity restart.

## 16. Theme System

### 16.1 SyncBudgetColors (Theme.kt, Color.kt)

| Property | Dark | Light |
|---|---|---|
| headerBackground | #1E2D23 (dark green) | #2C2C2C (near black) |
| headerText | #E0E0E0 | #F0E8D8 (off-white) |
| cardBackground | #1A1A1A (charcoal) | #305880 (blue) |
| cardText | #E8D5A0 (warm amber) | #FFFFFF |
| displayBackground | #383838 (dark grey) | #D6E5DE (light green) |
| displayBorder | #4A4A4A | #B8CCC2 (sage) |
| userCategoryIconTint | (LightCardBackground ref) | (LightCardBackground ref) |
| accentTint | DarkCardText | (light variant) |

Static composition local `LocalSyncBudgetColors` + `LocalAdBannerHeight`.

### 16.2 Material 3 Slots

| Slot | Dark | Light |
|---|---|---|
| primary | #E8D5A0 amber | #2E5C80 navy |
| onPrimary | #1A1A1A | #FFFFFF |
| background | #2A3A2F green/grey | #BDD5CC greenish-blue |
| surface | #1A1A1A | #FFFFFF |
| onBackground / onSurface | #E8D5A0 | #1C1B1F |

### 16.3 Typography (`Type.kt`)

| Font | Usage |
|---|---|
| FlipFontFamily (Monospace) | Solari-style flip-digit display |
| headlineLarge | Default, Bold, 24sp, 2sp letter-spacing |

### 16.4 DialogStyle Enum

`{ DEFAULT, DANGER, WARNING }` drives header color: green, dark red (#B71C1C), orange (#E65100) with appropriate text tint.

### 16.5 AdAware Dialogs

- `AdAwareDialog`: custom-positioned `Dialog()` avoiding ad banner overlap; applies `SOFT_INPUT_ADJUST_NOTHING` so keyboard does not shift dialog
- `AdAwareAlertDialog`: drop-in Material3 AlertDialog replacement on top of AdAwareDialog; optional `scrollState` for the pulsing scroll-arrow affordance

### 16.6 Scroll Affordance (bidirectional, v2.6.2)

Every scrollable dialog body and every dropdown body shows pulsing
arrows to indicate further content above / below. Two composables in
`Theme.kt`:

- **`BoxScope.PulsingScrollArrows(scrollState)`** — renders an up-arrow
  at `TopStart` when `scrollState.canScrollBackward`, and a down-arrow
  at `BottomStart` when `scrollState.canScrollForward`. Default
  paddings: `topPadding = 36.dp` (clears `DialogHeader`), `bottomPadding = 50.dp`
  (clears footer buttons); both 24dp-wide icons animate with
  `tween(600ms, Reverse)` bounce, alpha 0.5 onSurface.
- **`ScrollableDropdownContent { … }`** — wrap inside a
  `DropdownMenu` / `ExposedDropdownMenu` when the list may scroll at
  default or enlarged font. Owns its own `ScrollState`, caps height
  at `280.dp`, indents its content by `32.dp` on the start edge so
  items clear the arrow column. Short lists wrap to content size and
  show no arrows.

Down-only `PulsingScrollArrow` still exists for backward compatibility
but new code should prefer `PulsingScrollArrows` (plural).

## 17. SYNC System (Firestore-Native)

### 17.1 Overview

Up to 5 devices per household sync via Firestore (per-field encrypted
docs + filtered snapshot listeners) with RTDB for presence. v2.2
replaced the ~4,000-line CRDT stack with ~1,500 lines of
Firestore-native code. v2.4 added per-collection `updatedAt` cursors
(~300x read-cost drop) and RTDB presence. v2.5 moved startup to async
IO with EMA progress gate, made `recomputeCash()` synchronous, split
`BackgroundSyncWorker` into three tiers, and consolidated checks into
24-h-gated `runPeriodicMaintenance()`. **v2.6** added server-side
FCM fan-out (`onSyncDataWrite`) and a 15-minute heartbeat
(`presenceHeartbeat`) to wake devices Android has put in aggressive
App-Standby Buckets, closing the "widget stale for hours" gap on
Samsung and similar OEMs.

### 17.2 Per-Field Encryption

Each business field is serialized as `enc_fieldName` in its Firestore
doc using `CryptoHelper.encryptWithKey` (ChaCha20-Poly1305 with the
raw 256-bit group key). Metadata fields (`deviceId`, `updatedAt`,
`deleted`, `lastEditBy`) stay plaintext, enabling server-side filters
(`whereGreaterThan("updatedAt", cursor)`), echo filtering via
`lastEditBy`, and minimal-diff `update()` writes.

### 17.3 Firestore Document Structure

    groups/{gid}/transactions/{id}:
      enc_source, enc_amount, enc_description, enc_date,
      enc_categoryAmounts, enc_type, ...     // base64(ChaCha20-P1305)
      deviceId: "plain", updatedAt: ServerTimestamp,
      deleted: false, lastEditBy: "deviceId"

Data classes carry only `deviceId` and `deleted`. Tombstones via
`deleted = true`.

#### Collections (8)

`transactions`, `categories`, `incomeSources`, `recurringExpenses`,
`amortizationEntries`, `savingsGoals`, `periodLedger` (all
`groups/{gid}/<name>/{id}`) plus the singleton
`groups/{gid}/sharedSettings/current`.

### 17.4 Real-Time Sync (Filtered Listeners)

`FirestoreDocSync` attaches 8 listeners (7 collections +
`sharedSettings/current`). Each uses
`whereGreaterThan("updatedAt", cursor)` with a **per-collection**
cursor in SharedPrefs `sync_cursor`. Cursor is saved **after**
`onBatchChanged` applies data, so partial delivery replays only
incomplete collections. Initial attach without a cursor = full read
(reinstall / first sync).

**awaitInitialSync(30 s)** — `markCollectionDelivered()` fires on
every collection's first snapshot (even empty). Gates migrations,
period refresh, cold-start saves. Instant on filtered resumes.

**Cost** — ~\$50K/mo → ~\$160/mo at 100K devices with cursor filters.

#### Listener callback pipeline

1. Mark delivered; prune expired echo keys.
2. Echo filter: drop a change only if in `recentPushes` AND
   `lastEditBy == ourDeviceId`. Pure-echo batches still advance the
   cursor so fresh listeners (e.g., worker cold start) don't
   re-deliver.
3. On `Dispatchers.Default`: composite-hash `enc_*` fields; skip
   decrypt when hash matches `lastSeenEnc`; else decrypt → emit
   `DataChangeEvent`.
4. Conflict: `lastEditBy != ours` AND `localPendingEdits` has this
   key → `isConflict = true`; clear pending on self-echo.
5. `withContext(Main)` → `onBatchChanged(events)`; persist
   `enc_hash_cache.json`; advance cursor to max `updatedAt` in the
   **full** batch (echoes included).

Three-tier threading: decrypt `Default`, UI `Main`, JSON save `IO`.

When `onBatchChanged` sees new `receiptId*` values not present
locally, up to 5 parallel Cloud Storage downloads fire inline — no
`imageLedger` listener, no 15-min worker wait.

### 17.5 Push & Diff

`FirestoreDocSync.pushRecord`:

- No `lastKnownState` → **new**: `set()` with full field map.
- Else → `EncryptedDocSerializer.diffFields` vs last known; empty =
  return; else `updateFields()` with only changed `enc_*` keys.
- `PeriodLedgerEntry` uses `createDocIfAbsent()` (first writer wins).
- `NOT_FOUND` / "No document to update" → fallback `set()`.
- Marks `recentPushes`, `localPendingEdits`, populates `lastSeenEnc`
  and `lastKnownState`; persists both.

`pushRecordsBatch` → `FirestoreDocService.writeBatch` (500-op
chunks). `SyncWriteHelper.pushBatch` retries once then falls back to
individual `pushRecord`. `saveCollection<T>` is the generic
disk-persist helper (optional `hint`).

### 17.6 Conflict Detection

- `lastEditBy` (plaintext) — device that last wrote the doc.
- `localPendingEdits` — unpushed local edits, persisted to
  SharedPrefs `pending_edits`, expired > 1 h old.

Same-field conflict → transaction flagged
`isUserCategorized = false`, pushed back by `SyncMergeProcessor` (or
by `BackgroundSyncWorker.pushSyncSideEffects`). Different-field edits
merge automatically via per-field `update()`.

### 17.7 Enc-Hash Cache

Composite hash of the doc's `enc_*` fields (sorted key=value join,
`.hashCode().toString()`). Persisted to `enc_hash_cache.json` so
cold starts skip decryption of unchanged docs. Populated at **both**
receive time and push time (batch + individual + fallback set).

### 17.8 Echo Filtering

Per-doc key `"{collection}:{docId}"` in `recentPushes`:

- Foreground pushes: TTL 5 s.
- Background worker pushes (period refresh, etc.): persisted to
  SharedPrefs `sync_engine/bgPushKeys`, loaded on next
  `FirestoreDocSync` init with 20-min TTL.
- `lastEditBy != ours` in a recent-push key = someone else edited →
  process, don't filter.

### 17.9 PERMISSION_DENIED Recovery

`triggerFullRestart()` (30-s debounce) stops all listeners,
force-refreshes the App Check token
(`getAppCheckToken(true).await()` under `withTimeoutOrNull(15 s)`),
waits 500 ms, and restarts everything with reset reconnect counters.

Other listener errors use exponential backoff
(5 s × 2^attempts, capped 300 s, max 10 attempts).

### 17.10 Integrity & Consistency Checks

Both in `runPeriodicMaintenance()` (24-h gate from `onResume`, after
initial sync).

**Integrity** — local record IDs via `Source.CACHE` (zero network);
push anything local-only; `recomputeCash()`.

**Consistency (two layers)**:

| Layer | Check | On mismatch |
|-------|-------|-------------|
| 1 | `FirestoreDocService.countActiveDocs(c)` vs local `.active.size` per collection | Clear that cursor (full re-read next sync); `recordNonFatal("CONSISTENCY_COUNT_MISMATCH")` |
| 2 | `cashHash = availableCash.toString().hashCode().toString(16)` written to `groups/{gid}.deviceChecksums[deviceId].{timestamp, counts, cashHash}` | ≥3: majority vote, minority clears cursors. 2: both re-read on confirmed mismatch. <2: skip. 1-h `checksumMismatchAt` confirmation gate. |

Layer-2 digest is hex (`toString(16)`), safe to log. No peer
fingerprint polling; no 5-min health loop.

**Layer-1 periodLedger special-case (v2.6):** `periodLedger` docs
don't carry a `deleted` field (entries are immutable, no client
delete path). `countActiveDocs` skips the `deleted = false` filter
for this collection so the count matches local `.size`. Before the
fix it always returned 0, firing a false mismatch on every
maintenance pass.

### 17.11 Period Ledger Sync

- **Create-if-absent**: `FirestoreDocService.createDocIfAbsent` ensures
  the first device wins the period entry.
- **Deferred**: period refresh waits for `awaitInitialSync()` before
  running — prevents offline-stale writes.
- **Solo skip**: non-sync devices run immediately.
- **Dedup** by epoch day.

### 17.12 Save & Push Architecture

Every save in `MainViewModel` persists to disk, then calls
`SyncWriteHelper.push<Type>(record)` on `Dispatchers.IO`. There's no
`changed` flag — sync-configured installs can't create local-only
writes. Widget transactions push through the same path.

#### Sync Components

| Component | Lines | Purpose |
|-----------|-------|---------|
| EncryptedDocSerializer | 1,039 | Per-field encrypt/decrypt (8 types); `diffFields`, `fieldUpdate`, `toFieldMap` |
| FirestoreDocService | 257 | Filtered listeners; `writeBatch` 500-op chunks; `countActiveDocs`; `Source.CACHE` reads |
| FirestoreDocSync | 927 | Sync coordinator: per-collection cursors, echo filter, conflict detection, enc-hash cache, `awaitInitialSync`, `triggerFullRestart` |
| SyncWriteHelper | 90 | IO singleton: `push<T>`, `pushBatch` with retry + fallback |
| SyncMergeProcessor | 303 | Merge logic (FG + worker): category dedup, conflict push-back, settings application |
| PeriodRefreshService | 275 | Ledger creation + RE/SG accrual (@Synchronized) |
| BackgroundSyncWorker | 610 | Three-tier `doWork()`, 15-min periodic |
| RealtimePresenceService | 198 | RTDB presence + device capabilities |
| FirestoreService | 621 | Device / group / pairing / admin-claim / subscription |
| GroupManager | 213 | Group lifecycle + pairing-code encrypt/decrypt |
| DebugDumpWorker | 86 | One-shot FCM `debug_request`; debug builds only |
| WakeReceiver | 39 | Manifest `ACTION_POWER_CONNECTED/DISCONNECTED` → `runOnce` (5-min rate limit) |
| DiagDumpBuilder | 241 | Diagnostic dump, sanitized device name |
| Helpers | — | FcmSender (134), FcmService (47), SecurePrefs (70), PeriodLedger (73), SyncFilters (37), SyncIdGenerator (20), SubscriptionReminder (109) |

### 17.13 Three-Tier BackgroundSyncWorker

Sync entry point shared by WorkManager (`doWork`) and FcmService
(`runFullSyncInline` companion). Three unique work names:
`WORK_NAME = "period_refresh"` (15-min periodic, sync users),
`ONESHOT_WORK_NAME = "period_refresh_oneshot"` (FCM/wake fallback),
`BOUNDARY_WORK_NAME = "period_boundary_oneshot"` (Phase 3 solo path).

**Schedule branching (v2.7 Phase 3):** `schedule(ctx)` checks
`isSyncConfigured`. Sync users get the periodic worker; solo users get a
one-shot via `scheduleNextBoundary(ctx)` that fires at the next period
boundary (computed via `PeriodRefreshService.nextBoundaryAt` from
`budgetPeriod`/`resetHour`/`resetDayOfWeek`/`resetDayOfMonth`/`familyTimezone`,
clamped 60 s–24 h). The slim Tier 3 path re-arms the next boundary at the
end of every solo run, perpetuating the chain. Solo users go from ~96
worker runs/day to ~1 per period (1/day for DAILY budgets).

`MainViewModel.instance` is a WeakReference, used by Tier 2/3 detection.

| Tier | Condition | Work |
|------|-----------|------|
| 1 | `MainActivity.isAppActive` | Return — foreground owns it |
| 2 | ViewModel alive, `isSyncConfigured` | Proactive App Check refresh (**v2.7: 16-min threshold**, 10-s timeouts); restart dead `docSync` listeners; RTDB `lastSeen` ping (**v2.7: 10-s timeout**); receipt sync (paid/sub only, gated on `!vm.isReceiptSyncActive()`) |
| 3 | ViewModel dead | Slim path OR full sync — see routing below |

Tiers 2–3 short-circuit on `isSyncConfigured == false` — solo users
skip Auth / App Check / RTDB / Firestore entirely.

**Tier 3 slim-vs-full routing (v2.7 Phase 3 + Phase 4-alt):** Tier 3
takes a slim ~25 ms path (period refresh + cash recompute + widget
update only) when `!isSyncConfigured` OR `(sourceLabel == "Worker" &&
lastInlineSyncCompletedAt < 30 min ago && no consistency mismatch
pending)`. Solo users always slim (and re-arm the boundary one-shot at
end). Sync users on the periodic Worker slim when FCM-inline has
recently done the heavy work — periodic stays as a safety net but
avoids redundant Firestore-listener cycles. FCM-triggered runs
(sourceLabel starts with `"FCM-"`) never slim — they signal real data
to fetch. Full Tier 3 is only reached on cold-start or when FCM has
been silent for >30 min.

**Tier 3 receipt-sync positioning (v2.7):** receipt sync was
previously Step 6 (after period refresh, pushes, consistency, RTDB
ping). Samsung power management was observed cancelling Tier 3
runs ~1 min 48 s into the cycle, routinely hitting receipt sync
mid-flight. Moved to Step 2b — immediately after the initial
Firestore merge — so it runs while the worker is young and has the
best chance of completing before any system-level cancellation. The
later steps (period refresh, pushes, consistency, RTDB ping, widget
update) tolerate cancellation: period refresh retries on next run,
pushes are idempotent, consistency has a 1-h cooldown, widget
catches up on next data change.

**Cancellation handling (v2.7):** outer `doWork()` catches
`CancellationException` explicitly and logs `stopReason` (API 31+)
before rethrowing so WorkManager sees the worker as stopped.
`ImageLedgerService.downloadFromCloud` / `getFlagClock` /
`getLedgerEntry` rethrow `CancellationException` instead of
swallowing it as a generic `Exception`, so structured-concurrency
cancellation propagates cleanly. `ReceiptSyncManager.syncReceipts`
logs phase boundaries (step1…step4) so tomorrow's dump can pinpoint
the phase each cycle reached before completion or cancellation.

**Persistent receipt-sync logging (v2.7):** all five
`ReceiptSyncManager` construction sites route their `syncLog`
callback through `BudgeTrakApplication.syncEvent`, so receipt-sync
events land in `token_log.txt` (128 KB rotate) + Crashlytics custom
log + logcat, surviving process death. Context-prefixed messages:
`ReceiptSync(Tier2)`, `ReceiptSync(Tier3)`, `ReceiptSync(SyncNow)`,
`ReceiptSync(onBatch)`, `ReceiptSync(UploadDrainer)`,
`ReceiptSync(FgRetry)`.

**Network-awareness gate (v2.8).** Tier 2 and Tier 3 early-return at
the top of their bodies when `NetworkUtils.isOnline(context) == false`
— skipping App Check refresh, listener restart, RTDB ping, and
receipt sync rather than burning each call's per-SDK timeout. The
return propagates through a new `Boolean workDone` chain
(`runFullSyncBody` → `runTier2` / `runTier3` → `runFullSyncInline`)
so an offline-skipped FCM heartbeat does NOT stamp
`KEY_LAST_INLINE_AT` — that stamp gates the slim path, and a false
stamp would suppress real sync for up to 30 min after network
recovery. `runFullSyncInline` only stamps when `workDone &&
sourceLabel.startsWith("FCM-")`. The `timedOut` flag is now tracked
separately from `workDone`, so the `TIME-BUDGET-EXPIRED` log fires
only on actual `withTimeoutOrNull` expiry — not on offline-skip.

**Tier 2 receipt-sync result propagation (v2.8).** Tier 2 captures
`syncReceipts(txns, devices)`'s returned transaction list and
applies the changed `receiptId1..5` fields back to `vm.transactions`
on `Dispatchers.Main`. Without this, `clearLostReceiptSlot`'s push
echo-filtered through the Firestore listener (the listener skips
`lastEditBy == ourDeviceId`) and the in-memory state stayed stale
until app restart — open dialogs displayed phantom photo frames for
slots that were already cleared on disk. Targeted update (per-id,
only `receiptId1..5`) preserves any concurrent foreground edits.
Tier 2's broad `catch (e: Exception)` now rethrows
`CancellationException` (parity with the v2.7 `ImageLedgerService`
fix) and surfaces other exceptions to Crashlytics + `token_log.txt`
via `syncEvent` with full stack trace.

### 17.14 WakeReceiver

Manifest-registered for `ACTION_POWER_CONNECTED` and
`ACTION_POWER_DISCONNECTED` (the only user-interaction proxies Android
allows static-registered). 5-min rate limit in SharedPrefs, fires
`BackgroundSyncWorker.runOnce()` and logs via `syncEvent()`.
Mitigates aggressive process kills on Samsung One UI.

### 17.15 FCM Wake Architecture (v2.6)

Purely-periodic `WorkManager` work stops firing when Android drops
the app into `rare` / `restricted` App-Standby Buckets — one
2026-04-12 dump showed a 4h46m silence on Kim's device with no
worker activity at all. FCM-triggered wakes bypass this because
high-priority data FCM goes through the same channel that delivered
our `debug_request` even in Doze.

**Server-side Cloud Functions** (v1 API, Node.js 22, `functions/index.js`):

| Function | Trigger | Purpose |
|---|---|---|
| `onSyncDataWrite` | Firestore `onWrite` on `groups/{gid}/{collection}/{id}` for the 8 sync collections | Fan-out high-priority FCM `sync_push` to every group device where `deviceId != lastEditBy`. Writer device is excluded. |
| `presenceHeartbeat` | Pub/Sub `every 15 minutes` | Scan RTDB `groups/*/presence`. For any device with `lastSeen > 15 min` stale, send FCM `heartbeat`. Catches devices whose periodic worker has stopped. |
| `cleanupGroupData` | Firestore `onDelete` on `groups/{gid}` | Cascade-delete subcollections + RTDB + Cloud Storage (unchanged from v2.5). |

**Client-side `FcmService.onMessageReceived` (v2.7 inline architecture):**

The handler **branches by VM lifecycle** — replaced the prior 9-second
busy-wait + WorkManager-enqueue pattern (which forced a 9-s wakelock per
FCM and added WM dispatch latency) with direct in-process invocation:

- **VM alive** (`MainViewModel.instance?.get() != null`) → launch on
  `BudgeTrakApplication.processScope` (file-static `SupervisorJob() +
  Dispatchers.Default`) and return immediately. The VM keeps the
  process alive past `onMessageReceived` returning, so long Tier-2
  work (snapshot building, multi-photo upload bursts, App Check
  refresh on cold cellular) all complete naturally with **no time
  budget**. No WM fallback needed.
- **VM dead** → `runBlocking { runFullSyncInline(ctx, "FCM-$type",
  8_500L) }` (1.5-s headroom in the FCM 10-s window). On budget
  expiry, fall back to `BackgroundSyncWorker.runOnce(ctx)`.

`type = "sync_push"` and `"heartbeat"` use the same routing.
`type = "debug_request"` uses the parallel `runDebugDumpInline` path
(also branches on VM lifecycle); legacy `DebugDumpWorker` is now a
thin fallback delegate.

In-process `AtomicBoolean isRunning` dedup collapses bursts to one run
(observed 2026-04-26: 16 sync_push FCMs in 350 ms during a CSV import
— 1 ran, 15 cleanly skipped). Each FCM arrival logged via
`syncEvent("FCM received: type=$type")` — visible in `token_log.txt`.

`pingRtdbLastSeen` in `BackgroundSyncWorker` logs both success
(`"RTDB lastSeen pinged"`) and failure via `syncEvent()`, giving us
the worker heartbeat in dumps. **v2.7: wrapped in `withTimeoutOrNull(10_000)`**
to prevent indefinite hangs that previously cascaded into WorkManager's
10-min cancel + receipt-sync `Job was cancelled` (observed twice
2026-04-26 — both started ~12 min into a Worker run).

Cost estimate at 40K groups × 2.2 devices × 12 txns/day: ~$150/mo,
dominated by per-write Firestore `devices` subcollection reads + RTDB
heartbeat scan. Optimizations queued in `memory/project_prelaunch_todo.md`
(cache tokens in group doc; server-side debounce; indexed presence
query) drop it toward ~$10-15/mo.

### 17.16 Tombstones

- Soft-delete via `deleted = true`; listeners replicate.
- Admin tombstone cleanup runs at most every 30 days
  (`lastAdminCleanup`), uses RTDB `syncDevices` lastSeen to find the
  oldest device, deletes tombstones older than that minus 1-day buffer.
- Migration push skips tombstones (new groups start clean).
- First-launch purge of local tombstones.

### 17.17 Group Management

**Create (admin)** — 12-char hex groupId + 256-bit random key in
`SecurePrefs` (AES256-GCM / KeyStore); group doc with
`expiresAt = now + 90d` (refreshed on launch);
`familyTimezone = TimeZone.getDefault()`; migration push via
`pushAllRecords` (tombstones filtered). **Never** use `lastActivity`
for TTL — it's always in the past; caused overnight dissolutions
before the 2026-04-04 `expiresAt` fix.

**Pairing code**

- 6 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no 0/O, 1/I).
- `expiresAt = now + 10 min` (Firestore TTL auto-cleans).
- Stored at `pairing_codes/{code}` with `groupId` + encrypted-key
  blob.
- Sync key encrypted with the 6-char code as a **PBKDF2 password**
  (ChaCha20-Poly1305). Raw key never in Firestore.
- `normalizeCode()` (uppercase + trim) on both ends. Deleted on
  redemption.

**Join** — redeem → decrypt key → store groupId + key → register
membership (self, allowed by rules) → register device doc (requires
membership) → listeners pull all data. No push.

**Roles**

| Role | Capabilities |
|------|--------------|
| Admin | Generate codes, rename/remove devices, edit timezone, toggle attribution, dissolve |
| Non-Admin | View/add, leave, claim admin (subscription required) |

**Admin transfer (24-h objection window)** — `adminClaim/current`
sub-doc `{claimantDeviceId, claimedAt, expiresAt, objections}`.
Other devices object via Firestore transaction. Post-expiry resolves
atomically via another transaction (no objections = transfer, any =
reject).

**Remove / leave** — admin sets `removed = true` on device doc;
removed device auto-leaves next sync. Voluntary leave: device doc +
RTDB presence + membership deletes + local clear +
`BackgroundSyncWorker.cancel`.

**Dissolution** — admin deletes its own member doc + group doc.
`cleanupGroupData` Cloud Function (v1 API, Node.js 22 per
`functions/package.json`) cascade-deletes **14 subcollections** (the
8 sync collections + `devices`, `members`, `imageLedger`,
`adminClaim`, legacy `deltas`, `snapshots`), the entire RTDB
`groups/{gid}` node, and all Cloud Storage `groups/{gid}/` objects.
Stay on v1.

Non-admins detect `status = "dissolved"` → auto-leave.

### 17.18 Shared Settings

`sharedSettings/current` — one encrypted doc replicated like all
others. Carries budget period, reset hour/day/week/month,
`familyTimezone`, currency, income mode, `carryForwardBalance`,
`archiveCutoffDate`, `receiptPruneAgeDays`, etc.

### 17.19 Deterministic Cash

    availableCash = carryForwardBalance
                  + sum(periodLedgerCredits since archiveCutoff)
                  + sum(transactionEffects for active transactions)

Cash is not synced; all devices converge to the same value from the
same Firestore state. `recomputeCash()` runs **synchronously** on the
caller (no `Dispatchers.Default`) to prevent startup races.

### 17.20 RTDB Presence

`RealtimePresenceService.kt` — `syncDevices/{gid}/{deviceId}` plus
`presence/{gid}/{deviceId}/lastSeen`. Launch writes `online: true`;
server-side `onDisconnect()` writes `online: false` + timestamp on
any disconnect (crash, battery, airplane). Zero Firestore reads for
presence.

DeviceInfo fields (RTDB): `online`, `photoCapable`, `uploadSpeedBps`,
`uploadSpeedMeasuredAt`. `ReceiptSyncManager` takes `DeviceInfo`
directly — no `FirestoreService.getDevices()` on hot path.

UI status dots: green <2 min, yellow <10 min, gray older. Label
"Last data sent/received" via `snapshotFlow { lastSyncActivity }` +
10-s ticker.

**Prerequisites**: RTDB enabled in Firebase Console,
`google-services.json` includes the RTDB URL, `firebase-database` in
`build.gradle.kts`.

### 17.21 App Check

| Build | Provider | Token TTL |
|-------|----------|-----------|
| Debug | `DebugAppCheckProviderFactory` (token captured from logcat → `token_log.txt`) | **1 h fixed** — Google-imposed by Debug provider, ignores Console setting |
| Release | `PlayIntegrityAppCheckProviderFactory` | **40 h** — set via Firebase Console → Project Settings → Your apps → BudgeTrak → App Check section dropdown (verified 2026-04-26) |

Debug-build observed refresh cadence is therefore 40× higher than
release. All `getAppCheckToken` calls wrap `withTimeoutOrNull(10-15 s)`
to prevent hangs in Doze / network loss.

**Play Integrity advanced settings:** `PLAY_RECOGNIZED` required
(anti-piracy — blocks modified/re-signed APKs); `LICENSED` not
required (don't gate free users on Huawei / degooglified devices);
device integrity = "Don't explicitly check" (per-field encryption is
the actual security boundary; tighten post-launch only if Crashlytics
shows abuse).

**Authentication enforcement:** Monitor (not Enforce). Anonymous Auth
must always succeed for sync setup; downstream rules enforce App Check.

Refresh triggers (v2.7):

- `MainActivity.onResume`
- Network `onAvailable`
- Worker Tier 2/3 proactive (**16-min threshold**, dropped from 35)
- `triggerFullRestart()` on PERMISSION_DENIED
- ViewModel keep-alive loop (check every 45 min, **refresh at 16 min** — dropped from 35 to dedupe with Worker; VM remains as backup if Worker silenced for >45 min by Doze)
- Firebase SDK auto-refresh (~5 min before expiry, in-process only)

### 17.22 Crashlytics + Firebase Analytics + BigQuery

**Crashlytics custom keys** written in `BudgeTrakApplication` + VM
maintenance: `cashDigest`, `listenerStatus`, `lastRefreshDate`,
`activeDevices`, `txnCount`, `reCount`, `plCount`, `lastTokenExpiry`,
`authAnonymous`. Non-fatals on `PERMISSION_DENIED`, consistency
mismatch, and `TOKEN_REFRESH_TIMEOUT` (15-s timeouts in Worker + VM
keep-alive paths).

**Firebase Analytics** (`data/telemetry/AnalyticsEvents.kt`, added
2026-04-22): per-event usage telemetry. Events:
- `health_beacon` — daily sync-user heartbeat from
  `runPeriodicMaintenance`. Migrated 2026-04-22 from a Crashlytics
  non-fatal (which has a 10/session cap and pollutes the crash
  dashboard).
- `ocr_feedback` — fires on save of an OCR-populated transaction;
  measures per-field user-correction rate. Anonymous deltas/booleans
  only — no merchant text or amounts.

Both Crashlytics and Analytics share the `crashlyticsEnabled`
SharedPref toggle (default true). Setting renamed in UI to "Send
crash reports and anonymous usage data".

**Important: Analytics events were silently dropped from 2026-04-22
through 2026-04-26** because the Firebase project wasn't linked to a
GA4 property. Linkage completed 2026-04-26 (property ID 534603748,
stream "BudgeTrak Android" id 14591145419). Modern Firebase Analytics
SDK (BoM 32.x) auto-discovers measurement ID at runtime via
`mobilesdk_app_id` — `google-services.json` doesn't need an explicit
`analytics_service` block.

**BigQuery export** (configured 2026-04-26):
- Crashlytics legacy tables `com_securesync_app_ANDROID*` populated
  through 2026-04-12 (rebrand date). Export for the new app
  `com_techadvantage_budgetrak_ANDROID*` enabled 2026-04-26 (~24 h
  to first data).
- Performance Monitoring + Sessions: same legacy → new transition.
- Firebase Analytics `analytics_<propertyId>` dataset enabled
  2026-04-26 with Daily + Streaming, no advertising IDs (privacy
  hygiene; not running UA campaigns).
- Cloud Messaging + Imported Segments: not exported (not needed yet).

## 18. Receipt Photo System

### 18.1 Overview

Users attach up to 5 photos or PDFs per transaction via
`receiptId1`..`receiptId5`. Paid users sync encrypted blobs across
devices via Cloud Storage; free users capture and store locally only.

### 18.2 Capture

`SwipeablePhotoRow.kt` — swipe-left on a row reveals the photo panel.
Camera via `ActivityResultContracts.TakePicture()`; gallery/file
picker via `OpenMultipleDocuments()` filtered to `image/*` and
`application/pdf`. PDFs are rasterised on import (first page,
~1500 px long edge, white background, q=95 JPEG) by
`ReceiptManager.readAsJpegBytes` before the normal resize/compress
pipeline runs. Full-screen viewer supports **rotation** (persisted to
disk + thumbnail regenerated) and **delete** (the only path to
deletion; long-press no longer opens a delete dialog).

### 18.3 Compression (`ReceiptManager.kt`)

| Parameter    | Value |
|--------------|-------|
| Max long edge | 1000 px (proportional) |
| Min short edge | 400 px (`MIN_IMAGE_DIMENSION`) — prevents tall e-receipts from being crushed into unreadable slivers |
| Target size  | 250 KB per megapixel |
| Quality      | iterative JPEG: start Q=92, log-linear interpolate, ±10% tolerance, clamp 20–100 |
| Never re-compresses an already-encoded JPEG when dimensions + target bytes are already in range |
| Thumbnail    | 200 px, Q=70, saved alongside |

The pipeline is a single service method `processAndSavePhoto(uri)` in
`ReceiptManager`; both the dialog and the list-row camera/gallery
paths now call it directly (previously the list-row duplicated the
pipeline; dedupe landed in 2.7 at ~160 LOC removed).

### 18.4 Local Storage

- Full: `filesDir/receipts/{receiptId}.jpg`
- Thumb: `filesDir/receipt_thumbs/{receiptId}.jpg`
- `ReceiptManager.cleanOrphans` deletes files not referenced by any
  active transaction and not in the pending-upload queue.

### 18.5 Cloud Sync (Paid Only) — Four-Layer Architecture

**No dedicated listener on `imageLedger`.** Photo propagation runs
across four complementary layers so originator-side uploads drain
immediately and peer-side downloads are retried with backoff + eventual
escalation to recovery requests.

**Layer 0 — Foreground upload drainer.** `MainViewModel.kickUploadDrainer`
launches a coroutine on `viewModelScope` that drains the pending-upload
queue (`pending_receipt_uploads.json`). Kicked by `saveTransactions`
whenever a transaction save attaches a new `receiptIdN`, and on VM
init if the queue survived from a prior session. Backs off
30s→60s→2m→5m→10m on failure; exits when queue is empty. Uses
`ReceiptSyncManager.processPendingUploads` (5 parallel). Eliminates
the window where a transaction reaches peers before its photo reaches
Cloud Storage.

**Layer 1 — Transaction-sync fast path for downloads.** When
`FirestoreDocSync` delivers a transaction batch via `onBatchChanged`,
`MainViewModel` scans arriving `receiptId*` fields and calls
`ReceiptSyncManager.downloadReceiptWithRetry` for any not-locally-
present blob (5 in parallel). The helper does download + save +
`markPossession` + `pruneCheckTransaction` inline. On miss, hands off
to Layer 2.

**Layer 2 — Foreground download retry.**
`MainViewModel.kickFgDownloadRetry` maintains an in-memory set of
stuck receiptIds; coroutine on `viewModelScope` drains with
30s→60s→2m→5m→10m backoff via the same `downloadReceiptWithRetry`
helper. Self-filters each tick (drops ids now referenced elsewhere or
that appeared locally via another path). Resets backoff on success;
exits when set is empty.

**Layer 3 — Background coverage.**
- **Tier 2** (ViewModel alive, app backgrounded): creates a transient
  `ReceiptSyncManager`, calls `syncReceipts()`. Catch-up for cases
  where foreground drainers didn't fire (Doze, CPU throttling). Cheap
  no-op when there's nothing to do.
- **Tier 3** (ViewModel dead): already calls `syncReceipts()` as part
  of full BG sync.

**Shared retry semantics.**
`ReceiptSyncManager.downloadReceiptWithRetry` is the single download
helper used by Layers 1, 2, `processRecovery`, and (via
`syncReceipts()`) Layer 3. It tracks a per-receipt retry counter in
SharedPrefs (`KEY_RETRY_PREFIX`); on the 3rd **real** failure when
the ledger claims `uploadedAt > 0`, it deletes the ledger entry and
creates a recovery request, triggering a peer re-upload. Transient
"no ledger entry yet" cases are distinguished from "ledger claims
uploaded but blob is broken" and handled separately.

Flag clock bumps on: recovery request (`createRecoveryRequest`),
re-upload complete (`markReuploadComplete`), explicit deletion
(`deleteReceiptFull`), stale-prune deletions, snapshot request
(`createSnapshotRequest`). **Not** bumped on originator upload
(`createLedgerEntry`) — Layer 1 conveys discovery, and the inline
prune check at every download site keeps cloud storage tidy without
the extra round-trip.

**`processLedgerOperations` does NOT download.** The old
`handleDownload` branch was removed. It now handles only: re-upload
requests (we have the file, peer asked for help), non-possession
marking + `checkPhotoLost`, and possession + prune + **rotation
detection** for locally-present entries. Downloads for receipts this
device references are always routed through `processRecovery` or the
foreground paths — this prevents flag-clock bumps from causing
indiscriminate re-downloads of entries we've locally pruned or don't
care about.

**Rotation / edit propagation via `contentVersion`.** Ledger entries
carry a monotonic `contentVersion: Long` (default 0 for fresh
uploads). Rotating a photo rewrites the local bytes via
`ReceiptManager.replaceReceipt`, which also re-queues the receiptId
for upload. **v2.7 false-rotation guard:** `replaceReceipt` calls
`ReceiptSyncManager.bumpLocalContentVersionForRotation(ctx, receiptId)`
BEFORE `addToPendingQueue`, marking the rotation as pending in
`receipt_sync_prefs/content_version_<receiptId>`. `processPendingUploads`
then uses three-way decision logic after a successful upload:

1. No entry OR `uploadedAt == 0` → `createLedgerEntry` (fresh upload or
   servicing a recovery request). v2.7 also stamps `lastEditBy =
   originatorDeviceId` so step 3 below can detect resumes.
2. Entry exists, `uploadedAt > 0`, `lastEditBy == ourDeviceId`,
   `contentVersion == localContentVersion` → **resume of a partial
   commit** from a previous cycle (cancelled between ledger write and
   queue removal). Storage upload is idempotent, ledger already at
   desired state — skip the bump and just remove from queue. Without
   this, every cancelled-mid-commit upload would false-rotate, fanning
   out a flag-clock bump and forcing every peer to re-download
   identical content.
3. Otherwise → real rotation OR peer-edited ledger conflict → call
   `incrementContentVersion`: bumps counter, resets `possessions` to
   just the editor, stamps `lastEditBy`, bumps the flag clock.

Peers track `lastSeenContentVersion[receiptId]` in SharedPrefs and, on
flag-clock sync, the "uploadedAt > 0 && hasLocalFile" branch of
`processLedgerOperations` compares — mismatch →
`deleteLocalReceipt`, then same-cycle `processRecovery` downloads
the new content and records the new version. Recovery requests
preserve `contentVersion` across the delete-and-recreate so stale
peers still invalidate after subsequent rotations.

**Snapshot cancellation handling (v2.7):** `buildSnapshot` and
`processSnapshotDownload` catch `CancellationException` separately
from generic `Exception` and rethrow after deleting partial files.
Build path leaves status as `"building"` (NOT `"error"`) on cancel
so the lifecycle's 2-h staleness gate handles re-claim by the same
or another device. Without this, the cancellation was silently
swallowed by the generic catch and `runFullSyncInline` returned
`true` (success), suppressing the WM `runOnce` fallback.

**Near-real-time push via Cloud Function `onImageLedgerWrite`.** The
function (`functions/index.js`) triggers on every `imageLedger` write
and fires a filtered `sync_push` FCM (high priority, skips the
writer by `lastEditBy`) when the write is meaningful to peers:
rotation (`contentVersion` incremented), recovery re-upload
complete (`uploadedAt` went 0 → >0), or recovery request created
(new entry with `uploadedAt === 0`). Fresh uploads are NOT pushed
(the concurrent `transactions` write already fires a `sync_push`
that peers act on via `onBatchChanged`). Possession / prune /
non-possession writes are ignored (bookkeeping only). Peers'
`BackgroundSyncWorker.runOnce` dedups bursts (50-photo batch
rotation = 1 `syncReceipts()` run per peer via
`enqueueUniqueWork(KEEP)`).

Upload target: `groups/{gid}/receipts/{receiptId}.enc`
(ChaCha20-Poly1305 AEAD with group sync key).
Ledger doc `imageLedger/{receiptId}`:
`{receiptId, originatorDeviceId, createdAt, possessions: Map<String,Bool>, uploadAssignee?, assignedAt?, uploadedAt?}`.

**Upload-first, ledger-second**: pending queue persisted to JSON so a
crash between the two orphans nothing — next run finishes the ledger
step. Upload speed (`uploadSpeedBps`, `uploadSpeedMeasuredAt`) writes
to RTDB presence for re-upload assignment.

Background: worker Tier 3 only (ViewModel dead + paid) calls
`syncReceipts()`. Tiers 1–2 skip.

**v2.8 offline + forensics:** `processPendingUploads` and
`processRecovery` early-return with `syncLog` when
`NetworkUtils.isOnline(context) == false` (Cloud Storage has no
fast-fail path; each queued upload would otherwise burn the full 60 s
SDK timeout). The persistent queue stays intact;
`MainViewModel.networkCallback.onAvailable` `cancelAndJoin`s + restarts
the upload drainer when network returns so queued uploads resume
immediately rather than waiting out the drainer's exponential backoff
(was up to 10 min). Debug builds also instrument the receipt-file
lifecycle: `ReceiptManager.addToPendingQueue` / `removeFromPendingQueue`
/ `deleteLocalReceipt` log via `BudgeTrakApplication.syncEvent` with a
caller stack-trace tag (forensic for "phantom photo frame" bugs); the
upload drainer's "no local file" branch re-checks the persistent queue
to distinguish concurrent user-delete from genuine file loss;
`DiagDumpBuilder` adds a "Receipt Files Audit" section listing
files-on-disk vs active txn refs vs tombstone refs vs pending queue,
with orphan / missing / queue-without-file / queue-without-ref
divergence sets.

### 18.6 Three-State Possession

| `possessions["dev"]` | Meaning |
|---------------------|---------|
| `true` | Device holds a valid blob (on upload or download success) |
| `false` | Device **confirmed** it doesn't have it (via `ImageLedgerService.markNonPossession`, e.g. after join-snapshot or batch recovery returned empty) |
| *absent* | Not evaluated — don't count against it |

All `true` → safe to prune cloud copy. All photo-capable devices
`false` → `ImageLedgerService.checkPhotoLost` confirms permanent loss;
cleanup nulls `receiptIdN` on the transaction and deletes the ledger
entry. Gated on completed initial sync so new joiners don't declare
everything lost before their recovery runs.

### 18.7 Delete Chain — `ReceiptManager.deleteReceiptFull`

1. Clear receipt slot on transaction.
2. Remove from pending-upload queue.
3. Delete local file + thumbnail.
4. Delete Cloud Storage object.
5. Delete ledger entry (bumps flag clock).

Other devices observe `receiptId: X → null` in the transaction update
and cleanup locally via `SyncMergeProcessor`. The periodic orphan
scan is the safety net.

### 18.8 Recovery

- Per file: 3 **real** failures (transient network errors don't
  count) → replace ledger entry with a recovery request; bump flag
  clock.
- Re-uploader selection: `(online in last 24 h, fastest
  uploadSpeedBps)`; tie-break
  `abs(hash(receiptId + deviceId)) % 1000`; `claimUploadAssignment`
  CAS transaction; 5-min stale-assignment failover.

### 18.9 Snapshot Archive (≥ 50 photos)

Builder assembles one encrypted archive + manifest, uploads once,
records a snapshot ledger entry. Consumer downloads once and extracts
per-photo locally. Used for long-offline batch recovery and
join-snapshot (`upload/download/deleteJoinSnapshot`). Doc ID
`_snapshot_request` (single-underscore — v2.3 fix for Firestore's
`__*__` reserved-ID rule).

### 18.10 14-Day Cloud Pruning

`ReceiptSyncManager.processStalePruning` — hardcoded 14 days,
possession-independent, after noon local time, once per 24 h per
device (`imageLastCleanupDate` on group doc, no CAS). Deletes cloud
file + ledger entry, bumps flag clock. `receiptPruneAgeDays` in
SharedSettings controls **local** pruning only — not wired to cloud.

### 18.11 Orphan Cloud Scan

Safety net for cloud files with no ledger entry. Admin-only startup,
30-day gate (`lastAdminCleanup`), 10-min guard against in-progress
uploads from other devices.

### 18.12 Free vs Paid

| Feature                     | Free            | Paid                 |
|-----------------------------|-----------------|----------------------|
| Local capture / store (5/txn) | Yes           | Yes                  |
| Cloud sync                  | No              | Yes                  |
| Download from other devices | No              | Yes                  |
| `photoCapable` in RTDB      | Not set         | Published            |

### 18.13 Thumbnail-Bar Interactions (2.7)

Two renderings of the 5-slot thumbnail bar use one gesture model:
`SwipeablePhotoRow` (transaction-list swipe-left panel) and the
Add/Edit dialog's inline bar.

- **Tap** → `FullScreenPhotoViewer` (delete + rotate live inside).
  On a pending-download placeholder, tap shows a toast
  ("Waiting for this photo to download from the device that added it")
  instead of opening the viewer.
- **Long-press** → picks up the thumbnail; 2 dp blue outline appears.
  Dialog variant: the highlight persists after release and becomes the
  AI OCR scan target (see §18.14). List-row variant: highlight clears
  on release.
- **Long-press + drag (no lift)** → reorders among occupied slots.
  Non-dragged thumbnails animate via `animateIntAsState(tween(150))`;
  dragged thumbnail follows finger directly with
  `IntOffset` + `zIndex(1f)`. On release, `receiptId1..5` is rewritten
  in the new visual order, compacting any gaps from prior deletes.
- **Pending-download placeholders** (receiptId set, thumbnail absent)
  participate in the reshuffle just like real thumbnails. When the
  bytes arrive, they land in whichever slot the receiptId ended up in.
- Position-anchored toasts — gesture-triggered toasts pass a
  `windowYPx` captured via `onGloballyPositioned` so the toast
  renders just above the source element via
  `AppToastState.show(msg, windowYPx)`.

Implementation: `detectDragGesturesAfterLongPress` with
`rememberUpdatedState` on `occupiedSlots` + `dialogReceiptIds` so
callbacks (established once per slot index) always see current
composition state. Long-press without drag toggles the dialog-variant
highlight against a pre-drag snapshot (so the first press doesn't
clear itself).

### 18.14 AI Receipt OCR Integration (2.7)

Subscriber-only. The user highlights one thumbnail in the photo bar
(long-press), then taps the `AutoAwesome` sparkle icon in the
TransactionDialog header. Pipeline details in §11 (renamed
"Auto-Categorization" → "AI Auto-Fill"). Pre-selected categories at
sparkle-tap time constrain the AI to those buckets; empty selection
lets it choose from the full list. A small help banner above the
category picker (header-background row, subscriber dialogs only)
deep-links to a dedicated Transactions Help subsection explaining
that the AI never modifies the category selection — it only fills
amounts, so the user must deselect all cats first to let the AI
re-pick categories on an existing transaction.

## 19. Home Screen Widget

### 19.1 Overview

Single home-screen widget: Canvas-bitmap Solari flip-display of
available cash plus quick-add +/− buttons. Theme-aware,
paid-gated.

### 19.2 Components

| Component | File | Lines | Purpose |
|-----------|------|-------|---------|
| BudgetWidgetProvider | BudgetWidgetProvider.kt | 288 | AppWidgetProvider: lifecycle, resetHour alarms, `updateAllWidgets()` throttle, schedules `BackgroundSyncWorker` |
| WidgetRenderer | WidgetRenderer.kt | 276 | Canvas bitmap renderer for Solari cards |
| WidgetTransactionActivity | WidgetTransactionActivity.kt | 996 | `ComponentActivity` with inline Compose dialog; pushes via `SyncWriteHelper` |
| BackgroundSyncWorker | (Section 16.13) | 610 | Widget freshness also comes from here |

**`WidgetRefreshWorker` no longer exists** — retired 2026-03-29 and
absorbed into `BackgroundSyncWorker`. `BudgetWidgetProvider.onUpdate`
simply calls `BackgroundSyncWorker.schedule(context)`.

### 19.3 Visual Design

- Solari = Canvas bitmap; button bar = XML layout below, aligned to
  card edges via `setViewPadding`.
- Bitmap auto-sized to card content; max 75 % of widget height.
- Light mode: blue cards `#305880`; dark: `#1A1A1A`.
- Logo (blue tint `#305880`) controlled by `showWidgetLogo` pref.
- Custom vector `ic_minus.xml` for the red minus button.

### 19.4 Update Triggers

- `MainViewModel` (on resume, data change)
- `WidgetTransactionActivity` (after add)
- `BackgroundSyncWorker` — periodic 15-min for sync users; one-shot
  per period boundary for solo users (Phase 3, see §17.13). Plus
  Tier-3 cold-start freshness.
- Settings (theme, currency, logo toggle)
- Exact-alarm (`setExactAndAllowWhileIdle`) at the next budget reset
  boundary for every period type

#### Throttle

`BudgetWidgetProvider.updateAllWidgets()` debounces to once per 5 s
(`WIDGET_THROTTLE_MS = 5_000L`). Within the window, one deferred
redraw is queued via `Handler.postDelayed`; additional calls are
dropped while `@Volatile pendingRedraw` is true. Prevents frame drops
from rapid-fire RE/SG accrual pushes during period refresh.

App-closed freshness comes from `BackgroundSyncWorker` Tier 3: a
short-lived Firestore listener (~5–10 s from offline cache) applies
merges via `SyncMergeProcessor`, runs period refresh, recomputes
cash, redraws.

### 19.5 Size Configuration

| Property | Value |
|----------|-------|
| Minimum | 2×1 (110 dp × 40 dp) |
| Default | 4×1 (250 dp) |
| Maximum | No limits (resizable both directions) |

### 19.6 Free vs Paid

- Free: overlay "Upgrade for full widget" on the Solari display;
  **1 widget transaction per day**.
- Paid: unlimited; `isPaidUser` in `app_prefs`.

### 19.7 Widget Lifecycle

- `onEnabled` / `onUpdate` → `BackgroundSyncWorker.schedule`,
  re-render, rebind click intents.
- `onDisabled` → does **not** cancel the worker (it serves data sync
  + period refresh, broader than the widget alone).
- `onReceive(ACTION_RESET_REFRESH)` → `BackgroundSyncWorker.runOnce`
  + re-schedule the reset alarm.
## 20. Data Models

### 20.1 Enumerations

| Enum | Values |
|------|--------|
| TransactionType | EXPENSE, INCOME |
| BudgetPeriod | DAILY, WEEKLY, MONTHLY |
| RepeatType | DAYS, WEEKS, BI_WEEKLY, MONTHS, BI_MONTHLY, ANNUAL |
| IncomeMode | FIXED, ACTUAL, ACTUAL_ADJUST |
| SuperchargeMode | REDUCE_CONTRIBUTIONS, ACHIEVE_SOONER |
| BankFormat | GENERIC_CSV, US_BANK, SECURESYNC_CSV |
| ImportStage | FORMAT_SELECTION, PARSING, PARSE_ERROR, DUPLICATE_CHECK, COMPLETE |

All sync metadata is `deviceId` + `deleted` only. Per-field CRDT clocks were removed when sync switched to Firestore-native per-field encryption; no data class carries `_clock` fields.

### 20.2 Transaction

| Property | Type | Default |
|----------|------|---------|
| id | Int | required |
| type | TransactionType | required |
| date | LocalDate | required |
| source | String | required |
| description | String | "" |
| categoryAmounts | List\<CategoryAmount\> | emptyList() |
| amount | Double | required |
| isUserCategorized | Boolean | true |
| isBudgetIncome | Boolean | false |
| excludeFromBudget | Boolean | false |
| linkedRecurringExpenseId | Int? | null |
| linkedRecurringExpenseAmount | Double | 0.0 |
| linkedAmortizationEntryId | Int? | null |
| amortizationAppliedAmount | Double | 0.0 |
| linkedIncomeSourceId | Int? | null |
| linkedIncomeSourceAmount | Double | 0.0 |
| linkedSavingsGoalId | Int? | null |
| linkedSavingsGoalAmount | Double | 0.0 |
| receiptId1..5 | String? | null |
| deviceId | String | "" |
| deleted | Boolean | false |

### 20.3 CategoryAmount

`categoryId: Int`, `amount: Double`.

### 20.4 Category

| Property | Type | Default |
|----------|------|---------|
| id | Int | required |
| name | String | required |
| iconName | String | required |
| tag | String | "" |
| charted | Boolean | true |
| widgetVisible | Boolean | true |
| deviceId | String | "" |
| deleted | Boolean | false |

Protected tags (cannot be deleted): `"other"`, `"recurring_income"`, `"supercharge"`.

### 20.5 IncomeSource

Fields: `id, source, description, amount, repeatType (MONTHS), repeatInterval (1), startDate?, monthDay1?, monthDay2?, deviceId, deleted`.

### 20.6 RecurringExpense

Fields: `id, source, description, amount, repeatType (MONTHS), repeatInterval (1), startDate?, monthDay1?, monthDay2?, deviceId, deleted, setAsideSoFar (0.0), isAccelerated (false)`.

### 20.7 AmortizationEntry

Fields: `id, source, description, amount, totalPeriods, startDate, deviceId, deleted, isPaused (false)`.

### 20.8 SavingsGoal

Fields: `id, name, targetAmount, targetDate?, totalSavedSoFar (0.0), contributionPerPeriod (0.0), isPaused (false), deviceId, deleted`. `superchargeMode` is app-level state (not a per-goal field).

### 20.9 SharedSettings

| Property | Type | Default |
|----------|------|---------|
| currency | String | "$" |
| budgetPeriod | String | "DAILY" |
| budgetStartDate | String? | null |
| isManualBudgetEnabled | Boolean | false |
| manualBudgetAmount | Double | 0.0 |
| weekStartSunday | Boolean | true |
| resetDayOfWeek | Int | 7 |
| resetDayOfMonth | Int | 1 |
| resetHour | Int | 0 |
| familyTimezone | String | "" |
| matchDays/matchPercent/matchDollar/matchChars | 7 / 1.0 / 1 / 5 |
| showAttribution | Boolean | false |
| availableCash | Double | 0.0 |
| incomeMode | String | "FIXED" |
| deviceRoster | String | "{}" |
| receiptPruneAgeDays | Int? | null |
| lastChangedBy | String | "" |
| archiveCutoffDate | String? | null |
| carryForwardBalance | Double | 0.0 |
| lastArchiveInfo | String? | null |
| archiveThreshold | Int | 10000 |

Archive fields: `archiveCutoffDate` is the boundary below which transactions have been archived. `carryForwardBalance` is the deterministic cash balance computed over archived transactions (used by `recomputeAvailableCash()`). `lastArchiveInfo` is JSON `{date, count, totalArchived}`.

### 20.10 PeriodLedgerEntry

Fields: `periodStartDate: LocalDateTime, appliedAmount: Double, corrected: Boolean (unused, kept for JSON back-compat), deviceId: String`. Computed `id = periodStartDate.toLocalDate().toEpochDay().toInt()` is the dedup key.

### 20.11 ImageLedgerEntry

| Property | Type | Description |
|----------|------|-------------|
| receiptId | String | Unique photo ID |
| originatorDeviceId | String | Capturing device |
| createdAt | Long | Capture epoch ms |
| possessions | Map\<String, Boolean\> | Three-state per device (true/false/absent) |
| uploadAssignee | String? | Device tasked with re-upload |
| assignedAt | Long | Assignment epoch ms |
| uploadedAt | Long | 0 = not yet in cloud |

## 21. Persistence Strategy

### 21.1 JSON File Storage

All primary data models are persisted as JSON arrays under `filesDir/` via SafeIO atomic writes (temp file + rename). The `future_expenditures.json` name is intentionally preserved across the Future Expenditures -> Savings Goals rebrand for back-compat.

| File | Repository | Data Model |
|------|------------|------------|
| transactions.json | TransactionRepository | Transaction |
| categories.json | CategoryRepository | Category |
| income_sources.json | IncomeSourceRepository | IncomeSource |
| recurring_expenses.json | RecurringExpenseRepository | RecurringExpense |
| amortization_entries.json | AmortizationRepository | AmortizationEntry |
| future_expenditures.json | SavingsGoalRepository | SavingsGoal |
| shared_settings.json | SharedSettingsRepository | SharedSettings |
| period_ledger.json | PeriodLedgerRepository | PeriodLedgerEntry |
| archived_transactions.json | TransactionRepository (archive) | Transaction |
| enc_hash_cache.json | FirestoreDocSync | per-doc hash skip cache |

Each repository: `save(ctx, list)` serializes JSONArray via SafeIO; `load(ctx)` reads, parses, returns list (emptyList on missing/blank); new fields loaded with backward-compatible defaults.

### 21.2 SharedPreferences

`app_prefs` — primary UI/settings store. Keys include: `currencySymbol, digitCount, showDecimals, dateFormatPattern, appLanguage, chartPalette, weekStartSunday, budgetPeriod, budgetStartDate, resetHour, resetDayOfWeek, resetDayOfMonth, isManualBudgetEnabled, manualBudgetAmount, availableCash, lastRefreshDate, matchDays, matchPercent, matchDollar, matchChars, incomeMode, autoCapitalize, showWidgetLogo, crashlyticsEnabled, archiveThreshold, lastMaintenanceCheck, loadSegTime_0..6, localDeviceId`.

Separate stores: `sync_engine` (groupId, listener cursors, pushed-doc keys, fingerprint, App Check state), `fcm_prefs` (FCM token), plus `backup_prefs` (retention, schedule, last-backup timestamps).

### 21.3 Persistence Timing

All mutations trigger:

1. Update in-memory `mutableStateListOf` in MainViewModel
2. `repository.save(ctx, list)` via SafeIO
3. Push record to Firestore via `SyncWriteHelper.push()`
4. Recalculate budget if relevant
5. Recompute available cash if relevant
6. Update SharedPreferences if relevant

## 22. PieChartEditor Component

### 22.1 PieChartEditor (PieChartEditor.kt)

Interactive Composable for allocating a transaction amount across multiple categories. Three input modes with real-time visual feedback.

### 22.2 Visual Design

- Category-colored arc segments proportional to allocation
- Draggable handle circles at segment boundaries; pulsing on the active drag
- Category legend with color swatches and amounts
- Central total display

### 22.3 Drag Interaction

Touch captured via `pointerInput`; angle from center via `atan2`; handle positions updated with enforced minimum slice; amounts recalculated proportionally and snapped to currency precision.

### 22.4 Color Palettes

Four palettes with paired light/dark variants (8 total): Bright, Pastel, Sunset, Earthy.

## 23. Help System

### 23.1 Architecture

Each major screen has a dedicated help screen accessible from a help icon in its top app bar. Help screens are pure Composables built on shared blocks from `HelpComponents.kt` (165 lines).

### 23.2 Help Screen Inventory

| Help Screen | Lines |
|-------------|-------|
| TransactionsHelpScreen | 965 |
| SettingsHelpScreen | 511 |
| DashboardHelpScreen | 503 |
| BudgetConfigHelpScreen | 374 |
| RecurringExpensesHelpScreen | 325 |
| SavingsGoalsHelpScreen | 300 |
| AmortizationHelpScreen | 271 |
| SyncHelpScreen | 116 |
| SimulationGraphHelpScreen | 88 |
| BudgetCalendarHelpScreen | 87 |

### 23.3 Shared Help Components

`HelpComponents.kt` provides section headers, bullet lists with icons, tip/note callouts, key-value rows, and numbered step lists.

## 24. Error Handling

### 24.1 File I/O

Repository loads check existence first; blank or missing file returns `emptyList()`. SafeIO atomic writes (temp + rename) prevent corruption. New fields get back-compatible defaults.

### 24.2 Import

`CsvParser` returns `CsvParseResult` with nullable error. Line-level parse errors include line number and exception. Header validation for native CSV; empty file detection; partial results preserved; Generic CSV auto-detection with fallback scoring.

### 24.3 Encryption

Minimum data size validation (salt + nonce + 1); Poly1305 tag verification throws on tamper; wrong password yields authentication failure.

### 24.4 Data Validation

- Transaction IDs generated with collision-avoidance retry loop
- Amount matching uses percent AND absolute thresholds
- Day-of-month coerced to month length
- Deleting a category requires transaction reassignment
- Savings goal contributions capped at remaining amount
- Amortization periods clamped to `[0, totalPeriods]`
- Budget calculations rounded to 2 decimal places

### 24.5 Startup

- LoadingScreen gates UI until IO-thread data load completes
- Lifecycle observer registered AFTER the load gate; `onResume()` returns early if `!dataLoaded`
- `recomputeCash()` synchronous to avoid startup races
- 500 ms minimum display time prevents loading flash on fast devices

### 24.6 Sync

- Persistent filtered listeners auto-reconnect; per-collection cursors prevent data loss on reattach
- Enc hash skip prevents redundant decryption of own writes
- Firestore operations wrapped in try/catch
- Conflict detection via `lastEditBy` + `localPendingEdits`
- Period ledger create-if-absent via Firestore transaction (first writer wins)
- Receipt upload queue persisted to disk for crash safety
- Orphan Cloud Storage scan with 10-minute time guard
- RTDB `onDisconnect()` ensures offline state even on unclean shutdown
- `awaitInitialSync()` has a 30 s timeout to prevent indefinite blocking

## 25. Android Manifest Configuration

| Config | Value |
|--------|-------|
| package / namespace / applicationId | com.techadvantage.budgetrak |
| application name | .BudgeTrakApplication |
| allowBackup | false |
| usesCleartextTraffic | false |
| supportsRtl | true |
| theme | @android:style/Theme.Material.NoActionBar |

### Permissions

Only `INTERNET` is declared in the manifest. CAMERA and media access are handled via runtime permission requests and system pickers (Storage Access Framework for backup restore and CSV import; the system photo picker for gallery selection), so no `CAMERA`, `READ_MEDIA_IMAGES`, or `READ_EXTERNAL_STORAGE` declaration is required.

### Components

| Component | Purpose |
|-----------|---------|
| MainActivity | Launcher activity (portrait, singleTask, exported) |
| widget.WidgetTransactionActivity | Widget quick-add (singleInstance, excludeFromRecents) |
| widget.BudgetWidgetProvider | AppWidgetProvider receiver (APPWIDGET_UPDATE) |
| data.sync.FcmService | Firebase Cloud Messaging handler (MESSAGING_EVENT) |
| data.sync.WakeReceiver | Opportunistic wake on ACTION_POWER_CONNECTED / _DISCONNECTED (5-min internal rate limit) |
| androidx.core.content.FileProvider | `${applicationId}.fileprovider` for CSV/receipt sharing |

## 26. Code Statistics

> **As-of**: figures below were captured at v2.7 release (versionCode=4). v2.8 dev branch totals **~100 Kotlin files / ~51,500 lines** — most of the growth is in `TransactionsScreen.kt` (now ~6.3 K lines, the unified-dialog refactor + photo-bar reorder + OCR-state plumbing), `MainViewModel.kt` (~3.2 K, network awareness + receipt forensics + ID generators), and `BackgroundSyncWorker.kt` (~1.2 K, three-tier inline routing + offline gates). Refresh on each release tag, not each commit.

### 26.1 Top Files by Size (v2.7)

| Rank | File | Lines |
|------|------|-------|
| 1 | TransactionsScreen.kt | 5,633 |
| 2 | MainViewModel.kt | 2,650 |
| 3 | MainActivity.kt | 2,438 |
| 4 | EnglishStrings.kt | 1,896 |
| 5 | SpanishStrings.kt | 1,882 |
| 6 | SettingsScreen.kt | 1,651 |
| 7 | AppStrings.kt | 1,498 |
| 8 | TranslationContext.kt | 1,477 |
| 9 | MainScreen.kt | 1,303 |
| 10 | BudgetConfigScreen.kt | 1,243 |
| 11 | SyncScreen.kt | 1,206 |
| 12 | RecurringExpensesScreen.kt | 1,097 |
| 13 | EncryptedDocSerializer.kt | 1,039 |
| 14 | WidgetTransactionActivity.kt | 996 |
| 15 | CsvParser.kt | 996 |
| 16 | TransactionsHelpScreen.kt | 965 |
| 17 | FirestoreDocSync.kt | 927 |
| 18 | SavingsGoalsScreen.kt | 808 |
| 19 | ReceiptSyncManager.kt | 741 |
| 20 | ImageLedgerService.kt | 741 |

### 26.2 File Count by Package (v2.7)

| Package | Files |
|---------|-------|
| com.techadvantage.budgetrak (root) | 3 |
| .data | 31 |
| .data.sync | 21 |
| .sound | 1 |
| .ui.components | 5 |
| .ui.screens | 22 |
| .ui.strings | 5 |
| .ui.theme | 3 |
| .widget | 3 |
| **TOTAL** | **94** |

> v2.8 added `data/ocr/` (3 files), `data/telemetry/` (1 file), and `data/sync/NetworkUtils.kt` (1 file) — the package totals shift to roughly `.data 35` / `.data.sync 22` for current snapshot.

## 27. Build Configuration

### 27.1 Root build.gradle.kts

| Plugin | Version |
|--------|---------|
| com.android.application | 8.7.3 |
| org.jetbrains.kotlin.android | 2.0.21 |
| org.jetbrains.kotlin.plugin.compose | 2.0.21 |
| com.google.gms.google-services | 4.4.2 |
| com.google.firebase.crashlytics | 3.0.2 |

### 27.2 App build.gradle.kts

| Setting | Value |
|---------|-------|
| namespace / applicationId | com.techadvantage.budgetrak |
| compileSdk | 34 |
| minSdk | 28 |
| targetSdk | 34 |
| versionCode / versionName | 5 / 2.8 (dev); 4 / 2.7 in production |
| source/target / jvmTarget | Java 17 |
| compose / buildConfig | enabled |
| minify / shrinkResources (release) | true |
| release signingConfig | reads `BUDGETRAK_KEYSTORE_FILE`, `BUDGETRAK_KEYSTORE_PASSWORD`, `BUDGETRAK_KEY_ALIAS` from `local.properties` (git-ignored). Upload keystore at `~/keystore/upload-keystore.jks` with offline backup; SHA-256 `E0:2B:5D:D6:5E:86:1B:3B:79:AC:F4:F3:F4:76:D4:3B:35:D1:FC:3A:D4:E1:6D:26:C0:CC:0D:22:E9:9D:04:0A`. Used by `./gradlew bundleRelease` to produce signed AAB for Play Store upload. |

BuildConfig emits a UTC `BUILD_TIME` stamp.

### 27.3 Dependencies

| Dependency | Version |
|------------|---------|
| androidx.compose:compose-bom | 2024.09.03 |
| androidx.core:core-ktx | 1.13.1 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.6 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.6 |
| androidx.activity:activity-compose | 1.9.2 |
| androidx.compose.ui / ui-graphics / material3 / material-icons-extended / animation | BOM managed |
| com.google.firebase:firebase-bom | 32.7.0 |
| firebase-auth / firestore / storage / messaging / database | BOM managed |
| firebase-appcheck-playintegrity / firebase-appcheck-debug | BOM managed |
| firebase-crashlytics | BOM managed |
| androidx.work:work-runtime-ktx | 2.9.1 |
| androidx.security:security-crypto | 1.1.0-alpha06 |
| androidx.documentfile:documentfile | 1.0.1 |
| junit:junit (test) | 4.13.2 |
| org.json:json (test) | 20231013 |

Do NOT upgrade `core-ktx` past 1.13.1 or Compose BOM past 2024.09.03 — newer versions require `compileSdk 35`.

### 27.4 Gradle Properties

| Property | Value |
|----------|-------|
| org.gradle.jvmargs | -Xmx2048m -Dfile.encoding=UTF-8 |
| android.useAndroidX | true |
| kotlin.code.style | official |
| android.nonTransitiveRClass | true |
| android.aapt2FromMavenOverride | Termux path to aapt2 (build env override) |

## 28. Document Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Feb 2026 | BudgeTrak Dev Team | Initial release. 47 source files; full spec for architecture, features, data models, persistence, encryption, build. |
| 2.0 | Mar 2026 | BudgeTrak Dev Team | Added SYNC (CRDT multi-device), cash flow simulation, linked transactions, ad-aware dialogs, ANNUAL repeat type. 68 files / 27,738 lines. |
| 2.2 | Mar 2026 | BudgeTrak Dev Team | Replaced CRDT sync (~4,000 lines) with Firestore-native per-field encryption (~1,500 lines). Removed all `_clock` values. Added snapshot listeners, field-level update(), receipt system, home widget, calendar + simulation screens, generic CSV detection, SafeIO, SyncWriteHelper. Renamed SecureSync -> BudgeTrak. 87 files / 42,506 lines. |
| 2.3 | Mar 2026 | BudgeTrak Dev Team | Added `BackgroundSyncWorker` (15-min periodic) replacing `WidgetRefreshWorker`. Extracted `SyncMergeProcessor` and `PeriodRefreshService`. Fixed ImageLedgerService snapshot doc ID. 89 files / 43,374 lines. |
| 2.3.1 | Mar 2026 | BudgeTrak Dev Team | Widget 5-sec throttle. `DiagDumpBuilder` utility. Dialog extraction for JIT. `MainActivity.isAppActive` flag. 90 files / 44,242 lines. |
| 2.4 | Mar 2026 | BudgeTrak Dev Team | Extracted `MainViewModel` (1,795 lines) from MainActivity. Per-collection updatedAt cursors; `awaitInitialSync()` signal. `RealtimePresenceService` on RTDB. Receipt listener replaces 60s poll. Single-pass startup health check. 92 files / 43,924 lines. |
| 2.5 | Apr 2026 | BudgeTrak Dev Team | Async IO-thread load with LoadingScreen gate, EMA segment timings. Back = Home. Synchronous `recomputeCash()`. Consolidated `runPeriodicMaintenance()` (24h gate). Three-tier `BackgroundSyncWorker`. Calculated-period sleep. Transaction archiving (threshold default 10,000; `archived_transactions.json`; carry-forward). `BudgeTrakApplication` class for App Check. 93 files / 45,095 lines. |
| 2.5.x | Apr 2026 | BudgeTrak Dev Team | SYNC rebrand; two-layer consistency check; echo suppression; App Check proactive refresh; Crashlytics observability; real-time SYNC eviction popup; admin-claim voting; TTL via `expiresAt`; subscription-expiry popup; ranked match dialogs; Auto-Capitalize; solo-user gating; help refresh; Crashlytics opt-out; SAF backup directory picker; immediate-backup-on-enable + same-day letter suffixes. Namespace/applicationId rebranded to `com.techadvantage.budgetrak`. |
| 2.6 | 2026-04-12 | BudgeTrak Dev Team | **SSD/LLD audit against code + conversion to Markdown (2026-04-12).** Rebranded to `com.techadvantage.budgetrak` under Tech Advantage LLC (Apr 11). Full memory audit: clarified the two sync hashes (`cashHash` Layer 2 = hex via `.toString(16)` at MainViewModel.kt:835; `enc_hash` per-doc cache in FirestoreDocSync uses decimal); auto-categorize scope (CSV only, not manual entry); dropped stale `WidgetRefreshWorker` references; aligned Transaction model (no clock fields); corrected screen count (10 navigable + 10 help + QuickStartGuide overlay = 21 total); App Check TTL 4h (Console-set); Cloud Functions Node.js 22. Backup retention default 1 -> 10. Added `autoCapitalize, showWidgetLogo, incomeMode` to backup `localPrefs`. Shipped orphan-no-possession (`markNonPossession` + `checkPhotoLost`). Verified against source: 94 files / 47,192 lines; archive file is `archived_transactions.json`; manifest declares only `INTERNET` (camera/media via runtime request + pickers); `BankFormat` is `{GENERIC_CSV, US_BANK, SECURESYNC_CSV}`. |
| 2.6.2 | 2026-04-13 | BudgeTrak Dev Team | **Bidirectional scroll affordance.** New `BoxScope.PulsingScrollArrows(scrollState)` in `Theme.kt` replaces the down-only `PulsingScrollArrow` at all 18 dialog callsites — pulsing up-arrow at TopStart when `canScrollBackward`, down-arrow at BottomStart when `canScrollForward`, with standardized paddings that clear `DialogHeader` (topPadding=36.dp) and footer (bottomPadding=50.dp). New `ScrollableDropdownContent { … }` wraps items in every `DropdownMenu` / `ExposedDropdownMenu` (12 callsites including the 24-item hour-of-day selector); caps popup height at 280.dp, indents items by 32.dp on the start edge so text clears the arrow column. Motivation: users with enlarged system font push otherwise-fitting content into scrollable territory. Down-only `PulsingScrollArrow` kept for backward compat. |
| 2.6.1 | 2026-04-13 | BudgeTrak Dev Team | **FCM wake architecture.** Added two Cloud Functions: `onSyncDataWrite` (Firestore-triggered, fans out high-priority `sync_push` FCM to every group device except the writer — via `lastEditBy` filter) and `presenceHeartbeat` (15-min Pub/Sub cron, wakes devices whose RTDB `lastSeen` is >15 min stale). Closes the 4h46m silent-worker gap observed on Kim's Samsung device (App-Standby Bucket restrictions). Client: `FcmService` now handles `sync_push` + `heartbeat` by enqueueing `BackgroundSyncWorker.runOnce` — now `enqueueUniqueWork(ONESHOT_WORK_NAME, KEEP)` with `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)` on API 31+. `pingRtdbLastSeen` + `WakeReceiver` + FCM arrivals all log via `syncEvent()` which now writes to `token_log.txt` in debug builds (was Crashlytics + logcat only). Fixed Layer-1 consistency false-positive on `periodLedger`: `countActiveDocs` skips `deleted=false` filter for that collection since entries don't carry a `deleted` field. SYNC page UI: duplicate "Code expires in 10 minutes" label removed (dialog still shows it); group-ID row gated to debug builds only. Operations: $1 budget alert + 4 Cloud Monitoring policies (function + Firestore rate) configured on billing account `01ADA3-6ACE89-738567`; `sync-23ce9` project migrated into `techadvantagesupport-org`. |
| 2.7.1 | 2026-04-27 | BudgeTrak Dev Team | **Transaction save audit.** Six silent-loss vectors closed: (1) `DuplicateResolutionDialog` non-dismissable on dashboard, transactions screen, and widget — tap-outside / back are no-ops, user must pick Keep Existing / Keep New / Keep Both / Ignore All. (2) `MainViewModel.onResume` disk reload changed to add-only merge — never overwrites in-memory state, eliminating races against in-flight saves and pending sync-merge disk writes. (3) Entity-id generators (Transaction / RE / IS / AE / SG / Category seed / Settings inline) widened from `0..65535` to `1..Int.MAX_VALUE` — cross-device collision probability drops from ~1 in 600 per heavy-user group/year to ~1 in 40 M (no schema change, existing low-range ids remain valid). (4) Multi-category save path sets `showValidation` and shows toast `S.transactions.multiCategoryAmountsInvalid` on every silent return — dialog no longer looks dead on bad input. (5) `onUpdateTransaction` shows `S.transactions.editFailedTransactionMissing` toast when the edit target was archived / tombstone-purged mid-edit, instead of silently closing the dialog. (6) `addTransactionWithBudgetEffect` now fully atomic: SG deduction + local list mutation + disk write + Firestore push are all gated by one dedup check, so double-tap or recomposition replay is a complete no-op. New `feedback_silent_save_failures.md` memory captures the audit method. |
| 2.7 | 2026-04-18 | BudgeTrak Dev Team | **AI features + photo-bar UX overhaul.** Shipped **AI Receipt OCR** (Subscriber, explicit-tap via `AutoAwesome` sparkle icon) — Gemini 2.5 Flash-Lite 3-call pipeline with Call 1 routing probe (`multiCategoryLikely` hint lets single-cat receipts finish in 1 API call; multi-cat proceeds to Call 2 items+cats then Call 3 per-item prices); proportional reconciliation keeps Σ(line items) = Call 1 total; invalid-categoryId remap handles tax-line hallucinations. Shipped **AI CSV Categorization** (Paid+Sub, opt-in) — on-device matcher handles high-confidence rows; Flash-Lite called only when <5 matches or <80% agreement; payload is merchant+amount only (date removed in this release for a narrower privacy footprint). Photo-bar gesture overhaul: long-press selects the AI scan target (blue outline, dialog only — persists after release); long-press+drag reorders photos among occupied slots with real-time reshuffle animation (both the dialog thumb bar and the list-row `SwipeablePhotoRow`); pending-download placeholders participate in reorder and show an explanatory toast on tap; delete moved exclusively to the full-screen viewer. Photo-pipeline hardening: dedupe (SwipeablePhotoRow calls `processAndSavePhoto` directly, ~160 LOC removed); 400 px min-edge floor fixes unreadable tall e-receipts; PDF import via `PdfRenderer` (first page at ~1500 px, JPEG q=95); orphan cleanup prunes stale pending-upload entries; queue-on-save moves upload-queue insertion from photo-capture to `saveTransactions` so cancelled dialogs don't leak orphans. Background worker: `AtomicBoolean isRunning` double-fire guard (avoids two Tier-3 runs 118 ms apart seen in Kim's diag dump); FCM `handleWakeForSync` busy-waits up to 9 s on `isRunning` so Doze-aggressive OEMs don't kill the process before WorkManager dispatches. Cash Flow Simulation tier changed from Subscriber-only to Paid+Subscriber (entry button on `SavingsGoalsScreen`). Anchored toasts (`AppToastState.show(msg, windowYPx)`) render near triggering element instead of at default mid-screen. Memory system consolidated: `~/.claude/projects/.../memory/` is now a symlink to the repo's `memory/` directory (tracked + pushed); un-tracked `private-notes/` sibling for personal content. 98 files / 49,088 lines. |

---

BudgeTrak SSD v2.7 — April 2026 — END OF DOCUMENT

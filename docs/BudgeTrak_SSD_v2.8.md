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
| Code Size | ~100 Kotlin files, ~51,500 lines (refreshed at release tags — see §26) |
| Status | Dev — Internal / Technical Reference |

> v2.7 (versionCode=4) is the production release on Google Play; v2.8 (versionCode=5) is in development. Per-release diff in §28.

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
28. Backend Infrastructure & External Services
29. Document Revision History

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

1. **MainActivity** — UI-only shell. Obtains `MainViewModel` via `viewModel()`, observes state, renders screen Composables. No business logic, no saves, no mutable state beyond nav + dialog visibility. `LoadingScreen` gates rendering until `dataLoaded`. Back on main calls `moveTaskToBack(true)` to keep ViewModel + listeners alive.
2. **MainViewModel** — holds ~80 state vars, save functions, sync lifecycle, background loops. Loads data on `Dispatchers.IO` with learned-timing progress, then populates SnapshotStateLists on Main. Companion holds `WeakReference<MainViewModel>` for `BackgroundSyncWorker` listener-health checks.
3. **Navigation** — string-based `mutableStateOf<String>` in ViewModel; keys like `"main"`, `"settings"`, `"transactions"`, etc. (see 2.3).
4. **In-memory data** — loaded from JSON into SnapshotStateLists at init, mutated in memory, saved via SafeIO (temp + rename). `LoadingScreen` uses `LinearProgressIndicator` with EMA segment timings + 60 fps ticker and a 500 ms min display.
5. **Period refresh** — calculates time until next period boundary and sleeps until boundary + 60 s buffer (clamped 60 s – 15 min). Background refresh every 15 min via `BackgroundSyncWorker`. Shared logic in `PeriodRefreshService` with `@Synchronized`. In sync groups, deferred until `awaitInitialSync()` completes.
6. **System categories** — Other, Recurring, Amortization, Recurring Income auto-provisioned at startup from `DefaultCategories.kt`.
7. **Theme** — `CompositionLocalProvider` injects `SyncBudgetColors` and `AppStrings`.
8. **Sync metadata** — all data classes carry `deviceId` + `deleted` only. Tombstones kept with `deleted=true`; UI filters via `.active`. Conflict detection uses Firestore field-level updates with `lastEditBy`.
9. **Deterministic cash** — `BudgetCalculator.recomputeAvailableCash()` rebuilds cash from period ledger + transactions; `recomputeCash()` runs synchronously for startup ordering.
10. **Auto-push** — save functions push via `SyncWriteHelper`; local-only changes are impossible when sync is configured.
11. **Lifecycle** — `MainActivity.isAppActive` is `@Volatile` set on `ON_START` / cleared on `ON_STOP`. Observer registered after the loading-screen gate (initial `ON_RESUME` missed by design). `onResume()` guards with `if (!dataLoaded) return`. `BackgroundSyncWorker.doWork()` is three-tier: (1) skip if active, (2) check listener health if VM alive via WeakReference, (3) full sync only when VM dead.
12. **Back = Home** — Back on main calls `moveTaskToBack(true)`; ViewModel and listeners stay alive. Sub-screens back-navigate normally.
13. **Consolidated maintenance** — `runPeriodicMaintenance()` called from `onResume`, 24-hour time-gate in SharedPreferences (`lastMaintenanceCheck`). Runs backup check, integrity check, receipt orphan cleanup, receipt storage pruning, admin tombstone cleanup (30-day sub-gate).
14. **Transaction archiving** — `archiveThreshold` in SharedPreferences (default 10,000). When active count exceeds threshold, oldest 25% moved to `archived_transactions.json` with carry-forward balance from `recomputeAvailableCash()`. Archive is view-only; metadata stored in SharedSettings and synced.

### 2.2 Component Architecture

> **Note on line counts.** Per-file line counts in this doc are *indicative* — `MainViewModel` and `TransactionsScreen` are the dominant single files, most data-layer files are <1 K lines. Run `find app/src/main/java -name "*.kt" | xargs wc -l` for exact current sizes.

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
| id | Int | random `(1..Int.MAX_VALUE)` rejected against existing ids |
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
| id | Int | random `(1..Int.MAX_VALUE)` rejected against existing ids |
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
| id | Int | random `(1..Int.MAX_VALUE)` rejected against existing ids |
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
| id | Int | req | random `(1..Int.MAX_VALUE)` rejected against local existing ids; full positive Int range keeps cross-device collision probability ≈1 in 2.1 B per concurrent pair |
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

**Save-path invariants** (no silent loss):

- **Atomic add** — `addTransactionWithBudgetEffect` gates ALL side effects (savings-goal deduction, local list mutation, disk write, Firestore push) behind one `transactions.none { it.id == stamped.id }` check, so double-tap and recomposition replay are complete no-ops rather than partial side effects.
- **Duplicate dialog non-dismissable** — `DuplicateResolutionDialog` (transactions screen + widget) requires an explicit Keep Existing / Keep New / Keep Both choice; tap-outside and back are no-ops, so the new transaction can never be silently dropped.
- **Multi-category save validation surfaces** — every silent `return@DialogPrimaryButton` in the multi-category branch sets `showValidation = true` and shows `S.transactions.multiCategoryAmountsInvalid`.
- **Edit no-op surfaced** — when `onUpdateTransaction`'s `indexOfFirst` returns -1 (target archived or tombstone-purged mid-edit) the user gets a 5 s toast (`editFailedTransactionMissing`) instead of a successful-looking dialog close.
- **`onResume` add-only merge** — `MainViewModel.onResume` reloads disk and ADDs disk-only ids to memory; never overwrites in-memory state. Avoids races against in-flight saves (synchronous disk write on Main during the suspended IO read) and pending sync-merge disk writes (memory updates synchronously, disk via `withContext(IO)` separately).

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

### 8.7 TransactionDialog (Unified Add / Edit)

Single Composable used for **all three** transaction-dialog entry points — Add Income, Add Expense, and Edit — across both the dashboard quick-add and the Transactions screen.

- **Internal `typeIsExpense` state:** `var typeIsExpense by remember(isExpense) { mutableStateOf(isExpense) }`. The `isExpense` parameter seeds the initial value but the *current* type is mutable. All in-dialog references (validation, save, linked-RE-only sections, source-field label, autocategorize temp Transaction) read `typeIsExpense`. Title is `if (isEdit) S.transactions.editTransaction else S.common.addTransaction`; `sourceLabel` is `if (typeIsExpense) S.common.merchantLabel else S.common.sourceLabel`.
- **Header type pill:** compact two-segment toggle in the dialog header next to the title. EXPENSE side is red (`#F44336`); INCOME side is green (`#4CAF50`). Tapping flips `typeIsExpense`. The source/merchant field label flips with the toggle. The right half of the title bar holds the AI OCR icon + photo bar entry.
- **Refund auto-flip in OCR prefill:** `LaunchedEffect(ocrState)` `OcrState.Success` handler. When `r.amount < 0` (refund receipt), prefill sets `typeIsExpense = false` and uses `kotlin.math.abs(...)` for `singleAmountText`, `totalAmountText`, and per-category `categoryAmountTexts[..]`. The user sees the type pill swap to INCOME and a positive amount populated. Save validation (`amount < 0`) is unchanged — model invariant: amount always positive, type carries polarity. `CsvParser` follows the same convention for bank-import polarity mapping.
- **Type flip in Edit mode:** users can flip a misfiled EXPENSE↔INCOME without re-creating. Linked-RE UI hides itself when `typeIsExpense=false`; existing linked state remains in the data model but is not visible until type flips back.

### 8.8 Help-from-Dialog Overlay

The AI preselect-help banner inside an open transaction dialog routes through `vm.transactionsHelpOverlayShowing: Boolean`. At the top level of `setContent` (after `DashboardDialogs` and the `QuickStartOverlay`), if true, MainActivity renders:

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

Why a `Dialog` and not a plain `Surface` overlay: `AdAwareDialog` (used by `TransactionDialog`) creates its own platform Dialog window. A non-Dialog overlay rendered in the main composition window sits *behind* that Dialog in the Window Manager z-order. Wrapping the help overlay in its own `Dialog` makes it a sibling Dialog window — Android Window Manager renders the most recently added Dialog on top. The transaction dialog never disposes during the round-trip — its `remember { mutableStateOf(...) }` state survives, so source/amount/date/photos all return on overlay dismissal.

The Transactions screen's top-bar help icon still uses normal `currentScreen = "transactions_help"` navigation; only the in-dialog banner uses the overlay path.

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

### 9.7 Public-download writes and orphan resilience

Every BudgeTrak feature that writes into `/storage/emulated/0/Download/BudgeTrak/<subdir>/` falls into one of seven categories. Android scoped storage refuses File API writes to files left by a previous install ("orphans") with `EACCES` — each path below documents how it copes.

| Subdir | Producer | Mechanism | Orphan-safe |
|---|---|---|---|
| `backups/` (`.enc`) | `BackupManager` system + photos backup | `nextAvailableSuffix(dir, dateStr, suffix)` walks `_a, _b, …, _z` until a free slot. `File.exists()` sees orphans, so the suffix steps past them. | Yes |
| `backups/` (`.csv`, `.xlsx`, `.json`) | `TransactionsScreen` SAF launchers | `ActivityResultContracts.CreateDocument` — user picks destination URI, OS owns it. | Yes — SAF, not File API |
| `PDF/` | `ExpenseReportGenerator` | `PublicDownloadWriter.writeStream` (v2.10.03+) | Yes — via helper |
| `photos/<yyyy-MM-dd_HHmmss>/` | Settings → "Save Photos" (`MainActivity`) | Fresh timestamped subdir per dump; `FileOutputStream(File(subdir, name))` | Yes — fresh subdir per write. Active feature, not deprecated. |
| `support/sync_diag*.txt`, `logcat_*.txt` | `DiagDumpBuilder.writeDiagToMediaStore` (called from Dump button + `DebugDumpWorker` FCM path) | `PublicDownloadWriter.writeBytes` (v2.10.03+) | Yes — via helper |
| `support/pre_restore_backup.json` | `FullBackupSerializer.applyRestore` (production users hit this on restore-after-reinstall) | `PublicDownloadWriter.writeBytes` (v2.10.03+) | Yes — via helper |
| `support/token_log.txt`, `native_sync_log.txt`, `crash_log.txt` | `BudgeTrakApplication.tokenLog` / `syncEvent`, `FirestoreDocSync.syncLog`, uncaught-exception handler | `File.appendText`, wrapped in `try/catch` (silently swallows `EACCES`). Debug-only or crash-only; content is also captured by Crashlytics. | No — but tolerated |

`PublicDownloadWriter` (`data/PublicDownloadWriter.kt`, v2.10.03) is the helper used by every fixed-name write that doesn't get orphan resilience for free. Three-tier strategy:

1. **Cached path** — after a previous MediaStore-fallback success, the resolved on-disk File path is stored in SharedPreferences `public_download_writer` keyed by `<relSubdir>/<fileName>`. Subsequent writes go straight there, skipping the EACCES round-trip.
2. **Canonical direct write** — `File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS), relSubdir).writeBytes(bytes)`. Fast path for fresh installs and own files.
3. **MediaStore fallback** — `MediaStore.Files.getContentUri("external")` insert with `RELATIVE_PATH = Download/<relSubdir>/`. Auto-suffixes ` (1)`, ` (2)`, … when the canonical path is occupied by an orphan. The resolved path is cached for tier 1.

API:
```kotlin
PublicDownloadWriter.writeBytes(context, relSubdir, fileName, mimeType, bytes): File?
PublicDownloadWriter.writeStream(context, relSubdir, fileName, mimeType, produce): File?
```

The stream variant materializes via `ByteArrayOutputStream` first so all three tiers can retry without re-invoking `produce` mid-stream.

Trade-off (chosen 2026-05-02): a single `(N)` suffix may appear after a reinstall, then stays stable for the life of that install (the cache keeps subsequent writes pointed at the same physical file). The user never sees a system delete-confirmation dialog — that was the explicit design choice over a `MediaStore.createDeleteRequest`-based clean-overwrite path.

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

**Call 1.5 — text-only reconciliation.** Re-reads `fullTranscript` to validate `date` and `amountCents` from Call 1 (catches digit-OCR errors and locale date swaps). Falls back silently to Call 1's values on parse error, API error, or timeout. Runs in PARALLEL with Call 2; capped at `CALL1R_TIMEOUT_PAST_C2_MS = 2_000` ms past Call 2 completion — refund receipts with multiple negative numbers can otherwise stretch reconciliation reasoning to ~9 s and block the pipeline.

**Call 2 — image + item names → category scores.** For each item, returns top-K category candidates with scores 0-100. `topChoice` field at top level and `multiCategoryLikely` boolean drive the multi-vs-single routing.

**Call 3 — image + line items → per-item prices.** Multi-cat only. Cents per item; reconciled against Call 1's `amountCents` total (drift ≤ $0.05 acceptable; salesTax preserved exactly).

**Refund-receipt support.** `runCall1` and `runCall1Reconcile` use `Int.MIN_VALUE` as the "missing amountCents" sentinel so legitimate negative cents flow through unmodified. The dialog prefill detects `r.amount < 0` and flips `typeIsExpense = false` + `kotlin.math.abs(...)` for the amount field — refunds become INCOME with positive amount (model invariant: amount always positive, type carries polarity).

**Per-call timing logs (debug only).** Every call emits `Call N dispatch (...)` at start and `Call N response after Nms (...)` on response. `Call1.5: timed out 2000ms past C2 — using C1 values` when the cap fires. Logs land in dump files for forensic latency analysis.

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

`data/ExpenseReportGenerator.kt`. Called from `TransactionsScreen.kt` via `ExpenseReportGenerator.generateReports(context, toSave, categories, currencySymbol)`.

- One PDF per transaction (multi-page)
- Page 1: Expense Report Form (merchant, date, amount, category breakdown)
- Pages 2-N: Full-size receipt photos (up to 5 slots, via `ReceiptManager.getReceiptFile()`)
- Paper: US Letter (612 x 792 pts), margin 40pt
- Output dir: `Download/BudgeTrak/PDF/` via `PublicDownloadWriter.writeStream(...)` (since v2.10.03). Filename `expense_<yyyy-MM-dd>_<merchant>_<txn.id>.pdf`. The helper handles the EACCES case where a backup-restored transaction reuses an orphan PDF filename from a previous install — falls through to MediaStore insert (auto-suffixed) and caches the resolved path so repeat exports of the same transaction don't accumulate `(N)` siblings. Detail in §9.7.

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

### 16.6 Scroll Affordance (bidirectional)

Every scrollable dialog body and every dropdown body shows pulsing arrows for further content above / below. Two composables in `Theme.kt`:

- **`BoxScope.PulsingScrollArrows(scrollState)`** — renders an up-arrow at `TopStart` when `scrollState.canScrollBackward`, and a down-arrow at `BottomStart` when `scrollState.canScrollForward`. Default paddings: `topPadding = 36.dp` (clears `DialogHeader`), `bottomPadding = 50.dp` (clears footer buttons); both 24dp-wide icons animate with `tween(600ms, Reverse)` bounce, alpha 0.5 onSurface.
- **`ScrollableDropdownContent { … }`** — wrap inside a `DropdownMenu` / `ExposedDropdownMenu` when the list may scroll at default or enlarged font. Owns its own `ScrollState`, caps height at `280.dp`, indents its content by `32.dp` on the start edge so items clear the arrow column. Short lists wrap to content size and show no arrows.

## 17. SYNC System (Firestore-Native)

### 17.1 Overview

Up to 5 devices per household sync via Firestore (per-field encrypted docs + filtered snapshot listeners) with RTDB for presence. Per-collection `updatedAt` cursors keep read cost low. Startup loads data async on IO with an EMA-progress gate, then `recomputeCash()` runs synchronously. `BackgroundSyncWorker` is three-tier (foreground skip / VM-alive listener-health / VM-dead full sync). `runPeriodicMaintenance()` consolidates daily checks under a 24-h gate. Cloud Function `onSyncDataWrite` fans out high-priority FCM `sync_push` per write, and `presenceHeartbeat` (15-min Pub/Sub) wakes devices Android has put in aggressive App-Standby Buckets — closing the "widget stale for hours" gap on Samsung-style OEMs.

### 17.2 Per-Field Encryption

Each business field is serialized as `enc_fieldName` in its Firestore
doc using `CryptoHelper.encryptWithKey` (ChaCha20-Poly1305 with the
raw 256-bit group key). Metadata fields (`deviceId`, `updatedAt`,
`deleted`, `lastEditBy`) stay plaintext, enabling server-side filters
(`whereGreaterThan("updatedAt", cursor)`), echo filtering via
`lastEditBy`, and minimal-diff `update()` writes.

### 17.3 Firestore Document Structure

> **See §28.3.1 for the complete document schema** across all 12 sub-collections plus the top-level `pairing_codes`. Sketch below.

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

**Cost** — ~\$160/mo at 100K devices with cursor filters.

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

Layer-2 digest is hex (`toString(16)`), safe to log.

**Layer-1 periodLedger special-case:** `periodLedger` docs are
immutable (no client delete path), so `countActiveDocs` skips the
`deleted = false` filter for this collection.

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

| Component | Purpose |
|-----------|---------|
| EncryptedDocSerializer | Per-field encrypt/decrypt (8 types); `diffFields`, `fieldUpdate`, `toFieldMap` |
| FirestoreDocService | Filtered listeners; `writeBatch` 500-op chunks; `countActiveDocs`; `Source.CACHE` reads |
| FirestoreDocSync | Sync coordinator: per-collection cursors, echo filter, conflict detection, enc-hash cache, `awaitInitialSync`, `triggerFullRestart` |
| SyncWriteHelper | IO singleton: `push<T>`, `pushBatch` with retry + fallback |
| SyncMergeProcessor | Merge logic (FG + worker): category dedup, conflict push-back, settings application |
| PeriodRefreshService | Ledger creation + RE/SG accrual (`@Synchronized`) |
| BackgroundSyncWorker | Three-tier `doWork()`, 15-min periodic + boundary one-shot |
| RealtimePresenceService | RTDB presence + device capabilities |
| FirestoreService | Device / group / pairing / admin-claim / subscription |
| GroupManager | Group lifecycle + pairing-code encrypt/decrypt |
| DebugDumpWorker | Thin delegate to `runDebugDumpInline`; FCM `debug_request`, debug builds |
| WakeReceiver | Manifest `ACTION_POWER_CONNECTED/DISCONNECTED` → `runOnce` (5-min rate limit) |
| DiagDumpBuilder | Diagnostic dump, sanitized device name |
| Helpers | `FcmSender`, `FcmService`, `SecurePrefs`, `PeriodLedger`, `SyncFilters`, `SyncIdGenerator`, `NetworkUtils` |

### 17.13 Three-Tier BackgroundSyncWorker

Sync entry point shared by WorkManager (`doWork`) and FcmService (`runFullSyncInline` companion). Three unique work names: `WORK_NAME = "period_refresh"` (15-min periodic, sync users), `ONESHOT_WORK_NAME = "period_refresh_oneshot"` (FCM/wake fallback), `BOUNDARY_WORK_NAME = "period_boundary_oneshot"` (solo period-boundary one-shot).

**Schedule branching:** `schedule(ctx)` checks `isSyncConfigured`. Sync users get the periodic worker; solo users get a one-shot via `scheduleNextBoundary(ctx)` that fires at the next period boundary (computed via `PeriodRefreshService.nextBoundaryAt` from period/reset config and `familyTimezone`, clamped 60 s–24 h). The slim Tier 3 path re-arms the next boundary at the end of every solo run, perpetuating the chain. Solo users run ~1 worker per period (1/day for DAILY budgets) instead of every 15 minutes.

`MainViewModel.instance` is a WeakReference, used by Tier 2/3 detection.

| Tier | Condition | Work |
|------|-----------|------|
| 1 | `MainActivity.isAppActive` | Return — foreground owns it |
| 2 | ViewModel alive, `isSyncConfigured` | Proactive App Check refresh (16-min threshold, 10-s timeouts); restart dead `docSync` listeners; RTDB `lastSeen` ping (10-s timeout); receipt sync (paid/sub only, gated on `!vm.isReceiptSyncActive()`) |
| 3 | ViewModel dead | Slim path OR full sync — see routing below |

Tiers 2–3 short-circuit on `isSyncConfigured == false` — solo users skip Auth / App Check / RTDB / Firestore entirely.

**Tier 3 slim-vs-full routing.** Tier 3 takes a slim ~25 ms path (period refresh + cash recompute + widget update only) when `!isSyncConfigured` OR `(sourceLabel == "Worker" && lastInlineSyncCompletedAt < 30 min ago && no consistency mismatch pending)`. Solo users always slim (and re-arm the boundary one-shot at end). Sync users on the periodic Worker slim when FCM-inline has recently done the heavy work — periodic stays as a safety net but avoids redundant Firestore-listener cycles. FCM-triggered runs (sourceLabel starts with `"FCM-"`) never slim — they signal real data to fetch. Full Tier 3 is only reached on cold-start or when FCM has been silent for >30 min.

**Tier 3 receipt-sync positioning.** Receipt sync runs as Step 2b — immediately after the initial Firestore merge — so it executes while the worker is young, before any system-level cancellation. Samsung power management has been observed cancelling Tier 3 runs ~1 min 48 s in; later steps (period refresh, pushes, consistency, RTDB ping, widget update) tolerate cancellation: period refresh retries on next run, pushes are idempotent, consistency has a 1-h cooldown, widget catches up on next data change.

**Cancellation handling.** Outer `doWork()` catches `CancellationException` explicitly and logs `stopReason` (API 31+) before rethrowing so WorkManager sees the worker as stopped. `ImageLedgerService.downloadFromCloud` / `getFlagClock` / `getLedgerEntry` rethrow `CancellationException` instead of swallowing it as a generic `Exception`. `ReceiptSyncManager.syncReceipts` logs phase boundaries (step1…step4) so dumps can pinpoint the phase each cycle reached before completion or cancellation.

**Persistent receipt-sync logging.** All `ReceiptSyncManager` construction sites route their `syncLog` callback through `BudgeTrakApplication.syncEvent`, so receipt-sync events land in `token_log.txt` (128 KB rotate) + Crashlytics custom log + logcat, surviving process death. Context-prefixed messages: `ReceiptSync(Tier2)`, `ReceiptSync(Tier3)`, `ReceiptSync(SyncNow)`, `ReceiptSync(onBatch)`, `ReceiptSync(UploadDrainer)`, `ReceiptSync(FgRetry)`.

**Network-awareness gate.** Tier 2 and Tier 3 early-return at the top of their bodies when `NetworkUtils.isOnline(context) == false` — skipping App Check refresh, listener restart, RTDB ping, and receipt sync rather than burning each call's per-SDK timeout. A `Boolean workDone` chain through `runFullSyncBody` → `runTier2` / `runTier3` → `runFullSyncInline` ensures offline-skipped FCM heartbeats do NOT stamp `KEY_LAST_INLINE_AT` (which gates the slim path; a false stamp would suppress real sync for up to 30 min after network recovery). `runFullSyncInline` stamps only when `workDone && sourceLabel.startsWith("FCM-")`. `timedOut` is tracked separately so the `TIME-BUDGET-EXPIRED` log fires only on actual `withTimeoutOrNull` expiry.

**Tier 2 receipt-sync result propagation.** Tier 2 captures `syncReceipts(txns, devices)`'s returned transaction list and applies the changed `receiptId1..5` fields back to `vm.transactions` on `Dispatchers.Main`. Required because `clearLostReceiptSlot`'s push gets echo-filtered through the Firestore listener (`lastEditBy == ourDeviceId` skip) — without the propagation, in-memory state stays stale until app restart and open dialogs render phantom photo frames for slots already cleared on disk. The targeted update (per-id, only `receiptId1..5`) preserves concurrent foreground edits. Tier 2's broad `catch (e: Exception)` rethrows `CancellationException` and surfaces other exceptions to Crashlytics + `token_log.txt` via `syncEvent` with full stack trace.

### 17.14 WakeReceiver

Manifest-registered for `ACTION_POWER_CONNECTED` and
`ACTION_POWER_DISCONNECTED` (the only user-interaction proxies Android
allows static-registered). 5-min rate limit in SharedPrefs, fires
`BackgroundSyncWorker.runOnce()` and logs via `syncEvent()`.
Mitigates aggressive process kills on Samsung One UI.

### 17.15 FCM Wake Architecture

Purely-periodic `WorkManager` work stops firing when Android drops the app into `rare` / `restricted` App-Standby Buckets (one observed dump showed a 4h46m worker silence on a Samsung device). FCM-triggered wakes bypass this because high-priority data FCM goes through the same channel that delivers `debug_request` even in Doze.

**Server-side Cloud Functions** (v1 API, Node.js 22, `functions/index.js`):

| Function | Trigger | Purpose |
|---|---|---|
| `onSyncDataWrite` | Firestore `onWrite` on `groups/{gid}/{collection}/{id}` for the 8 sync collections | Fan-out high-priority FCM `sync_push` to every group device where `deviceId != lastEditBy`. Writer device is excluded. |
| `presenceHeartbeat` | Pub/Sub `every 15 minutes` | Scan RTDB `groups/*/presence`. For any device with `lastSeen > 15 min` stale, send FCM `heartbeat`. Catches devices whose periodic worker has stopped. |
| `onImageLedgerWrite` | Firestore `onWrite` on `groups/{gid}/imageLedger/{rid}` | Filter to rotation / recovery-request / recovery-complete writes; fan out `sync_push` (skips writer + bookkeeping noise). |
| `presenceOrphanCleanup` | Pub/Sub weekly | Removes RTDB presence entries whose Firestore device record is absent or `removed`. |
| `cleanupGroupData` | Firestore `onDelete` on `groups/{gid}` | Cascade-delete subcollections + RTDB + Cloud Storage. |

**Client-side `FcmService.onMessageReceived`** branches by VM lifecycle:

- **VM alive** (`MainViewModel.instance?.get() != null`) → launch on `BudgeTrakApplication.processScope` (file-static `SupervisorJob() + Dispatchers.Default`) and return immediately. The VM keeps the process alive past `onMessageReceived` returning, so long Tier-2 work (snapshot building, multi-photo upload bursts, App Check refresh on cold cellular) all complete naturally with **no time budget**. No WM fallback needed.
- **VM dead** → `runBlocking { runFullSyncInline(ctx, "FCM-$type", 8_500L) }` (1.5-s headroom in the FCM 10-s window). On budget expiry, fall back to `BackgroundSyncWorker.runOnce(ctx)`.

`type = "sync_push"` and `"heartbeat"` share the routing. `type = "debug_request"` uses the parallel `runDebugDumpInline` path (also branches on VM lifecycle); `DebugDumpWorker` is a thin fallback delegate.

In-process `AtomicBoolean isRunning` dedup collapses bursts to one run (16 sync_push FCMs in 350 ms during a CSV import → 1 ran, 15 cleanly skipped). Each FCM arrival logged via `syncEvent("FCM received: type=$type")` — visible in `token_log.txt`.

`pingRtdbLastSeen` in `BackgroundSyncWorker` is wrapped in `withTimeoutOrNull(10_000)` to prevent indefinite hangs from cascading into WorkManager's 10-min cancel + receipt-sync `Job was cancelled`. Both success and failure log via `syncEvent()`.

Cost estimate at 40K groups × 2.2 devices × 12 txns/day: ~$150/mo, dominated by per-write Firestore `devices` subcollection reads + RTDB heartbeat scan. Optimizations queued (cached tokens in group doc; server-side debounce; indexed presence query) drop it toward ~$10–15/mo.

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
`pushAllRecords` (tombstones filtered). **Never** use `lastActivity` for TTL — it's a "last write" stamp, not an expiry — overnight dissolutions before this was fixed.

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
`cleanupGroupData` Cloud Function (v1, Node.js 22) cascade-deletes
14 subcollections + RTDB `groups/{gid}` + all Cloud Storage
`groups/{gid}/` objects. Non-admins detect `status = "dissolved"`
and auto-leave.

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

> **See §28.4 for the complete RTDB schema, security rules, onDisconnect pattern, and the orphan-presence mitigation.**

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
| Release | `PlayIntegrityAppCheckProviderFactory` | **40 h** — set via Firebase Console → Project Settings → Your apps → BudgeTrak → App Check section dropdown |

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

Refresh triggers:

- `MainActivity.onResume`
- Network `onAvailable`
- Worker Tier 2/3 proactive (16-min threshold)
- `triggerFullRestart()` on PERMISSION_DENIED
- ViewModel keep-alive loop (45-min check, 16-min refresh — VM is the backup path if Worker is silenced >45 min by Doze)
- Firebase SDK auto-refresh (~5 min before expiry, in-process only)

### 17.22 Crashlytics + Firebase Analytics + BigQuery

> **See §28.9 (Crashlytics), §28.10 (Firebase Analytics), and §28.11 (BigQuery) for the complete picture: custom-key inventory, non-fatal triggers, event schema, GA4 linkage, BigQuery dataset/table layout, service-account auth, and the `tools/query-crashlytics.js` flag reference.**

In short: both Crashlytics and Analytics share the `crashlyticsEnabled` SharedPref toggle (default true; UI label "Send crash reports and anonymous usage data"). Custom keys (`buildTime`, `versionCode`, `cashDigest`, `listenerStatus`, etc.) are stamped on every future crash/non-fatal so post-publish queries can isolate the latest build via `query-crashlytics.js --build <prefix>`. Crashlytics + Analytics + Performance + Sessions all stream to BigQuery dataset `firebase_crashlytics` / `analytics_<propertyId>`. The query helper UNIONs legacy `com_securesync_app_*` and rebranded `com_techadvantage_budgetrak_*` tables for cross-rebrand history.

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

The pipeline is a single service method `processAndSavePhoto(uri)` in `ReceiptManager`; both the dialog and the list-row camera/gallery paths call it directly.

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

**Rotation / edit propagation via `contentVersion`.** Ledger entries carry a monotonic `contentVersion: Long` (default 0 for fresh uploads). Rotating a photo rewrites local bytes via `ReceiptManager.replaceReceipt`, which calls `ReceiptSyncManager.bumpLocalContentVersionForRotation(ctx, receiptId)` BEFORE `addToPendingQueue` — marking the rotation as pending in `receipt_sync_prefs/content_version_<receiptId>`. `processPendingUploads` then uses three-way decision logic after a successful upload:

1. No entry OR `uploadedAt == 0` → `createLedgerEntry` (fresh upload or servicing a recovery request); stamps `lastEditBy = originatorDeviceId` so step 2 can detect resumes.
2. Entry exists, `uploadedAt > 0`, `lastEditBy == ourDeviceId`, `contentVersion == localContentVersion` → **resume of a partial commit** (cancelled between ledger write and queue removal). Storage upload is idempotent, ledger already at the desired state — skip the bump and just remove from queue. Prevents every cancelled-mid-commit upload from false-rotating and forcing peers to re-download identical content.
3. Otherwise → real rotation OR peer-edited ledger conflict → `incrementContentVersion` bumps counter, resets `possessions` to just the editor, stamps `lastEditBy`, bumps the flag clock.

Peers track `lastSeenContentVersion[receiptId]` in SharedPrefs and, on flag-clock sync, the "uploadedAt > 0 && hasLocalFile" branch of `processLedgerOperations` compares — mismatch → `deleteLocalReceipt`, then same-cycle `processRecovery` downloads the new content and records the new version. Recovery requests preserve `contentVersion` across the delete-and-recreate so stale peers still invalidate after subsequent rotations.

**Snapshot cancellation handling.** `buildSnapshot` and `processSnapshotDownload` catch `CancellationException` separately from generic `Exception` and rethrow after deleting partial files. The build path leaves status as `"building"` (NOT `"error"`) on cancel, so the lifecycle's 2-h staleness gate handles re-claim. A generic-catch swallow would let `runFullSyncInline` return `true` and suppress the WM `runOnce` fallback.

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

**Offline + forensics:** `processPendingUploads` and `processRecovery` early-return with `syncLog` when `NetworkUtils.isOnline(context) == false` (Cloud Storage has no fast-fail path; each queued upload would otherwise burn the full 60 s SDK timeout). The persistent queue stays intact;
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
`_snapshot_request` (single-underscore — Firestore reserves `__*__` IDs).

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

### 18.13 Thumbnail-Bar Interactions

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

### 18.14 AI Receipt OCR Integration

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
| BudgetWidgetProvider | BudgetWidgetProvider.kt | AppWidgetProvider: lifecycle, resetHour alarms, `updateAllWidgets()` throttle, schedules `BackgroundSyncWorker` |
| WidgetRenderer | WidgetRenderer.kt | Canvas bitmap renderer for Solari cards |
| WidgetTransactionActivity | WidgetTransactionActivity.kt | `ComponentActivity` with inline Compose dialog; pushes via `SyncWriteHelper` |
| BackgroundSyncWorker | (Section 17.13) | Widget freshness also comes from here |

`BudgetWidgetProvider.onUpdate` simply calls `BackgroundSyncWorker.schedule(context)` — there is no separate widget worker.

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
- `BackgroundSyncWorker` — periodic 15-min for sync users; one-shot per period boundary for solo users (see §17.13). Plus Tier-3 cold-start freshness.
- Settings (theme, currency, logo toggle)
- Exact-alarm (`setExactAndAllowWhileIdle`) at the next budget reset boundary for every period type

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

### 20.2 Type summary

> **See LLD §5 for full per-field tables.** This SSD section gives the high-level shape; the LLD has every field's type, default, and sync semantics enumerated.

| Type | Key fields | Notes |
|------|------------|-------|
| Transaction | id, type, date, source, amount, categoryAmounts[], receiptId1..5, deviceId, deleted | Amount always positive; `type` carries polarity (refund = INCOME with positive amount). 4 link-id pairs (recurringExpense / incomeSource / amortization / savingsGoal) carry remembered amounts at link time. |
| CategoryAmount | categoryId, amount | Used inside Transaction.categoryAmounts; preserves multi-cat allocations across sync |
| Category | id, tag, name, deviceId, deleted | `tag = "other"` is the system fallback bucket |
| IncomeSource | id, name, amount, repeatType, repeatInterval, anchorDate, isPaused | `RepeatType.WEEKS`/`DAYS` use `repeatInterval`; others ignore it |
| RecurringExpense | id, name, amount, repeatType, repeatInterval, anchorDate, isPaused, isAccelerated | Acceleration = `expectedAmount` budgeted, actual deducted on save |
| AmortizationEntry | id, name, totalAmount, periodicAmount, repeatType, anchorDate, paid, isPaused | `paid` advances on each linked transaction; period-budget excludes |
| SavingsGoal | id, name, targetAmount, totalSavedSoFar, mode (TARGET_DATE / FIXED / SUPERCHARGE), targetDate, fixedPerPeriod | Three contribution modes with different auto-computation paths |
| SharedSettings | budgetPeriod, resetHour, resetDayOfWeek, resetDayOfMonth, familyTimezone, currencySymbol, manualBudget, archive thresholds, ... | Single doc; per-field encrypted; field renames across sync would break compat (memory `feedback_preserve_persistence_names.md`) |
| PeriodLedgerEntry | id, periodStartDate, appliedAmount | Tracks the sum applied at each period reset for deterministic carry-forward |
| ImageLedgerEntry | receiptId, originatorDeviceId, possessions{}, uploadedAt, contentVersion, lastEditBy, ... | Drives the 4-layer receipt sync; see §18 |

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

> Indicative snapshot — refresh with `find app/src/main/java -name "*.kt" | xargs wc -l` for an exact tree count. Currently ~100 files / ~51,500 lines.

### 26.1 Top Files by Size

| Rank | File | Approx. lines |
|------|------|---------------|
| 1 | TransactionsScreen.kt | 6,300 |
| 2 | MainViewModel.kt | 3,200 |
| 3 | MainActivity.kt | 2,600 |
| 4 | EnglishStrings.kt | 1,930 |
| 5 | SpanishStrings.kt | 1,910 |
| 6 | SettingsScreen.kt | 1,700 |
| 7 | AppStrings.kt | 1,530 |
| 8 | TranslationContext.kt | 1,500 |
| 9 | MainScreen.kt | 1,300 |
| 10 | BudgetConfigScreen.kt | 1,250 |
| 11 | SyncScreen.kt | 1,200 |
| 12 | BackgroundSyncWorker.kt | 1,180 |
| 13 | RecurringExpensesScreen.kt | 1,100 |
| 14 | EncryptedDocSerializer.kt | 1,040 |
| 15 | ReceiptSyncManager.kt | 1,010 |
| 16 | WidgetTransactionActivity.kt | 1,000 |
| 17 | CsvParser.kt | 1,000 |
| 18 | TransactionsHelpScreen.kt | 1,050 |
| 19 | FirestoreDocSync.kt | 930 |
| 20 | ImageLedgerService.kt | 870 |

### 26.2 File Count by Package

| Package | Files |
|---------|-------|
| com.techadvantage.budgetrak (root) | 3 |
| .data | 31 |
| .data.sync | 22 |
| .data.ocr | 2 |
| .data.ai | 2 |
| .data.telemetry | 1 |
| .sound | 1 |
| .ui.components | 5 |
| .ui.screens | 22 |
| .ui.strings | 5 |
| .ui.theme | 3 |
| .widget | 3 |
| **TOTAL** | **~100** |

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
| namespace | com.techadvantage.budgetrak |
| applicationId (release) | com.techadvantage.budgetrak |
| applicationId (debug, v2.10.04+) | com.techadvantage.budgetrak**`.debug`** (via `applicationIdSuffix = ".debug"` on the `debug` buildType) |
| compileSdk | 35 (canonical / CI); flipped to 34 locally for Termux compiles since Termux's bundled aapt2 v2.19 can't load android-35 platform jar |
| minSdk | 28 |
| targetSdk | 35 (canonical / CI); 34 in Termux compiles |
| versionCode / versionName | 19 / 2.10.04 (dev), 15 / 2.10.00 in Internal Testing |
| versionNameSuffix (debug) | `-debug` (debug builds render as e.g. `2.10.04-debug` in Settings + Crashlytics) |
| source/target / jvmTarget | Java 17 |
| compose / buildConfig | enabled |
| minify / shrinkResources (release) | true |
| release signingConfig | reads `BUDGETRAK_KEYSTORE_FILE`, `BUDGETRAK_KEYSTORE_PASSWORD`, `BUDGETRAK_KEY_ALIAS` from `local.properties` (git-ignored). Upload keystore at `~/keystore/upload-keystore.jks` with offline backup; SHA-256 `E0:2B:5D:D6:5E:86:1B:3B:79:AC:F4:F3:F4:76:D4:3B:35:D1:FC:3A:D4:E1:6D:26:C0:CC:0D:22:E9:9D:04:0A`. Used by `./gradlew bundleRelease` to produce signed AAB for Play Store upload. |
| BuildConfig field `APP_CHECK_DEBUG_TOKEN` | sourced from `local.properties:APP_CHECK_DEBUG_TOKEN` (gitignored). Pinned UUID seeded into the firebase-appcheck-debug SharedPreferences in `BudgeTrakApplication.onCreate` (debug branch only). See §28.6.1. |

BuildConfig emits a UTC `BUILD_TIME` stamp; `BudgeTrakApplication.onCreate` re-publishes it as a Crashlytics custom key so BigQuery queries can isolate the latest build's events post-publish (see §28.9.1).

**Debug `applicationIdSuffix` rationale (added v2.10.04, 2026-05-03):** debug builds use a different keystore (the AGP-auto-generated `~/.android/debug.keystore`) than release builds (the Play upload keystore), so Android refuses install-over conflicts on the same package name. Suffixing the debug applicationId to `com.techadvantage.budgetrak.debug` makes them two separate Android packages — both can coexist on the same device, each with its own data sandbox, Firebase Anonymous Auth UID, FCM token, App Check provider, Firestore group membership, and SecurePrefs-backed sync key. Setup requires a corresponding second Android app entry (`com.techadvantage.budgetrak.debug`) registered in the Firebase project; `app/google-services.json` then carries entries for both applicationIds. The pinned debug-token UUID is registered under both Firebase app entries' "Manage debug tokens" lists so either applicationId authenticates against the same Console-registered token. Full setup steps: `firebase-config-reference.txt` §5b.

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

## 28. Backend Infrastructure & External Services

This chapter consolidates every server-side, cloud, and external-service configuration the app depends on. The intent is reconstructability — given this chapter and the Kotlin source, the live system can be re-deployed from scratch. Where feasible, security rules and Cloud Function logic are reproduced verbatim rather than summarized; where the live console holds settings that don't exist as code, the values are stated with their console paths.

The app's runtime backend is a single Firebase project, **`sync-23ce9`** (display name "SecureSync (Prod)"). It hosts Authentication (anonymous), Cloud Firestore (default database), Realtime Database, Cloud Storage (default bucket), App Check, Cloud Messaging (FCM), Cloud Functions, Crashlytics, and Firebase Analytics linked to a Google Analytics 4 property. Two external dependencies sit alongside: **Google Generative AI / Gemini API** (Flash-Lite 2.5 for AI categorization and OCR) and **BigQuery** (Crashlytics + Analytics streaming export, queried from Termux for diagnostics).

### 28.1 Firebase Project

| Field | Value |
|---|---|
| Project ID | `sync-23ce9` |
| Display name | `SecureSync (Prod)` |
| Project number | `620762828182` |
| Plan | Blaze (pay-as-you-go) — required for Cloud Functions, Storage egress, RTDB beyond free tier, BigQuery export |
| Console URL | `https://console.firebase.google.com/project/sync-23ce9` |
| Android package | `com.techadvantage.budgetrak` (rebranded 2026-04-11 from `com.securesync.app` / `com.syncbudget.app` — legacy entry retained in the Firebase Console for backward-compat token handling) |
| Mobile SDK app ID | `1:620762828182:android:2f4fcc7ca522516ccca1b7` |
| Firestore database | `(default)` |
| RTDB instance | `sync-23ce9-default-rtdb` (region `us-central1`) |
| Storage bucket | `sync-23ce9.firebasestorage.app` (default region) |
| GA4 property | property ID `534603748`, stream "BudgeTrak Android" id `14591145419` |
| BigQuery project | same — `sync-23ce9` (export lives in the Firebase project, not a separate analytics project) |

`app/google-services.json` carries the project number, RTDB URL, project ID, storage bucket, and the per-app `mobilesdk_app_id` and `api_key`. The file is checked into the repo (no secrets — the `api_key` is a public Android API key gated by App Check + Firestore rules, not an admin credential).

### 28.2 Firebase Anonymous Authentication

**Why anonymous.** BudgeTrak doesn't ask the user for an email/password — it's a local-first budgeting app with optional multi-device sync. The Firebase auth UID is a privacy-preserving handle for two purposes: (1) Firestore membership rules use `request.auth.uid` to gate access to a group's data, and (2) the `groups/{gid}/members/{uid}` doc is the membership record. UIDs aren't surfaced in the UI; the app calls users "you" and "Kim" via local device names.

**Provisioning.** `MainViewModel.signInAnonymouslyIfNeeded()` (around `MainViewModel.kt:338`) calls `FirebaseAuth.getInstance().signInAnonymously().await()` exactly once when both (a) `isSyncConfigured` and (b) `currentUser == null`. Network-aware: skipped offline, retried via the `networkCallback.onAvailable` resume path. `BackgroundSyncWorker` enforces the same precondition before any Firestore call (`BackgroundSyncWorker.kt:585` re-runs `signInAnonymously` synchronously inside Tier 2/3 if VM has been reaped and auth is gone).

**Lifecycle.** `BudgeTrakApplication.onCreate` registers an `addAuthStateListener` that emits a `tokenLog("Auth state: uid=… anon=…")` entry on every transition and updates Crashlytics custom keys (`setUserId(uid)`, `setCustomKey("authAnonymous", isAnonymous)`). The listener is registered after the Crashlytics opt-out check so a user who has disabled telemetry never has their UID stamped.

**Anonymous auth is one-way.** Once an anonymous user is created, the app never converts to email/password. Reinstall produces a new anonymous UID; the old `members/{old_uid}` doc is left dangling and is reaped when the group itself TTLs, when a peer admin manually evicts, or when `cleanupGroupData` cascades. Never use `members/{uid}` count as a "currently active devices" signal — use `groups/{gid}/devices` instead, which has a `removed` flag and an `updatedAt` cursor.

**App Check interaction.** Anonymous Auth is intentionally **not** enforced under App Check (see §28.6). The token is needed to mint App Check tokens for downstream services; if Auth required App Check, it would deadlock on a cold start. Downstream services (Firestore, RTDB, Storage) all enforce, so an attacker who acquires an anonymous UID without App Check still cannot read or write any data.

### 28.3 Cloud Firestore

#### 28.3.1 Document structure

```
groups/
  {groupId}/                              # 12-char hex, generated client-side
    lastActivity         : Timestamp        # Server timestamp (informational only)
    expiresAt            : Timestamp        # now + 90 d (TTL field)
    status               : string?          # "dissolved" when group deleted
    subscriptionExpiry   : number?          # Epoch ms — admin's Play subscription
    imageLedgerFlagClock : number?          # Counter, bumped on meaningful imageLedger writes
    imageLastCleanupDate : string?          # "YYYY-MM-DD" of last 14-d prune
    deviceChecksums      : map<deviceId, {cashHash, txCount, recordedAt}>
    checksumMismatchAt   : number?          # 1-h confirmation gate for mismatches
    debug_{deviceId}_*   : string?          # FCM debug-dump fields (per device)

    transactions/                           # Per-field encrypted business data
      {docId}/
        enc_{fieldName}  : string           # ChaCha20-Poly1305(base64), each Kotlin field encrypted independently
        deviceId         : string           # Originating device (plaintext metadata)
        deleted          : boolean          # Tombstone
        lastEditBy       : string           # Most recent writer's deviceId
        updatedAt        : Timestamp        # Server timestamp (cursor source)

    recurringExpenses/                      # Same enc_/metadata pattern
    incomeSources/
    savingsGoals/
    amortizationEntries/
    categories/
    periodLedger/                           # Period rollovers (ledger entries)

    sharedSettings/
      current/                              # Singleton doc (id == "current")
        enc_*            : string
        updatedAt        : Timestamp
        lastEditBy       : string

    members/                                # Membership / security rule source
      {auth.uid}/
        deviceId         : string           # Correlated device ID
        joinedAt         : number           # Epoch ms

    devices/
      {deviceId}/
        deviceId         : string
        deviceName       : string
        isAdmin          : boolean
        removed          : boolean          # Soft-delete (admin eviction or self-leave)
        lastSyncVersion  : number
        lastSeen         : number           # Written by BackgroundSyncWorker (epoch ms)
        appSyncVersion   : number
        minSyncVersion   : number
        photoCapable     : boolean
        uploadSpeedBps   : number?
        fcmToken         : string?          # Per-device FCM token

    imageLedger/
      {receiptId}/
        receiptId          : string
        originatorDeviceId : string
        createdAt          : number
        uploadedAt         : number          # 0 = recovery request (no upload yet)
        contentVersion     : number
        uploadAssignee     : string?
        assignedAt         : number
        possessions        : map<deviceId, boolean>
        lastEditBy         : string
      _snapshot_request/                     # Snapshot lifecycle doc (special doc id)
        requestedBy   : string
        requestedAt   : number
        builderId     : string?
        status        : string
        consumedBy    : map<deviceId, boolean>

    adminClaim/
      current/                               # Singleton (id == "current")
        claimantDeviceId : string
        claimantName     : string
        claimedAt        : number
        expiresAt        : number
        objections       : string[]
        status           : string

    deltas/    snapshots/                    # Legacy CRDT collections — may exist on pre-2.0 groups; not written anymore but cleanupGroupData purges them

pairing_codes/
  {CODE}/                                    # 6-char alphanumeric (A-Z, 2-9), the doc ID is the code itself
    groupId        : string
    encryptedKey   : string                  # Group key wrapped under the code
    expiresAt      : Timestamp               # 10-min TTL
```

The data-collection layout is duplicated by FirestoreService client code (`FirestoreService.kt`) and by the Cloud Function subcollection list — keep both in sync when adding a collection. The `enc_` field naming convention is enforced by `EncryptedDocSerializer`: every Kotlin field except plaintext metadata (`deviceId`, `deleted`, `lastEditBy`, `updatedAt`) is serialized with the `enc_` prefix.

#### 28.3.2 Security rules (verbatim)

Source of truth: `firestore.rules` at the repo root. Deploy with `firebase deploy --only firestore:rules`. Refresh from live with `node tools/fetch-rules.js`.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isMember(groupId) {
      return request.auth != null &&
        exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    }

    match /groups/{groupId} {
      // `get` (single doc by ID) is allowed for any authenticated user so
      // flows like dissolution/eviction detection can work. `list` is
      // explicitly forbidden — without it, any authenticated user could
      // enumerate every group in the database and harvest per-device cash
      // checksums, flag clocks, and timezone metadata.
      allow get: if request.auth != null;
      allow list: if false;
      allow create: if request.auth != null;
      allow update, delete: if isMember(groupId);

      match /members/{uid} {
        allow create: if request.auth != null && request.auth.uid == uid;
        allow read: if request.auth != null && (request.auth.uid == uid || isMember(groupId));
        allow delete: if request.auth != null &&
          (request.auth.uid == uid || isMember(groupId));
      }

      match /transactions/{docId}        { allow read, write: if isMember(groupId); }
      match /recurringExpenses/{docId}   { allow read, write: if isMember(groupId); }
      match /incomeSources/{docId}       { allow read, write: if isMember(groupId); }
      match /savingsGoals/{docId}        { allow read, write: if isMember(groupId); }
      match /amortizationEntries/{docId} { allow read, write: if isMember(groupId); }
      match /categories/{docId}          { allow read, write: if isMember(groupId); }
      match /periodLedger/{docId}        { allow read, write: if isMember(groupId); }
      match /sharedSettings/{docId}      { allow read, write: if isMember(groupId); }
      match /devices/{docId}             { allow read, write: if isMember(groupId); }
      match /imageLedger/{docId}         { allow read, write: if isMember(groupId); }
      match /adminClaim/{docId}          { allow read, write: if isMember(groupId); }
    }

    match /pairing_codes/{code} {
      allow create: if request.auth != null
        && request.resource.data.keys().hasAll(['groupId', 'encryptedKey', 'expiresAt'])
        && request.resource.data.expiresAt is timestamp;
      // `get` requires you to already know the pairing code (it's the doc
      // ID). `list` would let any authenticated user enumerate every active
      // pairing code + its encryptedKey — the code itself is the
      // decryption password, so the encryptedKey alone is useless, but
      // the doc ID gives the code directly. Must forbid list.
      allow get: if request.auth != null;
      allow list: if false;
      allow delete: if request.auth != null;
    }

    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

**Design notes.**
- **Membership-based.** `isMember(gid)` checks `exists(groups/{gid}/members/{auth.uid})`. Any access to a data subcollection requires this check.
- **Self-registration.** `members/{uid}` create is allowed only when `request.auth.uid == uid` so users can join a group only by writing their own membership record (the uid in the path must equal their token).
- **`get` vs `list`.** Both `groups/{gid}` and `pairing_codes/{code}` allow `get` (read by ID) but forbid `list` (collection enumeration). On groups this prevents harvesting checksums and metadata across the entire database; on pairing codes it prevents harvesting the codes themselves (the doc ID is the code).
- **Defense in depth on top of encryption.** Even if a rule regression let an attacker read a sub-collection, business data is ChaCha20-Poly1305-encrypted with a per-group key (held client-side in members' encrypted prefs); they would see only `enc_*` blobs and metadata.
- **Deny-all wildcard.** The trailing `match /{document=**}` ensures any future top-level collection added accidentally is denied until a rule is written.

#### 28.3.3 Indexes

- **Automatic indexes** (built-in, always on): ascending, descending, arrays — collection scope.
- **No manual composite indexes required.** Filtered listeners use `whereGreaterThan("updatedAt", cursor)` against a single-field index on `updatedAt` which Firestore auto-creates.
- **Monitoring.** If logcat shows `FAILED_PRECONDITION: The query requires an index`, click the link in the error to auto-create. Don't pre-emptively define indexes.

#### 28.3.4 TTL policies

| Collection group | TTL field | Lifetime | Source of writes |
|---|---|---|---|
| `groups` | `expiresAt` | now + 90 d | `FirestoreService.updateGroupActivity()` (called once per app launch + on group join) |
| `pairing_codes` | `expiresAt` | now + 10 min | `FirestoreService.createPairingCode()` |

Configured at Firestore Console → TTL policies → Add Policy. **Do not** use `lastActivity` (server timestamp) as a TTL field — that caused immediate eligibility (fixed 2026-04-04). Use absolute future timestamps stored in `expiresAt`.

**TTL deletion is not instantaneous** — Firestore reaps within 24–72 h of expiry. TTL also only deletes the matched doc, not subcollections, RTDB nodes, or Storage files. Cascade cleanup is handled by the `cleanupGroupData` Cloud Function (§28.7.1).

### 28.4 Realtime Database

#### 28.4.1 Schema

```
groups/
  {groupId}/
    presence/
      {deviceId}/
        online                : boolean        # Server-managed via onDisconnect()
        lastSeen              : number         # ServerValue.TIMESTAMP, ms
        deviceName            : string
        photoCapable          : boolean
        uploadSpeedBps        : number?
        uploadSpeedMeasuredAt : number?
```

Single-purpose: instant online/offline presence detection across group devices, with reliable disconnect detection via the RTDB server's TCP-keep-alive heartbeat. Replaces a pre-2.0 Firestore polling design that was too slow + too expensive.

#### 28.4.2 Security rules (verbatim)

Source of truth: `database.rules.json` at the repo root.

```json
{
  "rules": {
    "groups": {
      "$groupId": {
        "presence": {
          ".read": "auth != null",
          "$deviceId": {
            ".write": "auth != null",
            ".validate": "newData.hasChildren(['online', 'lastSeen', 'deviceName']) && newData.child('online').isBoolean() && newData.child('lastSeen').isNumber()"
          }
        }
      }
    },
    ".read": false,
    ".write": false
  }
}
```

**Design notes.**
- **Auth-gated read.** Reading `groups/{gid}/presence` requires any authenticated user. Group ID is treated as the access secret (same model as Firestore — the 12-char hex group ID is effectively unguessable).
- **Validated writes.** Each presence node must have `online` (boolean), `lastSeen` (number), and `deviceName` (any). Prevents garbage writes that would trip the client validators.
- **Top-level deny.** `".read": false` + `".write": false` at the root ensures nothing else under the database root is accessible.
- **Known gap:** RTDB rules can't cross-reference Firestore, so `.write: auth != null` permits any authenticated user to write to **any** `groups/$gid/presence/$deviceId` regardless of group membership. Mitigated, not closed, by the `presenceOrphanCleanup` Cloud Function (§28.7.5) and by `presenceHeartbeat`'s membership-gated FCM-send (§28.7.4) which means orphan presence cannot escalate into FCM spam — only into RTDB node bloat. Permanent closure would require either App Check enforcement on RTDB writes (currently `Enforce`d, see §28.6) or a server-mediated write path.

#### 28.4.3 onDisconnect pattern

`RealtimePresenceService.setupPresence(deviceId, deviceName, photoCapable)` is the only writer. On startup it:

1. Connects to `.info/connected` (a special RTDB path that emits `true`/`false` based on the SDK's session state).
2. Whenever `connected == true`, writes `{ online: true, lastSeen: ServerValue.TIMESTAMP, deviceName, photoCapable, uploadSpeedBps?, uploadSpeedMeasuredAt? }` to `groups/{gid}/presence/{deviceId}`, and registers an `onDisconnect()` handler that will write `{ online: false, lastSeen: ServerValue.TIMESTAMP }` server-side when the connection drops (TCP keep-alive timeout, app process killed, etc.).
3. **Idempotent re-arm.** `connectedListener` and `myPresenceRef.onDisconnect()` are explicitly canceled before re-installing on each call, so repeated calls (e.g., from network resume) don't stack handlers.

The `RealtimePresenceService` writer is the **only** legitimate writer of presence data. A peer reading `RealtimePresenceService.getDevices()` derives "active devices" by filtering `presence` entries where `online == true && lastSeen > now - 5 min`.

#### 28.4.4 Orphan-presence problem

The `.write: auth != null` rule means a malicious or malformed client could write fake presence entries for arbitrary `(groupId, deviceId)` pairs. Cleanup is `presenceOrphanCleanup` (§28.7.5), which runs every Sunday 03:00 UTC and removes entries whose corresponding Firestore `devices/{deviceId}` is missing or `removed: true`.

### 28.5 Cloud Storage

#### 28.5.1 Bucket layout

Default bucket `sync-23ce9.firebasestorage.app`. Per-group folder, encrypted file payloads:

```
groups/
  {groupId}/
    receipts/
      {receiptId}.enc           # ChaCha20-Poly1305 receipt photo
    photoSnapshot.enc           # Encrypted snapshot archive (transient — built on demand for newcomer-onboarding, deleted ≤ 7 d)
    joinSnapshot.enc            # Onboarding snapshot for a new device
```

Receipt files are written by `ImageLedgerService.uploadToCloud()` after compression by `ReceiptManager` (§18.3) and ChaCha20-Poly1305 encryption with the same group key used for Firestore. Files are not directly readable from the bucket — they are encrypted blobs.

#### 28.5.2 Security rules (verbatim)

Source of truth: `storage.rules` at the repo root.

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /groups/{groupId}/{allPaths=**} {
      allow read, write: if request.auth != null
        && firestore.exists(/databases/(default)/documents/groups/$(groupId)/members/$(request.auth.uid));
    }
    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

The `firestore.exists()` call is a **cross-service rule** — Storage rules can read from Firestore. Performance-wise this adds one Firestore read per Storage operation; cost is acceptable for the safety it provides.

#### 28.5.3 Lifecycle

- **Receipt files** are deleted by `ImageLedgerService.deleteFromCloud()` when the corresponding `imageLedger/{receiptId}` document is deleted. Tombstone-driven, not Storage-lifecycle-driven.
- **14-day cloud pruning** (`ReceiptSyncManager`) deletes any file older than 14 d, regardless of ledger state. Coordinated across devices via `groups/{gid}/imageLastCleanupDate` so only one device per day does the work.
- **Snapshot archives** are deleted when their `imageLedger/_snapshot_request` doc is deleted (typically ≤ 7 d after build, by the snapshot consumer or the snapshot-cleanup admin path).
- **Orphan scan** (`runPeriodicMaintenance` admin path, 30-d gate) lists `groups/{gid}/receipts/` and deletes any file with no matching ledger entry. Skips files < 10 min old to avoid races with concurrent uploads.
- **Group dissolution** triggers `cleanupGroupData` (§28.7.1) which lists every file under `groups/{gid}/` and deletes them all (paginated).

### 28.6 Firebase App Check

App Check ensures only legitimate, signed instances of the BudgeTrak app can talk to the backend. Without it, anyone with a leaked anonymous Auth token + the public Firebase config could read/write the database.

#### 28.6.1 Provider configuration

Installed in `BudgeTrakApplication.onCreate` before any Firebase service call:

```kotlin
val providerFactory = if (BuildConfig.DEBUG)
    DebugAppCheckProviderFactory.getInstance()
else
    PlayIntegrityAppCheckProviderFactory.getInstance()
appCheck.installAppCheckProviderFactory(providerFactory)
```

**Debug builds** use the debug provider. Two complementary mechanisms give the SDK a registered token:

1. **Pinned UUID seed (preferred, v2.10.03+)** — `local.properties` carries a fixed UUID under key `APP_CHECK_DEBUG_TOKEN`; `app/build.gradle.kts` exposes it as `BuildConfig.APP_CHECK_DEBUG_TOKEN`; `BudgeTrakApplication.onCreate` (debug branch) writes it into the SDK's prefs file `com.google.firebase.appcheck.debug.store.<FirebaseApp.persistenceKey>` under key `com.google.firebase.appcheck.debug.DEBUG_SECRET` **before** `installAppCheckProviderFactory`. The SDK reuses this pre-seeded token instead of generating a fresh per-install UUID. One Console-registered UUID covers every dev/test device and survives reinstall / clear-data — no Console roundtrip per APK. Skip-if-already-equal so cold starts don't churn the prefs.

   *Security model*: the pinned UUID is baked into every debug APK and recoverable via decompile. Acceptable because (a) debug APKs aren't shipped to end users (release uses Play Integrity, per-install attestation), (b) the UUID only authenticates "BudgeTrak debug" — Firestore/RTDB/Storage rules still enforce per-user/per-group access. Revoke by deleting the Console entry and rotating to a new UUID.

2. **Legacy logcat scrape (fallback)** — the first launch of a debug build emits a `DebugAppCheckProvider` log line containing the active debug secret; `BudgeTrakApplication.onCreate` greps logcat for it (`Regex("debug secret.*: ([a-f0-9-]+)", IGNORE_CASE)`) and writes the secret to `token_log.txt` so it's visible in FCM dump uploads. Useful when `APP_CHECK_DEBUG_TOKEN` is unset (the SDK falls back to per-install UUIDs and the scrape surfaces them for ad-hoc Console registration).

Debug tokens must be registered in Firebase Console → App Check → Apps → BudgeTrak Android → Manage debug tokens.

**Release builds** use Play Integrity. The Play Integrity Service hits Google's attestation servers each refresh, which requires a Play-installed APK (or a Play-signed APK sideload from Internal Testing) — meaningfully harder to forge than the debug provider.

#### 28.6.2 Console settings

| Setting | Path | Value |
|---|---|---|
| Token TTL — Play Integrity | Project Settings → Your apps → BudgeTrak Android → App Check section dropdown | **40 hours** (raised from default 1 h to reduce attestation traffic) |
| Token TTL — Debug | (same dropdown) | **1 hour** — Google-imposed, ignores Console setting (by design for short-lived dev tokens) |
| Enforcement — Firestore | App Check → APIs → Firestore | **Enforce** |
| Enforcement — Realtime Database | App Check → APIs → Realtime Database | **Enforce** |
| Enforcement — Cloud Storage | App Check → APIs → Cloud Storage | **Enforce** |
| Enforcement — Authentication | App Check → APIs → Authentication | **Monitor** (must remain unenforced — anonymous Auth must succeed before App Check has a token to mint) |
| Play Integrity — `PLAY_RECOGNIZED` | App Check → Apps → BudgeTrak Android → Play Integrity → Advanced settings | **Required** (anti-piracy: blocks modified or re-signed APKs) |
| Play Integrity — `LICENSED` | (same panel) | **Not required** (don't gate free users on Huawei / degooglified devices) |
| Play Integrity — Device integrity | (same panel) | "Don't explicitly check" — relies on `PLAY_RECOGNIZED` plus per-field encryption for actual data protection. Tighten post-launch if abuse appears in Crashlytics. |

The 40 h vs 1 h TTL gap means **debug-build refresh cadence is ~40× higher than release**. When measuring App Check refresh behavior, normalize for this.

#### 28.6.3 Refresh triggers

All gated by `isSyncConfigured` (solo users never call App Check). All `getAppCheckToken()` calls wrapped with `withTimeoutOrNull(10–15 s)` to handle network stalls.

| Trigger | Threshold | Source |
|---|---|---|
| `onResume` | Always | `MainActivity.onResume` |
| Network resume (`onAvailable`) | Always | `MainViewModel.networkCallback` |
| `BackgroundSyncWorker` Tier 2/3 | Proactive — 16 min before expiry | `BackgroundSyncWorker.runTier2/runTier3` |
| `triggerFullRestart()` | On `PERMISSION_DENIED` | `FirestoreDocSync` |
| ViewModel keep-alive | 45-min check, 16-min refresh | `MainViewModel` keep-alive loop |
| SDK auto-refresh | ~5 min before expiry | Firebase SDK (in-process only) |

The 16-min threshold (dropped from 35 min on 2026-04-25) is calibrated so that one heartbeat per ~4 h Play Integrity token cycle catches the refresh and the rest skip. At 35 min, refreshes were colliding too often between Worker and VM keep-alive.

#### 28.6.4 Failure mode

If App Check refresh fails (Play Integrity timeout, no network), every Firestore/RTDB/Storage operation will start returning `PERMISSION_DENIED`. The client's `FirestoreDocSync` watches for this signature and triggers `triggerFullRestart()` — stops all listeners, force-refreshes App Check, restarts. Debounced 30 s to prevent thrash.

#### 28.6.5 Project linkage prerequisites — MANDATORY for Enforce mode

Documented after a multi-hour debug session on 2026-05-03. The following four Console steps must all be completed before App Check Play Integrity will mint valid tokens for release builds. Skipping any single one produces identical silent-failure symptoms — no `AppCheck token refreshed` log, no metrics card under App Check → Apps → BudgeTrak Android → Play Integrity, every Firestore call rejected with `PERMISSION_DENIED`, every Storage call rejected with the misleading `"User is not authenticated"` (the Firebase Storage SDK's generic 401 phrasing — App Check failures surface here as well as on Firestore).

| # | Step | Console path | Notes |
|---|---|---|---|
| 1 | Enable Play Integrity API in Cloud | https://console.cloud.google.com/apis/library/playintegrity.googleapis.com?project=sync-23ce9 | Distinct from the Play Developer API used for the publishing pipeline (CI auto-publish). Both must be enabled. |
| 2 | Link Firebase project to Google Play | Firebase Console → Project settings → Integrations → Google Play | Tick **App Distribution + Crashlytics + Google Analytics** when establishing the link. The OAuth scopes the Play Integrity verifier needs are bundled under these service grants; ticking only "App Distribution" leaves App Check unable to query the Play Integrity API. |
| 3 | Link Cloud project to Play Integrity API | Play Console → Setup → App integrity → Play Integrity API section → "Link Cloud project" | **Separate** from step 2. Step 2 is project-level Firebase ↔ Play; step 3 is product-specific Cloud-project ↔ Play-Integrity-API. Both are required. After linking, the Play Integrity API status flips from "Integration not started" to "Integration started." Ignore the third checklist item ("Integrate the Play Integrity API") — that's for apps calling the API directly from their own code; Firebase handles the integration server-side. |
| 4 | Register Play **app signing key** SHA-256 in Firebase | Firebase Console → Project settings → Your apps → BudgeTrak Android → SHA certificate fingerprints → Add fingerprint | The SHA-256 to paste comes from **Play Console → Setup → App integrity → "App signing" section → "App signing key certificate"** — *not* the upload key SHA. Play strips the upload signature on receipt of the AAB and re-signs the installable APK with its own per-app signing key, so the runtime fingerprint that Play Integrity attests against is the app signing key, not the upload key. Firebase's verifier rejects every token whose runtime fingerprint isn't in the Project-settings fingerprint list. The upload-key SHA-256 stays in the list too (covers sideload of upload-signed AABs and other Auth flows); both fingerprints coexist. |

The order matters: a setup that completes only steps 1, 2, and 3 can render an empty Play Integrity metrics card and still reject every token. Step 4 is the one that's silently absent in most first-time setups, because Firebase Console only prompts for SHA-1 originally (legacy from the OAuth/Sign-In era). Verified-working configuration captured in `firebase-config-reference.txt` §5b.

### 28.7 Cloud Functions

All in `functions/index.js` (single file, 347 lines as of 2026-05). v1 API. `firebase-functions ^5.1.1` + `firebase-admin ^12.7.0`. Node.js 22 per `functions/package.json` `engines.node`. Region `us-central1` (default). Deploy: `firebase deploy --only functions` from the project root in Termux.

Five functions: two onWrite/onDelete triggers, two scheduled jobs, plus shared helpers. The first deployment auto-enables `cloudfunctions`, `cloudbuild`, `artifactregistry`, and `cloudscheduler` APIs in the underlying GCP project.

`firebase-config-reference.txt` says Node.js 20 — that's stale; `package.json` engines is the source of truth.

#### 28.7.1 `cleanupGroupData` — Firestore onDelete

```
functions.firestore.document('groups/{groupId}').onDelete(...)
```

When the group doc is deleted (TTL after 90 d inactivity, or admin dissolution), cascade-delete:

1. **Firestore subcollections** — paginated delete of:
   ```
   transactions, recurringExpenses, incomeSources, savingsGoals,
   amortizationEntries, categories, periodLedger, sharedSettings,
   devices, members, imageLedger, adminClaim,
   deltas, snapshots                             // legacy CRDT (may exist on old groups)
   ```
   Page size 500; loop while a page returns >= 500 docs.
2. **RTDB** — `rtdb.ref('groups/{gid}').remove()`.
3. **Cloud Storage** — `bucket.getFiles({prefix: 'groups/{gid}/'})` then `Promise.all(file.delete().catch(() => {}))` (best-effort — swallows individual file failures).

Logs `Cleaned up group {gid}: subcollections, RTDB, and Storage` on success. The client's `FirestoreService.deleteGroup` only deletes the group doc itself plus its own member record (per the 2026-04-12 dissolve-bug fix) — this Function does the rest via the Admin SDK (which bypasses security rules).

#### 28.7.2 `onSyncDataWrite` — Firestore onWrite

```
functions.firestore.document('groups/{groupId}/{collection}/{docId}').onWrite(...)
```

Fires on every write to **any** subcollection of any group. Filters in-function:

1. `if (!SYNC_PUSH_COLLECTIONS.has(collection)) return;` — only the 8 sync data collections (`transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger, sharedSettings`) trigger fan-out.
2. `if (!change.after.exists) return;` — skip deletes (tombstone has `deleted: true` and is propagated by the listener path; the Function would otherwise double-fire).
3. Read `lastEditBy` (fallback `deviceId`) — the writer to exclude from fan-out.
4. `collectRecipientTokens(gid, writerDeviceId)` (§28.7.6) → token list.
5. `sendFcm(tokens, {type: "sync_push", collection, groupId}, "sync_push")`.

Defense-in-depth: `collectRecipientTokens` also re-validates the writer (§28.7.6). If the writer isn't a current member of the group, the fan-out is suppressed entirely so a security-rule regression that allowed a non-member write cannot escalate into group-wide FCM spam.

Client receivers: `FcmService.handleWakeForSync` → `BackgroundSyncWorker.runFullSyncInline("FCM-sync_push")`. With `enqueueUniqueWork(KEEP)` on the client, bursts of writes (e.g. a 500-row CSV import) collapse to a single sync run per peer.

#### 28.7.3 `onImageLedgerWrite` — Firestore onWrite

```
functions.firestore.document('groups/{groupId}/imageLedger/{receiptId}').onWrite(...)
```

`imageLedger` is intentionally **not** in `SYNC_PUSH_COLLECTIONS` because most of its writes are bookkeeping chatter (`markPossession`, `markNonPossession`, `pruneCheckTransaction` deletions) that peers don't need to react to. This trigger applies a content filter so only meaningful writes fan out:

| Condition | Fires |
|---|---|
| `before == null && after.uploadedAt == 0` | **Recovery request** — peers with the file should consider re-uploading |
| `before.uploadedAt == 0 && after.uploadedAt > 0` | **Recovery re-upload complete** — peers should pull the new file |
| `after.contentVersion > before.contentVersion` | **Rotation** — peers' cached file is stale |

Skipped:
- Fresh `createLedgerEntry` (a brand-new receipt with no prior version) — already covered by the concurrent `onSyncDataWrite` push on the `transactions` collection (peer's `onBatchChanged` fast-path downloads the photo).
- Possession updates — informational; UI updates from the next periodic sync.
- Transaction-driven deletions — propagated via the `transactions` collection.
- The special `_snapshot_request` doc — uses its own signaling path.

Writer is excluded via `lastEditBy` (fallback `originatorDeviceId`). Client behavior matches §28.7.2.

#### 28.7.4 `presenceHeartbeat` — scheduled

```
functions.pubsub.schedule('every 15 minutes').timeZone('UTC').onRun(...)
```

Walks all groups in Firestore. For each, reads `groups/{gid}/presence` from RTDB, collects `deviceId`s with `lastSeen < now - HEARTBEAT_STALE_MS` (15 min), looks up their FCM tokens via `tokensForDevices`, and sends `{type: "heartbeat", groupId}`.

Purpose: backstop for devices that Android has dropped into the App-Standby `rare` or `restricted` bucket so their periodic worker has stopped firing. The 2026-04-12 sync_diag dump showed 4h46m of worker silence on one device — `presenceHeartbeat` closes that gap by waking devices server-side every 15 min.

**Scale caveat:** sequential walk. At ~50 ms/group the 60 s default function timeout is hit at ~1.2 K groups; the 9 min Gen-1 ceiling at ~10 K. Tracked for migration to an indexed presence query in `memory/project_prelaunch_todo.md` #7. Acceptable at current scale.

#### 28.7.5 `presenceOrphanCleanup` — scheduled

```
functions.pubsub.schedule('every sunday 03:00').timeZone('UTC').onRun(...)
```

Mitigation for the RTDB `.write: auth != null` gap (§28.4.4). Every Sunday 03:00 UTC:

1. List all groups in Firestore.
2. For each group, read `groups/{gid}/presence` from RTDB.
3. Bulk-fetch corresponding `groups/{gid}/devices/{deviceId}` from Firestore via `db.getAll(...refs)` — N reads at most for N presence entries.
4. For each presence entry whose Firestore device doc is missing or has `removed === true`, queue a `rtdb.ref('groups/{gid}/presence/{deviceId}').remove()`.
5. `Promise.all` the removals.

Logs `presenceOrphanCleanup: checked {N} group(s), pruned {M} orphan presence entrie(s)`. Same O(n) sequential scaling concern as `presenceHeartbeat`.

Strict replacement for membership-enforcing rules would require either App Check enforcement on RTDB writes (already on, but doesn't substitute for membership) or a server-mediated write path. For now, fresh orphans live up to a week between sweeps.

#### 28.7.6 Shared helpers

`collectRecipientTokens(gid, writerDeviceId)`:
1. Read `groups/{gid}/devices` (single subcollection get).
2. **Writer-membership validation** (defense in depth — option A, free because we already read the snapshot): if `writerDeviceId` doesn't appear in the snapshot OR has `removed === true`, log `Fan-out suppressed: writer {id} is not a current member of {gid}` and return `[]`. Catches a security-rule regression that lets a non-member write slip through; suppresses the FCM amplification.
3. For every other device with a non-empty `fcmToken` and `removed !== true`, collect the token.

`tokensForDevices(gid, deviceIds[])` — per-device lookup variant (used by `presenceHeartbeat`). Skips missing docs, `removed`, empty `fcmToken`.

`sendFcm(tokens, data, label)` — chunks at 500 tokens per `sendEachForMulticast`, Android `priority = "high"`, logs per-batch failure counts. Stringifies all data values (FCM data fields must be strings).

### 28.8 Firebase Cloud Messaging (FCM)

FCM is the wake transport. The server sends data-only messages with `priority: high` so they bypass Doze and App-Standby.

#### 28.8.1 Token lifecycle

`FcmService.onNewToken(token)` saves the new token to `fcm_prefs` SharedPreferences:
```
fcm_prefs:
  fcm_token            : String
  token_needs_upload   : Boolean
```

On the next successful Firestore-authenticated sync run, `FirestoreService.storeFcmToken(gid, deviceId, token)` writes the token to `groups/{gid}/devices/{deviceId}.fcmToken` (merge). The flag is cleared after a successful upload.

Tokens rotate on app reinstall, app-data clear, and Google Play services updates. The Cloud Function fan-out paths handle stale tokens via the per-batch failure log (`sendEachForMulticast` returns per-token results) — failed sends don't retry, but the next valid token write replaces the stale one.

#### 28.8.2 Message types

All wakes are data-only messages (`message.data`, never `message.notification`). The `type` field disambiguates routing:

| Type | Source | Effect on receiver |
|---|---|---|
| `sync_push` | `onSyncDataWrite`, `onImageLedgerWrite` | `runFullSyncInline("FCM-sync_push")` — Tier 2 if VM alive, Tier 3 inline + budget if dead |
| `heartbeat` | `presenceHeartbeat` (15-min cron) | Same as `sync_push`, sourceLabel `"FCM-heartbeat"` |
| `debug_request` | Manual server trigger (admin uses Cloud Console or `gcloud` to send to a specific FCM token) | Debug builds only — `runDebugDumpInline()` builds a diag dump + uploads via Storage |

`sync_push` and `heartbeat` are functionally equivalent in client routing — they both wake the sync pipeline. The distinction is for diagnostics: `heartbeat` indicates a server-detected stale-presence wake; `sync_push` indicates real data has changed. Slim-path eligibility on Tier 3 differs: `heartbeat` slims when there's been a recent inline sync; `sync_push` never slims (signals real data to fetch).

Additional fields in `sync_push`:
- `collection : String` — which collection was written (e.g. `"transactions"`)
- `groupId : String` — for cross-membership debugging.

`heartbeat` carries only `groupId`. `debug_request` is empty.

#### 28.8.3 Client routing (`FcmService.kt`)

```
override fun onMessageReceived(message: RemoteMessage) {
    val type = message.data["type"] ?: return
    when (type) {
        "debug_request" -> handleDebugRequest()
        "sync_push", "heartbeat" -> handleWakeForSync(type)
    }
}
```

`handleWakeForSync` branches on VM lifecycle:

- **VM alive (Tier 2)** — launches on `BudgeTrakApplication.processScope` and returns from the FCM thread immediately. The ViewModel's existence keeps the process alive past `onMessageReceived` returning, so long Tier 2 work (snapshot building, multi-photo upload) completes naturally. No time budget. No WM fallback needed.

- **VM dead (Tier 3)** — `runBlocking { runFullSyncInline(ctx, "FCM-$type", INLINE_BUDGET_MS) }`. The blocking is load-bearing — it's the only thing keeping the process alive while WorkManager and the inline body do their work. `INLINE_BUDGET_MS = 8500`, leaving 1.5 s of the FCM 10 s service window for service teardown. WM `runOnce` fallback fires if the inline body returns `false` (timeout / offline / failed).

`debug_request` follows the same VM-alive vs dead branch, with `runDebugDumpInline` instead of `runFullSyncInline`.

### 28.9 Crashlytics

#### 28.9.1 Custom keys

Stamped by `BudgeTrakApplication.onCreate` and `BudgeTrakApplication.updateDiagKeys(map)` (which `MainViewModel.runPeriodicMaintenance` calls daily for sync users). All attached to every future crash and non-fatal.

| Key | Source | Purpose |
|---|---|---|
| `buildTime` | `BuildConfig.BUILD_TIME` (set in `app/build.gradle.kts` from `SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()) + " UTC"`) | Identify which build a crash came from when many devices in the wild are on older APKs. Filter via `query-crashlytics.js --build <prefix>`. |
| `versionCode` | `BuildConfig.VERSION_CODE` | Monotonic version filter. |
| `lastTokenExpiry` | `addAppCheckListener` callback | Token freshness. |
| `authAnonymous` | `addAuthStateListener` callback | True/false — should always be true. |
| `cashDigest` | `runPeriodicMaintenance` | Hex digest of `availableCash`, for cross-device consistency analysis. |
| `listenerStatus` | `runPeriodicMaintenance` | Aggregated state of all 8 Firestore listeners. |
| `lastRefreshDate` | `runPeriodicMaintenance` | When did this device last successfully sync? |
| `activeDevices` | `runPeriodicMaintenance` | Device count — sanity check for "should this group still exist?" |
| `txnCount`, `reCount`, `plCount` | `runPeriodicMaintenance` | Inventory snapshot. |
| `isSyncConfigured`, `isSyncAdmin`, `syncGroupId` | `MainViewModel` updates | Sync surface state for crash triage. |

#### 28.9.2 Non-fatal triggers

`BudgeTrakApplication.recordNonFatal(tag, message, exception?)` records a `RuntimeException` with a custom tag and message. Sites:

| Tag | Where | Trigger |
|---|---|---|
| `PERMISSION_DENIED` | `FirestoreDocSync` listener-error path | Any sustained Firestore PERMISSION_DENIED |
| `CONSISTENCY_COUNT_MISMATCH` | `MainViewModel.runConsistencyCheck` | Layer 1 active-doc-count divergence |
| `CONSISTENCY_HASH_MISMATCH` | `MainViewModel.runConsistencyCheck` | Layer 2 cash-hash divergence (after 1 h confirmation gate) |
| `TOKEN_REFRESH_TIMEOUT` | `BackgroundSyncWorker` Tier 2/3, ViewModel keep-alive | App Check token refresh exceeded 15 s |
| `OCR_PIPELINE_FAILURE` | `ReceiptOcrService.kt:202` | Any OCR exception (caught at runtime, recorded via `recordException(e)` before returning `Result.failure`) |

Non-fatals are rate-limited to 10/session by Crashlytics (an SDK-imposed limit). Going over 10 silently drops. `health_beacon` was migrated to Firebase Analytics specifically to avoid burning the cap.

#### 28.9.3 Opt-out

SharedPref `app_prefs:crashlyticsEnabled` (default `true`). Read in `BudgeTrakApplication.onCreate` **before** any Firebase service call, then applied via `setCrashlyticsCollectionEnabled` and `setAnalyticsCollectionEnabled` (one toggle controls both). UI label in Settings: "Send crash reports and anonymous usage data."

### 28.10 Firebase Analytics

`data/telemetry/AnalyticsEvents.kt`. Same opt-out toggle as Crashlytics. Events are silently no-op'd when the toggle is off — `isEnabled(ctx)` short-circuits before `Firebase.analytics.logEvent`.

#### 28.10.1 Event schema

**`ocr_feedback`** — fires on save of an OCR-populated transaction. Anonymous deltas/booleans only — no merchant text or amounts. Measures per-field user-correction rate so OCR quality regressions are observable.

```
merchant_changed   : Boolean   # User changed the merchant string
date_changed       : Boolean   # User changed the date
amount_delta_cents : Long      # finalCents - ocrCents (signed)
cats_added         : Long      # Count of category IDs added vs OCR
cats_removed       : Long      # Count of category IDs removed vs OCR
had_multi_cat      : Boolean   # OCR returned ≥ 2 categoryAmounts
```

**`health_beacon`** — daily sync-user heartbeat from `runPeriodicMaintenance` (24 h gate). Migrated from a Crashlytics non-fatal so it doesn't burn the 10/session cap.

```
listener_up    : Boolean  # All 8 Firestore listeners healthy
active_devices : Long
txn_count      : Long
re_count       : Long
pl_count       : Long
```

#### 28.10.2 GA4 linkage

The Firebase project must be linked to a GA4 property, or events are silently dropped. Events were dropped on `com.techadvantage.budgetrak` from 2026-04-22 (when SDK calls were added) to 2026-04-26 (when the GA4 link was created) for exactly this reason — fix was at Firebase Console → Integrations → Google Analytics → Link → property `534603748`, stream `14591145419`.

Firebase BoM 32.x autodiscovers the measurement ID at runtime via `mobilesdk_app_id` — `google-services.json` doesn't need an explicit `analytics_service` block. Confirmed working without it.

#### 28.10.3 BigQuery export

Enabled at Firebase Console → Project Settings → Integrations → BigQuery for the `analytics_534603748` dataset on 2026-04-26 alongside Crashlytics streaming. Daily + Streaming. No advertising IDs. See §28.11 for query patterns.

### 28.11 BigQuery

#### 28.11.1 Datasets and tables

| Dataset | Tables | Purpose |
|---|---|---|
| `firebase_crashlytics` | `com_securesync_app_ANDROID` (batch, daily) | Legacy Crashlytics — receives data through 2026-04-12 (rebrand date) |
| | `com_securesync_app_ANDROID_REALTIME` (stream) | Legacy Crashlytics realtime — same cutoff |
| | `com_techadvantage_budgetrak_ANDROID` (batch) | Rebranded Crashlytics — receives data from 2026-04-26 |
| | `com_techadvantage_budgetrak_ANDROID_REALTIME` (stream) | Rebranded Crashlytics realtime — same start |
| `analytics_534603748` | `events_YYYYMMDD` (per-day batch tables) | Firebase Analytics events (`ocr_feedback`, `health_beacon`) |
| | `events_intraday_YYYYMMDD` | Today's events (until midnight rollover) |

All in BigQuery project `sync-23ce9` (no separate analytics project).

The legacy `com_securesync_app_*` tables don't get renamed when the Android applicationId changes — Firebase keeps existing export tables intact. Both legacy and rebranded must be `UNION ALL`'d for historical lookups spanning 2026-04-12 to 2026-04-26.

#### 28.11.2 Service account

Auth via `~/.config/budgetrak/sa-key.json` (a JSON service-account key). IAM roles required, both project-scoped on `sync-23ce9`:
- `roles/bigquery.jobUser` — run queries
- `roles/bigquery.dataViewer` — read data

Key creation may need a Workspace org-policy override if `iam.disableServiceAccountKeyCreation` (legacy constraint) is set. Check at IAM & Admin → Organization Policies for the parent org.

The query helper falls back to `~/.config/configstore/firebase-tools.json` (Firebase CLI refresh token) if the SA key is missing. The fallback is RAPT-limited (re-auth every 3–7 d for Workspace accounts), which is the reason we switched to a SA — see `memory/reference_bigquery_service_account.md`.

#### 28.11.3 Query helper

`tools/query-crashlytics.js`. Modes:
```
node tools/query-crashlytics.js                  # last 24 h events
node tools/query-crashlytics.js --days 7         # window
node tools/query-crashlytics.js --crashes        # fatals only
node tools/query-crashlytics.js --nonfatals      # PERMISSION_DENIED + others
node tools/query-crashlytics.js --keys           # raw custom_keys arrays
node tools/query-crashlytics.js --analytics      # ocr_feedback + health_beacon
node tools/query-crashlytics.js --list-devices   # distinct device fingerprints (7 d)
node tools/query-crashlytics.js --build 2026-05  # filter to one build via STARTS_WITH(buildTime, prefix)
node tools/query-crashlytics.js --query "SQL"    # custom SQL
```

The `--build` filter unwraps `custom_keys` from its `ARRAY<STRUCT<key,value>>` shape:
```sql
WHERE EXISTS (
  SELECT 1 FROM UNNEST(custom_keys) ck
  WHERE ck.key = 'buildTime' AND STARTS_WITH(ck.value, '<prefix>')
)
```

Distinct device-fingerprint rollup (`--list-devices`):
```sql
SELECT DISTINCT
  application.display_version AS app_version,
  device.model AS device_model,
  installation_uuid,
  (SELECT value FROM UNNEST(custom_keys) ck WHERE ck.key = 'buildTime') AS build_time,
  COUNT(*) AS events,
  MAX(event_timestamp) AS last_event
FROM `sync-23ce9.firebase_crashlytics.com_techadvantage_budgetrak_ANDROID_REALTIME`
WHERE event_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
GROUP BY 1, 2, 3, 4
ORDER BY last_event DESC
```

`installation_uuid` is stable across upgrades and changes only on uninstall + reinstall. Combined with `device.model` and `buildTime`, it uniquely fingerprints a build-on-device.

#### 28.11.4 Cost

Free tier covers BigQuery storage and queries below 1 TiB/month. At BudgeTrak's volume (current beta + foreseeable launch), no real cost. The Crashlytics + Analytics streaming export itself is free on Blaze.

### 28.12 Gemini API (external)

Two app features call Gemini directly: AI CSV categorization (`AiCategorizerService`) and AI Receipt OCR (`ReceiptOcrService`). Both use the **public Generative AI API** (not Vertex AI), addressed via `https://generativelanguage.googleapis.com` by the `com.google.ai.client.generativeai` SDK.

#### 28.12.1 Model selection

Both services use `gemini-2.5-flash-lite`. Selected for the cost-per-token / latency / accuracy sweet spot: Flash-Lite is ~30% the cost of Flash, runs receipt-OCR calls in 600–900 ms per call, and at `temperature = 0` is deterministic enough to be reliable.

#### 28.12.2 API key sourcing

Key lives in `local.properties` (gitignored) under `GEMINI_API_KEY`. `app/build.gradle.kts` reads the property and emits it as `BuildConfig.GEMINI_API_KEY`. The services check `BuildConfig.GEMINI_API_KEY.isBlank()` and return `Result.failure(IllegalStateException("GEMINI_API_KEY missing"))` on missing key. There is no runtime key rotation; ship-time injection only. Rotating requires a rebuild.

For a fresh dev environment: Google AI Studio → Get API Key → paste into `local.properties` as `GEMINI_API_KEY=…`.

#### 28.12.3 Request shapes

**AI categorization** (`AiCategorizerService`): single text-only call per chunk of ≤ 100 transactions. Schema-constrained JSON output — the SDK enforces `responseMimeType = "application/json"` + a `Schema.obj(...)` that returns a list of `{index, categoryId}` records. Prompt includes the known category list (id + name only, no merchant/amount training data leaks). One model instance, lazily initialized. Retries on transient errors (`503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket`); silent fallback to the on-device heuristic on hard failure.

**Receipt OCR** (`ReceiptOcrService`): three or four sequential / parallel calls per receipt:

```
Call 1   (image → extract):       merchant, date, amountCents, itemNames[], topChoice, multiCategoryLikely, focusedTranscript
Call 1.5 (text → reconcile):      re-read date + amountCents from focusedTranscript (no image — cheap)
Call 2   (image + items[] → categorize): items[{description, categoryId, ...}]
Call 3   (image + item-list → prices):    priceCents per item, only when multi-category
```

- **Single-cat path** (`preSelect.isEmpty()`, `multiCategoryLikely == false`): 3 calls — Call 1, then Call 1.5 ‖ Call 2 in parallel.
- **Multi-cat path** (`preSelect.size >= 2` or `multiCategoryLikely == true`): 4 calls — Call 1, Call 1.5 ‖ Call 2 in parallel, then Call 3.

Why the split: Call 1 produces stable item-name text; Call 2 reasons over text (cheaper, more accurate) with the image as backup. Reconciliation lives in Call 1.5 (text-only) because Flash-Lite still produces single-digit OCR errors at temperature 0; running a text-only sanity pass over the focused transcript catches them.

**Image bytes are passed via `content { blob("image/jpeg", bytes) }`, not `image(bitmap)`.** The SDK's `image(bitmap)` path re-encodes every Bitmap at JPEG quality 80 before sending (`encodeBitmapToBase64Png` in the SDK), which silently degrades the q=92+ receipt JPEGs we store. `blob()` bypasses the re-encode.

Failure handling: any uncaught exception is caught at the service boundary, recorded via `FirebaseCrashlytics.recordException(e)` with tag `OCR_PIPELINE_FAILURE`, and returned as `Result.failure`. The UI shows a toast and lets the user fall back to manual entry.

#### 28.12.4 Quotas + limits

Free tier: 15 RPM, 1.5 K RPD, 1M TPM (per the public limits at writing). At the user's current beta volume this is fine; production launch may need a billing-enabled key with paid tier. Monitor via the Google Cloud Console → APIs & Services → Generative Language API → Quotas. Server-side quota errors surface as `429 RESOURCE_EXHAUSTED` and are retried by the categorization service; OCR fails immediately with a non-fatal.

### 28.13 Cost & Scale Snapshot

Estimates from 2026-04 (40 K groups, 100 K devices, 5 sessions/day, 2 changes/session). Actual current usage is well below.

| Component | Monthly | Notes |
|---|---|---|
| Filtered listeners with cursors | ~$27 | Per-collection `whereGreaterThan(updatedAt, cursor)` |
| Reinstall full reads | ~$135 | One-time per install |
| Firestore writes | ~$54 | Per-field encrypted docs |
| Firestore storage | ~$81 | |
| Cloud Storage (photos) | ~$86 | Receipts + snapshot archives |
| Startup health-check reads | ~$18 | `Source.CACHE` everywhere — minimal egress |
| RTDB presence | ~$0 | Free tier |
| Integrity check | ~$0 | `Source.CACHE` |
| Cloud Functions | ~$5 | 5 functions; `presenceHeartbeat` dominates at ~3 K invocations/day |
| BigQuery export + queries | ~$0 | Free tier |
| FCM | ~$0 | Free |
| Gemini API | tier-dependent | Paid users only; rates above |
| **Total (legacy components)** | **~$401** | Pre-optimization v2.3 estimate was ~$50,643 — 99.2% reduction from cursor + cache strategies. |

**Scaling concerns**:
- `presenceHeartbeat` and `presenceOrphanCleanup` are sequential O(n) walks. Migration to indexed presence queries is in `memory/project_prelaunch_todo.md` #7. Hard ceiling ~10 K groups under Gen-1 9-min limit.
- Cloud Functions Gen-2 migration would eliminate the 9-min ceiling but requires Cloud Run + Eventarc + IAM setup not currently configured. v1 is sufficient at projected scale.

---

## 29. Document Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 – 2.4 | Feb–Mar 2026 | Initial release; CRDT → Firestore-native sync migration; per-field encryption; receipt system; widget; SecureSync → BudgeTrak rename; `BackgroundSyncWorker` 15-min periodic; `MainViewModel` extracted from `MainActivity`; per-collection `updatedAt` cursors + `awaitInitialSync()`; RTDB presence; `_clock` fields removed app-wide. |
| 2.5 / 2.5.x | Apr 2026 | Async IO-thread load with LoadingScreen gate; Back = Home; synchronous `recomputeCash()`; consolidated `runPeriodicMaintenance()` (24-h gate); three-tier `BackgroundSyncWorker`; transaction archiving; SYNC rebrand; two-layer consistency check; echo suppression; App Check proactive refresh; Crashlytics observability; admin-claim voting; ranked match dialogs; SAF backup directory picker. |
| 2.6 / 2.6.x | Apr 12–13 2026 | Rebrand to `com.techadvantage.budgetrak` under Tech Advantage LLC. Full memory + doc audit. FCM wake architecture: Cloud Functions `onSyncDataWrite` + `presenceHeartbeat` (closes the 4h46m worker-silence gap on Samsung). Bidirectional scroll affordance (`PulsingScrollArrows` plural + `ScrollableDropdownContent`). |
| 2.7 | Apr 18 2026 | **AI features + photo-bar UX overhaul.** AI Receipt OCR (Subscriber, explicit-tap sparkle icon, Gemini 2.5 Flash-Lite 3-call pipeline + Call 1 routing probe); AI CSV Categorization (Paid+Sub, opt-in). Photo-bar long-press + drag reorder with real-time reshuffle. Photo-pipeline hardening (dedupe, 400 px floor, PDF via `PdfRenderer`, queue-on-save). Worker `AtomicBoolean isRunning` double-fire guard. Cash Flow Simulation widened to Paid+Subscriber. |
| 2.7.1 | Apr 27 2026 | **Transaction save audit — six silent-loss vectors closed:** non-dismissable `DuplicateResolutionDialog`; `onResume` add-only disk merge; entity-id range widened to `1..Int.MAX_VALUE`; multi-category validation toasts on silent returns; `onUpdateTransaction` toasts on missing edit target; `addTransactionWithBudgetEffect` made atomic. |
| 2.8 | Apr 27 2026 | **TransactionDialog unification + network-awareness pass.** Three add/edit entry points consolidated into one dialog with EXPENSE/INCOME pill toggle. Single layered Add-Transaction icon replaces +/- IconButton pairs. OCR refund-receipt support (negative `amountCents` auto-flips type + `abs()`). Preselect-help banner opens as a sibling-`Dialog` overlay so the underlying transaction dialog survives the round-trip. Fail-fast offline + auto-resume across receipt sync / AI OCR / Sync Now / `BackgroundSyncWorker` / App Check / anonymous auth via `networkCallback.onAvailable`. Tier 2 receipt-sync rethrows `CancellationException` and propagates `clearLostReceiptSlot` state back to `vm.transactions`. Debug receipt forensics in `token_log.txt` + `sync_diag.txt` Receipt Files Audit. Call 1.5 capped at 2 s past Call 2. ~100 files / ~51,500 lines. Dead code removed: down-only `PulsingScrollArrow`; `DeviceRecord.fingerprintData/fingerprintSyncVersion` (written but never read); `updateDeviceMetadata.fingerprintJson` parameter (no caller passed it). |
| 2.8 (post-fix) | May 1 2026 | **Backend-infrastructure consolidation.** Added Chapter 28 covering Firebase project, Auth, Firestore (rules + schema verbatim), RTDB (rules + schema verbatim), Cloud Storage (rules verbatim), App Check (provider config + Console TTL per provider + Play Integrity advanced settings + refresh triggers), Cloud Functions (all 5 documented with rationale), FCM (token lifecycle + message types + client routing), Crashlytics (custom keys + non-fatal triggers + opt-out), Firebase Analytics (event schema + GA4 linkage + BigQuery export), BigQuery (datasets + SA auth + query helper), Gemini API (model + key sourcing + request shapes), and cost snapshot. Designed for project reconstructability if all source were lost. **`scheduleNextBoundary` fatal-crash fix** (illegal `setExpedited` + `setInitialDelay` combo on API 31+ — fired hourly via `widget_info.xml` `updatePeriodMillis="3600000"` for solo users). **Crashlytics `buildTime` + `versionCode` custom keys** stamped in `BudgeTrakApplication.onCreate` for post-publish per-build filtering via `query-crashlytics.js --build <prefix>`. `--list-devices` flag added. |
| 2.9 | May 1 2026 | **First Play Store release** (versionCode 7). GitHub Actions CI workflow (`.github/workflows/release.yml`) for Play-bound AAB builds — Termux can't compile against android-35 (its aapt2 v2.19 doesn't load the platform jar; no Linux-ARM aapt2 ships from Google), so release builds move to `ubuntu-latest` runners. Termux still owns debug builds. Aapt2 override moved out of repo `gradle.properties` into user-scoped `~/.gradle/gradle.properties`. Workflow supports `workflow_dispatch` with optional Play auto-upload via `r0adkll/upload-google-play`; release notes auto-pulled from `whatsNew/whatsnew-en-US` and `whatsnew-es-419`. Spanish strings audit confirmed `es-419` (LATAM) variant. |
| 2.9.1 | May 1 2026 | **Restore-flow UI refresh fix** (versionCode 8). `MainViewModel.reloadAllFromDisk` now wraps all Compose state mutations (the seven `mutableStateListOf` lists + `sharedSettings` + 19 prefs-backed scalars + `lastSaved*` cache resets) in `Snapshot.withMutableSnapshot { … }`. Without that, the multi-step `clear()`+`addAll()` sequence on each list was producing UI symptoms post-restore where Categories settings, Recurring Expenses, and Income Sources screens rendered as empty (or seeded defaults) until the user force-killed the app and relaunched, even though both on-disk JSON files and in-memory VM lists were correct (confirmed via state dump). Transactions happened to render only because of follow-up state mutations elsewhere that nudged recomposition. Also resets the `lastSaved*` diff caches inside the same snapshot so the next save after a restore doesn't push every record as "changed" against pre-restore state. `DiagDumpBuilder` now also prints Income Sources, Savings Goals, and Amortization Entries sections (previously omitted from dumps). |
| 2.10.00 | May 1 2026 | **Encrypted-restore stale-VM fix + Restore dialog UX overhaul + version-format change** (versionCode 15). Root cause of the post-restore empty-UI bug identified: `BackupManager.restoreSystemBackup` writes JSON files to disk but never refreshes VM state. The encrypted-restore handler in `MainActivity.kt:1540` called `Activity.recreate()` to "force refresh" — but `recreate()` preserves the `ViewModelStore` by design, so the rebuilt UI continued reading pre-restore in-memory lists. Fixed by replacing `recreate()` with `vm.reloadAllFromDisk()` and adding a `dataReloadVersion` `mutableIntStateOf` that's bumped inside the `Snapshot.withMutableSnapshot` block. `MainActivity.setContent` wraps the `when (vm.currentScreen) { … }` block in `key(vm.dataReloadVersion) { … }`, forcing the screen subtree to unmount + remount on every wholesale reload — bypasses any Compose smart-skipping or stale `derivedStateOf` cache through the `clear()`+`addAll()` transition. **Restore dialog**: auto-launches the SAF folder picker when no backups visible and no usable persisted URI; persists picked tree URI to `app_prefs.backup_folder_uri` so subsequent restore attempts in the same install skip the picker; strict folder-name validation (must be literally `backups`) catches the realistic mistake of picking `Download/` as parent (SAF tree-URI listing only enumerates immediate children, so subfolder backups would be invisible AND future auto-backups would diverge from the read folder); content validation (rejects picks with no `backup_*_system.enc` files); `PulsingScrollArrows` overlay matching other dialog screens. New strings `pickBackupFolderMessage` / `pickedFolderHasNoBackups` / `pickedFolderNotBackups` (en + es-419) replace the `noBackupsFound` wording. **Version format change**: `versionName` now follows `MAJOR.MINOR.PP` with zero-padded `00-99` patch segment for extensive debug-cycle iteration (100 slots per minor before bumping minor). **Release-build subscription override** (TEMPORARY, until Google Play Billing integration ships): `MainViewModel.init` forces `isPaidUser = isSubscriber = true` on `!BuildConfig.DEBUG`, persisting both to prefs every launch, so internal/closed/open testers can exercise paid-only features without a billing flow. Debug builds keep prefs-toggleable state for development-time tier exercise. New feedback memory `feedback_recreate_preserves_viewmodel.md` documents the `recreate()` ↔ `ViewModelStore` interaction. |
| 2.10.01 | May 2 2026 | **Data Safety hygiene + native debug symbols** (versionCode 16). SYNC group setup field renamed from "Your name" / "Tu nombre" to "Device nickname" / "Apodo del dispositivo" (the `enterNickname` string in `EnglishStrings` / `SpanishStrings`, used by Create-group + Join-group + repair flows in `SyncScreen.kt`). The previous wording implied BudgeTrak collects personal name data, which conflicted with the Data Safety declaration that we don't. Repair Attributions dialog already used "Nickname" — this aligns SYNC group setup with that precedent. `TranslationContext` updated to document the device-labeling intent so translators don't drift the field back toward personal-name semantics. **Native debug symbols** now embedded in release AABs via `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` in the `release` buildType — fixes the Play Console "AAB contains native code, and you've not uploaded debug symbols" warning, gives Crashlytics native-frame symbolication for bundled libs (Firebase, Compose runtime). Privacy policy at `https://techadvantagesupport.github.io/privacy` updated with explicit `## Data Deletion` section enumerating five deletion paths so Play Console Data Safety review (Yes-with-deletion-URL answer) lands on a clearly findable section via `#data-deletion` anchor. |
| 2.10.02 | May 2 2026 | **Phantom-group-state PERMISSION_DENIED loop fix** (versionCode 17). `onCreateGroup` / `onJoinGroup` catch handlers (`MainActivity.kt`) now record a `GROUP_CREATE_FAILED` / `GROUP_JOIN_FAILED` non-fatal and **fully roll back** local state — `vm.disposeSyncListeners()`, `GroupManager.leaveGroup(localOnly = true)`, `vm.resetSyncState()` — instead of silently leaving `groupId` set in prefs while the Firestore group doc is missing (the v2.10.01 behavior that produced Paul's PERMISSION_DENIED loop on 2026-05-01). New strings `createGroupFailed` / `joinGroupFailed` (en + es-419). **Dissolution detection in `FirestoreDocSync.triggerFullRestart()`**: on PERMISSION_DENIED, before refreshing the App Check token, probes `groups/{groupId}` and `groups/{groupId}/members/{authUid}` via `Source.SERVER` (10 s timeout each); if either returns "doesn't exist", fires the new `onGroupDissolved` callback wired in `MainViewModel.configureSyncGroup`, which dispatches `evictFromSync(strings.sync.evictionDissolved)`. Probe-time errors fall through to the standard token-refresh path so transient outages don't trigger spurious eviction. Together: any path that writes `groupId` to local prefs rolls it back atomically on Firestore failure, and any orphaned `groupId` left over from older bugs gets cleaned up the next time it surfaces a PERMISSION_DENIED. |
| 2.10.03 | May 2 2026 | **Public-download write hardening + pinned debug token** (versionCode 18). New `data/PublicDownloadWriter.kt` — three-tier orphan-safe writer (cached path → canonical direct → MediaStore fallback with `(N)` auto-suffix). Refactor: `DiagDumpBuilder.writeDiagToMediaStore` (used by Dump button + DebugDumpWorker FCM path), `FullBackupSerializer.applyRestore` (pre-restore snapshot — production users hit this on restore-after-reinstall), `ExpenseReportGenerator.generateSingleReport` (PDF expense reports — production users with backup-restored transactions reusing orphan filenames). Other public-download writes (encrypted backups via `nextAvailableSuffix`, SAF-mediated CSV/XLSX/JSON, photo dumps in fresh timestamped subdirs, append-mode debug logs in swallow-EACCES try/catch) don't need the helper; full survey in §9.7. **Pinned App Check debug token**: `local.properties:APP_CHECK_DEBUG_TOKEN` → `BuildConfig.APP_CHECK_DEBUG_TOKEN` → seeded into `com.google.firebase.appcheck.debug.store.<persistenceKey>` SharedPreferences in `BudgeTrakApplication.onCreate` before `installAppCheckProviderFactory`. One Console-registered UUID covers every dev/test device — no Console roundtrip on reinstall. Skip-if-already-equal so cold starts don't churn the prefs. Empty BuildConfig value disables the seed (SDK falls back to per-install UUIDs; logcat scrape still surfaces them to `token_log.txt`). Detail: §28.6.1. |
| 2.10.04 | May 3 2026 | **Restore-list merge fix + first end-to-end CI publish + side-by-side debug install + Play Integrity setup post-mortem.** (versionCode 19, first AAB published via CI auto-publish to Play Console internal testing track.) Restore dialog (`MainActivity.kt`) now merges the SAF tree-URI listing with `BackupManager.listAvailableBackups()` (was choosing one or the other); fixes orphan `.enc` files dropping off the restore list as soon as the user creates their first own auto-backup. CI workflow (`.github/workflows/release.yml`) gains a `release_status` workflow_dispatch input (default `draft` while the app is in Google's "Draft app" state pre-12/14-closed-test gate; flip to `completed` once production access is granted). New `whatsNew/whatsnew-en-US` + `whatsnew-es-419` notes refreshed for v2.10.04 and trimmed under the 500-char Play API limit (es-419 was 555). Side-by-side debug install: debug buildType gets `applicationIdSuffix = ".debug"` + `versionNameSuffix = "-debug"` so debug-keystore-signed sideloads coexist with the Play-Store-signed release on the same device; second Firebase Android app entry registered for `com.techadvantage.budgetrak.debug`; pinned debug token UUID registered under both app entries' Manage-debug-tokens lists; `app/google-services.json` carries both packages. **Play Integrity setup post-mortem (§28.6.5):** documented the four mandatory Console steps after a six-hour debug session in which release-build group creation kept failing with `PERMISSION_DENIED` while debug builds worked fine. Root cause: the Play app signing key SHA-256 wasn't registered in Firebase Project settings; only the upload-key SHA-1 was. Play strips the upload signature and re-signs the installable APK with its own key, so the runtime fingerprint Play Integrity attests is different from the upload-key fingerprint, and Firebase's App Check verifier silently rejects every token. `firebase-config-reference.txt` §5b rewritten with the verified-working configuration. |

---

BudgeTrak SSD v2.8 — April 2026 — END OF DOCUMENT

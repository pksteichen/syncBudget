# BudgeTrak — Low-Level Design Document

**Application:** BudgeTrak  **Package / applicationId:** `com.techadvantage.budgetrak`
**Vendor:** Tech Advantage LLC.  **Platform:** Android (minSdk 28, targetSdk 34)
**Framework:** Jetpack Compose, Material Design 3  **Language:** Kotlin
**Document Version:** 2.8 (in development, April 2026)
**Source:** ~100 Kotlin files, ~51,500 lines (refreshed at release tags)

> Per-release diff in §15.

## Table of Contents

1. [Introduction](#1-introduction)
2. [Application Classes](#2-application-classes)
   - 2.1 [BudgeTrakApplication](#21-budgetrakapplication)
   - 2.2 [MainActivity](#22-mainactivity)
   - 2.3 [MainViewModel](#23-mainviewmodel)
   - 2.4 [MainScreen](#24-mainscreen)
   - 2.5 [TransactionsScreen](#25-transactionsscreen)
   - 2.6 [BudgetConfigScreen](#26-budgetconfigscreen)
   - 2.7 [SettingsScreen](#27-settingsscreen)
   - 2.8 [SavingsGoalsScreen](#28-savingsgoalsscreen)
   - 2.9 [AmortizationScreen](#29-amortizationscreen)
   - 2.10 [RecurringExpensesScreen](#210-recurringexpensesscreen)
   - 2.11 [SyncScreen](#211-syncscreen)
   - 2.12 [BudgetCalendarScreen](#212-budgetcalendarscreen)
   - 2.13 [SimulationGraphScreen](#213-simulationgraphscreen)
   - 2.14 [QuickStartGuide](#214-quickstartguide)
3. [UI Component Classes](#3-ui-component-classes)
4. [Sound Classes](#4-sound-classes)
5. [Data Classes and Enumerations](#5-data-classes-and-enumerations)
6. [Utility Classes](#6-utility-classes)
7. [Sync Classes](#7-sync-classes)
8. [Theme Classes](#8-theme-classes)
9. [Localization Classes](#9-localization-classes)
10. [Help Screen Classes](#10-help-screen-classes)
11. [Widget Classes](#11-widget-classes)
12. [Persistence Schema](#12-persistence-schema)
13. [Repository Classes](#13-repository-classes)
14. [Error Handling](#14-error-handling)
15. [Document Revision History](#15-document-revision-history)

## 1. Introduction

BudgeTrak is a personal budget-management Android application built with Jetpack Compose and Material Design 3. It tracks income and expenses, configures recurring entries, amortizes large purchases, manages savings goals, simulates cash flow, captures receipt photos, and visualizes spending through interactive charts. A Solari-style split-flap display animates available cash on the dashboard.

Two languages (English, Spanish), multiple currency formats, export to CSV / XLSX / PDF, CSV import (generic auto-detect + US Bank format), automatic encrypted backups, a home-screen widget with quick-add, and multi-device synchronization via **SYNC** (Firestore-native per-document encrypted sync). Domain data is persisted as JSON in app-private storage; preferences in `SharedPreferences`.

Architecture: single-activity Compose with an `AndroidViewModel` (`MainViewModel`) holding all state and business logic. `MainActivity` is a thin UI-only shell with a `LoadingScreen` composable gating on `dataLoaded`. `MainViewModel` owns ~80 state variables, save functions, sync lifecycle, and background loops via `viewModelScope`. Data loading runs asynchronously on `Dispatchers.IO` with a learned-timing progress bar.

### Source File Summary

| Package / Directory | Files | Description |
|---|---|---|
| com.techadvantage.budgetrak | 3 | `BudgeTrakApplication`, `MainActivity`, `MainViewModel` |
| com.techadvantage.budgetrak.data | 31 | Data classes, repositories, utilities, `PeriodRefreshService`, `DiagDumpBuilder` |
| com.techadvantage.budgetrak.data.sync | 22 | Sync engine, encryption, receipts, `SyncMergeProcessor`, `BackgroundSyncWorker` (3-tier), `RealtimePresenceService`, `NetworkUtils` |
| com.techadvantage.budgetrak.data.ocr | 2 | `ReceiptOcrService` (Gemini Flash-Lite pipeline), `OcrResult` |
| com.techadvantage.budgetrak.data.ai | 2 | `AiCategorizerService`, `CategorizerPromptBuilder` |
| com.techadvantage.budgetrak.data.telemetry | 1 | `AnalyticsEvents` |
| com.techadvantage.budgetrak.ui.screens | 22 | Main screens + help screens |
| com.techadvantage.budgetrak.ui.components | 5 | Flip display, charts, photo row |
| com.techadvantage.budgetrak.ui.theme | 3 | Theme, colors, typography |
| com.techadvantage.budgetrak.ui.strings | 5 | i18n, translation context |
| com.techadvantage.budgetrak.sound | 1 | Flip sound player |
| com.techadvantage.budgetrak.widget | 3 | Home-screen widget |
| **Total** | **~100** | ~51,500 lines |

### Firestore / RTDB / Storage Structure

```
groups/{groupId}/
  transactions/{id}          -- per-field encrypted
  recurringExpenses/{id}     -- per-field encrypted
  incomeSources/{id}         -- per-field encrypted
  savingsGoals/{id}          -- per-field encrypted
  amortizationEntries/{id}   -- per-field encrypted
  categories/{id}            -- per-field encrypted
  periodLedger/{id}          -- per-field encrypted
  sharedSettings/current     -- per-field encrypted singleton
  devices/{deviceId}         -- plain-text device metadata (isAdmin, photoCapable, ...)
  members/{uid}              -- membership
  imageLedger/{receiptId}    -- plain-text receipt possession/assignment
  adminClaim/{claimId}       -- admin transfer workflow documents
pairing_codes/{code}         -- 10-minute TTL

(Collection names are camelCase. Legacy `deltas`/`snapshots` appear only in the Cloud Function retention list.)

Firebase Realtime Database:
  groups/{groupId}/presence/{deviceId}   -- online/offline presence, capabilities

Cloud Storage:
  groups/{groupId}/receipts/{receiptId}.enc
  groups/{groupId}/receipt_snapshot.enc
```

### Build Configuration

Kotlin 2.0.21, JVM 17, Gradle 8.9, AGP 8.7.3, compileSdk/targetSdk 34, minSdk 28. Full dependency / version table: SSD §27.

## 2. Application Classes

### 2.1 BudgeTrakApplication

**File:** `BudgeTrakApplication.kt` | **Extends:** `Application`

`onCreate` runs before any Activity, even when WorkManager starts the process. Order:

1. Honors `crashlyticsEnabled` pref before any Firebase call (opt-out is immediate).
2. Installs App Check provider (`DebugAppCheckProviderFactory` in debug, `PlayIntegrityAppCheckProviderFactory` in release) + `addAppCheckListener` for token-expiry logging.
3. Debug builds: scrapes the debug App Check token from logcat and writes it to `token_log.txt`.
4. Attaches a Firebase `AuthStateListener` that sets Crashlytics `userId` and `authAnonymous`.

**Companion helpers (static-style):**

| Helper | Purpose |
|---|---|
| `tokenLog(msg)` | Crashlytics `log()` + logcat; debug builds also append to `token_log.txt` (rotated at 100 KB). |
| `recordNonFatal(tag, message, exception?)` | Records a non-fatal `RuntimeException` in Crashlytics with custom tag/message. |
| `syncEvent(msg)` | Crashlytics `log()` + logcat for key sync lifecycle breadcrumbs. Debug builds also append to `token_log.txt`, so sync events (FCM arrivals, RTDB pings, WakeReceiver fires, worker-tier transitions) are visible in dumps. |
| `updateDiagKeys(keys)` | Batch-sets Crashlytics custom keys (attached to every future crash/non-fatal). |
| `processScope: CoroutineScope` | Process-lifetime `SupervisorJob() + Dispatchers.Default`. Used by `FcmService` to launch Tier 2 work asynchronously when VM is alive — FCM thread returns immediately, ViewModel keeps the process alive until the work completes naturally (no time budget). Cancelled implicitly when Android kills the process. |

### 2.2 MainActivity

**File:** `MainActivity.kt` | **Extends:** `ComponentActivity`

Thin UI shell. Handles edge-to-edge setup, the (debug-only) file-based crash logger, Firebase anonymous auth, lifecycle observation, ViewModel acquisition (`viewModel()`), the `LoadingScreen` gate, and `setContent` screen routing.

#### Companion Object

```kotlin
companion object { @Volatile var isAppActive = false }
```

Set `true` in `onCreate` and on `ON_START`; cleared on `ON_STOP`. `BackgroundSyncWorker` reads it to skip work when the app is foregrounded.

#### Screen Navigation Routes

| Route | Target |
|---|---|
| `main` | MainScreen (dashboard) |
| `settings` | SettingsScreen |
| `transactions` | TransactionsScreen |
| `savings_goals` | SavingsGoalsScreen |
| `amortization` | AmortizationScreen |
| `recurring_expenses` | RecurringExpensesScreen |
| `budget_config` | BudgetConfigScreen |
| `sync` | SyncScreen |
| `budget_calendar` | BudgetCalendarScreen |
| `simulation_graph` | SimulationGraphScreen |
| `*_help` | 10 help screens + QuickStartOverlay |

#### Loading Gate

`LoadingScreen(progress: Float)` — private `@Composable` at the bottom of `MainActivity.kt`. Renders a 192 dp app-icon image (rounded 24 dp), the "BudgeTrak" title at 28 sp bold, and a 200 dp × 4 dp `LinearProgressIndicator` bound to `vm.loadProgress`. Dark/light palette (dark: `#2A3A2F` / `#E8D5A0`; light: `#BDD5CC` / `#2E5C80`). Gates all UI:

```kotlin
if (!vm.dataLoaded) { LoadingScreen(vm.loadProgress); return@setContent }
```

#### Back = Home

```kotlin
if (vm.currentScreen == "main") BackHandler { moveTaskToBack(true) }
else BackHandler { vm.currentScreen = parentOf(vm.currentScreen) }
```

On the dashboard, Back sends the task to the background (keeps the process + ViewModel alive). On every other screen, Back navigates to the parent.

#### Lifecycle Observer Placement

`DisposableEffect(lifecycleOwner)` registering `LifecycleEventObserver` is placed **after** the loading gate. The initial `ON_RESUME` during activity creation is therefore intentionally missed — `onResume` work is already covered by the async init block. The observer flips `isAppActive` on `ON_START`/`ON_STOP` and calls `vm.onResume()` on `ON_RESUME`.

#### Key Methods

| Method / Block | Purpose |
|---|---|
| `onCreate(savedInstanceState)` | Installs (debug) crash logger; sets `isAppActive = true`; `enableEdgeToEdge()`; enters `setContent`. |
| Crash logger | `Thread.setDefaultUncaughtExceptionHandler` — appends device info + stack traces to `Download/BudgeTrak/support/crash_log.txt` in debug builds only (release uses Crashlytics). File rotates at 100 KB. |
| Firebase anonymous auth | Handled in `MainViewModel.init` via `FirebaseAuth.signInAnonymously().await()`; required by Firestore security rules (`request.auth != null`). |
| `setContent { ... }` | Loading gate, lifecycle observer, eviction/admin-claim popups, and the `when (vm.currentScreen)` branch that routes to each screen composable. |

#### JIT Composable Extraction

| Extracted | Role |
|---|---|
| `DashboardDialogs` | 79-parameter composable wrapping 9 dashboard dialogs; each inner dialog guarded by an `if` so it only composes when visible. |
| `SettingsScreenBranch` | Wrapper for the `currentScreen == "settings"` branch. |
| `TransactionsScreenBranch` | Wrapper for the `currentScreen == "transactions"` branch. |
| `SyncScreenBranch` | Wrapper for the `currentScreen == "sync"` branch. |

BudgetConfigScreen extraction was reverted: the 18 required setter lambdas added more bytecode than inlining saved.

### 2.3 MainViewModel

**File:** `MainViewModel.kt` | **Extends:** `AndroidViewModel`

Central coordinator. Owns ~80 state variables, all save functions, budget/derived calculations, sync lifecycle, matching chain, period refresh, maintenance, and background loops via `viewModelScope`. Survives configuration changes.

#### Companion Object

```kotlin
companion object {
    @Volatile var instance: java.lang.ref.WeakReference<MainViewModel>? = null
        private set
}
```

Set in `init { instance = WeakReference(this) }`, cleared in `onCleared()`. Read by `BackgroundSyncWorker` to decide between Tier 2 (ViewModel alive — restart dead listeners only) and Tier 3 (process restart — full sync).

#### SharedPreferences (`app_prefs`)

| Key | Type | Default | Purpose |
|---|---|---|---|
| currencySymbol | String | "$" | Currency symbol |
| digitCount | Int | 3 | Whole-digit flip cards |
| showDecimals | Boolean | false | Decimals on display |
| dateFormatPattern | String | "yyyy-MM-dd" | Date pattern |
| isPaidUser | Boolean | false | Paid-features flag |
| isSubscriber | Boolean | false | SYNC subscription flag |
| matchDays / matchPercent / matchDollar / matchChars | Int/Double/Int/Int | 7 / 1.0 / 1 / 5 | Duplicate-match tolerances |
| weekStartSunday | Boolean | true | Week start day |
| chartPalette | String | "Sunset" | Chart palette name |
| appLanguage | String | device / "en" | "en" or "es" |
| budgetPeriod | String | "DAILY" | BudgetPeriod enum |
| resetHour / resetDayOfWeek / resetDayOfMonth | Int | 0 / 7 / 1 | Daily / weekly / monthly reset |
| isManualBudgetEnabled / manualBudgetAmount | Boolean / Double | false / 0 | Manual override |
| availableCash | String? | null | Current spendable cash |
| budgetStartDate / lastRefreshDate | String? | null | Budget tracking boundaries |
| localDeviceId | String | "" | Sync device ID |
| showWidgetLogo | Boolean | true | Widget logo toggle |
| autoCapitalize | Boolean | true | Title-case merchants/descriptions |
| crashlyticsEnabled | Boolean | true | Crashlytics opt-out |
| archiveThreshold | Int | 10000 | 0 = disabled |
| loadSegTime_0..6 | Float | [50,10,5,5,5,5,5] | EMA-smoothed segment times |
| lastMaintenanceCheck | Long | 0 | 24-h maintenance gate |
| checksumMismatchAt | Long | 0 | Pending consistency recheck |

Two additional `SharedPreferences` are exposed: `sync_engine` (`syncPrefs`) and `backup_prefs` (`backupPrefs`).

#### State Fields (selected)

| Field | Type | Notes |
|---|---|---|
| currentScreen | String | Navigation route |
| transactions / categories / incomeSources / recurringExpenses / amortizationEntries / savingsGoals / periodLedger | SnapshotStateList | Primary domain collections |
| activeTransactions / activeRecurringExpenses / ... | `derivedStateOf { ... .active }` | Filters deleted + skeleton |
| sharedSettings | SharedSettings | Synced singleton |
| safeBudgetAmount | Double (derived) | `BudgetCalculator.calculateSafeBudgetAmount(...)` |
| budgetAmount | Double (derived) | base – amortization – savings – accelerated-RE deductions, clamped ≥ 0, rounded |
| budgetToday | LocalDate (derived) | Respects familyTimezone, DAILY `resetHour` |
| simAvailableCash | Double (derived) | Projected cash using current-period applied amount |
| availableCash | Double | Spendable cash; `recomputeCash()` writes it |
| lastSyncActivity / lastSyncTimeDisplay | Long / String? | Elapsed since last push/receive |
| syncDevices | List\<DeviceInfo\> | Merged Firestore + RTDB presence. Initialised at process start from `loadCachedDevices()` (`sync_engine/cachedDeviceRoster` JSON). The cache persists `lastSeen` and `online` so the roster avoids showing stale Firestore `registerDevice` values during the seconds–minutes window before the RTDB presence listener delivers its initial snapshot. |
| syncStatus | String | "off" / "synced" / "offline" / "error" |
| isSyncConfigured / syncGroupId / isSyncAdmin | Boolean / String? / Boolean | GroupManager-derived |
| initialSyncReceived | Boolean | true for solo; flipped after first batch for sync users |
| dataLoaded / loadProgress | Boolean / Float | Loading gate |
| archiveThreshold | Int | Threshold for transaction archiving |
| archiveCutoffDate | LocalDate? (derived) | Parsed from `sharedSettings.archiveCutoffDate` |
| carryForwardBalance | Double (derived) | `sharedSettings.carryForwardBalance` |
| loadedArchivedTransactions | List\<Transaction\> | Lazy-loaded view; see `loadArchivedTransactionsAsync()` |
| imageLedgerListener / deviceDocListener / adminClaimListener | ListenerRegistration? | Persistent Firestore listeners |
| quickStartStep | QuickStartStep? | Onboarding overlay |

#### Key Methods

| Method | Purpose |
|---|---|
| `init { ... }` | `instance = WeakReference(this)`; registers connectivity callback; launches async data-load coroutine on `Dispatchers.IO`; launches Firebase anonymous auth via idempotent `attemptAnonymousAuth()` (gated on `isNetworkAvailable` at init + retried inside `networkCallback.onAvailable` so offline launches don't hang on the SDK's 30–60 s auth timeout); sync-time display (`snapshotFlow` + 10 s ticker), `configureSyncGroup()` after data loads, one-time migrations, QuickStart auto-launch, period-refresh loop, App Check keep-alive (45-min check, skipped when `!isNetworkAvailable`). All data-dependent coroutines gate on `snapshotFlow { dataLoaded }.first { it }`. |
| Async data load | 7 repos loaded sequentially on IO: transactions, categories, incomeSources, recurringExpenses, amortizationEntries, savingsGoals, periodLedger. A ticker coroutine interpolates `loadProgress` at ~60 fps using EMA-stored segment times. `withContext(Main) { addAll(...); dataLoaded = true }`. Minimum 500 ms display (delay fills if load was faster). |
| Learned-timing progress bar | `loadSegTime_0..6` floats in `app_prefs`. First run saves actual times directly. Subsequent runs: `updated = (4 * old + new) / 5` (EMA). `boundaries[]` derived proportionally to `segTimes`. |
| `saveTransactions / saveCategories / saveIncomeSources / saveRecurringExpenses / saveAmortizationEntries / saveSavingsGoals / savePeriodLedger(hint?)` | All delegate to `saveCollection()` which: (1) persists to disk; (2) if `SyncWriteHelper.isInitialized()`, pushes via `SyncWriteHelper.pushBatch()` — hint-driven if supplied, otherwise diff vs. last-saved cache; (3) updates `lastSyncActivity`. `saveTransactions()` additionally dedups by id. |
| `saveSharedSettings()` | Persists + pushes via `SyncWriteHelper.pushSharedSettings`. |
| `persistAvailableCash()` | Guards NaN/Infinity → 0; rounds cents; writes `availableCash` pref; notifies widget provider. |
| `recomputeCash()` | **Synchronous** (no coroutine wrapper, to avoid startup races). Reads `budgetStartDate`, `periodLedger`, `activeTransactions`, `activeRecurringExpenses`, `incomeMode`, `activeIncomeSources`, `carryForwardBalance`, `archiveCutoffDate` and calls `BudgetCalculator.recomputeAvailableCash(...)`. Assigns + persists. |
| `addTransactionWithBudgetEffect(txn)` | Stamps `deviceId`. Then **atomically** under one `transactions.none { it.id == stamped.id }` guard: deducts from linked savings goal if any → `transactions.add` → `saveTransactions(listOf(stamped))` (writes JSON + pushes to Firestore). After the guard: `recomputeCash()`; `checkAndTriggerArchive()` if threshold exceeded. The all-or-nothing guard makes double-tap and recomposition replay complete no-ops rather than leaking partial side effects (e.g., a Firestore push without a local list mutation, or a duplicate SG deduction). |
| `runLinkingChain(txn)` | Recurring → Amortization (expense) / Budget-income (income). Background search on `Dispatchers.Default`; shows confirm dialog or calls `addTransactionWithBudgetEffect`. |
| `runMatchingChain(txn)` | Duplicate check first (`findDuplicates` on Default); shows `ManualDuplicateDialog` or delegates to `runLinkingChain`. |
| `configureSyncGroup()` | Disposes any prior `FirestoreDocSync`/`SyncWriteHelper`, creates new `FirestoreDocSync`, wires `onBatchChanged` + `onListenerRecovered`, calls `startListeners()`, `startSyncSetup()`, and sets up `RealtimePresenceService.setupPresence() + listenToGroupPresence(...)`. Presence merges into `syncDevices`. |
| `startSyncSetup()` | After data + group ready: initial device-list fetch (one retry), early dissolution/removal check via `getGroupHealthStatus` + `isDeviceRemoved`, one-time `updateDeviceMetadata` (photoCapable, appSyncVersion=2, minSyncVersion=2), device-doc listener (own device removed / admin flipped), admin-claim listener, FCM registration, one-time migrations, image-ledger listener, `awaitInitialSync(30_000)` → `runIntegrityCheck()` + `recomputeCash()`. |
| `runIntegrityCheck()` | Private suspend. Compares local record IDs vs. `FirestoreDocService.readDocIdsFromCache()` (`Source.CACHE`, zero network). Pushes any local-only records via `SyncWriteHelper.push*`. Callable from startup and `runPeriodicMaintenance`. |
| `runConsistencyCheck()` | Private suspend. **Layer 1:** per-collection `countActiveDocs()` vs. `local.active.size`; on mismatch, clears the collection cursor to force a full re-read on next listener attach. **Layer 2:** cashHash majority vote — writes `deviceChecksums[deviceId].cashHash` on the group doc (hex digest: `availableCash.toString().hashCode().toString(16)`), with a 1-hour confirmation gate before triggering any re-read recovery. |
| `recheckConsistency()` | Public suspend wrapper; called from `onResume` and `BackgroundSyncWorker` Tier 3 when `checksumMismatchAt > 0`. |
| `runPeriodicMaintenance()` | Private suspend. Called from `onResume` under a 24-hour gate (`lastMaintenanceCheck`). Consolidates: (1) daily `HEALTH_BEACON` non-fatal + `updateDiagKeys` (sync users, crashlytics-enabled); (2) backup check (`BackupManager.isBackupDue` → `performBackup` on IO); (3) `runIntegrityCheck` + `recomputeCash` + `runConsistencyCheck` (sync users only); (4) receipt-orphan cleanup (solo-user reference cleanup + orphan local files); (5) receipt-storage pruning (`receiptPruneAgeDays`); (6) admin-tombstone + cloud-orphan cleanup — time-gated to 30 days via `lastAdminCleanup` and gated on admin-only. |
| `checkAndTriggerArchive() / applyArchiveCutoff(cutoff)` | When active count > `archiveThreshold` and initial sync received, archives ~25% of the oldest transactions. Computes new `carryForwardBalance` for the archived slice via `BudgetCalculator.recomputeAvailableCash(...)`, appends to `archived_transactions.json` (off-thread), removes from active list, writes `archiveCutoffDate` + `carryForwardBalance` + `lastArchiveInfo` to `SharedSettings`, pushes SharedSettings, `recomputeCash()`. |
| `onBatchChanged(events)` | Suspend. Calls `SyncMergeProcessor.processBatch(...)`; applies non-null collection results to state lists; pushes any conflict-resolved transactions back; deletes remapped categories; applies settings/prefs; saves each mutated repo; `recomputeCash()`; Layer 1 fast-path: downloads any newly-referenced receipts via `ReceiptSyncManager.downloadReceiptWithRetry` (5 concurrent); misses are handed to `kickFgDownloadRetry` for Layer 2 backoff retry. |
| `kickUploadDrainer()` | Layer 0. Launches or reuses `uploadDrainerJob` on `viewModelScope` (IO). While `pending_receipt_uploads.json` is non-empty, calls `ReceiptSyncManager.processPendingUploads()` and backs off 30 s → 60 s → 2 m → 5 m → 10 m on failure. Kicked from `saveTransactions` when a new receiptId attaches, from the rotation callback (`onPhotoContentChanged`), and once at VM init after `dataLoaded = true` for crash recovery. |
| `kickFgDownloadRetry(receiptId)` | Layer 2. Adds id to in-memory `fgDownloadRetryQueue` and launches / reuses `fgDownloadRetryJob`. Coroutine drains the set with the same exponential backoff as Layer 0; self-filters each tick (drops ids now unreferenced or that arrived via another path); uses `ReceiptSyncManager.downloadReceiptWithRetry` (3-retry-to-recovery-request escalation, shared with `processRecovery`). |
| `isReceiptSyncActive()` | Returns `uploadDrainerJob?.isActive == true || fgDownloadRetryJob?.isActive == true`. Read by `BackgroundSyncWorker` Tier 2 to skip the transient `syncReceipts()` call when foreground is already handling it. |
| `cancelReceiptSyncJobs()` | Cancels `uploadDrainerJob` + `fgDownloadRetryJob` and clears `fgDownloadRetryQueue`. Called from `resetSyncState()` (leave/dissolve/evict), the inline leave handler, and `MainActivity` paid/subscriber downgrade toggles — any transition that invalidates the captured `syncGroupId` or encryption key. |
| Period refresh loop | After data loads + `initialSyncReceived` (or 60 s timeout for solo users). Builds `PeriodRefreshService.RefreshConfig`, calls `refreshIfNeeded(context, config)`, applies `RefreshResult` to state, pushes ledger/SG/RE via hint-driven save functions. **Sleep:** computes `nextBoundary` from `BudgetCalculator.currentPeriodStart(...)` + one period; sleeps `boundaryMs - nowMs + 60_000`, **clamped to [60 s, 15 min]**. |
| `onResume()` | Early-return if `!dataLoaded`. Bumps `syncTrigger`; **add-only disk merge** — reads `TransactionRepository.load` on IO and adds any disk-only ids to memory (never wipes in-memory state); App Check token refresh (sync users); RTDB presence re-setup; runs `runPeriodicMaintenance()` if 24-h gate elapsed, else `recheckConsistency()` if mismatch pending; updates Crashlytics diag keys (if opted-in). Add-only avoids two race classes: (a) just-saved transactions whose disk write completed after the IO read started, and (b) sync-merged in-memory state whose disk write is still pending (`onBatchChanged` updates memory synchronously and writes via `withContext(IO)` separately). |
| `handleWidgetIntent(action)` | Maps widget `ACTION_ADD_INCOME` / `ACTION_ADD_EXPENSE` to dashboard quick-add flags. |
| `reloadAllFromDisk()` | Full state reload after backup restore. |
| `disposeSyncListeners()` | Disposes `FirestoreDocSync`, `SyncWriteHelper`, `RealtimePresenceService`, and all three persistent listeners. |
| `evictFromSync(reason)` | Private. Local-only group leave; clears sync state; sets `syncEvictionMessage` for dashboard popup. |
| `onAdminClaimChanged / resolveExpiredClaim / voteOnAdminClaim` | Admin-claim voting workflow on the group doc. |
| `onCleared()` | Clears `instance`; disposes all sync resources; unregisters network callback. |

#### Auto-Provisioned Categories

On startup the system reconciles required categories by tag; any missing is auto-created with the localized name.

| Category Name | Tag | Icon | Purpose |
|---|---|---|---|
| Other | `__other__` | CreditCard | Default fallback |
| Recurring | `__recurring__` | Sync | Recurring-expense matches |
| Amortization | `__amortization__` | Schedule | Amortization matches |
| Recurring Income | `__recurring_income__` | Payments | Budget-income matches |

#### Sync Time Display

`snapshotFlow { lastSyncActivity }` immediately updates `lastSyncTimeDisplay`; a 10-second ticker coroutine refreshes the elapsed-time string so it ticks forward ("5 s ago" → "15 s ago").

#### Background Worker Scheduling

`BackgroundSyncWorker.schedule(context)` is called after data loads for sync users; `DebugDumpWorker.schedule(context)` is scheduled during sync setup when enabled.

### 2.4 MainScreen

**File:** `ui/screens/MainScreen.kt`

Dashboard: Solari split-flap display, spending charts (pie + bar), quick-add dialogs, Supercharge, sync-status indicators, five-button nav bar.

| Composable | Visibility | Purpose |
|---|---|---|
| `MainScreen` | public | Full dashboard |
| `SpendingPieChart` | private | Period spending pie |
| `SavingsSuperchargeDialog` | private | Supercharge allocation dialog |

Key observed state: `vm.availableCash`, `vm.budgetAmount`, `vm.budgetStartDate`, `vm.activeSavingsGoals`, `vm.activeTransactions`, `vm.activeCategories`, `vm.syncStatus`, `vm.syncDevices`, `vm.localDeviceId`, `vm.syncRepairAlert`.

### 2.5 TransactionsScreen

**File:** `ui/screens/TransactionsScreen.kt`

Largest screen. List + filter + search + multi-select + entry/edit dialogs, CSV import (generic auto-detect, US Bank, BudgeTrak CSV), export (CSV / XLSX / PDF expense report), linked-transaction display, receipt photo capture/display, full-backup load. Encryption is used only in the auto-backup feature (no manual encrypted save format).

| Composable | Visibility | Role |
|---|---|---|
| `TransactionsScreen` | public | Main list |
| `TransactionDialog` | public | Add/edit with receipt slots |
| `TransactionRow` | private | Row with expand + photo thumbnails |
| `TransactionCard` | private | Card layout helper |
| `DuplicateResolutionDialog` | public | CSV + manual-add duplicate confirm. **Non-dismissable** — `MatchDialogCard.onDismiss = {}` so tap-outside / back are no-ops; user must pick Keep Existing / Keep New / Keep Both / Ignore All (import only). Required because a dismissable variant silently dropped the new transaction when the user tapped outside. The widget's inline duplicate dialog (`WidgetTransactionActivity.kt`) follows the same pattern. |
| `RecurringExpenseConfirmDialog` | public | RE match confirm. Dismiss = "no match", txn proceeds — intentionally dismissable. |
| `AmortizationConfirmDialog` | public | Amortization match confirm. Dismiss = "no match", txn proceeds — intentionally dismissable. |
| `BudgetIncomeConfirmDialog` | public | Budget-income confirm. Dismiss = "no match", txn proceeds — intentionally dismissable. |
| `TextSearchDialog` / `AmountSearchDialog` / `SearchDatePickerDialog` | private | Search inputs |
| `SaveFormatDialog` / `FullBackupLoadDialog` / `ImportFormatSelectionDialog` / `ImportParseErrorDialog` / `ManualDuplicateDialog` / `EffectExplanationPopup` | private | JIT-extracted with `if`-guards so each only composes when visible |
| `MatchDialogCard` | public | Shared card layout for match dialogs |

### 2.6 BudgetConfigScreen

**File:** `ui/screens/BudgetConfigScreen.kt`

Income sources, budget-period selection, reset hour/day configuration, safe-budget display, manual override with warnings, income mode, budget reset. Top-level composables: `BudgetConfigScreen` (public) + two private dialog helpers (income-source add/edit, reset-confirm).

### 2.7 SettingsScreen

**File:** `ui/screens/SettingsScreen.kt`

Currency, category management (charted / widgetVisible toggles, reassign), match tolerances, language, date format, chart palette, widget logo, paid features, backup / restore (SAF directory picker), expense-report generation, Crashlytics opt-out, auto-capitalize.

| Composable | Visibility |
|---|---|
| `SettingsScreen` | public |
| `AddCategoryDialog` / `EditCategoryDialog` / `ReassignCategoryDialog` | private |

### 2.8 SavingsGoalsScreen

**File:** `ui/screens/SavingsGoalsScreen.kt`

Savings goals with target-date and fixed-contribution types, progress tracking, Supercharge integration. Top-level composables: `SavingsGoalsScreen` (public) + private goal add/edit dialog.

### 2.9 AmortizationScreen

**File:** `ui/screens/AmortizationScreen.kt`

Amortization entries with progress tracking, per-period deductions, pause/resume. Top-level composables: `AmortizationScreen` (public) + private entry dialog.

### 2.10 RecurringExpensesScreen

**File:** `ui/screens/RecurringExpensesScreen.kt`

Recurring expenses with 6 repeat types, "savings required" simulation box, Why-explanation dialog, set-aside tracking, accelerated-expense toggle. Top-level composables: `RecurringExpensesScreen` (public) + private add/edit dialog + "Why" simulation popup.

### 2.11 SyncScreen

**File:** `ui/screens/SyncScreen.kt`

SYNC configuration screen. Create / join / leave / dissolve groups; sync status; device list with online status; admin claims; subscription management; admin-only gating on budget-period + reset-config edits; device naming; device removal; FCM-triggered debug-file request. Top-level composable: `SyncScreen` (public).

### 2.12 BudgetCalendarScreen

**File:** `ui/screens/BudgetCalendarScreen.kt`

Calendar showing daily spending, income events, and recurring expense / income due dates across budget periods. Blue tint marks the reset day. Top-level composable: `BudgetCalendarScreen` (public).

### 2.13 SimulationGraphScreen

**File:** `ui/screens/SimulationGraphScreen.kt`

Cash-flow projection over time based on income, recurring expenses, savings goals, and budget spending. Subscriber feature. Top-level composable: `SimulationGraphScreen` (public).

### 2.14 QuickStartGuide

**File:** `ui/screens/QuickStartGuide.kt`

Multi-step onboarding overlay auto-launched once per install when `incomeSources.isEmpty() && !isSyncConfigured && !quickStartCompleted`.

```kotlin
enum class QuickStartStep { WELCOME, BUDGET_PERIOD, INCOME, EXPENSES, FIRST_TRANSACTION, DONE }
```

| Step | Destination navigation |
|---|---|
| WELCOME | stay on current screen |
| BUDGET_PERIOD | `budget_config` |
| INCOME | `budget_config` (same page) |
| EXPENSES | `recurring_expenses` |
| FIRST_TRANSACTION | `main` |
| DONE | stay on current screen |

Top-level composable: `QuickStartOverlay` (public) with alignment adapting per step. Dismissible backdrop on `WELCOME` / `DONE`. English/Spanish step content tables (`englishSteps()` / `spanishSteps()`).

---

**Application-class coverage complete.** Subsequent chunks document UI components (§3), sound (§4), data classes (§5), utilities (§6), sync (§7), theme (§8), i18n (§9), the 10 help screens + QuickStartGuide (§10), widget (§11), persistence schema (§12), repositories (§13), error handling (§14), and revision history (§15).
## 3. UI Component Classes

All components live under `com.techadvantage.budgetrak.ui.components` unless noted.

### 3.1 FlipDisplay

**File:** `ui/components/FlipDisplay.kt` (258 lines)

Top-level Solari-style readout. Arranges FlipChar (sign + currency) and FlipDigit cards into a responsive currency display with leading-zero suppression and optional decimal places. Canvas bitmap rendering; monospace font family `FlipFontFamily` defined in `ui/theme/Type.kt`.

| Constant | Value | Purpose |
|---|---|---|
| CARD_ASPECT | 1.5 | Height/width ratio |
| GAP | 5dp | Inter-card spacing |
| DOT_WIDTH | 10dp | Decimal separator width |
| FRAME_H_PAD / V_PAD | 16dp / 20dp | Frame insets |
| MAX_CARD_WIDTH | 72dp | Card width cap |

### 3.2 FlipChar

**File:** `ui/components/FlipChar.kt` (293 lines)

Animated flip card for string values (sign, currency symbol). 250ms flip with 3D rotation, gradient overlays, canvas bitmap rendering, and sound callback at the midpoint (half-flip).

### 3.3 FlipDigit

**File:** `ui/components/FlipDigit.kt` (316 lines)

Animated flip card for digits 0–9 with a blank state (digit = -1). Integer-based targets, same 3D-rotation/canvas mechanics as FlipChar.

### 3.4 PieChartEditor

**File:** `ui/components/PieChartEditor.kt` (470 lines)

Interactive donut-style multi-category allocation editor used inside `TransactionDialog`. Canvas rendering with drag-to-resize slice boundaries, calculator/percentage input modes, real-time visual feedback, and 6 palette variants.

### 3.5 SwipeablePhotoRow

**File:** `ui/components/SwipeablePhotoRow.kt` (669 lines)

Horizontally swipeable row for up to 5 receipt photos per transaction. Swipe-left reveals an action panel; camera and gallery pickers via `ActivityResultContracts`; full-screen viewer supports rotation. Shows loading state for photos still downloading from Cloud Storage.

### 3.6 Theme helpers (ui/theme/Theme.kt, 665 lines)

| Composable | Purpose |
|---|---|
| `AdAwareDialog` | Raw dialog wrapper; `SOFT_INPUT_ADJUST_NOTHING`; respects `LocalAdBannerHeight` + `LocalAppToast` anchoring. Overlays `PulsingScrollArrows` onto the body automatically when a `scrollState` is provided. |
| `AdAwareAlertDialog` | AlertDialog replacement with Surface/Column layout, optional body ScrollState, auto bidirectional scroll arrows |
| `AdAwareDatePickerDialog` | Date picker wrapped in AdAwareDialog |
| `BoxScope.PulsingScrollArrows(scrollState, topPadding=36.dp, bottomPadding=50.dp)` | Bidirectional scroll affordance: pulsing up-arrow at `Alignment.TopStart` when `canScrollBackward`, down-arrow at `Alignment.BottomStart` when `canScrollForward`. 24-dp icons, 600 ms `RepeatMode.Reverse` bounce, alpha 0.5 onSurface. Default paddings clear a `DialogHeader` at the top and a footer button row at the bottom. |
| `ScrollableDropdownContent(maxHeight=280.dp, contentStartPadding=32.dp, content)` | Drop-in wrapper for `DropdownMenu` / `ExposedDropdownMenu` bodies. Owns its own `ScrollState`, caps height to `maxHeight`, indents content start by `contentStartPadding` so items clear the left-edge arrow column. Short lists wrap to content size. |
| `PulsingScrollArrow(scrollState, modifier)` | Legacy down-only variant. Kept for backward compatibility; new code uses `PulsingScrollArrows` (plural). |
| `DialogHeader`, `DialogFooter` | Colored header/footer per `DialogStyle` |
| `DialogPrimaryButton`, `DialogSecondaryButton`, `DialogDangerButton`, `DialogWarningButton` | Standardized buttons, 500 ms debounce |

Supporting declarations:

- `enum class DialogStyle { DEFAULT, DANGER, WARNING }` — green / red / orange header palettes.
- `val LocalAdBannerHeight = compositionLocalOf { 0.dp }` — 50.dp (free tier) or 0.dp (paid).
- `val LocalAppToast = staticCompositionLocalOf { AppToastState() }` + class `AppToastState` — Y-anchored toast positioned above the ad banner.

## 4. Sound Classes

### 4.1 FlipSoundPlayer

**File:** `sound/FlipSoundPlayer.kt` (134 lines)  |  **Package:** `com.techadvantage.budgetrak.sound`

Synthesizes a mechanical clack at init and plays it through a `SoundPool`. Constants and synthesis algorithm: see SSD §3.6.

Implementation note: encoded as 16-bit mono PCM RIFF WAV, written to `cacheDir/clack.wav`, loaded into SoundPool (temp file is deleted after load).

## 5. Data Classes and Enumerations

### 5.1 BudgetPeriod (Enum)

**File:** `data/BudgetPeriod.kt` (5 lines)

| Value | Periods / yr | Meaning |
|---|---|---|
| DAILY | 365.25 | Resets each day |
| WEEKLY | 365.25 / 7 | Resets each week |
| MONTHLY | 12 | Resets each month |

### 5.2 RepeatType (Enum)

**File:** `data/IncomeSource.kt`

| Value | Meaning |
|---|---|
| DAYS | Every N days from start date |
| WEEKS | Every N weeks from start date |
| BI_WEEKLY | Every 14 days from start date |
| MONTHS | Every N months on `monthDay1` |
| BI_MONTHLY | Twice per month on `monthDay1` + `monthDay2` |
| ANNUAL | Once per year |

### 5.3 TransactionType / SuperchargeMode / IncomeMode / BankFormat

| Enum | Values |
|---|---|
| TransactionType | `EXPENSE`, `INCOME` |
| SuperchargeMode | `REDUCE_CONTRIBUTIONS`, `ACHIEVE_SOONER` |
| IncomeMode | `FIXED`, `ACTUAL`, `ACTUAL_ADJUST` |
| BankFormat | `GENERIC_CSV`, `US_BANK`, `SECURESYNC_CSV` |
| SaveFormat (TransactionsScreen) | `CSV`, `XLS`, `PDF` |

`SuperchargeMode` is a UI-state enum only — not a `SavingsGoal` field.

### 5.4 Sync Fields Convention

All per-record entities carry exactly two sync-metadata fields: `deviceId: String = ""` (origin) and `deleted: Boolean = false` (tombstone). **No `_clock` / Lamport / HLC fields on any data class.** `SharedSettings` uses a single-document merge model (no per-field clocks).

### 5.5 Transaction

**File:** `data/Transaction.kt` (48 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | required | Unique ID (random `(1..Int.MAX_VALUE)`, rejected against local `existingIds`) |
| type | TransactionType | required | EXPENSE / INCOME |
| date | LocalDate | required | Transaction date |
| source | String | required | Merchant name |
| description | String | "" | Free text |
| categoryAmounts | List\<CategoryAmount\> | emptyList() | Multi-category split |
| amount | Double | required | Total amount |
| isUserCategorized | Boolean | true | false = auto-categorized or sync-flagged |
| excludeFromBudget | Boolean | false | Don't apply to cash/budget |
| isBudgetIncome | Boolean | false | Matches an income source (FIXED mode) |
| linkedRecurringExpenseId | Int? | null | Linked RE |
| linkedAmortizationEntryId | Int? | null | Linked AE |
| linkedIncomeSourceId | Int? | null | Linked IS |
| linkedSavingsGoalId | Int? | null | Linked SG |
| amortizationAppliedAmount | Double | 0.0 | Amount deducted by AE at link time |
| linkedRecurringExpenseAmount | Double | 0.0 | RE amount remembered at link time |
| linkedIncomeSourceAmount | Double | 0.0 | IS amount remembered at link time |
| linkedSavingsGoalAmount | Double | 0.0 | SG amount remembered at link time |
| receiptId1 .. receiptId5 | String? | null | Up to 5 receipt photo slot IDs |
| deviceId | String | "" | Origin device |
| deleted | Boolean | false | Tombstone |

Remembered `linked*Amount` fields make cash recompute deterministic across devices even after the linked RE/IS/SG amount changes.

### 5.6 CategoryAmount (inner)

Declared in `data/Transaction.kt`.

| Field | Type | Purpose |
|---|---|---|
| categoryId | Int | FK into Category |
| amount | Double | Dollars allocated to that category |

### 5.7 Category

**File:** `data/Category.kt` (13 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | required | Unique ID |
| name | String | required | Display name |
| iconName | String | required | Key into `CATEGORY_ICON_MAP` |
| tag | String | "" | Protected-category tag |
| charted | Boolean | true | Include in pie chart |
| widgetVisible | Boolean | true | Show on home-screen widget |
| deviceId | String | "" | Origin |
| deleted | Boolean | false | Tombstone |

Protected tags: `"other"`, `"recurring_income"`, `"supercharge"`. The initial category set is provisioned from `DefaultCategories.kt`.

### 5.8 IncomeSource

**File:** `data/IncomeSource.kt` (28 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | required | Unique ID |
| source | String | required | Income name |
| description | String | "" | Optional |
| amount | Double | required | Per-occurrence amount |
| repeatType | RepeatType | MONTHS | Recurrence |
| repeatInterval | Int | 1 | N-step multiplier |
| startDate | LocalDate? | null | Anchor date |
| monthDay1 | Int? | null | Monthly day |
| monthDay2 | Int? | null | Second day (BI_MONTHLY) |
| deviceId | String | "" | Origin |
| deleted | Boolean | false | Tombstone |

### 5.9 RecurringExpense

**File:** `data/RecurringExpense.kt` (29 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | required | Unique ID |
| source | String | required | Expense name |
| description | String | "" | Optional |
| amount | Double | required | Per-occurrence amount |
| repeatType | RepeatType | MONTHS | Recurrence |
| repeatInterval | Int | 1 | N-step multiplier |
| startDate | LocalDate? | null | Anchor |
| monthDay1 | Int? | null | Monthly day |
| monthDay2 | Int? | null | Second day (BI_MONTHLY) |
| deviceId | String | "" | Origin |
| deleted | Boolean | false | Tombstone |
| setAsideSoFar | Double | 0.0 | Accumulated set-aside |
| isAccelerated | Boolean | false | Accelerated set-aside mode |

### 5.10 AmortizationEntry

**File:** `data/AmortizationEntry.kt` (24 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | required | Unique ID |
| source | String | required | Purchase label |
| description | String | "" | Optional |
| amount | Double | required | Total purchase amount |
| totalPeriods | Int | required | Periods to spread over |
| startDate | LocalDate | required | Amortization start |
| deviceId | String | "" | Origin |
| deleted | Boolean | false | Tombstone |
| isPaused | Boolean | false | Suspend deductions |

### 5.11 SavingsGoal

**File:** `data/SavingsGoal.kt` (48 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | required | Unique ID |
| name | String | required | Goal name |
| targetAmount | Double | required | Goal amount |
| targetDate | LocalDate? | null | null = fixed-contribution mode |
| totalSavedSoFar | Double | 0.0 | Accumulated savings |
| contributionPerPeriod | Double | 0.0 | Fixed per-period contribution |
| isPaused | Boolean | false | Suspend deductions |
| deviceId | String | "" | Origin |
| deleted | Boolean | false | Tombstone |

`SuperchargeMode` is **not** persisted on the goal — the enum lives in the same file but is UI-state only.

### 5.12 SharedSettings

**File:** `data/SharedSettings.kt` (28 lines). Single Firestore doc at `sharedSettings/current`.

| Field | Type | Default | Purpose |
|---|---|---|---|
| currency | String | "$" | Currency symbol |
| budgetPeriod | String | "DAILY" | DAILY / WEEKLY / MONTHLY |
| budgetStartDate | String? | null | ISO date |
| isManualBudgetEnabled | Boolean | false | Manual override toggle |
| manualBudgetAmount | Double | 0.0 | Manual override amount |
| weekStartSunday | Boolean | true | Week start day |
| resetDayOfWeek | Int | 7 | ISO DOW for weekly reset |
| resetDayOfMonth | Int | 1 | DOM for monthly reset |
| resetHour | Int | 0 | Daily reset hour |
| familyTimezone | String | "" | Shared TZ |
| matchDays / matchPercent / matchDollar / matchChars | Int / Double / Int / Int | 7 / 1.0 / 1 / 5 | Duplicate / link tolerances |
| showAttribution | Boolean | false | Show `lastChangedBy` UI |
| availableCash | Double | 0.0 | Shared cash balance |
| incomeMode | String | "FIXED" | FIXED / ACTUAL / ACTUAL_ADJUST |
| deviceRoster | String | "{}" | JSON map `{deviceId: nickname}` |
| receiptPruneAgeDays | Int? | null | null = no prune (admin-only) |
| lastChangedBy | String | "" | Device ID that last wrote |
| archiveCutoffDate | String? | null | ISO date; txns before are archived |
| carryForwardBalance | Double | 0.0 | Cumulative cash effect of archived data |
| lastArchiveInfo | String? | null | JSON `{"date","count","totalArchived"}` |
| archiveThreshold | Int | 10000 | 0 = off; synced across devices |

### 5.13 PeriodLedgerEntry

**File:** `data/sync/PeriodLedger.kt` (73 lines)

| Field | Type | Default | Purpose |
|---|---|---|---|
| periodStartDate | LocalDateTime | required | Period start |
| appliedAmount | Double | required | Credit applied to cash |
| corrected | Boolean | false | Unused; kept for JSON back-compat |
| deviceId | String | "" | Origin |

Derived: `id: Int = periodStartDate.toLocalDate().toEpochDay().toInt()`. Dedup: `groupBy { it.id }.map { maxByOrNull { it.periodStartDate } }`. No `deleted` field (ledger rows are immutable), no clock fields.

### 5.14 ImageLedgerEntry / SnapshotLedgerEntry

**File:** `data/sync/ImageLedgerEntry.kt` (26 lines)

ImageLedgerEntry:

| Field | Type | Default | Purpose |
|---|---|---|---|
| receiptId | String | required | Unique receipt ID |
| originatorDeviceId | String | required | Capturing device |
| createdAt | Long | required | Epoch ms |
| possessions | Map\<String, Boolean\> | required | Per-device has-file flag |
| uploadAssignee | String? | null | Device picked for re-upload |
| assignedAt | Long | 0 | When assignment made |
| uploadedAt | Long | 0 | 0 = not in cloud |

Possession map is three-state: `true` = has file, `false` = known gone, **key absent** = unknown.

SnapshotLedgerEntry: `requestedBy, requestedAt, builderId?, builderAssignedAt, status, progressPercent, errorMessage?, lastProgressUpdate, snapshotReceiptCount, readyAt, consumedBy: Map<String,Boolean>`. Status values: `"requested" | "building" | "uploading" | "ready" | "error"`.

### 5.15 ID Generators

All follow the same pattern: random `Int` in `1..Int.MAX_VALUE`, rejected against `existingIds` in a do/while loop. The full positive `Int` range drops cross-device collision probability in a sync group to ≈1 in 2.1 B per concurrent pair (the local rejection loop handles same-device collisions; previously a 16-bit space made simultaneous picks across devices a real risk).

| Function | File |
|---|---|
| `generateTransactionId(existingIds)` | Transaction.kt |
| `generateIncomeSourceId(existingIds)` | IncomeSource.kt |
| `generateRecurringExpenseId(existingIds)` | RecurringExpense.kt |
| `generateAmortizationEntryId(existingIds)` | AmortizationEntry.kt |
| `generateSavingsGoalId(existingIds)` | SavingsGoal.kt |

The default-category seed loop in `MainViewModel.kt` and the inline category-create dialog in `SettingsScreen.kt` use the same range pattern.

## 6. Utility Classes

Package `com.techadvantage.budgetrak.data` unless noted.

### 6.1 BudgetCalculator

**File:** `data/BudgetCalculator.kt` (504 lines)  |  object singleton

All budget math: occurrence generation, safe-budget projection, deterministic cash recompute, and per-period deduction math. Uses theoretical annual rates (365.25-based) to avoid 26-vs-27-paycheck alignment artifacts.

| Method | Purpose |
|---|---|
| `generateOccurrences(repeatType, interval, startDate, monthDay1, monthDay2, rangeStart, rangeEnd)` | All 6 RepeatType variants; skip-ahead avoids iterating from startDate |
| `calculateSafeBudgetAmount(is, re, period, today)` | `(totalIncome − totalExpenses) / periodsPerYear`, clamped ≥ 0 |
| `theoreticalAnnualOccurrences(rt, ri)` | Annual rate per RepeatType |
| `countPeriodsCompleted(from, to, period)` | Raw period count |
| `countElapsedPeriods(from, to, period, resetDow)` | Week-boundary-aligned for WEEKLY |
| `currentPeriodStart(period, resetDow, resetDom, tz?, resetHour)` | Current period start date; DAILY honors `resetHour` |
| `activeAmortizationDeductions(entries, period, today, resetDow)` | Cumulative-diff approach (avoids rounding drift) |
| `activeSavingsGoalDeductions(goals, period, today, resetDow)` | Target-date vs fixed-contribution modes |
| `normalPerPeriodDeduction(re, period, today)` | Implicit per-period set-aside (no rounding — caller rounds) |
| `periodsUntilNextOccurrence(re, period, today, resetDow)` | `≥ 1` once `nextDue > today` |
| `acceleratedREExtraDeductions(re, period, today, resetDow)` | Extra beyond normal rate for accelerated REs |
| `calculateAccruedSavingsNeeded(re, today)` | Pro-rata accrual across current billing cycle |
| `computeFullBudgetAmount(is, re, ae, sg, period, manual?, manualAmt, today, resetDow)` | `base − amort − savings − accel`, clamped ≥ 0 |
| `recomputeAvailableCash(budgetStart, ledger, txns, re, incomeMode, is, carryForward, archiveCutoff?)` | Deterministic cash (see below) |
| `roundCents(v)` | `roundToLong(v*100)/100` |

`recomputeAvailableCash` formula (starting from `carryForwardBalance`; entries/txns before `archiveCutoffDate ?: budgetStart` are ignored):

1. Sum deduped ledger credits (one per epoch day, keep latest `periodStartDate`).
2. For each active non-excluded txn:
   - **EXPENSE, savings-goal-linked**: subtract `max(0, amount − linkedSavingsGoalAmount)`.
   - **EXPENSE, amortization-linked**: skip (budget already covered it).
   - **EXPENSE, had `amortizationAppliedAmount > 0`** (AE since deleted): subtract only the unamortized remainder.
   - **EXPENSE, recurring-linked**: add `linkedRecurringExpenseAmount − amount` (or live RE amount for legacy rows).
   - **EXPENSE, unlinked**: subtract full amount.
   - **INCOME, IS-linked**: FIXED / ACTUAL_ADJUST → no effect; ACTUAL → add `amount − linkedIncomeSourceAmount`.
   - **INCOME, unlinked & not `isBudgetIncome`**: add full amount.
3. Return `roundCents(result)`.

### 6.2 CryptoHelper

**File:** `data/CryptoHelper.kt` (93 lines)  |  object singleton

Authenticated encryption via ChaCha20-Poly1305. Cipher params + mode summary: SSD §10.1–10.2.

| Method | Wire format | Use |
|---|---|---|
| `encrypt(plaintext, password)` / `decrypt(data, password)` | `[salt][nonce][ct+tag]` | Backups (PBKDF2WithHmacSHA256 → ChaCha20-Poly1305) |
| `encryptWithKey(plaintext, key)` / `decryptWithKey(data, key)` | `[nonce][ct+tag]` | Sync payloads (pre-shared 32-byte key) |
| `deriveKey(password, salt)` | — | PBKDF2; 100k iterations, 256-bit key |

### 6.3 CsvParser

**File:** `data/CsvParser.kt`. Top-level functions + enums.

Parses US Bank CSV, generic bank CSV, and the native "BudgeTrak CSV Save File" format; serializes transactions back to CSV. Includes merchant-name cleaning and multi-format auto-detect.

| Function | Purpose |
|---|---|
| `parseUsBank(reader, existingIds)` | US Bank CSV parser |
| `parseCsvLine(line)` | Quote-aware line splitter |
| `cleanMerchantName(raw)` | Private; strips control codes / junk |
| `serializeTransactionsCsv(txns)` | Native CSV export |
| `parseSyncBudgetCsv(reader, existingIds)` | Native CSV import |

`BankFormat`: `GENERIC_CSV` (auto-detect), `US_BANK`, `SECURESYNC_CSV` (native — name preserved across rebrand).  
`SaveFormat` (in TransactionsScreen): `CSV`, `XLS` (.xlsx), `PDF` (expense report).

### 6.4 DuplicateDetector

**File:** `data/DuplicateDetector.kt` (249 lines). Top-level functions.

Actively used on CSV import and manual entry. The `findXMatches` variants return **ranked lists** that feed the multi-candidate radio-button dialog.

| Function | Purpose |
|---|---|
| `filterAlreadyLoadedDays(fileTxns, appTxns)` | Skip a day if ≥ 80 % (or 100 % for ≤ 5 txns) of amounts match |
| `findDuplicates(incoming, existing, ...)` | Ranked duplicate candidates (amount → date) |
| `findRecurringExpenseMatches(incoming, re, ...)` | Ranked RE matches (date → amount) |
| `findAmortizationMatches(incoming, entries, ...)` | Ranked AE matches (amount) |
| `findBudgetIncomeMatches(incoming, is, ...)` | Ranked IS matches (INCOME txns only) |
| `nearestOccurrenceDistance / Date(...)` | Advisory: days from expected occurrence |
| `isRecurringDateCloseEnough(txnDate, re)` | ≤ 2-day window |
| `amountMatches(a1, a2, pct, $)` | Percent-or-dollar tolerance |
| `merchantMatches(s1, s2, minChars)` | 5-char substring match, strips non-alphanumerics |

### 6.5 AutoCategorizer

**File:** `data/AutoCategorizer.kt` (53 lines). Top-level functions.

| Function | Purpose |
|---|---|
| `autoCategorize(imported, existing, categories, minChars=5)` | Pick most-used category from matching merchant in last 6 months; fallback to `"other"`; sets `isUserCategorized = false` |
| `sharesFiveCharSubstring(a, b, minChars=5)` (private) | 5-char sliding-window substring match |

Called only on CSV bank imports (`GENERIC_CSV`, `US_BANK`). **Not** called on manual entry.

### 6.6 CategoryIcons

**File:** `data/CategoryIcons.kt` (327 lines). Top-level declarations.

- `CATEGORY_ICON_MAP: Map<String, ImageVector>` — 120+ Material icons keyed by name.
- `getCategoryIcon(iconName): ImageVector` — lookup with default fallback.

### 6.7 SavingsSimulator

**File:** `data/SavingsSimulator.kt` (517 lines)  |  object singleton

18-month forward-looking cash flow simulation to compute the savings buffer needed to avoid going negative.

Public types:

- `SimResult(savingsRequired: Double, lowPointDate: LocalDate?)`
- `SimulationPoint(date: LocalDate, balance: Double)` — for graphing.
- `CashEvent(date, amount, priority, label)` (private) — priority: `0 = income`, `1 = period deduction`, `2 = expense`.

| Method | Purpose |
|---|---|
| `calculateSavingsRequired(...)` | Returns `SimResult` |
| `simulateTimeline(...)` | `Pair<SimResult, List<SimulationPoint>>` |
| `traceSimulation(...)` | Human-readable trace string for diagnostics |

Algorithm: horizon = `today + 18 months`. Build events: day-0 (`-availableCash`), all IS occurrences, all RE occurrences, and one period-boundary deduction per boundary (computed via `addDynamicBudgetEvents` — simulates goal accumulation, RE set-aside resets, and accelerated-RE extras forward). Sort by `(date, priority)`. Walk and track `minBalance`. Return `SimResult(roundCents(max(0, -minBalance)), minDate or null)`.

### 6.8 DefaultCategories

**File:** `data/DefaultCategories.kt` (51 lines)

`DefaultCategoryDef(tag, iconName, charted=true, widgetVisible=true)`. The built-in list:

| Tag | Icon | Notes |
|---|---|---|
| `other` | CreditCard | Protected |
| `recurring_income` | Payments | Protected |
| `supercharge` | Bolt | Protected; `widgetVisible = false` |
| `transportation` | DirectionsCar | |
| `groceries` | LocalGroceryStore | |
| `entertainment` | SportsEsports | |
| `home_supplies` | Home | |
| `restaurants` | Restaurant | |
| `charity` | VolunteerActivism | |
| `clothes` | Checkroom | |

Helpers: `getDefaultCategoryName(tag, strings)`, `getAllKnownNamesForTag(tag)` (for cross-locale rename detection).

### 6.9 FullBackupSerializer

**File:** `data/FullBackupSerializer.kt` (287 lines)

Serializes/deserializes full app state for backup and for the join-snapshot handshake. Two modes:

- `mode = "backup"` — filters out tombstones (`deleted=true`).
- `mode = "joinSnapshot"` — keeps tombstones (sync needs them).

Top-level JSON: `type`, `version=1`, `savedAt`, optional `snapshotTimestamp`, plus raw arrays for transactions, categories, RE, IS, AE, savingsGoals (legacy file name `future_expenditures.json`), periodLedger, and SharedSettings. Used by BackupManager.

### 6.10 BackupManager

**File:** `data/BackupManager.kt` (405 lines)  |  object singleton

Backup/restore to `/storage/emulated/0/Download/BudgeTrak/`. System data and photos are split into two files (`backup_<tag>_system.enc` + `backup_<tag>_photos.enc`). Photos file uses a 4-byte magic `"BKPH"` + version byte.

| Method | Purpose |
|---|---|
| `getBudgetrakDir()` | `Download/BudgeTrak/` |
| `getSupportDir()` | `Download/BudgeTrak/support/` |
| `getBackupDir()` | `Download/BudgeTrak/backups/` |
| `performBackup(context, password)` | Create system + photos backup pair (requires ≥ 50 MB free) |
| `createSystemBackup(context, password)` | System-only encrypted backup |
| `createPhotosBackup(context, password, tag)` | Photos-only encrypted backup |
| `restoreBackup(...)` | Restore from encrypted file |
| `listBackups()` | List `BackupEntry` records |

Backups use `CryptoHelper.encrypt` (password-based PBKDF2 + ChaCha20-Poly1305).

### 6.11 SafeIO

**File:** `data/SafeIO.kt` (119 lines)  |  object singleton

Crash-safe file I/O and defensive JSON parsing. Atomic write = temp file → (fsync implicit via `writeBytes`) → `renameTo`, with a per-file `ReentrantLock` guarding concurrent writes. Reads return empty/null on any failure so corruption never crashes the app.

| Method | Purpose |
|---|---|
| `atomicWrite(context, fileName, data)` | `.tmp` → rename; lock-guarded |
| `atomicWriteJson(..., JSONArray \| JSONObject)` | Delegates to atomicWrite |
| `atomicWriteLocked(...)` / `atomicWriteJsonLocked(...)` | Suspend-function shims |
| `readJsonArray(context, fileName)` | Returns `JSONArray()` on any error |
| `readJsonObject(context, fileName)` | Returns `null` on any error |
| `safeDouble(v, default=0.0)` | `NaN`/`∞` → default |

### 6.12 PrefsCompat

**File:** `data/PrefsCompat.kt` (24 lines). Extension function.

`SharedPreferences.getDoubleCompat(key, default=0.0): Double` — reads a numeric pref stored historically as String, Float, Long, or Int. Multi-level `try/catch` cascade; logs and returns `default` if all casts fail.

### 6.13 TitleCaseUtil

**File:** `data/TitleCaseUtil.kt` (55 lines). Top-level function.

`toApaTitleCase(text)` — APA-style title case. Capitalizes all words except minor words (articles, short prepositions, conjunctions); the first word always capitalizes; hyphenated parts are capitalized individually. Preserves user-typed capitalization in two heuristics:

- Short all-caps tokens (≤ 4 letters) → treat as acronyms (`USA`, `BMW`, `NASA`).
- Lowercase-first with internal capitals → user-styled (`iPhone`, `eBay`).

Longer all-caps (`DOORDASH`) still get title-cased so a stuck Caps Lock doesn't poison the merchant list.

### 6.14 ExpenseReportGenerator

**File:** `data/ExpenseReportGenerator.kt` (365 lines)  |  object singleton

Generates a multi-page PDF expense report **per transaction** using Android `PdfDocument` + `Canvas`. Letter pages (612 × 792 pt), 40-pt margins. Page 1 = expense-report form (employee info, expense details, purpose checkboxes, justification, attendees, receipt check, approval). Pages 2–6 = full-size scaled receipt photos (up to 5). Output directory: `BackupManager.getBudgetrakDir()/PDF/`, file name `expense_<yyyy-MM-dd>_<merchant>_<id>.pdf`. Called from `TransactionsScreen.kt:1849`.

### 6.15 DiagDumpBuilder

**File:** `data/DiagDumpBuilder.kt` (241 lines)  |  object singleton

Builds a diagnostic state dump **from disk** (repositories + SharedPreferences), not from live Compose state. Reusable by foreground (Settings → "Dump & Sync Debug") and background (`BackgroundSyncWorker`, `DebugDumpWorker` FCM flow).

| Method | Purpose |
|---|---|
| `build(context, simAvailableCash?)` | Loads all repos + prefs, computes `budgetAmount` / `safeBudgetAmount`, emits formatted text |
| `writeDiagToMediaStore(context, fileName, text)` | Writes to `Download/BudgeTrak/support/`; falls back to MediaStore on failure |
| `sanitizeDeviceName(name)` | Regex `[^a-zA-Z0-9]` → `_`, capped at 20 chars |

Dump sections: timestamp, deviceId, admin/sync flags, App Prefs, SharedSettings snapshot, Sync Metadata (including `catIdRemap`), Native Sync Log tail (last 50 lines of `support/native_sync_log.txt`), Categories, Recurring Expenses, Transactions (active, in current period, with link digest), Cash Verification (ledger credits vs recomputed vs stored), Period Ledger.
## 7. Sync Classes

All classes below live in package `com.techadvantage.budgetrak.data.sync` unless noted. Line counts reflect the verified source.

### 7.1 EncryptedDocSerializer (object)

Serializes data classes to/from encrypted Firestore documents using per-field encryption. Each business field is individually encrypted as `enc_<fieldName>` via `CryptoHelper.encryptWithKey` (ChaCha20-Poly1305, direct 256-bit key); metadata fields (`deviceId`, `updatedAt`, `deleted`, `lastEditBy`) are plaintext. Backward-compatible with the legacy single-blob format (key `"enc"`).

**Collection constants** (`COLLECTION_*`): `transactions`, `recurringExpenses`, `incomeSources`, `savingsGoals`, `amortizationEntries`, `categories`, `periodLedger`, `sharedSettings`. `SHARED_SETTINGS_DOC_ID = "current"`. `ALL_COLLECTIONS` — 7 entries (excludes sharedSettings).

**Per-type method family** (xxx = transaction, recurringExpense, incomeSource, savingsGoal, amortizationEntry, category, periodLedger, sharedSettings):

| Method | Purpose |
|---|---|
| `xxxToFieldMap(record, key, deviceId)` | Full encryption for Firestore `set()` |
| `xxxFromDoc(doc, key)` | Decrypt; auto-detects per-field vs legacy blob |
| `xxxFieldUpdate(changed, record, key, deviceId)` | Encrypts only changed fields for `update()` |
| `diffXxxFields(old, new)` | Compares records; returns `Set<String>` of changed field names |

**Generic dispatchers:** `docId(record)`, `collectionName(record)`, `toFieldMap(...)`, `fieldUpdate(...)`, `diffFields(...)`.

**Encryption helpers:** `encryptField(value, key)`, `decryptField(enc, key)`, plus `DocumentSnapshot.decryptString/Int/Double/Boolean` extensions and nullable variants. `isPerField(doc)` checks for `enc_id`, `enc_periodStartDate`, or `enc_currency`.

**Encrypted fields per type:**

| Type | enc_* fields | Nullable enc_* |
|---|---|---|
| Transaction | id, type, date, source, description, amount, isUserCategorized, excludeFromBudget, isBudgetIncome, amortizationAppliedAmount, linkedRecurringExpenseAmount, linkedIncomeSourceAmount, linkedSavingsGoalAmount, categoryAmounts | linkedRecurringExpenseId, linkedAmortizationEntryId, linkedIncomeSourceId, linkedSavingsGoalId, receiptId1–5 |
| Category | id, name, iconName, tag, charted, widgetVisible | — |
| IncomeSource | id, source, description, amount, repeatType, repeatInterval | startDate, monthDay1, monthDay2 |
| RecurringExpense | id, source, description, amount, repeatType, repeatInterval, setAsideSoFar, isAccelerated | startDate, monthDay1, monthDay2 |
| AmortizationEntry | id, source, description, amount, totalPeriods, startDate, isPaused | — |
| SavingsGoal | id, name, targetAmount, totalSavedSoFar, contributionPerPeriod, isPaused | targetDate |
| PeriodLedgerEntry | id, periodStartDate, appliedAmount | — |
| SharedSettings | currency, budgetPeriod, isManualBudgetEnabled, manualBudgetAmount, weekStartSunday, resetDayOfWeek, resetDayOfMonth, resetHour, familyTimezone, matchDays, matchPercent, matchDollar, matchChars, showAttribution, availableCash, incomeMode, deviceRoster, lastChangedBy, archiveThreshold | budgetStartDate, receiptPruneAgeDays, archiveCutoffDate |

**Firestore layout** `groups/{gid}/transactions/{id}`:
```
enc_id, enc_type, enc_source, enc_amount, … (all enc_* fields Base64)
deviceId, updatedAt (ServerTimestamp), deleted, lastEditBy
```

Nullable fields set to null emit `FieldValue.delete()`.

---

### 7.2 FirestoreDocService (object)

Low-level Firestore ops. No encryption logic — that's EncryptedDocSerializer. All ops use `withTimeout(OP_TIMEOUT_MS = 30_000L)`; cache reads use `5_000L`.

| Method | Purpose |
|---|---|
| `writeDoc(gid, coll, docId, data)` | `set()` full doc |
| `createDocIfAbsent(...): Boolean` | Transaction-based; period ledger first-writer-wins |
| `writeBatch(gid, coll, docs)` | Chunks of 500 via `db.batch().commit()` |
| `updateFields(gid, coll, docId, data)` | Firestore `update()` — field-level merge |
| `countActiveDocs(gid, coll): Long` | Server `count()` aggregation where `deleted == false` for the 6 soft-deletable collections. **PeriodLedger special-case:** skips the filter and counts all docs, since `PeriodLedgerEntry` has no `deleted` field (entries are immutable; no client delete path). Returns `-1L` on failure |
| `readAllDocs(gid, coll)` | Full collection read |
| `readDocIdsFromCache(gid, coll): Set<String>` | `Source.CACHE` only (zero network, zero billing); filters `deleted`; empty set on miss |
| `listenToCollection(...)` | Unfiltered `addSnapshotListener` |
| `listenToCollectionSince(...,since,...)` | `whereGreaterThan("updatedAt", since).addSnapshotListener` |
| `listenToDocument(...)` | Single-doc listener |
| `deleteDoc(...)` | Hard delete (cleanup only; regular deletes are tombstones) |

---

### 7.3 FirestoreDocSync (class)

Firestore-native sync coordinator. Manages 8 persistent listeners (7 collection + `sharedSettings/current`), per-collection `updatedAt` cursors, an `awaitInitialSync()` gate, echo prevention, enc-hash skip, diff-based field updates, and conflict detection. Listener lifecycle driven by `MainViewModel`, not `DisposableEffect`.

**Constructor:** `(context: Context, groupId: String, deviceId: String, encryptionKey: ByteArray)`.

**Companion:**

| Member | Description |
|---|---|
| `ECHO_SUPPRESS_MS = 5_000L` | Foreground echo window |
| `BG_ECHO_SUPPRESS_MS = 20 * 60 * 1000L` | Background worker echo window |
| `PERMISSION_DENIED_RESTART_COOLDOWN_MS = 30_000L` | Debounce for full restart |
| `setCursorsFromTimestamp(ctx, ms)` | Seeds every collection cursor at join time so first session is a filtered read |

**Internal state:**

| Field | Type | Notes |
|---|---|---|
| `listeners` | `ConcurrentHashMap<String, ListenerRegistration>` | One entry per of the 8 collections |
| `recentPushes` | `ConcurrentHashMap<String, Long>` | Echo suppression; 20-min background window |
| `lastSeenEnc` | `ConcurrentHashMap<String, String>` | Composite decimal hash of `enc_*` per doc; persisted to `enc_hash_cache.json` |
| `lastKnownState` | `ConcurrentHashMap<String, Any>` | For diff-based `update()` |
| `localPendingEdits` | `ConcurrentHashMap<String, Long>` | Persisted to SharedPrefs `"pending_edits"`; 1 h expiry |
| `cursorPrefs` | `SharedPreferences "sync_cursor"` | Keys `cursor_<collection>_seconds/_nanos`; saved after `onBatchChanged` applies data |
| `deliveredCollections` | `ConcurrentHashMap.KeySetView` | Drives `allDelivered: CompletableDeferred<Unit>` |
| `onBatchChanged` | `(suspend (List<DataChangeEvent>) -> Unit)?` | Always invoked on Main |
| `isListening` | `@Volatile Boolean` | |

On init: restores `enc_hash_cache.json`, restores pending edits, and loads persisted `bgPushKeys` from `sync_engine` prefs into `recentPushes` (entries within the 20-min cutoff) — then clears the pref. This makes echoes of the previous background worker's writes survive into the next listener session.

**Key methods:**

| Method | Description |
|---|---|
| `startListeners()` | Attaches all 8. Per-collection: `listenToCollectionSince(cursor)` if cursor exists, else `listenToCollection(...)`. No-ops if already listening |
| `stopListeners(graceful=false)` | Removes listeners, clears `recentPushes`; `graceful=true` skips `cancelChildren()` so late Firestore callbacks still land in `deserializeScope` |
| `awaitDeserializationComplete(timeoutMs=5_000)` | Joins `deserializeScope` children |
| `awaitInitialSync(timeoutMs=30_000): Boolean` | Suspends until all 8 collections call `markCollectionDelivered()` |
| `dispose()` | Full cleanup including `enc_hash_cache.json`, pending edits, and state caches |
| `reattachListener(collection)` | Detach + clear caches for that collection + reattach (integrity repair) |
| `pushRecord(record)` | Diffs against `lastKnownState`; `updateFields(diff)` if existing, `set()` if new, `createDocIfAbsent()` for `PeriodLedgerEntry`. Falls back to `set()` on NOT_FOUND |
| `pushRecordsBatch(records)` | `writeBatch()` full-doc for migration/bulk; populates `lastKnownState` + `lastSeenEnc` after |
| `pushAllRecords(...)` | One-time migration push — 7 collections + sharedSettings; filters tombstones |
| `triggerFullRestart()` | PERMISSION_DENIED recovery: stops all, `getAppCheckToken(true)` with 15 s timeout, delays 500 ms, restarts fresh. 30-s debounce |

**Filtered listener flow:** `attachCollectionListener()` → load cursor from `sync_cursor` prefs. Non-PERMISSION_DENIED errors: exponential backoff capped at 300 s, up to 10 tries.

**Cursors:** advance only after `onBatchChanged` completes. Use `changes` (full batch) rather than `toProcess` (post-echo-filter) so echoes in a mixed or pure batch still advance the cursor past themselves.

**Echo suppression:** `recentPushes[stateKey]` set at push time. On listener delivery the doc is dropped unless `lastEditBy != deviceId` (someone else re-edited since our push); on pure-echo batches the cursor advances without invoking `onBatchChanged`. Persisted echo keys come from `BackgroundSyncWorker.persistBackgroundPushKeys` (20-min TTL).

**Enc-hash skip:** composite decimal hash of all `enc_*` fields via `entries.sortedBy{it.key}.joinToString("|"){"${key}=${value}"}.hashCode().toString()`. Populated at both receive time and push time; persisted to `enc_hash_cache.json`.

**Conflict detection:** on listener delivery, if `lastEditBy != deviceId` and `localPendingEdits` has the key, `DataChangeEvent.isConflict = true`. If `lastEditBy == deviceId`, pending edit is cleared.

**Threading:** Firestore callbacks → `deserializeScope` (Default) for decrypt + JSON; `onBatchChanged` dispatched via `withContext(Dispatchers.Main)`; enc cache + pending edits persisted on IO.

**DataChangeEvent** (bottom of file): `collection: String`, `action: String` ("added"/"modified"/"removed"), `record: Any`, `docId: String`, `isConflict: Boolean = false`.

---

### 7.4 SyncWriteHelper (object)

Fire-and-forget push wrapper. `pushXxx()` methods for each type plus `pushTransactions/pushCategories` list variants. `pushBatch(records: List<Any>)` calls `FirestoreDocSync.pushRecordsBatch`, retries once, and on a second failure falls back to per-record `pushRecord()`. All pushes run on an internal `CoroutineScope(Dispatchers.IO + SupervisorJob())`.

| Method | Description |
|---|---|
| `initialize(docSync)` / `dispose()` / `isInitialized()` | Lifecycle |
| `pushTransaction/RecurringExpense/IncomeSource/SavingsGoal/AmortizationEntry/Category/PeriodLedgerEntry/SharedSettings(...)` | Per-record push |
| `pushTransactions(txns)` / `pushCategories(cats)` | Batched list push |
| `pushBatch(records)` | Chunks at 500 ops with retry + individual fallback |

---

### 7.5 FirestoreService (object)

Device/group management, pairing, admin claims, subscriptions, debug-file upload.

**Data classes (defined in file):**

| Class | Key fields |
|---|---|
| `DeviceRecord` | deviceId, deviceName, isAdmin, lastSyncVersion, lastSeen, photoCapable, uploadSpeedBps, uploadSpeedMeasuredAt |
| `PairingData` | groupId, encryptedKey |
| `AdminClaim` | claimantDeviceId, claimantName, claimedAt, expiresAt, votes (deviceId→"accept"/"reject"), status |
| `GroupHealthStatus` | isDissolved, subscriptionExpiry |
| `DebugFileSet` | deviceName, syncLog, syncDiag, updatedAt |

**Key methods:**

| Method | Purpose |
|---|---|
| `updateDeviceMetadata(gid, did, deviceName, syncVersion, appSyncVersion, minSyncVersion, photoCapable, uploadSpeedBps, uploadSpeedMeasuredAt)` | One-time launch write of device caps. No `lastSeen` — RTDB owns presence |
| `getDeviceRecord(gid, did)` | Single doc read; treats `removed==true` as absent |
| `getGroupHealthStatus(gid): GroupHealthStatus` | Single read returning `isDissolved` + `subscriptionExpiry`. Trusts "not exists" only from server (cache miss ≠ dissolved) |
| `isDeviceRemoved(gid, did)` | Single doc read |
| `updateGroupActivity(gid)` | Sets `lastActivity = serverTimestamp()` and `expiresAt = now + 90 d` (Firestore Timestamp). **Never use `lastActivity` as TTL** — TTL works on `expiresAt` only |
| `createPairingCode(gid, code, encryptedKey, expiresAt)` | Validates formats (`^[a-f0-9]{12}$`, `^[A-Z2-9]{6}$`); stores at `pairing_codes/{code}`. `expiresAt` written as Firestore `Timestamp(ms/1000, 0)` so TTL fires |
| `redeemPairingCode(code): PairingData?` | Reads, checks expiry (Timestamp or legacy Long), deletes on success |
| `getDevices(gid)` | List non-removed devices |
| `registerDevice/registerMembership/removeDevice/removeMembership/updateDeviceName` | Roster ops; `registerDevice` also calls `updateGroupActivity(gid)` |
| `deleteGroup(gid, onProgress)` | 6-step dissolve: (1) `status = dissolved`, (2) paginated delete of 11 subcollections (legacy `deltas`/`snapshots` left to the Cloud Function via admin SDK), (3) Storage receipts + `photoSnapshot.enc`, (4) RTDB `groups/{gid}`, (5) group doc, (6) own `members/{authUid}` |
| `createAdminClaim/getAdminClaim/castVote/resolveAdminClaim/deleteAdminClaim/transferAdmin` | Admin-transfer flow (claim TTL 24 h; transaction-based resolution) |
| `storeFcmToken/getFcmTokens` | FCM device roster |
| `uploadDebugFiles/downloadDebugFiles/requestDebugDump/getDebugRequestTime` | Encrypted debug-dump transfer (last 50 KB of log/diag each) |
| `getJoinSnapshotAge/setJoinSnapshotTimestamp/clearJoinSnapshotTimestamp` | Join-snapshot TTL reuse |
| `updateSubscriptionExpiry/` | Admin subscription write |

Subcollections deleted by dissolve: `transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger, sharedSettings, devices, imageLedger, adminClaim` (11 total; `members` handled separately).

---

### 7.6 GroupManager (object)

Group lifecycle: create, join, leave, dissolve, pairing codes, device roster.

**Data classes:**

| Class | Fields |
|---|---|
| `GroupInfo` | groupId, encryptionKey: ByteArray, isAdmin |
| `DeviceInfo` | deviceId, deviceName, isAdmin, lastSeen, online=false, photoCapable=false, uploadSpeedBps=0L, uploadSpeedMeasuredAt=0L |

`DeviceInfo` is the unified device type used by `ReceiptSyncManager`, `RealtimePresenceService.getDevices()`, and `MainViewModel`.

**Methods:**

| Method | Description |
|---|---|
| `isConfigured/getGroupId/getEncryptionKey/isAdmin/setAdmin/getDeviceName/setDeviceName` | Pref accessors. Key comes from `SecurePrefs` first, falling back to plain `sync_engine` for pre-migration |
| `createGroup(ctx): GroupInfo` | 12-char hex groupId, 256-bit key. Stores groupId/isAdmin in `sync_engine`; key in `SecurePrefs`. Sets `familyTimezone` = `TimeZone.getDefault().id` |
| `joinGroup(ctx, code): Boolean` | Redeems code, decrypts key with `normalizeCode(code)` (uppercase+trim) via `CryptoHelper.decrypt`, registers membership, then device |
| `leaveGroup(ctx, localOnly=false)` | Firestore device+membership+RTDB removal unless localOnly; clears all sync prefs; cancels `BackgroundSyncWorker` |
| `dissolveGroup(ctx, gid, onProgress)` | Delegates to `FirestoreService.deleteGroup` then `leaveGroup` |
| `generatePairingCode(ctx, gid, key): String` | 6 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no ambiguous 0/O/1/I). Encrypts key via `CryptoHelper.encrypt(key, normalizeCode(code))` — raw key never in Firestore. 10-min TTL |
| `getDevices(gid): List<DeviceInfo>` | Maps `FirestoreService.getDevices` to DeviceInfo |
| `normalizeCode(code): CharArray` | private — uppercase + trim |

---

### 7.7 SyncFilters (37 lines)

Extension properties filtering tombstoned + skeleton records:

| Extension | Filter |
|---|---|
| `List<Transaction>.active` | `!deleted && source.isNotEmpty()` |
| `List<RecurringExpense>.active` | `!deleted && source.isNotEmpty()` |
| `List<IncomeSource>.active` | `!deleted && source.isNotEmpty()` |
| `List<SavingsGoal>.active` | `!deleted && name.isNotEmpty()` |
| `List<AmortizationEntry>.active` | `!deleted && source.isNotEmpty()` |
| `List<Category>.active` | `!deleted && name.isNotEmpty()` |

---

### 7.8 SyncIdGenerator (object)

`@Synchronized fun getOrCreateDeviceId(ctx): String` — UUID, persisted in SharedPreferences `"sync_device"` at key `"deviceId"`.

---

### 7.9 PeriodLedger (73 lines)

`PeriodLedgerEntry(periodStartDate: LocalDateTime, appliedAmount: Double, corrected: Boolean = false, deviceId: String = "")`. `id: Int` derived from `periodStartDate.toLocalDate().toEpochDay().toInt()` (same date → same ID). `corrected` unused, kept for JSON back-compat.

**PeriodLedgerRepository:**

| Method | Description |
|---|---|
| `save(ctx, entries)` | Dedup first, then `SafeIO.atomicWriteJson("period_ledger.json", ...)` |
| `load(ctx)` | Parse, dedup, rewrite file if dedup shrank it |
| `dedup(entries)` | Group by epoch-day id; per group, keep entry with latest `periodStartDate` |

---

### 7.10 SecurePrefs (object)

Encrypted SharedPreferences wrapper over `androidx.security:security-crypto`.

- `get(ctx): SharedPreferences` — `@Synchronized`; creates `EncryptedSharedPreferences` (`AES256_SIV` key scheme, `AES256_GCM` value scheme) named `sync_engine_secure`. On KeyStore corruption, deletes prefs file and retries once; second failure throws `IllegalStateException("Secure storage unavailable — re-pairing required")`.
- Migration on first access: moves `encryptionKey` from plain `sync_engine` → encrypted `sync_engine_secure`, then removes from plain.

---

### 7.11 FcmService (`FirebaseMessagingService`, ~120 lines)

- `onNewToken(token)` — stores in `fcm_prefs` (`fcm_token`) and sets `token_needs_upload = true` for next Firestore push.
- `onMessageReceived(msg)` — dispatch by `msg.data["type"]`. Every arrival logs `syncEvent("FCM received: type=$type")` (visible in `token_log.txt` in debug).

Routing table (see SSD §17.15 for VM-lifecycle branching rationale, dedup, and cost):

| Type | VM alive | VM dead |
|---|---|---|
| `sync_push` | `processScope.launch { runFullSyncInline(ctx, "FCM-sync_push", null) }` | `runBlocking { runFullSyncInline(ctx, …, 8_500); if(!ok) runOnce(ctx) }` |
| `heartbeat` | same as sync_push | same as sync_push |
| `debug_request` | `processScope.launch { runDebugDumpInline(ctx, null) }` | `runBlocking { runDebugDumpInline(ctx, 8_500); if(!ok) enqueue DebugDumpWorker }` |

`debug_request` is **silently ignored in release** (`BuildConfig.DEBUG` gate). `runDebugDumpInline` sets `fcm_debug_requested = true` at entry; body clears on successful upload to dedup with the WM fallback.

---

### 7.12 FcmSender (object)

Debug-builds-only helper for sending FCM v1 data-only messages.

- `sendDebugRequest(ctx, targetFcmToken): Boolean` — guarded by `BuildConfig.DEBUG`; builds high-priority `data: {type:"debug_request", timestamp:…}` message; authenticates via OAuth2 JWT signed with the service-account private key (`SHA256withRSA`) loaded from assets; caches access token until ~1 min before expiry.
- `lastError: String?` — last failure reason, surfaced to diagnostic UI.
- Constants: `PROJECT_ID = "sync-23ce9"`, `FCM_URL = https://fcm.googleapis.com/v1/projects/.../messages:send`, `TOKEN_URL = https://oauth2.googleapis.com/token`.

---

### 7.13 DebugDumpWorker (`CoroutineWorker`)

Fallback worker triggered by FCM `debug_request` (debug builds only). **Primary path is `BackgroundSyncWorker.runDebugDumpInline` called directly from FcmService**; this worker is enqueued only when that inline path returns `false` (8.5 s budget expired before upload completed).

`doWork()` is a thin delegate: `BackgroundSyncWorker.runDebugDumpInline(applicationContext, timeBudgetMs = null)`. The actual diag-build + logcat capture + upload logic lives in the file-private `runDebugDumpBody(ctx)` in `BackgroundSyncWorker.kt` so both paths share one implementation. Body steps: (1) ensure anonymous auth, (2) skip if `fcm_prefs.fcm_debug_requested` is false, (3) build fresh dump via `DiagDumpBuilder.build(ctx)`, write to support-dir, (4) concatenate `native_sync_log.txt` + `token_log.txt`, (5) capture 1000 lines of `logcat -d` (debug only), (6) `FirestoreService.uploadDebugFiles(...)`, (7) clear the flag on success.

---

### 7.14 BackgroundSyncWorker (`CoroutineWorker`)

> **Routing semantics, slim-vs-full Tier 3 decisions, the network-awareness gate, the Tier 2 receipt-sync VM propagation, and the cancellation/diagnostic-log behaviour all live in SSD §17.13.** This LLD entry is the call-table only.

| Surface | Purpose |
|---|---|
| `WORK_NAME = "period_refresh"` | 15-min `PeriodicWorkRequest` (KEEP policy), sync users only |
| `ONESHOT_WORK_NAME = "period_refresh_oneshot"` | FCM / wake fallback, expedited on API 31+ |
| `BOUNDARY_WORK_NAME = "period_boundary_oneshot"` | Phase 3 solo path (one-shot per period boundary) |
| `schedule(ctx)` | Branches on `isSyncConfigured`: sync → periodic, solo → `scheduleNextBoundary`. Cancels the opposite path. |
| `runOnce(ctx)` | One-shot via `ONESHOT_WORK_NAME`. Used as fallback when `runFullSyncInline` returns false. |
| `cancel(ctx)` | Cancels both `WORK_NAME` and `BOUNDARY_WORK_NAME`. |
| `scheduleNextBoundary(ctx)` | Computes next period boundary via `PeriodRefreshService.nextBoundaryAt(...)`, enqueues a one-shot via `BOUNDARY_WORK_NAME` (REPLACE, expedited, clamped 60 s–24 h). Self-rearms at end of every solo run. |
| `runFullSyncInline(ctx, sourceLabel, timeBudgetMs?): Boolean` | Sole owner of Tier 1/2/3 routing. Returns `true` only when real work was done; offline-skipped runs return `false`, and only `workDone && sourceLabel.startsWith("FCM-")` stamps `KEY_LAST_INLINE_AT`. Guarded by file-static `isRunning: AtomicBoolean`. |
| `runDebugDumpInline(ctx, timeBudgetMs?)` | FCM `debug_request` analogue of `runFullSyncInline`. |
| `runFullSyncBody / runTier2 / runTier3` (file-private) | Three-tier routing impl, all return `Boolean`. Each early-returns `false` on `!NetworkUtils.isOnline(context)` (and on Tier 1 app-active skip / sync-not-configured skip). Tier 2 captures `syncReceipts(txns, devices)`'s returned list and applies changed `receiptId1..5` back to `vm.transactions` on Main. |
| `resolveDevicesForReceiptSync(ctx, groupId, vm)` | Four-tier fallback resolver: VM `syncDevices` → RTDB presence → Firestore `groups/{gid}/devices/*` → SharedPref cache. Cold-start Tier 3's RTDB read returns empty before auth handshake; the cache (populated by every successful resolution) covers that gap. |

**`doWork()` is a thin wrapper:** `try { runFullSyncInline(applicationContext, "Worker", null) } catch (ce: CancellationException) { syncEvent(stopReason); throw ce }`. Other exceptions are caught and converted to `Result.success()` so the next scheduled run isn't penalized.

---

### 7.15 WakeReceiver (`BroadcastReceiver`, 39 lines)

Manifest-registered for `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED`. `RATE_LIMIT_MS = 5 * 60 * 1000L` enforced via SharedPreferences `wake_receiver.last_wake`. On allow, logs `syncEvent("WakeReceiver fired ($intent.action), enqueueing runOnce")` and calls `BackgroundSyncWorker.runOnce(context)` (expedited on API 31+ per 7.14). Rationale: Samsung process-death mitigation — charger events correlate with user activity and opportunistically refresh the widget before the 15-min tick.

---

### 7.16 RealtimePresenceService (object)

Firebase RTDB presence. Path: `groups/{gid}/presence/{did}`.

`PresenceRecord(deviceId, online, deviceName, lastSeen, photoCapable=false, uploadSpeedBps=0L, uploadSpeedMeasuredAt=0L)`.

| Method | Description |
|---|---|
| `setupPresence(gid, did, deviceName, photoCapable, uploadSpeedBps, uploadSpeedMeasuredAt)` | Listens on `.info/connected`; on connect writes `{online:true, deviceName, lastSeen: ServerValue.TIMESTAMP, photoCapable, uploadSpeedBps, uploadSpeedMeasuredAt}` and registers `onDisconnect().setValue({online:false, …})`. Covers crash/network loss server-side |
| `listenToGroupPresence(gid, callback)` | `ValueEventListener` on `groups/{gid}/presence` delivering full list per change |
| `getDevices(gid): List<DeviceInfo>` | One-shot suspend read; returns DeviceInfo list. Used by `BackgroundSyncWorker` Tier 3 |
| `deletePresenceNode(gid, did)` | Single delete on leave |
| `deleteGroupPresence(gid)` | Delete `groups/{gid}` (dissolve) |
| `cleanup()` | Cancel `onDisconnect`, remove listeners, null refs |

`getDatabase()` tolerates missing RTDB config (no `firebase_url`) — returns null and all methods no-op.

---

### 7.17 ReceiptManager (object)

Local receipt photos: capture, downsize, encrypt, store, thumbnails, and the pending-upload queue.

Constants: `MAX_IMAGE_DIMENSION = 1000`, `THUMBNAIL_SIZE = 200`, `TARGET_BYTES_PER_MEGAPIXEL = 250 * 1024`, `PENDING_QUEUE_FILE = "pending_receipt_uploads.json"`, `RECEIPTS_DIR = "receipts"`, `THUMBS_DIR = "receipt_thumbs"`.

| Method | Description |
|---|---|
| `loadPendingUploads/savePendingUploads` | JSON array of receiptIds, synchronized on `pendingQueueLock` |
| `addToPendingQueue/removeFromPendingQueue` | Atomic queue ops |
| `generateReceiptId()` | `UUID.randomUUID().toString()` |
| `processAndSavePhoto(ctx, uri)` / `processAndSaveFromCamera(ctx, tempUri)` | Downsize, encrypt, write to `receipts/{id}.jpg` (encrypted) + thumb |
| `loadThumbnail(ctx, id)` / `loadFullImage(ctx, id)` | Decrypt + return Bitmap |
| `deleteLocalReceipt(ctx, id)` / `deleteReceiptFull(ctx, id)` | Local only / local + queue |
| `encryptForUpload(ctx, id, key)` / `decryptAndSave(ctx, id, bytes, key)` | Upload/download encryption endpoints |
| `getTotalStorageBytes(ctx)` / `cleanOrphans(ctx, allReceiptIds)` | Storage hygiene |
| `collectAllReceiptIds(transactions)` | Union of non-null `receiptId1..5` across txns |
| `getReceiptIds(t)` / `nextEmptySlot(t)` / `clearReceiptSlot(t, id)` | Slot helpers |

---

### 7.18 ImageLedgerEntry (27 lines)

```
ImageLedgerEntry(receiptId, originatorDeviceId, createdAt,
    possessions: Map<String, Boolean>,  // deviceId → true/false (key absent = unknown)
    uploadAssignee: String?, assignedAt: Long, uploadedAt: Long,
    contentVersion: Long = 0L,
    lastEditBy: String? = null)         // upload race-detection

SnapshotLedgerEntry(requestedBy, requestedAt, builderId, builderAssignedAt,
    status, progressPercent, errorMessage, lastProgressUpdate,
    snapshotReceiptCount, readyAt, consumedBy: Map<String, Boolean>)
```

`possessions` is three-state: `true` / `false` / key-absent. `lastEditBy` is set by `createLedgerEntry`, `incrementContentVersion`, `createRecoveryRequest`, `resetEntryToRecoveryRequest`, `markReuploadComplete` — used by `processPendingUploads` resume-detection (see §7.20) and by `onImageLedgerWrite` writer-skip filter (see §7.26.2a).

---

### 7.19 ImageLedgerService (object)

Firestore CRUD for `groups/{gid}/imageLedger/*` and Cloud Storage for `groups/{gid}/receipts/{rid}.enc`. `SNAPSHOT_DOC_ID = "_snapshot_request"` (single underscore — double-underscore was a Firestore reserved ID). UUID regex gate on receiptIds.

| Method | Description |
|---|---|
| `uploadToCloud(gid, rid, bytes): Boolean` | 60 s timeout; updates `lastUploadError` on failure |
| `downloadFromCloud(gid, rid): ByteArray?` | 30 s timeout, 2 MB max. Rethrows `CancellationException` cleanly so Samsung-canceled workers don't waste subsequent suspension points |
| `getFlagClock(gid): Long` + `getLedgerEntry(gid, rid)` | Same 30 s timeout; rethrow `CancellationException` instead of swallowing as generic Exception |
| `existsInCloud(gid, rid)` / `deleteFromCloud(gid, rid)` | Metadata read / hard delete |
| `purgeOrphanedCloudFiles(gid): Int` | Lists cloud + reads full ledger; deletes files with no ledger entry and >10 min old |
| `createLedgerEntry(gid, rid, originatorDeviceId)` | After successful upload of a fresh receipt; writes `contentVersion = 0` and stamps `lastEditBy = originatorDeviceId` so `processPendingUploads` can recognize a partial-commit resume (see §7.20). **Does NOT bump flag clock** — peers discover via transaction-sync `onBatchChanged`, and pruning is triggered inline at every download site |
| `incrementContentVersion(gid, rid, editingDeviceId)` | After rotation / edit re-upload: batch-writes `possessions = {editor: true}`, `uploadedAt = now`, `contentVersion += 1`, `lastEditBy = editingDeviceId`, bumps flag clock. Cloud Function `onImageLedgerWrite` fires `sync_push` to all non-writer peers; their BG worker kicks `syncReceipts()` which invalidates stale local copies via `lastSeenContentVersion` mismatch |
| `createRecoveryRequest(gid, rid, originatorDeviceId, preserveContentVersion = 0L)` | File lost; empty `possessions`; bumps flag clock. Stamps `lastEditBy = originatorDeviceId`. `preserveContentVersion` keeps the monotonic counter across a `deleteLedgerEntry → createRecoveryRequest` recovery cycle so stale peers still invalidate through future rotations. Cloud Function pushes peers to consider re-uploading |
| `resetEntryToRecoveryRequest(gid, rid, requestingDeviceId, fallbackContentVersion)` | Atomic Firestore transaction: reads existing entry (if any), rewrites to recovery-request state (`uploadedAt=0, possessions={}`, `uploadAssignee=null`, `assignedAt=0`, `lastEditBy=requester`), preserves existing `contentVersion` + `createdAt`, bumps flag clock in the same transaction. Used by `downloadReceiptWithRetry` on the 3rd real failure (a `delete + create` pair would race concurrent re-uploaders and reset the version counter). |
| `markPossession(gid, rid, did)` / `markNonPossession(gid, rid, did)` | Dot-notation `update("possessions.$did", true/false)` |
| `checkPhotoLost(gid, rid, photoCapableDeviceIds): Boolean` | Transaction: confirms permanent loss when all photo-capable devices have `false` and deletes ledger entry |
| `pruneCheckTransaction(gid, rid, allDeviceIds): Boolean` | Transaction: if all devices possess it, delete ledger + Cloud Storage. Called inline inside `downloadReceiptWithRetry` and the `processLedgerOperations` "have-it" branch |
| `getLedgerEntry/getFullLedger/deleteLedgerEntry` | CRUD |
| `getFlagClock(gid)` / `bumpFlagClock(gid)` | Flag-clock polling primitive |
| `claimUploadAssignment(gid, rid, myDid, expectedAssignee, expectedAssignedAt)` | CAS transaction |
| `markReuploadComplete(gid, rid, reuploaderDeviceId)` | Sets `uploadedAt` + `lastEditBy = reuploaderDeviceId`; bumps flag clock. Cloud Function fires `sync_push` to non-writer peers so waiting requester downloads immediately |
| `getCleanupState(gid): CleanupState` | Reads `imageLastCleanupDate` from group doc (`CleanupState(lastCleanupDate: String?)`) |
| `markCleanupDone(gid, todayDate)` | Plain `update("imageLastCleanupDate", todayDate)` — no CAS, idempotent |
| Snapshot ops | `getSnapshotEntry/createSnapshotRequest/claimSnapshotBuilder/updateSnapshotStatus/markSnapshotConsumed/deleteSnapshotEntry/uploadSnapshotArchive/downloadSnapshotArchive/uploadJoinSnapshot/downloadJoinSnapshot/deleteJoinSnapshot/deleteSnapshotArchive` |

---

### 7.20 ReceiptSyncManager (class)

Coordinates receipt photo sync. Paid devices only. Constructor: `(context, groupId, deviceId, encryptionKey, syncLog: (String)->Unit = {})`.

Constants: `STALE_ASSIGNMENT_MS = 5 min`, `FOURTEEN_DAYS_MS`, `MAX_DOWNLOAD_RETRIES = 3`, `SPEED_STALENESS_MS = 24 h`, `SNAPSHOT_THRESHOLD = 50`, `SNAPSHOT_STALE_MS = 2 h`, `BATCH_RECOVERY_CAP = 50`, `SNAPSHOT_GRACE_PERIOD_MS = 5 min`, `SNAPSHOT_MAGIC = "SNAP"`.

**Public entry points** (called from `MainViewModel` foreground drainers and `BackgroundSyncWorker` Tier 2/3):
- `syncReceipts(transactions, allDevices): List<Transaction>` — full 5-step pipeline below. Returns the (potentially modified) transaction list so callers can propagate changes back to in-memory state. `BackgroundSyncWorker.runTier2` uses this to apply `clearLostReceiptSlot`'s nulled `receiptId*` fields back to `vm.transactions` on Main — required because the Firestore listener's echo filter (`lastEditBy == ourDeviceId`) would otherwise keep in-memory state stale until app restart, leaving phantom photo frames in open dialogs. Tier 3 calls it too but discards the return (VM is dead; next launch loads from disk). Phase-boundary `syncEvent` logs (`syncReceipts START/step1/step2/step3/step3b/step4/END`, with elapsed-ms on END) and a `CANCELLED after Nms` log on `CancellationException` (rethrown). Routes through all `ReceiptSyncManager` construction sites' persistent log channel (Tier2/Tier3/SyncNow/onBatch/UploadDrainer/FgRetry). `processPendingUploads` and `processRecovery` early-return + log when `!NetworkUtils.isOnline(context)`.
- `processPendingUploads(): Int` — drains upload queue in chunks of 5; returns # completed. Used by `MainViewModel.kickUploadDrainer` in a backoff loop. After a successful Cloud Storage upload, applies the three-way ledger decision (fresh / partial-commit resume / real rotation) — see SSD §18.5 *Rotation / edit propagation via `contentVersion`*.
- `downloadReceiptWithRetry(receiptId, photoCapableDeviceIds): Boolean` — single-receipt download + save + `markPossession` + `pruneCheckTransaction` + retry counter. On 3rd real failure with `uploadedAt > 0`, resets the ledger entry to a recovery request. Used by `onBatchChanged` fast path, `kickFgDownloadRetry` coroutine, and `processRecovery`.
- `bumpLocalContentVersionForRotation(ctx, receiptId)` (companion) — increments `receipt_sync_prefs/content_version_<receiptId>` by 1. Called from `ReceiptManager.replaceReceipt` to mark a rotation as pending before queueing.

**`syncReceipts` — 5 steps:**
1. `processPendingUploads` — upload-first: encrypt → upload → create-or-bump ledger entry per the three-way logic above.
2. `processLedgerOperations` — flag-clock check + ledger cache. Handles (a) re-upload requests when we have the file, (b) non-possession marking + `checkPhotoLost` for recovery requests, (c) `markPossession` + `pruneCheckTransaction` for entries we already have locally. **Does not download** — downloads route through `processRecovery` or the foreground paths.
3. `processRecovery` — missing local files referenced in transactions. Delegates per-receipt to `downloadReceiptWithRetry`; if no ledger entry exists at all, creates one (recovery request). Re-uploader selected by online filter + fastest `uploadSpeedBps` in last 24 h + `abs(hash(receiptId+deviceId)) % 1000` tiebreak.
4. `processSnapshotLifecycle` — build/download snapshot archives when ≥ 50 missing (also used by join). `buildSnapshot` / `processSnapshotDownload` rethrow `CancellationException` separately and leave status `"building"` on cancel — see SSD §18.5 *Snapshot cancellation handling*.
5. `processStalePruning` — 14-day cleanup, noon trigger, local 24 h skip gate (`lastStalePruneRun`) + group `imageLastCleanupDate` check, plain `markCleanupDone` write (no CAS; idempotent).

Foreground polls `imageLedgerFlagClock` (single field on the group doc) — **not** a dedicated listener. Transaction-arrival downloads are driven by the business collection listener. See SSD §18.5 for the full four-layer architecture.

---

### 7.21 SyncMergeProcessor (object)

Stateless merge. Used by both `MainViewModel.onBatchChanged` and `BackgroundSyncWorker`.

**MergeResult:** `transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger: List<T>?` (null = unchanged), `sharedSettings: SharedSettings?`, `conflictDetected`, `conflictedTransactionsToPushBack`, `categoriesToDeleteFromFirestore: List<Int>`, `settingsPrefsToApply: Map<String,Any>?`, `archivedIncoming: List<Transaction>`.

**`processBatch(events, currentTransactions, currentRecurringExpenses, currentIncomeSources, currentSavingsGoals, currentAmortizationEntries, currentCategories, currentPeriodLedger, currentSharedSettings, catIdRemap: MutableMap<Int,Int>, currentBudgetStartDate: LocalDate?, archiveCutoffDate: LocalDate? = null)`:**

1. Works on mutable copies; pre-builds id → index maps for O(1) lookup.
2. **Transactions:** if `isConflict`, set `isUserCategorized=false` and queue for push-back. If `archiveCutoffDate != null && txn.date < cutoff`, route to `archivedIncoming`; else add-or-replace.
3. **Categories:** tag-based dedup — if another local category owns this tag, remap `catIdRemap[remoteId] = localId`, remap all txn `categoryAmounts.categoryId`, queue `remoteId` for Firestore delete; otherwise add-or-replace.
4. **RE / IS / SG / AE / PLE:** add-or-replace by id.
5. **SharedSettings:** builds pref map; if `syncedBudgetStartDate != currentBudgetStartDate`, includes `budgetStartDate` + refreshed `lastRefreshDate = LocalDate.now()`.
6. Result lists are non-null only when that collection was touched.

---

### 7.22 PeriodRefreshService (`data/PeriodRefreshService.kt`)

Package `com.techadvantage.budgetrak.data`. Shared period-refresh logic, used by foreground ViewModel and `BackgroundSyncWorker`.

**RefreshConfig:** budgetStartDate, lastRefreshDate, budgetPeriod, resetHour, resetDayOfWeek, resetDayOfMonth, familyTimezone, localDeviceId, incomeMode, isManualBudgetEnabled, manualBudgetAmount, carryForwardBalance=0.0, archiveCutoffDate=null.

**RefreshResult:** newLedgerEntries, updatedSavingsGoals, updatedRecurringExpenses, newLastRefreshDate, newCash.

**`@Synchronized refreshIfNeeded(ctx, config): RefreshResult?`:**
1. Compute "today" with resetHour shift for DAILY.
2. Resolve `currentPeriod` via `BudgetCalculator.currentPeriodStart` (timezone-aware).
3. `missedPeriods = BudgetCalculator.countPeriodsCompleted(lastRefreshDate, currentPeriod, budgetPeriod)`. Return null if ≤ 0.
4. Load all data from disk.
5. For each missed period: compute `budgetAmount` from current SG/RE state, create/dedup `PeriodLedgerEntry`, accrue SG `totalSavedSoFar`, accrue RE `setAsideSoFar`.
6. Save changed data via repositories.
7. `BudgetCalculator.recomputeAvailableCash(...)` → update `availableCash` and `lastRefreshDate` in `app_prefs`.
8. Return `RefreshResult` with only changed records.

---

### 7.23 DiagDumpBuilder (`data/DiagDumpBuilder.kt`)

Package `com.techadvantage.budgetrak.data`. Generates diagnostic text dumps from disk (SharedPrefs + JSON repos) — usable from any worker.

`build(ctx, simAvailableCash: Double? = null): String` — loads all data, parses prefs, computes derived values (safe budget amount, full budget amount, recomputed cash, ledger credits), and formats a plain-text report suitable for encrypted upload.

---

## 7.24 Consistency, Cursors, and Cold-Start Summary

**Per-collection cursors** in SharedPreferences `sync_cursor` — one (seconds, nanos) pair per collection. Saved after `onBatchChanged` applies data (so a crash between data apply and cursor save causes harmless re-read on next launch). Fresh install → no cursor → unfiltered read.

**Echo handling:** `recentPushes[collection:docId]` kept for 5 s (foreground) / 20 min (background). Filter keeps entries where `lastEditBy != deviceId`. Pure-echo batches still advance the cursor so a later fresh listener doesn't re-deliver. `BackgroundSyncWorker.persistBackgroundPushKeys` persists worker pushes to `sync_engine.bgPushKeys`; next `FirestoreDocSync.init` loads them into `recentPushes`.

**Initial sync gate:** `awaitInitialSync(30_000 ms)` suspends until all 8 listeners have called `markCollectionDelivered()` at least once — even for empty filtered results. Used to defer migrations and period refresh on cold start.

**Consistency check** in `MainViewModel.runPeriodicMaintenance()` (24-hour gate):
- **Layer 1 (counts):** `FirestoreDocService.countActiveDocs` vs `local.active.size` per collection. Mismatch → clear cursor → full re-read. Logs `CONSISTENCY_COUNT_MISMATCH`.
- **Layer 2 (cashHash majority vote):** `cashHash = availableCash.toString().hashCode().toString(16)` — **hex digest** at `MainViewModel.kt:835`. Stored in group `deviceChecksums`. ≥3 devices → majority vote, minority re-reads. 2 devices → both re-read on confirmed mismatch. 1-hour confirmation gate via `app_prefs.checksumMismatchAt`.

**Integrity check:** runs at startup and periodically (24 h gate). Uses `Source.CACHE` (zero network). Pushes local-only records, then `recomputeCash()`.

---

## 7.25 App Check

- **Provider:** `DebugAppCheckProviderFactory` (debug) / `PlayIntegrityAppCheckProviderFactory` (release), switched by `BuildConfig.DEBUG` in `BudgeTrakApplication.onCreate`.
- **Token TTL is provider-dependent**:
  - Play Integrity (release): 40 h, set via Firebase Console → Project Settings → Your apps → BudgeTrak Android → App Check section dropdown.
  - Debug provider: **always 1 h, ignores Console setting** by design (Google-imposed for short-lived dev tokens). Means debug-build refresh cadence is 40× higher than release.
- **Debug token:** extracted from logcat on startup, written to `token_log.txt`, included in FCM dump uploads.
- **Play Integrity advanced settings:** `PLAY_RECOGNIZED` required (anti-piracy — blocks modified/re-signed APKs), `LICENSED` not required (don't gatekeep free users on Huawei / degooglified devices), device integrity = "Don't explicitly check" (relies on `PLAY_RECOGNIZED` + per-field encryption for actual data protection).
- **Authentication enforcement:** Monitor (not Enforce). Anonymous Auth must always succeed for sync setup; downstream Firestore/RTDB/Storage rules enforce App Check, so a bot-acquired anonymous UID can't actually do anything.
- **All `getAppCheckToken()` calls wrapped with `withTimeoutOrNull(10–15 s)`.**
- **Refresh triggers** (all gated by `isSyncConfigured`):
  - `onResume`, `onAvailable` network callback
  - `BackgroundSyncWorker` Tier 2/3 proactive (16-min threshold — server heartbeats are reliable enough that one per ~4 h Play Integrity cycle catches the refresh, the rest skip)
  - `FirestoreDocSync.triggerFullRestart()` on PERMISSION_DENIED
  - `MainViewModel` keep-alive loop (45-min check, 16-min refresh — VM is the backup if Worker is silenced >45 min by Doze)
  - SDK auto-refresh (~5 min before expiry, in-process only).

---

## 7.26 Cloud Functions (`functions/index.js`)

All three functions deployed via `firebase deploy --only functions` to `sync-23ce9`. v1 API (`firebase-functions` 5.1.x + `firebase-admin` 12.7.x, Node.js 22 per `functions/package.json`).

### 7.26.1 `cleanupGroupData` — Firestore onDelete

Triggered by `onDelete` of `groups/{groupId}`. Cascade:

1. Paginated delete (pageSize 500) of 14 subcollections: `transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger, sharedSettings, devices, members, imageLedger, adminClaim, deltas, snapshots` (the last two are legacy — may exist on old groups).
2. RTDB `groups/{gid}` remove.
3. Cloud Storage `groups/{gid}/*` delete (receipts + snapshot archive).

Client `FirestoreService.deleteGroup` deletes the 11 active subcollections itself; the Function handles anything left plus legacy deltas/snapshots.

### 7.26.2 `onSyncDataWrite` — Firestore onWrite

Triggered by `onWrite` on `groups/{groupId}/{collection}/{docId}`. Filters collection name against `SYNC_PUSH_COLLECTIONS` (the 8 sync-data collections — transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger, sharedSettings). Skips deletes (`!change.after.exists`).

Flow:
1. Read `lastEditBy` (fallback `deviceId`) from the new doc — the writer to exclude from fan-out.
2. `collectRecipientTokens(gid, writerDeviceId)` walks `groups/{gid}/devices`, returning `fcmToken` for every device where `removed != true` and the device ID isn't the writer. **Defense-in-depth writer validation:** before building the token list, the helper searches the same snapshot for `writerDeviceId`; if the writer isn't found or is flagged `removed`, the helper returns `[]` and the fan-out is suppressed. Catches Firestore-rule regressions where a non-member write could trigger group-wide FCM spam.
3. `sendFcm(tokens, {type:"sync_push", collection, groupId}, "sync_push")` — chunks at 500 tokens per `sendEachForMulticast`, `android.priority = "high"`, logs per-token failures.

Purpose: cross-device sync in near-real-time despite Android Doze / App-Standby bucket restrictions on peer devices. Client receiver in §7.11.

### 7.26.2a `onImageLedgerWrite` — Firestore onWrite

Triggered by `onWrite` on `groups/{groupId}/imageLedger/{receiptId}`. `imageLedger` is intentionally NOT in `SYNC_PUSH_COLLECTIONS` because most of its writes are bookkeeping chatter (`markPossession`, `markNonPossession`, `pruneCheckTransaction` deletions) that peers don't need to react to. This trigger applies a content filter so only meaningful writes fan out.

Filter — fire `sync_push` only when one of:
- **Rotation**: `after.contentVersion > before.contentVersion`.
- **Recovery re-upload complete**: `before.uploadedAt === 0 && after.uploadedAt > 0`.
- **Recovery request created**: `!before.exists && after.uploadedAt === 0`.

Skipped:
- Fresh `createLedgerEntry` — already covered by the concurrent `onSyncDataWrite` push on the `transactions` collection (peer's `onBatchChanged` fast-path downloads the photo).
- Possession updates (`markPossession`, `markNonPossession`) — informational.
- Prune / deletion — transaction-level changes already propagate.
- Snapshot-request doc (`_snapshot_request` docId) — uses its own signaling.

Writer skipped via `after.lastEditBy` (client writes `incrementContentVersion`, `createRecoveryRequest`, `markReuploadComplete` stamp this field).

Peers' `BackgroundSyncWorker.runOnce` uses `enqueueUniqueWork(KEEP)` so bursts (e.g. batch rotation) collapse into one `syncReceipts()` run per peer.

### 7.26.2b `presenceOrphanCleanup` — scheduled

Weekly pub/sub (`every sunday 03:00 UTC`). Walks every group's RTDB presence node, bulk-fetches the corresponding Firestore `devices/{deviceId}` via `db.getAll(...refs)`, and removes any RTDB presence entry whose matching Firestore device is absent or `removed = true`. Mitigates the RTDB presence write gap (rules allow any authenticated user to write presence; can't cross-reference Firestore to enforce membership). Orphan presence can't escalate to FCM spam — `presenceHeartbeat` gates FCM sends via Firestore `devices/{id}/fcmToken` which requires `isMember` — but it can bloat the RTDB node and slow `RealtimePresenceService.getDevices()` for legitimate users.

Counters logged: `groupsChecked`, `totalPruned`.

**Scale caveat:** sequential per-group walk, same O(n) concern as `presenceHeartbeat`. Fine at current scale; upgrade alongside the heartbeat (tracked in `project_prelaunch_todo.md`).

### 7.26.3 `presenceHeartbeat` — scheduled

Pub/Sub schedule `every 15 minutes` (UTC). Walks all groups in Firestore; for each, reads RTDB `groups/{gid}/presence`; collects `deviceId`s whose `lastSeen < now − 15 min`; calls `tokensForDevices(gid, staleIds)` → `sendFcm(tokens, {type:"heartbeat", groupId}, "heartbeat")`.

Purpose: backstop when Android stops scheduling the periodic `BackgroundSyncWorker` (observed up to 4h46m worker silence on Samsung devices in App-Standby Bucket `restricted`).

**Scale caveat:** the current implementation walks groups sequentially. At ~50 ms/group the 60 s default timeout is hit at ~1.2K groups and the 9-min Gen-1 ceiling at ~10K. Migration to an indexed presence query (tracked in `memory/project_prelaunch_todo.md` #7) eliminates the loop entirely.

### 7.26.4 Shared helpers

- `collectRecipientTokens(gid, writerDeviceId)` — reads `groups/{gid}/devices` subcollection.
- `tokensForDevices(gid, deviceIds[])` — per-device lookup.
- `sendFcm(tokens, data, label)` — chunked multicast with per-batch failure logging.

---

## 7.26a Telemetry — Crashlytics + Firebase Analytics + BigQuery

**SDK initialization** (`BudgeTrakApplication.onCreate`): both Crashlytics and Analytics share the `crashlyticsEnabled` SharedPref toggle (default true). UI label: "Send crash reports and anonymous usage data". `setAnalyticsCollectionEnabled(crashlyticsEnabled)` runs at app start; toggle changes in `MainActivity` UI propagate immediately.

**Crashlytics events:** non-fatals via `BudgeTrakApplication.recordNonFatal(tag, message, e?)`. Sites: `PERMISSION_DENIED` recovery, consistency mismatch, `TOKEN_REFRESH_TIMEOUT` (worker + ViewModel keep-alive 15 s timeouts). Custom keys via `updateDiagKeys(map)`: `cashDigest, listenerStatus, lastRefreshDate, activeDevices, txnCount, reCount, plCount, lastTokenExpiry, authAnonymous`.

**Analytics events** (`data/telemetry/AnalyticsEvents.kt`):
- `logHealthBeacon(ctx, listenerUp, activeDevices, txnCount, reCount, plCount)` — daily sync-user heartbeat from `runPeriodicMaintenance` (24 h gate).
- `logOcrFeedback(ctx, merchantChanged, dateChanged, amountDeltaCents, catsAdded, catsRemoved, hadMultiCat)` — fires on save of an OCR-populated transaction. Anonymous deltas/booleans only — no merchant text or amounts. Measures per-field user-correction rate.

Both gated by `isEnabled(ctx)` reading the same `crashlyticsEnabled` pref. Modern Firebase Analytics SDK (BoM 32.x) auto-discovers measurement ID at runtime via `mobilesdk_app_id`; `google-services.json` doesn't need an explicit `analytics_service` block. The Firebase project must be linked to a GA4 property in Project Settings → Integrations → Google Analytics — without it, the SDK accepts events but they are silently dropped.

**BigQuery export** (Firebase Console → Project Settings → Integrations → BigQuery): Crashlytics, Performance Monitoring, Sessions, and `analytics_<propertyId>` all stream into `com_techadvantage_budgetrak_ANDROID*` with Daily + Streaming and no advertising IDs.

**Query helper** `tools/query-crashlytics.js`: auth via `~/.config/configstore/firebase-tools.json` refresh token — periodically expires with `rapt_required` error, requires `firebase login --reauth` (interactive only).

---

## 7.27 AndroidManifest Entries Relevant to Sync

- `<service android:name=".data.sync.FcmService">` with `MESSAGING_EVENT` intent filter.
- `<receiver android:name=".data.sync.WakeReceiver" android:exported="true">` with `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` intent filter.
- Single permission: `android.permission.INTERNET`.

---

## 8. Theme Classes (`ui/theme/`)

### 8.1 Theme.kt (665 lines)

Owns the app theme, color composition locals, the ad-aware dialog stack, and shared scroll/toast helpers.

**Color composition:**
- `SyncBudgetColors` data class — semantic palette (headerBackground, headerText, cardBackground, cardText, displayBackground, displayBorder, userCategoryIconTint, accentTint).
- `LocalSyncBudgetColors` — composition local, default dark; overridden per Material scheme by `SyncBudgetTheme`.
- `LocalAdBannerHeight` — `0.dp` paid users, `50.dp` free users (drives dialog bottom padding).
- `LocalAppToast` — global `AppToastState` instance for in-dialog toasts.

**Dialogs:**
- `AdAwareDialog` — base dialog that shifts content above the ad banner and mounts `AppToast` overlay.
- `AdAwareAlertDialog` — header/body/footer alert wrapper with `DialogStyle` + `PulsingScrollArrow`.
- `AdAwareDatePickerDialog` — Material3 date picker wrapped in `AdAwareDialog`.
- `DialogStyle` enum — `DEFAULT` (green) / `DANGER` (red) / `WARNING` (orange); drives header + footer colors.
- `DialogHeader(title, style)` / `DialogFooter(content)` — composables for custom form dialogs that use `AdAwareDialog` directly.
- `DialogPrimaryButton` (green) / `DialogSecondaryButton` (gray) / `DialogDangerButton` (red) / `DialogWarningButton` (orange) — 500 ms click debounce on primary/danger/warning buttons.

**Scroll / toast:**
- `PulsingScrollArrow(scrollState)` — animated chevron appears when content scrolls.
- `AppToastState` — `show(msg, windowYPx, durationMs=2500L)`; rendered by `AppToast` inside `AdAwareDialog`.

**`SyncBudgetTheme(darkTheme, content)`** — entry point. Resolves `SyncBudgetColors` for the scheme, provides all four composition locals, applies `SyncBudgetTypography`.

### 8.2 Color.kt (29 lines)

Raw `Color(0xAARRGGBB)` constants only. Two palettes:

| Token | Dark | Light |
|---|---|---|
| Background | `0xFF2A3A2F` | `0xFFBDD5CC` |
| Surface | `0xFF1A1A1A` | `0xFFFFFFFF` |
| HeaderBackground | `0xFF1E2D23` | `0xFF2C2C2C` |
| HeaderText | `0xFFE0E0E0` | `0xFFF0E8D8` |
| Primary | `0xFFE8D5A0` | `0xFF2E5C80` |
| OnPrimary | `0xFF1A1A1A` | `0xFFFFFFFF` |
| CardBackground | `0xFF1A1A1A` | `0xFF305880` |
| CardText | `0xFFE8D5A0` | `0xFFFFFFFF` |
| DisplayBackground | `0xFF383838` | `0xFFD6E5DE` |
| DisplayBorder | `0xFF4A4A4A` | `0xFFB8CCC2` |

Light theme also defines `LightOnBackground` / `LightOnSurface` (`0xFF1C1B1F`).

### 8.3 Type.kt (18 lines)

- `FlipFontFamily = FontFamily.Monospace` — used by every `FlipChar` / `FlipDigit` / `FlipDisplay`.
- `SyncBudgetTypography` — one `headlineLarge` style (24sp bold, 2sp letter-spacing).

---

## 9. Localization Classes (`ui/strings/`)

### 9.1 Architecture

A single Kotlin interface holds every user-facing string. Two concrete `object`s (English, Spanish) implement it. A `CompositionLocal` injects one at the root of the tree.

- `val S = LocalStrings.current` is the only read path composables use.
- Changing `appLanguage` swaps the provided object inside `CompositionLocalProvider` — no restart, no Android resource reload.
- "SYNC" renders all-caps in both languages as a brand mark.
- "BudgeTrak" is never translated.

### 9.2 AppStrings.kt (1,498 lines)

- `interface AppStrings` — 22 per-screen `*Strings` data classes (~1,393 total `val` fields), e.g. `CommonStrings`, `DashboardStrings`, `TransactionsStrings`, `SavingsGoalsStrings`, `AmortizationStrings`, `RecurringExpensesStrings`, `SyncStrings`, `BudgetCalendarStrings`, `BudgetConfigStrings`, `SettingsStrings`, and 10 `*HelpStrings` (one per help screen), plus `WidgetTransactionStrings`, `DefaultCategoryNames`.
- Many fields are `(T) -> String` lambdas for runtime-formatted text (e.g., `startDateLabel: (String) -> String`).

### 9.3 EnglishStrings.kt (1,896 lines)

`object EnglishStrings : AppStrings` — all English literals. Default when no preference is set.

### 9.4 SpanishStrings.kt (1,882 lines)

`object SpanishStrings : AppStrings` — parallel Spanish translations. "SYNC" kept all-caps.

### 9.5 TranslationContext.kt (1,477 lines)

Translator-facing companion: one `mapOf("fieldName" to "context description", ...)` per data class explaining what each string is for and where it appears. Never read at runtime.

### 9.6 LocalStrings.kt (5 lines)

```kotlin
val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
```

`MainActivity` wraps the content tree in `CompositionLocalProvider(LocalStrings provides languageStrings)` where `languageStrings` is derived from `vm.appLanguage`.

---

## 10. Help Screen Classes (10 total)

All help screens follow the same pattern: a `Scaffold` with top app bar, a scrolling body composed of shared `HelpComponents` primitives (`HelpSection`, `HelpSubsection`, bullet lists, note cards). They read strings from `LocalStrings.current.*HelpStrings` and never touch the ViewModel.

Inventory (10 screens, lines per screen): see SSD §23.2.

### HelpComponents.kt (165 lines)

Shared scaffolding used by every help screen: section header, sub-header, bullet row, note-card composables, common paddings and colors.

---

## 11. Widget Classes (`widget/`)

### 11.1 BudgetWidgetProvider.kt (288 lines)

`AppWidgetProvider` for the dashboard home-screen widget.

- `onUpdate()` → calls `BackgroundSyncWorker.schedule(context)` (no foreground work).
- `onReceive(ACTION_RESET_REFRESH)` → `BackgroundSyncWorker.runOnce()`.
- `onDisabled()` — no-op for workers (presence handled by RTDB).
- `updateAllWidgets(context)` — throttled to once per 5 seconds via `WIDGET_THROTTLE_MS = 5_000L`; deferred call is scheduled when throttled.
- Reads `showWidgetLogo` and `appLanguage` from `app_prefs` to localize widget chrome.

### 11.2 WidgetRenderer.kt (276 lines)

Object singleton. Canvas-based bitmap renderer for the Solari card shown in the widget:

- Theme-aware — light blue or dark card depending on system theme.
- Renders the period total, currency glyph, optional logo, and attribution.
- Bitmap is set via `RemoteViews.setImageViewBitmap`.

### 11.3 WidgetTransactionActivity.kt (996 lines)

`ComponentActivity` opened by widget tap for quick-add. Contains its own inline Compose dialog (does **not** reuse `TransactionDialog` from the main app). Implements its own matching chain against local JSON repositories.

- Free users: **1 widget transaction / day** via `widgetTxCount` in `app_prefs`, reset at midnight.
- Push path: writes through repositories and calls `SyncWriteHelper.pushRecord()` fire-and-forget.

### 11.4 Widget layout (`res/xml/widget_info.xml`)

- `minWidth="250dp"`, `minHeight="40dp"` — default 4×1.
- `minResizeWidth="110dp"`, `minResizeHeight="40dp"` — smallest 2×1.
- `resizeMode="horizontal|vertical"`.
- `updatePeriodMillis="3600000"` (1 h OS update; real refresh path is `BackgroundSyncWorker`).

### 11.5 Historical note

A single `BackgroundSyncWorker` handles sync, period refresh, cash recompute, and widget update together.

---

## 12. Persistence Schema

### 12.1 JSON files under `filesDir/`

| File | Purpose |
|---|---|
| `transactions.json` | Active transactions |
| `archived_transactions.json` | Archived transactions (below cutoff) |
| `categories.json` | Categories |
| `income_sources.json` | Income sources |
| `recurring_expenses.json` | Recurring expenses |
| `amortization_entries.json` | Amortization entries |
| `future_expenditures.json` | Savings goals (legacy filename, preserved intentionally) |
| `shared_settings.json` | Group-synced settings singleton |
| `period_ledger.json` | Per-period applied amounts |
| `enc_hash_cache.json` | Per-document ciphertext hash cache for sync skip (survives cold start) |
| `pending_receipt_uploads.json` | Receipt upload queue |
| `native_sync_log.txt` | Rotating sync log (512 KB, rotates to `_prev`) |

**Transaction fields:** `id`, `type` (EXPENSE/INCOME), `date`, `source`, `description`, `amount`, `categoryAmounts[]`, `isUserCategorized`, `excludeFromBudget`, `isBudgetIncome`, `linkedRecurringExpenseId/Amount`, `linkedAmortizationEntryId`, `linkedIncomeSourceId/Amount`, `amortizationAppliedAmount`, `linkedSavingsGoalId/Amount`, `receiptId1..5`, `deviceId`, `deleted`.

**Category fields:** `id`, `name`, `iconName`, `tag`, `charted`, `widgetVisible`, `deviceId`, `deleted`.

**IncomeSource / RecurringExpense fields:** `id`, `source`, `description`, `amount`, `repeatType`, `repeatInterval`, `startDate`, `monthDay1`, `monthDay2`, `deviceId`, `deleted`. `RecurringExpense` adds `setAsideSoFar` and `isAccelerated`.

**AmortizationEntry fields:** `id`, `source`, `description`, `amount`, `totalPeriods`, `startDate`, `isPaused`, `deviceId`, `deleted`.

**SavingsGoal fields:** `id`, `name`, `targetAmount`, `targetDate?`, `totalSavedSoFar`, `contributionPerPeriod`, `isPaused`, `deviceId`, `deleted`.

**SharedSettings fields:** currency, budgetPeriod, budgetStartDate, isManualBudgetEnabled, manualBudgetAmount, weekStartSunday, resetDayOfWeek, resetDayOfMonth, resetHour, familyTimezone, matchDays, matchPercent, matchDollar, matchChars, showAttribution, availableCash, incomeMode, deviceRoster, receiptPruneAgeDays, lastChangedBy, `archiveCutoffDate`, `carryForwardBalance`, `lastArchiveInfo`, `archiveThreshold`.

**PeriodLedger row:** `periodStartDate`, `appliedAmount`, `corrected` (unused; kept for compat), `deviceId`.

No per-field `*_clock` fields in any schema (sync uses `lastEditBy` + Firestore field-level updates instead).

### 12.2 SharedPreferences files

| Name | Purpose |
|---|---|
| `app_prefs` | Main app preferences — see 12.3 |
| `sync_engine` | Sync config (groupId, isAdmin, deviceName, migration flags, lastAdminCleanup, bgPushKeys, cursor_{collection}_seconds/_nanos) |
| `sync_engine_secure` | Encrypted (EncryptedSharedPreferences, AES256-GCM / KeyStore) — holds `encryptionKey` |
| `sync_device` | Persistent deviceId (UUID) |
| `sync_cursor` | Per-collection Firestore cursors |
| `pending_edits` | JSON map of unpushed local edits for conflict detection |
| `fcm_prefs` | FCM token + debug request flag |
| `receipt_sync_prefs` | Upload speed, `lastStalePruneRun`, retry counts |
| `backup_prefs` | `backup_retention` (default **10**; `-1` = all), `backup_frequency_weeks`, `last_backup_date`, `last_backup_success` |
| `sync_prefs` | Group / pairing UI state |

### 12.3 `app_prefs` keys

Display / currency: `currencySymbol` ("$"), `digitCount` (3), `showDecimals` (false), `showAttribution` (false), `dateFormatPattern` ("yyyy-MM-dd"), `chartPalette` ("Sunset"), `appLanguage`.

Budget: `budgetPeriod` ("DAILY"), `budgetStartDate`, `lastRefreshDate`, `resetHour` (0), `resetDayOfWeek` (7), `resetDayOfMonth` (1), `isManualBudgetEnabled`, `incomeMode` ("FIXED"), `weekStartSunday` (true).

Matching: `matchDays` (7), `matchDollar` (1), `matchChars` (5).

Subscription / flags: `isPaidUser`, `isSubscriber`, `subscriptionExpiry`, `lastSubscriptionWarning`, `crashlyticsEnabled` (**true**), `autoCapitalize` (**true**), `showWidgetLogo` (**true**), `quickStartCompleted`, `syncRepairAlert`.

Sync gating / maintenance: `lastMaintenanceCheck`, `checksumMismatchAt`, `widgetTxCount`, `widgetTxDate`, `migration_*` flags.

### 12.4 Internal cache files

See `enc_hash_cache.json`, `pending_receipt_uploads.json`, `native_sync_log.txt` above.

---

## 13. Repository Classes

Every repository uses `SafeIO` for atomic writes (temp file → fsync → rename, per-file `ReentrantLock`) and corruption-safe reads (missing-field defaults, corrupt-record skip). Standard methods: `load(context)`, `save(context, data)`, and — for some — `clear(context)`.

| Repository | File | Lines | JSON |
|---|---|---|---|
| TransactionRepository | `data/TransactionRepository.kt` | 130 | transactions.json + archived_transactions.json |
| CategoryRepository | `data/CategoryRepository.kt` | 55 | categories.json |
| IncomeSourceRepository | `data/IncomeSourceRepository.kt` | 67 | income_sources.json |
| RecurringExpenseRepository | `data/RecurringExpenseRepository.kt` | 72 | recurring_expenses.json |
| SavingsGoalRepository | `data/SavingsGoalRepository.kt` | 65 | future_expenditures.json |
| AmortizationRepository | `data/AmortizationRepository.kt` | 60 | amortization_entries.json |
| SharedSettingsRepository | `data/SharedSettingsRepository.kt` | 134 | shared_settings.json |
| PeriodLedgerRepository | `data/sync/PeriodLedger.kt` | (of 73) | period_ledger.json |

`SharedSettingsRepository` is slightly different — it's a singleton document (one JSON object), not a list, and participates directly in group sync.

---

## 14. Error Handling

**14.1 CSV import** — line-level parse errors with line number + message, BudgeTrak-format header validation, empty-file detection, partial results preserved.

**14.2 Encryption / decryption** — `CryptoHelper` uses `ChaCha20-Poly1305`. Minimum size check, AEAD tag verification throws `AEADBadTagException` on wrong key or tampered ciphertext. Per-field decrypt failures during sync are logged and the record is skipped with a wrong-key hint.

**14.3 Preference loading** — try-catch with enum defaults (`BudgetPeriod`, `IncomeMode`). `PrefsCompat.getDoubleCompat()` cascades Double → Float → Long → String for legacy numeric types.

**14.4 Repository loading** — existence check, empty-blank returns `emptyList()`, missing fields default to backward-compatible values, corrupt records skipped with warn log.

**14.5 ID collision** — all entity-id generators (Transaction / RE / IS / AE / SG / Category) use do-while retry against the local `existingIds`. Range is `1..Int.MAX_VALUE`; the do-while loop guarantees no local collision, and the wide range makes cross-device collisions in a sync group ≈1 in 2.1 B per concurrent pair. `SyncIdGenerator` (string deviceId) is unrelated to the entity-id generators.

**14.6 Budget calculation safety** — division by zero guarded (`totalPeriods > 0`, `repeatInterval > 0`), day-of-month clamped to month length, rounded to 2 decimals, NaN/Infinity guarded by `SafeIO.safeDouble()`.

**14.7 UI input validation** — required-field checks in every dialog, decimal-place limiting per currency, date-range validation, amount-sum verification for multi-category allocation.

**14.8 Sync errors** — 30 s Firestore op timeout, listener auto-reconnect after 5 s on error, enc-hash skip avoids redundant decrypt, `pushRecord()` falls back to `set()` on `NOT_FOUND`, period ledger uses create-if-absent. Exponential backoff on reconnect. `PERMISSION_DENIED` triggers `triggerFullRestart()` (30 s cooldown) which refreshes App Check and restarts all listeners. Listener watchdog + App Check timeout wrap Doze-safe. Filtered listeners fall back to unfiltered on null cursor. RTDB presence degrades gracefully if RTDB is not configured.

**14.9 File I/O** — atomic writes via temp + rename (copy fallback), per-file `ReentrantLock` against concurrent writes, crash logger to `Download/crash_log.txt`.

**14.10 Receipt sync** — upload-first flow (Storage before ledger), 30-day admin orphan scan, speed-based upload assignment with stale detection, 3-attempt real-failure recovery, stale-assignment failover, batch recovery cap (50), snapshot lifecycle with error states, 14-day pruning under local 24 h skip gate + group-level `lastCleanupDate` (idempotent).

---

## Appendix A: File Inventory

For exact current line counts run `find app/src/main/java -name "*.kt" | xargs wc -l`. Brief inventory by package; detailed responsibilities are in the body sections referenced.

### Root (`com.techadvantage.budgetrak`) — 3 files
`MainActivity` (UI shell, navigation, lifecycle, LoadingScreen, BackHandler — §2.2), `MainViewModel` (state + business logic + sync lifecycle + save functions + async load + maintenance + archiving — §2.3), `BudgeTrakApplication` (Application entry, App Check, Crashlytics, FCM helpers — §2.1).

### `data/` — 31 files
Data classes: `Transaction`, `Category`, `IncomeSource`, `RecurringExpense`, `AmortizationEntry`, `SavingsGoal`, `SharedSettings`, `BudgetPeriod` (enum) — §5.
Repositories: `TransactionRepository`, `CategoryRepository`, `IncomeSourceRepository`, `RecurringExpenseRepository`, `AmortizationRepository`, `SavingsGoalRepository`, `SharedSettingsRepository` (one per JSON file) — §13.
Utilities: `BudgetCalculator`, `CryptoHelper`, `CsvParser`, `DuplicateDetector`, `AutoCategorizer`, `CategoryIcons`, `SavingsSimulator`, `DefaultCategories`, `FullBackupSerializer`, `BackupManager`, `SafeIO`, `PrefsCompat`, `TitleCaseUtil`, `ExpenseReportGenerator`, `DiagDumpBuilder`, `PeriodRefreshService` — §6.

### `data/sync/` — 22 files
Sync engine: `EncryptedDocSerializer`, `FirestoreDocService`, `FirestoreDocSync`, `SyncWriteHelper`, `FirestoreService`, `GroupManager`, `SyncFilters`, `SyncIdGenerator`, `PeriodLedger`, `SecurePrefs`. Receipts: `ImageLedgerEntry`, `ImageLedgerService`, `ReceiptManager`, `ReceiptSyncManager`. Workers + FCM: `BackgroundSyncWorker`, `DebugDumpWorker`, `WakeReceiver`, `FcmService`, `FcmSender`. Presence: `RealtimePresenceService`. Merge: `SyncMergeProcessor`. Network: `NetworkUtils`. Detail: §7.

### `data/ocr/` — 2 files
`ReceiptOcrService` (Gemini 2.5 Flash-Lite pipeline with Call 1 routing probe + Call 1.5 reconciliation; reconcilePrices / aggregateCategoryAmounts / remapInvalidCategoryIds post-processing); `OcrResult` (`OcrResult`, `OcrCategoryAmount`, `OcrState` sealed class). SSD §11.3.

### `data/ai/` — 2 files
`AiCategorizerService` (Gemini Flash-Lite CSV categorizer; batched 100-at-a-time; `{i, merchant, amount}` payload; 30 s timeout + 3× exponential retry); `CategorizerPromptBuilder` (`CSV_CATEGORIZER_PROMPT_VERSION`). SSD §11.2.

### `data/telemetry/` — 1 file
`AnalyticsEvents` (`logHealthBeacon`, `logOcrFeedback`). §7.26a.

### `sound/` — 1 file
`FlipSoundPlayer` (procedural flip audio). §4.

### `ui/components/` — 5 files
`FlipChar`, `FlipDigit`, `FlipDisplay`, `PieChartEditor`, `SwipeablePhotoRow`. §3.

### `ui/screens/` — 22 files
11 main: `MainScreen`, `TransactionsScreen`, `RecurringExpensesScreen`, `AmortizationScreen`, `SavingsGoalsScreen`, `BudgetConfigScreen`, `BudgetCalendarScreen`, `SimulationGraphScreen`, `SyncScreen`, `SettingsScreen`, `QuickStartGuide`. 10 help screens (one per main, except BudgetCalendar/Sync/Simulation share the pattern). 1 shared: `HelpComponents`. §2.4–2.14, §10.

### `ui/strings/` — 5 files
`AppStrings` (interface, 22 data classes, ~1,393 fields), `EnglishStrings`, `SpanishStrings`, `TranslationContext`, `LocalStrings`. §9.

### `ui/theme/` — 3 files
`Color`, `Theme` (palette, dialogs, scroll arrows, toast), `Type` (`FlipFontFamily`). §8.

### `widget/` — 3 files
`BudgetWidgetProvider` (AppWidgetProvider, 5 s throttle, schedules `BackgroundSyncWorker`), `WidgetRenderer` (Canvas bitmap), `WidgetTransactionActivity` (quick-add from widget). §11.

---

## 15. Document Revision History

| Version | Date | Changes |
|---|---|---|
| 1.0 – 2.5 | Feb–Apr 2026 | Initial LLD; CRDT → Firestore-native sync migration; per-field encryption + filtered listeners + receipt system; SecureSync → BudgeTrak rename; `BackgroundSyncWorker` periodic 15-min; `MainViewModel` extracted from `MainActivity`; per-collection `updatedAt` cursors + `awaitInitialSync`; RTDB presence; async IO load + LoadingScreen; Back = Home; synchronous `recomputeCash()`; consolidated `runPeriodicMaintenance()`; three-tier `BackgroundSyncWorker`; transaction archiving. |
| 2.6 / 2.6.x | Apr 2026 | Package rebranded to `com.techadvantage.budgetrak` under Tech Advantage LLC. FCM wake architecture: Cloud Functions `onSyncDataWrite` + `presenceHeartbeat` (closes the 4h46m worker-silence gap on Samsung). Bidirectional scroll affordance (`PulsingScrollArrows` plural, `ScrollableDropdownContent`). Backup retention default raised to 10. |
| 2.7 | Apr 18 2026 | **AI features + photo-bar UX overhaul.** `data/ocr/ReceiptOcrService.kt` (Gemini 2.5 Flash-Lite 3-call pipeline + Call 1 routing probe + reconcile/remap/aggregate post-process). `data/ai/AiCategorizerService.kt` (CSV categorization fallback, merchant+amount payload only). Photo-bar long-press selects AI scan target + drag reorders. PDF import via `PdfRenderer`. `MIN_IMAGE_DIMENSION = 400` floor. `BackgroundSyncWorker.isRunning` `AtomicBoolean` double-fire guard. Cash Flow Simulation widened to Paid+Subscriber. Anchored toasts. |
| 2.7.1 | Apr 27 2026 | **Transaction save audit — six silent-loss vectors closed.** Non-dismissable `DuplicateResolutionDialog`; `onResume` add-only disk merge; entity-id range widened to `1..Int.MAX_VALUE`; multi-category validation toasts; `onUpdateTransaction` missing-target toast; `addTransactionWithBudgetEffect` atomic. |
| 2.8 | Apr 27 2026 | **TransactionDialog unification + network-awareness pass.** Three add/edit entry points consolidated (header EXPENSE/INCOME pill toggle); single layered Add-Transaction icon replaces +/- IconButton pairs. OCR refund-receipt support (`Int.MIN_VALUE` sentinel; auto-flip `typeIsExpense` + `abs()`). Preselect-help banner opens as a sibling-`Dialog` overlay so the underlying transaction dialog survives the round-trip. Fail-fast offline + auto-resume across receipt sync / AI OCR / Sync Now / `BackgroundSyncWorker` / App Check / anonymous auth via `networkCallback.onAvailable`. Tier 2 receipt-sync rethrows `CancellationException` and propagates `clearLostReceiptSlot` state back to `vm.transactions`. Debug receipt forensics in `token_log.txt` + `sync_diag.txt` Receipt Files Audit. Call 1.5 capped at 2 s past Call 2. ~100 files / ~51,500 lines. **Dead code removed:** down-only `PulsingScrollArrow`; `DeviceRecord.fingerprintData`/`fingerprintSyncVersion` (written but never read); `updateDeviceMetadata.fingerprintJson` parameter (no caller passed it). |

---

BudgeTrak Low-Level Design Document v2.8 — April 2026 — END OF DOCUMENT

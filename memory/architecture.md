---
name: Architecture notes
description: Key files, folder map, and architectural patterns for BudgeTrak
type: reference
---

# Architecture Notes

Total: ~47,000 lines across ~94 Kotlin source files.

## Top-level files
- `MainActivity.kt` (2438 lines) — screen router, lifecycle observer, composable wrappers, ad-banner host. All state + logic lives in MainViewModel.
- `MainViewModel.kt` (2650 lines) — `AndroidViewModel` holding ~80 state vars (mutableStateOf / mutableStateListOf / derivedStateOf), save functions, business logic, sync lifecycle, background loops (viewModelScope). `companion object { var instance: WeakReference<MainViewModel> }` lets `BackgroundSyncWorker` check whether the ViewModel is alive.
- `BudgeTrakApplication.kt` (107 lines) — App-level init: App Check provider (Debug/Play Integrity via `BuildConfig.DEBUG`), Crashlytics opt-out read, debug token capture to `token_log.txt`, `tokenLog()`, `syncEvent()`, `recordNonFatal()`, `updateDiagKeys()`.

## `ui/screens/` (11 main + 11 help + QuickStartGuide)
- `MainScreen.kt` (1303) — dashboard: Solari flip display, spending chart, nav cards, supercharge bolt modal, +/- quick-add.
- `TransactionsScreen.kt` (5633) — transaction list, search/filter dialogs, selection mode + batch ops, add/edit dialog, CSV import/export, PDF export trigger.
- `RecurringExpensesScreen.kt` (1097) — RE CRUD, accelerated-mode toggle, set-aside display.
- `AmortizationScreen.kt` (642) — AE CRUD, pause/resume, progress bar.
- `SavingsGoalsScreen.kt` (808) — target-date / fixed / supercharge goals.
- `BudgetConfigScreen.kt` (1243) — income sources, budget period + reset, income mode, manual budget.
- `BudgetCalendarScreen.kt` — month calendar of RE/IS events.
- `SimulationGraphScreen.kt` (686) — interactive 18-month cash projection.
- `SyncScreen.kt` (1206) — group create/join, device roster, admin controls.
- `SettingsScreen.kt` (1651) — preferences, categories, backups, widget, matching, dump button.
- `*HelpScreen.kt` — per-screen help, share `HelpComponents.kt` scaffolding.
- `QuickStartGuide.kt` — 6-step onboarding overlay.

## `ui/components/`
- `PieChartEditor.kt` — drag-to-resize multi-category splitter.
- `FlipDigit.kt` / `FlipDisplay.kt` / `FlipChar.kt` — canvas-based Solari digits.
- `SwipeablePhotoRow.kt` (669) — receipt photo swipe panel + full-screen viewer with rotation.

## `ui/theme/`
- `Theme.kt` (665) — `AdAwareDialog/AlertDialog/DatePickerDialog`, dialog styles/buttons, `LocalAppToast`, `LocalAdBannerHeight`, `LocalSyncBudgetColors`, `FlipFontFamily`.
- `Color.kt`, `Type.kt`.

## `ui/strings/`
- `AppStrings.kt` (1498, ~1226 val fields), `EnglishStrings.kt` (1896), `SpanishStrings.kt` (1882), `TranslationContext.kt` (1477), `LocalStrings.kt`.

## `sound/`
- `FlipSoundPlayer.kt` (134) — synthesizes "clack" sound (exponential decay + bounce + band-limited noise), encodes WAV, caches, loads into SoundPool (6-stream cap).

## `data/` (core business logic)
- Data classes: `Transaction.kt`, `RecurringExpense.kt`, `IncomeSource.kt`, `AmortizationEntry.kt`, `SavingsGoal.kt`, `Category.kt`, `SharedSettings.kt`, `CategoryAmount` (inner).
- Repositories (local JSON via `SafeIO.kt`): `TransactionRepository`, `CategoryRepository`, `RecurringExpenseRepository`, `IncomeSourceRepository`, `AmortizationRepository`, `SavingsGoalRepository`, `SharedSettingsRepository`.
- `BudgetCalculator.kt` (504) — `safeBudgetAmount`, `budgetAmount`, `recomputeAvailableCash`, period helpers, amortization/SG/accelerated-RE deduction math.
- `PeriodRefreshService.kt` (261) — shared foreground/background period refresh (`@Synchronized`).
- `SavingsSimulator.kt` (517) — 18-month projection engine.
- `BackupManager.kt` (405) — password-derived ChaCha20 backups, same-day letter suffix, SAF restore.
- `FullBackupSerializer.kt` (287) — backup payload (7 repos + SharedSettings + 20-key SharedPreferences).
- `ExpenseReportGenerator.kt` (365) — per-transaction PDF (form + receipt photos).
- `CsvParser.kt` (996) — Generic / US_BANK / SECURESYNC_CSV formats.
- `DuplicateDetector.kt` — ranked match finders (dup, RE, AE, BudgetIncome) — **actively used**, not dead code.
- `AutoCategorizer.kt` — per-merchant category learning (CSV bank imports only).
- `TitleCaseUtil.kt` — APA title case.
- `CryptoHelper.kt` — ChaCha20-Poly1305 AEAD (password PBKDF2 or direct 256-bit key).
- `DiagDumpBuilder.kt` (241) — diagnostic dump builder, works from disk (no live state).
- `CategoryIcons.kt`, `DefaultCategories.kt`, `PrefsCompat.kt`, `SafeIO.kt`.

## `data/sync/` (Firestore-native sync)
- `EncryptedDocSerializer.kt` (1039) — per-field encrypt/decrypt for all 8 collection types, `toFieldMap`, `fieldUpdate`, `fromDoc`, `diffFields`, collection-name constants (camelCase paths).
- `FirestoreDocService.kt` — low-level ops: `writeDoc` (set), `updateFields` (update), `listenToCollectionSince`, `readAllDocs`, `countActiveDocs`, batched `writeBatch` (500 ops).
- `FirestoreDocSync.kt` (927) — listener lifecycle, filtered-listener cursors per collection, enc_hash cache (`enc_hash_cache.json`), `pushRecord` (diff-based), conflict detection via `lastEditBy` + `localPendingEdits`, cold-start gate (`initialSyncReceived`, `awaitInitialSync`), `triggerFullRestart` on PERMISSION_DENIED, background echo suppression (`recentPushes`/`bgPushKeys`).
- `SyncWriteHelper.kt` — singleton push dispatcher (IO thread), `pushBatch` with 500-op chunking + retry/fallback.
- `SyncMergeProcessor.kt` — merge logic shared by foreground and background: category tag dedup, conflict detection, settings application.
- `BackgroundSyncWorker.kt` — 3-tier: (1) app active → skip, (2) ViewModel alive → App Check refresh + listener health + RTDB ping, (3) ViewModel dead → full sync + period refresh + receipts + cash + widget update.
- `WakeReceiver.kt` — manifest-registered `ACTION_POWER_CONNECTED`/`DISCONNECTED`; 5-min rate limit; triggers `BackgroundSyncWorker.runOnce`.
- `DebugDumpWorker.kt` — one-shot, FCM debug-request triggered, debug builds only.
- `FirestoreService.kt` — device/group management, pairing code, admin claims, subscriptions.
- `GroupManager.kt` — group lifecycle (create/join/leave/dissolve).
- `SyncFilters.kt` — `.active` extensions (excludes deleted + skeleton).
- `SyncIdGenerator.kt`, `SecurePrefs.kt` (KeyStore-backed AES256-GCM for encryption key).
- `PeriodLedger.kt` — `PeriodLedgerEntry` data class + dedup helper.
- `FcmService.kt` / `FcmSender.kt` — FCM message handling (debug dump requests only) + sender helper.
- `RealtimePresenceService.kt` — RTDB presence at `groups/{gid}/presence/{deviceId}`; `onDisconnect` writes offline+timestamp; replaces Firestore device polling.
- `ReceiptManager.kt` — local photo FS, compression, encrypt/decrypt, pending-upload queue, orphan cleanup.
- `ReceiptSyncManager.kt` (741) — top-level `syncReceipts()`: flag-clock polling, downloads, recovery, snapshot archives, pruning.
- `ImageLedgerService.kt` (741) — Firestore `imageLedger` CRUD, Cloud Storage ops, possession CAS, snapshot + join-snapshot ops.
- `ImageLedgerEntry.kt` — data class.

## `widget/`
- `BudgetWidgetProvider.kt` — AppWidgetProvider, throttles `updateAllWidgets()` to once per 5 seconds, schedules `BackgroundSyncWorker`.
- `WidgetRenderer.kt` — Canvas bitmap rendering for the Solari card.
- `WidgetTransactionActivity.kt` (996) — quick-add `ComponentActivity` with inline Compose dialog and its own matching chain.

> There is no `WidgetRefreshWorker` — that class was retired when `BackgroundSyncWorker` absorbed its job.

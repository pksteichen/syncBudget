---
name: UI Architecture Specification
description: Screen map, navigation, layout, widget integration, string system — BudgeTrak UI reference
type: reference
---

# UI Architecture Specification

## App structure

- Single Activity (`MainActivity.kt`, 2438 lines: screen router, lifecycle observer, composable wrappers) + `MainViewModel.kt` (2650 lines: ~80 state vars, business logic, sync lifecycle, background loops via `viewModelScope`).
- Kotlin + Jetpack Compose. No Navigation Component; navigation via `currentScreen: String` mutable state on the ViewModel.
- 7 JSON-file repositories load async on `Dispatchers.IO` with a learned-timing progress bar, gated by `dataLoaded`. See `MEMORY.md` "Async Loading & Lifecycle".
- Derived state (`derivedStateOf`) for `activeTransactions`, `safeBudgetAmount`, `budgetAmount`, `simAvailableCash`, etc.
- `MainViewModel.Companion.instance` is a `WeakReference<MainViewModel>` set in `init`, cleared in `onCleared`; read by `BackgroundSyncWorker` to decide Tier 2 vs Tier 3.

## Screens — 11 main + 11 help (22 total)

| Main | Help |
|---|---|
| MainScreen (dashboard) | DashboardHelpScreen |
| TransactionsScreen | TransactionsHelpScreen |
| RecurringExpensesScreen | RecurringExpensesHelpScreen |
| AmortizationScreen | AmortizationHelpScreen |
| SavingsGoalsScreen | SavingsGoalsHelpScreen |
| BudgetConfigScreen | BudgetConfigHelpScreen |
| BudgetCalendarScreen | BudgetCalendarHelpScreen |
| SimulationGraphScreen | SimulationGraphHelpScreen |
| SyncScreen | SyncHelpScreen |
| SettingsScreen | SettingsHelpScreen |
| QuickStartGuide (6-step onboarding overlay) | — |

`HelpComponents.kt` is a shared component library for all help screens. Most screens route via `vm.currentScreen = "..."` from the dashboard cards or settings entries.

## Dashboard layout (`MainScreen.kt`, 1303 lines)

1. **Top bar** — logo, settings icon (left), help icon (right).
2. **Ad banner** — 320×50 placeholder Box (black bg / gray text), gated by `!vm.isPaidUser`. Positioned inside the outer `Column(fillMaxSize().statusBarsPadding())` ABOVE the screen-router `Box(weight(1f))`, so it stays visible across all page transitions. `LocalAdBannerHeight` CompositionLocal exposes 50.dp (unpaid) / 0.dp (paid) to dialogs. See `project_ad_implementation.md`.
3. **Solari flip display** — bitmap-rendered retro flip-clock showing available cash. Sync indicator dot (bottom-left), supercharge bolt (bottom-right). Details in `spec_dashboard.md`.
4. **Spending chart** — bar/line chart with `SpendingRange` time window + `chartPalette` color scheme.
5. **Navigation cards** — Transactions, Recurring, Amortization, Savings Goals, Budget Calendar, Simulation Graph, Sync.
6. **Quick-add buttons** — + Income / − Expense bottom bar.

## Back = Home

On the main screen, system Back calls `moveTaskToBack(true)` — the app goes to the launcher but the ViewModel and listeners stay alive. `MainActivity.kt:252`.

## Dialog system — `ui/theme/Theme.kt` wrappers

Never use raw `AlertDialog` / `Dialog`. Use:

- `AdAwareAlertDialog` — confirmations, selections, text-field dialogs. Handles keyboard avoidance (`.imePadding()`), scrollability, sticky footer, ad-banner offset automatically.
- `AdAwareDialog` — custom form layouts; wrap with `Surface + DialogHeader + DialogFooter`; manually add `.imePadding()`, `.verticalScroll()`.
- `AdAwareDatePickerDialog` — date pickers.

Styles: `DialogStyle.DEFAULT` (green `#2E7D32`/`#1B5E20`), `DANGER` (red `#B71C1C`), `WARNING` (orange `#E65100`). Buttons: `DialogPrimaryButton / SecondaryButton / DangerButton / WarningButton` (500 ms debounce). `PulsingScrollArrow` when content overflows.

Full guide: `feedback_dialog_design_guide.md`.

## Toast system — `LocalAppToast`

`val toastState = LocalAppToast.current; toastState.show("message", windowYPx?, durationMs?)`. Tap-triggered toasts receive the Y position of the tapped element; non-tap toasts centre at ~60 % of usable screen height. Dark bg `#2A2A2A` / light `#F5F5F5`; app icon + message; 2500 ms default. Avoids status bar and ad banner automatically.

Never use raw `Toast.makeText` or `Snackbar`. Details in `feedback_dialog_design_guide.md`.

## Settings screen sections (`SettingsScreen.kt`, 1651 lines)

- Currency (8 options: `$`, `€`, `£`, `¥`, `₹`, `₩`, `₱`, `Fr`), language (device default / en / es), date format (11 patterns).
- Budget period + reset schedule.
- Categories management (add, rename, icon picker, charted/widgetVisible toggles, protected-tag handling for `"other"` / `"recurring_income"` / `"supercharge"`).
- Backups (retention 1/10/All, frequency 1/2/4 weeks, manual Backup Now, Restore).
- Receipt photos (paid only) — prune age, cache size, save button.
- Widget (logo toggle, preview).
- Matching config (matchDays / matchPercent / matchDollar / matchChars — synced).
- Auto-capitalize toggle (default on).
- Crashlytics opt-out toggle (`crashlyticsEnabled` in `app_prefs`, default `true`, read in `BudgeTrakApplication.onCreate`).
- Dump & Sync Debug button (debug builds only — uploads encrypted dump via FCM, polls 90 s).

## QuickStart Guide (`QuickStartGuide.kt`)

6 steps: `WELCOME → BUDGET_PERIOD → INCOME → EXPENSES → FIRST_TRANSACTION → DONE`. Bilingual via `LocalStrings`. Shown once on first run, skippable.

## Widget (`widget/`)

Single widget: `BudgetWidgetProvider` (AppWidgetProvider) + `WidgetRenderer` (Canvas bitmap for Solari) + `WidgetTransactionActivity` (quick-add dialog).

- Min size 2×1 (110 dp × 40 dp), default 4×1 (250 dp), resizable.
- Theme-aware: light mode blue cards, dark mode dark cards. `showWidgetLogo` toggle in Settings.
- `BudgetWidgetProvider.updateAllWidgets()` throttles to once per 5 seconds (`WIDGET_THROTTLE_MS`).
- Widget add pushes via `SyncWriteHelper`. Free = 1 txn/day, paid/subscriber = unlimited.
- Refresh scheduled through `BackgroundSyncWorker.schedule(context)` in `onUpdate`. **There is no `WidgetRefreshWorker` — that class was retired when `BackgroundSyncWorker` absorbed its job.**

## Strings system — `ui/strings/`

- `AppStrings.kt` — 1498 lines, data-class structure (~1,226 `val` fields across ~25 data classes). Add new fields here first.
- `EnglishStrings.kt` (1896 lines) and `SpanishStrings.kt` (1882 lines) are singletons extending `AppStrings`; Kotlin's constructor enforces field parity at compile time.
- `TranslationContext.kt` (1477 lines) — parallel `mapOf` with translator-facing context per field (not compiled into the app).
- `LocalStrings.kt` — `staticCompositionLocalOf` read as `val S = LocalStrings.current` in composables.
- Language selection: `appLanguage` pref ("en" / "es"), falls back to device default.

See `reference_strings_system.md` and `feedback_translation_context.md`.

## Sync status indicators

- "Last data sent/received" label (`lastSynced` key) + elapsed time, updated via `snapshotFlow { lastSyncActivity }` and a 10 s ticker.
- Device roster (other devices): green = RTDB online; dark blue < 1 h; yellow 1–2 h; red > 2 h.
- Device roster (own device): green = syncing, yellow = listeners down, red = no internet.
- Magenta flash (`syncRepairAlert`) on integrity repair or conflict detection.
- Offline indicator via `ConnectivityManager.NetworkCallback` (instant on/off).

## Key composables worth knowing

- `SwipeablePhotoRow` — swipe-left panel for receipt photos; drives camera/gallery pickers + full-screen viewer with rotation.
- `PieChartEditor` — drag-to-resize multi-category splitter.
- `FlipDisplay` / `FlipDigit` / `FlipChar` — canvas-based Solari digits.
- `HelpComponents` — shared scrollable help-screen scaffolding.
- `BudgetCalendarScreen` — month view of RE / IS occurrences by date; tap a day for event list.
- `SimulationGraphScreen` — interactive 18-month cash projection (zoom, pan, velocity decay).

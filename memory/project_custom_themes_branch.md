---
name: feature/custom-themes branch — color theming feature
description: Long-lived feature branch off dev adding user-customizable color themes + chart palettes; scope, locked-color rationale, page button system, and what's deferred
type: project
originSessionId: 97a3a389-349e-430a-a999-28f81c785774
---
# feature/custom-themes branch

## Status (2026-05-17)
Working APK in `Download/BudgeTrak.apk`. Not pushed for release — branch is exploratory, no release dispatch.

## Architecture decisions

**Decoupled theme vs chart palette** (initial design conflated them):
- `ThemeProfile` = base light + dark `ThemeColorSet` (six themable roles each).
- `ChartPalette` = chartLight + chartDark (12 colors each).
- Two independent active selections in MainViewModel: `activeTheme` + `activeChartPalette`.
- Built-ins: 1 theme (Default) + 3 chart palettes (Bright, Pastel, Sunset).

**Themable roles**: cardBackground, cardText, background, surface, onSurface, displayBackground. Six. Persistence JSON keys match the field names — do NOT rename them.

**Auto-derived inside SyncBudgetTheme** (don't add slots for these):
- `MaterialTheme.colorScheme.primary` = `cs.cardBackground` — so every existing `MaterialTheme.colorScheme.primary` usage in the app follows the user's Header pick. `onPrimary` derived from `cardBackground.luminance()` (black if >0.5 else white).
- Solari border = `solariBorderFor(displayBackground)` = `lerp(bg, White, 0.15f)`. Single source of truth in `Theme.kt`. Tune the 0.15 there.

**Locked colors (intentionally non-themable)**:
- **Income green / Expense red** — Western finance convention. Reinforced by text labels everywhere they appear, so red-green colorblind users still parse the UI. **Revisit if/when shipping an East-Asian locale** (CN/JP/KR finance UX inverts: red=up/gains, green=down/losses).
- Sync-indicator states (green/blue/yellow/red/grey)
- Dialog Danger (red) / Warning (orange)
- AdMob "Ad" badge yellow (#FFCC00) + black stroke — AdMob policy
- Native-ad overlay backdrop (#B3000000) — readability backstop
- UpgradeBadge yellow/black in InHouseAd

## Page button system (added 2026-05-17)

`ScreenPrimaryButton` in `Theme.kt` — filled button using `LocalSyncBudgetColors.current.headerBackground` + `.headerText`. Use for buttons on pages. Most prior `OutlinedButton` sites were swapped (Settings, BudgetConfig, RecurringExpenses, SavingsGoals, Amortization, Sync, Transactions, dashboard icon bar).

**Don't swap these** — deliberate exceptions:
- `SyncScreen` Dissolve Group / Leave Group — `OutlinedButton` with `Color(0xFFF44336)` Text; danger semantic. Filling with header amber would clash with the red text.
- `TransactionsScreen.kt` bank-import tab buttons (~lines 839/873/903) — already pass explicit `ButtonDefaults.outlinedButtonColors`.
- `MainActivity.kt` dialog backup-folder picker — inside a dialog body.
- `WidgetTransactionActivity.kt` — uses its own MaterialTheme outside the SyncBudgetTheme tree; `LocalSyncBudgetColors` resolves to fallback.

**Dialog buttons stay convention-locked**: `DialogPrimaryButton` (green) / `DialogSecondaryButton` (gray) / `DialogDangerButton` (red) / `DialogWarningButton` (orange). These are NOT swapped to `ScreenPrimaryButton`.

## System bars (added 2026-05-17)

Status bar AND nav bar pick up `headerBackground` via a single `windowInsetsPadding(statusBars ∪ displayCutout ∪ navigationBars)` on the root `Column` whose background is `topBarColor`. Icons forced light on both bars (`isAppearanceLightStatusBars = false`, `isAppearanceLightNavigationBars = false`) since header is dark in both themes.

## Storage pattern (matches other local-only data)
- `themes.json` + `chart_palettes.json` in `context.filesDir`.
- Selections in `app_prefs`: `selectedThemeName` + `selectedChartPaletteName`.
- Bundled into full backup, NOT joinSnapshot — local-only, survives group-join.
- Built-ins live in code (`BuiltInThemes.ALL`, `BuiltInChartPalettes.ALL`) — never corruptible, never deletable. Only user-created profiles are written.
- Edit-a-built-in auto-forks to `"<name> (Custom)"`.
- **Lineage tracking** via optional `forkedFrom: String?` on `ThemeProfile` + `ChartPalette` — names the source built-in. Drives the undo icon's "restore default" target so a Pastel-forked custom undoes to Pastel, not Bright. JSON field is optional.
- **One-time migration** of legacy `chartPalette` pref into `selectedChartPaletteName` inside `ChartPalettesRepository.getSelected` — preserves prior Sunset/Pastel selections on first launch after upgrade.
- **Backwards-compat parse**: `colorSetFromJson` silently ignores removed keys (`primary`, `displayBorder`, `incomeGreen`, `expenseRed`) so old custom theme JSON still loads.

## ColorsScreen design quirks
- Standard screen chrome (`CenterAlignedTopAppBar` + Scaffold + LazyColumn, 24dp outer padding, 16dp item spacing) — same pattern as SyncScreen/BudgetConfigScreen.
- **In-page preview wrapper**: the page body wraps its Scaffold in a nested `MaterialTheme` + `CompositionLocalProvider(LocalSyncBudgetColors provides …)` using `currentTheme` + `previewDark` (driven by Mode, not system theme). Page renders in the theme being edited even if device is in the opposite mode. Tool dialogs (picker / new / delete) intentionally compose against the OUTER theme so they remain readable mid-edit.
- **Edit/undo controls**: pencil opens the picker; undo appears only when `currentSlotColor != defaultSlotColor` (lineage-aware via `forkedFrom`).
- **Sample previews** at bottom: base modes → inline mock dialog (Surface w/ DialogHeader/DialogFooter); chart modes → 12-wedge pie.

## Pulse animations
Add-transaction + icon (both dashboard icon bar and Transactions toolbar) pulses **color** (`Color(0xFF0D47A1)` ↔ `Color.White`) via `animateColor`, not alpha. The body underneath uses `headerText`. 900 ms FastOutSlowIn, reverse-repeat.

## Deferred
- **Migrate hardcoded `0xFF4CAF50` / `0xFFF44336` literals** to a semantic helper if income/expense ever becomes themable. For now they stay hardcoded — see East-Asia-locale note above.

## Why not merge to dev yet
User wants to soak-test before committing to ship. Branch may be abandoned if the feature feels wrong; merging would force unwind. Release workflow is dispatch-only so no accidental publish.

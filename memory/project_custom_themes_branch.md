---
name: feature/custom-themes branch — color theming feature
description: Long-lived feature branch off dev adding user-customizable color themes + chart palettes; scope, architecture decisions, and what's deferred
type: project
---

# feature/custom-themes branch

## Status (2026-05-15)
Working APK in `Download/BudgeTrak.apk`. Not pushed for release — branch is exploratory, no release dispatch.

## Architecture decisions

**Decoupled theme vs chart palette** (learned the hard way — initial design conflated them):
- `ThemeProfile` = base light + dark color sets (10 roles each).
- `ChartPalette` = chartLight + chartDark (12 colors each).
- Two independent active selections in MainViewModel: `activeTheme` + `activeChartPalette`.
- Built-ins: 1 theme (Default) + 3 chart palettes (Bright, Pastel, Sunset).

**10 themable roles per mode** (locked colors below — DO NOT add to ThemeColorSet):
primary, cardBackground, cardText, background, surface, onSurface, displayBackground, displayBorder, incomeGreen, expenseRed.

**Auto-derived (not themable, computed in SyncBudgetTheme)**:
- `onPrimary` from `primary.luminance()` — pick black if `>0.5`, else white. Keeps CTA/pill text legible regardless of user's Primary pick.

**Locked colors (intentionally non-themable)**:
- Sync-indicator states (green/blue/yellow/red/grey)
- Dialog Danger (red) / Warning (orange)
- AdMob "Ad" badge yellow (#FFCC00) + black stroke — AdMob policy
- Native-ad overlay backdrop (#B3000000) — readability backstop
- UpgradeBadge yellow/black in InHouseAd

## Storage pattern (matches other local-only data)
- `themes.json` + `chart_palettes.json` in `context.filesDir`.
- Selections in `app_prefs`: `selectedThemeName` + `selectedChartPaletteName`.
- Bundled into full backup, NOT joinSnapshot — local-only, survives group-join unchanged.
- Built-ins live in code (`BuiltInThemes.ALL`, `BuiltInChartPalettes.ALL`) — never corruptible, never deletable. Only user-created profiles are written.
- Edit-a-built-in auto-forks to `"<name> (Custom)"`.
- **Lineage tracking** via optional `forkedFrom: String?` on `ThemeProfile` + `ChartPalette` — names the source built-in. Drives the undo icon's "restore default" target so a Pastel-forked custom undoes to Pastel, not Bright. JSON field is optional (back-compat: missing → null → falls back to Default/Bright).

## Pieces in place
- `ui/theme/ThemeProfile.kt` — data classes + built-ins.
- `data/ThemesRepository.kt` — both repositories.
- `ui/theme/ColorWheelPicker.kt` — HSV wheel + brightness slider + hex input, wrapped in `AdAwareDialog`.
- `ui/screens/ColorsScreen.kt` — 3-dropdown editor; theme/palette dropdown is context-sensitive per Mode.
- Settings → Colors button replaces the old Chart Palette dropdown.

## ColorsScreen design quirks
- Matches the standard screen chrome (`CenterAlignedTopAppBar` + Scaffold + LazyColumn, 24dp outer padding, 16dp item spacing) — same pattern as SyncScreen/BudgetConfigScreen.
- **In-page preview wrapper**: the page body wraps its Scaffold in a nested `MaterialTheme` + `CompositionLocalProvider(LocalSyncBudgetColors provides …)` using `currentTheme` + `previewDark` (driven by Mode, not system theme). This makes the page render in the theme being edited — even if device is in light mode and user is editing Dark. Tool dialogs (picker / new / delete) intentionally stay in the OUTER theme — they spawn through `AdAwareDialog`/`AdAwareDialogHost` whose content composes against the outer's `LocalSyncBudgetColors`, so they remain readable while you're mid-edit.
- **Edit/undo controls**: pencil icon (`Icons.Filled.Edit`) opens the picker; undo icon (`Icons.Filled.Undo`) appears only when `currentSlotColor != defaultSlotColor` (lineage-aware via `forkedFrom`).
- **Sample previews** at the bottom of the page:
  - Base modes → inline mock dialog (Surface w/ DialogHeader/DialogFooter — looks like a real dialog but is composed in-place so it re-renders in the preview theme).
  - Chart modes → 12-wedge pie. Strict "20%/5%/linear/sum=100" is impossible for 12 wedges (arithmetic series of 20→5 sums to 150); compromise was linear 20→5 weights normalized to 100% (largest ≈13.3%, smallest ≈3.3% — ratios preserved). If you ever want the math exact, drop to 8 wedges.

## Deferred (not blocking; do after feature proves out)
- **Migrate ~50 hardcoded `0xFF4CAF50`/`0xFFF44336` literals** to `LocalSyncBudgetColors.current.incomeGreen/expenseRed` — many are sync-indicator/dialog uses that must stay locked, so per-site review needed.
- **Wire `PieChartEditor.kt`** to read from `vm.activeChartPalette.chartLight/chartDark` instead of the old `chartPalette` pref. Today the chart palette setting affects nothing visible until this wiring is done.
- **Migration**: old `chartPalette` pref ("Bright"/"Pastel"/"Sunset") is not auto-converted to `selectedChartPaletteName` — first launch defaults to Bright. Acceptable since branch hasn't shipped.

## Why not merge to dev yet
User wants to soak-test before committing to ship. Branch may be abandoned if the feature feels wrong; merging would force unwind. Release workflow is dispatch-only so no accidental publish.

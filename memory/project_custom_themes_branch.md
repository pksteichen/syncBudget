---
name: feature/custom-themes branch — color theming feature
description: Long-lived feature branch off dev adding user-customizable color themes + chart palettes; scope, architecture decisions, and what's deferred
type: project
---

# feature/custom-themes branch

## Status (2026-05-14)
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

## Pieces in place
- `ui/theme/ThemeProfile.kt` — data classes + built-ins.
- `data/ThemesRepository.kt` — both repositories.
- `ui/theme/ColorWheelPicker.kt` — HSV wheel + brightness slider + hex input, wrapped in `AdAwareDialog`.
- `ui/screens/ColorsScreen.kt` — 3-dropdown editor; theme/palette dropdown is context-sensitive per Mode.
- Settings → Colors button replaces the old Chart Palette dropdown.

## Deferred (not blocking; do after feature proves out)
- **Migrate ~50 hardcoded `0xFF4CAF50`/`0xFFF44336` literals** to `LocalSyncBudgetColors.current.incomeGreen/expenseRed` — many are sync-indicator/dialog uses that must stay locked, so per-site review needed.
- **Wire `PieChartEditor.kt`** to read from `vm.activeChartPalette.chartLight/chartDark` instead of the old `chartPalette` pref. Today the chart palette setting affects nothing visible until this wiring is done.
- **Migration**: old `chartPalette` pref ("Bright"/"Pastel"/"Sunset") is not auto-converted to `selectedChartPaletteName` — first launch defaults to Bright. Acceptable since branch hasn't shipped.

## Why not merge to dev yet
User wants to soak-test before committing to ship. Branch may be abandoned if the feature feels wrong; merging would force unwind. Release workflow is dispatch-only so no accidental publish.

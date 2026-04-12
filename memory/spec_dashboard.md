---
name: Dashboard Specification
description: Solari flip display, spending chart, supercharge bolt, sync indicator, navigation cards — everything on MainScreen
type: reference
---

# Dashboard Specification (`ui/screens/MainScreen.kt`, 1303 lines)

## Layout order (top to bottom)

1. Top bar — logo, settings icon (left), help icon (right).
2. Ad banner (free users only — 320×50 placeholder Box, black bg + gray text).
3. **Solari flip display** — retro flip-clock of `simAvailableCash`. Theme-aware.
4. **Spending chart** — bar or line, user-selectable range and palette.
5. **Navigation cards** — Transactions, Recurring, Amortization, Savings Goals, Budget Calendar, Simulation Graph, Sync.
6. Quick-add bottom bar — `+ Income` / `− Expense`.

## Solari flip display

Canvas-rendered bitmap, not Compose `Text`. Files:
- `ui/components/FlipDisplay.kt` — composable that splits the amount into whole/decimal parts based on `CURRENCY_DECIMALS` map, builds `FlipChar` animations.
- `ui/components/FlipDigit.kt` — single digit rendering with the flip transition.
- `ui/components/FlipChar.kt` — base unit that owns the current value and target value.
- `sound/FlipSoundPlayer.kt` — procedural "clack":
  - Exponential decay envelope + secondary bounce.
  - Band-limited noise (1200 / 2400 / 800 Hz sine + random).
  - Encodes to WAV once, caches, loaded into SoundPool with 6-stream cap.
  - `FlipDisplay` plays on each digit transition; overlapping flips reuse streams.
- Font: `FlipFontFamily` in `ui/theme/Type.kt`.
- Widget bitmap rendering in `widget/WidgetRenderer.kt` mirrors the in-app display.

### Indicators on the Solari

- **Sync indicator dot** (bottom-left): reflects `syncStatus`. Colors:
  - green — online / syncing
  - yellow — listeners down
  - red — no internet
  - magenta flash — sync repair or conflict detected (`syncRepairAlert`)
- **Supercharge bolt** (bottom-right): animated when there is spare cash + at least one eligible goal. Tap opens the Supercharge dialog (REDUCE_CONTRIBUTIONS / ACHIEVE_SOONER — see `spec_recurring_and_savings.md`).

### Display rules

- Currency symbol prefix / suffix controlled by `CURRENCY_SUFFIX_SYMBOLS` (some currencies — `Fr`, European layouts — trail the amount).
- Decimal places driven by `CURRENCY_DECIMALS` (JPY shows 0 decimals, most others 2).
- Free tier: 1 widget transaction / day is enforced on the widget, not the dashboard — the dashboard has no per-day cap.

## Spending chart

Displays expense spend inside a selectable window. State in `MainViewModel`:
- `spendingRange: SpendingRange` — enum: `TODAY, THIS_WEEK, ROLLING_7, THIS_MONTH, ROLLING_30, THIS_YEAR, ROLLING_365`. "Rolling N" counts back from today; "this week/month/year" respects reset day + timezone.
- `chartPalette: ChartPalette` — the selected color scheme (Sunset, Bright, Pastel, etc.). Same palette is used by `PieChartEditor` and the dashboard pie/bar chart.

User can cycle range + palette inline on the chart (no settings round-trip). Selection persists in SharedPreferences.

Data source: `activeTransactions` filtered to EXPENSE and `!excludeFromBudget`, grouped by category for a pie view or by date-bucket for a line/bar view. Multi-category transactions show up in each of their categories weighted by `CategoryAmount.amount`.

## Sync status label

Below the Solari: "Last data sent/received: N min ago" using `snapshotFlow { lastSyncActivity }` + a 10 s ticker. `lastSyncActivity` is set on outbound pushes (save functions), inbound `onBatchChanged`, manual Sync Now, and initial listener attachment.

## Device roster colors (on SyncScreen, shown here for completeness)

Other devices — green online (RTDB), dark blue < 1 h, yellow 1-2 h, red > 2 h.
Own device — reflects `syncStatus` (see indicator colors above).

## Dashboard → other screens

Navigation cards set `vm.currentScreen = "<route>"`. There's no Navigation Component; MainActivity's `when (vm.currentScreen)` picks the screen composable.

## Back gesture
`MainScreen` is the one screen where Back → `moveTaskToBack(true)` (app goes to launcher, ViewModel stays alive). All other screens Back → `vm.currentScreen = "main"`.

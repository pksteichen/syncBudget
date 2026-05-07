---
name: Cash Flow Simulation Specification
description: How SavingsSimulator projects cash forward 18 months, sizes the "You need" amount via max(floor − balance), and how SimulationGraphScreen renders the cash + dashed-floor lines
type: reference
---

# Cash Flow Simulation

## Purpose
Projects available cash forward up to 18 months so users can see when they'll run low, how a savings goal affects their runway, and how big a cash buffer they should keep on hand to honor both budget AND earmarked savings. Used by `SimulationGraphScreen` (interactive chart) and `SavingsGoalsScreen` (the "You need ..." message at the top + low-point toast on goal create/edit).

## Engine — `data/SavingsSimulator.kt`

### Inputs
Current `availableCash` (pass `vm.simAvailableCash`), transactions, active REs, active ISs, active AEs, **all non-deleted SGs (paused included)**, SharedSettings (`budgetPeriod`, reset fields, `familyTimezone`, `manualBudget*`, `incomeMode`).

### Public surface
- `calculateSavingsRequired(...) → SimResult` — returns the "You need" amount + the date of the worst gap.
- `simulateTimeline(...) → Triple<SimResult, List<SimulationPoint> cash, List<SimulationPoint> floor>` — adds a parallel cash trajectory and a step-shaped SG floor timeline for the chart.
- `traceSimulation(...) → String` — diagnostic dump used by Settings → Dump & Sync Debug.

### Event model
The simulator builds a timeline of events sorted by `(date, priority)`:

| Priority | Event type |
|---|---|
| 0 | Income — IS occurrences within horizon, plus scheduled INCOME transactions dated in the future |
| 1 | Period deduction — one per budget period boundary; amount = `−(baseBudget − amortDed − savingsDed − accelDed)` (see Mechanism A note below) |
| 2 | Expense — RE occurrences within horizon, plus scheduled EXPENSE transactions dated in the future (including AE-linked) |

Priority ordering keeps period deductions from being applied before same-day income (so payday → period reset lines up naturally).

### Today's-draw neutralization
The `today` event is `−(availableCash + currentSGDed)` where `currentSGDed = BudgetCalculator.activeSavingsGoalDeductions(savingsGoals, …)`. Adding `currentSGDed` back cancels the per-period SG reduction that's already baked into `simAvailableCash`. Without it, adding/removing/pausing/resuming a SG would shift today's draw by the SG's contribution amount, which would in turn shift `minBalance` and the buffer portion of Need — producing counter-intuitive jumps. AE and accelerated-RE deductions are intentionally NOT neutralized: those represent real upcoming outflows already correctly shaping this period's spendable.

### Mechanism A (per-period budget reduction)
For future period boundaries, `effectiveBudget` keeps `savingsDed` subtracted (`base − amort − savings − accel`). This reflects reality: when a user actively contributes to a SG, less cash drains per period because that money stays in the bank as earmarked savings. We tried removing it (treat full-base every period regardless of SG) and reverted — that produced a misleading cash chart. Instead, the floor line and the new max-gap formula handle the rising-floor-vs-cash interaction (see "Need formula" below).

### Per-boundary loop (`addDynamicBudgetEvents`)
For each boundary B:
1. Reset `simRESetAside[i]` and clear `simREAccelerated[i]` for any RE whose occurrence fell in `(prevDate, B]`.
2. Compute `amortDed` (active AEs at B).
3. Compute `savingsDed`: skip goals where `goal.deleted || goal.isPaused || simGoalSaved[i] >= goal.targetAmount`. Per-goal contribution is `min(contributionPerPeriod, remaining)`. Accumulate `simGoalSaved[i] += ded`.
4. Compute `accelDed` for accelerated REs.
5. Append cash event `−(base − amort − savingsDed − accel)`.
6. Update floor timeline: emit `(B, prevFloor)` before step 3 and `(B, newFloor)` after, so the floor line forms a staircase.

The deleted/paused checks must be on both flags; the parallel RE loop checks `re.deleted` too.

### Floor timeline
A `List<SimulationPoint>` parallel to the cash timeline:
- Starts at `(today, sum of totalSavedSoFar across non-deleted goals)` — paused goals included, since their already-set-aside cash is still earmarked.
- Each boundary emits two points (staircase): `(B, prevFloor)` then `(B, newFloor)` if the floor changed.
- Paused goals contribute their static `totalSavedSoFar` (their `simGoalSaved[i]` doesn't grow — the boundary loop skips paused).
- Floor stops growing once a goal hits its target (capped at `targetAmount`).

### Need formula — max(floor − balance)
**Need = max over all events t of (floor_at_t − balance_at_t)**, clamped at 0.

This replaces the older `max(0, −minBalance) + initialFloor` formula, which only ensured cash stayed above **today's** floor at the worst trough. As the floor rises over the horizon, the old formula could let cash dip below the higher portions of the floor — the user would see the chart's solid cash line cross under the dashed line. The max-gap formula sizes Need so cash stays at-or-above the rising floor at every point.

Walk algorithm (`walkEventsForMaxGap`):
- Initialize `balance = 0`, `floor = initialFloor`, `maxGap = initialFloor` (gap at `today` before any events).
- For each event in sorted order: advance `floor` through any floor-timeline entries with `date <= event.date` (this includes both pre-step and post-step entries at boundaries — the post-step value wins, which is correct since by the boundary the period's contribution has been committed). Then `balance += event.amount`. Compute `gap = floor − balance`; track max + maxDate.
- Return `(maxGap, maxGapDate)`.

### Why pause/resume should leave Need unchanged
With the max-gap formula:
- For active SGs: cash trajectory is lifted by `cumulative savingsDed` at each t (Mechanism A), and the floor at each t equals `initialFloor + cumulative savingsDed` to t. The two offsets cancel, so `floor − balance = initialFloor − balance_no_SG`.
- For paused SGs: cash trajectory uses full base (no Mechanism A discount), and the floor stays at `initialFloor`. Same `floor − balance = initialFloor − balance_no_SG`.
- The max gap is identical in both states. Pausing only freezes future contributions; it doesn't change Need or anything visible on the chart's gap.

### Add / Delete are mirror operations on Need
With both today's-draw neutralization and max-gap formula:
- Adding a SG with `$X` already saved → Need rises by exactly `$X` (the new floor adds to initialFloor).
- Deleting an SG with `$X` saved → Need drops by exactly `$X` (the freed earmark removes from floor).
- New SG with `$0` starting → Need does not jump immediately; it grows by `contributionPerPeriod` at each real period refresh as `PeriodRefreshService` adds the contribution to `totalSavedSoFar`.

### Empty-horizon fallback
When `buildSortedEvents` returns null (no income, no recurring expenses), we can't project. Need = `roundCents(max(0, availableCash) + initialFloor)`. Same fallback in `simulateTimeline` and `traceSimulation`.

### Acceleration awareness
Accelerated REs (`isAccelerated`) pull their per-period deduction forward — the simulator uses the same `acceleratedREExtraDeductions` math as `BudgetCalculator` so projected cash matches what the dashboard will show when that period arrives.

## Graph — `ui/screens/SimulationGraphScreen.kt`

### Layout
- Two text inputs at top:
  - **Current Savings** — defaults to `simResult.savingsRequired`, lets user explore "what if I have $X today."
  - **Over/Under Budget per Day/Week/Month** — positive = overspending (bigger drain), negative = under-budget (smaller drain). Sign-flipped compared to the legacy "Saved per period" label. Wired into the simulator as `baseBudget + overUnderPerPeriod`.
- Solid line: cash trajectory (`adjustedPoints[i] = adjTimeline[i].balance + currentSavings`).
- **Dashed blue line: SG floor** (`adjFloor`). Step-shaped — rises at each period boundary as active goals accrue, plateaus when a goal hits target, stays flat for paused goals.
- Red dot: marker at the `maxGapDate` (worst point — where cash trajectory comes closest to or under the floor).
- Negative-balance region shaded — the visual cue that tells users when they'll go under zero.

### Y-axis range
Computed from BOTH `adjustedPoints` and `adjFloor` so a high floor doesn't get clipped.

### Interaction model
- Pinch zoom / pan horizontally; double-tap resets zoom (handled by zoom buttons).
- Inertia: pan velocity decays exponentially (EMA) so flicks feel natural.

### Progress timing
Initial build runs on `Dispatchers.Default` — the EMA ticker from the async-load progress bar is reused here so the user sees progress while large simulations build.

## Diagnostic trace
`traceSimulation` mirrors `calculateSavingsRequired`'s today's-draw neutralization and max-gap formula. Each event row prints `Date | Type | Amount | Balance | Floor` plus a `<< GAP` marker on the worst-gap row. The result line shows `Worst (floor − balance)`, the initial floor, and the final required amount. Any divergence between trace and dashboard "You need" indicates a regression.

## Supercharge integration (dashboard bolt)
The dashboard's supercharge bolt is not part of the simulator — see `spec_recurring_and_savings.md`. But when a user accepts a Supercharge ACHIEVE_SOONER adjustment, the simulator's worst-gap value usually improves. Showing the before/after delta would be a natural next UX step.

## Gating
The "View Chart" entry button on `SavingsGoalsScreen` is gated to **Paid Users and Subscribers** (`isPaidUser || isSubscriber`). Free users see the button but get an "Upgrade to access this feature" toast when tapping. `SimulationGraphScreen` itself (the destination) isn't separately paywalled — the gate lives on the entry button only.

Previously Subscriber-only; promoted to Paid+Subscriber on 2026-04-18. Pricing table in the public README (`/storage/emulated/0/Download/Tech Advantage Pages/README.md`) reflects the new gating.

## Performance notes
- Simulator runs O(events) — typically ~2000 events for a 3-person household over 18 months.
- Sampling is adaptive: one sample per event; the chart is responsible for any visual smoothing.
- Transactions past `archiveCutoffDate` are excluded (they're already folded into `carryForwardBalance`).

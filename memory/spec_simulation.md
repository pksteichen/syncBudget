---
name: Cash Flow Simulation Specification
description: How SavingsSimulator projects cash forward 18 months, and how SimulationGraphScreen renders the interactive graph
type: reference
---

# Cash Flow Simulation

## Purpose
Projects available cash forward up to 18 months so users can see when they'll run low, how a new savings goal affects their runway, and whether acceleration is needed. Used by `SimulationGraphScreen` (interactive graph) and `SavingsGoalsScreen` (low-point toast when creating/editing a goal).

## Engine — `data/SavingsSimulator.kt` (517 lines)

### Inputs
Current `availableCash`, transactions, active REs, active ISs, active AEs, active SGs, SharedSettings (`budgetPeriod`, reset fields, `familyTimezone`, `manualBudget*`, `incomeMode`).

### Event model
The simulator builds a timeline of events sorted by `(date, priority)`:

| Priority | Event type |
|---|---|
| 0 | Income — IS occurrences within horizon, plus scheduled INCOME transactions dated in the future |
| 1 | Period deduction — one per budget period boundary; amount = live `budgetAmount` (deductions and SG/RE contributions factored in) |
| 2 | Expense — RE occurrences within horizon, plus scheduled EXPENSE transactions dated in the future (including AE-linked) |

Priority ordering keeps period deductions from being applied before same-day income (so payday → period reset lines up naturally).

### Walk
Iterate events in order, maintain a running balance and record `(date, balance)` samples. Also track the minimum balance and the date it occurs — used in the "projected low" toast.

### Horizon
18 months by default. Longer horizons quickly become meaningless because RE/IS repeat assumptions compound; we opted for a bounded, truthful range over a long, noisy one.

### Acceleration awareness
Accelerated REs (`isAccelerated`) pull their per-period deduction forward — the simulator uses the same `acceleratedREExtraDeductions` math as `BudgetCalculator` so projected cash matches what the dashboard will show when that period arrives.

## Graph — `ui/screens/SimulationGraphScreen.kt` (686 lines)

### Interaction model
- Pinch zoom / pan horizontally.
- Tap a point to pin a tooltip (date + balance).
- Inertia: pan velocity decays exponentially (EMA) so flicks feel natural.
- Double-tap resets zoom.

### Rendering
- Plain `Canvas` (no charting library).
- Axis labels computed from the visible range (ticks snap to week/month/quarter depending on zoom).
- Negative-balance region shaded — the visual cue that tells users when they'll go under.
- Marker on minimum-balance point.

### Progress timing
Initial build runs on `Dispatchers.Default` — the EMA ticker from the async-load progress bar is reused here so the user sees progress while large simulations build.

## Supercharge integration (dashboard bolt)
The dashboard's supercharge bolt is not part of the simulator — see `spec_recurring_and_savings.md`. But when a user accepts a Supercharge ACHIEVE_SOONER adjustment, the simulator's low-point value usually improves: showing the before/after low point is a natural next UX step if we decide to surface it.

## Gating
Simulation is not paywalled. `isPaidUser` / `isSubscriber` do not gate `SimulationGraphScreen`.

## Performance notes
- Simulator runs O(events) — typically ~2000 events for a 3-person household over 18 months.
- Sampling is adaptive: one sample per event plus fill-in samples at regular intervals so the render curve stays smooth between sparse-event weeks.
- Transactions past `archiveCutoffDate` are excluded (they're already folded into `carryForwardBalance`).

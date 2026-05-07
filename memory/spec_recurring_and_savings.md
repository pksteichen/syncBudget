---
name: Recurring Expenses and Savings Goals Specification
description: Accelerated RE mode, set-aside tracking, single-type savings-goal math (fixed contribution + target-date helper), and the Supercharge bolt flow
type: reference
---

# Recurring Expenses + Savings Goals

## Recurring Expense model
`RecurringExpense`: `id, source, amount, description, category, repeatType, repeatInterval, startDate, setAsideSoFar, isAccelerated, paused, deviceId, deleted`.

### setAsideSoFar
Cumulative amount already deducted from budget across periods since the **last occurrence**. On each period refresh:
- If an occurrence falls inside the period → reset `setAsideSoFar = 0` and deactivate any ad-hoc acceleration.
- Otherwise → `setAsideSoFar += rate` where `rate = acceleratedRate if isAccelerated else normalRate`.

`normalRate = amount × annualOccurrences(repeatType, repeatInterval) / periodsPerYear`.

### Accelerated mode (`isAccelerated = true`)
Goal: finish putting aside the full RE amount **before** the next occurrence, even if the user started late.

```
remaining       = max(0, re.amount - re.setAsideSoFar)
periodsLeft     = periodsUntilNextOccurrence(today, re)
acceleratedRate = remaining / periodsLeft        // if periodsLeft > 0
extraDeduction  = max(0, acceleratedRate - normalRate)
```

`BudgetCalculator.acceleratedREExtraDeductions` sums `extraDeduction` across active non-paused REs. This extra is subtracted from `budgetAmount` (see `spec_budget_calculation.md` step 2). When `remaining = 0`, extra is 0 — acceleration effectively turns off.

### Paused
Frozen: excluded from set-aside updates and budget deductions; not shown in simulation.

### UI
`RecurringExpensesScreen.kt`: list with set-aside progress bar per RE, add/edit dialog, accelerated toggle (with row-level indicator). Help screen `RecurringExpensesHelpScreen.kt` documents the accelerated mode explicitly (audit caught a gap in 2026-04-11).

## Savings Goals model
`SavingsGoal`: `id, name, targetAmount, totalSavedSoFar, contributionPerPeriod, targetDate?, isPaused, deviceId, deleted`. The `targetDate` field exists in the data class for backward compatibility but the add/edit dialog **always saves with `targetDate = null`** as of v2.10.06 — there is now only one goal type.

### Single goal type — fixed contribution
- `deduction = min(contributionPerPeriod, remaining)` per period (`remaining = targetAmount − totalSavedSoFar`).
- Active iff `!deleted && !isPaused && totalSavedSoFar < targetAmount`.
- `BudgetCalculator.activeSavingsGoalDeductions` sums deductions across active goals. Per-period deductions are added to `totalSavedSoFar` by `PeriodRefreshService` at each real boundary; the simulator's `simGoalSaved[i]` array projects the same accumulation forward for the chart's floor line.

### Target-Date helper (calculator, not a goal type)
The "Calculate with Target Date" button inside the add/edit dialog opens a date picker, then computes `contribution = roundCents(remaining / periodsBetween(today, picked))` and writes that into the Contribution per Period field. The user can edit the suggested number before saving. The saved goal still has `targetDate = null` — it's a one-shot calculator, not a separate goal type. Branch `goal.targetDate != null` exists in `calculatePerPeriodDeduction` and the simulator for legacy synced goals; new goals never take that branch.

### Linked transactions
When a transaction is linked to a savings goal:
- `linkedSavingsGoalAmount = min(txn.amount, goal.totalSavedSoFar)` (partial funding supported).
- `goal.totalSavedSoFar -= linkedSavingsGoalAmount`.
- Transaction skipped in cash calc (money came from savings). If `linkedSavingsGoalAmount < txn.amount`, the remainder hits the budget as a normal expense.
- Edit path: `available = goal.totalSavedSoFar + previouslyTakenFromGoal`.

UI: transaction row shows green badge (fully funded from savings) or orange badge with remainder (partially funded). Dedicated SG-link dialog in `TransactionsScreen` and `SavingsGoalsScreen`.

## Supercharge (dashboard bolt)
A dashboard affordance when the user has spare cash + eligible goals. Tapping the animated bolt opens a dialog offering two modes:

### REDUCE_CONTRIBUTIONS
"You have enough saved — lower future contributions." Redistributes excess `totalSavedSoFar` across eligible goals by lowering each goal's `contributionPerPeriod`. The user has headroom in the current budget period without changing balances.

### ACHIEVE_SOONER
"Dump spare cash into a goal now so you hit the target earlier." Transfers from `availableCash` → `goal.totalSavedSoFar`, increasing `contributionPerPeriod`.

Implementation: `SuperchargeMode` enum, dialog in `MainScreen.kt` (around the bolt composable), math in `MainViewModel` bolt handler. The bolt animates when both conditions hold:
1. `simAvailableCash > X` (spare cash exists after live deductions), **and**
2. At least one goal is "eligible" (active, not yet at target, with lowerable contribution rate).

## "You need" message + chart floor (v2.10.06+)
The text at the top of the SG page reads "You need $X saved to cover your budget, future expenses, and the savings goals below." `$X` comes from `SavingsSimulator.calculateSavingsRequired`, which sizes Need as the worst `floor − balance` gap across the 18-month projection (see `spec_simulation.md`). The same calculation drives the chart's red low-point marker and the blue dashed floor line. Together they answer: "what cash floor must I keep so my projected balance never dips below my earmarked savings?"

## Linking ambiguities — agent guardrails
- **Accelerated RE** and **Supercharge** are different features. Accelerated is per-RE (pay off a bill faster). Supercharge is per-goal (deploy spare cash to savings goals).
- RE linking math on a transaction is *separate* from set-aside accumulation: `setAsideSoFar` tracks budget-side accrual across periods; `linkedRecurringExpenseAmount` tracks the remembered RE amount on each linked payment transaction. They don't interact in the cash calculation — linked transactions use the delta formula, set-aside feeds `budgetAmount`.
- Deleting an RE that has linked transactions preserves the remembered amounts on those transactions (see `feedback_delete_vs_unlink.md`).

## Skeleton records
Both RE and SG can arrive as skeletons with empty `source` / `name` during partial sync. `.active` filters in `SyncFilters.kt` hide them until the full content arrives.

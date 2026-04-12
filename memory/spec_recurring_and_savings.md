---
name: Recurring Expenses and Savings Goals Specification
description: Accelerated RE mode, set-aside tracking, savings goal math (target-date vs fixed), and the Supercharge bolt flow
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
`SavingsGoal`: `id, name, targetAmount, totalSavedSoFar, contributionPerPeriod, targetDate?, superchargeMode?, deviceId, deleted`.

### Two modes
- **Target-date goal** — `targetDate` set. `contributionPerPeriod` is derived:
  `deduction = roundCents(remaining / periodsUntil(today, targetDate))`.
  Missed periods use each period's own date (not today) when catching up.
- **Fixed-contribution goal** — `contributionPerPeriod` set, `targetDate` null.
  `deduction = min(contributionPerPeriod, remaining)`.

`BudgetCalculator.activeSavingsGoalDeductions` sums deductions across active (non-deleted, non-complete) goals. Per-period deductions are added to `totalSavedSoFar` by `PeriodRefreshService`.

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
"You have enough saved — lower future contributions." Redistributes excess `totalSavedSoFar` across eligible goals by lowering each goal's `contributionPerPeriod` (or pushing target dates out for target-date goals). The user has headroom in the current budget period without changing balances.

### ACHIEVE_SOONER
"Dump spare cash into a goal now so you hit the target earlier." Transfers from `availableCash` → `goal.totalSavedSoFar`, pulling target date forward or increasing `contributionPerPeriod`.

Implementation: `SavingsGoal.superchargeMode` enum, dialog in `MainScreen.kt` (around the bolt composable), math in `MainViewModel` bolt handler. The bolt animates when both conditions hold:
1. `simAvailableCash > X` (spare cash exists after live deductions), **and**
2. At least one goal is "eligible" (either target-date with reachable earlier-date, or fixed-contribution with lowerable rate).

## Linking ambiguities — agent guardrails
- **Accelerated RE** and **Supercharge** are different features. Accelerated is per-RE (pay off a bill faster). Supercharge is per-goal (deploy spare cash to savings goals).
- RE linking math on a transaction is *separate* from set-aside accumulation: `setAsideSoFar` tracks budget-side accrual across periods; `linkedRecurringExpenseAmount` tracks the remembered RE amount on each linked payment transaction. They don't interact in the cash calculation — linked transactions use the delta formula, set-aside feeds `budgetAmount`.
- Deleting an RE that has linked transactions preserves the remembered amounts on those transactions (see `feedback_delete_vs_unlink.md`).

## Skeleton records
Both RE and SG can arrive as skeletons with empty `source` / `name` during partial sync. `.active` filters in `SyncFilters.kt` hide them until the full content arrives.

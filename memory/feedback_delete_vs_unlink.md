---
name: Delete preserves remembered amounts, unlink clears them
description: Critical design rule — when linked entities are deleted, transactions preserve remembered amounts (expense already paid). Manual unlink clears them (linked-in-error). This asymmetry is intentional for ALL link types.
type: feedback
---

Delete preserves remembered amounts. Manual unlink clears them. NEVER change this.

**Why:** When a user deletes an RE, IS, AE, or SG, the linked transactions' expenses were already paid/accounted for. Clearing the remembered amount would cause availableCash to drop suddenly (double-counting). When a user manually unlinks (linked-in-error), the full transaction amount should apply to budget.

**How to apply:**
- `onDeleteRecurringExpense` → set linkedRecurringExpenseId=null, KEEP linkedRecurringExpenseAmount
- `onDeleteIncomeSource` → set linkedIncomeSourceId=null, KEEP linkedIncomeSourceAmount
- `onDeleteAmortizationEntry` → set linkedAmortizationEntryId=null, SET amortizationAppliedAmount to cumulative deduction
- `onDeleteSavingsGoal` → set linkedSavingsGoalId=null, KEEP linkedSavingsGoalAmount
- Manual unlink (edit transaction) → set ID=null, CLEAR amount to 0.0

This was previously discussed and implemented correctly. An audit incorrectly identified the SG delete behavior as a bug and "fixed" it — causing a regression. This rule must be checked before any linking-related changes.

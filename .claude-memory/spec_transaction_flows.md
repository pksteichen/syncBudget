---
name: Transaction Flows Specification
description: All transaction operations — create, edit, delete, link/unlink lifecycle, categorization, import, widget, sync integration
type: reference
---

# Transaction Flows Specification

## Create (In-App)
- TransactionDialog: date, merchant, description, category, amount, link buttons
- `addTransactionWithBudgetEffect()`: stamps all 20+ sync fields with lamportClock.tick()
- If linked to savings goal: deduct from goal.totalSavedSoFar
- Triggers: saveTransactions(), recomputeCash(), markSyncDirty()

## Create (Widget)
- WidgetTransactionActivity: minimal form (amount, merchant, category)
- Same matching chain: duplicate → RE → AE → IS
- Free users: 1 transaction/day limit
- Updates cash directly, triggers widget refresh

## Edit
- Per-field clock advancement: ONLY changed fields get new clock
- Manual unlink detection: if prev.linkedXxxId != null && updated.linkedXxxId == null → clear remembered amount to 0.0
- Savings goal: unlink restores funds to goal.totalSavedSoFar

## Delete
- Soft delete: `deleted=true, deleted_clock=tick()` (tombstone)
- If linked to savings goal: restore funds
- Filtered via `.active` extension (UI never shows deleted/skeleton records)
- Batch delete supported (selection mode in TransactionsScreen)

## Linking Lifecycle (KEY DESIGN RULE)
**Delete preserves remembered amounts. Manual unlink clears them.**

### Link to Recurring Expense
- Set ID + rememberedAmount at link time
- Cash effect: `cash += (rememberedAmount - txn.amount)` (delta)
- RE deleted → ID=null, amount PRESERVED (expense already paid)
- Manual unlink → ID=null, amount=0.0 (linked-in-error, full amount applies)

### Link to Income Source
- Set ID + rememberedAmount at link time
- Cash effect depends on incomeMode: FIXED=none, ACTUAL=delta, ADJUST=none
- IS deleted → ID=null, amount PRESERVED
- Manual unlink → ID=null, amount=0.0

### Link to Amortization Entry
- Set ID only (no remembered amount needed — AE handles deductions)
- While linked: transaction excluded from cash calc entirely
- AE deleted → ID=null, amortizationAppliedAmount=cumulative deduction at delete
- Manual unlink → ID=null, amortizationAppliedAmount=0.0

### Link to Savings Goal
- Set ID + amount, goal.totalSavedSoFar += amount
- While linked: transaction excluded from cash (money in savings)
- SG deleted → ID=null, amount PRESERVED (money already spent from savings)
- Manual unlink → ID=null, amount=0.0, goal.totalSavedSoFar += amount (restore funds)

## Categorization
- Single or multi-category (2-7 categories per transaction)
- CategoryAmount: categoryId + amount per category
- Modes: percentage or amount-based split
- Pie chart visualization for multi-category
- Auto-categorize on CSV import based on past transactions

## Flags
- `excludeFromBudget`: skip in cash calc (transfers, gifts)
- `isBudgetIncome`: planned income, no cash effect in FIXED mode

## Cash Recomputation Triggers
After: add, edit, delete, link/unlink, import, sync merge, period refresh, settings change, budget reset

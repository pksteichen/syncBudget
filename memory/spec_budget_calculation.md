---
name: Budget Calculation Specification
description: Complete specification of how safeBudgetAmount, budgetAmount, and availableCash are computed — formulas, every branch, worked examples
type: reference
---

# Budget Calculation Specification

## Overview

Multi-layer budget system:
1. **safeBudgetAmount** = (annual income - annual expenses) / periodsPerYear
2. **budgetAmount** = max(0, base - amortization - savings - accelerated RE deductions)
3. **availableCash** = Σ(period ledger credits) + Σ(transaction effects)
4. **simAvailableCash** = same as availableCash but with live budgetAmount for current period

## Enums

- **BudgetPeriod**: DAILY (365.25/yr), WEEKLY (52.18/yr), MONTHLY (12/yr)
- **IncomeMode**: FIXED (linked income=no effect), ACTUAL (delta: actual-remembered), ACTUAL_ADJUST (source updated, delta=0)
- **TransactionType**: EXPENSE, INCOME

## safeBudgetAmount

```
periodsPerYear = DAILY:365.25, WEEKLY:365.25/7, MONTHLY:12.0
totalAnnualIncome = Σ(src.amount × theoreticalAnnualOccurrences(src.repeatType, src.repeatInterval))
totalAnnualExpenses = Σ(re.amount × theoreticalAnnualOccurrences(re.repeatType, re.repeatInterval))
safeBudgetAmount = max(0, (totalAnnualIncome - totalAnnualExpenses) / periodsPerYear)
```

## budgetAmount

```
base = isManualBudgetEnabled ? manualBudgetAmount : safeBudgetAmount
budgetAmount = max(0, base - amortDeductions - savingsDeductions - accelDeductions)
```

### Amortization Deductions
For each non-paused AE where elapsed < totalPeriods:
```
elapsed = countPeriodsCompleted(startDate, today, budgetPeriod)
deduction = roundCents(amount × (elapsed+1) / totalPeriods) - roundCents(amount × elapsed / totalPeriods)
```

### Savings Goal Deductions
- Target-date: `remaining / periodsUntilTarget`
- Fixed-contribution: `min(contributionPerPeriod, remaining)`

### Accelerated RE Extra Deductions
```
normalRate = re.amount × annualOccurrences / periodsPerYear
acceleratedRate = (re.amount - setAsideSoFar) / periodsUntilNext
extra = max(0, acceleratedRate - normalRate)
```

## recomputeAvailableCash() — THE CORE FORMULA

```kotlin
fun recomputeAvailableCash(budgetStartDate, periodLedgerEntries, activeTransactions,
    activeRecurringExpenses, incomeMode, activeIncomeSources): Double
```

### Step 1: Sum Period Ledger Credits
```
cash = Σ(entry.appliedAmount) for entries where date >= budgetStartDate
       deduped by date (keep highest clock per epoch day)
```

### Step 2: Apply Transaction Effects

For each active transaction where date >= budgetStartDate AND !excludeFromBudget:

**EXPENSE branches (in order of precedence):**

1. `linkedSavingsGoalId != null OR linkedSavingsGoalAmount > 0` → **SKIP** (money from savings)
2. `linkedAmortizationEntryId != null` → **SKIP** (budget-accounted via AE deduction)
3. `amortizationAppliedAmount > 0` → `cash -= max(0, amount - amortizationAppliedAmount)` (deleted AE remainder)
4. `linkedRecurringExpenseId != null`:
   - If `linkedRecurringExpenseAmount > 0`: `cash += (linkedRecurringExpenseAmount - amount)` (remembered delta)
   - Else (legacy): live lookup RE, `cash += (re.amount - amount)` or `cash -= amount` if not found
5. `linkedRecurringExpenseAmount > 0` (but no ID = deleted RE): `cash += (linkedRecurringExpenseAmount - amount)`
6. Regular expense: `cash -= amount`

**INCOME branches:**

1. `linkedIncomeSourceId != null`:
   - FIXED mode: **no effect**
   - ACTUAL mode: `cash += (amount - linkedIncomeSourceAmount)` (or live lookup)
   - ACTUAL_ADJUST mode: **no effect**
2. `linkedIncomeSourceAmount > 0` (deleted source): ACTUAL mode: `cash += (amount - linkedIncomeSourceAmount)`. FIXED/ADJUST mode: **no effect** (transaction continues to behave as it did while linked).
3. `isBudgetIncome == true`: **no effect** (already budgeted)
4. Non-budget income: `cash += amount`

### Step 3: Return roundCents(cash)

## Worked Example

Setup: MONTHLY, budgetStartDate=2026-03-01
- Ledger: [Mar-01: $3000, Apr-01: $3000]
- RE "Rent" id=100, $1200/mo

Transactions:
- EXPENSE $100 unlinked → cash -= 100
- EXPENSE $1200 linked RE 100, reAmt=$1200 → cash += (1200-1200) = 0
- INCOME $5000 linked IS, FIXED mode → no effect
- INCOME $500 non-budget → cash += 500

Result: 3000 + 3000 - 100 + 0 + 0 + 500 = **$6400**

## Key Invariant

All devices with identical synced data compute identical availableCash. The formula is deterministic given the same inputs.

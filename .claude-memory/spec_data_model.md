---
name: Data Model Specification
description: Every data class, field, enum, linking lifecycle, and constraints — the complete schema reference
type: reference
---

# Data Model Specification

## Enums

| Enum | Values |
|------|--------|
| TransactionType | EXPENSE, INCOME |
| RepeatType | DAYS, WEEKS, BI_WEEKLY, MONTHS, BI_MONTHLY, ANNUAL |
| BudgetPeriod | DAILY, WEEKLY, MONTHLY |
| IncomeMode | FIXED, ACTUAL, ACTUAL_ADJUST |

## Transaction

| Field | Type | Default | Synced | Purpose |
|-------|------|---------|--------|---------|
| id | Int | - | immutable | 0-65535 unique ID |
| type | TransactionType | - | Yes | EXPENSE or INCOME |
| date | LocalDate | - | Yes | Transaction date |
| source | String | - | Yes | Vendor/bank name |
| description | String | "" | Yes | Optional details |
| amount | Double | - | Yes | Always positive |
| categoryAmounts | List\<CategoryAmount\> | [] | Yes | Split by category |
| excludeFromBudget | Boolean | false | Yes | Skip in cash calc |
| isBudgetIncome | Boolean | false | Yes | Planned income (no cash effect) |
| linkedRecurringExpenseId | Int? | null | Yes | → RecurringExpense.id |
| linkedRecurringExpenseAmount | Double | 0.0 | Yes | RE amount at link time |
| linkedAmortizationEntryId | Int? | null | Yes | → AmortizationEntry.id |
| amortizationAppliedAmount | Double | 0.0 | Yes | Cumulative AE deduction at delete |
| linkedIncomeSourceId | Int? | null | Yes | → IncomeSource.id |
| linkedIncomeSourceAmount | Double | 0.0 | Yes | IS amount at link time |
| linkedSavingsGoalId | Int? | null | Yes | → SavingsGoal.id |
| linkedSavingsGoalAmount | Double | 0.0 | Yes | Amount saved to goal |
| receiptId1-5 | String? | null | Yes | Cloud Storage photo IDs |

All synced fields have `_clock` (Long, default 0L) companions.
Also: deviceId (String), deleted (Boolean), with clocks.

## Linking Lifecycle

### Link to RecurringExpense
- **Link**: Set linkedRecurringExpenseId=re.id, linkedRecurringExpenseAmount=re.amount
- **Unlink (manual)**: Set ID=null, amount=0.0 (signals "linked-in-error, full amount applies")
- **RE deleted**: Set ID=null, preserve remembered amount (amount stays > 0 for correct delta calc)
- **Cash effect**: `cash += (rememberedAmount - txn.amount)` (delta). After RE deleted: same formula using preserved remembered amount.

### Link to IncomeSource
- **Link**: Set linkedIncomeSourceId=src.id, linkedIncomeSourceAmount=src.amount
- **Unlink (manual)**: Set ID=null, amount=0.0 (linked-in-error)
- **IS deleted**: Set ID=null, preserve remembered amount (amount stays > 0 for correct delta calc)
- **Cash effect (ACTUAL)**: `cash += (txn.amount - rememberedAmount)`. After IS deleted: same formula using preserved amount.
- **Cash effect (FIXED/ADJUST)**: no effect

### Link to AmortizationEntry
- **Link**: Set linkedAmortizationEntryId=ae.id
- **While linked**: Transaction fully excluded from cash calc (AE handles deductions)
- **AE deleted**: Set ID=null, amortizationAppliedAmount=cumulative deduction at delete
- **Unlink (manual)**: Set ID=null, amortizationAppliedAmount=0.0
- **Cash effect (deleted AE)**: `cash -= max(0, amount - amortizationAppliedAmount)`

### Link to SavingsGoal
- **Link**: Set linkedSavingsGoalId=goal.id, linkedSavingsGoalAmount=txn.amount, goal.totalSavedSoFar += amount
- **Unlink (manual = linked-in-error)**: Set ID=null, amount=0.0, goal.totalSavedSoFar += savedAmount (restore funds to goal). Transaction becomes normal expense.
- **SG deleted**: Set ID=null, PRESERVE linkedSavingsGoalAmount (amount stays > 0). Those expenses were already paid from savings — clearing would double-count them and drop availableCash.
- **Cash effect**: While amount > 0 (linked or formerly-linked to deleted goal) → SKIP entirely (money from savings). After manual unlink (amount=0) → transaction counts as normal expense.
- **KEY DESIGN RULE**: Delete preserves remembered amounts (expense already paid). Manual unlink clears them (linked-in-error). This asymmetry is intentional and applies to ALL link types (RE, IS, AE, SG).

## PeriodLedgerEntry

| Field | Type | Purpose |
|-------|------|---------|
| periodStartDate | LocalDateTime | When period began |
| appliedAmount | Double | budgetAmount at creation |
| clockAtReset | Long | Lamport clock at creation |
| clock | Long | CRDT creation timestamp |
| id (computed) | Int | epochDay of periodStartDate (dedup key) |

Dedup: per date, keep highest clock. Entries are immutable.

## SharedSettings

Singleton synced via CRDT. Key fields:
- currency, budgetPeriod, budgetStartDate, resetHour, resetDayOfWeek, resetDayOfMonth
- isManualBudgetEnabled, manualBudgetAmount, incomeMode
- availableCash (SYNCED — note: this is a computed value synced via CRDT)
- deviceRoster, familyTimezone, receiptPruneAgeDays

## Skeleton Records

Skeletons are incomplete CRDT records received via sync with missing critical field VALUES (empty source, empty name). Identified by **data content**, NOT by clock values — solo users who never synced have clock=0 on all records but their data is valid. Filtered from `.active` extension by `!deleted && source/name.isNotEmpty()`. Kept in storage for CRDT delivery guarantee.

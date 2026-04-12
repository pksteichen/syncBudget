---
name: Data Model Specification
description: Data classes, fields, enums, linking lifecycle, sync fields — schema reference for BudgeTrak (Firestore-native sync, no clocks)
type: reference
---

# Data Model Specification

> Sync moved to Firestore-native per-field encryption on 2026-03-23 (v2.1). All `_clock` / Lamport fields are gone. The only sync metadata on data classes is `deviceId` + `deleted`. Field-level changes are pushed via Firestore `update()` and ordered by server-assigned `updatedAt`.

## Enums

| Enum | Values |
|------|--------|
| TransactionType | EXPENSE, INCOME |
| RepeatType | DAYS, WEEKS, BI_WEEKLY, MONTHS, BI_MONTHLY, ANNUAL |
| BudgetPeriod | DAILY, WEEKLY, MONTHLY |
| IncomeMode | FIXED, ACTUAL, ACTUAL_ADJUST |
| SuperchargeMode | REDUCE_CONTRIBUTIONS, ACHIEVE_SOONER (savings goals) |

## Transaction (`data/Transaction.kt`)

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| id | Int | — | unique per device at creation |
| type | TransactionType | — | EXPENSE or INCOME |
| date | LocalDate | — | transaction date |
| source | String | — | vendor / bank / payer |
| description | String | "" | optional |
| amount | Double | — | always positive |
| categoryAmounts | List\<CategoryAmount\> | [] | split across categories (`{categoryId, amount}`) |
| excludeFromBudget | Boolean | false | skip in cash calc |
| isBudgetIncome | Boolean | false | planned income, no cash effect |
| isUserCategorized | Boolean | true | false = unverified (conflict/auto) |
| linkedRecurringExpenseId | Int? | null | → RE.id |
| linkedRecurringExpenseAmount | Double | 0.0 | RE.amount at link time |
| linkedAmortizationEntryId | Int? | null | → AE.id |
| amortizationAppliedAmount | Double | 0.0 | cumulative AE deduction captured at delete |
| linkedIncomeSourceId | Int? | null | → IS.id |
| linkedIncomeSourceAmount | Double | 0.0 | IS.amount at link time |
| linkedSavingsGoalId | Int? | null | → SG.id |
| linkedSavingsGoalAmount | Double | 0.0 | amount taken from savings |
| receiptId1..5 | String? | null | up to 5 receipt photo IDs |
| deviceId | String | "" | origin device |
| deleted | Boolean | false | tombstone |

No per-field clocks. The encrypted-field diff against `lastKnownState` (held in `FirestoreDocSync`) drives which `enc_fieldName`s are pushed on edit.

## Linking Lifecycle — **KEY DESIGN RULE**

**Delete preserves remembered amounts. Manual unlink clears them.** Same asymmetry for all four link types. See `feedback_delete_vs_unlink.md`.

### RecurringExpense
- Link: id = re.id, amount = re.amount
- Cash effect: `cash += (rememberedAmount − txn.amount)`
- RE deleted → id=null, amount **preserved**
- Manual unlink → id=null, amount=0.0

### IncomeSource
- Link: id = src.id, amount = src.amount
- Cash effect (ACTUAL): `cash += (txn.amount − rememberedAmount)`; (FIXED / ACTUAL_ADJUST): no effect
- IS deleted → id=null, amount **preserved**
- Manual unlink → id=null, amount=0.0

### AmortizationEntry
- Link: id = ae.id only
- While linked: transaction skipped entirely in cash calc (AE handles its own deductions)
- AE deleted → id=null, `amortizationAppliedAmount` = cumulative deduction at delete time. Cash effect (deleted AE): `cash −= max(0, txn.amount − amortizationAppliedAmount)`
- Manual unlink → id=null, `amortizationAppliedAmount = 0.0` (acts as normal expense)

### SavingsGoal
- Link: id = goal.id, amount = `min(txn.amount, goal.totalSavedSoFar)`; `goal.totalSavedSoFar -= amount`
- While id or amount > 0: transaction skipped in cash calc (money from savings). If `amount < txn.amount`, remainder hits budget as a normal expense.
- SG deleted → id=null, amount **preserved** (those expenses were already paid from savings)
- Manual unlink → id=null, amount=0.0, `goal.totalSavedSoFar += savedAmount` (restore funds)

## RecurringExpense (`data/RecurringExpense.kt`)

`id, source, amount, description, category, repeatType, repeatInterval, startDate, setAsideSoFar, isAccelerated, paused, deviceId, deleted`

- `setAsideSoFar`: cumulative "already put aside" across periods. Reset to 0 when an occurrence is reached. Updated per-period in multi-period catch-up.
- `isAccelerated`: when true, per-period deduction is pushed above normal rate to finish before next occurrence (see `spec_recurring_and_savings.md`).
- `paused`: freezes accumulation and excludes from budget/set-aside updates.

## IncomeSource (`data/IncomeSource.kt`)

`id, source, amount, repeatType, repeatInterval, startDate, deviceId, deleted`

Theoretical annual occurrences drive `safeBudgetAmount`. `IncomeMode` controls how linked transactions affect cash.

## AmortizationEntry (`data/AmortizationEntry.kt`)

`id, name, amount, startDate, totalPeriods, paused, deviceId, deleted`

Deduction math lives in `BudgetCalculator.activeAmortizationDeductions`.

## SavingsGoal (`data/SavingsGoal.kt`)

`id, name, targetAmount, totalSavedSoFar, contributionPerPeriod, targetDate?, superchargeMode?, deviceId, deleted`

- Target-date goal: `targetDate` set, `contributionPerPeriod` computed as `remaining / periodsUntilTarget`.
- Fixed-contribution goal: `contributionPerPeriod` set, `targetDate` null.
- Supercharge: bolt dialog on dashboard writes `superchargeMode` = REDUCE_CONTRIBUTIONS or ACHIEVE_SOONER, redistributing excess cash.

## Category (`data/Category.kt`)

`id, name, iconName, tag, charted, widgetVisible, deviceId, deleted`

- Protected `tag` values: `"other"`, `"recurring_income"`, `"supercharge"` — cannot be renamed/deleted through the UI.
- `charted`: shows on dashboard pie chart.
- `widgetVisible`: shows on the widget's category selector.

## SharedSettings (`data/SharedSettings.kt`)

Singleton synced to `groups/{groupId}/sharedSettings/main`. Fields include:

`currency, budgetPeriod, budgetStartDate, resetHour, resetDayOfWeek, resetDayOfMonth, weekStartSunday, isManualBudgetEnabled, manualBudgetAmount, incomeMode, availableCash, deviceRoster, familyTimezone, receiptPruneAgeDays, matchDays, matchPercent, matchDollar, matchChars, showAttribution, lastChangedBy, archiveCutoffDate, carryForwardBalance, lastArchiveInfo, archiveThreshold`

`familyTimezone` name is preserved across branding for backward sync compatibility (see `feedback_preserve_persistence_names.md`).

## PeriodLedgerEntry (`data/sync/PeriodLedger.kt`)

| Field | Type | Purpose |
|-------|------|---------|
| periodStartDate | LocalDateTime | when period began |
| appliedAmount | Double | budgetAmount at creation |
| corrected | Double? | optional user correction (legacy) |
| deviceId | String | creator |
| deleted | Boolean | tombstone |

Computed `id = periodStartDate.toLocalDate().toEpochDay().toInt()` — dedup key. Dedup keeps entry with the **maxByOrNull periodStartDate** (timestamp) per epoch-day. `clockAtReset` and `clock` fields were removed with the Firestore migration.

## Skeleton records

Skeletons are records delivered by sync with empty critical field values (empty `source`, empty `name`). Identified **by data content, not clock values** — solo users never synced, so clock is always 0 and isn't a reliability signal. `.active` filters in `data/sync/SyncFilters.kt` apply `!deleted && source/name.isNotEmpty()`.

## CategoryAmount (`data/Transaction.kt`)

`{categoryId: Int, amount: Double}` — used in `Transaction.categoryAmounts`. When the list has more than one entry the transaction is multi-category; otherwise the single-category code path runs.

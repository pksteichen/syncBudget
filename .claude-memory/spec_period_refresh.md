---
name: Period Refresh Specification
description: Period boundary detection, ledger creation, multi-period catch-up, savings/RE tracking, cash recomputation triggers
type: reference
---

# Period Refresh Specification

## Period Boundary Detection

Every 30 seconds, the app checks:
```
timezone = if (syncConfigured && familyTimezone.isNotEmpty()) ZoneId.of(familyTimezone) else null
currentPeriod = BudgetCalculator.currentPeriodStart(budgetPeriod, resetDayOfWeek, resetDayOfMonth, timezone, resetHour)
missedPeriods = countPeriodsCompleted(lastRefreshDate, currentPeriod, budgetPeriod)
```

**Timezone rule**: When in a sync group, ALL period calculations (refresh loop, simAvailableCash, budget reset) must use `sharedSettings.familyTimezone` to ensure all devices see the same period boundaries regardless of local timezone.

### currentPeriodStart()
- **DAILY**: today (or yesterday if before resetHour)
- **WEEKLY**: most recent resetDayOfWeek (1=Mon, 7=Sun)
- **MONTHLY**: resetDayOfMonth of current month (or previous if not reached)

## Ledger Entry Creation

When missedPeriods > 0, create one entry per missed period:
```
for period in 0..missedPeriods-1:
    periodDate = currentPeriod - (missedPeriods - 1 - period) periods
    if not alreadyRecorded(periodDate):
        entryClock = lamportClock.tick()
        add PeriodLedgerEntry(
            periodStartDate = periodDate.atStartOfDay(),
            appliedAmount = budgetAmount,  // LIVE value
            clockAtReset = entryClock,
            clock = entryClock
        )
```

### appliedAmount
Set to **live budgetAmount** at time of creation (reflects current deductions for amortization, savings, accelerated REs).

### Duplicate Prevention
- In-memory: `periodLedger.any { it.periodStartDate.toLocalDate() == periodDate }`
- On-disk: `dedup()` groups by epochDay, keeps highest clock

## Multi-Period Catch-Up

If app offline 3 days (DAILY mode):
1. Create 3 ledger entries (one per day), each with live budgetAmount
2. Update savings goals per-period (using period-specific dates for remaining calculation)
3. Update RE setAsideSoFar per-period (reset if occurrence reached, else increment)
4. Call recomputeCash()

### Savings Goal Updates During Catch-Up
- Target-date: `deduction = remaining / periodsUntil(periodDate, targetDate)`
- Fixed: `deduction = min(contributionPerPeriod, remaining)`
- Each period uses its own periodDate (not "today")

### RE Set-Aside Updates During Catch-Up
- If occurrence falls in period: reset setAsideSoFar=0, deactivate acceleration
- Otherwise: increment by normalRate or acceleratedRate

## Cash Recomputation Triggers

recomputeCash() is called after:
- Migrations complete
- Sync merge applies merged data
- Manual transaction add/delete
- Period refresh catch-up
- Amortization/savings/RE/income add/edit/delete
- Budget reset
- Synced settings applied

## simAvailableCash

Derived state that replaces current period's ledger appliedAmount with live budgetAmount:
```
adjustedLedger = periodLedger.map { entry ->
    if (entry.date == currentPeriod) entry.copy(appliedAmount = budgetAmount)
    else entry
}
simCash = recomputeAvailableCash(budgetStartDate, adjustedLedger, ...)
```
Purpose: Show mid-period budget changes immediately in UI.

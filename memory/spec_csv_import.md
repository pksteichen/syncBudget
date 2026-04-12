---
name: CSV Import Specification
description: CSV parsing formats, duplicate detection rules, auto-linking chain, matching tolerances, and import user flow
type: reference
---

# CSV Import Specification

## Supported Formats
- **Generic CSV**: Auto-detects delimiter, columns, date format. Scores columns, tries 3 candidate mappings.
- **US Bank**: Date/Type/Name/Amount. Falls back to generic if parse fails.
- **BudgeTrak CSV**: Native format with full sync metadata (clocks, deviceIds, linked IDs).

## Import Flow
1. User taps Import → selects format → picks file
2. Parser runs on IO thread, auto-categorizes bank imports
3. Day filtering: skips days already loaded (≥80% amount match for large days, 100% for small)
4. Duplicate check loop: for each parsed transaction:
   - Check duplicate → if found, show resolution dialog (Keep Both/Keep New/Keep Existing/Ignore All)
   - If not duplicate, try auto-link chain:
     a. `findRecurringExpenseMatch()` → confirm dialog
     b. `findAmortizationMatch()` → confirm dialog
     c. `findBudgetIncomeMatch()` → confirm dialog
     d. No match → add unlinked
5. Toast: "Loaded N of M transactions"

## Matching Tolerances (SharedSettings, synced)
- `matchDays`: Date window (default ±7 days)
- `matchPercent`: Amount % tolerance (default 1%)
- `matchDollar`: Amount $ tolerance (default $1)
- `matchChars`: Merchant substring length (default 5 chars)

## Matching Rules
- **Amount**: passes if `abs(a1-a2)/max ≤ percent` OR `abs(round(a1)-round(a2)) ≤ dollar`
- **Date**: `abs(days_between) ≤ matchDays`
- **Merchant**: case-insensitive, short strings require exact match, long strings check N-char substring overlap
- **Recurring match**: amount+merchant match AND date within matchDays of any generated occurrence

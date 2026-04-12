---
name: Transaction Flows Specification
description: All transaction operations — create, edit, delete, link lifecycle, categorization — plus widget add, CSV import, and selection-mode batch ops
type: reference
---

# Transaction Flows Specification

## Add pipeline — three-step entry (`MainViewModel.kt`)

All transaction creation (in-app, widget, CSV, future OCR) enters through:

| Step | Function | Line | Behavior |
|---|---|---|---|
| 1 | `runMatchingChain(txn)` | 1071 | duplicate detection; if dupes found, shows dupe dialog — otherwise → step 2 |
| 2 | `runLinkingChain(txn)` | 1019 | checks RE / AE / IncomeSource matches; shows match dialog if any — otherwise → step 3 |
| 3 | `addTransactionWithBudgetEffect(txn)` | 991 | SG deduction, `transactions.add`, `saveTransactions(listOf(stamped))`, `recomputeCash()`, archive-check if over threshold |

New entry points should call `runMatchingChain(txn)` at the top so they inherit dedupe + matching for free.

## TransactionDialog (`ui/screens/TransactionsScreen.kt`)

Used for both add and edit. Fields: date, source/merchant, description, category (single or multi via pie-chart toggle — `showPieChart` state, `PieChartEditor`), amount, link buttons, receipt photos (up to 5 via `SwipeablePhotoRow`).

- **Auto-capitalize**: merchant and description run through `TitleCaseUtil.toApaTitleCase()` on input when Settings `autoCapitalize` is on (default). `TransactionsScreen.kt:3562, 3618`.
- **Auto-categorize on manual entry**: does **not** fire in `TransactionDialog`. Auto-categorization is CSV-bank-import only (see CSV section below). Memory previously claimed this fired on merchant-length match — that was wrong.
- **Receipt photos**: view/add/delete via swipe-left panel; full-screen viewer supports rotation.

## Edit flow

- `transaction.copy(...)` produces the updated record.
- `saveTransactions(hint = listOf(updated))` → Firestore diffs `enc_*` fields against `lastKnownState` and pushes only changed fields (`FirestoreDocSync.pushRecord`). There is no per-field clock advancement; changed-field detection is encryption-hash-based.
- Manual unlink detection in the dialog's save handler: if `prev.linked*Id != null` and updated `linked*Id == null`, set remembered amount to 0.0 and (for SG) restore `goal.totalSavedSoFar += savedAmount`.
- Edit-RE-amount confirmation: when changing an RE's amount, a dialog asks "apply to past linked transactions?" and either updates or leaves linkedRecurringExpenseAmount on existing transactions.

## Delete

- Soft delete: `deleted = true`. Tombstone synced to group; local solo users purge tombstones in `runPeriodicMaintenance`.
- If linked to SG and not manually unlinked first, deletion restores savings (mirrors unlink behavior).
- `.active` extension filters delete + skeleton records so UI never shows them.

## Linking lifecycle

**Delete preserves remembered amounts. Manual unlink clears them.** See `spec_data_model.md` for per-link-type detail and `feedback_delete_vs_unlink.md` for why.

## Selection mode + batch ops (`TransactionsScreen.kt`)

- Long-press a row toggles selection. Additional taps extend the set (`selectedTxnIds: Set<Int>`).
- Top bar swaps to show selection actions: bulk delete, bulk link to RE / AE / SG, bulk exclude-from-budget, bulk archive.
- Exit selection via back or clear-selection button.

## Search + filter

Separate dialogs, not a unified filter bar:
- **Text search**: source + description substring.
- **Amount range**: min / max dialog.
- **Date picker**: search within a date range.

Results update a filtered view of the list. Selection mode and search are orthogonal.

## CSV import (`CsvParser.kt`, called from `TransactionsScreen`)

Three formats (`BankFormat` enum):
- **GENERIC_CSV** — auto-detects delimiter, columns, date format; scores column candidates; tries three mappings; reports parse errors per line.
- **US_BANK** — Date/Type/Name/Amount; falls back to GENERIC if parse fails.
- **SECURESYNC_CSV** — BudgeTrak native CSV (full sync metadata). Name preserved for backward compatibility with backups from legacy builds.

Post-parse flow:

1. Auto-categorize on bank imports only (`AutoCategorizer` learns per-merchant category from history).
2. Day filtering: skip days already loaded (≥ 80 % amount match on large days, 100 % on small days).
3. Per-transaction loop: duplicate check → (if none) auto-link chain (RE → AE → BudgetIncome) → add unlinked.
4. Toast: "Loaded N of M transactions".

## CSV export + PDF export

- CSV export writes BudgeTrak-native CSV preserving link fields. Triggered from Transactions screen export menu.
- **PDF expense reports** — `data/ExpenseReportGenerator.kt` generates per-selected-transaction multi-page PDFs: page 1 is an expense-report form (date, merchant, category breakdown, amount, description), subsequent pages are full-size receipt photos. Called from `TransactionsScreen.kt:1849`. Output directory: `BudgetrakDir/PDF/`. Not tier-gated in code, but CSV/PDF/Excel exports are a Paid-tier feature per the pricing tiers (`project_pricing.md`).

## Matching engine (`data/DuplicateDetector.kt`, `data/AutoCategorizer.kt`)

All four finders return ranked lists and the match dialogs show a radio-button list of candidates with the best pre-selected:

| Finder | Rank order |
|---|---|
| `findDuplicates` | closest amount, then date |
| `findRecurringExpenseMatches` | closest date, then amount |
| `findAmortizationMatches` | closest amount |
| `findBudgetIncomeMatches` | closest date, then amount |

Merchant comparison strips non-alphanumeric characters (`Wal-Mart` = `Walmart`, `O'Riley` = `ORiley`); tolerances `matchDays / matchPercent / matchDollar / matchChars` live in SharedSettings and sync to the whole group.

`DuplicateDetector.kt` is **actively used**, not dead code (old audits flagged otherwise).

## Widget add (`widget/WidgetTransactionActivity.kt`)

- Standalone `ComponentActivity` with its own inline Compose dialog (not reusable `TransactionDialog`).
- Same matching chain as in-app: duplicate → RE → AE → IncomeSource.
- Free users: 1 widget transaction per day; paid/subscriber: unlimited.
- Save path pushes to Firestore via `SyncWriteHelper` and triggers widget refresh.
- Photo capture is not yet wired to the widget's quick-add (see `project_widget_photos.md`).

## Archive system

- Trigger: transaction count or date crosses `archiveThreshold` / `archiveCutoffDate` in SharedSettings.
- Archived transactions move to a separate `transactions_archived.json` file and are excluded from active lists.
- `carryForwardBalance` stores the accumulated cash effect of archived transactions; `recomputeCash` starts from this value rather than re-summing old entries.
- `lastArchiveInfo` records the last archive event for display.

## Multi-category transactions — already built

The entire stack handles `categoryAmounts`:
- Encryption: `enc_categoryAmounts` (`EncryptedDocSerializer.kt`).
- Sync merge: `SyncMergeProcessor`.
- Entry: `PieChartEditor.kt` (~470 lines) with drag-to-resize across selected categories, or per-category text fields.
- List rendering: `TransactionsScreen.kt:2657+` with `hasMultipleCategories` handling and per-category row display.
- Dashboard pie chart, spending chart range.
- CSV import / native-format export.
- `AutoCategorizer` (single-category fallback only).
- `FullBackupSerializer` (included in backup payload).
- `ExpenseReportGenerator` (PDF shows per-category breakdown).

Do not propose adding multi-category support — it is fully implemented.

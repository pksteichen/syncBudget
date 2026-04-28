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
| 1 | `runMatchingChain(txn)` | 1201 | duplicate detection; if dupes found, shows dupe dialog — otherwise → step 2 |
| 2 | `runLinkingChain(txn)` | 1149 | checks RE / AE / IncomeSource matches; shows match dialog if any — otherwise → step 3 |
| 3 | `addTransactionWithBudgetEffect(txn)` | 1088 | dedup-by-id guard, then atomically: SG deduction, `transactions.add`, `saveTransactions(listOf(stamped))`. After the guard: `recomputeCash()`, archive-check if over threshold |

New entry points should call `runMatchingChain(txn)` at the top so they inherit dedupe + matching for free.

**Atomicity invariant (2026-04-27):** `addTransactionWithBudgetEffect` gates ALL add side effects (SG deduction + local list mutation + disk write + Firestore push) behind one `transactions.none { it.id == stamped.id }` check. A double-tap or recomposition replay is a complete no-op rather than re-deducting the SG or pushing a different-content txn under an existing id. Pre-2026-04-27 the SG deduction and Firestore push happened outside the guard, which could double-deduct on double-tap and leak "saved on Firestore but missing locally" state.

**Duplicate dialog is non-dismissable** (`DuplicateResolutionDialog` in `TransactionsScreen.kt:2644`, dashboard wiring `MainActivity.kt:1031-1064`, widget at `WidgetTransactionActivity.kt:578`): tap-outside and back are no-ops; user must pick Keep Existing / Keep New / Keep Both (and Ignore All on import). Pre-2026-04-27 dismiss meant Keep Existing — silently dropped the new transaction.

## TransactionDialog (`ui/screens/TransactionsScreen.kt`)

Single dialog used for **all three** entry points (Add Income, Add Expense, Edit). The 2026-04-28 consolidation removed the `title` and `sourceLabel` parameters — both are now derived inside the dialog from `editTransaction != null` (Add vs Edit) and `typeIsExpense` (Merchant vs Source). The header shows "Add Transaction" / "Edit Transaction" plus a compact EXPENSE/INCOME pill toggle.

**Type as mutable state**: `var typeIsExpense by remember(isExpense) { mutableStateOf(isExpense) }`. The `isExpense` parameter is the *seed*; the dialog can flip the type at any time (manual pill tap or OCR refund detection). Save logic uses `typeIsExpense`, not the parameter. Fields: date, source/merchant (label flips with type), description, category (single or multi via pie-chart toggle — `showPieChart` state, `PieChartEditor`), amount, link buttons, receipt photos (up to 5 via `SwipeablePhotoRow`).

- **Auto-capitalize**: merchant and description run through `TitleCaseUtil.toApaTitleCase()` on input when Settings `autoCapitalize` is on (default). `TransactionsScreen.kt:3562, 3618`.
- **Auto-categorize on manual entry**: does **not** fire in `TransactionDialog`. Auto-categorization is CSV-bank-import only (see CSV section below). Memory previously claimed this fired on merchant-length match — that was wrong.
- **Receipt photos**: view/add/delete via swipe-left panel; full-screen viewer supports rotation.
- **Save validation**: every silent `return@DialogPrimaryButton` in the multi-category save branch (`~4796-4815`) sets `showValidation = true` and shows `S.transactions.multiCategoryAmountsInvalid` so the dialog never looks dead. Single-category mode already used `showValidation` for inline `isError` styling on source/category/amount; multi-category fields don't have showValidation-tied styling so the toast is the user-facing signal. **Negative amounts remain invalid** (`singleAmountInvalid` checks `amount < 0` at `:4379`) — the data model is "amount always positive, type carries polarity" (see Refund handling below).
- **Refund handling (2026-04-28)**: when OCR Call 1 returns `amountCents < 0` (return/refund receipt), the prefill flips `typeIsExpense = false` and uses `kotlin.math.abs(...)` for all amount fields. The user sees the type pill swap to INCOME and a positive amount populated; save works. CsvParser.kt:869 follows the same convention for bank-statement imports (negative inflow → EXPENSE polarity, but amount stored positive).
- **Help-from-dialog overlay (2026-04-28)**: tapping the AI preselect-help banner sets `vm.transactionsHelpOverlayShowing = true` (NOT `currentScreen = "transactions_help"` — that path closed the dialog or rendered help behind it). The overlay lives in its own Compose `Dialog` window at the top of MainActivity, stacking above the AdAwareDialog window. Back arrow / system back dismisses just the overlay — the transaction dialog stays mounted, so in-progress entries + photos survive the round-trip.

## Edit flow

- `transaction.copy(...)` produces the updated record.
- `saveTransactions(hint = listOf(updated))` → Firestore diffs `enc_*` fields against `lastKnownState` and pushes only changed fields (`FirestoreDocSync.pushRecord`). There is no per-field clock advancement; changed-field detection is encryption-hash-based.
- Manual unlink detection in the dialog's save handler: if `prev.linked*Id != null` and updated `linked*Id == null`, set remembered amount to 0.0 and (for SG) restore `goal.totalSavedSoFar += savedAmount`.
- Edit-RE-amount confirmation: when changing an RE's amount, a dialog asks "apply to past linked transactions?" and either updates or leaves linkedRecurringExpenseAmount on existing transactions.
- **Edit no-op surfaced as toast** (`MainActivity.kt:2058-2063`, 2026-04-27): if `vm.transactions.indexOfFirst { it.id == updated.id }` returns -1 (target was archived or tombstone-purged mid-edit), the user gets a 5 s toast `S.transactions.editFailedTransactionMissing`. Pre-2026-04-27 the entire body was guarded by `if (index >= 0)` and the `else` branch did nothing — the dialog closed successfully-looking and the edit silently vanished.

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

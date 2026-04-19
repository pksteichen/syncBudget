---
name: Share-intent routing when dialogs are open
description: How ACTION_SEND / ACTION_SEND_MULTIPLE images are routed depending on which dialog or screen is active
type: feedback
originSessionId: a00b436a-3ced-4e78-a40e-780a8f5acff8
---
When a user shares image(s) to BudgeTrak while the app is backgrounded, the
receiving behavior depends on what dialog (if any) is active when BudgeTrak
returns to foreground.

**Why:** Before 2026-04-19, sharing to BudgeTrak with any dialog already open
just foregrounded the app and silently dropped the image — user saw nothing
happen. Worse edge cases included competing dialogs overlaying in-flight RE/IS
edits, or the CSV import dedup/match flow getting interrupted.

**How to apply (routing rules, implemented in `MainViewModel.consumePendingSharedImages`):**

1. **Any DIALOG or in-flight multi-step user process** → discard URIs and show
   `strings.settings.shareBlockedByOpenDialog`. Being on a non-dashboard SCREEN
   (Transactions list, Help pages, Settings, etc.) by itself is NOT a blocker;
   the share handler navigates to the dashboard before opening the Add
   Expense dialog in those cases.
   `anyNonTransactionDialogOpen()` checks:
   `dashShowManualDuplicateDialog`, `dashShowRecurringDialog`,
   `dashShowAmortizationDialog`, `dashShowBudgetIncomeDialog`,
   `showBackupPasswordDialog`, `showDisableBackupDialog`, `showRestoreDialog`,
   `showSavePhotosDialog`, pending-amount-update prompts, and
   `csvImportInProgress` (set by TransactionsScreen while the sequential CSV
   duplicate-check phase is active; losing the match-queue progress was the
   one "screen-level" case worth protecting). Other CSV stages (format select,
   parsing, complete) are safe — navigation just discards the UI and the user
   re-imports if they care.
2. **TransactionDialog already open** → route URIs into that dialog's next
   empty receipt slots, silently discard overflow beyond 5, fire
   `strings.settings.shareOverflowDiscarded` if any overflow occurred.
   Transaction dialog signals its aliveness via
   `vm.transactionDialogOpenCount` (incremented/decremented by a
   `DisposableEffect` inside `TransactionDialog`).
3. **No dialog open** → set `currentScreen = "main"`, process first URI to a
   receiptId (slot 1 seed via `pendingSharedReceiptId` + `initialReceiptId1`),
   open the Add Expense dialog, let the dialog's absorber fill slots 2-5 from
   the remainder. Overflow toast same as case 2. Works whether the user was
   on the dashboard or anywhere else when the share arrived.

Free (non-paid) users still see the dialog open, but every shared photo is
discarded and the 5-second "upgrade required" toast fires (existing behavior
preserved). Shares arriving mid-TransactionDialog for a Free user clear the
URIs and toast without interrupting the dialog.

**Multi-share (`ACTION_SEND_MULTIPLE`):** also wired as of 2026-04-19. The
manifest now declares both `ACTION_SEND` (image/* + application/pdf) and
`ACTION_SEND_MULTIPLE` (image/*). `extractSharedImageUris` returns a `List<Uri>`
covering both forms.

**Absorption lives in `TransactionDialog`**, not in the VM. The VM hands a live
`pendingSharedImageUris` list to the dialog via parameter. A `LaunchedEffect`
keyed on the list size processes up to (5 - occupied) URIs per arrival, calls
`onConsumeSharedImageUris` to clear the VM list (before processing, to avoid
double-consume races), and fires `onShareOverflow` if needed. This mirrors the
existing `dialogGalleryLauncher` multi-pick pipeline so behavior stays
consistent whether the user gallery-picked or shared.

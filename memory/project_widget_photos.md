---
name: Widget photo support (designed, not yet implemented)
description: Future work — add receipt photo capture to WidgetTransactionActivity's quick-add dialog, with orphan cleanup on dismiss
type: project
---

Widget photo support is **designed but not yet implemented**.

## Why
Users should be able to attach receipt photos when adding transactions from the home-screen widget, not just from the main app.

## Scope
- Camera icon in `WidgetTransactionActivity`'s dialog header (paid / subscriber only).
- Photo thumbnail bar below the header, shows after capture.
- Track photos in local state (`addModeReceiptId1..5`, `addedPhotoIds`).
- Include `receiptId*` in the Transaction when saving.
- Orphan cleanup: delete captured photos from disk if the dialog is dismissed without saving.
- Confirmation dialog on dismiss if photos or data have been entered.

## Key differences from main app
- Widget uses its own `ComponentActivity` with an inline Compose dialog — not the reusable `TransactionDialog`.
- `isPaidUser` gate is at `WidgetTransactionActivity.kt:~128` (current location; verify before editing).
- Save path is the Activity's own `saveTransaction()` (distinct from MainViewModel's pipeline).
- Needs camera/gallery `ActivityResultContracts` launchers registered in the Activity (Compose registries don't carry across activities).

## Not related to widget refresh
Widget refresh is already handled by `BackgroundSyncWorker` (not a dedicated worker — the old `WidgetRefreshWorker` was retired in the 2026-03-29 background-sync refactor). `BudgetWidgetProvider.onUpdate()` schedules `BackgroundSyncWorker`, which takes care of cash recompute + widget update + period refresh + Firestore sync together.

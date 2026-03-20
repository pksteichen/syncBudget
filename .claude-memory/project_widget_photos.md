---
name: Widget photo support design plan
description: Future task — add photo capture to WidgetTransactionActivity's quick-add dialog, with orphan cleanup on dismiss
type: project
---

Widget photo support — designed but not yet implemented.

**Why:** Users should be able to capture receipt photos when adding transactions from the home screen widget, not just from the main app.

**How to apply:** When implementing, follow the same pattern used in TransactionDialog for add-mode photos.

**Scope:**
- Add camera icon to WidgetTransactionActivity dialog header (paid/subscriber users only)
- Add photo thumbnail bar below header (shows after capture)
- Track photos in local state (addModeReceiptId1-5, addedPhotoIds)
- Include receiptIds in the Transaction when saving
- Orphan cleanup: delete captured photos from disk if dialog dismissed without saving
- Confirmation dialog on dismiss if photos/data entered
- File: `app/src/main/java/com/syncbudget/app/widget/WidgetTransactionActivity.kt` (~998 lines, has its own inline dialog system separate from TransactionDialog)

**Key differences from main app:**
- Widget uses its own ComponentActivity, not the TransactionsScreen composable
- Widget dialog is inline Compose, not the reusable TransactionDialog function
- Widget has `isPaidUser` check at line 128
- Widget saves via its own `saveTransaction()` method (line 923)
- Need camera/gallery ActivityResultContracts launchers registered in the Activity

---
name: Pre-launch TODO
description: Prioritized list of items to complete before Play Store publication
type: project
---

## Must-fix before launch

1. ~~**Firebase security rules**~~ DONE (April 3) — members/{auth.uid} subcollection, Firestore/Storage/RTDB rules updated

2. **App Check → MEETS_DEVICE_INTEGRITY** — Currently `MEETS_BASIC_INTEGRITY`. Deferred until overnight token investigation resolves. One-click Firebase Console change.

3. ~~**SecurePrefs plaintext fallback**~~ DONE (April 3) — Throws on failure with KeyStore retry, no plaintext fallback

3b. **Health check after listener exhaustion** — When all collection listeners exhaust retries (10x PERMISSION_DENIED), force-refresh App Check token, then run `getGroupHealthStatus`. If group dissolved/missing with fresh token → evict. If group exists → restart listeners. Gives objective evidence rather than assuming dissolution from PERMISSION_DENIED.

## Should-fix before launch

4. ~~**Firebase Crashlytics**~~ DONE (April 3) — Added with token/sync diagnostics, PERMISSION_DENIED non-fatals, custom keys

5. ~~**ProGuard/R8 minification**~~ VERIFIED (April 3) — Already configured: minifyEnabled=true, shrinkResources=true, keep rules present

6. ~~**Unscoped coroutines**~~ DONE (April 3) — Replaced with vm.launchIO (ViewModel-scoped)

## Nice to have

7. **Thumbnail loading on IO thread** — Currently synchronous bitmap decode in LazyColumn composition (TransactionsScreen.kt:1113). Switch to `LaunchedEffect` + `withContext(IO)` to prevent scroll jank with many photos.

8. **Batched Firestore writes** — Individual `push()` calls per record in SyncWriteHelper. A batch write helper for bulk operations (category remap, migrations) would reduce round-trips and Firestore cost.

9. **Privacy policy** — Play Store requires one. Should cover: anonymous auth UID, encrypted financial data in Firestore, local storage, device-to-device sharing, server cannot read encrypted data.

## Low priority (code quality, no user impact)

11. **Consolidate matching chain** — Triplicated across Dashboard dialogs, TransactionsScreen, and WidgetTransactionActivity. Extract to ViewModel callback-based architecture.

12. **Parallel pending receipt uploads** — `processPendingUploads` in ReceiptSyncManager uploads sequentially. Use chunked parallel pattern (like download path) for faster batch photo sync.

13. **Consolidate 7 save functions** — (same as #9, moved here since it's pure code quality)

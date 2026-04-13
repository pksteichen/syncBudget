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

## FCM sync-push cost optimizations (from 2026-04-12 estimate)

At 40K groups the current design is ~$150/mo. These drop it toward ~$10–15/mo.

14. **Cache FCM tokens in a group-level field** — `onSyncDataWrite` currently reads the entire `groups/{gid}/devices` subcollection on every sync write (~80M Firestore reads/mo at 40K groups, ~$48/mo). Replace with a single `groups/{gid}.fcmTokens: {deviceId: token}` map kept in sync on device add/remove. Saves ~$43/mo.

15. **Server-side debounce on `sync_push`** — fan-out fires once per write; a 500-row CSV import sends 500 FCM per recipient. Add a per-(groupId, targetDeviceId) cooldown in the Cloud Function (Firestore or Redis lock, e.g. 10 s window) so bursts collapse at the server. Clients already dedupe with `enqueueUniqueWork(KEEP)`, but server dedupe also cuts FCM count + invocations.

16. **Smarter `presenceHeartbeat` scan** — currently walks every group's `presence` node every 15 min (~75 GB/mo RTDB download at 40K groups, $75/mo). Replace with an indexed query so we fetch *only* stale records, not all ~88K then filter. Drops from O(all devices) to O(stale devices) — ~95 % reduction.

   Two implementation paths:
   - **RTDB flat index**: maintain a parallel flat node like `presence_index/{groupId}__{deviceId}: { lastSeen }` with `.indexOn: ["lastSeen"]` in RTDB rules. One query `orderByChild("lastSeen").endAt(cutoff)` returns every stale device across the fleet. Keep it in sync via `updateChildren({...})` alongside each presence write — effectively free, since the device is already writing its presence.
   - **Firestore index**: maintain a `stale_candidates` Firestore collection instead. Firestore supports range indexes on any field natively, so `where("lastSeen", "<", cutoff)` works out of the box. Slightly more write-side cost (separate Firestore set) but simpler schema — we're already paying for Firestore.

   Either path also eliminates #17's timeout risk (no loop to run).

17. **Parallelize / shard `presenceHeartbeat`** — sequential `for…await` inside the function means at ~50 ms/group the 60-second default timeout is hit at ~1.2K groups, and the hard 9-min Gen-1 ceiling at ~10K groups. Current implementation will time out past that. Fix options: (a) batch with `Promise.all` in chunks of ~100 groups, (b) shard by group ID hash and run multiple parallel cron functions, or (c) fold into #16 (indexed query eliminates the loop entirely). #16 is the preferred fix.

18. **Detect OEM FCM blocking + user prompt** — some OEMs (Xiaomi, Huawei, aggressive Samsung profiles) silently drop FCM or kill the process before our handler runs. We can infer this from round-trip silence: the Cloud Function writes `lastHeartbeatSentAt` on the device doc before sending each FCM, then on the next cron tick compares to RTDB `lastSeen`. After 3 consecutive misses (~45 min), set `fcmSuspectedBlocked: true`. Client reads the flag on launch and shows a modal guiding the user to whitelist BudgeTrak. Suppress with a 7-day `lastBlockedPromptAt` local pref so we don't nag. **Tune thresholds after a week of live heartbeat data** — "3 misses" is a guess; real noise floor TBD.

19. **Settings deep-link for battery/autostart whitelist** — UI half of #18. Try in order: (a) `ACTION_IGNORE_BATTERY_OPTIMIZATIONS` (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission — Play Store review item), (b) OEM-specific `ComponentName` intents for Samsung "Deep Sleeping Apps", Xiaomi "Autostart", Huawei "Protected Apps", (c) fallback to `ACTION_APPLICATION_DETAILS_SETTINGS`. Branch on `Build.MANUFACTURER`. Test matrix: Pixel, Samsung, Xiaomi, OnePlus. Fragile but industry-standard; worth the effort for widget reliability.

## Low priority (code quality, no user impact)

11. **Consolidate matching chain** — Triplicated across Dashboard dialogs, TransactionsScreen, and WidgetTransactionActivity. Extract to ViewModel callback-based architecture.

12. **Parallel pending receipt uploads** — `processPendingUploads` in ReceiptSyncManager uploads sequentially. Use chunked parallel pattern (like download path) for faster batch photo sync.

13. **Consolidate 7 save functions** — (same as #9, moved here since it's pure code quality)

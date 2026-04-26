---
name: Pre-launch TODO
description: Outstanding items before Play Store publication. Completed items are removed — their design + rationale live in the code and the relevant memory files.
type: project
originSessionId: ea9e173a-ca3d-4f87-b67a-ceac73953250
---
## Pre-launch

11. **Period-boundary scheduling for BackgroundSyncWorker (Phase 3 + 4)** — replace the 15-min periodic with one-shots scheduled at the next period boundary. Solo users go from ~96 worker runs/day to ~4 (one per period), big battery + CPU win. Sync users follow once Phase 1 (`runFullSyncInline`, shipped 2026-04-25) is verified reliable overnight (≥ 95% of FCM wakes complete inline on Samsung + Pixel). If FCM-inline doesn't hit that bar, do Phase 4-alternative (keep periodic but no-op when fresh FCM-inline ran in last 30 min). Full plan in [`project_period_boundary_scheduling.md`](project_period_boundary_scheduling.md).

## Post-launch (data-driven)

2. **App Check device integrity tightening** — Currently set to "Don't explicitly check device integrity level" (most permissive). The `PLAY_RECOGNIZED` app-integrity verdict (enabled) is what actually blocks pirated/re-signed APKs — that's intact. Device integrity is a separate axis. Tightening order if abuse appears post-launch: MEETS_BASIC_INTEGRITY (rejects obvious tampering / emulator+root), then MEETS_DEVICE_INTEGRITY (also rejects rooted real devices + custom ROMs). Don't go to MEETS_STRONG_INTEGRITY (rejects older real devices). Decide after 2-4 weeks of live PERMISSION_DENIED Crashlytics data; per-field encryption means a leaked App Check token can't decrypt data anyway, so device integrity strictness is anti-abuse only, not data protection.

3. **Google Play Billing + server-side purchase verification** — Local paid-feature flags (`isPaidUser` in SharedPreferences) are bypassable by anyone who can decompile + re-sign the APK. App Check already stops pirated APKs from using SYNC / cloud receipts / admin features (unrecognized app signature), but local features (CSV/PDF export, receipt capture, unlimited widget transactions, cash-flow simulation) don't require server calls and aren't protected. Fix:
   - Integrate Google Play Billing Library in the app.
   - New Cloud Function (e.g. `verifyPurchase`) that calls Play Developer API `purchases.products.get` with our service-account creds to confirm the purchase token.
   - On verified purchase, write `isPaidUser: true` to a per-user Firestore doc that only App Check-validated clients can read.
   - App reads the server-authoritative flag; local flag becomes a cache only. Modified APK that forges the local flag can't read the server flag (App Check blocks it), so features stay locked.
   - Handle restore flow carefully (new device, new install, cross-platform).
   - Probably a week of focused work. Not blocking launch — small budgeting apps don't attract mass piracy — but worth doing before any paid-feature revenue is material.

4. **Optional cheap anti-piracy: runtime signature pinning** — ~20 lines in `BudgeTrakApplication.onCreate`: read `packageManager.getPackageInfo(...).signingInfo.apkContentsSigners[0]`, SHA-256 it, compare to the expected hash. Refuse to run if mismatched. Catches naive repackaging, determined attackers patch it out. Low cost, low ceiling.

## FCM sync-push cost optimizations (from 2026-04-12 estimate)

At 40K groups the current design is ~$150/mo. These drop it toward ~$10-15/mo. Not urgent until we approach that scale.

5. **Cache FCM tokens in a group-level field** — `onSyncDataWrite` currently reads the entire `groups/{gid}/devices` subcollection on every sync write (~80M Firestore reads/mo at 40K groups, ~$48/mo). Replace with a single `groups/{gid}.fcmTokens: {deviceId: token}` map kept in sync on device add/remove. Saves ~$43/mo.

6. **Server-side debounce on `sync_push`** — fan-out fires once per write; one period refresh on a peer device produces ~42 FCMs per recipient (observed in the 2026-04-13 overnight dump at 05:03). Add a per-(groupId, targetDeviceId) cooldown in the Cloud Function (Firestore or Redis lock, e.g. 10 s window) so bursts collapse at the server. Clients already dedupe with `enqueueUniqueWork(KEEP)`, but server dedupe also cuts FCM count + invocations.

7. **Smarter `presenceHeartbeat` scan** — currently walks every group's `presence` node every 15 min (~75 GB/mo RTDB download at 40K groups, $75/mo). Replace with an indexed query so we fetch *only* stale records, not all ~88K then filter. Drops from O(all devices) to O(stale devices) — ~95 % reduction.

   Two implementation paths:
   - **RTDB flat index**: maintain a parallel flat node like `presence_index/{groupId}__{deviceId}: { lastSeen }` with `.indexOn: ["lastSeen"]` in RTDB rules. One query `orderByChild("lastSeen").endAt(cutoff)` returns every stale device across the fleet. Keep it in sync via `updateChildren({...})` alongside each presence write — effectively free, since the device is already writing its presence.
   - **Firestore index**: maintain a `stale_candidates` Firestore collection instead. Firestore supports range indexes on any field natively, so `where("lastSeen", "<", cutoff)` works out of the box. Slightly more write-side cost (separate Firestore set) but simpler schema — we're already paying for Firestore.

   Either path also eliminates #8's timeout risk (no loop to run).

8. **Parallelize / shard `presenceHeartbeat`** — sequential `for…await` inside the function means at ~50 ms/group the 60-second default timeout is hit at ~1.2K groups, and the hard 9-min Gen-1 ceiling at ~10K groups. Current implementation will time out past that. Fix options: (a) batch with `Promise.all` in chunks of ~100 groups, (b) shard by group ID hash and run multiple parallel cron functions, or (c) fold into #7 (indexed query eliminates the loop entirely). #7 is the preferred fix.

9. **Detect OEM FCM blocking + user prompt** — some OEMs (Xiaomi, Huawei, aggressive Samsung profiles) silently drop FCM or kill the process before our handler runs. We can infer this from round-trip silence: the Cloud Function writes `lastHeartbeatSentAt` on the device doc before sending each FCM, then on the next cron tick compares to RTDB `lastSeen`. After 3 consecutive misses (~45 min), set `fcmSuspectedBlocked: true`. Client reads the flag on launch and shows a modal guiding the user to whitelist BudgeTrak. Suppress with a 7-day `lastBlockedPromptAt` local pref so we don't nag. **Tune thresholds after a week of live heartbeat data** — "3 misses" is a guess; real noise floor TBD.

10. **Settings deep-link for battery/autostart whitelist** — UI half of #9. Try in order: (a) `ACTION_IGNORE_BATTERY_OPTIMIZATIONS` (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission — Play Store review item), (b) OEM-specific `ComponentName` intents for Samsung "Deep Sleeping Apps", Xiaomi "Autostart", Huawei "Protected Apps", (c) fallback to `ACTION_APPLICATION_DETAILS_SETTINGS`. Branch on `Build.MANUFACTURER`. Test matrix: Pixel, Samsung, Xiaomi, OnePlus. Fragile but industry-standard; worth the effort for widget reliability.

## Completed + documented elsewhere (as of 2026-04-13)
- Firebase security rules (membership-based) → MEMORY.md "Firebase Backend", spec_group_management.md.
- SecurePrefs no plaintext fallback → architecture.md (SecurePrefs line).
- Firebase Crashlytics + non-fatals + custom keys + HEALTH_BEACON → spec_diagnostics.md.
- ProGuard / R8 minification → `app/build.gradle.kts` (`isMinifyEnabled = true`).
- ViewModel-scoped coroutines (`vm.launchIO`) → architecture.md (MainViewModel line).
- Health-check-after-PERMISSION_DENIED → implemented as `triggerFullRestart()` (App Check force-refresh + restart all listeners, 30 s debounce) → MEMORY.md SYNC section.
- Batched Firestore writes (500-op chunks + retry fallback) → MEMORY.md "Save functions" section.
- Privacy policy → `techadvantagesupport.github.io/budgetrak-legal/privacy`.
- Consolidated 7 save functions into generic `saveCollection<T>` → MEMORY.md "Save functions".
- Parallel pending receipt uploads — already done: `ReceiptSyncManager.processPendingUploads` uses `chunked(5)` with `async` + `await` in a `coroutineScope` (`ReceiptSyncManager.kt:95-135`), mirroring the download path at line 340.
- Consolidate matching chain — retired 2026-04-13 after analysis. The deterministic match-finders (`findDuplicates`, `findRecurringExpenseMatches`, etc. in `DuplicateDetector.kt`) are already shared. The April 3 commit `a394a3a` made all 5 entry points (dashboard, screen add, screen edit, screen CSV import, widget) agree on the same type-based order. Further consolidation of the **orchestration** was considered but rejected: each entry point has different post-match side effects (addAndScroll / importIndex / addTransactionWithBudgetEffect / separate-Activity repo loads / free-tier 1/day widget cap) that can't share a single call signature without VM becoming aware of screen-local state. Design note added above `runLinkingChain` in `MainViewModel.kt` so this doesn't get re-proposed.
- Thumbnail loading on IO thread — retired 2026-04-13. Already implemented at `TransactionsScreen.kt:1123-1127` inside `LaunchedEffect` with `withContext(Dispatchers.IO)`, keyed on the 5 `receiptId*` fields + `photoThumbRefreshKey`. The original todo's line pointer `:1113` was stale. A follow-up LRU-cache polish was considered and rejected: the current sub-millisecond re-decode cost already happens off the main thread, and any cache adds invalidation complexity (rotation save, delete, cloud-received updates) without meaningful UX benefit. Revisit only if scroll-jank reports appear and profile to photos.

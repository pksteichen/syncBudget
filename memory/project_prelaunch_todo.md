---
name: Pre-launch TODO
description: Outstanding items before Play Store publication. Completed items are removed — their design + rationale live in the code and the relevant memory files.
type: project
originSessionId: ea9e173a-ca3d-4f87-b67a-ceac73953250
---
## Pre-launch

11. **Period-boundary scheduling for BackgroundSyncWorker (Phase 3 + 4)** — replace the 15-min periodic with one-shots scheduled at the next period boundary. Solo users go from ~96 worker runs/day to ~4 (one per period), big battery + CPU win. Sync users follow once Phase 1 (`runFullSyncInline`, shipped 2026-04-25) is verified reliable overnight (≥ 95% of FCM wakes complete inline on Samsung + Pixel). If FCM-inline doesn't hit that bar, do Phase 4-alternative (keep periodic but no-op when fresh FCM-inline ran in last 30 min). Full plan in [`project_period_boundary_scheduling.md`](project_period_boundary_scheduling.md).

13. **Launch monetization workstream (AdMob + Play Billing Layer 1 + license testing)** — start as soon as listing screenshots are uploaded. Three coordinated sub-tasks; together they unblock public production launch with full monetization on day one. Layer 2 server-side verification (anti-piracy) stays in item #3 below as a post-launch tightening per the small-app risk profile.

    **13a. AdMob real integration (~1–2 days).** Replace the placeholder `Box` at the top of the main column with `AndroidView` wrapping a real `AdView` at 320×50. Wire lifecycle (resume/pause/destroy via `DisposableEffect`). Use Google test ad unit IDs (`ca-app-pub-3940256099942544/6300978111`) during dev; swap to real production unit IDs only when promoting to Production. Keep the existing `if (!vm.isPaidUser)` gate so Paid + Subscriber stay ad-free. The IAB MRC viewability architecture and `AdAwareDialog` offsetting are already in place — no surrounding refactor needed. Ship as `v2.10.06` to Internal Testing.

    **13b. Play Billing Layer 1 — client-side IAP. SHIPPED in v2.10.10 (2026-05-08, code-only — pending SKU creation + ~24h propagation + license-tester validation per item 13c).**
    - Products: `paid_upgrade` (INAPP, non-consumable, $9.99) + `subscriber` (SUBS, $4.99/mo).
    - Library: `com.android.billingclient:billing-ktx:7.1.1`.
    - Files: `data/billing/BillingService.kt` (~200 LOC), `data/billing/BillingProducts.kt` (constants).
    - Integration: `MainViewModel.refreshBillingState()` runs on init + onResume + after purchase flow + Restore Purchases tap. Maps Play purchases → `isPaidUser` / `isSubscriber` / `subscriptionExpiry = purchase.purchaseTime + 30d`.
    - Removed the v2.10.00 release-build override (`MainViewModel.init` no longer forces `isPaidUser = isSubscriber = true` on `!BuildConfig.DEBUG`).
    - **7-day TTL on cached entitlement** bounds the offline-refund attack window: if `now - lastSuccessfulBillingCheck > 7 days`, in-memory `isPaidUser` / `isSubscriber` are forced to false until the next successful Play query. Layer 2 (post-launch item #3 below) closes the loophole entirely.
    - Settings UI: new Subscription section with current-tier label + upgrade buttons (live Play prices) + Restore Purchases. Debug-only "Override Google Billing" checkbox above the existing manual paid/subscriber toggles — when off, those greys out and Play is authoritative; when on, they win and Play queries skip writing entitlement state.
    - Existing 7-day grace period on admin sub expiry (`MainViewModel.kt:2165-2188`) integrates cleanly: Play-driven `subscriptionExpiry` is what the admin pushes to `groups/{gid}.subscriptionExpiry`; existing eviction / dashboard-popup / claim-admin flows unchanged. See `memory/project_play_billing_integration.md` for the full integration mapping.

    Pending validation (covered by item 13c):
    - SKU catalog propagation (~24h after Play Console product creation).
    - License-tester end-to-end checklist (buy / refund / cancel / restore flows).

    **13c. License testing setup + validation. COMPLETE 2026-05-11.** Paul + Kim added as license testers. All six validation paths verified on release build (v2.10.18) over a single billing_dump.txt session:

    - [x] Buy `paid_upgrade` → `isPaidUser = true`, paid features unlock.
    - [x] Refund test purchase via Play Console → flag drops. Debug: instant. Release: **Play-Console-admin path has 22h+ propagation lag**; see `project_play_billing_integration.md` "License-tester refund propagation — A/B finding (2026-05-11)". Open follow-up flagged for 2026-05-12: revisit whether the stuck paid_upgrade refund eventually clears (now 22h+ and counting on Paul's device).
    - [x] Buy `subscriber` → `isSubscriber = true`, subscriber features unlock.
    - [x] Subscriber gets paid features too (verified via `isPaidUser || isSubscriber` pattern in code review).
    - [x] Accelerated renewal stays subscribed (27-min dump showed `isAutoRenewing = true` after 5+ renewal cycles).
    - [x] Cancel sub via Play Store app → `isSubscriber = false` within minutes, no Restore Purchases tap needed.
    - [x] Fresh install / same account → init-time `refreshBillingState` auto-restores entitlement.

    **Production refund support guidance**: real users using Play Store app's in-Order Refund button hit the fast pipeline (analogous to the sub-cancel path verified above, propagates within minutes). Refunds we issue from Play Console admin have the 24h+ lag. Encourage Play-Store-app self-service in refund replies (see `reference_refund_workflow.md`).

    **Tester instructions caveat (still relevant for future tester onboarding):** the first IAP per Google account requires a payment method on file even for license testers (won't be charged). Some testers think they're being charged — flag this clearly in the tester onboarding email.

## Post-launch (data-driven)

2. **App Check device integrity tightening** — Currently set to "Don't explicitly check device integrity level" (most permissive). The `PLAY_RECOGNIZED` app-integrity verdict (enabled) is what actually blocks pirated/re-signed APKs — that's intact. Device integrity is a separate axis. Tightening order if abuse appears post-launch: MEETS_BASIC_INTEGRITY (rejects obvious tampering / emulator+root), then MEETS_DEVICE_INTEGRITY (also rejects rooted real devices + custom ROMs). Don't go to MEETS_STRONG_INTEGRITY (rejects older real devices). Decide after 2-4 weeks of live PERMISSION_DENIED Crashlytics data; per-field encryption means a leaked App Check token can't decrypt data anyway, so device integrity strictness is anti-abuse only, not data protection.

3. **Play Billing Layer 2 — server-side purchase verification (refund-lag fix + partial anti-piracy) — LANDED 2026-05-11.** Cloud Function `verifyPurchase` (Gen 2 callable, us-central1, `enforceAppCheck: true`, runs as `play-publisher@sync-23ce9.iam.gserviceaccount.com` which holds Play Console "View financial data") calls Play Developer API `purchases.products.get` (INAPP) or `purchases.subscriptionsv2.get` (SUBS) and returns `{ verified, purchaseState, expiryTimeMillis?, orderId? }`. Defined in `functions/index.js` alongside the existing Gen 1 functions. **Android client**: `data/billing/EntitlementVerifier.kt` wraps the call, caches results 24h in `entitlement_verifier` prefs (keyed by hashed purchase token); `MainViewModel.refreshBillingStateWithState` invokes it for every PURCHASED-state purchase from Layer 1 and reconciles via `reconcileEntitlement`:
   - Verified → entitled (local flag true).
   - Refunded (purchaseState=1, expired sub, Play API 410/404) → NOT entitled, even if local BillingClient still says PURCHASED. **This is the refund-lag fix** — local Play Store caches lag Console-admin refunds 24h+; the Developer API reflects them in minutes.
   - Unreachable (network/App Check fail) → fall back to cached server result if <24h old, else trust local. Better to leave a paying user briefly entitled during a network blip than revoke on a network failure.
   - For subs, `subscriptionExpiry` is overwritten with the Developer-API `expiryTimeMillis` when verified (more accurate than `purchaseTime + 30d`).
   - Billing dump (`/Download/BudgeTrak/support/billing_dump.txt`) gains a "Layer 2 server verification" section showing per-product VERIFIED / REFUNDED / UNREACHABLE state.
   - Scope NOT covered: the App-Check-gated Firestore entitlement doc that would make `isPaidUser` truly server-authoritative against decompile-and-forge attacks. That remains a follow-up — current implementation closes the refund-lag bug but an attacker who skips the verifier call entirely still gets local-flag forgery. Layer 2.5 plan: write `entitlements/{anonymousUid}` from the Cloud Function, gate read on App Check, treat local flag as cache only.

4. **Cheap anti-piracy: runtime signature pinning — LANDED + ARMED 2026-05-11.** `BudgeTrakApplication.verifyAppSignature()` hashes the APK's signing certificate(s) at startup and matches against `expectedApkSignatureSha256` (pinned to the Play App Signing key SHA-256 from Play Console → App integrity → Play app signing → Settings). Mismatch → `recordNonFatal` to Crashlytics + `kotlin.system.exitProcess(0)`. Skipped in `BuildConfig.DEBUG`. Multi-signer aware (rotation-safe). Logs observed hash to logcat as `AppSignature: observed=...` on every cold start regardless of match result, so we can spot regressions after Play app signing key rotation. **If Play rotates the signing key** (rare; typically only after a security incident or explicit dev request), update the constant in `BudgeTrakApplication.kt` and ship before the rotation takes effect. Catches naive repackaging only; determined attackers strip the check by smali patching — Layer 2 server-side verification (item #3 above) closes the loophole properly.

5. **In-house fallback ad when AdMob can't load — LANDED 2026-05-11.** Five-ad cycle (fixed order, index resumes across AdMob recoveries) renders in place of AdMob on `onAdFailedToLoad`. Three Paid-tier promos (Receipts, Exports, Simulation) + two Subscriber-only (SYNC, OCR). Pure Compose at `ui/components/InHouseAd.kt`, matches AdMob layout dimensions (64dp small / 144dp medium with BudgeTrak app icon in the 160×120 media area). Yellow "Upgrade" badge in place of "Ad" (1st-party content, not 3rd-party). Tap routes to `vm.launchPaidUpgrade(activity)` or `vm.launchSubscribe(activity)` per the ad's tier. Doubles as anti-piracy: free user blocking internet still sees our upgrade promo.

   Strings live in `AppStrings.ads` (`InHouseAdStrings` data class) — EN + ES populated; translation context entries in `TranslationContext.ads`. To add/edit ad copy: change the `*Headline` / `*Body` strings in both `EnglishStrings.kt` and `SpanishStrings.kt`. To rotate which features are advertised: edit `InHouseAds` list in `InHouseAd.kt` (id, icon, tier) — each new id needs matching strings + a branch in `headlineFor` / `bodyFor`.

12. **BIS annual self-classification report (US Encryption Export Compliance)** — On the Play Console "US export laws" question we declared use of License Exception ENC for standard encryption (TLS, AES, ChaCha20-Poly1305). Under EAR §740.17(e), products using ENC must file an annual self-classification report to the Bureau of Industry and Security listing each exporting product, by **February 1 each year**, covering the previous calendar year. Submit via email to `crypt-supp@bis.doc.gov` AND `enc@nsa.gov` as a CSV/spreadsheet using the BIS template at https://www.bis.doc.gov/index.php/policy-guidance/encryption (look for "Self-classification report"). Required fields: product name, model/version, manufacturer, ECCN (likely 5D992.c for software), encryption description (e.g. "TLS 1.2/1.3, AES-256, ChaCha20-Poly1305"), and authorization type (ENC). Realistically zero enforcement against indie apps, but technically required once BudgeTrak is distributed outside the US (which Play does automatically). First report due **2027-02-01** for any 2026 distribution. Set a calendar reminder for January 2027.

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
- Privacy policy → `techadvantagesupport.github.io/privacy` (moved from `/budgetrak-legal/privacy` 2026-04-27 to org root Pages site).
- Consolidated 7 save functions into generic `saveCollection<T>` → MEMORY.md "Save functions".
- Parallel pending receipt uploads — already done: `ReceiptSyncManager.processPendingUploads` uses `chunked(5)` with `async` + `await` in a `coroutineScope` (`ReceiptSyncManager.kt:95-135`), mirroring the download path at line 340.
- Consolidate matching chain — retired 2026-04-13 after analysis. The deterministic match-finders (`findDuplicates`, `findRecurringExpenseMatches`, etc. in `DuplicateDetector.kt`) are already shared. The April 3 commit `a394a3a` made all 5 entry points (dashboard, screen add, screen edit, screen CSV import, widget) agree on the same type-based order. Further consolidation of the **orchestration** was considered but rejected: each entry point has different post-match side effects (addAndScroll / importIndex / addTransactionWithBudgetEffect / separate-Activity repo loads / free-tier 1/day widget cap) that can't share a single call signature without VM becoming aware of screen-local state. Design note added above `runLinkingChain` in `MainViewModel.kt` so this doesn't get re-proposed.
- Thumbnail loading on IO thread — retired 2026-04-13. Already implemented at `TransactionsScreen.kt:1123-1127` inside `LaunchedEffect` with `withContext(Dispatchers.IO)`, keyed on the 5 `receiptId*` fields + `photoThumbRefreshKey`. The original todo's line pointer `:1113` was stale. A follow-up LRU-cache polish was considered and rejected: the current sub-millisecond re-decode cost already happens off the main thread, and any cache adds invalidation complexity (rotation save, delete, cloud-received updates) without meaningful UX benefit. Revisit only if scroll-jank reports appear and profile to photos.

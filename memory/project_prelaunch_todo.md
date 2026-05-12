---
name: Pre-launch TODO
description: Outstanding items before Play Store production launch. Completed items are removed — their design + rationale live in the code and the relevant memory files.
type: project
originSessionId: ea9e173a-ca3d-4f87-b67a-ceac73953250
---

## Pre-launch

1. **AdMob production ad unit swap.** Currently running TEST app ID + TEST Native Advanced ad unit IDs. Required before flipping to Production:
   - **AdMob Console:** create production app + **two Native Advanced ad units** (one for the small phone template, one for the medium tablet template). Copy each `ca-app-pub-XXXXXXXX/XXXXXXX` (ad unit ID) and the production app ID `ca-app-pub-XXXXXXXX~XXXXXXX`.
   - **`AndroidManifest.xml`:** replace the TEST `APPLICATION_ID` meta-data with the real app ID.
   - **`MainActivity.kt`:** replace the TEST native ad unit ID in `AdLoader.Builder(this@MainActivity, "<TEST>")`. Tier selection branches on `isMediumTier` to choose phone vs tablet ad unit ID.
   - **`app-ads.txt`** already published at `https://techadvantagesupport.github.io/app-ads.txt` — covers all ad units under the publisher account, no per-unit change needed.
   - **Optional pre-production:** `MobileAds.openAdInspector(this) {}` from a debug menu to verify served-creative diagnostics on real devices; add tester device IDs to `RequestConfiguration.Builder().setTestDeviceIds(...)` so live debugging doesn't accrue impressions / risk invalid-traffic flags.
   - Full details: `project_ad_implementation.md` "Production-promotion swap checklist".

2. **Period-boundary scheduling — Phase 4-alt verification (interim data 2026-05-12 = inconclusive but informative; full study deferred to ~2-week window after 2.10.24).** Phase 1 (inline FCM dispatch), Phase 3 (solo users → boundary one-shot), and Phase 4-alt (sync users keep periodic but slim Tier 3 fast-exit) all shipped 2026-04-25.

   **2026-05-12 interim finding (one device / ~24h, Paul's S22):** 0 of 10 overnight Tier 3 fires took the slim path. The slim-gate code is correct (`BackgroundSyncWorker.kt:280-287, 539-546`): it requires `lastInlineSyncCompletedAt < 30 min ago`, written only by FCM-prefixed successful runs. **The miss is by design, not a bug.** Pattern observed: from ~02:00 to ~10:00 Samsung's Doze killed the VM and serialized FCM heartbeats behind a running Worker (saw 7+ `FCM-heartbeat: skipped (another run already in progress)` log lines). When Tier 3 next fired periodically, `lastInlineSyncCompletedAt` was ~5h stale → full sync correctly required (skipping would risk 8h of undetected peer changes). **Slim path only delivers savings when FCM stays alive concurrently with the Worker periodic.** On Samsung overnight that condition does not hold; the "wasted" full Tier 3 fires are real insurance, not optimization gaps.

   **Implications:**
   - **The verification target (≥95% overnight FCM-inline reliability) is unlikely to be met on Samsung devices in deep Doze.** Phase 4 full upgrade (boundary one-shots for sync users) would replace the 15-min periodic insurance with one-per-period — losing the very coverage that today's full Tier 3 fires provide.
   - **Recommendation: stay on Phase 4-alt indefinitely for sync users.** Solo users keep their already-shipped boundary one-shot path (Phase 3). Don't pursue Phase 4 full.
   - **Battery accounting:** Phase 4-alt's slim path is a best-effort gain that arrives when FCM and Worker concur. When they don't (overnight Doze), full Tier 3 every 15 min is correct behavior. Move the design framing from "should deliver consistent slim-path savings" to "delivers savings opportunistically, full path is the default + insurance."

   **Still pending:** the 2-week window of structured data to validate this interpretation across more devices and conditions. **2026-05-12 enabling change:** bumped `TOKEN_LOG_MAX_BYTES` from 128 KB to 512 KB in `BudgeTrakApplication.kt` (shipped 2.10.24), expanding rolling token-log retention from ~6 days to ~24 days. Tap a dump on each test device ~weekly until ~2026-05-26, then analyze the SLIM vs full-Tier-3 ratio on each. If the data confirms the Samsung-Doze pattern is universal across the tester fleet, formally close out Phase 4 full as not-pursuing and update `project_period_boundary_scheduling.md` accordingly. If a subset of devices show high slim-path hit rates (e.g., Pixel may behave better than Samsung), consider conditional Phase 4 enablement by `Build.MANUFACTURER`.

   **Optional follow-on (low priority):** Tier 3 snapshot-builder gating — defer snapshot building to foreground/Tier 2 unless 24h stale or sole photo-capable device; promote to `FOREGROUND_SERVICE_TYPE_DATA_SYNC` when escalated. Current 2h staleness gate covers most failure modes.

   Full plan + sequencing: `project_period_boundary_scheduling.md`.

## Post-launch (data-driven)

1. **App Check device integrity tightening.** Currently set to "Don't explicitly check device integrity level" (most permissive). The `PLAY_RECOGNIZED` app-integrity verdict (enabled) is what actually blocks pirated/re-signed APKs — that's intact. Device integrity is a separate axis. Tightening order if abuse appears post-launch: `MEETS_BASIC_INTEGRITY` (rejects obvious tampering / emulator+root), then `MEETS_DEVICE_INTEGRITY` (also rejects rooted real devices + custom ROMs). Don't go to `MEETS_STRONG_INTEGRITY` (rejects older real devices). Decide after 2-4 weeks of live PERMISSION_DENIED Crashlytics data; per-field encryption means a leaked App Check token can't decrypt data anyway, so device integrity strictness is anti-abuse only, not data protection.

2. **Play Billing Layer 2.5 — App-Check-gated entitlement doc (anti-forge).** Layer 2 (server-side refund-lag verification, LANDED 2026-05-11) doesn't stop an attacker who patches out the verifier call entirely — the local `isPaidUser` flag is still forgeable. Layer 2.5 makes the local flag a pure cache against a Cloud-Function-written, App-Check-read-gated `entitlements/{anonymousUid}` Firestore doc. **Defer trigger:** Crashlytics signature-pin failures spike, entitlement claims from devices never seen in Play purchase records, OR paid revenue becomes material enough that small leakage matters. Full design + caveats + offline-grace logic + rotation considerations: `project_play_billing_layer_2_5.md`.

3. **BIS annual self-classification report (US Encryption Export Compliance).** On the Play Console "US export laws" question we declared use of License Exception ENC for standard encryption (TLS, AES, ChaCha20-Poly1305). Under EAR §740.17(e), products using ENC must file an annual self-classification report to BIS listing each exporting product, by **February 1 each year**, covering the previous calendar year. Submit via email to `crypt-supp@bis.doc.gov` AND `enc@nsa.gov` as a CSV/spreadsheet using the BIS template at https://www.bis.doc.gov/index.php/policy-guidance/encryption ("Self-classification report"). Required fields: product name, model/version, manufacturer, ECCN (likely 5D992.c for software), encryption description ("TLS 1.2/1.3, AES-256, ChaCha20-Poly1305"), authorization type (ENC). Realistically zero enforcement against indie apps but technically required once distributed outside US (which Play does automatically). First report due **2027-02-01** for 2026 distribution. Set a calendar reminder for January 2027.

## FCM sync-push cost optimizations (at 40K-group scale)

At 40K groups the current design is ~$150/mo. These drop it toward ~$10-15/mo. Not urgent until we approach that scale.

1. **Cache FCM tokens in a group-level field.** `onSyncDataWrite` currently reads the entire `groups/{gid}/devices` subcollection on every sync write (~80M Firestore reads/mo at 40K groups, ~$48/mo). Replace with a single `groups/{gid}.fcmTokens: {deviceId: token}` map kept in sync on device add/remove. Saves ~$43/mo.

2. **Server-side debounce on `sync_push`.** Fan-out fires once per write; one period refresh on a peer device produces ~42 FCMs per recipient (observed in the 2026-04-13 overnight dump at 05:03). Add a per-(groupId, targetDeviceId) cooldown in the Cloud Function (Firestore or Redis lock, e.g. 10 s window) so bursts collapse at the server. Clients already dedupe with `enqueueUniqueWork(KEEP)`, but server dedupe also cuts FCM count + invocations.

3. **Smarter `presenceHeartbeat` scan.** Currently walks every group's `presence` node every 15 min (~75 GB/mo RTDB download at 40K groups, $75/mo). Replace with an indexed query so we fetch *only* stale records, not all ~88K then filter. Drops from O(all devices) to O(stale devices) — ~95% reduction.

   Two implementation paths:
   - **RTDB flat index:** maintain a parallel flat node like `presence_index/{groupId}__{deviceId}: { lastSeen }` with `.indexOn: ["lastSeen"]` in RTDB rules. One query `orderByChild("lastSeen").endAt(cutoff)` returns every stale device across the fleet. Keep it in sync via `updateChildren({...})` alongside each presence write — effectively free, since the device is already writing its presence.
   - **Firestore index:** maintain a `stale_candidates` Firestore collection instead. Firestore supports range indexes on any field natively, so `where("lastSeen", "<", cutoff)` works out of the box. Slightly more write-side cost (separate Firestore set) but simpler schema — we're already paying for Firestore.

   Either path also eliminates #4's timeout risk (no loop to run).

4. **Parallelize / shard `presenceHeartbeat`.** Sequential `for…await` inside the function means at ~50 ms/group the 60-second default timeout is hit at ~1.2K groups, and the hard 9-min Gen-1 ceiling at ~10K groups. Current implementation will time out past that. Fix options: (a) batch with `Promise.all` in chunks of ~100 groups, (b) shard by group ID hash and run multiple parallel cron functions, or (c) fold into #3 (indexed query eliminates the loop entirely). #3 is the preferred fix.

5. **Detect OEM FCM blocking + user prompt.** Some OEMs (Xiaomi, Huawei, aggressive Samsung profiles) silently drop FCM or kill the process before our handler runs. We can infer this from round-trip silence: the Cloud Function writes `lastHeartbeatSentAt` on the device doc before sending each FCM, then on the next cron tick compares to RTDB `lastSeen`. After 3 consecutive misses (~45 min), set `fcmSuspectedBlocked: true`. Client reads the flag on launch and shows a modal guiding the user to whitelist BudgeTrak. Suppress with a 7-day `lastBlockedPromptAt` local pref so we don't nag. **Tune thresholds after a week of live heartbeat data** — "3 misses" is a guess; real noise floor TBD.

6. **Settings deep-link for battery/autostart whitelist.** UI half of #5. Try in order: (a) `ACTION_IGNORE_BATTERY_OPTIMIZATIONS` (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission — Play Store review item), (b) OEM-specific `ComponentName` intents for Samsung "Deep Sleeping Apps", Xiaomi "Autostart", Huawei "Protected Apps", (c) fallback to `ACTION_APPLICATION_DETAILS_SETTINGS`. Branch on `Build.MANUFACTURER`. Test matrix: Pixel, Samsung, Xiaomi, OnePlus. Fragile but industry-standard; worth the effort for widget reliability.

## Completed + documented elsewhere (May 2026)

- **Layer 1 Play Billing** (v2.10.10, 2026-05-08) — `data/billing/BillingService.kt` + `BillingProducts.kt`; `MainViewModel.refreshBillingState` derives `isPaidUser`/`isSubscriber`/`subscriptionExpiry` from Play; 7-day TTL on stale cached entitlement. Design: `project_play_billing_integration.md`.
- **License-tester end-to-end validation** (2026-05-11) — all 6 paths verified on release build (v2.10.18): buy paid_upgrade, refund (A/B finding on propagation lag captured), buy subscriber, subscriber-implies-paid, accelerated renewal, cancel, fresh install restore. Refund-handling guidance for support replies: `reference_refund_workflow.md`.
- **Play Billing Layer 2 — server-side refund-lag verification** (v2.10.22, 2026-05-11) — Gen 2 callable `verifyPurchase` runs as `play-publisher@sync-23ce9.iam.gserviceaccount.com` (with Play Console "View financial data"); client `data/billing/EntitlementVerifier.kt` + `MainViewModel.reconcileEntitlement` override local PURCHASED on server REFUNDED. Closes the 24h+ Console-admin refund propagation window. Design: `project_play_billing_integration.md` "Layer 2" section + SSD §28.7.7.
- **Runtime APK signature pinning** (v2.10.19, 2026-05-11) — `BudgeTrakApplication.verifyAppSignature()` pinned to Play App Signing SHA-256; mismatch → recordNonFatal + process exit. Multi-signer aware (rotation-safe). Design: SSD §27.2a.
- **In-house fallback ads** (v2.10.20, 2026-05-11) — 5-ad cycle in `ui/components/InHouseAd.kt` renders when AdMob `onAdFailedToLoad` fires. Three Paid-tier promos + two Subscriber-only. Doubles as anti-piracy (offline free user still sees upgrade promo). Strings in `AppStrings.ads`. Design: `project_ad_implementation.md`.
- **Gemini API key gateway restriction** (2026-05-12) — Android-app cert + Gemini-only API restrictions applied at Google Cloud API gateway. Three SHA-1 entries registered (Play App Signing + Upload key + debug keystore). Defends against key extraction → use elsewhere. Rotation gotcha (new keys must be SA-bound before API restriction can be applied): `reference_gemini_api_key.md`.
- **Firebase Analytics `appInstanceId` in diag dump** (v2.10.22, 2026-05-12) — `BudgeTrakApplication.appInstanceId` cached async on startup; `DiagDumpBuilder.build` surfaces it under `DeviceId:`. Lets BigQuery↔dump correlation work without per-device guesswork. Design: LLD §6.15.
- **Period-boundary scheduling Phase 1, 3, 4-alt** (2026-04-25) — `BackgroundSyncWorker.runFullSyncInline` (inline FCM dispatch) + `scheduleNextBoundary` (solo users boundary one-shot, self-rearming) + slim Tier 3 fast-exit for sync users. Solo users dropped from ~96 worker runs/day to ~4. Design: `project_period_boundary_scheduling.md`.

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

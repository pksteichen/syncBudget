# BudgeTrak Project Memory

## Project Overview
- Android budgeting app **BudgeTrak** by **Tech Advantage LLC** (`techadvantagesupport@gmail.com`).
- Package/namespace: `com.techadvantage.budgetrak` (renamed from `com.securesync.app` / `com.syncbudget.app` on 2026-04-11).
- GitHub: `techadvantagesupport/BudgeTrak` (transferred from `pksteichen/syncBudget`; old URL redirects).
- Privacy policy: `https://techadvantagesupport.github.io/privacy`. Pages repo clone at `/storage/emulated/0/Download/Tech Advantage Pages` (legacy `budgetrak-legal` repo still serves v2.7 fallback URL).
- Working dir: `/data/data/com.termux/files/home/dailyBudget`. ~51.5 k lines, ~100 Kotlin files.

## Build Environment (Termux)
- `export JAVA_HOME=/data/data/com.termux/files/usr` before builds.
- Android SDK at `~/android-sdk` via `local.properties` (NOT env vars).
- AGP-bundled aapt2 is x86_64 — override to build-tools 34 ARM aapt2 via `android.aapt2FromMavenOverride` in gradle.properties.
- Termux aapt2 (v2.19) cannot load android-35 — pin `compileSdk = 34`.
- Build: `./gradlew assembleDebug --no-daemon`. APK copies to `/storage/emulated/0/Download/` for install.

## Dependencies (compileSdk 34)
- AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Compose BOM 2024.09.03, core-ktx 1.13.1, lifecycle 2.8.6, Firebase BOM 32.7.0, work-runtime-ktx 2.9.1, documentfile 1.0.1.
- Do NOT bump core-ktx ≥ 1.15 or Compose BOM ≥ 2024.12.01 (require compileSdk 35).

## Architecture
- Single `MainActivity.kt` (~2.6 K lines — router, lifecycle, wrappers) + `MainViewModel.kt` (~3.2 K lines — state, business logic, sync lifecycle, background loops).
- `MainViewModel.Companion.instance: WeakReference<MainViewModel>` lets `BackgroundSyncWorker` pick Tier 2 vs Tier 3.
- File map: [`architecture.md`](architecture.md). UI layer: [`spec_ui_architecture.md`](spec_ui_architecture.md).

## SYNC (Firestore-native, v2.1 onwards — 2026-03-23)
- Branding: "BudgeTrak SYNC" full, "BudgeTrak" short. SYNC always all-caps (EN + ES). "Family Sync" abrogated.
- Per-field encryption (`enc_fieldName`, ChaCha20-Poly1305 via `CryptoHelper.encryptWithKey`); metadata (`deviceId, updatedAt, deleted, lastEditBy`) plaintext.
- Field-level updates: `pushRecord` diffs against `lastKnownState`, pushes only changed fields.
- Filtered listeners with `whereGreaterThan("updatedAt", cursor)` — per-collection cursors in SharedPrefs, saved after `onBatchChanged` completes.
- Conflict detection: `lastEditBy` + `localPendingEdits` (SharedPrefs). Same-field conflict → `isUserCategorized = false`.
- Data classes carry only `deviceId` + `deleted` — no clocks. Tombstones via `deleted = true`. UI uses `.active` filters (data-content based, not clock).
- **Enc hash cache** (`enc_hash_cache.json`) skips decryption of unchanged docs; populated at both receive and push time.
- **Echo optimization**: cursors advance on echo-filtered batches (pure or mixed) so fresh listeners don't re-deliver own pushes. `lastEditBy == ourDeviceId` filter. Background pushes persist keys (`bgPushKeys`, 20-min TTL).
- **Cold-start gate** (`initialSyncReceived` / `awaitInitialSync`, up to 30 s) defers migrations + period refresh until all 8 collection listeners deliver.
- **Persistent listeners** managed by ViewModel lifecycle. On PERMISSION_DENIED → `triggerFullRestart()` (stop all, force-refresh App Check, restart; 30 s debounce).
- **Integrity check**: startup (after initial sync) + `runPeriodicMaintenance()` (24 h gate). Uses `Source.CACHE` (zero network). Pushes any local-only records, then `recomputeCash()`.
- **Consistency check (two-layer)**: Layer 1 `countActiveDocs()` vs local `.active.size` — mismatch clears the cursor (full re-read), logs `CONSISTENCY_COUNT_MISMATCH`. Layer 2 `cashHash` = `availableCash.toString().hashCode().toString(16)` (hex digest, `MainViewModel.kt:835`) in `deviceChecksums` on group doc; 3+ devices → majority vote, 2 → both re-read on confirmed mismatch. 1-hour confirmation gate (`checksumMismatchAt`). Separately, the `enc_hash` per-doc cache in `FirestoreDocSync.kt:223` uses `.toString()` (decimal) — different hash, different purpose.
- **Three-tier threading**: decrypt on Default, UI on Main, JSON saves on IO.
- **`saveCollection<T>`** generic with optional `hint`. `SyncWriteHelper.pushBatch()` chunks at 500 ops with retry+individual-push fallback.
- **Receipt sync**: four-layer architecture — Layer 0 fg upload drainer (`viewModelScope` coroutine), Layer 1 onBatchChanged fast-path 5-concurrent download, Layer 2 fg download retry coroutine, Layer 3 BG Tier 2/3 `syncReceipts` (flag-clock polling on `imageLedgerFlagClock` field of group doc, no separate meta doc). Cloud Function `onImageLedgerWrite` fires `sync_push` on rotation / recovery / re-upload-complete for near-real-time peer wake. **False-rotation guard (v2.7+, 2026-04-25)**: `ReceiptManager.replaceReceipt` calls `ReceiptSyncManager.bumpLocalContentVersionForRotation` BEFORE `addToPendingQueue`, and `processPendingUploads` skips `incrementContentVersion` when `existing.lastEditBy == ourDeviceId && existing.contentVersion == localContentVersion` — recognises a partial-commit resume (cancelled between ledger write and queue removal) and avoids fanning out an unnecessary flag-clock bump for unchanged content. `createLedgerEntry` now stamps `lastEditBy = originatorDeviceId` so fresh-upload resumes also match. Full spec: [`spec_receipt_photos.md`](spec_receipt_photos.md).
- **Three-tier `BackgroundSyncWorker`**: (1) app active → skip, (2) ViewModel alive → App Check refresh + listener health + RTDB ping + **receipt sync (v2.7, gated on `!isReceiptSyncActive()`)**, (3) ViewModel dead → slim path OR full sync. Tiers 2/3 Firebase ops gated by `isSyncConfigured` — solo users skip Auth / App Check / RTDB / Firestore. RTDB `lastSeen` uses `.await()`. Guarded by in-process `AtomicBoolean isRunning` — periodic + FCM one-shot use different unique work names, so without this guard they could double-fire (seen in Kim's 2026-04-16 diag dump: two Tier 3 runs 118ms apart, doubled listeners + RTDB pings). **Tier 3 receipt sync is step 2b** (right after `syncFromFirestore`+`saveMergeResult`), not step 6 — Samsung power management was observed cancelling Tier 3 cycles ~1m48s in, hitting receipt sync when it was last; earlier positioning maximizes completion odds. Receipt-sync `syncLog` routes through `BudgeTrakApplication.syncEvent` so events persist in `token_log.txt` + Crashlytics (v2.7). `CancellationException` is rethrown (not swallowed) in outer `doWork`, in `ImageLedgerService.downloadFromCloud`/`getFlagClock`/`getLedgerEntry`, and in `ReceiptSyncManager.buildSnapshot` / `processSnapshotDownload` (v2.7+, was previously swallowed).
- **Inline FCM path (v2.7+, 2026-04-25)**: `BackgroundSyncWorker.runFullSyncInline(context, sourceLabel, timeBudgetMs)` is the single entry point used by both `doWork()` (no budget) and `FcmService` (budget = 8.5 s, leaves 1.5 s of FCM 10 s window). Replaces the prior 9 s busy-wait. **FcmService branches on VM lifecycle**: VM alive → launch on `BudgeTrakApplication.processScope` (no budget — VM keeps the process alive past `onMessageReceived` returning, so long Tier 2 work like snapshot building / multi-photo upload completes naturally); VM dead → `runBlocking` with 8.5 s budget + WM `runOnce` fallback on timeout. App Check refresh threshold dropped 35→16 min in Tier 2/3 (server heartbeats are reliable enough that one heartbeat per 4 h token cycle catches the refresh; the rest skip). Body refactored to file-private top-level functions taking `Context`. Same pattern for `runDebugDumpInline` / `DebugDumpWorker`.
- **Slim-path Tier 3 (v2.7+, 2026-04-25)**: `runTier3` early-returns to a 25 ms slim body (period refresh + recompute cash + widget update only) when (a) solo user (no group → nothing to sync) **OR** (b) sync user, sourceLabel == "Worker", `lastInlineSyncCompletedAt` < 30 min ago, and no consistency mismatch pending. Solo path also calls `scheduleNextBoundary(context)` to perpetuate the boundary chain. Skips Anonymous Auth / App Check / Firestore listeners / RTDB / receipt sync entirely.
- **Period-boundary scheduling (Phase 3, 2026-04-25)**: solo users get `scheduleNextBoundary` (one-shot via `BOUNDARY_WORK_NAME`, REPLACE policy, expedited on API 31+) instead of the 15-min periodic. Boundary instant computed by `PeriodRefreshService.nextBoundaryAt` (clamped 60 s–24 h). The slim Tier 3 body re-arms the next boundary at end of every solo run. `BackgroundSyncWorker.schedule()` branches on `isSyncConfigured`. `cancel()` cancels both work names. `GroupManager.leaveGroup` calls `schedule()` to switch into solo path cleanly. Sync users stay on the periodic with the slim path optimization (Phase 4-alt). Full plan: [`project_period_boundary_scheduling.md`](project_period_boundary_scheduling.md).
- **WakeReceiver**: manifest-registered `ACTION_POWER_CONNECTED/DISCONNECTED`, 5-min rate limit, fires `BackgroundSyncWorker.runOnce` (which routes through the same inline body via `doWork`).
- **DebugDumpWorker**: one-shot, FCM `debug_request` triggered, debug builds only.

## Transaction Linking & Remembered Amounts
- `linkedRecurringExpenseAmount`, `linkedIncomeSourceAmount`, `linkedSavingsGoalAmount` — entity amount at link time. `amortizationAppliedAmount` — cumulative AE deduction captured at delete.
- **Delete preserves remembered amounts; manual unlink clears them** (rule applies to all 4 link types).
- Editing RE/IS amount prompts "apply to past transactions?"
- Math: [`spec_budget_calculation.md`](spec_budget_calculation.md). Data: [`spec_data_model.md`](spec_data_model.md). Flows: [`spec_transaction_flows.md`](spec_transaction_flows.md).

## Home Screen Widget
- `BudgetWidgetProvider` + `WidgetRenderer` (Canvas bitmap). Solari renders as bitmap, XML button bar below.
- Theme-aware (blue light / dark cards). `showWidgetLogo` toggle.
- Min 2×1 (110×40 dp), default 4×1 (250 dp). `updateAllWidgets()` 5 s throttle.
- Refresh scheduled via `BackgroundSyncWorker` — **no `WidgetRefreshWorker` exists** (retired 2026-03-29).

## Async Loading & Lifecycle
- 7 repos load on IO with learned-timing progress (EMA `(4·old + new)/5`, 60 fps ticker), 500 ms minimum display.
- `return@setContent` gates all UI until `dataLoaded = true`. Lifecycle observer registered AFTER the gate (initial ON_RESUME intentionally missed).
- `onResume` guard: `if (!dataLoaded) return`.
- Back = Home on main screen: `moveTaskToBack(true)`.
- `recomputeCash()` is synchronous (no async dispatch) — avoids startup races.

## Periodic Maintenance
`runPeriodicMaintenance()` from `onResume`, 24 h gate (`lastMaintenanceCheck`):
- Daily `HEALTH_BEACON` non-fatal + Crashlytics diag keys (sync users).
- Backup check (auto-backup if due).
- Integrity check + `recomputeCash()` (sync users).
- Layer 1 + Layer 2 consistency check (sync users).
- Receipt orphan cleanup + local pruning (`receiptPruneAgeDays`).
- Solo user tombstone purge (`!isSyncConfigured`) — removes `deleted=true` from 6 collections.
- Admin tombstone + cloud orphan cleanup (30-day sub-gate via `lastAdminCleanup`).

Mismatch re-check: `checksumMismatchAt` → `recheckConsistency()` bypasses 24 h gate on `onResume` and Tier 3.

## Period Refresh
- **Calculated sleep** (next-boundary + 60 s; clamped 60 s–15 min). NOT 30 s polling — that was old spec text.
- `resetHour` default 0. User's current config: `resetHour = 5` (5 AM CDT).
- Full detail: [`spec_period_refresh.md`](spec_period_refresh.md). Shared service: [`project_background_refresh.md`](project_background_refresh.md).

## Output & Diagnostic Files
- Support: `/storage/emulated/0/Download/BudgeTrak/support/`. Non-admin: `/Download/Quick Share/`.
- Key files: `sync_diag.txt`, `native_sync_log.txt`, `token_log.txt` (debug only), `logcat_*.txt`, per-device FCM dumps.
- Dump button (Settings → "Dump & Sync Debug", debug builds) — encrypted upload via FCM + 90 s poll.
- **Public download writes**: 7 active categories (backups, CSV/XLSX/JSON, PDF, photos, support, pre-restore, append-logs); v2.10.03 added `PublicDownloadWriter` for orphan-EACCES resilience on PDF + support files. Photo dumps are NOT deprecated. Survey + helper details: [`reference_public_download_writes.md`](reference_public_download_writes.md).
- Full spec: [`spec_diagnostics.md`](spec_diagnostics.md). Crashlytics/BigQuery tool: [`reference_crashlytics_bigquery.md`](reference_crashlytics_bigquery.md). **Backend-infra reconstruction reference: SSD §28** (Firebase project, Auth, Firestore + RTDB + Storage rules verbatim, App Check, Cloud Functions, FCM, Crashlytics, Analytics, BigQuery, Gemini).

## Matching & Auto-Categorize
- 4 ranked finders: `findDuplicates` (amount then date), `findRecurringExpenseMatches` + `findBudgetIncomeMatches` (date then amount), `findAmortizationMatches` (amount). Match confirmation dialogs show radio list, best pre-selected.
- Merchant matching strips non-alphanumeric (Wal-Mart = Walmart).
- **Auto-categorize** runs in three modes: (1) **CSV bank imports** (US_BANK, GENERIC_CSV) — on-device heuristic applies to every row, falls back to "other" when no match; (2) **AI upgrade** (opt-in, Paid+Sub) — after the heuristic runs, rows with <5 matches OR <80% agreement are batched to Gemini 2.5 Flash-Lite; silent heuristic fallback on network/API failure; (3) **Manual entry in TransactionDialog** — fires once when merchant ≥ `matchChars` and no category selected, applies only if a real match exists (skips "other" fallback), respects an already-picked category. BudgeTrak-native CSV imports skip auto-categorize entirely.
- Auto-capitalize APA Title Case (`TitleCaseUtil`) on merchant + description; Settings checkbox, default on.

## Dashboard, Simulation, Savings, Receipts, Backup
- [`spec_dashboard.md`](spec_dashboard.md) — Solari display (canvas + procedural sound), spending chart ranges/palettes, Supercharge bolt, sync indicator.
- [`spec_simulation.md`](spec_simulation.md) — 18-month cash-flow projection engine (SavingsSimulator) + interactive SimulationGraphScreen.
- [`spec_recurring_and_savings.md`](spec_recurring_and_savings.md) — accelerated RE mode, set-aside tracking, SG target-date/fixed/supercharge math.
- [`spec_receipt_photos.md`](spec_receipt_photos.md) — capture, compression, flag-clock polling, possession, pruning, snapshot archive, rotation.
- [`spec_backup.md`](spec_backup.md) — full backup spec (retention, pre-restore snapshot, photos file, serialized prefs).

## i18n / Translation
- [`feedback_translation_context.md`](feedback_translation_context.md) — how to add strings.
- [`reference_strings_system.md`](reference_strings_system.md) — 1,393 val fields across 22 data classes; files: AppStrings 1498, English 1896, Spanish 1882, TranslationContext 1477.

## Git Workflow
- Two branches: `dev` → `main`. Default push: **dev only**.
- Tags: v2.1–v2.6 (see `project_firestore_native_sync.md`).
- Post-commit hook copies repo → `Download/BudgeTrak Dev Project Files/` (one-way). See `feedback_git_hook_direction.md`.
- `gh` CLI has both `pksteichen` and `techadvantagesupport` accounts logged in; active account is `techadvantagesupport`. `gh auth switch -u <user>` toggles (affects all terminals). Git push/pull uses `~/.git-credentials` (techadvantagesupport PAT), independent of `gh` active account.
- Memory files live at `memory/` inside this repo. A symlink at `~/.claude/projects/-data-data-com-termux-files-home-dailyBudget/memory` points here so Claude Code reads the tracked copy — `/push` backs them up alongside code.
- Persistence-layer names preserved across rename: `future_expenditures.json`, `familyTimezone`, `syncbudget_full_backup`. See `feedback_preserve_persistence_names.md`.

## Specifications
- [Budget Calculation](spec_budget_calculation.md), [Data Model](spec_data_model.md), [Period Refresh](spec_period_refresh.md), [UI Architecture](spec_ui_architecture.md), [Transaction Flows](spec_transaction_flows.md), [CSV Import](spec_csv_import.md), [Group Management](spec_group_management.md), [Receipt Photos](spec_receipt_photos.md), [Dashboard](spec_dashboard.md), [Simulation](spec_simulation.md), [Recurring + Savings](spec_recurring_and_savings.md), [Backup](spec_backup.md), [Diagnostics](spec_diagnostics.md).

## Important Feedback
- [Specs vs code: assume spec is wrong first](feedback_specs_vs_code.md) — existing working code usually reflects design choices specs don't capture.
- [Verify code state before proposing changes](feedback_verify_before_proposing.md) — BudgeTrak exceeds any single context window; grep before architecting.
- [Delete vs Unlink rule](feedback_delete_vs_unlink.md) — critical for link-type changes.
- [Preserve existing fixes](feedback_preserve_fixes.md).
- [Keep specs current](feedback_update_specs.md).
- [Dialog design guide + checklist](feedback_dialog_design_guide.md) — AdAware wrappers, DialogStyle, Toast.
- [Add debug logging early](feedback_debug_with_logging.md).
- [Analyze ALL failure cases](feedback_analyze_all_cases.md).
- [Wait for user decision](feedback_wait_for_decision.md).
- [Keep analysis concise](feedback_concise_analysis.md).
- [Help-text vs code audit method](feedback_help_audit_method.md).
- [Audit methodology](feedback_audit_methodology.md).
- [SYNC branding rules](feedback_sync_branding.md).
- [Never rename persistence-layer fields](feedback_preserve_persistence_names.md).
- [Keep firebase-config-reference.txt updated](feedback_update_firebase_config.md).
- [Ad banner implementation](project_ad_implementation.md).
- [JIT extraction lambda overhead](feedback_jit_extraction.md).
- [Compose state-seed order + LaunchedEffect cancellation](feedback_compose_state_seed_order.md) — seed VM fields before the visibility flag; hoist long work to viewModelScope.
- [Receipt pruning design](feedback_receipt_pruning_design.md) — cloud 14-day and local prune age are independent.
- [No flag-clock bump on imageLedger creation](feedback_image_ledger_no_create_bump.md) — peers discover via transaction sync; prune fires inline at every download site; don't re-add the bump.
- [Firebase rules — source of truth + audit findings](reference_firebase_rules.md) — firestore.rules / storage.rules / database.rules.json committed; `node tools/fetch-rules.js` refreshes from live project.
- [BigQuery service account](reference_bigquery_service_account.md) — `tools/query-crashlytics.js` reads via SA key at `~/.config/budgetrak/sa-key.json` (roles: bigquery.jobUser + bigquery.dataViewer on `sync-23ce9`). Avoids Firebase CLI RAPT re-auth.
- [AI feature UX — explicit trigger, tier per-feature](feedback_ai_feature_ux.md) — OCR sub-only; CSV categorization Paid+Sub; OCR prefill always overwrites scalars and preserves cat selection when pre-selected; CSV payload is merchant+amount only (no date).
- [Share-intent routing when dialogs are open](feedback_share_intent_routing.md) — block with toast for non-transaction dialogs, absorb into open transaction dialog, fall through to new Add dialog otherwise; multi-share supported.
- [APK naming — always BudgeTrak.apk](feedback_apk_naming.md) — one file in Downloads, overwritten each build; no versioned names.
- [Batteries = Home Supplies, not Other](feedback_batteries_as_home_supplies.md) — receipt-labeling preference.
- [Claude unsuitable for BudgeTrak OCR](feedback_claude_receipt_ocr_unsuitable.md) — 30 prompt variants × Haiku/Sonnet/Opus all fail on compressed receipts; don't re-run.
- [Run gradle clean after dep swap](feedback_gradle_clean_after_dep_swap.md) — removing deps leaves stale DEX files; causes startup crash.
- [Ambiguous categories aren't OCR errors](feedback_ocr_ambiguous_categories_not_errors.md) — rotisserie chicken, steel-toed boots, soap: multiple valid buckets; don't penalize.
- [Memory routing — project vs private](feedback_memory_routing.md) — tracked `memory/` for project content; un-tracked `~/.claude/projects/.../private-notes/` for personal.
- [Surface save failures, never silently no-op](feedback_silent_save_failures.md) — six silent-loss vectors found in transaction add/edit (2026-04-27); rules for guards, dismiss handlers, validation toasts.
- [Activity.recreate() preserves the ViewModel](feedback_recreate_preserves_viewmodel.md) — disk writes need explicit vm.reloadAllFromDisk(); recreate() rebuilds UI but keeps stale VM state. Pair with Snapshot.withMutableSnapshot + key(vm.dataReloadVersion) wrap on the screen-routing block.
- [Compose Dialog windows stack above main window](feedback_compose_dialog_window_stacking.md) — overlays meant to cover an open Dialog must themselves be a Dialog; a plain Surface renders behind.
- [Network-touching code must be network-aware](feedback_network_aware_code.md) — fail-fast offline (vm.isNetworkAvailable foreground, NetworkUtils.isOnline background); auto-resume drainer on onAvailable via cancelAndJoin; thread Boolean through to avoid stamping "work done" after offline-skipped runs.

## Firebase Backend
- Plan: Blaze. App Check enforced on Firestore/RTDB/Storage (not Auth). Debug → `DebugAppCheckProviderFactory`, release → `PlayIntegrityAppCheckProviderFactory`. **TTL is provider-dependent**: Play Integrity (release) = 40 h as of 2026-04-26 (set in Firebase Console → Project Settings → Your apps → BudgeTrak Android → App Check section, dropdown selector); Debug provider = 1 h (Google-imposed, ignores Console setting — by design for short-lived dev tokens). So debug-build observed refresh cadence is 40× higher than release will be. **Play Integrity advanced settings**: `PLAY_RECOGNIZED` required (anti-piracy), `LICENSED` not required (don't gate free users on Huawei/degooglified devices), device integrity = "Don't explicitly check" (relies on PLAY_RECOGNIZED + per-field encryption for actual security; tighten post-launch if Crashlytics shows abuse).
- **App Check debug token**: pinned UUID `8dc731bc-5213-4b09-bb31-b0382af175f1` (v2.10.03+) seeded from `local.properties:APP_CHECK_DEBUG_TOKEN` → `BuildConfig` → SDK prefs in `BudgeTrakApplication.onCreate`. One Console-registered token covers every dev/test device — no re-registration on reinstall. Logcat scrape fallback still runs; token also lands in `token_log.txt` for FCM dump uploads. Full spec: [`project_token_debug.md`](project_token_debug.md). The `.debug` Firebase Android app entry (`com.techadvantage.budgetrak.debug`, registered 2026-05-03) has the same UUID added under its Manage debug tokens so applicationIdSuffix-suffixed builds also authenticate.
- **Play Integrity setup checklist (2026-05-03 post-mortem)**: getting App Check Play Integrity to actually mint valid tokens for release builds requires FOUR independent Console steps, any one missing produces identical silent-failure symptoms (no `AppCheck token refreshed` breadcrumbs, no metrics card, every Firestore/Storage call rejected). (1) Play Integrity API enabled in Cloud Console, (2) Firebase project linked to Google Play with App Distribution + Crashlytics + Analytics ticked, (3) Cloud project linked to Play Integrity API on Play Console → Setup → App integrity (separate from #2), (4) Play **app signing key** SHA-256 (NOT the upload-key SHA-256) registered in Firebase → Project settings → Your apps → BudgeTrak Android → SHA fingerprints. Step 4 is the killer: Firebase Console only prompts for SHA-1 originally (legacy OAuth flow), and Play strips your upload signature and re-signs with its own app signing key — so the runtime fingerprint that Play Integrity attests is the app signing key, not the upload key. Full checklist + diagnostic symptoms in `firebase-config-reference.txt` §5b. **Operational caveat:** PLAY_RECOGNIZED requirement must be temporarily set to "Not required" during the 24-72 hours after a new release goes live to any track — Google's anti-piracy backend indexes new versionCodes asynchronously and per-device, so a fraction of testers initially receive UNEVALUATED/UNRECOGNIZED_VERSION verdicts. Symptom: some testers see PERMISSION_DENIED on identical hardware while others succeed. Re-enable once the failing-device fraction goes to zero. Verified 2026-05-03: 0–4 hours per device for v2.10.04 on Internal Testing.
- All App Check calls gated by `isSyncConfigured`; all wrapped `withTimeoutOrNull(10–15 s)`.
- Refresh triggers: `onResume`, `onAvailable`, `BackgroundSyncWorker` Tier 2/3 (proactive 16-min threshold, dropped from 35 on 2026-04-25), `triggerFullRestart()` on PERMISSION_DENIED, ViewModel keep-alive (45-min check / 16-min refresh, dropped from 35 on 2026-04-26 to dedupe with Worker), SDK auto-refresh (~5 min before expiry, in-process only).
- TTL policies: `groups/expiresAt` (now + 90 d, refreshed each launch), `pairing_codes/expiresAt` (10 min). **Never** use `lastActivity` as TTL (fixed 2026-04-04).
- Cloud Function `cleanupGroupData` (v1 API, Node.js 22 per `functions/package.json`) cascade-deletes 14 subcollections + RTDB + Storage on group-doc delete. Stay on v1 (v2 needs Cloud Run + Eventarc + IAM not configured). `firebase-config-reference.txt` says Node.js 20 and is stale.
- `firebase-tools` installed in Termux, authed; functions deployed from `functions/`.
- Crashlytics: non-fatals on PERMISSION_DENIED + consistency mismatches + token-refresh timeouts + OCR pipeline failures (`ReceiptOcrService.kt:202` catches all OCR exceptions and `recordException(e)` before returning `Result.failure`). Custom keys (attach to every future crash): `buildTime` + `versionCode` (set in `BudgeTrakApplication.onCreate` from `BuildConfig` — filter latest-build crashes post-publish via `query-crashlytics.js --build <prefix>`), `cashDigest, listenerStatus, lastRefreshDate, activeDevices, txnCount, reCount, plCount, lastTokenExpiry, authAnonymous`. Opt-out toggle `crashlyticsEnabled` in `app_prefs` (default true, read in `BudgeTrakApplication.onCreate`).
- Firebase Analytics: SDK calls were added 2026-04-22 but **events were silently dropped until 2026-04-26** because the Firebase project wasn't linked to a GA4 property. Linkage completed via Firebase Console → Integrations → Google Analytics → Link (property ID 534603748, stream "BudgeTrak Android" id 14591145419). Per-event usage telemetry, same opt-out toggle. Events: `ocr_feedback` (fires on save of OCR-populated transactions — measures per-field user-correction rate), `health_beacon` (daily sync-user heartbeat, migrated from Crashlytics non-fatal). Helper: `data/telemetry/AnalyticsEvents.kt`. Setting renamed to "Send crash reports and anonymous usage data". **Note**: modern Firebase BoM 32.x SDK auto-discovers measurement ID at runtime via `mobilesdk_app_id` — `google-services.json` doesn't need an explicit `analytics_service` block. Confirmed working without it. BigQuery export for Analytics enabled 2026-04-26 (Daily + Streaming, no advertising IDs).
- BigQuery streaming export: **legacy app `com.securesync.app` only** until 2026-04-26, when export was enabled for the rebranded `com.techadvantage.budgetrak` app via Project Settings → Integrations → BigQuery. New tables `com_techadvantage_budgetrak_ANDROID` (batch) + `com_techadvantage_budgetrak_ANDROID_REALTIME` (stream); legacy `com_securesync_app_*` stops receiving data on 2026-04-12. `tools/query-crashlytics.js` UNION ALLs both pairs so historical lookups span the rebrand; supports `--list-devices` (distinct fingerprints) and `--build <prefix>` (filter to one build via `buildTime` custom key).
- Firestore rules membership-based (`isMember()`). RTDB/Storage similar.
- Config reference: `firebase-config-reference.txt` in project root.

## Rebrand + History
- [Rebrand 2026-04-11](project_rebrand_2026_04_11.md) — namespace/applicationId/GitHub/PATs, gotchas.
- [Dissolve bug 2026-04-12](project_dissolve_bug_2026_04_12.md) — legacy `deltas`/`snapshots` security-rule gap; lessons.
- [App Check debug-token management](project_token_debug.md) — pinned UUID for stable debug auth across reinstalls (v2.10.03+); plus the resolved overnight dissolution + PERMISSION_DENIED root causes (2026-04-04).
- [Firestore-native sync implementation](project_firestore_native_sync.md).

## Pricing
- [Pricing tiers](project_pricing.md) — Free $0 / Paid $9.99 one-time / Subscriber $4.99/mo. Free can join SYNC but not create/admin, and cannot import/export.

## Future Work
- [Subscriber feature ideas](project_subscriber_feature_ideas.md).
- [OCR/AI receipt capture plan](project_ocr_receipt_capture.md).
- [OCR pipeline decisions — V18 split-pipeline + C1.5 reconciliation shipped](project_ocr_pipeline_decisions.md) — image→itemNames+fullTranscript, text-only reconcile of date+amount (parallelized with C2), image+text→scored categories, prices; C1.5 catches digit OCR errors + locale date swaps (+6pp amount, +2pp date on harness); 4 calls max for multi-cat with zero added wall-clock via parallel launch.
- [GenerativeAI Android SDK re-encodes Bitmaps at q=80](feedback_genai_sdk_bitmap_reencode.md) — use `blob("image/jpeg", bytes)` not `image(bitmap)` in content DSL; the latter silently drops stored q=95 receipts to q=80 before sending to Gemini.
- [AI CSV categorization](project_ai_csv_categorization.md) — **shipped 2026-04-16**. Flash-Lite, hybrid heuristic+AI (≥5 matches & ≥80% agreement skips AI), opt-in, Paid+Sub.
- [Widget photo support (designed)](project_widget_photos.md).
- [Pre-launch TODO](project_prelaunch_todo.md) — most done; App Check integrity level deferred.
- [Period-boundary scheduling (Phase 3 + 4)](project_period_boundary_scheduling.md) — replace BG worker 15-min periodic with one-shots at period boundaries; gated on Phase 1 FCM-inline overnight verification.
- [Play Store launch plan](project_play_store_launch.md) — personal-first then transfer-to-org strategy, address plan, DUNS timeline.
- [Play "Verified" badge](project_play_verified_badge.md) — currently VPN-only; criteria + BudgeTrak gap analysis for if/when Google expands to finance category. Requires org account + MASA Level 2 + independent security review + 90 days + 10K/250.
- [Billing + runaway-bug alerts (configured 2026-04-13)](project_billing_alerts.md) — $1 budget + 4 Monitoring policies + SMS channel. Killswitch still optional.
- [OCR Spanish / Country setting](project_ocr_spanish_country_setting.md) — when adding Spanish or LatAm launch, add a Country dropdown driving locale/currency/date/tax-vocab hints into the OCR prompt; integer-only currencies (CLP/COP/PYG) block launch without this.

## Documentation
- SSD/LLD v2.8 (dev) at `docs/BudgeTrak_SSD_v2.8.md` + `docs/BudgeTrak_LLD_v2.8.md`. Bump on any structural change. v2.7 was the last production-shipped version (renamed to v2.8 on 2026-04-28 when dialog consolidation + refund OCR landed).
- [Pages repo location](reference_pages_repo.md) — privacy.md + homepage + branding live in `techadvantagesupport.github.io` repo at `/storage/emulated/0/Download/Tech Advantage Pages`.

## Audit Follow-ups
- **2026-04-12 memory audit** (this one): clarified the two sync hashes — `cashHash` (Layer 2 consistency) is hex via `.toString(16)`; the `enc_hash` per-doc cache in FirestoreDocSync is decimal. An earlier draft conflated them. Removed `WidgetRefreshWorker` refs, restored auto-categorize scope (CSV import only), updated screen count (10 navigable + 10 help, plus QuickStartGuide overlay), deleted obsolete CRDT-era files, added spec_simulation / spec_dashboard / spec_recurring_and_savings / spec_backup / spec_diagnostics.
- v2.6 audit (solo-user impact): 3 medium fixed.
- v2.5 audit: 1 critical + 5 high + 4 medium fixed.

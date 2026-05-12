---
name: Play Billing Layer 1 integration — entitlement flow, TTL, debug override, grace-period interplay
description: How Play Billing drives isPaidUser / isSubscriber / subscriptionExpiry; how the 7-day TTL bounds the offline-refund attack; how the existing 7-day SYNC admin grace period plugs in unchanged; how the debug override lets dev exercise tier UX offline.
type: project
---

## Status
**Shipped in v2.10.10 (2026-05-08)**, code-only. Pending Play Console SKU creation + ~24h propagation + license-tester validation per `project_prelaunch_todo.md` item 13c.

## Files
- `data/billing/BillingProducts.kt` — product ID constants (`paid_upgrade`, `subscriber`) + `SUB_PERIOD_MS` (30 d).
- `data/billing/BillingService.kt` — wraps Billing Library 7.1.1: `ensureConnected` (10 s timeout), `queryAll` (one-call snapshot of product details + active purchases), `launchPaidUpgrade`, `launchSubscribe`, `acknowledge`. Exposes a `BillingState` data class.
- `MainViewModel`:
  - State: `paidUpgradePrice`, `subscriberPrice`, `billingOverrideEnabled`, `restoreToastMessage` (consumed + cleared by MainActivity LaunchedEffect, mirrors `archiveToastMessage` pattern), internal `paidUpgradeDetails` / `subscriberDetails` / `subscriberOfferToken`.
  - Methods: `refreshBillingState()` (the workhorse — returns `RestorePurchasesResult.{ QueryFailed | NoPurchases | PurchasesFound }`; init / onResume / onBillingPurchasesUpdated callers wrap in `launch { }` and silently discard the return; `restorePurchases()` is the only consumer of the result), `launchPaidUpgrade(activity)`, `launchSubscribe(activity)`, `restorePurchases()` (sets `restoreToastMessage` to `purchasesRestored` / `purchasesRestoredEmpty` / `purchasesRestoreFailed` based on result), `setBillingOverrideEnabled(enabled)`, `onBillingPurchasesUpdated(result, purchases)`.
  - Init: 7-day TTL gate replaces the v2.10.00 release-build override; kicks off async `refreshBillingState`.
  - `onResume`: re-runs `refreshBillingState` to catch sub expiry / refunds / account switches that happened while the app was closed.
- `SettingsScreen.kt`: new Subscription section (release + debug) + debug-only override checkbox above the existing manual paid/subscriber toggles.
- `MainActivity.kt`: passes `vm.paidUpgradePrice` / `vm.subscriberPrice` / activity-bound `onLaunch*` callbacks to `SettingsScreen`.
- `AndroidManifest.xml`: `<uses-permission android:name="com.android.vending.BILLING" />`.
- Strings: `subscriptionSection`, `currentTier`, `tierFree`, `upgradeToPaid`, `subscribeMonthly`, `restorePurchases`, `billingOverrideDebug`, `purchaseFailed`, `purchasesRestored`, `purchasesRestoredEmpty` ("No purchases found on this Google account"), `purchasesRestoreFailed` ("Could not contact Google Play") (en + es-419).

## Entitlement flow

```
                    ┌────────────────────────┐
Play Store ────────►│ BillingService.queryAll│
purchases           └──────────┬─────────────┘
                               │ BillingState
                               ▼
                    ┌────────────────────────┐
                    │MainViewModel.refresh-  │
                    │BillingState()          │◄── debug override skips this branch
                    └──────────┬─────────────┘
                               │ writes
                               ▼
              isPaidUser / isSubscriber / subscriptionExpiry
              + lastSuccessfulBillingCheck
              (in-memory + SharedPreferences)
                               │
                               ▼
                    feature gates throughout app
                    (`if (vm.isPaidUser || vm.isSubscriber)`)
```

- **`paid_upgrade`**: non-consumable INAPP. `purchase.purchaseState == PURCHASED` → `isPaidUser = true` permanently (until refunded — Play stops returning it, next refresh writes `false`).
- **`subscriber`**: SUBS. Active purchase → `isSubscriber = true` and `subscriptionExpiry = purchase.purchaseTime + SUB_PERIOD_MS`. `purchaseTime` advances on each successful auto-renewal so the timestamp stays ~30 days in the future while the sub is healthy. When Play stops returning the purchase (cancelled / payment failed for good), the cached `subscriptionExpiry` keeps its last value and the existing 7-day grace logic fires naturally.
- Acknowledgement: any purchase with `isAcknowledged == false` is acknowledged on every refresh. Play auto-refunds at 3 days otherwise.
- `lastSuccessfulBillingCheck` is stamped at the end of every successful refresh (i.e., a non-null `BillingState`).

## 7-day TTL — bounds the offline-refund attack

Without a TTL, an attacker could buy `paid_upgrade`, block internet to the app, refund via Play, and keep paid features forever (cached state never overwritten).

**Mitigation in `MainViewModel.init`:**
```kotlin
if (System.currentTimeMillis() - lastSuccessfulBillingCheck > 7d) {
    isPaidUser = false       // in-memory only
    isSubscriber = false
    // SharedPrefs unchanged — refreshBillingState restores them on next success
}
```

- Legit user offline + still entitled: cached value used until 7 d, then locked until next successful query.
- Attacker offline + refunded: locked at 7 d, max attack window = one billing cycle.
- Recovery: any successful Play round-trip restores correct state.

**Layer 2 (refund-lag fix) — LANDED 2026-05-11.** Gen 2 callable `verifyPurchase` in `functions/index.js` runs as `play-publisher@sync-23ce9.iam.gserviceaccount.com` (granted Play Console "View financial data"), enforces App Check, and calls Play Developer API `purchases.products.get` (INAPP) / `purchases.subscriptionsv2.get` (SUBS). `data/billing/EntitlementVerifier.kt` invokes it for every PURCHASED-state purchase Layer 1 returns; `MainViewModel.reconcileEntitlement` overrides the local flag with the server result. Closes the Console-admin refund-lag window (queryPurchasesAsync stuck on PURCHASED for 24h+ — Developer API reflects refunds in minutes). For SUBS, `subscriptionExpiry` is overwritten with the Developer-API `expiryTimeMillis` when verified. 24h server-result cache in `entitlement_verifier` prefs covers offline / App-Check-degraded windows. **Does NOT yet close decompile-and-forge** — an attacker who patches out the verifier call still gets local-flag forgery. Layer 2.5 (App-Check-gated `entitlements/{anonymousUid}` Firestore doc, treat local as cache only) is the follow-up that fully closes it.

## Debug override

`billingOverrideEnabled` (debug-only, `BuildConfig.DEBUG`-gated UI):
- **OFF (default):** `refreshBillingState` writes `isPaidUser` / `isSubscriber` / `subscriptionExpiry` from Play; the manual checkboxes + expiry picker render greyed (`Modifier.alpha(0.5f)`, `enabled = false`).
- **ON:** `refreshBillingState` early-returns before touching state. Manual checkboxes drive the flags; expiry picker drives `subscriptionExpiry`. The TTL gate also skips when override is on.
- Toggling OFF → re-runs `refreshBillingState` immediately to snap state back to Play.
- Release builds compile out the entire `if (BuildConfig.DEBUG)` block — only Play drives state. The override pref (`billingOverrideEnabled`) still persists but never has UI to flip it.

## License-tester refund propagation — A/B finding (2026-05-11)

Side-by-side test on Paul's device, same release install (2.10.18), same Google account, same `billing_dump.txt` session:

- **Subscription cancel via Play Store app** → `queryPurchasesAsync` dropped the SUBS purchase **within minutes**, no Restore Purchases tap needed. Flag correctly flipped to `false`.
- **paid_upgrade refund via Play Console → Order management** → `queryPurchasesAsync` **still returns `PURCHASED` with `isAcknowledged=true` after 22+ hours**, despite Play Console UI showing "Refunded since yesterday afternoon" and despite a full Play Store **data** clear + Google account sign-out/in.

Conclusion: **release-build BillingClient propagates Play-Store-app-driven cancellations promptly, but lags multi-day on Play-Console-admin-driven refunds of acknowledged INAPPs.** This is Play platform behavior — our code reads `Purchase.purchaseState` (live accessor) directly, no snapshot fields, no caching. Code audited 2026-05-11.

**Implication for production support**: when a real user requests a refund:
- Encourage them to use **Play Store app → Subscriptions/Orders → Refund** (fast pipeline) where possible (covers <48h auto-refunds, and most other cases).
- Refunds we issue from **Play Console admin** will have a propagation lag of 24h+ to BillingClient. The customer's local entitlement may not revoke promptly on the app side. The 7-day TTL gate still bounds the worst case.
- The behavior was not reproducible on **debug builds** (sideloaded, `.debug` applicationId) — they propagate Console-issued refunds instantly. Difference appears to come from Play Services' install-channel-aware caching path.

**Don't re-investigate** unless a production user reports a stuck entitlement after a refund. Diagnostic tool ready: `tools/check-purchase.js` (needs Play Developer API access granted to `bigquery-reader@sync-23ce9.iam.gserviceaccount.com`; that grant is blocked by the API access page being hidden on this Play Console account variant — see `project_prelaunch_todo.md`).

## Restore Purchases diagnostic dump (v2.10.18+)

`restorePurchases()` writes a snapshot to `/storage/emulated/0/Download/BudgeTrak/support/billing_dump.txt` (via `DiagDumpBuilder.writeDiagToMediaStore`) on every tap, in both debug and release. Captures flag state, `refreshBillingState` result, `ProductDetails` load status, **raw** `queryPurchasesAsync` output (every purchase, unfiltered — PENDING / UNSPECIFIED visible), and our PURCHASED-state filter outcome. Origin: license testers needed visibility into what Play's BillingClient actually returned when refunds didn't immediately revoke features on release builds (acknowledged-INAPP refund propagation lag).

Supporting plumbing:
- `BillingService.queryRawPurchases()` — public, returns unfiltered `Pair<List<Purchase>, List<Purchase>>?` (INAPP, SUBS). Promoted alongside the existing private `queryPurchases()`.
- `MainViewModel.refreshBillingStateWithState()` — private; same as `refreshBillingState` but returns `Pair<RestorePurchasesResult, BillingState?>`. `restorePurchases()` consumes both for the dump; the public `refreshBillingState()` is now a thin wrapper returning just the enum so the 4 other callers (init / onResume / onBillingPurchasesUpdated / billing-override-off) stay unchanged.

**Note:** debug + release have different applicationIds (`com.techadvantage.budgetrak.debug` vs `com.techadvantage.budgetrak`) → separate Play Billing namespaces. A dump taken on debug shows zero purchases unless the debug applicationId has its own SKUs in Play Console (typically it doesn't). To diagnose a stuck release-build purchase state, the dump must be captured on the release install.

## Existing 7-day SYNC admin grace period — works unchanged

`MainViewModel.kt:2165-2188` reads `groupHealth.subscriptionExpiry` from the group doc on every health check. The admin's local `subscriptionExpiry` (now Play-derived) is pushed via `FirestoreService.updateSubscriptionExpiry` when admin is online and `isSubscriber == true`.

- `elapsed > 7d` → admin dissolves group, non-admin evicts (`evictFromSync(strings.sync.evictionDissolved)`).
- `0 < elapsed ≤ 7d` → daily dashboard popup with `daysLeft` countdown.
- `elapsed ≤ 0` → silent.

Non-admin Subscriber claim-admin during grace → fresh Play-derived `subscriptionExpiry` (30d future) gets pushed → grace banners disappear, group survives. No code changes for this — the existing claim-admin flow already replays the new admin's `subscriptionExpiry` to the group doc.

## Production-promotion swap checklist

Before Production: nothing in code changes — same `BillingProducts.PAID_UPGRADE` / `SUBSCRIBER` IDs work across all tracks. **Required Console actions** before Production:
1. Confirm both products are in **Active** state in Play Console (not Draft).
2. Confirm pricing is configured in all target markets (Internal/Closed inherit from Production pricing).
3. License-tester validation (item 13c) complete.
4. Subscription's base plan is set to **monthly** with no introductory free-period (otherwise `purchaseTime + 30d` over-projects expiry during the trial).

## Anti-goals (do NOT do these)

- **Don't reintroduce** the v2.10.00 release-build override (`if (!BuildConfig.DEBUG) { isPaidUser = true; isSubscriber = true }`). It's permanently removed because Play Billing now drives state.
- **Don't bypass the TTL** for "convenience" — the 7-day window is the Layer-1 offline safety net; Layer 2 now also covers refund propagation, but TTL still matters when the server call is Unreachable. Tightening to a shorter TTL is fine; loosening is not.
- **Don't write `subscriptionExpiry` from anywhere except** `refreshBillingState` (Play-derived) and the debug DatePicker (manual). Avoid race conditions with the existing SYNC group-doc push.
- **Don't conflate** `Purchase.purchaseState == PURCHASED` with "fully active." Pending purchases (delayed payment, e.g., cash-at-convenience-store in some markets) have state `PENDING` and we correctly ignore them.
- **Don't acknowledge in `onPurchasesUpdated`** before refreshing state — we acknowledge inside `refreshBillingState` so the queryAll snapshot is the source of truth and we don't double-ack.
- **Don't query on every recomposition.** `refreshBillingState` is called from init + `onResume` + after a purchase flow + manual Restore Purchases tap. That's enough. Adding it to other paths risks rate-limiting from Play.
- **Don't merge INAPP + SUBS into one `queryProductDetailsAsync` call.** Billing 7+ throws `IllegalArgumentException("All products should be of the same product type")` synchronously from `setProductList(...)`. Issue two separate calls and merge results. Caught the hard way 2026-05-08 — every app launch crashed before the fix because the query runs in init.

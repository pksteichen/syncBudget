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

**Layer 2 (post-launch item #3)** closes the loophole entirely via App Check + Cloud Function token verification + server-authoritative entitlement flag in Firestore.

## Debug override

`billingOverrideEnabled` (debug-only, `BuildConfig.DEBUG`-gated UI):
- **OFF (default):** `refreshBillingState` writes `isPaidUser` / `isSubscriber` / `subscriptionExpiry` from Play; the manual checkboxes + expiry picker render greyed (`Modifier.alpha(0.5f)`, `enabled = false`).
- **ON:** `refreshBillingState` early-returns before touching state. Manual checkboxes drive the flags; expiry picker drives `subscriptionExpiry`. The TTL gate also skips when override is on.
- Toggling OFF → re-runs `refreshBillingState` immediately to snap state back to Play.
- Release builds compile out the entire `if (BuildConfig.DEBUG)` block — only Play drives state. The override pref (`billingOverrideEnabled`) still persists but never has UI to flip it.

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
- **Don't bypass the TTL** for "convenience" — the 7-day window is the only Layer-1 protection until Layer 2 ships. Tightening to a shorter TTL is fine; loosening is not.
- **Don't write `subscriptionExpiry` from anywhere except** `refreshBillingState` (Play-derived) and the debug DatePicker (manual). Avoid race conditions with the existing SYNC group-doc push.
- **Don't conflate** `Purchase.purchaseState == PURCHASED` with "fully active." Pending purchases (delayed payment, e.g., cash-at-convenience-store in some markets) have state `PENDING` and we correctly ignore them.
- **Don't acknowledge in `onPurchasesUpdated`** before refreshing state — we acknowledge inside `refreshBillingState` so the queryAll snapshot is the source of truth and we don't double-ack.
- **Don't query on every recomposition.** `refreshBillingState` is called from init + `onResume` + after a purchase flow + manual Restore Purchases tap. That's enough. Adding it to other paths risks rate-limiting from Play.
- **Don't merge INAPP + SUBS into one `queryProductDetailsAsync` call.** Billing 7+ throws `IllegalArgumentException("All products should be of the same product type")` synchronously from `setProductList(...)`. Issue two separate calls and merge results. Caught the hard way 2026-05-08 — every app launch crashed before the fix because the query runs in init.

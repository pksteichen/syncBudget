---
name: Play Billing Layer 2.5 — App-Check-gated Firestore entitlement doc (deferred anti-forge)
description: Design notes for closing the decompile-and-forge attack on paid local features. Defer until evidence of piracy.
type: project
---

# Layer 2.5 — App-Check-gated Firestore entitlement doc

## Why Layer 2 alone isn't enough

Layer 2 (LANDED 2026-05-11, see `project_play_billing_integration.md`) fixes the **refund-lag bug**: the local BillingClient cache can lag Console-admin refunds 24h+, and `verifyPurchase` now reads Google's authoritative purchase ledger to override. That part is solid and live.

What Layer 2 does **not** stop: **a decompile-and-forge attack on the local flag.** An attacker can:

1. Decompile the APK, locate `entitlementVerifier.verify(...)` in `MainViewModel.refreshBillingStateWithState`.
2. Replace the call with `VerifyResult.Verified(null, null)` (or patch `reconcileEntitlement` to always return `true`).
3. Set `isPaidUser = true` directly in SharedPreferences via a fake init path.
4. Re-sign with their own key and distribute.

Since all paid **local** features (CSV/PDF export, receipt photos, unlimited widget transactions, Cash Flow Simulation, ad-free) run client-side with no server calls, they "just work" against a forged local flag. App Check already protects SYNC / cloud receipts / admin features (signing-cert mismatch on the modified APK fails the App Integrity verdict). Layer 2.5 specifically targets the **paid local features**.

## Layer 2.5 design

Make the **server** the source of truth, local flag becomes a pure cache that's only honored when a recent server read confirmed it.

### Cloud Function side — extend `verifyPurchase`

After verifying with Play Developer API, also write to Firestore:

```
entitlements/{anonymousUid}
{
  isPaidUser:        bool,
  isSubscriber:      bool,
  expiryTimeMillis:  number | null,
  lastVerifiedAt:    number (server timestamp),
  paidOrderId:       string | null,
  subOrderId:        string | null,
}
```

Admin SDK bypasses Firestore rules, so the Cloud Function can write while clients are denied.

### Firestore rules

```javascript
match /entitlements/{uid} {
  allow read:  if request.auth.uid == uid;  // App Check enforced globally
  allow write: if false;                    // only Cloud Function writes
}
```

App Check enforcement is already global on the project; reads from a modified APK whose signing cert doesn't match get rejected at the Firestore layer.

### Android side

- New `EntitlementSource` reads `entitlements/{ourUid}` on every app launch + after each `verifyPurchase` call.
- Derived flag: `isPaidUser = serverEntitlement.isPaidUser || (cachedEntitlement.isPaidUser && cacheAgeMs < 30d)`.
- An install that has **never** seen a successful Firestore read keeps `isPaidUser = false` regardless of what local prefs say.

### Why an attacker can't bypass

- Modified APK → different signing cert → Play Integrity App Check verdict fails → Firestore rejects the entitlement-doc read → derived flag stays false.
- Patching local prefs is no longer sufficient — the gate is "did we ever see this doc on the server."
- They *could* patch `EntitlementSource` to always return `true`, but the bar is now "rewrite the entitlement model" rather than "flip a boolean." Qualitatively harder.

## Caveats / costs

1. **Forces Anonymous Auth on solo users.** Currently solo users skip Firebase Auth entirely (only SYNC users authenticate, gated by `isSyncConfigured`). Layer 2.5 needs anonymous auth for everyone. Cost is trivial; behavior change worth tracking. Per-install UID means uninstall + reinstall = empty entitlement doc; Restore Purchases flow re-populates it via `verifyPurchase`.
2. **Offline cold-start grace.** First launch with no network: paid features locked. Mitigation: honor the cache for 30 days after a successful server read. Prevents momentary blips from locking paying users.
3. **One extra Firestore read per session.** Negligible at BudgeTrak scale relative to existing per-collection cursor reads.
4. **Implementation effort:** ~1–2 days. New rule + `verifyPurchase` write side, new `EntitlementSource`, fallback logic, careful migration for existing installs that already have `isPaidUser = true` in prefs but no Firestore doc.
5. **Doesn't stop "rewrite the whole entitlement check."** Determined attackers with time can rewrite anything. Threat-model argument: budget app, $9.99 one-time — the marginal pirate isn't investing those hours.

## Why defer

- Refund-lag (the *known* hurting issue) is fixed by today's Layer 2.
- Decompile-and-forge is purely theoretical at current scale; no in-the-wild evidence.
- Existing **runtime signature pinning** (`BudgeTrakApplication.verifyAppSignature`, see `project_prelaunch_todo.md` item #4) already catches naive sideload-an-unmodified-APK-to-a-friend. Smali patching to strip it requires sophistication that doesn't match the product's price.

## When to revisit

- Crashlytics signature-pin failures spike (evidence of repackaging in the wild).
- Entitlement claims from devices that never appeared in Play purchase records (hard to detect today without server-side analytics — Layer 2.5 itself would surface this once the entitlement doc exists).
- Paid revenue becomes material enough that even small leakage matters.

## How to apply

Don't ship Layer 2.5 absent one of the triggers above. If the user asks "should we tighten anti-piracy?" — the answer is no until there's evidence. If the user asks "can we make `isPaidUser` server-authoritative?" — this memory IS the answer; pull the design from here rather than re-deriving.

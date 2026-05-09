---
name: Subscriber tier is a superset of Paid — every "paid feature" gate must check both
description: BudgeTrak's tier hierarchy is Free → Paid (one-time $9.99) → Subscriber (monthly $4.99). Subscriber includes all Paid features plus AI/SYNC-admin. So any gate that hides ads, enables photos, etc. must check `isPaidUser || isSubscriber`. A 2026-05-09 audit found 6 gates that incorrectly checked `isPaidUser` only.
type: feedback
---

**Rule.** A Paid feature gate is `isPaidUser || isSubscriber`. A Subscriber-only gate (AI, SYNC create/admin) is `isSubscriber`. There is no `isPaidUser`-only gate that's correct — Subscribers should never lose access to Paid-tier features.

**The hierarchy:**
- **Free:** ads shown, basic features, can join SYNC but not create/admin, no import/export
- **Paid** ($9.99 one-time): no ads, photo capture, CSV/PDF/XLSX export, simulation chart, unlimited widget transactions
- **Subscriber** ($4.99/mo): everything in Paid + AI receipt OCR + AI CSV categorize + SYNC create/admin

**Bugs caught 2026-05-09 (all fixed in v2.10.15):**

1. `MainActivity.kt` ad-bar gate (`adSize` null check + `remember` key) — only checked `isPaidUser`, so Subscribers saw ads.
2. `MainActivity.kt:1003` `QuickStartGuide` `isPaidUser` parameter — used to compute ad-bar padding; Subscribers got extra padding without an ad bar.
3. `MainActivity.kt:2009` `receiptCacheSize` `remember` key — didn't invalidate on Subscriber tier changes.
4. `SettingsScreen.kt:949` receipt-photo settings section — hidden from Subscribers.
5. `SettingsScreen.kt:1014-1015` Save Photos button — disabled for Subscribers.
6. `BudgetWidgetProvider.kt:185` widget overlay — Subscribers saw "upgrade" overlay.
7. `WidgetTransactionActivity.kt:156` widget daily-tx limit — Subscribers were rate-limited to 1 tx/day like Free users.

**Pattern recognition for future code:**

When adding a feature gate, ask: "Is this a Subscriber-exclusive feature?" If no, the gate must use `isPaidUser || isSubscriber`. If a feature is Subscriber-exclusive (AI / SYNC-admin), gate just on `isSubscriber`. There is no remaining "Paid-only" use case in BudgeTrak's tier model.

**Existing convention to be aware of:**

In `TransactionsScreen.kt` and `SavingsGoalsScreen.kt`, the parameter named `isPaidUser` is fed `vm.isPaidUser || vm.isSubscriber` from `MainActivity` at the call site. So inside those screens, `if (isPaidUser) { ... }` is functionally `paid || sub` and is correct. The naming is misleading but the semantics are right — don't "fix" those internal checks without also tracing the call-site value.

**Subscriber-implies-Paid invariant for debug overrides:**

The Settings → Override Google Billing → Subscriber checkbox auto-checks Paid (existing). The Paid checkbox refuses to uncheck while Subscriber is on (added v2.10.15) — keeps the invariant clean even in debug mode. The Subscriber-implies-Paid bidirectional logic prevents impossible states like "Subscriber=true, Paid=false" that would confuse the gates.

**How to apply when adding new paid features:**

1. Decide tier: is this Subscriber-exclusive or Paid-or-higher?
2. Gate accordingly: `isSubscriber` for sub-only, `isPaidUser || isSubscriber` for paid-or-higher.
3. If reading flags from SharedPreferences directly (e.g., from a Worker or non-UI context), do `prefs.getBoolean("isPaidUser", false) || prefs.getBoolean("isSubscriber", false)` — see `BudgetWidgetProvider.kt` and `WidgetTransactionActivity.kt` for examples.

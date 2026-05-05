---
name: BudgeTrak pricing tiers
description: Pricing and feature gates for free, paid ($9.99 one-time), and subscriber ($4.99/month) tiers. Decided 2026-04-12.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
## Pricing (decided 2026-04-12)

| Tier | Price | Key gates |
|---|---|---|
| Free | $0 | Budget engine, tracking, widget (1/day), join SYNC groups. NO import/save, no photos, has ads. |
| Paid | $9.99 one-time | CSV/Excel import, save to CSV/Excel/PDF, receipt photos (5/txn), Cash Flow Simulation, AI CSV categorization (opt-in), unlimited widget, ad-free, join SYNC. |
| Subscriber | $4.99/month | All paid features + create/admin SYNC groups, AI receipt scanning (opt-in). |

**Cash Flow Simulation moved from Subscriber to Paid (~mid-April 2026)** — verified in code at `SavingsGoalsScreen.kt:292`: `val canViewChart = isPaidUser || isSubscriber`. The Subscriber-only era is documented for historical context but no longer reflects the gating.

**Free tier can join SYNC groups** but cannot create or admin them. This lets a family member participate without paying — only the admin needs a subscription.

**Free tier cannot import or save** (CSV, Excel, PDF). This is a conversion lever: users who want to load bank statements or export data must upgrade to Paid.

**Why:** These prices are deliberately low compared to competitors. YNAB is $14.99/month, Goodbudget is $10/month, EveryDollar is $17.99/month. BudgeTrak's one-time $9.99 paid upgrade is less than a single month of any competitor, and even the $4.99/month subscription is a fraction of the competition.

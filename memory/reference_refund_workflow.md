---
name: Play Store refund handling — customer email template + Play Console workflow
description: How to process refund requests from BudgeTrak customers. Canned email template asking for Order ID, plus the Play Console steps to issue the refund. Play Console hides buyer identity (no name/email shown) — Order ID is the only handle.
type: reference
---

## Customer-facing email template (canned reply)

Send this when a customer emails `techadvantagesupport@gmail.com` asking for a refund without including their Order ID:

> Thanks for reaching out. To process your refund, please reply with your Google Play Order ID (it starts with "GPA." followed by numbers). You can find it in the Play Store app under Profile → Payments & subscriptions → Budget & history → BudgeTrak. Or it's in the receipt email Google sent at purchase (sender: googleplay-noreply@google.com).

## Play Console workflow (once you have the Order ID)

1. Play Console → Order management → paste Order ID into search box.
2. Click the matching order → "Refund" button.
3. Choose **Full refund** or **Partial refund** (partial is rare — usually full).
4. For subscriptions: also tick **Cancel subscription** to stop future renewals.
5. Refund propagates to the customer within 24-72 h (Google's pipeline). The entitlement revocation in BudgeTrak may lag by hours on acknowledged INAPPs — see `project_play_billing_integration.md` "Restore Purchases diagnostic dump" section.

## Self-service path (most refunds — zero developer work)

- **Within 48 h of purchase**: customer can self-serve at `play.google.com/store/account/orderhistory` or in the Play Store app → Profile → Payments & subscriptions → Budget & history → tap order → "Request a refund". Google auto-approves and refunds immediately. You won't see it unless you watch Order Management.
- **After 48 h**: their request gets routed to you via Play Console messages + email notification.

So if a customer's purchase is < 48 h old, the fastest answer is "go through the Play Store app — you can self-refund instantly there." No developer involvement.

## When customer can't find their Order ID

- Ask for: **purchase date + approximate amount + Google account email used**.
- In Play Console → Order management, filter by date range, match visually.
- Or have them forward the `googleplay-noreply@google.com` receipt email — Order ID is in the body.

## Anti-goals

- **Don't surface a refund link in the BudgeTrak UI.** Discoverability of the refund flow encourages refunds. Customers who genuinely want one will find it via Play Store; we don't need to advertise.
- **Don't try to identify customers by name or email in Play Console** — Google deprecated buyer-identity display in Order Management for privacy. Don't waste time looking for a UI affordance that doesn't exist; Order ID is the handle.

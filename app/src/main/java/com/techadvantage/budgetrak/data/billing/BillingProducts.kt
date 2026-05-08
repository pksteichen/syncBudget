package com.techadvantage.budgetrak.data.billing

/**
 * Play Billing product IDs configured in the Play Console.
 *
 * - PAID_UPGRADE: in-app product, non-consumable, $9.99 USD one-time.
 *   Once purchased, isPaidUser stays true forever.
 * - SUBSCRIBER: subscription, $4.99 USD per month, monthly base plan.
 *   purchase.purchaseTime advances on each successful auto-renewal;
 *   we derive subscriptionExpiry = purchaseTime + SUB_PERIOD_MS so the
 *   existing 7-day grace logic works without server-side queries.
 *
 * Changing these IDs requires Play Console product re-creation + ~24 h
 * SKU catalog propagation before clients can query them.
 */
object BillingProducts {
    const val PAID_UPGRADE = "paid_upgrade"
    const val SUBSCRIBER = "subscriber"

    /** Monthly subscription period in milliseconds — 30 days. */
    const val SUB_PERIOD_MS = 30L * 24 * 60 * 60 * 1000
}

#!/usr/bin/env node
/**
 * Query Google Play Developer API for authoritative purchase / refund state.
 *
 * Use this when BillingClient on-device is returning stale purchase state and
 * you need to confirm what Play's backend actually thinks. Bypasses Play
 * Services and the device's BillingClient cache entirely.
 *
 * Usage:
 *   node tools/check-purchase.js --voided
 *     # Lists every voided/refunded purchase Play knows about for BudgeTrak
 *     # (last 30 days). Use this to confirm a refund has actually been
 *     # processed on Play's side. If an order appears here, BillingClient
 *     # *should* (eventually) reflect it — if it doesn't after this returns
 *     # a match, the bug is in Play Services' on-device cache, not the
 *     # backend.
 *
 *   node tools/check-purchase.js --token <full-purchase-token>
 *     # Calls purchases.products.get against the token. Returns the full
 *     # Purchase resource Play has on record — purchaseState, consumption,
 *     # acknowledge state, etc. 410 GONE = Play has revoked / forgotten it.
 *
 * Auth:
 *   Uses ~/.config/budgetrak/sa-key.json (same SA as query-crashlytics.js).
 *   The SA must additionally be linked in Play Console → Setup → API access
 *   with at minimum "View financial data, orders, and cancellation survey
 *   responses" permission. The Android Publisher API must be enabled in GCP.
 *
 * If you get a 401/403, the SA isn't linked / lacks permission. If you get a
 * "API has not been used" 403, enable Android Publisher API in GCP Console.
 */

const { google } = require('googleapis');
const fs = require('fs');
const path = require('path');

const PACKAGE_NAME = 'com.techadvantage.budgetrak';
const SA_KEY = path.join(process.env.HOME || '', '.config/budgetrak/sa-key.json');

async function authClient() {
    if (!fs.existsSync(SA_KEY)) {
        console.error(`Missing service account key at ${SA_KEY}`);
        process.exit(1);
    }
    const auth = new google.auth.GoogleAuth({
        keyFile: SA_KEY,
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    return await auth.getClient();
}

async function listVoided() {
    const client = await authClient();
    const publisher = google.androidpublisher({ version: 'v3', auth: client });
    // 30-day window (API max). Includes refunds, chargebacks, dev-initiated
    // voids, and Google-initiated voids.
    const cutoff = Date.now() - 29 * 24 * 60 * 60 * 1000;
    try {
        const res = await publisher.purchases.voidedpurchases.list({
            packageName: PACKAGE_NAME,
            startTime: String(cutoff),
            endTime: String(Date.now()),
        });
        const entries = res.data.voidedPurchases || [];
        if (entries.length === 0) {
            console.log('No voided purchases in the last 30 days.');
            return;
        }
        console.log(`Found ${entries.length} voided purchase(s) in last 30d:\n`);
        for (const v of entries) {
            const voidedAt = new Date(Number(v.voidedTimeMillis));
            const purchasedAt = new Date(Number(v.purchaseTimeMillis));
            // voidedSource: 0=user, 1=developer, 2=Google; voidedReason: 0=other, 1=remorse, 2=not_received, 3=defective, 4=accidental, 5=fraud
            const sourceMap = { 0: 'user', 1: 'developer', 2: 'google' };
            const reasonMap = { 0: 'other', 1: 'remorse', 2: 'not_received', 3: 'defective', 4: 'accidental_purchase', 5: 'fraud' };
            console.log(`  orderId:    ${v.orderId}`);
            console.log(`  productId:  ${v.productId || '(legacy — productId only on v2+)'}`);
            console.log(`  voidedAt:   ${voidedAt.toISOString()}`);
            console.log(`  purchasedAt:${purchasedAt.toISOString()}`);
            console.log(`  source:     ${sourceMap[v.voidedSource] || v.voidedSource}`);
            console.log(`  reason:     ${reasonMap[v.voidedReason] || v.voidedReason}`);
            console.log(`  token suffix: ...${(v.purchaseToken || '').slice(-8)}`);
            console.log();
        }
    } catch (e) {
        console.error('API error:', e.message);
        if (e.errors) console.error('Details:', JSON.stringify(e.errors, null, 2));
        process.exit(2);
    }
}

async function getProduct(token) {
    const client = await authClient();
    const publisher = google.androidpublisher({ version: 'v3', auth: client });
    // productId is required by the API; we try paid_upgrade first (the only
    // INAPP product we currently sell).
    try {
        const res = await publisher.purchases.products.get({
            packageName: PACKAGE_NAME,
            productId: 'paid_upgrade',
            token: token,
        });
        console.log('purchases.products.get response:');
        console.log(JSON.stringify(res.data, null, 2));
        // purchaseState: 0=purchased, 1=canceled, 2=pending
        const stateMap = { 0: 'PURCHASED', 1: 'CANCELED', 2: 'PENDING' };
        if (res.data && typeof res.data.purchaseState !== 'undefined') {
            console.log(`\nPlay backend purchaseState: ${stateMap[res.data.purchaseState] || res.data.purchaseState}`);
        }
    } catch (e) {
        if (e.code === 410) {
            console.log('Play returned 410 GONE — purchase has been fully revoked / forgotten on Play\'s side.');
        } else {
            console.error('API error:', e.message);
            if (e.errors) console.error('Details:', JSON.stringify(e.errors, null, 2));
            process.exit(2);
        }
    }
}

(async () => {
    const args = process.argv.slice(2);
    if (args.includes('--voided')) {
        await listVoided();
    } else if (args.includes('--token')) {
        const i = args.indexOf('--token');
        const token = args[i + 1];
        if (!token) {
            console.error('--token requires the full purchase token as the next arg');
            process.exit(1);
        }
        await getProduct(token);
    } else {
        console.log('Usage:');
        console.log('  node tools/check-purchase.js --voided');
        console.log('  node tools/check-purchase.js --token <full-purchase-token>');
        process.exit(1);
    }
})();

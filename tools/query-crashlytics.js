#!/usr/bin/env node
/**
 * Query Crashlytics + Firebase Analytics + Performance data from BigQuery.
 *
 * Usage:
 *   node tools/query-crashlytics.js                  # Recent events (24h)
 *   node tools/query-crashlytics.js --days 7         # Last 7 days
 *   node tools/query-crashlytics.js --nonfatals      # Non-fatals (PERMISSION_DENIED etc)
 *   node tools/query-crashlytics.js --crashes        # Fatals only
 *   node tools/query-crashlytics.js --keys           # Custom keys from recent events
 *   node tools/query-crashlytics.js --analytics      # Firebase Analytics events
 *   node tools/query-crashlytics.js --query "SELECT ..."  # Custom SQL
 *
 * Auth (in priority order):
 *   1. GOOGLE_APPLICATION_CREDENTIALS env var → path to service account key JSON
 *   2. ~/.config/budgetrak/sa-key.json (default service account location)
 *   3. ~/.config/configstore/firebase-tools.json (firebase CLI refresh token,
 *      subject to Workspace RAPT policy — re-auth every ~3-7 days)
 *
 * The Crashlytics queries UNION the legacy `com_securesync_app_*` tables (data
 * through 2026-04-12) and the rebranded `com_techadvantage_budgetrak_*` tables
 * (data from 2026-04-26), so historical lookups span the rebrand.
 */

const { BigQuery } = require('@google-cloud/bigquery');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'sync-23ce9';
const ANALYTICS_PROPERTY_ID = '534603748';

const CRASHLYTICS_DATASET = 'firebase_crashlytics';
const CRASHLYTICS_TABLES_REALTIME = [
    `${CRASHLYTICS_DATASET}.com_securesync_app_ANDROID_REALTIME`,
    `${CRASHLYTICS_DATASET}.com_techadvantage_budgetrak_ANDROID_REALTIME`,
];
const CRASHLYTICS_TABLES_BATCH = [
    `${CRASHLYTICS_DATASET}.com_securesync_app_ANDROID`,
    `${CRASHLYTICS_DATASET}.com_techadvantage_budgetrak_ANDROID`,
];
const ANALYTICS_DATASET = `analytics_${ANALYTICS_PROPERTY_ID}`;

// ── Auth resolution ──────────────────────────────────────────────────────
function resolveCredentials() {
    // 1. Explicit env var (cloud-standard)
    const envPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (envPath && fs.existsSync(envPath)) {
        return { type: 'service_account', keyFilename: envPath };
    }
    // 2. Default SA location for this project
    const defaultSaPath = path.join(process.env.HOME || '', '.config/budgetrak/sa-key.json');
    if (fs.existsSync(defaultSaPath)) {
        return { type: 'service_account', keyFilename: defaultSaPath };
    }
    // 3. Fall back to firebase CLI refresh token (RAPT-limited)
    const fbConfigPath = path.join(process.env.HOME || '', '.config/configstore/firebase-tools.json');
    if (fs.existsSync(fbConfigPath)) {
        const cfg = JSON.parse(fs.readFileSync(fbConfigPath, 'utf8'));
        if (cfg.tokens?.refresh_token) {
            return {
                type: 'firebase_user',
                refresh_token: cfg.tokens.refresh_token,
                client_id: '563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com',
                client_secret: 'j9iVZfS8kkCEFUPaAeJV0sAi',
            };
        }
    }
    return null;
}

function buildBigQueryClient(creds) {
    if (creds.type === 'service_account') {
        return new BigQuery({ projectId: PROJECT_ID, keyFilename: creds.keyFilename });
    }
    // firebase_user (legacy refresh-token flow)
    return new BigQuery({
        projectId: PROJECT_ID,
        credentials: {
            type: 'authorized_user',
            client_id: creds.client_id,
            client_secret: creds.client_secret,
            refresh_token: creds.refresh_token,
        },
    });
}

// ── Query builders ────────────────────────────────────────────────────────
function unionRealtime(selectCols, whereExtra = '', limit = 50) {
    const parts = CRASHLYTICS_TABLES_REALTIME.map(
        t => `SELECT ${selectCols} FROM \`${PROJECT_ID}.${t}\` WHERE TRUE${whereExtra}`
    );
    return `${parts.join(' UNION ALL ')} ORDER BY event_timestamp DESC LIMIT ${limit}`;
}

function buildQuery({ days, nonFatalsOnly, crashesOnly, keysOnly, analyticsOnly, customQuery }) {
    if (customQuery) return customQuery;

    if (analyticsOnly) {
        // events_* is partitioned by date suffix; events_intraday_* covers today.
        const startDate = `FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL ${days} DAY))`;
        const endDate = `FORMAT_DATE('%Y%m%d', CURRENT_DATE())`;
        return `
            SELECT event_timestamp, event_name,
                   ARRAY(
                     SELECT AS STRUCT key, value.string_value AS s, value.int_value AS i,
                                      value.double_value AS d
                     FROM UNNEST(event_params)
                   ) AS params,
                   user_pseudo_id, platform
            FROM \`${PROJECT_ID}.${ANALYTICS_DATASET}.events_*\`
            WHERE _TABLE_SUFFIX BETWEEN ${startDate} AND ${endDate}
              AND event_name IN ('ocr_feedback', 'health_beacon')
            ORDER BY event_timestamp DESC
            LIMIT 50
        `;
    }

    const dateFilter = ` AND DATE(event_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL ${days} DAY)`;

    if (keysOnly) {
        return unionRealtime('event_timestamp, custom_keys, logs', dateFilter, 20);
    }
    if (nonFatalsOnly) {
        return unionRealtime(
            'event_timestamp, issue_id, issue_title, blame_frame.file AS file, blame_frame.line AS line, custom_keys, logs',
            ` AND is_fatal = false${dateFilter}`,
            50
        );
    }
    if (crashesOnly) {
        return unionRealtime(
            'event_timestamp, issue_id, issue_title, blame_frame.file AS file, blame_frame.line AS line, device.model AS device_model',
            ` AND is_fatal = true${dateFilter}`,
            20
        );
    }
    return unionRealtime(
        'event_timestamp, is_fatal, issue_id, issue_title, blame_frame.file AS file, blame_frame.line AS line, device.model AS device_model',
        dateFilter,
        50
    );
}

// ── Main ──────────────────────────────────────────────────────────────────
async function main() {
    const args = process.argv.slice(2);
    const days = args.includes('--days') ? parseInt(args[args.indexOf('--days') + 1]) : 1;
    const opts = {
        days,
        nonFatalsOnly: args.includes('--nonfatals'),
        crashesOnly: args.includes('--crashes'),
        keysOnly: args.includes('--keys'),
        analyticsOnly: args.includes('--analytics'),
        customQuery: args.includes('--query') ? args[args.indexOf('--query') + 1] : null,
    };

    const creds = resolveCredentials();
    if (!creds) {
        console.error('No credentials found. Provide one of:');
        console.error('  - GOOGLE_APPLICATION_CREDENTIALS env var (service account key path)');
        console.error('  - ~/.config/budgetrak/sa-key.json (service account)');
        console.error('  - firebase login (~/.config/configstore/firebase-tools.json)');
        process.exit(1);
    }
    console.error(`Auth: ${creds.type === 'service_account' ? `service account (${creds.keyFilename})` : 'firebase CLI refresh token'}`);

    const bigquery = buildBigQueryClient(creds);
    const query = buildQuery(opts);

    try {
        const [rows] = await bigquery.query({ query });
        if (rows.length === 0) {
            console.log('No results found.');
            return;
        }
        console.log(`${rows.length} results:\n`);
        for (const row of rows) {
            console.log(JSON.stringify(row, null, 2));
            console.log('---');
        }
    } catch (err) {
        console.error('Query failed:', err.message);
        if (err.message.includes('invalid_rapt')) {
            console.error('\nFirebase CLI RAPT expired. Re-auth with `firebase login --reauth`,');
            console.error('or switch to a service account (see header comment).');
        } else if (err.message.includes('Not found') || err.message.includes('does not exist')) {
            console.error('\nDataset/table may not exist yet. BigQuery exports take up to 24h.');
            console.error(`Check: https://console.cloud.google.com/bigquery?project=${PROJECT_ID}`);
        } else if (err.message.includes('Permission') || err.message.includes('access')) {
            console.error('\nMissing IAM permissions. Service account needs:');
            console.error('  - roles/bigquery.jobUser (run queries)');
            console.error('  - roles/bigquery.dataViewer (read data)');
            console.error(`Both at project scope on ${PROJECT_ID}.`);
        }
        process.exit(1);
    }
}

main();

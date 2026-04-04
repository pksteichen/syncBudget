#!/usr/bin/env node
/**
 * Query Crashlytics data from BigQuery.
 * Usage:
 *   node tools/query-crashlytics.js                  # Recent crashes + non-fatals (24h)
 *   node tools/query-crashlytics.js --days 7         # Last 7 days
 *   node tools/query-crashlytics.js --query "SELECT * FROM ... LIMIT 10"  # Custom SQL
 *   node tools/query-crashlytics.js --nonfatals      # Non-fatals only (PERMISSION_DENIED etc)
 *   node tools/query-crashlytics.js --crashes        # Crashes only
 *   node tools/query-crashlytics.js --keys           # Custom keys from recent events
 */

const { BigQuery } = require('@google-cloud/bigquery');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'sync-23ce9';
const DATASET = 'firebase_crashlytics';
const TABLE_CRASHES = `${DATASET}.com_securesync_app_ANDROID`;
const TABLE_REALTIME = `${DATASET}.com_securesync_app_ANDROID_REALTIME`;

// Auth: use Firebase CLI refresh token
const configPath = path.join(process.env.HOME, '.config/configstore/firebase-tools.json');
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
const refreshToken = config.tokens.refresh_token;

async function getAccessToken() {
    const resp = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `grant_type=refresh_token&refresh_token=${refreshToken}&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi`
    });
    const data = await resp.json();
    return data.access_token;
}

async function main() {
    const args = process.argv.slice(2);
    const days = args.includes('--days') ? parseInt(args[args.indexOf('--days') + 1]) : 1;
    const nonFatalsOnly = args.includes('--nonfatals');
    const crashesOnly = args.includes('--crashes');
    const keysOnly = args.includes('--keys');
    const customQuery = args.includes('--query') ? args[args.indexOf('--query') + 1] : null;

    const accessToken = await getAccessToken();

    const bigquery = new BigQuery({
        projectId: PROJECT_ID,
        credentials: {
            type: 'authorized_user',
            client_id: '563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com',
            client_secret: 'j9iVZfS8kkCEFUPaAeJV0sAi',
            refresh_token: refreshToken
        }
    });

    let query;
    if (customQuery) {
        query = customQuery;
    } else if (keysOnly) {
        query = `
            SELECT event_timestamp, custom_keys, logs
            FROM \`${PROJECT_ID}.${TABLE_REALTIME}\`
            WHERE DATE(event_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL ${days} DAY)
            ORDER BY event_timestamp DESC
            LIMIT 20
        `;
    } else if (nonFatalsOnly) {
        query = `
            SELECT event_timestamp, issue_id, issue_title,
                   blame_frame.file AS file, blame_frame.line AS line,
                   custom_keys, logs
            FROM \`${PROJECT_ID}.${TABLE_REALTIME}\`
            WHERE is_fatal = false
              AND DATE(event_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL ${days} DAY)
            ORDER BY event_timestamp DESC
            LIMIT 50
        `;
    } else if (crashesOnly) {
        query = `
            SELECT event_timestamp, issue_id, issue_title,
                   blame_frame.file AS file, blame_frame.line AS line,
                   device.model AS device_model, device.os_version AS os_version
            FROM \`${PROJECT_ID}.${TABLE_REALTIME}\`
            WHERE is_fatal = true
              AND DATE(event_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL ${days} DAY)
            ORDER BY event_timestamp DESC
            LIMIT 20
        `;
    } else {
        query = `
            SELECT event_timestamp, is_fatal, issue_id, issue_title,
                   blame_frame.file AS file, blame_frame.line AS line,
                   device.model AS device_model
            FROM \`${PROJECT_ID}.${TABLE_REALTIME}\`
            WHERE DATE(event_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL ${days} DAY)
            ORDER BY event_timestamp DESC
            LIMIT 50
        `;
    }

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
        if (err.message.includes('Not found')) {
            console.error('\nDataset may not exist yet. BigQuery export can take up to 24 hours for initial data.');
            console.error('Check: https://console.cloud.google.com/bigquery?project=sync-23ce9');
        }
    }
}

main();

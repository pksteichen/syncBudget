---
name: Crashlytics + BigQuery query tool
description: How to query BudgeTrak Crashlytics data from Termux via BigQuery — covers tool, auth, queries. Non-query infrastructure (recordNonFatal / syncEvent / updateDiagKeys / HEALTH_BEACON) lives in spec_diagnostics.md.
type: reference
---

## Tool
- `tools/query-crashlytics.js` — Node.js CLI using `@google-cloud/bigquery`.
- Auth: uses the Firebase CLI refresh token from `~/.config/configstore/firebase-tools.json`.
- Dependencies installed in project root (`package.json`).

## Tables
- Streaming: `com_securesync_app_ANDROID_REALTIME` (events within minutes).
- Batch: `com_securesync_app_ANDROID` (events within 24 h).
- **Table names still carry the legacy applicationId `com_securesync_app`.** Firebase does not rename existing BigQuery export tables after a package rename. New events from the renamed `com.techadvantage.budgetrak` app continue to land in the same tables.

## Usage

```bash
node tools/query-crashlytics.js                 # events, last 24 h
node tools/query-crashlytics.js --days 7        # last 7 days
node tools/query-crashlytics.js --nonfatals     # PERMISSION_DENIED + other non-fatals
node tools/query-crashlytics.js --crashes       # fatal crashes only (schema note below)
node tools/query-crashlytics.js --keys          # custom keys
node tools/query-crashlytics.js --query "SQL"   # custom BigQuery SQL
```

Schema note: `--crashes` has a known schema error on the REALTIME table (`os_version` doesn't exist there). Use `--query` with the batch table if needed.

## Getting a raw access token

```bash
TOKEN=$(node -e "
const creds = require(require('path').join(require('os').homedir(), '.config/configstore/firebase-tools.json'));
const rt = creds.tokens && creds.tokens.refresh_token;
const https = require('https');
const data = 'grant_type=refresh_token&refresh_token=' + rt + '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
const req = https.request({hostname:'oauth2.googleapis.com',path:'/token',method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'}}, res => {
  let body=''; res.on('data',d=>body+=d); res.on('end',()=>console.log(JSON.parse(body).access_token));
});
req.write(data); req.end();
" 2>/dev/null)
```

## Regular checks

### Overnight
1. `node tools/query-crashlytics.js --nonfatals` — look for PERMISSION_DENIED clusters.
2. Check `token_log.txt` in support dir for token refresh gaps.
3. If PERMISSION_DENIED: measure time between last `AppCheck token refreshed` and first PERMISSION_DENIED.

### After new releases
1. `node tools/query-crashlytics.js --crashes` — new crash signatures?
2. Check `issue_title` for LazyColumn key collisions, NPEs, sync-related crashes.
3. Compare crash rate before/after release.

### Weekly
1. `node tools/query-crashlytics.js --days 7` — overview.
2. Look for patterns: single device crashing repeatedly, same collection getting PERMISSION_DENIED, concentration by `syncGroupId`.

## Other diagnostic files
- Token log: `support/token_log.txt` — written by `BudgeTrakApplication.tokenLog()` (debug builds only since v2.6). Capped at 100 KB.
- State dump: `support/sync_diag.txt` — built by `DiagDumpBuilder`. See `spec_diagnostics.md`.

## Project
- Firebase project ID: `sync-23ce9`.
- Dataset: `firebase_crashlytics`.

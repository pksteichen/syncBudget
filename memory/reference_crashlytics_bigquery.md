---
name: Crashlytics + BigQuery query tool
description: How to query BudgeTrak Crashlytics + Firebase Analytics from BigQuery — auth, tables, device differentiation, build-filter pattern. Non-query diagnostics (recordNonFatal / syncEvent / HEALTH_BEACON) live in spec_diagnostics.md.
type: reference
---

## Tool
- `tools/query-crashlytics.js` — Node.js CLI using `@google-cloud/bigquery`.
- Auth priority: `GOOGLE_APPLICATION_CREDENTIALS` env → `~/.config/budgetrak/sa-key.json` (default SA, see `reference_bigquery_service_account.md`) → Firebase CLI refresh token (RAPT-limited fallback).
- Project: `sync-23ce9`. Dataset: `firebase_crashlytics`. Analytics dataset: `analytics_534603748`.

## Tables (queried as UNION ALL)
- Legacy: `com_securesync_app_ANDROID` (batch) + `com_securesync_app_ANDROID_REALTIME` (stream). Receives data through 2026-04-12 (rebrand date).
- Rebranded: `com_techadvantage_budgetrak_ANDROID` + `..._REALTIME`. Receives data from 2026-04-26 (export enabled).
- **Both unioned** by `unionRealtime()` so historical lookups span the rebrand.

## Usage

```bash
node tools/query-crashlytics.js                  # events, last 24 h
node tools/query-crashlytics.js --days 7         # last 7 days
node tools/query-crashlytics.js --nonfatals      # PERMISSION_DENIED + other non-fatals
node tools/query-crashlytics.js --crashes        # fatals only
node tools/query-crashlytics.js --keys           # raw custom_keys arrays
node tools/query-crashlytics.js --analytics      # ocr_feedback + health_beacon
node tools/query-crashlytics.js --list-devices   # distinct device fingerprints (7d default)
node tools/query-crashlytics.js --build 2026-05  # filter to events from builds whose buildTime starts with prefix
node tools/query-crashlytics.js --query "SQL"    # custom BigQuery SQL
```

## Differentiating devices in BigQuery

Each Crashlytics event carries these identifying fields:

| Field | Meaning | Example |
|---|---|---|
| `application.display_version` | `versionName` from `build.gradle.kts` | `"2.8"` |
| `application.id` | `applicationId` (rarely changes) | `com.techadvantage.budgetrak` |
| `device.model` | Hardware model code | `SM-G973U1` (S10), `SM-S908U` (S22 Ultra) |
| `installation_uuid` | Per-install hex UUID; **stable across upgrades**, changes on uninstall+reinstall | `4D089845...` |
| `custom_keys` (ARRAY<STRUCT<key,value>>) | App-set keys — `buildTime`, `versionCode`, `lastTokenExpiry`, `authAnonymous`, `isSyncConfigured`, `isSyncAdmin`, `syncGroupId`, ... | — |

**Quick distinct-fingerprint roll-up:**
```sql
SELECT DISTINCT
  application.display_version AS app_version,
  device.model AS device_model,
  installation_uuid,
  (SELECT value FROM UNNEST(custom_keys) ck WHERE ck.key = 'buildTime') AS build_time,
  COUNT(*) AS events,
  MAX(event_timestamp) AS last_event
FROM `sync-23ce9.firebase_crashlytics.com_techadvantage_budgetrak_ANDROID_REALTIME`
WHERE event_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
GROUP BY 1,2,3,4
ORDER BY last_event DESC;
```

(Or just `--list-devices` — same query baked in.)

## "Latest build only" filter (post-publish hygiene)

`BudgeTrakApplication.onCreate` stamps a `buildTime` custom key on every future crash/non-fatal, sourced from `BuildConfig.BUILD_TIME` (set in `app/build.gradle.kts:79` to `yyyy-MM-dd HH:mm UTC` of the build). After publish, many devices will be on older APKs — filter to the build you care about with:

```sql
WHERE EXISTS (
  SELECT 1 FROM UNNEST(custom_keys) ck
  WHERE ck.key = 'buildTime' AND STARTS_WITH(ck.value, '2026-05-01')
)
```

CLI shortcut: `--build 2026-05-01` (or any prefix — `2026-05-01 13` for a specific hour).

`versionCode` is also pushed as a custom key for monotonic-version filtering when bumped per release.

## Getting a raw OAuth access token (for ad-hoc curl)

```bash
TOKEN=$(gcloud auth application-default print-access-token \
        --impersonate-service-account=$(jq -r .client_email ~/.config/budgetrak/sa-key.json) 2>/dev/null) \
  || TOKEN=$(node -e "
const {GoogleAuth} = require('google-auth-library');
new GoogleAuth({keyFilename:require('os').homedir()+'/.config/budgetrak/sa-key.json',
                scopes:['https://www.googleapis.com/auth/cloud-platform']})
  .getAccessToken().then(t=>console.log(t));")
```

## Regular checks

**Overnight:** `--nonfatals` for PERMISSION_DENIED clusters; cross-reference `token_log.txt` for refresh gaps.

**After releases:** `--crashes --build <today>` for new-build-only crash signatures. Compare against `--list-devices` to confirm coverage.

**Weekly:** `--days 7` overview; watch for single-device repeated crashes (= unrecovered bug on one user's APK), same-collection PERMISSION_DENIED, or concentration by `syncGroupId`.

## Other diagnostic files
- `support/token_log.txt` — `BudgeTrakApplication.tokenLog()`, debug only, capped 100 KB.
- `support/sync_diag.txt` — `DiagDumpBuilder` snapshot. See `spec_diagnostics.md`.

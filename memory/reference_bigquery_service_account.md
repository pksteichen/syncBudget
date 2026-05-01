---
name: BigQuery service account for query tool
description: tools/query-crashlytics.js reads BigQuery via a service account key at ~/.config/budgetrak/sa-key.json — avoids Firebase CLI RAPT re-auth cycle
type: reference
---

`tools/query-crashlytics.js` queries BigQuery (Crashlytics + Firebase Analytics + Performance Monitoring exports) for the `sync-23ce9` Firebase project.

## Auth setup

A service account key is the primary auth mechanism. The script's `resolveCredentials()` checks paths in priority order:

1. `GOOGLE_APPLICATION_CREDENTIALS` env var (cloud-standard override)
2. `~/.config/budgetrak/sa-key.json` (default location for this project)
3. `~/.config/configstore/firebase-tools.json` (legacy refresh-token fallback, RAPT-limited)

## Service account details

- **GCP Console:** Cloud Console → IAM & Admin → Service Accounts (project `sync-23ce9`)
- **Suggested name:** `bigquery-reader` (or similar; not load-bearing)
- **IAM roles** (both at project scope on `sync-23ce9`):
  - `roles/bigquery.jobUser` — permission to run query jobs
  - `roles/bigquery.dataViewer` — permission to read data
- **Key location on this device:** `~/.config/budgetrak/sa-key.json`
- **Key is gitignored** by `.gitignore`'s blanket exclusion of dotfiles outside the repo

## Why service account instead of firebase-tools refresh token

The `support@techadvantageapps.com` Workspace account is subject to a RAPT (Reauth Proof Token) policy on sensitive operations like BigQuery. The token expires every ~3-7 days (configurable in admin.google.com → Security → Reauthentication policy) and refreshing requires interactive `firebase login --reauth` from a desktop browser — which doesn't work from Termux or non-TTY shells.

Service accounts bypass RAPT entirely (RAPT is for human users) and don't have this issue.

## Rotation

Service account keys never expire on their own. Rotate manually on schedule:
1. Cloud Console → IAM & Admin → Service Accounts → bigquery-reader → Keys → Add Key (creates new)
2. Replace `~/.config/budgetrak/sa-key.json` with the new key
3. Cloud Console → delete the old key

Recommend annual rotation. The blast radius of a leaked key here is low — read-only on a private analytics project — but discipline is cheap.

## What the tool queries

- Crashlytics: `firebase_crashlytics.com_securesync_app_*` (legacy, data through 2026-04-12) UNION `firebase_crashlytics.com_techadvantage_budgetrak_*` (data from 2026-04-26). Script unions both for cross-rebrand history.
- Analytics: `analytics_534603748.events_*` and `events_intraday_*`. Two known event names: `ocr_feedback`, `health_beacon`.
- Performance Monitoring + Sessions: `firebase_performance.*`, `firebase_sessions.*` (not yet wired into the script — add via `--query` for ad-hoc reads).

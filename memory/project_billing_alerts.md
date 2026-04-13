---
name: Billing + runaway-bug alerts (setup pending)
description: Planned Firebase / Google Cloud alerts so a loop bug or unexpected overage pages the user. Designed 2026-04-12; not yet configured.
type: project
---

## Status
Designed 2026-04-12, **not yet configured**. Daily cron reminder until done (7-day auto-expiry on recurring jobs — re-up if needed).

## Why
At ~500 devices we're comfortably in Firebase free tier. A bug that accidentally loops FCM sends, echoes transactions, or hammers Firestore could blow that out in hours. Budget + monitoring alerts are the tripwire so we hear about it before the bill does.

## 1. Budget alert (tripwire on billing)
Google Cloud Console → Billing → Budgets & alerts → Create budget (project `sync-23ce9`).
- Budget: **$1/month** (intentionally low; fires on any cost at all).
- Thresholds: 50 %, 90 %, 100 % of budget.
- Recipients: billing-admin account by default; add explicit email recipients as needed.

Fires after the fact once billing pipeline updates — useful as a floor, not a real-time signal.

## 2. Cloud Monitoring alerts (real-time bug detection)
Google Cloud Console → Monitoring → Alerting → Create policy. The pre-billing tripwires.

| Metric | Normal rate @ 500 devices | Threshold | Alert window |
|---|---|---|---|
| `cloudfunctions.googleapis.com/function/execution_count` for `onSyncDataWrite` | ~6/min peak | **> 100/min** | 5 min |
| `cloudfunctions.googleapis.com/function/execution_count` for `presenceHeartbeat` | 0.067/min (4/hr) | **> 2/min** | 5 min |
| `firestore.googleapis.com/document/read_count` | ~30/min | **> 1000/min** | 5 min |
| `firestore.googleapis.com/document/write_count` | varies | **> 500/min** | 5 min |

Re-tune thresholds after a week of live heartbeat data (same gating applies to the #18 blocking-detection threshold in `project_prelaunch_todo.md`).

Auto-close: 30 min after recovery.

## 3. Notification channel
Monitoring → Alerting → Notification channels → Add new. Pick one:

- **Native SMS** (recommended) — Google Cloud Monitoring supports SMS as a channel. Add phone number, verify with a code. Google pays for SMS. US + most international numbers supported.
- **Email-to-SMS** — `<10-digit>@vtext.com` (Verizon), `@txt.att.net` (AT&T), `@tmomail.net` (T-Mobile). No verification; message truncated to ~160 chars.
- PagerDuty / Slack / Discord — overkill pre-launch.

## 4. Killswitch (not urgent, but pair with alerts)
If a 2 AM alert fires, want a way to halt the runaway without redeploying:
- Firestore doc `ops/killswitch` with `{ disabled: bool, reason: string }`.
- Both `onSyncDataWrite` and `presenceHeartbeat` read this doc at function entry and `return` early if `disabled === true`.
- Mobile-friendly way to flip the flag: bookmark a Firestore Console URL on the phone.

## Implementation order when ready
1. Set budget + notification channel (2 min).
2. Add Cloud Monitoring alert policies (~10 min for all four).
3. Test: flip a policy's threshold to 0 briefly to verify the SMS actually arrives.
4. (Optional) Add killswitch Firestore doc + function gating.

All steps happen in Google Cloud Console (not Firebase Console). Project dropdown → `sync-23ce9` → Monitoring in the left nav.

## Why this lives in memory
No code to track this — pure runtime config in GCP. Putting it here so whoever sits down to do it has the thresholds + rationale + ordering without re-deriving.

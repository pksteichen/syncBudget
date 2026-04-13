---
name: Billing + runaway-bug alerts (configured)
description: Firebase / Google Cloud alerts that page the user on loop bugs or unexpected overage. Set up 2026-04-13.
type: project
---

## Status
**Configured 2026-04-13.** Floor budget alert + 4 Cloud Monitoring runaway-rate alerts in place, notifying via SMS + email.

## Environment
- Billing account: `01ADA3-6ACE89-738567` (distinct from the Tech Advantage family-wide account `01907E-A308CF-E3D95D`, which has a pre-existing $15/mo org-wide budget).
- Project: `sync-23ce9` migrated into `techadvantagesupport-org` on 2026-04-13 (was previously under "No Organization" — predated the org). Post-migration, budget-alert Monitoring-channel dropdowns populate correctly.

## What's deployed

### Budget alert (billing-pipeline tripwire; email-only)
- **Budget**: $1/month, scope = single project `sync-23ce9`, all services.
- Thresholds: 50 %, 90 %, 100 %.
- Notifications: email to billing admin + users. (SMS channel wasn't wired in initially because the project was under "No Organization" and the channel picker was empty; since migrating the project into `techadvantagesupport-org`, the picker works. Optional follow-up: edit the budget to add the SMS channel — Monitoring policies already cover SMS for real-time alerting, so this is cosmetic.)
- Fires 6-24 h after the fact; useful as a floor / catch-all for any paid service including ones we don't have explicit Monitoring alerts on (RTDB bandwidth, BigQuery scans, outbound networking, etc.).

### Cloud Monitoring alert policies (real-time; SMS + email via channel)
Notification channel: SMS → `Paul Steichen Cell` (verified under Monitoring → Alerting → Notification channels). Each policy has Auto-close = 30 min.

| Policy name | Metric | Threshold | Retest |
|---|---|---|---|
| `onSyncDataWrite runaway (>100/min for 5 min)` | `cloudfunctions.googleapis.com/function/execution_count` filtered `function_name = onSyncDataWrite`, rate / min | > 100 | 5 min |
| `presenceHeartbeat runaway (>2/min for 5 min)` | same metric, filtered `function_name = presenceHeartbeat` | > 2 | 5 min |
| `Firestore reads runaway (>1000/min for 5 min)` | `firestore.googleapis.com/document/read_count`, rate / min | > 1000 | 5 min |
| `Firestore writes runaway (>500/min for 5 min)` | `firestore.googleapis.com/document/write_count`, rate / min | > 500 | 5 min |

**RTDB + Cloud Storage bandwidth alerts were tried and removed 2026-04-13.** Lessons logged below under "Deleted alerts"; relevant if we ever want to re-add them at scale.

### Deleted alerts (bandwidth)
Initially set up two more policies — RTDB sent-bytes and Cloud Storage sent-bytes — and both were deleted the same day. Lessons:
1. **Wrong metric picked.** The picker offers `*_sent_bytes_limit` (monthly quota ceiling — huge static number) alongside `*_sent_bytes_count` (actual traffic). We accidentally picked the limit version, which is always above any sensible threshold, so the alert fired constantly.
2. **Even with the right metric, the threshold is hard to set at 2-device scale** — Firebase SDK protocol overhead (session handshakes, reconnects, initial presence-node snapshots on every process start) crosses ~1 MB/min intermittently. Any threshold calibrated to today's "normal" is nonsense at 500 devices.
3. **The 4 function-level + Firestore alerts catch every runaway loop we can realistically have** — a loop manifests as a function-invocation spike first, long before bandwidth spikes. Bandwidth alerts would have been overlap protection, not primary defense.
4. **The $1 budget tripwire backstops bandwidth overruns anyway** (it's a catch-all on billed cost).
5. When/if we re-add bandwidth alerts at scale (say 5K+ devices), use metric `firebasedatabase.googleapis.com/network/sent_bytes_count` (plain "Sent bytes", not "Sent bytes limit") and `storage.googleapis.com/network/sent_bytes_count`. Calibrate threshold against a week of real traffic data, not an estimate.

### Layered coverage
| Layer | Speed | Catches |
|---|---|---|
| $15 family-wide budget (pre-existing, different billing account) | Slow | Catastrophic spike across any project |
| **$1 sync-23ce9 budget** | Slow (6-24 h) | Floor: any paid service including ones without a Monitoring alert |
| **4 Monitoring alerts** | ~1-2 min | Runaway function loops + runaway Firestore reads/writes |

### Thresholds — re-tune after live data
Current values are educated guesses calibrated against our 2026-04-13 overnight dump (500-device estimates). After a week of real traffic, re-evaluate:
- Normal `onSyncDataWrite` peak was ~1 burst × 42/min during 5 AM period refresh — 100/min leaves headroom.
- Normal `presenceHeartbeat` is 4/hr = 0.067/min — 2/min is 30× normal.
- Firestore / RTDB / Storage figures are 30-100× projected normal at 500 devices.
- Tighten as scale grows so the alerts remain 10-30× normal rather than 1000×.

## Not yet configured (optional)

### Killswitch
If a 2 AM alert fires and the runaway isn't easy to stop via code deploy, we want a way to halt Cloud Functions immediately. Design:
- Firestore doc `ops/killswitch` with `{ disabled: bool, reason: string, setAt: timestamp }`.
- `onSyncDataWrite` and `presenceHeartbeat` read this doc at function entry and `return` early if `disabled === true`.
- Bookmark Firestore Console URL on the phone for one-tap flip.

Not yet implemented. Would take ~30 min next session if ever needed.

### Verify the SMS arrives
Best done by temporarily dropping one policy's threshold to 1 for a few minutes, confirming the text hits, then restoring. Worth doing in a calm moment before relying on these in a real incident.

## Billing account access gotcha (for future reference)
The billing account `01ADA3-6ACE89-738567` that funds `sync-23ce9` is accessible to `techadvantagesupport@gmail.com`. A separate billing account `01907E-A308CF-E3D95D` funds legacy/other projects under `pksteichen@gmail.com` and has its own $15 monthly budget. When Cloud Console throws a `billing.accounts.getSpendingInformationScoped` permission error on the Budgets page, it's usually because you landed on the wrong billing account — use `https://console.cloud.google.com/billing/linkedaccount?project=sync-23ce9` to find the right one.

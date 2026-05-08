---
name: Join-snapshot Cloud Storage TTL — deferred
description: At ~40K groups we should add a Storage-side TTL/lifecycle rule on `joinSnapshot.enc`. Today snapshots are reused for 7 days but never deleted; orphaned ones from inactive groups will accumulate.
type: project
---

## Status
**Deferred.** Not a launch blocker. Revisit when active group count approaches ~40K, or when Cloud Storage line on the Firebase bill becomes noticeable.

## Today's behavior
- `joinSnapshot.enc` lives at `groups/{gid}/joinSnapshot.enc` in Cloud Storage.
- Reused for **7 days** (gate in `MainActivity.kt:2696` checks `FirestoreService.getJoinSnapshotAge(gId)` on each "Generate pairing code" tap; only re-uploads when older than 7 d).
- Age tracked via `joinSnapshotAt` field on the group doc.
- The pairing-code Firestore doc has its own 10-min TTL (Firestore TTL policy on `pairing_codes/expiresAt`); that part is fine.
- The encrypted blob in Storage **is never deleted** — just overwritten on the next refresh after 7 d.

## Why this matters at scale
- A snapshot is roughly the size of one user's full data set (transactions + receipts metadata + categories + RE/IS/AE/SG/ledger + settings, encrypted). Order of ~100 KB to a few MB depending on history depth.
- Active groups overwrite their snapshot regularly, so per-group footprint stays bounded.
- **Inactive / abandoned groups** keep their last snapshot in Storage forever. At ~40K groups with typical churn, abandoned snapshots could become a meaningful share of paid Storage egress + at-rest cost.
- Cleanup via Cloud Function on group dissolution exists, but groups that simply go dormant (devices reinstalled, family stopped using the app) never trigger dissolution.

## How to apply (when revisited)
- **Option A — Storage lifecycle rule**: GCS lifecycle policy "delete object older than N days" on `groups/*/joinSnapshot.enc`. Pros: zero code, works for orphans. Cons: deletes for active groups too, forcing re-upload on the next pairing-code generation (acceptable — 7-d reuse window already accepts staleness).
- **Option B — Firestore TTL on a sibling field + Cloud Function trigger**: write a `joinSnapshotExpiresAt` on the group doc, Firestore TTL deletes the field, Cloud Function listens and deletes the matching Storage object. More moving parts.
- **Option C — Inline cleanup**: existing 7-d staleness check uploads a fresh blob; same operation could delete the old one first. Doesn't address abandoned groups.

Lean toward A — simplest, cheapest, no rule-rule interactions. Check storage cost dashboards once we cross 10K active groups to know when to act.

## Triggering signal
Cloud Storage line item on the Firebase invoice trends > $5/mo, **or** active group count hits ~30K (early warning, before hitting 40K).

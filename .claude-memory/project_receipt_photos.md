---
name: Receipt photo feature design
description: Architecture for receipt photo capture (up to 5/transaction), Cloud Storage sync with upload-first/ledger-second flow, delta hold-back, hash-based re-upload assignment, flag clock optimization, local pruning, 14-day cloud pruning
type: project
---

Receipt photo feature — designed but not yet implemented. Subscriber-tier.

**Why:** Users can photograph receipts or purchases, link to transactions, and sync photos across devices without bloating delta payloads.

**How to apply:** When implementing, follow the design in `docs/RECEIPT_PHOTO_DESIGN.md` in the project root. Check existing Firebase security rules during implementation.

**Core design decisions:**
- Up to 5 photos per transaction (`receiptId1`–`receiptId5` with independent CRDT clocks)
- Max 1000px longest dimension, JPEG 70% quality (~50-150KB)
- Encrypted locally before upload (same key as deltas)
- **Upload-first, ledger-second**: originator uploads to Cloud Storage, then creates ledger entry. No hash assignment for initial uploads.
- **Delta hold-back**: `receiptIdN` fields excluded from CRDT deltas until upload confirmed. Pending upload queue persisted to disk for crash safety.
- **Flag clock** on `imageLedgerMeta`: devices read one small doc per sync, only pull full ledger when flag clock changes. Bumped on: recovery requests, re-upload complete, cleanup, request replacement. NOT bumped on initial entry creation.
- **Recovery flow**: device checks cloud first, creates ledger request only if cloud copy also missing. Waits on flag clock (no polling).
- **Hash-based re-upload assignment** (recovery only): two-tier (online filter, then `hash(receiptId+deviceId) % 1000`). CAS transaction for takeover. 5-min failover.
- **Download retry**: 3 real failures (network errors don't count) → replace ledger entry with request
- **14-day cloud pruning**: noon trigger, hash-based cleanup assignment among online devices
- **Local storage pruning**: admin-only `receiptPruneAgeDays` shared setting, effective prune date = max(today-age, mostRecentPrunedDate), re-upload suppressed for old transactions
- **Deletion**: long-press thumbnail → confirm → clear slot + remove ledger + delete cloud. Merge-time detection (old non-null → new null) triggers local cleanup on other devices. Orphan scan as safety net.
- **Concurrency**: dot-notation for possession marking, transactions for prune check and CAS assignment takeover
- Solo devices (no family group) skip entire ledger/cloud system
- Group device enumeration via `getDevices()` (excludes removed devices)

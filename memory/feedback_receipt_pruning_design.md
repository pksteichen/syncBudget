---
name: Receipt pruning design decisions
description: Cloud pruning (14 days) and local pruning (configurable) are separate systems. Don't change the 14-day cloud cleanup or add possession checks to it.
type: feedback
---

Two separate pruning systems for receipts:

1. **Cloud pruning (14 days, hardcoded)**: `processStalePruning` deletes cloud files + ledger entries older than 14 days regardless of possession. If a device was offline and missed a receipt, it should create a new recovery request in the photo ledger after the old entry is pruned. This is by design — cloud storage is temporary.

2. **Local pruning (configurable via `receiptPruneAgeDays`)**: Controlled in Settings. Applies to LOCAL receipt files on the device only. Completely separate from cloud pruning.

**Why:** `receiptPruneAgeDays` in SharedSettings is for local cleanup, NOT for the cloud 14-day timer. Don't wire them together.

**How to apply:** Don't add possession checks to `processStalePruning`. Don't read `receiptPruneAgeDays` for cloud pruning. If an audit flags these as bugs, they are design decisions.

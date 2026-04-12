---
name: Receipt photo feature (implemented)
description: High-level status + design decisions. Full implementation spec is `spec_receipt_photos.md`.
type: project
---

The receipt photo system is implemented and live. See `spec_receipt_photos.md` for the operational reference (capture, compression, flag-clock polling, pruning, snapshot archives, rotation, deletion chain).

## Status
- Up to 5 photos per transaction (`receiptId1..5`).
- Local capture + storage for all users; cloud sync gated to paid users (`photoCapable` flag in RTDB presence).
- Integrated into TransactionDialog, TransactionsScreen list, full-screen viewer, PDF expense reports, and full backup/restore.

## Why this design
- **Per-field `receiptIdN` slots** (not a list) let `EncryptedDocSerializer.diffFields` produce minimal Firestore updates when a single photo is added or removed — the per-field encryption layer naturally handles it.
- **Flag-clock polling**, not a dedicated listener: most groups already have the 8 business-collection listeners open; adding a 9th just for image ledger was unnecessary overhead. Read one tiny `imageLedgerMeta` doc per sync and only pull the full ledger when the flag actually changed.
- **Upload-first, ledger-second**: the cloud blob is the source of truth. Creating the ledger entry only after the upload succeeds means a crash between steps is recoverable: the originator still has the local file and the pending-upload queue will retry.
- **Snapshot archive for ≥ 50 photos**: one encrypted archive + manifest beats 50 individual cloud GETs + 50 ledger round trips. Used for both batch recovery and join-snapshot.
- **Cloud pruning at 14 days, hardcoded, not gated on possession**: cloud storage is treated as ephemeral. Devices that missed a photo create a recovery request when they notice it's gone; the originator may still have the local file to re-upload. Keeps cloud cost bounded regardless of group behavior.
- **Local pruning via `receiptPruneAgeDays` is independent** of cloud pruning — see `feedback_receipt_pruning_design.md`.

## Historical notes
- The original CRDT design used delta hold-back: `receiptIdN` fields were excluded from deltas until the upload confirmed. After the Firestore-native migration this is no longer needed — `pushRecord` naturally skips a field if the local state still shows the pending upload.
- Snapshot archive design (2026-03-24 era) predates join-snapshot and was repurposed when join catch-up grew to the same scale.

---
name: No flag-clock bump on imageLedger entry creation
description: createLedgerEntry intentionally does NOT bump imageLedgerFlagClock — peers discover via transaction sync and prune inline; don't re-add the bump
type: feedback
originSessionId: ea9e173a-ca3d-4f87-b67a-ceac73953250
---
`ImageLedgerService.createLedgerEntry` intentionally omits a `bumpFlagClock(groupId)` call after the `.set()`. If you read the method expecting a bump and think it's missing, don't "fix" it — it's deliberate.

**Why:** peers don't need the flag-clock bump to discover a newly-uploaded photo. They discover it through the transaction-sync path: the originator's `pushTransaction` delivers the `receiptIdN` field to Firestore, Firestore pushes it to peers via the transactions-collection listener, `MainViewModel.onBatchChanged` scans arriving transactions for new `receiptIdN` values, and downloads the blob from Cloud Storage directly. The flag clock would only cause peers to re-pull the full ledger on top of that — redundant work. Possession-based pruning still works because `pruneCheckTransaction` fires inline at every download site (three of them, all consistent).

**How to apply:**
- When editing `ImageLedgerService.createLedgerEntry`, do not add `bumpFlagClock`.
- When adding a new download code path, call BOTH `markPossession` AND `pruneCheckTransaction` inline after `decryptAndSave` succeeds. The three existing sites (`MainViewModel.onBatchChanged`, `ReceiptSyncManager.handleDownload`, `ReceiptSyncManager.processRecovery`) are the pattern to match.
- Other flag-clock bumpers stay as-is: `createRecoveryRequest`, `markReuploadComplete`, `createSnapshotRequest`, stale-prune deletions, `deleteReceiptFull`. These correspond to ledger-state changes that peers can't learn about through transaction sync.
- Memory `spec_receipt_photos.md` and SSD §17.5 + LLD §7.19 have the full rationale.

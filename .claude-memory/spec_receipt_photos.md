---
name: Receipt Photo Specification
description: Photo capture, compression, local storage, cloud sync, pruning, snapshot archives, free/paid gating
type: reference
---

# Receipt Photo Specification

## Capture
- Camera intent (`TakePicture`) or gallery picker (`PickMultipleVisualMedia`)
- Up to 5 photos per transaction (receiptId1-5 slots)
- SwipeablePhotoRow UI: swipe left to reveal photo panel

## Compression
- Max dimension: 1000px (resize proportionally)
- Target: 250KB per megapixel
- Iterative JPEG quality search: start Q=92, binary search to hit ±10% of target
- Quality range 20-100, compresses from original (never re-compresses)

## Local Storage
- Full images: `filesDir/receipts/{receiptId}.jpg`
- Thumbnails: `filesDir/receipt_thumbs/{receiptId}.jpg` (200px, Q=70)
- Orphan cleanup: deletes files not referenced by any transaction

## Cloud Sync (paid users only)
1. Encrypt with group sync key → upload to Cloud Storage `groups/{groupId}/receipts/{receiptId}.enc`
2. Create Firestore ledger entry with possessions map, uploadedAt timestamp
3. Other devices download when imageLedgerFlagClock bumps
4. Pending upload queue persisted to JSON (survives app kill)
5. Upload speed measured and reported for assignment optimization

## Recovery
- Missing receipts: batch recovery (50/cycle), or snapshot archive for 50+
- Snapshot: encrypt each file individually, assemble with manifest, upload as single archive
- Re-upload assignment: speed-based selection (fastest device from last 24h)

## 14-Day Pruning
- Daily cleanup after 12pm, one device elected per day
- Delete cloud file + ledger entry for receipts older than 14 days
- Bump flagClock to notify other devices

## Free vs Paid
- Free: capture/store locally, no cloud sync
- Paid: full cloud sync, download from other devices
- `photoCapable` flag in device metadata

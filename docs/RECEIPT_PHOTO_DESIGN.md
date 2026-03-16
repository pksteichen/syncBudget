# BudgeTrak Receipt Photo Feature — Design Document
**Date:** 2026-03-16 | **Status:** Designed, not yet implemented | **Tier:** Subscriber

---

## Overview

Users can photograph receipts or purchases, optionally link them to transactions, and sync photos across family devices. Photos travel alongside delta sync but NOT inside delta payloads, using Firebase Cloud Storage instead of Firestore.

---

## On Capture

1. User takes photo or picks from gallery
2. App generates unique `receiptId` (UUID)
3. Downsizes to max 1000px on longest dimension (~50-150KB as JPEG at 70% quality)
4. Encrypts locally (same encryption key as sync deltas)
5. Stores in app-internal storage: `receipts/{receiptId}.jpg`
6. Links to transaction via `receiptId` field on Transaction data class

---

## Data Model

### Transaction Addition
```kotlin
val receiptId: String? = null,
val receiptId_clock: Long = 0L,
```

### Receipt Metadata (local)
```kotlin
data class ReceiptMetadata(
    val receiptId: String,
    val transactionId: Int?,      // null if not yet linked
    val localPath: String,
    val uploadedToCloud: Boolean,
    val capturedAt: Long
)
```

### Image Ledger (Firestore)
```
Path: groups/{groupId}/imageLedger/{receiptId}
```
```kotlin
data class ImageLedgerEntry(
    val receiptId: String,
    val originatorDeviceId: String,
    val createdAt: Long,
    val possessions: Map<String, Boolean>,  // deviceId -> has file
    val uploadAssignee: String? = null,     // device responsible for upload
    val assignedAt: Long = 0L,             // when assignment was made
    val uploadedAt: Long = 0L              // 0 = not yet in cloud
)
```

### Cloud Storage
```
Path: groups/{groupId}/receipts/{receiptId}.enc
```

---

## Upload Assignment — P2P-Inspired Design

### Hash-Based Load Distribution

Inspired by BitTorrent's piece selection strategy. Instead of always picking the lowest deviceId (which overloads one device), use a scoring system:

```
score = recentlySeen (0-10) + hashDistribution (0-5)
```

- **`recentlySeen`**: device with most recent `lastSeen` in Firestore scores highest (actually online)
- **`hashDistribution`**: `hash(receiptId + deviceId) % 5` — naturally spreads uploads across devices per-image

For image A, device 2 might score highest. For image B, device 4 might score highest. Work distributes without coordination — every device computes the same result independently.

### Upload Priority Rules

1. **Upload my own assigned files first** — clear your own queue before helping
2. **Wait 5 minutes after my last upload** before volunteering for others' assignments
3. **Pick up unfinished assignments** only if `assignedAt` was 5+ minutes ago and `uploadedAt` is still 0

This prevents fast devices from hogging all uploads and gives slow devices fair time to complete.

### Duplicate Upload Safety

If two devices upload the same file simultaneously: **no problem**. Cloud Storage overwrites at the same path. Both uploads produce identical encrypted content (same source, same key). Wasteful bandwidth but zero corruption risk. With 2-5 family devices, worst case is 2-3 redundant 150KB uploads — trivial.

### Timeline Example (20 photos, 3 devices)

```
Hash distribution: A gets 7, B gets 8, C gets 5

Minute 0-1:  All three devices start uploading their own assigned files

Minute 2-5:  Each device works through its own queue
             A finishes 7, B finishes 6 of 8, C finishes 5

Minute 5:    C is done with its own queue
             → 5 min since last upload? No (just finished)
             → Waits

Minute 10:   C checks again: 5 min since last upload? Yes
             → Scans ledger for uploadedAt=0 where assignedAt > 5 min ago
             → Finds B's 2 remaining files (B went offline mid-queue)
             → Picks them up, uploads

Minute 12:   All 20 files uploaded
```

---

## Sync Flow

### Normal Flow (New Photo)

1. **Device A captures** → encrypts, stores locally, creates ledger entry:
   ```
   possessions: {A: true}, uploadAssignee: A, assignedAt: <now>, uploadedAt: 0
   ```
2. **Device A uploads** encrypted file to Cloud Storage, updates ledger:
   ```
   uploadedAt: <timestamp>
   ```
3. **Device B syncs** → sees `uploadedAt > 0`, downloads from Cloud Storage, decrypts, stores locally:
   ```
   possessions: {A: true, B: true}
   ```
4. **Last device to download** checks possessions — all group devices covered → deletes Cloud Storage file, removes ledger entry
5. All devices keep local copies indefinitely

### Recovery Flow (Device Offline 3+ Weeks)

1. **Device C** comes online, sees transaction with `receiptId` but no ledger entry and no local file
2. **Device C** creates new ledger entry:
   ```
   possessions: {}, uploadedAt: 0
   ```
3. **Other devices** see entry with no possessions, check if they have file locally
4. **Hash-based scoring** determines which device uploads
5. Devices that have file but aren't assigned simply mark possession (no upload/download)
6. Assigned device uploads, normal download/prune cycle resumes

---

## Device Behavior Matrix

| Ledger State | Has File? | Highest Score? | Action |
|---|---|---|---|
| New: uploadedAt > 0 | No | — | Download, mark possession |
| New: uploadedAt > 0 | Yes | — | Just mark possession |
| Re-upload: uploadedAt = 0 | Yes | Yes | Set assignee, upload, mark possession |
| Re-upload: uploadedAt = 0 | Yes | No | Just mark possession |
| Re-upload: uploadedAt = 0 | No | — | Wait, show placeholder |
| Stale assignment (5+ min) | Yes | — | Take over upload |
| No ledger entry | No | — | Show camera placeholder |
| Own queue not empty | Yes (other's file) | — | Finish own queue first |
| < 5 min since own last upload | Yes (other's file) | — | Wait before volunteering |

---

## Ledger Communication Stages

| Ledger State | Meaning |
|---|---|
| `possessions={}` | "I need this file, does anyone have it?" |
| `uploadAssignee=A, assignedAt=<time>` | "Device A is planning to upload" |
| `possessions={A: true}` | "I have it locally, no download needed" |
| `uploadedAt > 0` | "File is in Cloud Storage, download it" |
| All devices in possessions | "Everyone has it, safe to prune" |

The ledger does triple duty: **coordination** (who uploads), **tracking** (who has it), and **garbage collection** (can we prune).

---

## Pruning

### Happy Path (All Devices Have File)
Last device to download checks possessions — if all group devices are covered, deletes Cloud Storage file and removes ledger entry.

### Stale Pruning (14-Day Cutoff)
Any device can prune ledger entries + Cloud Storage files where `createdAt` is older than 14 days, regardless of possession status. Covers offline/lost devices.

### Re-Upload After Prune
If a device comes online after pruning and is missing photos referenced by transactions, it creates new ledger entries. Devices with the files respond via hash-based upload assignment.

---

## UI

### Capture Points
- Camera button on add-transaction dialog
- "Attach receipt" button on transaction detail/edit
- Standalone capture (unlinked — link later)

### Display
- Thumbnail on transaction list items (if receipt attached)
- Full-size view on tap
- Gallery of unlinked receipts for later matching

### Missing Photo Placeholder
- Camera icon thumbnail shown when:
  - Transaction has `receiptId` but local file doesn't exist
  - Ledger entry exists but file hasn't been downloaded yet
- Silently resolves once sync delivers the file
- No error dialogs or confusing states

---

## Cost Analysis

### Per Image
- Local storage: ~150KB (JPEG)
- Cloud Storage: ~200KB (encrypted)
- Firestore ledger: ~200 bytes
- Transit: brief (deleted after all devices download)

### At Scale (1,000 users × 30 receipts/month)
- Cloud Storage: transient (~4.5GB peak, pruned to near-zero)
- Firestore: ~30K ledger writes/month
- Well within free tier initially
- ~$0.10/month on Blaze plan

### Firebase Free Tier
- Cloud Storage: 5GB storage, 1GB/day download
- 5GB = ~30,000 receipts at 150KB each

---

## Implementation Checklist

- [ ] `receiptId` + `receiptId_clock` fields on Transaction
- [ ] ReceiptManager — capture, downsize, encrypt, store locally
- [ ] Thumbnail generator for list display
- [ ] Camera placeholder drawable for missing images
- [ ] Firebase Cloud Storage dependency + initialization
- [ ] ImageLedger Firestore CRUD operations
- [ ] Hash-based upload scoring system
- [ ] Upload logic with 5-minute failover
- [ ] Own-queue-first priority with 5-minute cooldown before volunteering
- [ ] Download logic with possession marking
- [ ] Pruning (happy path + 14-day stale)
- [ ] Re-upload/recovery flow
- [ ] UI: camera button on transaction dialogs
- [ ] UI: thumbnail on transaction list items
- [ ] UI: full-size photo viewer
- [ ] UI: unlinked receipt gallery
- [ ] Gate behind Subscriber tier

---

*Designed 2026-03-16. Ready for implementation.*

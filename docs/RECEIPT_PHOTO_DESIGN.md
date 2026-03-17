# BudgeTrak Receipt Photo Feature — Design Document
**Date:** 2026-03-16 | **Status:** Designed, not yet implemented | **Tier:** Subscriber

---

## Overview

Users can photograph receipts or purchases, optionally link them to transactions, and sync photos across family devices. Photos travel alongside delta sync but NOT inside delta payloads, using Firebase Cloud Storage instead of Firestore.

---

## On Capture

1. User takes photo or picks from gallery (up to 5 per transaction)
2. App generates unique `receiptId` (UUID)
3. Downsizes to max 1000px on longest dimension (~50-150KB as JPEG at 70% quality)
4. Encrypts locally (same encryption key as sync deltas)
5. Stores in app-internal storage: `receipts/{receiptId}.jpg` + generates thumbnail in `receipt_thumbs/`
6. Links to transaction locally by writing `receiptId` to the next empty slot (`receiptId1`–`receiptId5`). Photo is visible in the local UI immediately.
7. Adds the `receiptId` to the **pending upload queue**
8. **Upload processing** (on current or next connected sync):
   - Upload encrypted file to Cloud Storage
   - On success → create ledger entry, remove from pending queue
   - On failure → remains in queue, retried on next sync
9. **Delta hold-back**: the delta builder skips any `receiptIdN` field whose `receiptId` is still in the pending upload queue. Other devices only learn about the photo after the cloud file and ledger entry are confirmed in place. This guarantees that by the time another device sees a `receiptIdN` via CRDT sync, the file is downloadable from cloud.

### Crash Safety

The pending upload queue is **persisted to disk** (SharedPreferences or a JSON file in app-internal storage). This ensures that if the app is killed (dead battery, OS kill, crash), on restart:

1. The persisted queue is loaded — the app knows which receiptIds still need uploading
2. Upload processing resumes for any pending entries
3. Delta hold-back remains in effect for those fields until their uploads complete

Without persistence, a kill would cause the delta builder to include un-uploaded `receiptIdN` fields in the next sync, sending other devices to download files that don't exist in cloud yet. The persisted queue prevents this.

---

## Data Model

### Transaction Addition (5 photo slots)
```kotlin
val receiptId1: String? = null, val receiptId1_clock: Long = 0L,
val receiptId2: String? = null, val receiptId2_clock: Long = 0L,
val receiptId3: String? = null, val receiptId3_clock: Long = 0L,
val receiptId4: String? = null, val receiptId4_clock: Long = 0L,
val receiptId5: String? = null, val receiptId5_clock: Long = 0L,
```

Each slot is independently clockable for CRDT merge. The UI's 5 `SwipeablePhotoRow` windows map 1:1 to these fields. If two devices add photos to different slots simultaneously, both survive. Same-slot conflict: LWW picks one, the other becomes orphaned (cleaned up by orphan scan).

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
    val uploadAssignee: String? = null,     // device responsible for re-upload
    val assignedAt: Long = 0L,             // when re-upload assignment was made
    val uploadedAt: Long = 0L              // 0 = not yet in cloud
)
```

### Ledger Flag Clock & Cleanup Fields (on Group Document)
```
Path: groups/{groupId}  (existing document, already read every sync for dissolution check)
```
```kotlin
// Added to the existing group document — zero additional Firestore reads
val imageLedgerFlagClock: Long = 0L,       // incremented on ledger changes that need attention
val imageCleanupAssignee: String? = null,  // device responsible for today's cleanup
val imageCleanupAssignedAt: Long = 0L,     // when cleanup was claimed
val imageLastCleanupDate: String? = null   // "YYYY-MM-DD" — prevents re-running same day
```

Piggybacked on the group document that every device already reads every sync cycle (for the dissolution check). Firestore returns the full document on any read, so these fields come along for free. Devices compare `imageLedgerFlagClock` to their locally stored `lastSeenFlagClock` to decide whether to pull the full ledger. See **Ledger Flag Clock** section for details.

### Cloud Storage
```
Path: groups/{groupId}/receipts/{receiptId}.enc
```

---

## Upload Assignment — P2P-Inspired Design

### Hash-Based Load Distribution

Inspired by BitTorrent's piece selection strategy. Two-tier system: online devices always take priority, then a deterministic hash evenly divides work among them.

#### Step 1 — Filter to online devices

A device is **online** if its `lastSeen` in Firestore device metadata is within the last 10 minutes. Only online devices that have the file locally (in `possessions`) are candidates for upload assignment.

If no online device has the file, the assignment waits (placeholder shown on requesting device).

#### Step 2 — Hash picks one candidate

Among online candidates, each device computes a deterministic score:

```
score = hash(receiptId + deviceId) % 1000
```

Highest score wins. Every device computes the same result independently — no coordination needed. For image A, device 2 might win. For image B, device 3 might win. Work distributes evenly across online devices by the law of large numbers.

#### Why two tiers?

A single combined score (e.g., `onlineWeight + hash`) risks an always-on device dominating if the online component leaks into the ranking. Filtering first guarantees that only reachable devices are considered, and the hash alone decides among them — perfectly even distribution.

### Upload Priority Rules

1. **Upload my own assigned files first** — clear your own queue before helping
2. **Wait 5 minutes after my last upload** before volunteering for others' assignments
3. **Pick up unfinished assignments** only if `assignedAt` was 5+ minutes ago and `uploadedAt` is still 0

This prevents fast devices from hogging all uploads and gives slow devices fair time to complete.

### Duplicate Upload Safety

If two devices upload the same file simultaneously: **no problem**. Cloud Storage overwrites at the same path. Both uploads produce identical encrypted content (same source, same key). Wasteful bandwidth but zero corruption risk. With 2-5 family devices, worst case is 2-3 redundant 150KB uploads — trivial.

### Note on Initial Uploads

Hash-based upload assignment does NOT apply to initial uploads. Each device uploads its own captured photos directly — no coordination needed. The hash distribution system only activates for **recovery re-uploads** when a device requests a file via the ledger and another device with the file must re-upload it.

---

## Sync Flow

### Normal Flow (New Photo)

1. **Device A captures** → encrypts, stores locally, generates thumbnail
2. **Device A uploads** encrypted file to Cloud Storage
3. **On successful upload**, Device A creates ledger entry (upload-first, ledger-second):
   ```
   Ledger entry: possessions: {A: true}, uploadedAt: <timestamp>, createdAt: <now>
   ```
   The ledger is never created until the cloud file is confirmed present. No `uploadAssignee`/`assignedAt` needed — the originator already uploaded. No flag clock bump needed — other devices learn about the photo via CRDT sync of the `receiptId` field and download directly from cloud.
4. **Device B syncs** → receives `receiptIdN` via CRDT delta (guaranteed to arrive after cloud file + ledger entry exist, thanks to delta hold-back). Downloads encrypted file from Cloud Storage, decrypts, stores locally. Marks possession on the ledger via dot-notation update (see Concurrency & Atomicity):
   ```kotlin
   ledgerRef.update("possessions.B", true)
   ```
5. **After marking possession**, device runs a prune-check transaction — if all current group devices are in `possessions`, atomically deletes ledger entry and triggers Cloud Storage file deletion
6. All devices keep local copies indefinitely (subject to local storage pruning if configured)

### Recovery Flow (Missing Photo)

When a device sees a transaction with `receiptId` but has no local file:

1. **Check cloud first** — try to download `groups/{groupId}/receipts/{receiptId}.enc` directly
2. **If found in cloud** → download, decrypt, store locally. Done. No ledger entry needed.
3. **If NOT in cloud** → check if a ledger entry already exists for this `receiptId`
   - If ledger entry exists with `uploadedAt > 0` → file may have just been re-uploaded. Try downloading from cloud again. If it fails, normal download failure handling applies (retry 3 times, then replace with request entry).
   - If ledger entry exists with `uploadedAt = 0` → another device already requested. Wait.
   - If no ledger entry → create one as a request, and bump the flag clock:
     ```
     Ledger entry: possessions: {}, uploadedAt: 0, uploadAssignee: null, assignedAt: 0
     group document: imageLedgerFlagClock++
     ```
4. **Requesting device waits** — does NOT keep polling the cloud. On subsequent syncs, it checks the flag clock. Only when `flagClock > lastSeenFlagClock` does it pull the ledger to check if `uploadedAt` has been set.
5. **Other devices** see the flag clock change on their next sync, pull the ledger, find the request entry, check if they have the file locally
6. **Hash-based scoring** among online devices with the file determines uploader (see Upload Assignment)
7. **Winner claims** assignment via CAS transaction, uploads, sets `uploadedAt`, bumps flag clock
8. **Requesting device** sees flag clock change, pulls ledger, sees `uploadedAt > 0`, downloads from cloud

### Download Failure Handling

When a device attempts to download a file from Cloud Storage and the download fails:

1. **Pull the full ledger** (regardless of flag clock) to get the current state of the entry
2. **If the ledger says `uploadedAt > 0`** (file should exist):
   - Track a `downloadRetryCount` locally for this `receiptId`
   - Network/Firestore connectivity errors do NOT count against retries (transient — will resolve)
   - Actual download failures (404, corrupt file, etc.) increment the retry count
   - Retry on the next 3 sync cycles
   - **After 3 real failures**: the file somehow disappeared or never fully saved. Delete the ledger entry and replace it with a new request entry (`possessions: {}, uploadedAt: 0`). Bump flag clock. Another device with the file will re-upload.
3. **If the ledger entry is gone** (pruned between the download attempt and the ledger pull):
   - Create a new request entry. Normal recovery resumes.

### Deletion Flow

1. User **long-presses** photo thumbnail in the transaction photo view → picker with "Delete" option
2. User **confirms** deletion
3. **On the deleting device:**
   - Set the matching slot (`receiptId1`–`receiptId5`) to `null`, bump its clock
   - Check photo ledger — if entry exists for this `receiptId`:
     - Remove the ledger entry
     - Delete the cloud file (`groups/{groupId}/receipts/{receiptId}.enc`)
   - Delete local file + thumbnail
4. **Normal CRDT sync** propagates the `receiptIdN = null` change to other devices via delta
5. **On receiving devices** — during CRDT merge, the repository compares old vs new for each `receiptId1`–`receiptId5` slot:
   - Before merge: `receiptId3 = "abc-123"` (non-null)
   - After merge: `receiptId3 = null` (incoming clock wins)
   - Detected change → delete local file `receipts/abc-123.jpg` + thumbnail
6. **Safety net**: periodic orphan scan checks local receipt files against all transaction `receiptId1`–`receiptId5` references, deletes unmatched files

### Scenario: Device Goes Offline Mid-Queue

```
Setup: A and B both online. A captured 10 photos.
       A uploaded all 10, created ledger entries.
       B starts downloading from cloud.
       B downloads 7 of 10, then goes offline.

State: Ledger entries for all 10 have uploadedAt > 0.
       7 entries: possessions = {A: true, B: true}
       3 entries: possessions = {A: true}

       B's 3 remaining files: B will download them when it comes back online.
       The ledger entries and cloud files persist until B (or any remaining
       device) downloads and the prune check passes.

       If B stays offline > 14 days: stale pruning removes ledger entries
       and cloud files. When B returns, recovery flow handles it.
```

### Scenario: No Device With File Is Online

```
Device C comes online after weeks, missing 5 photos.

Step 1: C checks cloud for each — all 5 pruned (> 14 days).
Step 2: C creates 5 ledger request entries:
          possessions: {}, uploadedAt: 0

        A and B are offline. Zero upload candidates.
        → C shows camera placeholder thumbnails.
        → Ledger entries persist as standing requests.

~Later~: Device A comes online.
         → Sees 5 request entries with uploadedAt = 0
         → Has all 5 files locally
         → Hash scoring: A is the only online candidate, wins all 5
         → Claims each via CAS transaction, uploads, sets uploadedAt
         → C's next sync: sees uploadedAt > 0, downloads all 5
         → Placeholders resolve to actual thumbnails
         → Prune check: all devices have files → cleanup
```

---

## Device Behavior Matrix

| Situation | Has File? | Action |
|---|---|---|
| Just captured photo | Yes | Upload to cloud → on success, create ledger entry |
| Ledger: uploadedAt > 0 | No | Download from cloud, mark possession, prune check |
| Ledger: uploadedAt > 0 | Yes | Just mark possession, prune check |
| Ledger: request (uploadedAt = 0) | Yes, highest hash | Claim via CAS transaction, upload, set uploadedAt |
| Ledger: request (uploadedAt = 0) | Yes, not highest | Just mark possession (don't upload) |
| Ledger: request (uploadedAt = 0) | No | Wait, show placeholder |
| Stale assignment (5+ min) | Yes | Claim via CAS transaction, upload |
| No ledger entry, no local file | No | Check cloud first → download if found, else create request entry |
| Transaction receiptId changed to null | Yes (old file) | Delete local file + thumbnail |
| Own queue not empty | Yes (other's file) | Finish own queue first |
| < 5 min since own last upload | Yes (other's file) | Wait before volunteering |

---

## Ledger Communication Stages

| Ledger State | Meaning |
|---|---|
| Entry created with `uploadedAt > 0` | "I just uploaded this file, come get it" (normal flow) |
| `possessions={A: true, B: true}` | "These devices have the file locally" |
| `possessions={}, uploadedAt=0` | "I need this file, does anyone have it?" (recovery request) |
| `uploadAssignee=A, assignedAt=<time>` | "Device A is working on the re-upload" |
| All group devices in possessions | "Everyone has it, safe to prune" |
| Entry deleted | Ledger + cloud file pruned — local copies remain |

The ledger serves three roles: **notification** (new file available), **recovery** (request re-upload), and **garbage collection** (prune when all devices have the file).

---

## Concurrency & Atomicity

Multiple devices may update the same ledger entry simultaneously. Naive read-modify-write cycles cause silent data loss (one device's write overwrites another's). Two patterns handle this:

### Possession Marking — Dot-Notation Updates

When a device downloads a file and marks itself in `possessions`, use Firestore's dot-notation field update — this writes a single map key atomically without reading or touching other keys:

```kotlin
// Safe: Device B and Device C can call this concurrently
// without overwriting each other's entries
ledgerRef.update("possessions.${myDeviceId}", true)
```

Two devices writing `possessions.B = true` and `possessions.C = true` at the same time both succeed independently. No transaction needed for the common case.

### Prune Check — Transaction

After marking possession, check whether all group devices now have the file. This read-then-decide step requires a transaction to avoid a race where two devices both think they're the last and both try to prune:

```kotlin
firestore.runTransaction { tx ->
    val snap = tx.get(ledgerRef)
    val possessions = snap.get("possessions") as? Map<*, *> ?: emptyMap<String, Any>()
    if (possessions.keys.containsAll(allGroupDeviceIds)) {
        tx.delete(ledgerRef)
        // also trigger Cloud Storage file deletion outside the transaction
    }
}
```

### Upload Assignment Takeover — Compare-and-Swap Transaction

When a device wants to take over a stale assignment (`assignedAt` 5+ minutes ago, `uploadedAt` still 0), use a transaction to claim it conditionally. This avoids redundant uploads when two devices try to volunteer simultaneously:

```kotlin
firestore.runTransaction { tx ->
    val snap = tx.get(ledgerRef)
    val currentAssignee = snap.getString("uploadAssignee")
    val currentAssignedAt = snap.getLong("assignedAt") ?: 0L

    // Only claim if the assignment hasn't changed since we checked
    if (currentAssignee == staleAssignee && currentAssignedAt == staleAssignedAt) {
        tx.update(ledgerRef, mapOf(
            "uploadAssignee" to myDeviceId,
            "assignedAt" to System.currentTimeMillis()
        ))
        true  // claimed — proceed to upload
    } else {
        false // another device already claimed it — skip
    }
}
```

If the transaction race is lost (two devices claim at the exact same instant), Firestore retries one of them. The retry will see the updated `assignedAt` and bail out. Worst case, if retries are exhausted and both upload, the duplicate upload safety net still applies — same content, same path, no corruption.

### Summary

| Operation | Technique | Why |
|---|---|---|
| Mark possession | `update("possessions.$id", true)` | Atomic per-key, no read needed |
| Check "all devices have it" | Transaction | Read-then-decide must be atomic |
| Claim re-upload assignment | Compare-and-swap transaction | Prevents redundant uploads |
| Create ledger (after upload) | `set()` with originator data | Single writer, no contention |
| Create recovery request | `set()` with empty possessions | Single writer (requesting device) |
| Mark `uploadedAt` (re-upload) | `update("uploadedAt", ts)` | Single writer (the re-uploader) |

---

## Pruning

### Happy Path (All Devices Have File)
Last device to download runs a prune-check transaction (see Concurrency & Atomicity). If all current group devices are in `possessions`, atomically deletes ledger entry and triggers Cloud Storage file deletion. Local copies remain on all devices.

### 14-Day Stale Pruning

All ledger entries older than 14 days are pruned — both normal entries (uploadedAt > 0) and active recovery requests (uploadedAt = 0). A device still missing files after its request is pruned can simply recreate a new request entry on its next sync.

#### When

Stale pruning triggers on the **first sync after 12:00 noon local group time**. This avoids multiple cleanup storms throughout the day. If no device syncs at noon (all apps closed), the cleanup simply waits until the next time any device syncs after noon — no urgency.

#### Who

Same two-tier logic as upload assignment: filter to online devices, hash picks one. Specifically:

1. Each device checks on sync: "Is it past noon today, and have I not yet run (or seen results of) today's cleanup?"
2. Compute `hash("cleanup" + todayDateString + deviceId) % 1000` among online devices
3. Highest score wins cleanup duty
4. If the assigned device doesn't complete cleanup within 5 minutes (based on `imageCleanupAssignedAt` on the group document), any other online device can take over via CAS transaction

#### Cleanup procedure

1. Pull the **full ledger** (regardless of flag clock) to ensure all entries are current
2. For each entry where `createdAt` is older than 14 days:
   - Delete the Cloud Storage file (`groups/{groupId}/receipts/{receiptId}.enc`)
   - Delete the ledger entry
3. Bump `imageLedgerFlagClock` on the group document to notify other devices the ledger changed

#### After pruning

If a device comes online after pruning and is missing photos referenced by transactions, the normal recovery flow handles it: check cloud first, then create a request entry if needed. Other devices with the file respond via hash-based upload assignment.

### Solo Devices

Apps not part of a family group store all receipt files locally only. No ledger, no cloud upload, no sync. The entire image ledger system is skipped.

---

## Group Device Enumeration

Several operations require knowing "all group devices" — prune checks, hash-based upload scoring, and cleanup assignment. This uses `FirestoreService.getDevices()`, which returns all devices in `groups/{groupId}/devices/` where `removed != true`.

- **Removed devices** (admin-marked `removed: true`) are excluded — they do not block prune checks or receive upload assignments
- **Stale devices** (long `lastSeen` gap) are NOT auto-removed. They remain in the device list until an admin manually removes them. This means a stale device can block the "all devices have it" prune check. However:
  - The 14-day stale pruning catches this — entries are pruned by age regardless of possession status
  - Admins should periodically review and remove devices that are no longer in use (existing "Repair Attributions" flow helps identify these)

---

## Local Storage Pruning

Local receipt files persist indefinitely by default. Over time this can grow (~54MB/year at 30 receipts/month). An admin-configurable pruning age allows controlled cleanup.

### Shared Setting
```kotlin
// Added to SharedSettings (synced via CRDT)
val receiptPruneAgeDays: Int? = null,       // null = no pruning (default)
val receiptPruneAgeDays_clock: Long = 0L,
```

Admin-only setting, configured in Settings. Syncs to all group devices via normal shared settings CRDT flow.

### Settings UI

- Show **total receipt cache size** (sum of `receipts/` + `receipt_thumbs/` directories) in Settings
- Admin sees a "Receipt retention" picker to set the pruning age (e.g., 30, 60, 90, 180, 365 days, or "Keep all")
- Non-admin sees the current setting as read-only

### Pruning Behavior

On app startup, if `receiptPruneAgeDays` is set:

1. Calculate the **effective prune date** = `max(today - pruneAgeDays, mostRecentPrunedDate)`
   - `mostRecentPrunedDate` is stored locally and tracks the last prune date used
   - If the admin changes the prune age to a longer period (further into the past), we use the most recent pruned date to avoid re-requesting files that were already intentionally deleted
2. Delete all local receipt files + thumbnails linked to transactions older than the effective prune date
3. Update `mostRecentPrunedDate` to the current effective prune date

### Re-Upload Suppression

Devices must NOT create recovery request ledger entries for photos linked to transactions older than the effective prune date. Otherwise pruning would trigger a storm of re-upload requests:

- Before creating a recovery request, check the transaction date against the effective prune date
- If the transaction is older → show a dimmed placeholder or no thumbnail, do NOT request re-upload
- This check also applies to the cloud download step — don't download files that would be immediately pruned

### Future: Transaction Pruning

Pruning transactions themselves is significantly more complex because budget calculations, period ledger, and amortization entries depend on them. This is noted as a future consideration but not designed here.

---

## Ledger Flag Clock — Sync Optimization

The full image ledger could contain dozens of entries. Reading the entire collection on every sync wastes Firestore reads when nothing has changed. The flag clock solves this.

### How it works

1. **Every sync cycle**, the device already reads the group document (`groups/{groupId}`) for the dissolution check. The `imageLedgerFlagClock` field comes along for free — zero additional Firestore reads.
2. Compare `imageLedgerFlagClock` to the locally stored `lastSeenFlagClock`
3. **If equal** → ledger is unchanged, skip reading the collection entirely
4. **If `imageLedgerFlagClock` > `lastSeenFlagClock`** → at least one entry was created or updated since last check. Pull the full ledger, process all entries, update `lastSeenFlagClock` to match

### When the flag clock is bumped

| Event | Bumps flag clock? | Why |
|---|---|---|
| New ledger entry created (after upload) | No | CRDT sync of `receiptId` already triggers cloud download on other devices |
| Recovery request entry created | Yes | Other devices need to see the request to re-upload |
| Re-upload completes (`uploadedAt` set) | Yes | Requesting device is waiting on this to download |
| Possession marked | No | Per-device bookkeeping, not actionable for others |
| Ledger entry deleted (prune) | Yes | Via cleanup procedure |
| Download failure → request replacement | Yes | Need a device to re-upload |

### Full ledger pull overrides

The flag clock is skipped (full ledger pulled regardless) when:
- **Cleanup is triggered** — must see all entries to evaluate `createdAt` ages
- **A download fails** — need current state of the specific entry to decide retry vs. request replacement

---

## UI

### Capture Points
- Camera button on add-transaction dialog
- "Attach receipt" button on transaction detail/edit

### Display
- Thumbnail on transaction list items (if receipt attached)
- Full-size view on tap

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
- Firestore ledger: ~200 bytes per ledger entry
- Transit: brief (cloud file deleted after all devices download)

### Per Transaction (up to 5 photos)
- Local storage: up to ~750KB
- Cloud Storage: up to ~1MB (encrypted, transient)
- Firestore ledger: up to 5 entries (~1KB total)

### At Scale (1,000 users × 30 transactions/month, avg 2 photos each)
- Cloud Storage: transient (~12GB peak if all in-flight, pruned to near-zero)
- Firestore: ~60K ledger entries/month, ~120K writes/month (create + possession + prune)
- Well within free tier initially
- ~$0.25/month on Blaze plan

### Firebase Free Tier
- Cloud Storage: 5GB storage, 1GB/day download
- 5GB = ~25,000 photos at 200KB each (encrypted)
- At 2 photos/transaction avg, supports ~12,500 in-flight transactions before hitting storage cap
- In practice, cap is rarely approached because files are pruned after all devices download

---

## Implementation Checklist

### Data Model
- [ ] `receiptId1`–`receiptId5` + `receiptId1_clock`–`receiptId5_clock` fields on Transaction (5 photo slots)
- [ ] `ImageLedgerEntry` data class
- [ ] `imageLedgerFlagClock` + cleanup fields on group document (no new data class needed)
- [ ] `ReceiptMetadata` local data class (add `downloadRetryCount` field)
- [ ] `receiptPruneAgeDays` + clock field on SharedSettings

### Core
- [ ] ReceiptManager — capture, downsize, encrypt, store locally
- [ ] Thumbnail generator for list display
- [ ] Camera placeholder drawable for missing images
- [ ] Firebase Cloud Storage dependency + initialization (check existing security rules)
- [ ] ImageLedger Firestore CRUD operations
- [ ] Solo device detection — skip ledger/cloud when not in a family group
- [ ] Pending upload queue — persisted to disk, track receiptIds awaiting upload, process on each connected sync
- [ ] Delta hold-back — delta builder skips receiptIdN fields still in pending upload queue
- [ ] Crash recovery — load persisted queue on startup, resume uploads, maintain hold-back

### Upload & Sync
- [ ] Upload-first flow: upload to cloud, then create ledger entry on success (no flag clock bump)
- [ ] Hash-based upload scoring system (two-tier: online filter, then hash)
- [ ] Group device enumeration via `getDevices()` (excludes `removed` devices)
- [ ] CAS transaction for re-upload assignment takeover
- [ ] Own-queue-first priority with 5-minute cooldown before volunteering
- [ ] Download logic with dot-notation possession marking
- [ ] Prune-check transaction (all non-removed devices have file → delete ledger + cloud)

### Flag Clock
- [ ] Read `imageLedgerFlagClock` from group document (already fetched every sync — no new read)
- [ ] Compare `flagClock` to local `lastSeenFlagClock` — skip ledger read if equal
- [ ] Pull full ledger when flag clock is newer
- [ ] Bump flag clock on: request create, re-upload complete, cleanup, request replacement (NOT on initial entry creation)
- [ ] Force full ledger pull on: cleanup trigger, download failure

### Recovery & Retry
- [ ] Recovery flow: check cloud first, then create ledger request + bump flag clock if missing
- [ ] Requesting device waits on flag clock (no cloud polling)
- [ ] Download retry tracking: 3 real failures (excluding network errors) → replace entry with request
- [ ] Full ledger pull on any download failure
- [ ] Suppress recovery requests for transactions older than effective prune date

### Cloud Pruning (14-day)
- [ ] 14-day stale pruning of all entry types (normal + request)
- [ ] Noon-trigger: first sync after 12:00 local group time
- [ ] Hash-based cleanup assignment with 5-min CAS takeover
- [ ] `imageLastCleanupDate` tracking on group document

### Local Storage Pruning
- [ ] `receiptPruneAgeDays` admin-only shared setting (CRDT-synced)
- [ ] Settings UI: show total receipt cache size
- [ ] Settings UI: admin retention picker (30/60/90/180/365 days or "Keep all")
- [ ] On startup: delete local files + thumbnails for transactions older than effective prune date
- [ ] Track `mostRecentPrunedDate` locally to prevent re-request after date change
- [ ] Suppress cloud downloads for photos that would be immediately pruned

### Deletion
- [ ] Long-press thumbnail → confirm → clear receiptIdN slot, remove ledger entry, delete cloud file
- [ ] Merge-time link removal detection: old receiptIdN non-null → new null → delete local file + thumbnail
- [ ] Periodic orphan scan: delete local receipt files not referenced by any transaction receiptId1–5

### UI
- [ ] Camera button on transaction dialogs
- [ ] 5-slot photo row integration with `receiptId1`–`receiptId5` (connect existing `SwipeablePhotoRow`)
- [ ] Full-size photo viewer
- [ ] Long-press thumbnail picker with Delete option
- [ ] Gate behind Subscriber tier

---

*Designed 2026-03-16. Ready for implementation.*

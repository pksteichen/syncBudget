---
name: Firestore-native sync migration plan
description: Plan to replace hand-rolled CRDT sync with Firestore-native document storage + field-level encryption. Eliminates clocks, deltas, merges, repairs.
type: project
---

# Firestore-Native Sync Migration

## Why
The hand-rolled CRDT sync system (Lamport clocks, DeltaBuilder, CrdtMerge, echo prevention, integrity checker, repair mechanism) is fragile and produces endless edge cases. Firestore already handles sync, offline persistence, conflict resolution, and retry — but we can't use it because we encrypt entire payloads as blobs.

## Plan: Option 1 — Firestore-native with field-level encryption
Store each record as a separate Firestore document. Encrypt field VALUES (not structure). Let Firestore handle all sync mechanics.

### Data structure
```
groups/{groupId}/transactions/{txnId}:
  enc: "base64_encrypted_blob"  // all field values encrypted together
  deviceId: "plain"             // for attribution (visible)
  updatedAt: ServerTimestamp     // Firestore ordering
  deleted: false                // for tombstone queries (visible)

groups/{groupId}/recurringExpenses/{reId}:
  (same pattern)
```

### What this eliminates (~2000 lines)
- LamportClock.kt (entire file)
- DeltaBuilder.kt (entire file)
- DeltaSerializer.kt (entire file)
- CrdtMerge.kt (entire file)
- SyncEngine.kt sync() method (most of it)
- IntegrityChecker.kt clock/segment comparison
- lastPushedClock, lastSyncVersion, echo prevention
- Snapshot/chunk mechanism
- Delta pruning
- Rescue migrations
- Push loop prevention

### What stays
- E2E encryption (field-level instead of blob-level)
- Pairing codes (encrypted key exchange)
- Firebase Auth (anonymous)
- IntegrityChecker value-based comparison (for corruption detection)
- Cash reconciliation
- Category tag dedup (handled client-side on read)

### How sync works
1. Local edit → encrypt fields → write to Firestore document
2. Firestore SDK handles offline queue, retry, server timestamps
3. Other devices receive changes via `addSnapshotListener` (real-time)
4. On receive → decrypt fields → update local state
5. Conflict resolution: last-write-wins based on server timestamp (automatic)
6. New device: listener receives ALL documents on first attach

### Migration path
1. Create new branch (`firestore-native-sync`)
2. Build new FirestoreDocService (read/write individual documents)
3. Build EncryptedDocSerializer (encrypt/decrypt field values)
4. Replace SyncEngine with FirestoreDocSync (snapshot listeners)
5. Migrate existing data: read from old JSON files, write to Firestore
6. Remove old sync code after verification
7. Keep old code on `dev` branch until migration is proven

### Open questions
- Firestore pricing at scale (1 doc read per record per sync vs batched deltas)
- Whether to encrypt all fields as one blob per doc or individually
- How to handle the transition period (old devices with CRDT vs new with native)

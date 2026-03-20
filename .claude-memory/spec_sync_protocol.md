---
name: Sync Protocol Specification
description: Complete sync protocol — clock lifecycle, sync cycle sequence, DeltaBuilder rules, CRDT merge, echo prevention, state persistence
type: reference
---

# Sync Protocol Specification

## Clock Lifecycle

### LamportClock
- **tick()**: Called on local writes (create/edit records, migrations). Returns incremented clock.
- **merge(remoteClock)**: Called when receiving remote data. Sets clock = max(current, remoteClock) + 1.
- Process-wide ReentrantLock ensures atomicity across all instances.

### lastPushedClock
Threshold for "dirty" fields. A field is pushed only if `field_clock > lastPushedClock`.

**Advanced at two points:**
1. **Step 5e (before DeltaBuilder)**: Set to maxReceivedClock from remote deltas — ECHO PREVENTION
2. **After successful push**: Set to max clock from pushed deltas

### lastSyncVersion
Latest delta version fetched from Firestore. Advanced after delta fetch.

## Sync Cycle Sequence

```
Step 0:  Pre-checks (stale 90d, version compat, dissolved, removed)
Step 1:  Device registration / snapshot bootstrap / catch-up
Step 2:  Fetch remote deltas (paginated, cursor-based)
Step 3:  Decrypt & deserialize (skip own, skip unreadable)
Step 4:  Per-field CRDT merge + lamportClock.merge(maxFieldClock)
Step 5:  Category dedup & remap (validate→tag dedup→orphan scan→resolve chains)
Step 5e: ADVANCE lastPushedClock past received clocks (ECHO PREVENTION)
Step 6:  Build deltas via DeltaBuilder, chunk at 200, push to Firestore
         → Advance lastPushedClock after push
Step 7:  Update device metadata + integrity fingerprint
Step 8:  Snapshot bookkeeping (write if 100+ deltas since last)
Step 9:  Record lastSuccessfulSync
Step 10: Admin claim check
Step 10.5: Integrity repair (30min interval, quiet cycles only)
Step 11: Receipt cleanup & sync
Step 12: Return SyncResult → SyncWorker saves merged data to JSON
```

## Echo Prevention (Step 5e)

**Problem**: CRDT merge uses maxOf(local, remote) for clocks. After merge, fields have higher clocks than lastPushedClock, making them look "dirty" to DeltaBuilder.

**Solution**: Before DeltaBuilder runs, advance lastPushedClock past all received clocks:
```kotlin
if (maxReceivedClock > lastPushedClock) {
    lastPushedClock = maxReceivedClock
}
```
Now DeltaBuilder only sees genuinely local changes.

## DeltaBuilder Rules

- Include field if `field_clock > lastPushedClock`
- Piggyback critical fields via `ensureField(key, value, clock)` — only if clock > 0 and not already in delta
- Receipt fields respect pendingUploadReceiptIds
- If no fields qualify → return null (no delta)

## CRDT Merge Rules

```kotlin
fun shouldAcceptRemote(localClock, remoteClock, localDeviceId, remoteDeviceId): Boolean {
    if (remoteClock > localClock) return true       // higher clock wins
    if (remoteClock == localClock) return remoteDeviceId > localDeviceId  // tie-break
    return false
}
```

Properties: commutative, associative, idempotent, convergent.

## State Persistence

**SharedPreferences (sync_engine)**: lastSyncVersion, lastPushedClock, lastSnapshotVersion, lastIntegrityCheckTime, catIdRemap, migration flags
**JSON files**: transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger, sharedSettings
**Firestore**: deltas (encrypted), device metadata, snapshots, admin claims

## Key Invariants

1. lamportClock.value >= max(all field clocks, lastPushedClock)
2. After Step 5e: lastPushedClock >= max(received clocks) → no echo
3. Merge is idempotent: re-pushing same delta is safe
4. Tombstone deletion: deleted=true with clock, never remove from list
5. Push threshold: only field_clock > lastPushedClock gets pushed

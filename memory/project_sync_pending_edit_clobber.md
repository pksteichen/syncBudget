---
name: SYNC pending-edit clobber on multi-device active groups — bug + fix shipped
description: When another device was actively writing to the same doc the local user just edited, the inbound silently overwrote the local pending edit despite conflict-detection firing. Fixed in v2.10.07 along with 4 related sync hardenings.
type: project
---

## Status
**Fixed in v2.10.07** (2026-05-08). `SyncMergeProcessor.processBatch` now drops conflicted inbounds at the top of every collection branch (`if (event.isConflict) { conflictDetected = true; continue }`); the old transaction `isUserCategorized=false` workaround + `conflictedTransactionsToPushBack` plumbing is gone from `MainViewModel` and `BackgroundSyncWorker`.

Four related FirestoreDocSync hardenings also shipped in the same release:
1. **pushRecord race window closed** — `localPendingEdits[stateKey]` is now set *before* the Firestore I/O (was after at line 636), with cleanup on the empty-diff and PeriodLedger-already-exists early returns. The catch block leaves `pendingEdits` set so the 1h TTL bounds divergence on permanent push failure.
2. **Cursor TOCTOU lock** — new `cursorWriteLock: ConcurrentHashMap<String, Any>` + `advanceCursor(collection, candidate)` helper makes load-compare-save atomic per collection. Replaces inline cursor logic at all 3 call sites (pure-echo, mixed-batch end-of-launch, sharedSettings).
3. **isListening guards** — `handleCollectionChanges` and `handleSharedSettingsChange` now early-return when `!isListening`, dropping callbacks that race with `stopListeners()` so a stale callback can't advance the cursor past data we never propagated.
4. **Failed-deserialization cursor skip** — new `failedDocIds` set in the per-batch loop; cursor advance filters these out so a transient corruption (or wrong-key) doc gets re-delivered on the next listener fire instead of being silently lost behind the cursor.

Original repro (foreign inbound silently overwriting local pending edit on tablet) is the canonical test — first RE/transaction edit should now stick on the first attempt. **Validated 2026-05-08 across 3 devices (including tablet emulator)** — first-attempt edits and deletions now apply correctly.

## Symptom
On the non-host device:
- First save of an RE/transaction/SG/AE/IS edit → no change applied.
- First delete → record stays.
- Second attempt → works correctly.

## Root cause
1. Local edit on tablet → `FirestoreDocSync.pushRecord` adds `localPendingEdits[stateKey]` and pushes to Firestore.
2. Before the tablet's push commits server-side, an inbound listener delivery arrives carrying the host's earlier write to the same doc (`lastEditBy = host_deviceId`).
3. `FirestoreDocSync.kt:839` detects the conflict (`lastEditBy != deviceId && localPendingEdits.containsKey(stateKey)`), sets `isConflict = true` on the `DataChangeEvent`, and logs `"Conflict detected: $stateKey ..."`.
4. **But the event is still emitted to `SyncMergeProcessor`, which applies the inbound to local state**, overwriting the tablet's pending edit:
   - **Transactions** (`SyncMergeProcessor.kt:141`): copies the inbound with `isUserCategorized = false` and queues for push-back. The local edit is still lost — the inbound's amount/category/etc. replace the user's edit.
   - **RE / IS / SG / AE / categories / periodLedger**: no `isConflict` handling at all. The inbound just replaces local at index.
5. The tablet's push then commits, but with the data that was just clobbered by the inbound, so it contains the host's values, not the user's edit.

The conflict-detection log line was added to surface these events but does not gate the merge.

## Why the host kept pushing during the test
Tablet logcat (2026-05-07 10:40–10:44) shows ~6 conflicts in 4 minutes against `lastEditBy = 66f6df55-…` (host phone deviceId). Different RE IDs each time (34227, 33260, 16728, 7610, 37521, 21102), so the host wasn't hammering one doc — it was pushing through a stream of RE updates that happened to overlap each tablet edit. Most likely culprits (untriaged):
- Host's 24-h `runPeriodicMaintenance()` integrity check fired in this window and pushed local-only / repaired records.
- Host's period-refresh re-stamping `setAsideSoFar` on REs.
- Host's budget recompute reacting to the tablet's incoming edits and pushing back updated derived fields.

## Proposed fix
In `SyncMergeProcessor.processBatch`: when `event.isConflict == true`, **skip applying the inbound entirely** — let the local pending edit survive. The local push will commit (`lastEditBy = us`); the next listener delivery is our own echo, which `FirestoreDocSync` filters out and clears `pendingEdits`; subsequent foreign writes apply normally.

```kotlin
when (event.collection) {
    EncryptedDocSerializer.COLLECTION_RECURRING_EXPENSES -> {
        if (event.isConflict) continue  // local pending edit wins
        val re = event.record as RecurringExpense
        ...
    }
    // same guard for IS, SG, AE, categories, periodLedger, sharedSettings
    EncryptedDocSerializer.COLLECTION_TRANSACTIONS -> {
        if (event.isConflict) continue  // remove the isUserCategorized=false hack
        var txn = event.record as Transaction
        ...
    }
}
```

Drop the existing transaction `isUserCategorized = false` workaround + push-back as part of the same change — it was a band-aid that still loses the user's edit.

### Edge cases the fix handles correctly
- **Local push fails post-conflict.** `localPendingEdits` has a 1-hour TTL (`FirestoreDocSync.kt:799`). After expiry, the next inbound applies normally. Worst case: tablet shows stale local state for up to 1 hour.
- **Both devices pending-editing the same doc concurrently.** Each drops the other's inbound. Both pushes commit. Firestore's last-writer-wins on `updatedAt` resolves the doc. Both devices receive the final state via subsequent listener delivery (their pending entries cleared by their own echoes). The "loser" sees their local change replaced *only after* their push echoes back — so they at least see their own change applied briefly before the merge.
- **Echo handling unchanged.** The early echo-filter at `FirestoreDocSync.kt:766-780` already drops self-pushes and clears `pendingEdits`. The new conflict skip only affects foreign writes that arrive while we have a pending edit.

### Out-of-scope (future work)
- A real "two devices edited at once — pick one" UI conflict resolver. The proposed fix lets local always win during the conflict window, which is correct for the common case (transient sync race) but wrong for the rare case (two users genuinely intending different edits at the same instant). For BudgeTrak's typical 1-3 person family group, transient race is overwhelmingly the case.

## How to verify the fix
1. Repro setup: phone (host) actively scrolls through transactions / opens budget config (triggers integrity / recompute pushes). Second device (tablet emulator) joins the group, opens RE list, edits the amount on any RE, taps Save.
2. Before fix: first save shows no change; logcat shows `Conflict detected` then `Received 1 changes` then `Batch pushed`. Second save sticks.
3. After fix: first save sticks. Logcat still shows `Conflict detected` (the log line is informational), but no `Received 1 changes` line interleaved with the save — the inbound is dropped.

## Reproduction artifacts (kept under /Download/BudgeTrak/support/)
- `logcat_Tablet.txt` — tablet logcat at 2026-05-07 10:40–10:44 with the conflict pattern.
- `sync_diag_Tablet.txt` — tablet sync diag for the same window.
- `sync_log_Tablet.txt` — tablet sync event log.

---

## Implementation plan (for a fresh-context session)

You're picking this up cold. The user has already approved the fix; **don't re-litigate the design** — just apply the changes, build, verify the diff is what's intended, then commit. The memory above captures the why.

### 0. Pre-flight (≤ 2 min)
- Verify branch: `git status` should show clean tree on `dev`. If not, ask before proceeding.
- Confirm `app/build.gradle.kts` has `compileSdk = 35`, `targetSdk = 35`, `versionCode = 21`, `versionName = "2.10.06"`. The committed state should be 35; you'll temporarily swap to 34 only for the local Termux build (and revert before commit).

### 1. Apply the code change to `SyncMergeProcessor.processBatch`
**File:** `app/src/main/java/com/techadvantage/budgetrak/data/sync/SyncMergeProcessor.kt`

Open it and read the `processBatch` function (search for `for (event in events)` — at line ~130). Verify the current shape matches what's described in the memory above, then make the following edits (one per `when` branch). You're adding a single `if (event.isConflict) continue` guard at the **top of each branch**, and removing the existing transaction-only `isUserCategorized = false` + `conflictedTransactionsToPushBack` workaround.

**Transactions branch (~line 137-158):**

Replace the entire conflict-handling block:
```kotlin
EncryptedDocSerializer.COLLECTION_TRANSACTIONS -> {
    var txn = event.record as Transaction

    // Conflict: another device edited while we had pending edits.
    // Mark as unverified so the user can review.
    if (event.isConflict) {
        txn = txn.copy(isUserCategorized = false)
        conflictDetected = true
    }

    // Route pre-cutoff transactions to archive instead of active list
    if (archiveCutoffDate != null && txn.date.isBefore(archiveCutoffDate)) {
        archivedIncoming.add(txn)
    } else {
        val idx = txnIndex[txn.id]
        if (idx != null) transactions[idx] = txn
        else { txnIndex[txn.id] = transactions.size; transactions.add(txn) }
    }

    // Push conflict flag back so other devices also see unverified
    if (event.isConflict) {
        conflictedTransactionsToPushBack.add(txn)
    }
}
```

with:

```kotlin
EncryptedDocSerializer.COLLECTION_TRANSACTIONS -> {
    // Local pending edit wins on conflict — drop the inbound. The local
    // push will commit and the next foreign write applies normally.
    // See memory/project_sync_pending_edit_clobber.md.
    if (event.isConflict) {
        conflictDetected = true
        continue
    }
    val txn = event.record as Transaction
    if (archiveCutoffDate != null && txn.date.isBefore(archiveCutoffDate)) {
        archivedIncoming.add(txn)
    } else {
        val idx = txnIndex[txn.id]
        if (idx != null) transactions[idx] = txn
        else { txnIndex[txn.id] = transactions.size; transactions.add(txn) }
    }
}
```

**RE branch (~line 162):** Add `if (event.isConflict) continue` as the first line of the `when` branch (before `val re = event.record as RecurringExpense`).

**IS branch (~line 170):** same — add the guard at the top.

**SG branch (~line 178):** same.

**AE / categories / periodLedger / sharedSettings branches:** same — add the guard at the top of every `when` branch in the loop. Read the full method body and apply the guard everywhere there's a `when (event.collection) { … -> { … inbound applied … } }` arm. Don't miss any.

After all edits, **delete the now-unused `conflictedTransactionsToPushBack` collection** (its declaration near the top of the function and its consumer downstream — search the file for the symbol). Use Grep to confirm no references remain.

### 2. Verify imports / unused symbols
Run `./gradlew compileDebugKotlin --no-daemon` after the Termux SDK swap (step 3). Compile errors will point at any `conflictedTransactionsToPushBack` references you missed; the symbol should be fully gone.

### 3. Build the debug APK
Per `memory/MEMORY.md` Termux build path (this is the canonical workflow — don't deviate):

```
# In app/build.gradle.kts: temporarily set compileSdk = 34, targetSdk = 34
export JAVA_HOME=/data/data/com.termux/files/usr
./gradlew assembleDebug --no-daemon
# Revert compileSdk = 35, targetSdk = 35
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Download/BudgeTrak.apk
```

The `.kts` swap is just for the local build; CI uses 35. If you forget to revert, `git status` will show it before commit — don't commit a 34 file.

### 4. Verification on device
Don't synthesize tests for this — the existing test suite doesn't cover sync flows and adding one is out of scope. Verify by repro on the tablet emulator:

a. **Setup**: install the new APK on the tablet emulator (either re-launch from the same Google Play AVD with Paul's group already joined, or rejoin if state was cleared).

b. **Trigger a host-side write storm**: have Paul's phone open BudgeTrak and either trigger period-refresh manually (toggle reset hour briefly) or just leave it idle on a screen that triggers integrity checks.

c. **On the tablet**: edit any RE's amount, save. Then delete a different RE. Then edit a transaction's amount, save.

d. **Expected (post-fix)**: each first attempt sticks. Logcat (`tag:FirestoreDocSync`) still prints `Conflict detected: ...` lines (the detection log is informational), but **no** `Received 1 changes in recurringExpenses` line should be interleaved between the conflict-detected log and the local push commit — the inbound is dropped at the merge layer.

e. **Pre-fix smoke test (optional but reassuring)**: revert the change locally, build, repro to confirm you can still reproduce the original first-edit-fails behavior. Then re-apply the fix and confirm it goes away.

### 5. Update spec_firestore_native_sync.md and LLD
- `memory/project_firestore_native_sync.md` (or wherever the SyncMergeProcessor conflict handling is documented — search the memory dir for `isConflict` and `isUserCategorized = false`): update the conflict-handling description to reflect the new "drop inbound, local wins" semantics.
- `docs/BudgeTrak_LLD_v2.8.md` line ~1471: update the bullet:
  > **Transactions:** if `isConflict`, set `isUserCategorized=false` and queue for push-back.

  to:
  > **All collections:** if `isConflict`, the inbound is dropped at the merge layer (local pending edit wins). The conflict-detection log line in `FirestoreDocSync` remains informational. The pending edit clears via the local push's own echo (`FirestoreDocSync.kt` echo filter); subsequent foreign writes apply normally.

### 6. Commit + push to dev
Per `/push` workflow in CLAUDE.md. Suggested commit message:

```
fix: drop inbound on SYNC conflict so local pending edit wins

Previously, when a foreign device's listener delivery arrived while
we had a pending local edit on the same doc, conflict detection fired
in FirestoreDocSync but SyncMergeProcessor applied the inbound anyway,
silently overwriting the user's edit. For transactions we patched the
inbound with isUserCategorized=false and pushed it back; for RE/IS/SG/
AE/categories/periodLedger we just clobbered local at index. Either
way, the user's first edit was lost — they had to retry. Visible on
non-host devices when the host was actively writing (period refresh,
integrity check, budget recompute). See
memory/project_sync_pending_edit_clobber.md for full diagnosis.

SyncMergeProcessor.processBatch now drops conflicted inbounds at the
top of every collection branch. Local push commits, lastEditBy=us,
echo filter clears pendingEdits, subsequent foreign writes apply
normally. Removed the transaction isUserCategorized=false workaround +
conflictedTransactionsToPushBack (band-aid that still lost the edit).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

Push to dev (not main). Don't bump versionCode — this lands in v2.10.06's continuing release window.

### Anti-goals (do NOT do these)
- Don't add a UI conflict-resolver dialog. That's flagged as out-of-scope above.
- Don't extend `localPendingEdits` semantics or change its TTL. The 1-hour expiry is fine.
- Don't touch `FirestoreDocSync.kt` itself — the conflict-detection log is correct as-is. The fix is purely in the consumer (`SyncMergeProcessor`).
- Don't try to merge fields between local pending and inbound. Drop-and-let-local-win is the simpler, correct behavior for the common case.

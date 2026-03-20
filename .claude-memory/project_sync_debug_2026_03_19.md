---
name: Sync debugging session plan (2026-03-19)
description: Active sync issues: push loop, disappearing categories, cash fluctuation. Detailed investigation plan with root cause hypotheses.
type: project
---

## Active Sync Issues (as of 2026-03-19 late night)

Both devices running same app version. Admin device = 6aeff266, Kim's device = 1578568b.

### Issue 1: Push Loop (258 records every other cycle)

**Symptoms:**
- Every sync pushes 258 records (181 txns, 18 RE, 2 IS, 7 SG, 24 AE, 21 PL, 5 cats)
- `pushClock` advances by 2 each cycle (e.g., 1772529876 → 1772529878)
- Pattern: receive deltas → next cycle pushes 258 → receive own deltas back → push again

**Hypotheses to investigate:**
1. **Re-stamp code (MainActivity ~line 1225)**: After sync, "addedTxns" re-stamps records that were created during sync. If this fires incorrectly (non-empty addedTxns when nothing was actually added), it bumps all clocks above lastPushedClock, creating dirty records.
   - **Check**: Add logging to see if `addedTxns` is non-empty and what's in it
   - **Check**: Is the snapshot taken before sync (`syncTxns = transactions.toList()`) different from `transactions` after sync?

2. **Echo prevention gap**: The echo prevention code (SyncEngine ~line 740) advances `lastPushedClock` past received remote clocks. But the re-stamp code in MainActivity runs AFTER this, bumping clocks above the new `lastPushedClock`.
   - **Fix**: The re-stamp code should only run for genuinely new records, not for records that existed in the snapshot

3. **Receipt photo pending uploads ticking clocks**: The 4 failing receipt uploads run every cycle. `ReceiptManager.loadPendingUploads()` is called in `DeltaBuilder.buildTransactionDelta()`. The pending set isn't empty, which might affect delta building.
   - **Check**: Are receipt-related fields being included/excluded differently each cycle?

4. **Lamport clock merge inflation**: `SyncEngine` merges the lamport clock with max received field clocks (line 730-731). This advances the clock. Then `recomputeCash()` or other post-sync code ticks it again.

**Diagnostic steps:**
1. Add logging in MainActivity's sync result handler: `syncLog("addedTxns=${addedTxns.size}, addedRe=${addedRe.size}, ...")`
2. Add logging before/after re-stamp: log which records are being re-stamped
3. Check if `lastPushedClock` value in SharedPreferences matches what SyncEngine uses
4. Temporarily disable the re-stamp code to see if the push loop stops

### Issue 2: Disappearing Categories

**Symptoms:**
- Admin added new categories, they disappeared
- Sync log shows 18 cats consistently (should be more)

**Hypotheses:**
1. **Non-admin's CSV import triggered category dedup/remap**: When the non-admin loaded credit card CSV, auto-categorization may have created categories that conflicted with existing ones. The `catIdRemap` logic may have merged or dropped categories.
   - **Check**: Look at `catIdRemap` in sync_engine SharedPreferences
   - **Check**: Are the missing categories in the `deleted=true` tombstone state?

2. **Clock conflict**: Admin adds category with clock N. Non-admin's sync pushes categories with clock N+1 (from re-stamp). Admin's categories get overwritten by LWW merge.
   - **Check**: Export sync_diag.txt and look at all category clocks, especially deleted ones

3. **Snapshot overwrite**: If a snapshot was written during the push storm, it may have captured a state without the new categories.

**Diagnostic steps:**
1. Export sync_diag.txt from BOTH devices — compare category lists
2. Check for `deleted=true` categories that shouldn't be deleted
3. Check catIdRemap for unexpected mappings

### Issue 3: AvailableCash Fluctuation

**Symptoms:**
- AvailableCash seems wildly wrong and fluctuates between syncs

**Hypotheses:**
1. **Both devices recomputing cash with different transaction sets**: If the push loop is causing transactions to appear/disappear between cycles, `recomputeAvailableCash` produces different results each time.
   - **Fix**: Fixing the push loop should stabilize this

2. **SharedSettings.availableCash CRDT conflict**: Both devices sync `availableCash` via CRDT. If both recompute different values and push them, LWW picks the highest clock, which alternates between devices.
   - **Check**: Is `availableCash_clock` advancing rapidly?
   - **Note**: `availableCash` in SharedSettings is a synced field, but `recomputeCash()` only writes to local app_prefs, NOT to SharedSettings CRDT. So this shouldn't be the issue unless something else syncs it.

3. **Period ledger conflicts**: The period ledger records budget amounts at each reset. If both devices have different period ledger entries (from the push storm), the recomputation uses different base data.

**Diagnostic steps:**
1. Fix the push loop first — this likely fixes the cash issue too
2. If cash is still wrong after push loop fix, compare period ledger entries between devices
3. Check if `recomputeAvailableCash` produces the same result on both devices with the same data

### Issue 4: Kim 87 Versions Behind

**Symptoms:**
- `Integrity skip Kim: syncVer mismatch (local=2310, remote=2223)`
- Kim's lastSyncVersion is 2223, admin is at 2310

**Explanation:**
- The push loop is inflating version numbers rapidly on the admin device
- Kim's device is processing deltas but may be behind due to the volume
- This should self-resolve once the push loop stops — Kim will catch up

### Receipt Upload Failures

**Symptoms:**
- 4 receipts stuck in pending upload queue, all failing every cycle
- `upload failed, will retry next sync` for each

**Explanation:**
- The `google-services.json` is a placeholder — Firebase Storage isn't configured
- These uploads will never succeed until a real Firebase project is connected
- The failing uploads are harmless but noisy in the sync log

**Quick fix**: Clear the pending upload queue by deleting `pending_receipt_uploads.json` from app internal storage, or add a max retry count for uploads that clears after N failures.

### Priority Order
1. Fix push loop (root cause of most issues)
2. Verify categories after push loop fix
3. Verify cash after push loop fix
4. Clear stuck receipt upload queue

### Key Files to Read
- `SyncEngine.kt` lines 700-750 (push clock advancement, echo prevention)
- `MainActivity.kt` lines 1220-1300 (re-stamp code, sync result handler)
- `DeltaBuilder.kt` lines 24-84 (transaction delta building, receipt hold-back)
- `IntegrityChecker.kt` lines 49-58 (maxClock for transactions — includes receipt clocks)

---
name: Activity.recreate() preserves the ViewModel
description: When you write to disk and need the UI to reflect the new state, recreate() is NOT a refresh — ViewModelStore survives Activity recreation by design. Always pair disk writes with an explicit vm.reloadAllFromDisk() (or equivalent), and force a Compose screen-tree remount via a `key(vm.dataReloadVersion)` wrapper for wholesale state changes.
type: feedback
---

## The rule

Anytime code writes the app's canonical state to disk (restore, import, sync merge, migration) and expects the UI to render the new content, the VM must be explicitly refreshed. **Do not rely on `Activity.recreate()` to "refresh".**

**Why:** ViewModels are scoped to the `ViewModelStore`, which is intentionally preserved across Activity recreation. That's the whole point of ViewModels — they survive config changes (rotation) and `recreate()`. After a recreate, the new MainActivity instance receives the exact same MainViewModel instance with the exact same in-memory state. The screen tree rebuilds, but it reads from the (un-refreshed) VM.

The only thing that destroys the VM is process death (force-stop, OS reclaim). That's why the bug "looks fixed" after a force-stop.

## How to apply

For any flow that writes to the JSON files in `filesDir/`:

1. Call `vm.reloadAllFromDisk()` after the write completes, on the Main thread.
2. Wrap the state mutations in `Snapshot.withMutableSnapshot { ... }` so derivedStateOf observers see one atomic transition rather than partial intermediate states.
3. Increment `vm.dataReloadVersion` inside the same snapshot block.
4. The screen-routing block in `MainActivity.setContent` is wrapped in `key(vm.dataReloadVersion) { ... }` — that re-mounts the entire screen subtree on every reload, sidestepping any Compose smart-skipping or stale derivedStateOf reads that retain pre-reload state.

`Snapshot.withMutableSnapshot` alone is not sufficient. The encrypted-restore bug (2026-05-01) reproduced even with the snapshot wrap because some downstream readers had cached values that didn't re-derive on the apply notification. The `key()` wrap is the safety net that always works — at the cost of resetting `remember { ... }` state inside screens, which is the right tradeoff for a wholesale reload.

## Reproduction trail (2026-05-01)

- v2.9 release shipped with Activity.recreate() in the encrypted-backup restore handler (`MainActivity.kt:1540` area). Plaintext-backup path correctly called `vm.reloadAllFromDisk()`; encrypted path didn't.
- Symptom: restore appears to succeed (transactions visible) but Categories/RecurringExpenses/Income Sources screens show empty/seeded-defaults until process kill. Dump output (which reads from disk) shows the data IS written; the VM lists held the pre-restore in-memory state from before recreate().
- Fix: replaced `recreate()` with `vm.reloadAllFromDisk()` and added the `dataReloadVersion` + `key()` infrastructure described above.

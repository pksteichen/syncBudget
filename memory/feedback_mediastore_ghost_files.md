---
name: MediaStore ghost files from Termux rm
description: Termux 'rm' on app-owned public-Download files leaves MediaStore ghost entries that block the app's subsequent open(O_CREAT) with EEXIST. Use the Files app for cleanup, or touch a placeholder to dodge the trap.
type: feedback
originSessionId: 911fd8f0-4c5a-4a6d-a592-a304d4d2704a
---
## The rule
Never use Termux `rm` to delete files in `/storage/emulated/0/Download/...` that are owned by the BudgeTrak app. Direct the user to the Android Files app instead.

**Why:** On Android 11+ scoped storage, public-Download files are indexed by MediaStore. Termux `rm` removes the inode via direct File API but does NOT update the MediaStore index. Result: a "ghost" path — MediaStore still claims it, but no file exists. When the app subsequently tries to create a fresh file there via `FileOutputStream` / `File.appendText` (which calls `open(O_WRONLY | O_APPEND | O_CREAT)`), the kernel returns **EEXIST (File exists)** even though `ls` shows nothing. The Java layer wraps this as `FileNotFoundException: <path>: open failed: EEXIST`.

This is **distinct** from the orphan-EACCES case documented in `reference_public_download_writes.md`. That one fires after a reinstall (different UID owns the file). EEXIST here is purely about MediaStore/inode disagreement caused by a non-MediaStore-aware deletion.

**How to recognize:**
- Toast: "Debug sync failed: `<full path>`" with no further detail
- Logcat: `EEXIST (File exists)` in the `open()` chain
- `ls` shows the file missing, but app `O_CREAT` still fails

**How to apply:**
- **Cleanup:** if you've already done the bad `rm`, recover with `touch <path>` from Termux — Android's FUSE layer auto-attributes the new placeholder to the directory-owning UID, so the app's `O_APPEND` won't trip the `O_CREAT` branch. Imperfect (the MediaStore ghost remains), but it unblocks immediately. The proper cleanup is to delete via the Android Files app, which calls `MediaStore.delete()`.
- **Code-side mitigation:** prefer `context.filesDir/diag_logs/` (per-app private, no MediaStore involvement, no permission required) for high-frequency diagnostic logs. The dump button can republish to public Download via `PublicDownloadWriter` (MediaStore-aware) at the moment the operator wants the file visible. As of 2026-05-06, `native_sync_log.txt` and `fcm_debug.txt` already follow this pattern via `BackupManager.getInternalDiagDir(context)`. `token_log.txt` is still on the old public-Download path and remains vulnerable to the same footgun on future reinstalls.

**Personal note (incident origin):** this footgun was discovered the hard way on 2026-05-06 — I `rm`'d two stale orphan logs from Termux to "fix" the dump-button error, and the next attempt failed with EEXIST. The user had to wait through the diagnosis. Use Files app, or `touch` if you've already messed up.

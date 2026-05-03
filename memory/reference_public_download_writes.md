---
name: Public Download write paths
description: Every place BudgeTrak writes into Download/BudgeTrak/, with orphan-resilience properties and which use PublicDownloadWriter
type: reference
originSessionId: 2ae43715-e466-4f34-8cb2-c1df4c388ef5
---
# Public Download write paths

All writes target `/storage/emulated/0/Download/BudgeTrak/<subdir>/`. The challenge: Android scoped storage refuses File API writes to files left behind by a previous install ("orphans") with `EACCES`, since the new install doesn't inherit ownership. Each path below is annotated with how it copes.

## All seven active write categories (none are deprecated)

| Subdir | Producer | Mechanism | Orphan-resilient? |
|---|---|---|---|
| `backups/` (`.enc`) | `BackupManager.createSystemBackup` / `createPhotosBackup` | `nextAvailableSuffix(dir, dateStr, suffix)` → `backup_2026-05-02b_system.enc` if `2026-05-02_system.enc` exists. `File.exists()` sees orphans. | **Yes** — suffix walks past orphans. |
| `backups/` (CSV / XLSX / JSON) | `TransactionsScreen` `csvSaveLauncher` / `xlsSaveLauncher` / `jsonSaveLauncher` | SAF `ActivityResultContracts.CreateDocument` — user picks destination URI, OS owns it. | **Yes** — SAF, not File API. |
| `PDF/` | `ExpenseReportGenerator.generateSingleReport` | `PublicDownloadWriter.writeStream` (since v2.10.03). Filename `expense_${date}_${merchant}_${txn.id}.pdf`. | **Yes** — via helper. |
| `photos/$stamp/` | `MainActivity` photo dump (Settings) | Direct `FileOutputStream(File(photosDir, name))`. `$stamp = "yyyy-MM-dd_HHmmss"` — fresh subdir every dump. | **Yes** — fresh subdir per dump. **NOT deprecated.** |
| `support/sync_diag*.txt`, `logcat_*.txt`, etc. | `DiagDumpBuilder.writeDiagToMediaStore` (called from `MainActivity` dump button + `BackgroundSyncWorker` DebugDumpWorker path) | `PublicDownloadWriter.writeBytes` (since v2.10.03). | **Yes** — via helper. |
| `support/pre_restore_backup.json` | `FullBackupSerializer.applyRestore` | `PublicDownloadWriter.writeBytes` (since v2.10.03). Production users hit this on restore-after-reinstall. | **Yes** — via helper. |
| `support/token_log.txt`, `native_sync_log.txt`, `crash_log.txt` | `BudgeTrakApplication.tokenLog` / `syncEvent`, `FirestoreDocSync.syncLog`, `MainActivity` uncaught handler | Direct `File.appendText`. Wrapped in `try {} catch (_) {}` — silently swallows EACCES. Debug only (or crash-only). | **No, but tolerated** — content also goes to Crashlytics; local file loss is acceptable. |

## `PublicDownloadWriter` (`data/PublicDownloadWriter.kt`, v2.10.03)

Three-tier writer for orphan-resilient public-download writes:

1. **Cached path** — after a previous MediaStore-fallback success, the resolved on-disk File path is stored in SharedPreferences `public_download_writer` keyed by `relSubdir/fileName`. Subsequent writes skip the EACCES round-trip and go straight there.
2. **Canonical direct write** — `File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS), relSubdir).writeBytes(bytes)`. Fast path for fresh installs and files we own.
3. **MediaStore fallback** — `MediaStore.Files.getContentUri("external")` insert with `RELATIVE_PATH=Download/<relSubdir>/`. Auto-suffixes with ` (1)`, ` (2)`, … when the canonical path is already occupied by an orphan. Resolved File path cached for tier 1.

API:
- `writeBytes(context, relSubdir, fileName, mimeType, bytes): File?`
- `writeStream(context, relSubdir, fileName, mimeType, produce: (OutputStream) -> Unit): File?` — materializes via `ByteArrayOutputStream` first so all three tiers can retry without re-invoking `produce` mid-stream.

Trade-off: a single `(N)` suffix may appear after a reinstall, then stays stable for the life of that install (the cache keeps subsequent writes pointed at the same physical file). The user never sees a system delete-confirmation dialog — that was the explicit design choice (see chat log 2026-05-02).

## Common confusions to avoid

- **"Photo saves were deprecated."** No — they're an active feature. Settings → "Save Photos" produces a fresh `Download/BudgeTrak/photos/yyyy-MM-dd_HHmmss/` subfolder, copies all referenced receipt files in, then runs orphan cleanup. `MainActivity.kt` ~1340–1390.
- **"All Downloads writes need the helper."** No — backups (suffix logic) and SAF exports (CSV/XLSX/JSON) are already orphan-safe. Only fixed-name files (PDF expense reports use `txn.id` which is preserved across backup-restore; support files use literal names like `sync_diag.txt`) needed the helper.
- **"Just use MediaStore everywhere."** Tempting but produces `(N).pdf` accumulation on every write without the prefs cache. The cache is what keeps reinstall cruft to one suffix per file.

## Verifying which path a file took

After a write, check `getSharedPreferences("public_download_writer", MODE_PRIVATE)`:
- Key `relSubdir/fileName` present → MediaStore fallback was used at least once. Value = actual on-disk path (may carry `(N)` suffix).
- Key absent → canonical direct write succeeded (fresh install or own file).

---
name: Backup Specification
description: Auto + manual backups, password encryption, SAF restore, photos file, pre-restore snapshot, retention, serialized fields
type: reference
---

# Backup Specification

## Goal
Give every user (including free) a simple path to back up and restore without caring about the cloud or an account. Encrypted with a user password, restorable across package renames and fresh installs, and portable enough to survive the legacy → rebranded app transition.

## Core design decisions

- **Encryption**: ChaCha20-Poly1305 AEAD with a PBKDF2-SHA256 key (100k iterations, 256-bit key, random salt per file). `CryptoHelper.kt:26, 56-58`. No Android KeyStore dependency — the user's password is the key material, so backups survive reinstall and cross-package moves.
- **No package-name dependency**: the file format is self-describing (`"type": "syncbudget_full_backup"` or `"syncbudget_join_snapshot"` — names intentionally preserved from the old `com.syncbudget.app` branding so existing backups still restore; see `feedback_preserve_persistence_names.md`).
- **Two separate files per backup**: a system backup (`{tag}.enc`) with all entities + prefs, and a parallel photos file (`{tag}_photos.enc`) with the receipt-photo blobs + manifest. Same tag lets restore link them.
- **Same-day versioning**: letter suffix b, c, d, … appended when a second backup is taken the same day, instead of overwriting. `BackupManager.kt:93-101`.
- **SAF directory access for restore**: the Storage Access Framework is used to pick the backup folder (no MANAGE_EXTERNAL_STORAGE permission). This is what makes restore work across a package rename where `File.listFiles()` fails on scoped storage. `MainActivity.kt:1281-1290`.

## When backups run
- **Auto-backup**: scheduled by `runPeriodicMaintenance()` (sync users) and `onResume` (all users). `isBackupDue()` gate using `backupFrequencyWeeks` pref (1 / 2 / 4 weeks; default 1). `BackupManager.kt:307-321`.
- **Immediate backup on enable**: when the user flips auto-backup on, the first backup runs right away (inside the setup dialog's confirm handler, not waiting for the next scheduled check). `MainActivity.kt:1154-1170`.
- **Manual Backup Now** button in Settings. Same code path as auto.

## Retention
- `backupRetention` pref: `1`, `10`, or `-1` (All).
- Default: `10` (changed from `1` on 2026-04-12).
- Cleanup deletes oldest files first, keeping the newest `N` (plus their paired `_photos.enc`).

## Serialized fields

### Entities (full rewrites in `FullBackupSerializer.kt:229-236`)
- transactions, categories, recurring expenses, income sources, amortization entries, savings goals, period ledger, archived transactions.

### SharedSettings (applied via `FullBackupSerializer.kt:239-242`)
All synced fields — currency, budgetPeriod, reset fields, income mode, manual budget, match tolerances, attribution, availableCash, deviceRoster, familyTimezone, receiptPruneAgeDays, archiveCutoffDate / carryForwardBalance / archiveThreshold / lastArchiveInfo, lastChangedBy.

### SharedPreferences (`app_prefs`, keys in `FullBackupSerializer.kt:245-285`)
- Display / format: `currencySymbol, digitCount, showDecimals, dateFormatPattern, chartPalette, appLanguage`.
- Budget config: `budgetPeriod, budgetStartDate, resetHour, resetDayOfWeek, resetDayOfMonth, isManualBudgetEnabled, manualBudgetAmount, weekStartSunday, incomeMode`.
- Matching: `matchDays, matchPercent, matchDollar, matchChars`.
- State: `availableCash, lastRefreshDate`.
- UI prefs: `autoCapitalize, showWidgetLogo` (both added 2026-04-12 — were previously lost on a fresh-device restore).
- *Not* serialized: Crashlytics opt-out pref, sync group ID (restoring a backup never joins the user into the source device's group).

## Photos backup file
`BackupManager.createPhotosBackup` (lines 119-186) collects every receiptId referenced by any transaction (`ReceiptManager.collectAllReceiptIds`), encrypts each full-size photo individually with the PBKDF2-derived key, and assembles a single archive with a JSON manifest of `{receiptId, offset, length}` entries.

Restore (`BackupManager.restorePhotosBackup`, 211-283) decrypts each entry, writes the full-size file, and regenerates the 200×Q=70 thumbnail via `BitmapFactory.decodeByteArray` → `ReceiptManager.saveThumbnail`. Thumbnails are **not** stored in the photos file — they're derived on restore.

## Pre-restore snapshot
Before any restore, `FullBackupSerializer.kt:212-219` auto-saves the current state to `support/pre_restore_backup.json` (plaintext, for emergency recovery). Gives the user an "undo" if they restore the wrong file.

## Toast feedback
- Success: `"Backup created"`.
- Failure: `"Backup failed — please try again"`, displayed with 7000 ms duration (longer than default so users see it).

## Failure handling
- 50 MB minimum free-space precheck (`BackupManager.kt:59-64`) before writing. Returns failure with a toast if insufficient.
- If the write fails partway, the partial file is deleted immediately (no corrupt half-files left behind — `BackupManager.kt:114` / `:183`).
- Password mismatch on restore → AEAD verification fails, decrypt returns null, restore is blocked.

## Tier gating
**Backups are not gated.** All tiers (free, paid, subscriber) have access to backup/restore. This is intentional — basic data portability is a right, not a premium feature. The gated exports are CSV / Excel / PDF (Paid and above).

## Restore entry points
- **Settings → Restore Backup** — auto-discovers backups in the configured backup directory; offers "Browse for backup" if none found, which launches the SAF picker.
- **Fresh-device restore**: default suggested location is `Download/BudgeTrak/backups`, but SAF lets the user navigate anywhere.
- **Join snapshot** (`"syncbudget_join_snapshot"` type) is used internally by the SYNC join flow and is **not** exposed as a restore option through the UI.

## Not covered by version migration
- The backup format has no explicit version-migration logic. Breaking changes to the payload would therefore silently fail to restore older files. Worth adding a version bump + migration branch if the format evolves significantly.

---
name: BudgeTrak rebrand (April 11, 2026)
description: Full namespace/applicationId/GitHub rebrand from com.syncbudget.app and com.securesync.app to com.techadvantage.budgetrak under Tech Advantage LLC. Records what changed, what was intentionally preserved, and gotchas encountered.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
On 2026-04-11, BudgeTrak was rebranded from its legacy identities to its launch identity.

## What changed

- **Kotlin namespace**: `com.syncbudget.app` → `com.techadvantage.budgetrak`
- **applicationId**: `com.securesync.app` → `com.techadvantage.budgetrak`
- **GitHub repo**: `pksteichen/syncBudget` → `techadvantagesupport/BudgeTrak` (old URL 301-redirects)
- **Firebase**: new Android app `com.techadvantage.budgetrak` registered in the existing `sync-23ce9` Firebase project alongside the legacy `com.securesync.app` entry
- **ProGuard rules**: updated to keep `com.techadvantage.budgetrak.data.**`
- **Publisher**: Tech Advantage LLC (`techadvantagesupport@gmail.com`)

## What was intentionally preserved

- **Persistence-layer protocol identifiers**: `"syncbudget_full_backup"` and `"syncbudget_join_snapshot"` in `FullBackupSerializer.kt` — renaming would break restore compatibility with existing backup files
- **SharedSettings/Firestore field names**: `familyTimezone`, `enc_familyTimezone`, etc. — renaming would break sync compatibility with existing groups
- **Local JSON file names**: `future_expenditures.json` — renaming would lose existing data on device upgrade
- **Local working directory**: `/data/data/com.termux/files/home/dailyBudget/` — purely local, renaming adds churn with no benefit
- **Firebase project ID**: `sync-23ce9` — cannot be changed and is invisible to users

## Gotchas encountered during the rebrand

1. **google-services.json must match applicationId**: the Google Services Gradle plugin validates the package_name in the JSON against the applicationId at build time. After renaming applicationId, the build fails until a new google-services.json (with the new package registered) is downloaded from Firebase Console.
2. **Android scoped storage blocks cross-package file reads**: backup files created by the old app (`com.securesync.app`) are invisible to the new app (`com.techadvantage.budgetrak`) via `File.listFiles()` even though they're in a shared directory. Fixed with a SAF directory picker (zero permissions). Do NOT add `MANAGE_EXTERNAL_STORAGE` — BudgeTrak is a zero-permission app and that's a brand promise.
3. **App Check SHA-1 fingerprint is per-app**: the debug SHA-1 must be registered under the NEW Firebase app entry, not just the old one. Without it, all Firestore/RTDB/Storage calls fail with PERMISSION_DENIED.
4. **Fine-grained PATs are scoped to specific repos**: the GitHub PAT created before the transfer didn't include the newly-transferred repo. Had to update the token to "All repositories" scope.
5. **`gh` CLI auth is global**: `gh auth switch` changes the active account for all terminals/tmux panes simultaneously. But git push/pull uses the stored PAT via local credential-helper overrides, so it's unaffected. Only `gh` CLI commands (pr, issue, release) care about the active gh account.

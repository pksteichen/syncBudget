---
name: Never rename persistence-layer field/file names
description: Local JSON file names, SharedSettings field names, and Firestore field names must be preserved across cosmetic renames
type: feedback
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
When sweeping the codebase for cosmetic renames (e.g., Family Sync → SYNC, Future Expenditures → Savings Goals), **never** rename names that are part of the persistence contract — they would silently break loading on every existing install.

**Why:** local JSON files are read by key, SharedSettings fields are serialized by name, and Firestore documents store fields by their Kotlin property name (or an `enc_<name>` derivative). Renaming any of these would either fail to load existing data, or load it as default/null. Migration code is possible but adds complexity and risk for a purely cosmetic improvement.

**How to apply:** during a rename sweep, **preserve** these names even when they look stale relative to the new branding:

- **Local JSON file names** under `app/src/main/java/com/techadvantage/budgetrak/data/*Repository.kt`:
  - `future_expenditures.json` (Savings Goals storage) — keep as-is
  - Any other `*.json` filename in a Repository's `FILE_NAME` constant

- **SharedSettings data class fields** in `data/SharedSettings.kt`:
  - `familyTimezone: String` — keep as-is (was renamed in `SyncStrings` only — that's a string label, not data)
  - Any other field that's serialized to `shared_settings.json`

- **Firestore field names** in `data/sync/EncryptedDocSerializer.kt`:
  - `enc_familyTimezone`, `enc_*` for any other persisted field
  - Any field name passed to `encryptField()` / `decryptString()` / `firestoreUpdate()`

- **Firestore collection names** in `data/sync/EncryptedDocSerializer.kt`:
  - `COLLECTION_*` constants — these are the actual paths under `groups/{groupId}/`. The collection names use **camelCase** (e.g., `incomeSources`, `recurringExpenses`, `savingsGoals`, `sharedSettings`, `periodLedger`), not snake_case — earlier docs got this wrong.

What **is** safe to rename:
- Composable function names (`FamilySyncScreen` → `SyncScreen`)
- Kotlin file names (`FamilySyncScreen.kt` → `SyncScreen.kt`) — Kotlin imports use FQ class names, not file paths
- Data class names (`FutureExpendituresStrings` → `SavingsGoalsStrings`)
- String label fields in `AppStrings.kt`/`EnglishStrings.kt`/`SpanishStrings.kt` (these are user-facing text, never persisted)
- Navigation route literals in `MainActivity.kt` (in-memory only, ephemeral)
- TranslationContext.kt entries (translator metadata, not persisted)

When in doubt, grep for the field name across `*Repository.kt`, `*Serializer.kt`, and `SharedSettings*.kt` — if it appears anywhere there, leave it alone and rename only the user-facing label.

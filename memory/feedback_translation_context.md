---
name: Translation context for new strings
description: NEVER hardcode UI strings — all user-visible text must go through the i18n system, with TranslationContext.kt kept lockstep
type: feedback
---

**NEVER use hardcoded English strings in UI code.** Every user-visible string — dialog titles, button labels, toasts, dropdown options, TextField labels/placeholders, error/validation messages, help text, accessibility `contentDescription`s, section headers, snackbars — must use the `S.section.key` pattern via `LocalStrings.current` (or `vm.strings.section.key` in MainActivity).

**Why:** The app supports English and Spanish (es-419). Hardcoded strings show in English to Spanish-locale users — a real translation bug, not just lint. This has happened in waves: receipt photos / backup work produced 57 untranslated strings; the 2026-05-09 audit found another ~30 (backup toasts, debug-dump flow, photo picker, accessibility descriptions, brand-rule `DialogHeader("Sync")` violation).

## Adding a new string — the four-file ritual

When you write ANY composable that displays text:

1. **Add the field** to the appropriate data class in `AppStrings.kt`. Pick the data class by where the text appears (dashboard text → `DashboardStrings`, settings → `SettingsStrings`, etc.; truly common reusables like Cancel/OK/Rotate → `CommonStrings`).
2. **Add the English value** in `EnglishStrings.kt`.
3. **Add the Spanish translation** in `SpanishStrings.kt` (es-419, neutral Latin American — see `reference_strings_system.md` for variant rules).
4. **Add a context entry** in `TranslationContext.kt` describing meaning, usage, and disambiguation.
5. **Use `S.section.key`** in the composable code — never a raw string literal.

All four files must land in the same commit. Do this AS YOU WRITE the UI code, not as a follow-up task.

## Misfile prevention (the #1 source of TranslationContext drift)

The Kotlin compiler enforces parity between `AppStrings` / `EnglishStrings` / `SpanishStrings` (data class constructor). It does NOT enforce `TranslationContext.kt` — that file can drift silently. The most common drift mode is **putting a context entry in the wrong `mapOf`** (e.g., a `RecurringExpensesStrings` field's context ends up in the `savingsGoals` map because the maps are visually adjacent).

The map name is **the data class name with `Strings` stripped and the first letter lowercased**:

| Data class | Map name |
|---|---|
| `CommonStrings` | `common` |
| `DashboardStrings` | `dashboard` |
| `SettingsStrings` | `settings` |
| `BudgetConfigStrings` | `budgetConfig` |
| `TransactionsStrings` | `transactions` |
| `SavingsGoalsStrings` | `savingsGoals` |
| `AmortizationStrings` | `amortization` |
| `RecurringExpensesStrings` | `recurringExpenses` |
| `SyncStrings` | `sync` |
| `BudgetCalendarStrings` | `budgetCalendar` |
| `*HelpStrings` | `*Help` (e.g., `transactionsHelp`) |
| `WidgetTransactionStrings` | `widgetTransaction` |
| `SimulationGraphHelpStrings` | `simulationGraphHelp` |
| `DefaultCategoryNames` | `defaultCategoryNames` (irregular — no `Strings` suffix) |

When adding a context entry, **first grep for an existing entry from the same data class to confirm you're inside the right `mapOf`** — don't trust visual proximity.

## Drift verification — run before every i18n-touching commit

This one-liner reports any mismatch between AppStrings fields and TranslationContext keys (zero output = clean):

```bash
cd app/src/main/java/com/techadvantage/budgetrak/ui/strings
awk '
  /^data class [A-Z][A-Za-z]+\(/ { cls = $3; sub(/\(.*/, "", cls); next }
  /^\)/ { cls = "" }
  cls && /^    val [a-zA-Z][a-zA-Z0-9]+ *:/ {
    f = $2; sub(/:.*/, "", f);
    sub(/Strings$/, "", cls); cls = tolower(substr(cls, 1, 1)) substr(cls, 2);
    print cls "\t" f
  }
' AppStrings.kt | sort > /tmp/app_norm.txt
awk '
  /^    val [a-zA-Z][a-zA-Z0-9]+ = mapOf\(/ { mapn = $2; next }
  /^    \)/ { mapn = "" }
  mapn && /^        "[a-zA-Z][a-zA-Z0-9]*" to/ {
    match($0, /"[a-zA-Z][a-zA-Z0-9]*"/);
    print mapn "\t" substr($0, RSTART+1, RLENGTH-2)
  }
' TranslationContext.kt | sort > /tmp/ctx_norm.txt
diff /tmp/app_norm.txt /tmp/ctx_norm.txt
```

If `diff` reports anything: lines starting with `<` are fields in AppStrings without context (forgotten or misfiled out); lines starting with `>` are stale orphans in TranslationContext. Fix before commit.

## Hardcoded-string scan — run before every UI-touching commit / `/push`

`TranslationContext` parity catches missing context for declared fields, but not strings that were never declared in the first place. Catch hardcoded UI strings with these greps in `app/src/main/java/com/techadvantage/budgetrak/ui/` and `MainActivity.kt`:

- `Text\("[A-Z]` — Text composable with capital-letter literal
- `Toast.makeText\([^,]+, *"` and `toastState\.show\("` — toast first arg
- `contentDescription = "[A-Z]` — accessibility label (visible to TalkBack)
- `title = \{ Text\("[A-Z]` / `text = \{ Text\("[A-Z]` — dialog params
- `label = \{ Text\("[A-Z]` / `placeholder = \{ Text\("[A-Z]` — TextField
- `setError\("` — TextField error
- `DropdownMenuItem\(\s*text = \{ Text\("` — dropdown options

Exclusions (these are NOT bugs):
- Log/diag: `Log.d/i/w/e`, `Crashlytics.log`, `syncLog(`, `BudgeTrakApplication.syncEvent`, exception messages
- Tags / keys: SharedPreferences keys, Firestore field names, intent action strings, MIME types
- Format specifiers: `"%s"`, `"%.2f"`, date patterns
- Brand names / proper nouns: `"BudgeTrak"`, `"SYNC"`, `"Tech Advantage LLC"`, file format names (`"CSV"`, `"PDF"`), example placeholders that read as data not UI (`"Netflix"`)
- `Text("English")` / `Text("Español")` — language picker labels stay in their own native language
- Sample-data illustrations inside help screens (already wrapped by translated help text)

## Files

- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/AppStrings.kt` — data class fields (compile-time schema)
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/EnglishStrings.kt` — English values
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/SpanishStrings.kt` — Spanish values
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/TranslationContext.kt` — translator context (NOT compiler-enforced — verify with the awk one-liner above)

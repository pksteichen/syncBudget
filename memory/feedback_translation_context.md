---
name: Translation context for new strings
description: NEVER hardcode UI strings — all user-visible text must go through the i18n system (AppStrings/EnglishStrings/SpanishStrings/TranslationContext)
type: feedback
---

**NEVER use hardcoded English strings in UI code.** Every user-visible string — dialog titles, button labels, toasts, dropdown options, error messages, help text, accessibility descriptions — must use the `S.section.key` pattern via `LocalStrings.current`.

**Why:** The app supports multiple languages. Hardcoded strings bypass translation and create technical debt. This happened extensively during the receipt photos and backup feature implementation, producing 57 untranslated strings.

**How to apply:** When writing ANY composable or dialog that displays text to the user:

1. **Add the field** to the appropriate data class in `AppStrings.kt` (e.g., `SettingsStrings`, `TransactionsStrings`)
2. **Add the English value** in `EnglishStrings.kt`
3. **Add the Spanish translation** in `SpanishStrings.kt`, using the English string and context as reference
4. **Add translation context** in `TranslationContext.kt` describing meaning, usage, and disambiguation
5. **Use `S.section.key`** in the composable code — never a raw string literal

All four files must stay in sync. Do this AS YOU WRITE the UI code, not as a follow-up task.

This applies to:
- Dialog titles, body text, buttons (including "Cancel", "Delete", "OK" — use `S.common.*`)
- Dropdown option labels
- Toast messages
- TextField labels and placeholder text
- Error/validation messages
- Help/explanatory text
- Content descriptions for accessibility
- Section headers

Files:
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/AppStrings.kt` — data class fields
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/EnglishStrings.kt` — English values
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/SpanishStrings.kt` — Spanish values
- `app/src/main/java/com/techadvantage/budgetrak/ui/strings/TranslationContext.kt` — translator context

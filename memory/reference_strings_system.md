---
name: Strings and translation system reference
description: How AppStrings / EnglishStrings / SpanishStrings / TranslationContext / LocalStrings fit together, and how to add a new string
type: reference
---

## File layout (`ui/strings/`)
- **`AppStrings.kt`** (1498 lines, ~1,226 `val` fields across ~25 `*Strings` data classes) — the schema. Add new fields here first. Kotlin's data-class constructor is the compile-time contract that forces the English/Spanish implementations to stay in lockstep.
- **`EnglishStrings.kt`** (1896 lines) — `object EnglishStrings : AppStrings` providing every English value.
- **`SpanishStrings.kt`** (1882 lines) — same structure with Spanish values.
- **`TranslationContext.kt`** (1477 lines) — parallel `mapOf` per data class with translator-facing context descriptions for every field. Metadata only — not compiled into the app.
- **`LocalStrings.kt`** — `staticCompositionLocalOf<AppStrings>`. Composables read via `val S = LocalStrings.current; Text(S.settings.title)`.

## Language selection
- `appLanguage` pref: `"en"`, `"es"`, or missing (device default via `Locale.getDefault().language`).
- Switches the injected singleton at the Activity-level `CompositionLocalProvider(LocalStrings provides ...)`.

## Adding a new string
1. Add the field to the relevant data class in `AppStrings.kt`.
2. Add the English value in `EnglishStrings.kt`.
3. Add the Spanish value in `SpanishStrings.kt` (use the English copy with `// TODO` if a translation isn't ready yet — but the constructor won't compile without *something*).
4. Add a context entry in `TranslationContext.kt` — describe meaning and use, disambiguate multi-sense words (Save = file save, not accumulate money; Resume = continue, not CV).
5. Reference from the composable via `S.<section>.<field>`.

All four files must land in the same commit. Do this inline with the UI code, not as a follow-up task.

## What goes through the i18n system
Every user-visible string: dialog titles/body/buttons (including common "Cancel", "OK", "Delete" → `S.common.*`), dropdown options, toast messages, TextField labels/placeholders, error/validation messages, help text, accessibility `contentDescription`s, section headers.

## Translator-context discipline (per 2026-04-10 audit)
`TranslationContext.kt` is production quality. The rules in force:
- Contexts describe **meaning and use**, never the existing translation.
- Multi-sense English words are explicitly disambiguated.
- Lambda parameter contents are documented inside the context.
- No self-referential ("the string 'X' as used in the app") or vague ("a button labeled X") patterns.
- Spanish-specific notes (plurals, gendered nouns) where relevant.

When adding new entries, follow the same style.

## SYNC in strings
- "SYNC" is always all-caps in both languages — brand mark, never translated. See `feedback_sync_branding.md`.
- Spanish "Sincronización" is fine as a concept/verb ("la sincronización está al día") but the **feature/page/button** is always uppercase **SYNC**.
- "BudgeTrak" is the product name — never translated.

## Size discipline
Lockstep between `AppStrings`, `EnglishStrings`, `SpanishStrings` is enforced by the Kotlin compiler. `TranslationContext` isn't compiled, so drift is possible there — periodic audits should check it covers every field.

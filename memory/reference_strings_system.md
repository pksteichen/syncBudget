---
name: Strings and translation system reference
description: How AppStrings / EnglishStrings / SpanishStrings / TranslationContext / LocalStrings fit together, and how to add a new string
type: reference
---

## File layout (`ui/strings/`)
- **`AppStrings.kt`** — the schema (~1,450 `val` fields across 22 data classes). Add new fields here first. Kotlin's data-class constructor is the compile-time contract that forces the English/Spanish implementations to stay in lockstep.
- **`EnglishStrings.kt`** — `object EnglishStrings : AppStrings` providing every English value.
- **`SpanishStrings.kt`** — same structure with Spanish values (es-419, see below).
- **`TranslationContext.kt`** — parallel `mapOf` per data class with translator-facing context descriptions for every field. Metadata only — **not compiled into the app**, so drift is possible. Verify with the one-liner in `feedback_translation_context.md`.
- **`LocalStrings.kt`** — `staticCompositionLocalOf<AppStrings>`. Composables read via `val S = LocalStrings.current; Text(S.settings.title)`.

Line counts shift with every change — don't memorize them. Run `wc -l` if you need an actual current count.

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

## Spanish locale variant
The Spanish strings target **neutral Latin American Spanish (es-419)**, not Castilian (es-ES). Confirmed 2026-05-01 by grepping `SpanishStrings.kt`:
- Uses *computadora* (LATAM), not *ordenador* (Castilian).
- Zero matches for *vosotros*, *móvil* (cell phone sense), *coche*, *ordenador*.
- Uses *tú* throughout (151 instances) — works in both regions; no *vos* (so not Argentine/Uruguayan voseo specifically).

**Implication for Play Console / store listing:** use `<es-419>` language tag for release notes, store listing description, screenshot captions, and any other locale-tagged content — never `<es-ES>`. Mismatching the tag means LATAM testers see Castilian release notes (or vice versa) for an app whose UI is the other variant.

## SYNC in strings
- "SYNC" is always all-caps in both languages — brand mark, never translated. See `feedback_sync_branding.md`.
- Spanish "Sincronización" is fine as a concept/verb ("la sincronización está al día") but the **feature/page/button** is always uppercase **SYNC**.
- "BudgeTrak" is the product name — never translated.

## Size discipline
Lockstep between `AppStrings`, `EnglishStrings`, `SpanishStrings` is enforced by the Kotlin compiler. `TranslationContext` isn't compiled, so drift is possible there. **Run the parity awk one-liner in `feedback_translation_context.md` before any commit that touches the strings system** — that's the only way to catch drift early.

## Drift history (what to expect / why the discipline matters)

- **2026-04-10 audit**: established that `TranslationContext.kt` was production quality at that point.
- **2026-05-09 audit + fix**: found that 27 days of feature work had introduced 33 missing context entries (mostly Settings archive + AI-OCR additions, Sync claim-vote / subscription-grace strings) plus 22 entries misfiled into the wrong `mapOf` (the `savingsGoals` ↔ `recurringExpenses` cluster) plus 8 stale orphans (target-date sub-fields removed when SG went single-type). Same audit found ~30 hardcoded UI strings in `MainActivity.kt`, `SettingsScreen.kt`, `TransactionsScreen.kt`, `BudgetCalendarScreen.kt`, `SimulationGraphScreen.kt`, `SwipeablePhotoRow.kt`, plus 3 `DialogHeader("Sync")` brand-rule violations. All fixed in the same /push.

The pattern: feature waves add fields without keeping `TranslationContext` lockstep, and accessibility/edge-case strings (toasts, contentDescription, debug flows) get hardcoded because they don't feel like "UI". Use the verification commands every time, not just on big audits.

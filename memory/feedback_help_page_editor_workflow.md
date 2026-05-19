---
name: Help-page editor workflow — .md round-trip
description: User-established pattern for editing in-app help screens via a markdown source file in Download/, instead of editing Kotlin directly.
type: feedback
---

The user prefers to edit help-page copy in a markdown source file rather than touching Kotlin.

**Why:** Direct Kotlin edits are slow and error-prone for prose. The four-file strings ritual (`AppStrings.kt` + `EnglishStrings.kt` + `SpanishStrings.kt` + `TranslationContext.kt`) is mechanical work the user shouldn't have to do. A markdown file is editable in any Android text editor or markdown previewer, and lets the user judge formatting at a glance.

**How to apply:**

1. **Export** a help screen with: read `<ScreenName>HelpScreen.kt` + the `<screenName>Help` block in `EnglishStrings.kt`. Write a markdown file to `/storage/emulated/0/Download/BudgeTrak/help-edits/<screen-name>-help.md` that mirrors the rendered layout: `#`/`##` for section/subsection titles, paragraphs for body text, `- ` bullets, blockquotes for callout boxes, `*italic*` where the Kotlin passes `italic = true`, `[STRUCTURE: …]` placeholders for in-code mockups (header bar, icon bar, pie charts), and `---` for `HelpDividerLine()`. Every editable chunk gets a `<!-- key: fieldName -->` HTML comment immediately before it, mapping to the string field — these are anchors, never edited by the user. `<!-- key-title: … -->` / `<!-- key-desc: … -->` for paired numbered-item title+description.

2. **The user edits** prose, possibly reorders/adds/deletes chunks, possibly adds `//` lines as inline notes/instructions to Claude. They may drop edits in a different folder (e.g. `Received from PC/Dads-Desktop/`).

3. **Incorporate** when asked: read the edited file, diff each chunk against the corresponding `EnglishStrings` field, patch the four strings files in lockstep, apply any structural changes (add/remove fields, reorder render calls in Kotlin, convert chunk types like body → bullet → icon row, etc.), follow `//` instructions, rebuild. Confirm with the user any ambiguous notes before applying.

4. **Orphan keys** when a section is removed: user has chosen Option A (delete entirely from all four files) over Option B (keep as orphan with a backward-compat TranslationContext note) in the only case faced so far. Default to A unless the user says otherwise.

5. **README** at `/storage/emulated/0/Download/BudgeTrak/help-edits/README.md` describes the workflow for the user's reference. Keep it in sync if conventions change.

Existing files in the directory:
- `dashboard-help.md` (consumed 2026-05-19)
- `colors-help.md` (created 2026-05-19, awaiting first edit pass)

When extending to a new help screen, follow the same template and add a README entry.

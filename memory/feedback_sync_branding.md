---
name: SYNC branding rules
description: How to write SYNC and BudgeTrak in user-facing strings, code symbols, and docs
type: feedback
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
The multi-device sync feature is branded **SYNC** (always all-caps, in both English and Spanish, treated as a brand mark like a logo). The full app title is **BudgeTrak SYNC**, but **BudgeTrak** alone is fine in most places (use the full title only at first mention in dashboard intro / welcome content).

**Why:** decided 2026-04-10 ahead of Play Store publication. The earlier "Family Sync" terminology was deemed too narrow (the feature works for any household — roommates, partners, families, multiple personal devices). Renaming to a brand-style mark also makes the feature more memorable and Play-Store-friendly.

**How to apply:**
- "Family Sync" / "family sync" terminology is **abrogated** — never use in user-facing strings, code comments, docs, or new help content
- "Sync" as a sentence-case word is fine when used as the verb ("syncs your data") or as part of generic phrases ("sync status", "sync icon"), but the **feature/page/button** is always uppercase **SYNC**
- "Family Group" / "family member" terminology is also retired in favor of "SYNC group" / "linked device" / "household member" — same Play Store inclusivity reasoning
- Field/file/symbol renames to match: `FamilySync*` → `Sync*`, `familySync` → `syncButton`, `familySyncDescription` → `syncDescription`, `familySyncHelp` → `syncHelp`, navigation routes `"family_sync"`/`"family_sync_help"` → `"sync"`/`"sync_help"`
- Spanish: do **not** translate "SYNC" — render as the all-caps brand mark in Spanish too

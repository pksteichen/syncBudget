---
name: Keep ALL memory documents current with every change
description: Always update spec files, help screens, and translations whenever fixing bugs, adding features, or changing behavior — stale docs cause agents to miss bugs and users to get wrong info
type: feedback
---

ALL memory documents, help screens, and translations must be updated whenever code changes affect documented behavior. This is not optional — stale docs directly caused the SG delete regression and diagnostic formula bug.

**Why:** Audit agents compare code against specs. Help screens guide users. Translations must match. If ANY of these are stale, agents validate against wrong assumptions, users get confused, and translators miss new strings.

**How to apply — update these files after EVERY relevant change:**

**Spec files (memory/):**
- `spec_budget_calculation.md` — BudgetCalculator changes (formulas, branches, rounding)
- `spec_sync_protocol.md` — SyncEngine, DeltaBuilder, CrdtMerge, LamportClock, rescue, repair
- `spec_data_model.md` — Data classes, fields, linking lifecycle, enums
- `spec_period_refresh.md` — Period detection, ledger creation, catch-up, recomputeCash triggers
- `spec_ui_architecture.md` — Screens, navigation, dashboard layout, widget
- `spec_csv_import.md` — CSV parsing, duplicate detection, auto-linking, matching rules
- `spec_group_management.md` — Group lifecycle, pairing, admin roles, transfer, dissolution
- `spec_receipt_photos.md` — Capture, compression, cloud sync, pruning
- `spec_transaction_flows.md` — Create/edit/delete, linking lifecycles, categorization

**Help screens (ui/screens/*HelpScreen.kt):**
- Update when UI changes affect user-facing behavior
- Update when new features are added that users need to know about
- Always update BOTH English and Spanish strings

**Translations (ui/strings/):**
- Add English + Spanish entries for every new user-facing string
- Add TranslationContext entry for every new string key
- Check that "BudgeTrak" brand name is preserved (not translated)

**After updating memory files:**
- Copy changed files to `.claude-memory/` in project root
- Update MEMORY.md index if new files were added

**When adding new subsystems:**
- Create a new spec document
- Add to MEMORY.md index
- Create help screen if user-facing

---
name: Help-text vs code audit method
description: When auditing help/docs against code, verify every specific claim against the actual source screen-by-screen — not against prior help text, and not by keyword search alone.
type: feedback
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
When auditing help pages, translation strings, specs, or any other doc that describes user-facing behavior, every concrete claim is a testable assertion. For each claim, find the exact code that implements it and confirm the claim is still true. Do not rely on the claim's survival through prior rounds of editing as evidence of correctness.

**Why:** The v2.5.x help audit round found six drifts that earlier rounds missed because prior audits compared help text to itself (or to general keyword searches) rather than tracing each claim to the underlying code. The encrypted-save removal shipped in commit 292c5e6 (2026-03-18), but `TransactionsHelpScreen.kt`, `EnglishStrings.kt`, `SpanishStrings.kt`, and `TranslationContext.kt` carried stale encryption content for weeks. A second, more rigorous pass caught additional drifts: `syncHelp` claimed attribution and date format were admin-synced when both are actually per-device; `recurringExpensesHelp` never mentioned Accelerated Set-Aside mode even though it's a toggle in the add/edit dialog with a row-level indicator; `budgetCalendarHelp` never documented the blue reset-day tint from commit 5903923. Each of these was a concrete claim (or a concrete omission) that would have been caught by reading the corresponding screen file.

**How to apply:**
- Before declaring a help section "clean", open the screen Kotlin file it documents. Confirm every widget/toggle/color described in the help actually exists in the screen code, and every visible UI element that a user would look up actually has help coverage.
- "Admin-controlled" vs "per-device" claims must be traced to persistence: check `SharedSettings` for shared fields and `SharedPreferences` for device-local ones, and check the write path (does `onChange` write to `prefs` or to `sharedSettings`?). Do not rely on section placement or naming as proof.
- When a feature is removed from the code, grep ALL help files plus `AppStrings.kt`, `EnglishStrings.kt`, `SpanishStrings.kt`, `TranslationContext.kt`, and the rendering `*HelpScreen.kt` for the feature keyword and any related strings before declaring removal complete. Prior removal commits (like 292c5e6) are good starting points for grep terms.
- Audit agents must be given explicit verification instructions ("verify every specific claim against the matching screen file — cite file:line"). A prompt that only asks them to read the help text will produce shallow results.
- If a finding is ambiguous, load the data flow yourself before applying a fix — the v2.5.x round had one audit finding ("accelerated mode missing from savingsGoalsHelp") that was wrong because the agent conflated the RE feature with savings goals. A trace of `BudgetCalculator.acceleratedREExtraDeductions()` showed it only touches recurring expenses, so the fix belonged in `recurringExpensesHelp`, not `savingsGoalsHelp`.

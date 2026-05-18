---
name: Always explain the idea before coding
description: For non-trivial changes — anything beyond a one-line tweak — describe the plan in plain language before touching files. The user can redirect, simplify, or reject before you've committed time + half-applied changes. Especially important when the request is ambiguous (e.g., "the price doesn't display") and the fix could be one of several things (load the data, add a fallback, log to diagnose, change the layout).
type: feedback
originSessionId: 5682369f-ce9a-4adb-978b-3517ce099586
---
For non-trivial changes — anything beyond a one-line tweak — describe the plan in plain language before touching files. The user can redirect, simplify, or reject before you've committed time + half-applied changes.

**Why:** Half-applied changes leave the codebase in a broken state (compile errors, partial features) that costs time to revert. More importantly, jumping straight into code denies the user the chance to redirect when the request was ambiguous or the wrong fix.

**How to apply:**
- One-line tweaks (text-size, padding, color swap): just do them.
- Anything that adds a field, changes a signature, restructures a flow, or chooses between several plausible interpretations: state the plan first in 2-4 sentences.
- If the user's request is ambiguous (e.g., "the price doesn't display" could be "load the data" / "add a fallback" / "fix a visibility bug" / "change the styling"), name your interpretation and the alternative before picking one.
- Confirmed 2026-05-15: user said "Always explain what your idea is before you start coding" after an in-house-ad price fallback half-implementation that broke the build and would have been the wrong fix anyway (debug builds simply don't have Play Console products under the .debug applicationId — user accepted that rather than wanting a fallback).

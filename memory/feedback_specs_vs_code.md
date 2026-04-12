---
name: When code disagrees with spec, assume spec is wrong first
description: Critical audit rule — existing working code likely reflects design decisions not captured in specs. Never "fix" code to match specs without understanding WHY the code does what it does. Ask the user.
type: feedback
---

When existing functionality disagrees with the spec, assume the SPEC is incomplete or wrong — not the code.

**Why:** The codebase reflects months of design discussions, edge case discoveries, and intentional trade-offs that specs may not fully capture. An audit agent that "fixes" working code to match an incomplete spec causes regressions (e.g., the SG delete revert where clearing remembered amounts broke availableCash).

**How to apply:**
1. When an audit finds code that doesn't match the spec, STOP before proposing a fix
2. Ask: "Why might the code intentionally do this differently?"
3. Look for comments, git history, related code paths, and similar patterns in other link types
4. If the reason isn't clear, ASK THE USER detailed questions like:
   - "The spec says X but the code does Y — was this a deliberate choice?"
   - "When a user deletes [entity], should linked transactions [behavior A] or [behavior B]? Here's the trade-off..."
   - "I found [code pattern]. The spec doesn't cover this case. What's the intended behavior?"
5. Only propose a code change after confirming the spec is correct and the code is wrong
6. Update the spec to match the code if the code is correct

**Never:** Silently change working code because it doesn't match a spec that was written by an agent from code analysis (not from the user's stated requirements).

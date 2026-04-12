---
name: Add debug logging early when debugging
description: When debugging a problem, immediately add step-by-step logging to the suspect function instead of theorizing from afar. One instrumented run gives more information than ten rounds of log-file analysis and guesswork.
type: feedback
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
When a function fails and the cause isn't obvious from existing logs, the FIRST thing to do is add step-by-step logging that marks each operation with "Step N: starting X" / "Step N: OK" / "Step N: failed: {message}". Build, have the user run it once, read the log. Done.

**Why:** The dissolve bug on 2026-04-12 took hours of theorizing about App Check enforcement propagation, Firestore security rule evaluation, offline cache behavior, and members collection query semantics — none of which was the actual cause. When we finally added step-by-step logging to `deleteGroup`, the very first instrumented run showed the function dying at "Step 2: deleting deltas" with no subsequent log output. That immediately identified the root cause: two legacy collection names (`deltas`, `snapshots`) with no matching security rule. The fix was removing two strings from a list.

We spent hours on theories (App Check propagation, enforcement toggling, members read rules, isMember() evaluation, Firestore cache behavior) that were all wrong. One instrumented build would have found the answer immediately.

**How to apply:**
- When a function fails and you can't identify the exact failing line from existing logs, don't guess. Add `log("Step N: description")` before and after each significant operation in the function, build, test, read the log.
- Use the existing `BudgeTrakApplication.tokenLog()` for logging — it writes to the token_log.txt file in the support directory, which survives process death and is included in dump output.
- Format: `functionName[identifier]: Step N: description` so multiple calls can be distinguished.
- Log both success ("Step N: OK") and failure ("Step N: failed: ${e.message}") for each step.
- Leave the logging in place after fixing the bug — it's written to a debug-only file and provides valuable diagnostic information for future issues. The dissolve step logging was kept in the final commit because it costs nothing and makes future dissolve debugging trivial.
- This applies to ANY multi-step function, not just dissolve: sync setup, backup restore, group creation, period refresh, migration flows. If it does more than 3 things in sequence, instrument it before guessing.

---
name: Memory edits must be committed in-session, not held for "next /push"
description: Memory files modified mid-conversation must be committed before the session ends — leaving them uncommitted means the knowledge dies with the session and doesn't reach future Claudes.
type: feedback
---

**Rule.** When you modify any file in `memory/` during a conversation, commit + push that change before the conversation ends. Don't assume "the next /push will sweep it up" — by the time the user starts a new session, the working tree's uncommitted memory edits are invisible to future-you, and the lesson the memory was supposed to teach is lost.

**Why:** confirmed pattern observed 2026-05-08 — the user pointed out that memories about CI dispatch were "slacking" because past sessions had likely updated memories but didn't push them. Same session also had an orphaned `project_sync_pending_edit_clobber.md` "Validated" edit sitting uncommitted until the user asked. Both cases prove the same failure mode.

**How to apply:**

1. **During /push:** if /push is the active task, sweep ALL uncommitted memory files into the commit (use `git add memory/` before the main `git add`s, or use `git add -A memory/`).

2. **After ad-hoc memory updates:** if you write or edit a memory file outside a /push context — at end of a research turn, after capturing user feedback, etc. — immediately do a follow-up `git add memory/<file> && git commit -m "memory: …" && git push origin dev`. Don't wait.

3. **Before ending a turn:** if the conversation feels like it might end (user said "thanks", "perfect", "ok", or seems wrapped), check `git status` for uncommitted memory files and push them.

4. **End-of-session check:** at the end of a long session, run `git status` and verify nothing in `memory/` is uncommitted.

**Don't bundle memory updates into the next code change** — they should ride alongside the trigger that prompted them, while the context for the commit message is fresh.

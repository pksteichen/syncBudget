---
name: Preserve existing fixes
description: When working on audit items, never undo previous bug fixes unless a rewrite solves both issues
type: feedback
---

When working through audit items, do NOT undo fixes already put in place to correct previous issues. Only replace a previous fix if a code rewrite can solve both the old and new issues together.

**Why:** Many subtle sync/CRDT fixes were made through careful debugging sessions (rescue loop, auto-leave, clock stamping, chunking, adaptive timeouts). Accidentally reverting these while fixing audit items would reintroduce production-critical bugs.

**How to apply:** Before editing any file that was modified in recent sync stability commits, read the current code carefully and understand WHY each piece exists before changing it. When in doubt, add to existing logic rather than replacing it.

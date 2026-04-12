---
name: Analyze all failure cases before implementing
description: When building or changing anything, proactively think through ALL possible failure/edge cases before presenting the solution — don't wait for the user to discover them
type: feedback
---

When implementing any change, proactively analyze ALL possible cases that could fail BEFORE presenting the solution. Don't implement the happy path and wait for the user to ask "what about X?"

**Why:** Multiple times the user had to prompt for edge cases that should have been caught upfront — e.g., "what if admin removes?" after only handling the self-removal path, or missing RTDB cleanup when only Firestore was updated. These are foreseeable cases that waste the user's time.

**How to apply:**
- For any state change (add/remove/update), trace ALL code paths that read or write that state
- For any new feature, enumerate: what if it's called twice? What if it races with sync? What if the process dies mid-operation? What if the other device does X?
- For cleanup/deletion: check EVERY store where the entity exists (Firestore, RTDB, Cloud Storage, local files, SharedPreferences, in-memory state) and ensure all are cleaned up
- For sync operations: consider both admin and non-admin paths, online and offline, foreground and background
- Present the analysis with the implementation, not after the user asks

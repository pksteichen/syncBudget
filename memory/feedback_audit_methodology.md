---
name: Audit methodology lesson
description: Audits must trace every write path end-to-end, not just search for missing imports or keywords. Dangerous defaults (empty list parameters) create silent failures.
type: feedback
---

When auditing code, don't just search for keywords or missing imports. Trace every data flow end-to-end: "user action → what functions are called → does the data reach its destination?"

**Why:** The per-record push migration left 58 save calls without Firestore pushes. The save functions had `changed: List<T> = emptyList()` — a dangerous default that made "don't push" the easy path. Audits searched for missing SyncWriteHelper calls but never checked if save() callers actually passed the changed parameter.

**How to apply:** When changing a fundamental pattern (like replacing markSyncDirty with per-record pushes), audit EVERY call site — not just the ones being actively worked on. Design APIs so the correct behavior is the default (pit of success, not pit of failure).

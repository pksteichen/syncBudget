---
name: Keep firebase-config-reference.txt updated
description: When changing Firebase rules, Cloud Functions, TTL policies, or App Check settings, update firebase-config-reference.txt to match
type: feedback
---

When making ANY change to Firebase configuration (Firestore rules, Storage rules, RTDB rules, Cloud Functions, TTL policies, App Check settings, indexes), update `firebase-config-reference.txt` in the project root to reflect the change.

**Why:** This file is the single source of truth when the Firebase console isn't available. If it drifts from reality, it becomes misleading and dangerous — someone could deploy stale rules from it.

**How to apply:** After any Firebase change, update the relevant section in the file with the new rules/config, change the "Verified" date, and update the audit summary checkmarks.

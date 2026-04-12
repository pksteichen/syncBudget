---
name: Wait for user decision before implementing
description: When presenting multiple options, wait for the user to choose before proceeding with implementation
type: feedback
---

When presenting options (e.g., "Option 1: TTL, Option 2: more frequent cleanup, Option 3: immediate delete"), STOP and wait for the user to pick one. Do not choose an option and start implementing it.

**Why:** User was presented with 3 options for tombstone cleanup, and without waiting for a decision, the assistant started implementing Option 1 (Firestore TTL with deleteAfter field).

**How to apply:** Present options concisely, then end the message. Do not continue with implementation until the user explicitly chooses.

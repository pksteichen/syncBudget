---
name: Repairs should be exceptional — investigate every one
description: Integrity repairs indicate a bug in the sync code. Every repair should trigger log investigation and root cause analysis. The goal is zero repairs in production.
type: feedback
---

Integrity repairs are NOT normal operation — they indicate a bug in the sync engine that allowed data to diverge.

**Why:** The CRDT sync system is designed so that normal operation (push, merge, echo prevention) keeps all devices perfectly converged. If a repair fires, something failed in the normal path. Treating repairs as routine masks bugs that will affect users at scale.

**How to apply:**
- The flashing magenta indicator alerts the user when repairs occur
- When magenta flashes: pull logs from both devices (Dump & Sync Debug button), investigate what caused the divergence, and fix the root cause
- Track the goal: zero repairs in steady-state operation
- Every repair should result in either a code fix or an explanation of why it was a one-time event (e.g., post-update migration)
- Never increase repair frequency or make repairs "smarter" as a substitute for fixing the underlying divergence cause
- The repair mechanism is a safety net, not a feature

---
name: Keep spec documents current
description: Always update spec files when fixing bugs, adding features, or changing algorithms — stale specs cause audit agents to miss real issues
type: feedback
---

Spec documents in memory/ must be updated whenever code changes affect the documented behavior.

**Why:** Audit agents compare code against specs. If specs are stale, agents validate code against outdated assumptions and miss real bugs (e.g., the diagnostic formula bug was invisible because no spec documented the correct formula).

**How to apply:**
- After any change to BudgetCalculator, update `spec_budget_calculation.md`
- After any change to SyncEngine/DeltaBuilder/CrdtMerge/LamportClock, update `spec_sync_protocol.md`
- After any change to data classes or linking logic, update `spec_data_model.md`
- After any change to period refresh or ledger logic, update `spec_period_refresh.md`
- After updating memory files, copy to `.claude-memory/` in project root
- When adding new subsystems, create new spec documents and index in MEMORY.md

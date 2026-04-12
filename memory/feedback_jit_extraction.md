---
name: JIT extraction lambda overhead
description: Extracting Compose screen branches with many state-setter lambdas can increase parent instruction count — prefer ViewModel over wrapper functions
type: feedback
---

Extracting @Composable functions with many callback/setter lambda parameters can make the ART JIT problem WORSE, not better.

**Why:** Each `onSetFoo = { foo = it }` parameter creates a lambda object in the parent method's DEX bytecode (~3-5 instructions per lambda). For a branch like BudgetConfigScreen with 18 setter lambdas, the call site adds ~90 instructions of lambda overhead — which can exceed the instruction savings from moving the inline logic out.

**How to apply:**
- Pure function extraction (no lambda params needed) is always safe and effective
- Dialog extraction works well because dialogs are guarded by `if` blocks that ART can skip
- Screen branch extraction with many state setters has diminishing returns
- The proper solution for a large setContent body is a ViewModel (moves state + logic out of the composable entirely, zero lambda overhead)
- When extracting, count the number of lambda parameters. If > 10, the extraction may not help.

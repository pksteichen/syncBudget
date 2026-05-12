---
name: Dialog content lambdas must close over captured values, not read gating state directly
description: Dialogs gated by `if (state != null) { ... }` are crash-vulnerable if their content lambdas read the same state via `!!` — the new in-tree overlay host can re-invoke the lambda one frame after dismiss but before the entry is removed. Use `state?.let { value -> ... }` instead to capture a non-null local val.
type: feedback
---

When a dialog is gated by a nullable state, prefer:

```kotlin
selectedDate?.let { date ->
    AdAwareAlertDialog(
        title = { Text("Day: ${date.dayOfMonth}") },  // closure-stable
        ...
    )
}
```

Over:

```kotlin
if (selectedDate != null) {
    AdAwareAlertDialog(
        title = { Text("Day: ${selectedDate!!.dayOfMonth}") },  // crashes on dismiss
        ...
    )
}
```

**Why:** AdAwareDialog is now an in-tree overlay (Theme.kt). It registers an entry in `AdAwareDialogState.activeDialogs` via `DisposableEffect`. The host iterates that list and invokes each entry's `content()` lambda. When the gating state goes null on dismiss:

1. Recomposition fires.
2. The caller's `if (state != null)` evaluates false → AdAwareDialog leaves composition.
3. AdAwareDialog's `DisposableEffect.onDispose` is *scheduled* — but it doesn't fire until the **Apply phase** of the current composition (i.e., one frame later).
4. During the *same* recomposition pass, the host re-renders. The entry is still in `activeDialogs` because removal hasn't applied yet.
5. The host invokes `entry.content()` one more time.
6. The content lambda reads `state!!` — but `state` is already null. **NPE crash.**

The `?.let { value -> ... }` form captures `value` as a non-null Kotlin local at composition time. Content lambdas close over `value` (a stable reference to the captured snapshot), not the live state. When the host invokes the lambda after dismiss, `value` is still the original non-null Date/Entry/whatever — no crash.

**How to apply:**

- Anywhere a dialog is gated by nullable state, prefer `state?.let { value -> AdAwareDialog/AdAwareAlertDialog(...) }`.
- If you must use `if (state != null) { ... }`, capture into a local val **outside** the dialog call: `val captured = state!!; AdAwareDialog(...)` then reference `captured` (not `state`) inside lambdas.
- `!!` is still safe inside `onClick` callbacks (e.g., `confirmButton = { Button(onClick = { state!!.doThing() }) }`) — those fire only on user button taps while state is still non-null.
- `!!` is also safe as a parameter expression evaluated at call time (e.g., `DialogX(transaction = state!!, ...)`) — synchronous read, value captured.

**History:** Discovered when migrating from Compose `Dialog` (separate Android window) to in-tree overlay (2026-05-11, commits `730dff2`/`dd8f922`). The calendar day-detail dialog used `if (selectedDate != null) { ... selectedDate!! ... }` and crashed on dismiss after the refactor. Fixed in `6bcf11d`. Audit of every other AdAware call site (~14 dialogs across Amortization/RecurringExpenses/SavingsGoals/BudgetConfig/Settings/Sync) found all others already used `state?.let { value -> ... }` — calendar was the lone outlier.

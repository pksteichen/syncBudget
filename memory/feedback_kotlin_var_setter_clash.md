---
name: Don't define `setX` / `getX` Kotlin functions when a `var x` exists in the same class
description: Kotlin auto-generates JVM `setX` / `getX` accessors for every `var x`. A hand-written `fun setX(...)` (or `getX()`) with matching parameter types clashes at compile time with "Platform declaration clash: same JVM signature". Pick a different verb.
type: feedback
---

**Rule.** When a class has `var x: T`, Kotlin auto-generates `setX(T)` and `getX()` JVM methods. A manual `fun setX(value: T)` in the same class (regardless of `private`/`public`/access modifier) collides with that synthetic setter and fails compilation.

**Symptom:**
```
Platform declaration clash: The following declarations have the same JVM signature (setX(Z)V):
   fun <set-x>(<set-?>: Boolean): Unit
   fun setX(enabled: Boolean): Unit
```

**Why:** access modifiers don't change JVM method signatures — only name + parameter types do. The synthetic accessor exists at the bytecode level even when declared `private set`.

**How to apply:**
- Pick a different verb for any helper that wraps a `var` setter: `updateX`, `applyX`, `commitX`, `setXAndPersist`, etc.
- Or use a `val` + `MutableState`/backing field if the property only changes through the helper (no public setter at all).
- This is a Kotlin-specific gotcha — Java callers don't see it because Java has no property syntax. Hits when adding helper methods around state that needs side effects (persistence, events).

**Verified:** 2026-05-08 in `MainViewModel.updateBillingOverride` — original `fun setBillingOverrideEnabled(enabled: Boolean)` clashed with the auto-generated setter for `var billingOverrideEnabled`. Renamed to `updateBillingOverride` and the build went green.

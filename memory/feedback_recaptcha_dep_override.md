---
name: reCAPTCHA Enterprise transitive dep is pinned to 18.4.0+ via direct override
description: Firebase BOM 32.7.0 pulls reCAPTCHA Enterprise 18.1.2 transitively through firebase-auth, which Play Console flags as deprecated + critical-security vuln. We override to 18.4.0 directly. When bumping Firebase BOM, check if the override is still needed; remove if the new BOM bundles 18.4.0+ already.
type: feedback
---

**Override line (in `app/build.gradle.kts`, after `firebase-auth`):**
```kotlin
implementation("com.google.android.recaptcha:recaptcha:18.4.0")
```

**Why it's there:**
Play Console review check flags `com.google.android.recaptcha:recaptcha:18.1.2` (the version Firebase BOM 32.7.0's `firebase-auth` pulls in) as deprecated + having a critical security vulnerability. Notice received 2026-05-08 against versionCode 24. Minimum acceptable version is 18.4.0; we pin to that for minimal risk.

**Why a direct override and not a Firebase BOM bump:**
- Firebase BOM 33+ requires compileSdk 35 unconditionally.
- BudgeTrak's bifurcated build path (compileSdk 34 in Termux for local debug builds, 35 in CI for releases) means a BOM bump that demands 35 would break the Termux debug-build flow we use multiple times per day.
- Per `MEMORY.md`: "Do NOT bump core-ktx ≥ 1.15 or Compose BOM ≥ 2024.12.01 — both require compileSdk 35 unconditionally and would break the Termux debug-build path." Firebase BOM 33+ falls in the same category.
- Gradle picks the highest version when there's a conflict — our 18.4.0 wins over the BOM's 18.1.2 transitively.

**When to remove the override:**
- Once the Termux compileSdk 34 constraint relaxes (better aapt2 ships, or we move to the conditional `localTermux` path), bump Firebase BOM and check whether reCAPTCHA 18.4.0+ is bundled by default.
- If yes, drop the explicit `implementation("com.google.android.recaptcha:recaptcha:18.4.0")` line.

**Note on Firebase Auth API compatibility:**
We don't call reCAPTCHA APIs directly — `firebase-auth` does, internally. As long as the override version (18.4.0+) keeps the public surface that `firebase-auth` 22.x-bundled-with-BOM-32.7.0 expects, no behavior change. Verified working 2026-05-08 in v2.10.11 builds.

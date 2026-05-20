---
name: Play Console "deprecated APIs" warnings are bytecode static analysis — fix the library version
description: Why overriding deprecated APIs in your own code doesn't silence the Play Console warning, and the bifurcation pattern for libraries that need a newer compileSdk than Termux supports.
type: feedback
---

## The rule

Google Play Console's "Your app uses deprecated APIs or parameters" warnings come from **static bytecode analysis of the uploaded AAB**, not runtime behavior. The analyzer scans every `.class` file (including those inside library AARs) and flags any reference to a deprecated symbol.

Implication: **you cannot silence the warning by overriding the deprecated API in your own code**. The library's bytecode still contains the deprecated reference. Only fix is upgrading the library to a version that emits the new constant or API call.

## How to read Play Console's "Read more" detail

The warning includes:
- The specific deprecated constants/methods (e.g. `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`)
- Call sites in **obfuscated class names** (e.g. `I2.b0.onApplyWindowInsets`)

The obfuscated names are R8/ProGuard-shortened. They almost always point to a library dependency, not your own code. Confirm with:

```bash
# Grep for the constants in your own source — should be zero hits
grep -rn "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES" app/src/main
# If zero hits, the call is in a library. Find which one by checking
# recent changelog entries for deprecation cleanup on the dependencies
# you use for edge-to-edge / window handling.
```

## The Termux bifurcation pattern when the fix requires compileSdk 35+

Often the library version that drops the deprecated call also bumps its **minimum required compileSdk**. Example: `androidx.activity:activity-compose:1.10+` switched from `SHORT_EDGES` to `ALWAYS` but requires compileSdk 35, which breaks Termux's aapt2 v2.19 (can only parse android-34 resources).

Solution: bifurcate by reusing the existing `localTermux` flag in `app/build.gradle.kts`:

```kotlin
val localTermux = project.hasProperty("localTermux")
val sdkVersion = if (localTermux) 34 else 35
val activityComposeVersion = if (localTermux) "1.9.3" else "1.10.1"

dependencies {
    implementation("androidx.activity:activity-compose:$activityComposeVersion")
}
```

- Local Termux debug builds: stay on the older version that compiles at 34
- CI release builds: use the newer version that emits the modern API in the AAB
- Play Console's static analyzer only sees the CI-produced AAB → warning clears

The runtime behavior is equivalent at minSdk 28 for any sane patch-version diff; the only thing that matters is what bytecode ships.

## How to apply

- When Play Console surfaces a "deprecated APIs" warning, click "Read more" to get the specific symbol(s).
- Grep your source for the symbol. If absent, it's a library.
- Check the deprecation cleanup in your library's recent changelogs to find the version that drops the call.
- If that version requires a higher compileSdk than Termux supports, use the bifurcation pattern above. Don't waste time overriding the API in app code — static analysis still flags the library reference.

## Related

- `MEMORY.md` build environment section — Termux compileSdk 34 vs CI 35
- v2.10.30 release (2026-05-19) — first time we applied this pattern; cleared two simultaneous warnings (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES/NEVER` and "edge-to-edge may not display for all users").

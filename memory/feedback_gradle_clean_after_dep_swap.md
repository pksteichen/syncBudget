---
name: Run gradle clean after swapping/removing dependencies
description: On this BudgeTrak project, Gradle's incremental build leaves stale DEX files from removed dependencies in the APK, causing runtime crashes. Always `./gradlew clean assembleDebug` after any dep change.
type: feedback
originSessionId: a00b436a-3ced-4e78-a40e-780a8f5acff8
---
When changing Android dependencies on BudgeTrak (adding, removing, or swapping a library), always run `./gradlew clean assembleDebug` instead of plain `assembleDebug`. Gradle's incremental build keeps stale DEX files from the previous dep in the APK even after the dep is removed from `build.gradle.kts`.

**Why:** Hit this debugging an OCR feature — kept getting startup crashes on a supposedly stubbed APK. Turned out prior experiments with `firebase-vertexai` (incompatible with the BOM in use) left their DEX classes in the project dex archive. Incremental builds reused those classes; the app loaded them at startup and crashed against a mismatched Firebase BOM. Clean build dropped ~3 MB of stale DEX and fixed the crash instantly.

**How to apply:**
- Any time `app/build.gradle.kts` dependencies change (add/remove/version bump), run `./gradlew clean assembleDebug`.
- If an APK crashes on startup after a dep experiment, suspect stale DEX before spending time on code bisection.
- Compare APK sizes — a suspiciously large APK vs expected is a sign of stale artifacts.
- **`./gradlew bundleRelease` also resets the dex cache as a side effect** (R8 builds a fresh dependency graph from scratch). Observed 2026-04-28: a debug APK had grown to 123 MB from accumulated stale DEX; running a release bundle invalidated the cache, and subsequent `assembleDebug` builds dropped to ~85 MB (the actual size). If the user reports an unexpected size *drop*, this is usually why — not a regression.

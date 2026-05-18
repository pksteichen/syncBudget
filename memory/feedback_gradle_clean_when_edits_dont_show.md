---
name: Gradle clean build when on-disk edits don't appear in the APK
description: When a resource/layout/Kotlin edit is correct on disk but the installed APK still renders pre-edit behavior despite a successful `./gradlew assembleDebug`, run `./gradlew clean assembleDebug` before debugging at runtime. Gradle's incremental task graph occasionally reuses stale `app/build/intermediates/` artifacts (most often processed-resources / AAPT2 outputs) across many small edits.
type: feedback
originSessionId: 5682369f-ce9a-4adb-978b-3517ce099586
---
When a resource, layout XML, or Kotlin edit is correct on disk but the installed APK still renders the pre-edit behavior, run `./gradlew clean assembleDebug` *before* spending time debugging the runtime.

**Why:** Gradle's incremental build occasionally reuses stale `app/build/intermediates/` artifacts (most often processed-resources / AAPT2 outputs, sometimes Kotlin compiled classes) across long edit sessions. The build reports `BUILD SUCCESSFUL` because every task ran (or was up-to-date), but the cached intermediates aren't actually consistent with the current sources. `versionCode` is no help — both builds came from the same source tree, only the compiled output differed.

**How to apply:** Symptoms to look for before reaching for runtime debugging:
- XML / dimens / Kotlin edits visible in source but absent in the APK.
- APK size unchanged across edits that should have changed it. (Or after clean, size drops noticeably — that's confirmation stale intermediates were duplicated in the previous outputs.)
- `versionCode` matches the latest source but visuals match an older state.

Confirmed instance 2026-05-15: ad layout XML restructure + dimens bumps were on disk but the APK rendered pre-edit (centered headline, smaller icon + CTA). `assembleDebug` reported success. `clean assembleDebug` regenerated everything and the APK dropped 95MB → 91MB, with visuals matching source.

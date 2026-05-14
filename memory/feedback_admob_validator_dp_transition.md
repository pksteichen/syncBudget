---
name: AdMob validator "too small" warning persists on low→high dp tier flip
description: The AdMob native ad validator UI keeps a "MediaView too small for video" warning across a runtime tier change (e.g., app opens at <400dp small template → user widens to ≥400dp medium template). The app-side state is already correct; the persistence is internal to the validator UI tool. Killing/restarting the app clears it. Don't chase further code fixes.
type: feedback
---

**Rule.** If the user reports the AdMob native ad validator showing "MediaView too small for video" after a runtime dp tier flip (small → medium, e.g., foldable open, Settings → Display Size, `adb shell wm density`), do NOT treat this as an app bug. The fix is to close + reopen the validator UI, or restart the app. Production ad serving is unaffected.

**Why:** as of 2026-05-14 the app already does all three things needed on a tier flip:
1. `widthDp` from `LocalConfiguration.current.screenWidthDp` (reactive, not `remember {}`) — recomposes on dp/density/orientation/foldable changes.
2. `androidx.compose.runtime.key(isMediumTier) { AndroidView(...) }` — forces fresh inflation of the matching layout XML (small vs medium template) when the tier flips.
3. `DisposableEffect(isMediumTier) { onDispose { nativeAd?.destroy(); nativeAd = null; adMobFailed = false } }` — destroys the previously-loaded NativeAd immediately on tier change so the new AndroidView's `update` lambda sees `nativeAd == null` and skips `setNativeAd`. The re-keyed `LaunchedEffect(nativeAdEnabled, isMediumTier, isAppActiveCompose)` then loads a fresh ad against the new MediaView dimensions.

After all three, the NEW NativeAdView at the new tier carries the correct dimensions and a freshly-bound ad. The remaining stale "too small" warning lives only in the validator UI's own session cache. Verified empirically: killing + restarting the app produces "no issues found" on the next validator run.

**How to apply:** when the user reports this warning after a dp transition, respond by:
- Confirming the three runtime defenses are in place (they should be — they shipped together).
- Pointing the user to the validator's own restart as the resolution.
- NOT making further code changes — additional cleanup attempts will not affect what the validator UI has already captured.

**Why production isn't affected:** end users never see the validator UI. The actual `MediaView` rendered by the app post-flip is the correct ≥120×120dp size, and AdMob's ad-serving infrastructure (separate from the validator UI) evaluates the live view dimensions per request. The warning is purely a developer-facing artifact of the validator tool.

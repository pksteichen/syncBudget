---
name: AdMob banner letterboxing on tablets is fundamentally unfixable from native code — don't re-try the experiments
description: A full session 2026-05-09 was spent trying to eliminate the black letterboxes that appear inside the AdView when AdMob serves a creative narrower than the requested slot on tablets. Every approach failed. Document the dead-end so future-Claude doesn't repeat the work.
type: feedback
---

**Rule.** When tablet letterboxing comes up again, do not iterate. The current state (anchored adaptive banner, AdView with `setBackgroundColor(topBarColor.toArgb())`, AndroidView at `fillMaxWidth().height(adBannerHeight)`) is the best achievable visual without significant trade-offs. The only remaining options are accepting the visual or hiding ads on tablets entirely — both with non-trivial costs.

## What was tried (all failed or had unacceptable trade-offs)

1. **Tier-based standard sizes** (`AdSize.LEADERBOARD` / `AdSize.FULL_BANNER` / adaptive). Goal: no internal letterbox since SDK only serves matching creatives. Result: AndroidView's default sizing **stretched the AdView to fill the parent Box constraints** instead of constraining it to the AdSize, so the AdView occupied the full screen width and any sub-728 served creative still letterboxed inside. Internal letterbox still visible as black.

2. **Custom AdSize matching device width** (`AdSize(widthDp, scaledHeight)` with aspect ratio preserved from the largest standard banner). Goal: SDK upscales served creatives to fill the slot. Result: SDK didn't reliably upscale beyond ~1.1x even when aspect ratios matched. Same internal letterboxing.

3. **WebView tinting via `onAdLoaded` callback.** Walked the AdView's view hierarchy in `onAdLoaded`, found the WebView child, called `setBackgroundColor(topBarColor.toArgb())` on it. Goal: color the area inside the AdView that the WebView paints. Result: most test ads have explicit `<body style="background:black">` in their HTML, which paints over our WebView background. Worked for the small subset of creatives without explicit body backgrounds.

4. **Box wrapper with explicit AndroidView size modifier.** Wrapped AndroidView in `Box(modifier = Modifier.fillMaxWidth().height(adBannerHeight), contentAlignment = Center)` with `Modifier.width(adSize.width.dp).height(adSize.height.dp)` on the AndroidView. Goal: force AdView to its declared AdSize, exposing the parent Column's `topBarColor` outside it. Result: the explicit modifier worked — sides became `topBarColor` correctly — but **`AdSize.LEADERBOARD` requests on the test ad unit returned no-fill on tablet emulator**, so the ad bar showed only the topBarColor sides with no ad in the middle. Reverted entirely.

5. **Dynamic-height "morphing bar"** (resize AdView post-load to served creative's actual size). Goal: read served dimensions in `onAdLoaded` and resize wrapper Box. Result: **AdMob's SDK does not expose served creative dimensions.** `AdView.measuredWidth/Height` returns the requested slot size. `AdView.responseInfo` exposes mediation metadata but no creative geometry. WebView measurement returns the WebView's fill-the-AdView size, not the creative inside. Inline Adaptive Banner has the same fixed-at-request-time limitation despite "variable height" marketing. **No public API surfaces the served creative's natural dimensions.**

## Why letterboxing happens

- AdMob's auction returns creatives at standard IAB sizes (320×50, 320×100, 468×60, 728×90, 300×250). When the requested slot is wider than the served creative, the SDK centers the creative and fills the leftover area with the WebView's background — typically opaque black or whatever the creative's HTML body specifies.
- The SDK upscales smaller creatives to fit the slot only up to ~1.5x scale (empirical, undocumented). 320→480 fits, 320→728 doesn't. So tablets with adaptive 800-wide slots receive 320×50 creatives at native 320×50 with ~240dp horizontal letterbox each side.
- The opaque black painting comes from one of two sources: the WebView's default View background (we tinted this), OR the creative HTML's `<body>` background-color (we can't override without violating AdMob TOS).

## What's possible vs not

| Approach | Possible? | Trade-off |
|---|---|---|
| Tint AdView background color | ✓ | Helps some creatives, doesn't help opaque-body creatives |
| Tint inner WebView background color | ✓ but limited | Same — body bg overrides |
| Force AdView to exactly AdSize via explicit modifier | ✓ | Works but lower fill rate for fixed sizes on test inventory |
| Resize AdView dynamically to served creative | ✗ | SDK doesn't expose served creative dimensions |
| Inject CSS to override creative body background | ✗ | AdMob TOS violation |
| Hide ads on tablets only | ✓ | Lose tablet ad revenue (small impact since tablet usage is small) |

## Production reality check

Most of the letterboxing problem is **a test-ad-unit artifact**. Real production advertisers tend to:
- Provide creatives at multiple sizes (so the auction has size matches)
- Style their creatives without opaque black backgrounds
- Use AdMob's adaptive ad creative tooling that auto-fits

The visual badness in test mode is worse than what users will see in production. Worth re-evaluating tablet letterboxes once the production ad unit ID is wired and real-advertiser inventory is serving.

## Final recommendation if it comes up again

1. Don't iterate on the AdView/AndroidView/WebView/Box wrappers — none of those approaches work.
2. Test with the production ad unit ID before declaring the visual unacceptable.
3. If it's still bad in production, the only viable response is to hide ads on tablets via `widthDp >= 600 -> null` in the adSize selection — accept the small ad-revenue loss.

## Known good state (what shipped in v2.10.15)

```kotlin
val adSize: AdSize? = remember(vm.isPaidUser, vm.isSubscriber) {
    if (vm.isPaidUser || vm.isSubscriber) null
    else {
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, widthDp)
    }
}
// AdView: setBackgroundColor(topBarColor.toArgb()) only — no WebView walking, no listener
// AndroidView: Modifier.fillMaxWidth().height(adBannerHeight) — no Box wrapper, no explicit size
```

Tablets letterbox black. Phones look fine. **This is the baseline. Don't re-architect.**

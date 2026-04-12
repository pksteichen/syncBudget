---
name: Ad banner implementation architecture
description: How the ad banner integrates with the UI — placement, viewability, lifecycle, and key implementation notes for when real AdView replaces the placeholder
type: project
---

## Current State
- **Placeholder**: 320x50 black Box with gray text at top of main Column, above screen content
- **Gate**: `if (!vm.isPaidUser)` — hidden for paid/subscriber users
- **Position**: Inside `Column(fillMaxSize().statusBarsPadding())`, ABOVE the `Box(weight(1f))` screen router

## Why This Architecture Maximizes Ad Revenue

**Ad stays visible across ALL page transitions:**
- The ad Box is OUTSIDE the `when (vm.currentScreen)` block
- Page navigation only recomposes the `weight(1f)` Box below
- Ad SDK sees continuous, uninterrupted visibility
- No "lost to view" events — impressions count correctly

**IAB MRC viewability standard**: 50% of pixels visible for 1 continuous second. Our banner: 100% visible, continuously, across all navigation.

**Dialogs/popups don't cover the ad**: All use `AdAwareDialog` which offsets content below the ad banner via `LocalAdBannerHeight` context + `padding(top = adPadding)`.

## When Replacing Placeholder with Real AdView

1. **Use `AndroidView` with `remember`ed factory** — don't recreate AdView on recomposition:
   ```kotlin
   AndroidView(
       factory = { ctx ->
           AdView(ctx).apply {
               setAdSize(AdSize.BANNER)
               adUnitId = "unit-id"
               loadAd(AdRequest.Builder().build())
           }
       },
       modifier = Modifier.fillMaxWidth().height(50.dp)
   )
   ```
2. **Lifecycle management**: Wire AdView.resume()/pause()/destroy() to Activity lifecycle via DisposableEffect
3. **Don't conditionally remove AdView mid-session** — `isPaidUser` gate is fine since it doesn't toggle during use
4. **Test ad unit IDs**: Use Google test IDs during development to avoid policy violations

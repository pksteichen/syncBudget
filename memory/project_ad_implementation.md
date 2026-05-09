---
name: Ad banner implementation — shipped v2.10.09
description: AdMob banner integration in BudgeTrak — adaptive sizing, cutout/decorative-strip handling, AdView background tinting, status-bar appearance override, manifest-merger workaround. Production-promotion swap checklist included.
type: project
---

## Status
**Shipped in v2.10.09** (2026-05-08). Replaced the placeholder `Box(Color.Black)` with a real AdMob `AdView`. Currently using Google's TEST app + ad-unit IDs — production-promotion swap checklist below.

## Architecture (still valid, augmented)

The placement intent from the original design carries forward:
- **Position:** outside the `when (vm.currentScreen)` block, inside the outer `Column` in `MainActivity.setContent`. Page navigation only recomposes the `weight(1f)` Box below; the AdView never recomposes between screens — IAB MRC viewability counts continuously.
- **Gate:** `if (adSize != null)` — `adSize` is null when `vm.isPaidUser || vm.isSubscriber`, so paid + subscriber tiers stay ad-free with no slot height (`adBannerHeight = 0.dp`). The `remember` key is `(vm.isPaidUser, vm.isSubscriber)` so the slot recomputes on either tier change. **Both flags must be checked** — Subscriber is a superset of Paid; both should be ad-free. Bug fixed in v2.10.15 (was previously `remember(vm.isPaidUser)` and only `if (vm.isPaidUser) null`, which surfaced ads to Subscribers when Paid was independently false).
- **AdAwareDialog offsetting:** unchanged. `LocalAdBannerHeight` flows the runtime banner height to dialogs that push their content below it.

## What ships

**1. Dependency:** `com.google.android.gms:play-services-ads:23.6.0` in `app/build.gradle.kts`.

**2. Manifest (`AndroidManifest.xml`):**
- `xmlns:tools="http://schemas.android.com/tools"` added to `<manifest>`.
- AdMob `APPLICATION_ID` meta-data inside `<application>` — currently the TEST app ID `ca-app-pub-3940256099942544~3347511713`.
- `<property android:name="android.adservices.AD_SERVICES_CONFIG" android:resource="@xml/gma_ad_services_config" tools:replace="android:resource" />` — required because both `play-services-ads-lite` and Firebase's `play-services-measurement-api` declare the same property pointing to different XML configs. AdMob's wins. See `feedback_admob_manifest_merger.md`.

**3. SDK init:** `MobileAds.initialize(this) {}` near the top of `BudgeTrakApplication.onCreate` — async, safe to call before any Activity.

**4. AdView creation in `MainActivity.setContent`:**
- `val topBarColor = LocalSyncBudgetColors.current.headerBackground` — theme-aware.
- **Per-device-tier sizing** (selected by `widthDp = displayMetrics.widthPixels / density`):
  - `widthDp > 550` → `AdSize.LEADERBOARD` (728×90); slot height = `widthDp / 8.1f`
  - `widthDp > 400` → `AdSize.FULL_BANNER` (468×60); slot height = `widthDp / 7.8f`
  - `widthDp ≤ 400` → `getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)`; slot height = `adSize.height.dp`
- Tablet tiers pin to a standard IAB size and size the slot to that ratio so a matching served creative scales to fill (no internal letterbox). The phone tier uses anchored adaptive — the modern standard, validated as the right default for phones.
- `val adView = remember(adSize, topBarColor) { AdView(activity).apply { setAdSize(adSize); adUnitId = "<TEST>"; setBackgroundColor(topBarColor.toArgb()); loadAd(...) } }`.
- `DisposableEffect` wires `resume/pause/destroy` to the Activity lifecycle and calls `destroy()` on dispose.
- `AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().height(adBannerHeight))`.

**5. Cutout + decorative top strip:**
- Outer Column uses `Modifier.fillMaxSize().background(topBarColor).windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))` — pads for whichever of status bar / cutout is taller (no double-pad), and the background fills the inset area with the theme's header color so the cutout space looks like an intentional decorative strip rather than a naked transparent zone.

**6. Status bar icon appearance:** `WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false` immediately after `enableEdgeToEdge()` in `onCreate`. Forces white status-bar icons in both light and dark mode — `headerBackground` is dark enough in both themes that the OS default (black icons in light mode) is unreadable. Without this, the icons only flip to white when a dim overlay (dialog) ever-so-slightly tips the auto-contrast.

**7. AdView background tint:** `setBackgroundColor(topBarColor.toArgb())` on the AdView itself. Trades the general "leave AdView transparent for themed-creative blending" best practice against the more-common case where the served creative letterboxes inside the adaptive slot or has hard borders — those leftover bars now show `topBarColor` instead of black, which blends with the decorative top strip. Themed creatives that draw their own opaque background still render normally on top; the override only affects letterbox/transparent areas.

## Production-promotion swap checklist

When promoting from internal/closed to production, do all four:

1. **AdMob Console:** create the production app + a Banner ad unit. Copy the resulting `ca-app-pub-XXXXXXXX~XXXXXXX` (app ID) and `ca-app-pub-XXXXXXXX/XXXXXXX` (ad unit ID).
2. **`AndroidManifest.xml`:** replace the TEST `APPLICATION_ID` meta-data value with the real app ID.
3. **`MainActivity.kt`:** replace the TEST `adUnitId` string in the `AdView.apply { }` block with the real ad unit ID.
4. **App-ads.txt:** publish at `https://techadvantagesupport.github.io/app-ads.txt` (Pages repo) per AdMob's app-ads.txt verification flow. Without this, AdMob serves house ads only on production for ~24h until the file resolves.

Optional but recommended pre-production:
- Enable `MobileAds.openAdInspector(this) {}` from a debug-only menu item to verify served-creative diagnostics on real devices.
- Add Paul + Kim deviceIDs to AdMob test devices via `RequestConfiguration.Builder().setTestDeviceIds(...)` so live debugging never accrues impressions / risks invalid-traffic flags.

## What does NOT need to change for production

- Adaptive banner sizing logic — same call works against real ad units.
- Lifecycle wiring (`DisposableEffect` + `resume/pause/destroy`) — production-grade as written.
- `LocalAdBannerHeight` plumbing for `AdAwareDialog` — unchanged.
- Manifest-merger override on `AD_SERVICES_CONFIG` — same conflict, same fix.

## Anti-goals (do NOT do these)

- **Don't** bring back fixed `AdSize.BANNER` (320×50) — looks narrow on modern phones; adaptive is the modern standard and what the user validated.
- **Don't** remove `setBackgroundColor` on the AdView — the user explicitly accepted the tradeoff (a small set of edge-case themed creatives might be tinted) against the more-visible black-letterbox issue.
- **Don't** wire `isAppearanceLightStatusBars` to follow the system theme — the override is intentionally always-white because both light- and dark-mode `headerBackground` colors are dark enough to demand light icons.
- **Don't** add a `displayCutoutPadding()` call separately on the Column — `windowInsetsPadding(statusBars.union(displayCutout))` already does this without double-pad on devices where status bar already includes the cutout.

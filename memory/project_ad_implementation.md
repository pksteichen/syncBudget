---
name: Native ad implementation — replaced banner in v2.10.16
description: AdMob Native Advanced ad integration in BudgeTrak — small/medium template tier on widthDp ≥480, custom layout XMLs, 60 s refresh timer with lifecycle gating, manifest-merger workaround. Production-promotion swap checklist included. History of preceding banner iterations is in git log; this file describes the current native architecture.
type: project
---

## Status
**Native ads in v2.10.16** (2026-05-09). Replaced the banner `AdView` with AdMob Native Advanced ads — adaptive banners couldn't deliver letterbox-free creatives at custom aspect ratios (the `AdSize` API doesn't filter delivery to fill-the-slot creatives only). Native ads sidestep the issue entirely: we render the layout, AdMob delivers asset data (headline / icon / image / CTA / advertiser / body), and there's no slot-vs-creative dimension mismatch. CPMs typically run 2-5× banner.

Currently using Google's TEST native ad unit ID `ca-app-pub-3940256099942544/2247696110` — production-promotion swap checklist below.

## Architecture

- **Position:** outside the `when (vm.currentScreen)` block, inside the outer `Column` in `MainActivity.setContent`. Page navigation only recomposes the `weight(1f)` Box below; the AdView never recomposes between screens.
- **Tier select** (portrait-locked, so `widthDp` is fixed per session):
  - `widthDp < 400` → `R.layout.native_ad_small` — single-row card, 64 dp slot
  - `widthDp ≥ 400` → `R.layout.native_ad_medium` — horizontal card (text column left + 160×120 MediaView right), 144 dp slot
- **Gate:** `nativeAdEnabled = !vm.isPaidUser && !vm.isSubscriber`. Paid + subscriber tiers stay ad-free with `adBannerHeight = 0.dp`. Both flags must be checked — Subscriber is a superset of Paid.
- **Refresh:** native ads have no built-in auto-refresh. A `LaunchedEffect` keyed on `(nativeAdEnabled, isMediumTier, isAppActiveCompose)` runs `AdLoader.loadAd()` every 60 s. Each successful load destroys the previous `NativeAd` to avoid leaks. Refresh pauses when app is backgrounded (`isAppActiveCompose = false`) and resumes immediately on foreground (effect re-keys, runs a fresh load).
- **Video creatives:** `NativeAdOptions` passes `VideoOptions.Builder().setStartMuted(true)` to the AdLoader — videos start muted (matches AdMob default + locks the policy locally). MediaView renders the speaker mute/unmute icon overlay automatically (AdMob policy requirement). A separate `DisposableEffect` registers an `ON_STOP` lifecycle observer that calls `nativeAd?.mediaContent?.videoController?.mute(true)` so a user who unmuted before backgrounding sees the ad re-muted on return rather than resuming with audio.
- **AdAwareDialog offsetting:** `LocalAdBannerHeight` plumbing unchanged — flows the slot height (64 / 260 / 0) to dialogs that push their content below it.

## What ships

**1. Dependency:** `com.google.android.gms:play-services-ads:23.6.0` in `app/build.gradle.kts`. **No `play-services-ads-native-templates` artifact** — Google never published this to Maven (404 on both `repo1.maven.org` and `dl.google.com/.../maven2`); their templates are GitHub-sample source code. We wrote custom layouts instead.

**2. Manifest (`AndroidManifest.xml`):**
- `xmlns:tools="http://schemas.android.com/tools"` on `<manifest>`.
- AdMob `APPLICATION_ID` meta-data inside `<application>` — TEST app ID `ca-app-pub-3940256099942544~3347511713`.
- `<property android:name="android.adservices.AD_SERVICES_CONFIG" android:resource="@xml/gma_ad_services_config" tools:replace="android:resource" />` — required because both `play-services-ads-lite` and Firebase's `play-services-measurement-api` declare the same property pointing to different XML configs. AdMob's wins. See `feedback_admob_manifest_merger.md`.

**3. SDK init:** `MobileAds.initialize(this) {}` in `BudgeTrakApplication.onCreate`.

**4. Layout XMLs** (`app/src/main/res/layout/`):
- `native_ad_small.xml` — root `NativeAdView` containing horizontal `LinearLayout`: 48 dp icon → middle column (Ad badge + headline 1-line / advertiser 1-line) → CTA button. Total wrap-content height fits in 64 dp slot.
- `native_ad_medium.xml` — root `NativeAdView` containing vertical `LinearLayout`: header row (56 dp icon + Ad badge / advertiser / headline 2-line) → `MediaView` (`adjustViewBounds=true`, wrap_content) → body text + CTA button. Total wrap-content height fits in 260 dp slot.
- Both layouts give every asset view a stable `R.id` so MainActivity's `update` block can `findViewById` and bind text/colors per recomposition.

**5. Drawables** (`app/src/main/res/drawable/`):
- `native_ad_badge_bg.xml` — yellow `#FFCC00` rounded rectangle (3 dp corners) for the mandatory "Ad" label.
- `native_ad_cta_bg.xml` — fallback CTA background (`LightPrimary` blue, 6 dp corners). Overridden at runtime in `MainActivity` with `MaterialTheme.colorScheme.primary` to follow theme.

**6. MainActivity binding** (in `setContent`):
- `AndroidView` factory inflates the right layout (small/medium), registers asset views with `NativeAdView` (`headlineView = ...`, `iconView = ...`, `callToActionView = ...`, `advertiserView = ...`, optional `mediaView = ...`, optional `bodyView = ...`). The setters are what wires AdMob's click-through registration.
- `update` lambda runs each recomposition: pulls `headerTextColor = LocalSyncBudgetColors.current.headerText` for text contrast against the dark `topBarColor`, builds a runtime `GradientDrawable` for the CTA button using `MaterialTheme.colorScheme.primary` so it follows theme, then if `nativeAd != null` binds `headline / advertiser / callToAction / body / icon` and calls `view.setNativeAd(ad)`.
- `nativeAd` is `mutableStateOf<NativeAd?>(null)`. The `forNativeAd { ad → nativeAd?.destroy(); nativeAd = ad }` callback in the `AdLoader.Builder` wires the swap-on-load path. A `DisposableEffect(Unit)` calls `nativeAd?.destroy()` on Compose disposal.

**7. Mandatory "Ad" badge:** the small yellow chip drawn via `native_ad_badge_bg.xml` is required by AdMob policy + FTC native-ad disclosure rules. The text is hard-coded `"Ad"` — DO NOT remove it, hide it, or make its contrast low; that fails AdMob policy review on first scan.

**8. Cutout + decorative top strip:** outer Column uses `Modifier.fillMaxSize().background(topBarColor).windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))` — pads for whichever of status bar / cutout is taller (no double-pad), background fills the inset area with the theme's header color.

**9. Status bar icon appearance:** `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars = false` after `enableEdgeToEdge()` — forces white status-bar icons in both light and dark mode. `headerBackground` is dark enough in both themes that black icons would be unreadable.

## Ad tappability while dialogs are open (v2.10.20+, 2026-05-11)

`AdAwareDialog` was rewritten from a separate-Compose-Dialog-window implementation to an **in-tree overlay** rendered inside the main Activity window. Reason: the old separate-window approach absorbed taps in its window bounds, including on the visible-but-behind ad bar — so `NativeAdView.callToActionView` clicks didn't register while any dialog was open. The in-tree overlay keeps the ad bar outside the dialog's bounds; AdMob clicks pass through normally during open dialogs.

Companion change: `MainActivity` manifest now declares `android:windowSoftInputMode="adjustResize"` so the IME shrinks content cleanly (instead of panning, which would push the ad bar behind the status bar). Dialog content's own `.imePadding()` lifts the dialog above the keyboard within the shrunk area.

Full architecture in `spec_ui_architecture.md` "Dialog system" section and `docs/BudgeTrak_LLD_v2.8.md` §8.1.

## In-house fallback ad (v2.10.20+, 2026-05-11)

When `AdLoader.onAdFailedToLoad` fires (offline, no fill, etc.), the AdMob `AndroidView` is replaced by a pure-Compose in-house promo. Five fixed-order ads cycle on each subsequent failure; `inHouseAdIndex` resumes (not resets) across AdMob recoveries so a free user sees variety over a session. Layout dimensions match the AdMob templates (same `adBannerHeight`) so the slot doesn't visually jump on swap.

- **Five ad themes** (in order): Receipts (Paid), Exports (Paid), SYNC (Subscriber), Simulation (Paid), OCR (Subscriber). Three Paid + two Subscriber.
- **Visual continuity**: yellow rounded chip mirrors AdMob's "Ad" badge, but text is "Upgrade" / "Mejora" because this is 1st-party promotional content (FTC "Ad" label doesn't apply to own promotional content).
- **Medium template difference from AdMob**: AdMob's 160×120 `MediaView` is replaced by a 160×120 box rendering `R.mipmap.ic_launcher_round` at 120dp centered — gives the in-house ad a visible "this is BudgeTrak" anchor.
- **Click handling**: whole banner is clickable; tier on the ad dictates whether `vm.launchPaidUpgrade(activity)` or `vm.launchSubscribe(activity)` is invoked.
- **Anti-piracy benefit**: a free user who blocks app internet to dodge AdMob still sees our upgrade promo cycling through.

Editing the cycle:
- Copy changes → edit `EnglishStrings.ads` + `SpanishStrings.ads` (`InHouseAdStrings` data class in `AppStrings.kt`).
- Add/remove/reorder ads → edit `InHouseAds: List<InHouseAd>` in `ui/components/InHouseAd.kt`. Each entry needs a unique `id`, an `ImageVector`, and a `tier`. New ids need branches in `headlineFor` / `bodyFor` plus matching strings.
- Translation context lives in `TranslationContext.ads` — keep that synchronized when adding strings.

## Spanish ad targeting (v2.10.20+)

AdMob picks creative language from `Resources.configuration.locale` plus IP geo + Google-account signals. To get Spanish-targeted ads for Spanish-language users (especially US-based Spanish speakers whose IP wouldn't otherwise signal Spanish), `BudgeTrakApplication.applyAppLocale(context, tag)` sets the per-app Resources locale on Application.onCreate and on every language toggle in MainActivity. AdMob then sees Spanish in its configuration query. Doesn't override IP geo (Mexico/Spain users already got Spanish via geo); main lift is for English-IP-Spanish-language users. See `reference_strings_system.md` "Language selection" for the full mechanism.

## Production-promotion swap checklist

When promoting from internal/closed to production, do all four:

1. **AdMob Console:** create the production app + **two Native Advanced ad units** (one for the small phone template, one for the medium tablet template). Copy each `ca-app-pub-XXXXXXXX/XXXXXXX` (ad unit ID) and the production app ID `ca-app-pub-XXXXXXXX~XXXXXXX`.
2. **`AndroidManifest.xml`:** replace the TEST `APPLICATION_ID` meta-data with the real app ID.
3. **`MainActivity.kt`:** replace the TEST native ad unit ID string in the `AdLoader.Builder(this@MainActivity, "<TEST>")` call. Tier selection logic should branch on `isMediumTier` to choose the phone vs tablet ad unit ID.
4. **App-ads.txt:** already published at `https://techadvantagesupport.github.io/app-ads.txt` — covers all ad units under the publisher account, no per-unit change needed.

Optional but recommended pre-production:
- `MobileAds.openAdInspector(this) {}` from a debug-only menu item to verify served-creative diagnostics on real devices.
- Add Paul + Kim deviceIDs to AdMob test devices via `RequestConfiguration.Builder().setTestDeviceIds(...)` so live debugging never accrues impressions / risks invalid-traffic flags.

## What does NOT need to change for production

- Layout XMLs (`native_ad_small.xml`, `native_ad_medium.xml`) — same files work against real ad units.
- 60 s refresh timer + lifecycle gating — production-grade as written.
- `LocalAdBannerHeight` plumbing for `AdAwareDialog` — unchanged.
- Manifest-merger override on `AD_SERVICES_CONFIG` — same conflict, same fix.
- Mandatory "Ad" badge drawable + `Ad` text — required for policy compliance, do not change.

## Anti-goals (do NOT do these)

- **Don't** revert to AdMob `AdView` + `AdSize` banners. The 4-iteration arc through anchored adaptive / custom AdSize / fixed-size constants in May 2026 confirmed every banner approach has a letterbox or clipping failure mode in at least one device class. Native sidesteps the entire problem because we render the layout, not AdMob.
- **Don't** remove the `Ad` badge `TextView` or replace it with a low-contrast / hidden equivalent. AdMob policy requires a clearly visible "Ad" or "Sponsored" label on every native ad. Failing this triggers automatic policy violations + ad-serving holds.
- **Don't** auto-refresh faster than 30 s (AdMob's documented minimum) or slower than user attention spans warrant. 60 s is conservative and safe; lower values risk invalid-traffic flags.
- **Don't** load native ads while the app is backgrounded. The `LaunchedEffect` re-keys on `isAppActiveCompose` for exactly this reason — cold loads off-screen waste impressions (no view → no impression credit) and burn battery.
- **Don't** call `setNativeAd()` without first registering all asset views (`headlineView`, `iconView`, `callToActionView`, etc.) on the `NativeAdView`. The setters are how AdMob wires click-through; binding text + image alone won't make the ad clickable.
- **Don't** forget to call `nativeAd.destroy()` before replacing it. Each `NativeAd` holds native memory + click handlers; abandoned instances leak.
- **Don't** wire `isAppearanceLightStatusBars` to follow the system theme — the override is intentionally always-white because both light- and dark-mode `headerBackground` colors are dark enough to demand light icons.

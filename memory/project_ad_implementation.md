---
name: Native ad implementation — replaced banner in v2.10.16, polished on feature/ad-polish
description: AdMob Native Advanced ad integration in BudgeTrak. 3-column small (<400dp) and medium (≥400dp) templates with dimens-based tier scaling at 400/600/800dp. Five-pill MediaView overlays (Ad badge + AdChoices + store/price/star), theme-aware text color, tier-flip robustness via LocalConfiguration + key() + DisposableEffect cleanup. In-house fallback mirrors the AdMob structure with Play Billing price pill, hardcoded "BudgeTrak" advertiser, and uppercase CTA. Production-promotion swap checklist included.
type: project
---

## Status
**Native ads in v2.10.16** (2026-05-09). Replaced the banner `AdView` with AdMob Native Advanced ads — adaptive banners couldn't deliver letterbox-free creatives at custom aspect ratios. Native ads sidestep the issue entirely: app renders the layout, AdMob delivers asset data (headline / icon / image / CTA / advertiser / body / mediaContent / price / store / starRating / adChoicesInfo).

**Polished on `feature/ad-polish`** (2026-05-13 → 2026-05-14): 3-column layouts, dimens-based tier scaling (400/600/800dp), all asset bindings wired, theme-aware text, runtime tier-flip robustness, in-house ad redesign with Play Billing price pill. Currently using Google's TEST native ad unit ID `ca-app-pub-3940256099942544/2247696110` — production swap checklist below.

## Architecture

- **Position:** outside the `when (vm.currentScreen)` block, inside the outer `Column` in `MainActivity.setContent`. Page navigation only recomposes the `weight(1f)` Box below; the AdView never recomposes between screens.
- **Tier select** (reactive via `LocalConfiguration.current.screenWidthDp` — handles foldable hinge, Settings display-size slider, dev-tools dp swap):
  - `widthDp < 400` → `R.layout.native_ad_small` — 3-col card, 70 dp slot
  - `widthDp ≥ 400` → `R.layout.native_ad_medium` — horizontal card with MediaView, slot height from `@dimen/ad_slot_height` (120/144/180 dp at 400/600/800dp)
- **Gate:** `nativeAdEnabled = !vm.isPaidUser && !vm.isSubscriber`. Paid + Subscriber stay ad-free with `adBannerHeight = 0.dp`. Both flags must be checked — Subscriber is a superset of Paid.
- **Refresh:** native ads have no built-in auto-refresh. `LaunchedEffect(nativeAdEnabled, isMediumTier, isAppActiveCompose)` runs `AdLoader.loadAd()` every 60 s. Each successful load destroys the previous `NativeAd` to avoid leaks. Refresh pauses while backgrounded and resumes immediately on foreground (effect re-keys).
- **Video creatives:** `NativeAdOptions.Builder().setVideoOptions(VideoOptions.Builder().setStartMuted(true))` locks muted start locally. MediaView renders mute/unmute icon overlay automatically (AdMob policy). `DisposableEffect` `ON_STOP` lifecycle observer re-mutes on backgrounding.
- **AdAwareDialog offsetting:** `LocalAdBannerHeight` plumbing — flows current slot height to dialogs that push content below it. See `spec_ui_architecture.md`.

## Tier-flip robustness (feature/ad-polish, 2026-05-14)

Three coordinated defenses make small ↔ medium tier flips clean (foldable open/close, Settings density slider, `adb shell wm density`):

1. **`widthDp = LocalConfiguration.current.screenWidthDp`** — reactive, recomposes on dp/density/orientation changes. NOT `remember {}` (which would cache the first-composition value forever).
2. **`androidx.compose.runtime.key(isMediumTier) { AndroidView(...) }`** — `AndroidView.factory` only runs on first composition per instance; `key` forces a fresh AndroidView (and fresh layout-XML inflation) when the tier flips.
3. **`DisposableEffect(isMediumTier) { onDispose { nativeAd?.destroy(); nativeAd = null; adMobFailed = false } }`** — destroys the cached NativeAd immediately on tier change so the new AndroidView's `update` lambda returns early (no `setNativeAd` with stale binding). The re-keyed `LaunchedEffect` reloads a fresh ad against the new MediaView dimensions.

**Caveat — validator UI's own caching:** the AdMob native ad validator UI keeps "MediaView too small" warnings across app-side fixes if the validator window was already open when the tier flipped. See `feedback_admob_validator_dp_transition.md` — don't chase further code fixes.

## Dimens-based tier scaling

Three `dimens.xml` files drive every layout size. Android picks the right file automatically based on `screenWidthDp`:
- `res/values/dimens.xml` — base values (≥400 dp), used by the medium template.
- `res/values-w600dp/dimens.xml` — 1.2× scale (tablet portrait, large phones landscape).
- `res/values-w800dp/dimens.xml` — 1.5× scale (tablet landscape, foldable open, large displays).

| Dimen | Purpose | Base / w600 / w800 |
|---|---|---|
| `ad_slot_height` | Medium tier ad bar height | 120 / 144 / 180 dp |
| `ad_media_width` | MediaView width (16:9 to slot height) | 214 / 256 / 320 dp |
| `ad_icon_size` | Left-column icon | 20 / 24 / 30 dp |
| `ad_icon_margin` | Icon margins (top/start/end) | 4 / 5 / 6 dp |
| `ad_icon_margin_bottom` | Icon bottom margin (tighter at base for body fit) | 0 / 3 / 4 dp |
| `ad_left_col_margin_end` | Gap between left column and MediaView | 4 / 6 / 8 dp |
| `ad_headline_text_size` | Headline `sp` | 14 / 16 / 18 sp |
| `ad_body_text_size` | Body `sp` | 12 / 14 / 16 sp |
| `ad_body_margin_top` | Gap above body | 0 / 3 / 4 dp |
| `ad_advertiser_text_size` | Advertiser line `sp` | 11 / 12 / 13 sp |
| `ad_pill_text_size` | Overlay pill text `sp` | 10 / 11 / 12 sp |
| `ad_pill_padding_h` / `ad_pill_padding_v` | Pill internal padding | 6/2 → 8/3 → 9/4 dp |
| `ad_pill_margin` | Pill external margin (stack spacing) | 4 / 5 / 6 dp |
| `ad_badge_text_size` | "Ad" badge `sp` | 10 / 11 / 12 sp |
| `ad_badge_padding_h` / `ad_badge_padding_v` | "Ad" badge internal padding | 5/1 → 6/2 → 8/3 dp |
| `ad_cta_text_size` | CTA pill `sp` | 11 / 12 / 14 sp |
| `ad_cta_padding_h` / `ad_cta_padding_v` | CTA internal padding | 14/0 → 16/3 → 20/4 dp |
| `ad_cta_margin_bottom` | Gap below CTA | 3 / 6 / 8 dp |
| `ad_inhouse_app_icon_size` | In-house app-icon size in medium right Box | 100 / 120 / 150 dp |

Body fit at base 400dp is tight — `ad_icon_margin_bottom`, `ad_body_margin_top`, `ad_cta_padding_v` are kept at 0 so 3-line body fits inside the 120dp slot with `includeFontPadding="false"`. Don't bump these without also growing the slot height.

## Layout XMLs

### `native_ad_small.xml` — 70dp 3-column

- **Slot:** `match_parent × 70dp`, horizontal LinearLayout
- **Left col (58dp wide):** 5dp paddingTop/Bottom + 25dp icon centered (4dp margin) + 5dp gap + 15dp CTA pill (`paddingV=0` + `includeFontPadding=false` + `gravity=center`). Total exact 70dp.
- **Center col (weight=1, gravity=center):** 5 rows, all `includeFontPadding=false` + `gravity=center`. Order: advertiser (8sp bold, underline set at runtime via `paintFlags`) → headline (9sp bold, maxLines=1) → body (8sp, maxLines=3). Vertically centered as a group.
- **Right col (58dp wide, FrameLayout):** Ad badge top-start + `AdChoicesView` top-end + LinearLayout bottom-center stacking `store / price / star` pills (7sp, `gone` until populated). All with 2-4dp margins to fit 70dp.

### `native_ad_medium.xml` — scaling 3-column

- **Slot:** `match_parent × @dimen/ad_slot_height`, horizontal LinearLayout
- **Left col (weight=1, vertical, `marginEnd=@dimen/ad_left_col_margin_end`):**
  - **Top row:** horizontal `Icon` (20dp, asymmetric margins for tight body fit) + advertiser TextView (bold, underline set at runtime, `maxLines=1`).
  - **Headline** (`@dimen/ad_headline_text_size`, bold, `gravity=center`, `includeFontPadding=false`, `maxLines=2`).
  - **Body** (`@dimen/ad_body_text_size`, `gravity=center`, `includeFontPadding=false`, `maxLines=3`, `marginTop=@dimen/ad_body_margin_top`).
  - **Space weight=1** — pushes CTA to bottom.
  - **CTA Button** (wrap_content, `gravity=center`, `includeFontPadding=false`, `marginBottom=@dimen/ad_cta_margin_bottom`).
- **Right col (FrameLayout, `@dimen/ad_media_width × @dimen/ad_slot_height`):**
  - **`MediaView`** fills (`match_parent × match_parent`). 16:9 aspect ratio matches dimens (214/120 ≈ 256/144 ≈ 320/180 ≈ 1.78).
  - **Ad badge** TextView at top-end — yellow `#FFCC00` with 2px black stroke via `native_ad_badge_bg.xml`.
  - **Top-start vertical LinearLayout:** `store` pill (10sp) + `AdChoicesView` directly below. AdChoices placement comes from this explicit view, NOT `setAdChoicesPlacement` (which is ignored when an explicit AdChoicesView is registered).
  - **Bottom-start vertical LinearLayout:** `star` pill (★ X.X format, set at runtime) + `price` pill. Bottom-end stays clear for the SDK's video mute icon.

Both layouts give every asset view a stable `R.id` so `MainActivity.update` can `findViewById` and bind text/colors per recomposition. AdChoicesView is treated like any other asset view (`view.adChoicesView = ...`).

## MainActivity binding

In `setContent`:

```kotlin
val widthDp = LocalConfiguration.current.screenWidthDp
val isMediumTier = nativeAdEnabled && widthDp >= 400
val nativeAdLayoutId = if (isMediumTier) R.layout.native_ad_medium else R.layout.native_ad_small
val mediumSlotHeight = dimensionResource(R.dimen.ad_slot_height)
val adBannerHeight = when {
    !nativeAdEnabled -> 0.dp
    isMediumTier -> mediumSlotHeight
    else -> 70.dp
}
```

Theme-aware text color picks header bg (light theme) vs onBackground (dark theme):
```kotlin
val darkTheme = isSystemInDarkTheme()
val pageTextColor =
    if (darkTheme) MaterialTheme.colorScheme.onBackground
    else LocalSyncBudgetColors.current.headerBackground
val ctaBgColor = MaterialTheme.colorScheme.primary
val ctaTextColor = MaterialTheme.colorScheme.onPrimary
```

`AndroidView` wrapped in `key(isMediumTier)`:
- **factory:** inflates the layout, binds every asset view (`iconView`, `headlineView`, `advertiserView`, `callToActionView`, `mediaView` via `?.let`, `bodyView` via `?.let`, `priceView` via `?.let`, `storeView` via `?.let`, `starRatingView` via `?.let`, `adChoicesView` via `?.let`).
- **update:** sets text colors using `pageTextColor.toArgb()` for headline/body/advertiser; sets advertiser underline via `paintFlags or UNDERLINE_TEXT_FLAG`; builds runtime `GradientDrawable` for CTA bg using `ctaBgColor`; sets CTA text color = `ctaTextColor`. Overlay pills (price/store/star) get a `GradientDrawable` bg using `ctaBgColor` and text color = `ctaTextColor` so they match the CTA's theme colors. Star rating formats as `"★ %.1f"`. Each pill's visibility is toggled based on whether AdMob delivered the asset (`isNullOrBlank` / `null`). Ad badge keeps the XML drawable (yellow + black border) — NOT overridden at runtime.

In debug builds only, every load logs to `BudgeTrakApplication.tokenLog`:
```
Ad load: tier=medium adChoicesInfo=false advertiser=null icon=true price=FREE store=Google Play star=4.5 body=...
```

This writes to `/Download/BudgeTrak/support/token_log.txt` so it's readable from Termux without ADB. Test ads omit `advertiser` and `adChoicesInfo` — production ads provide both.

## In-house fallback ad

When `AdLoader.onAdFailedToLoad` fires, the AdMob `AndroidView` is replaced by a pure-Compose in-house promo. Five fixed-order ads cycle on each subsequent failure; `inHouseAdIndex` resumes (not resets) across AdMob recoveries.

**Five ad themes (in order):** Receipts (Paid), Exports (Paid), SYNC (Subscriber), Simulation (Paid), OCR (Subscriber). Each entry in `InHouseAds: List<InHouseAd>` has a unique `id`, a Material `ImageVector` (feature icon), and a `tier` (PAID vs SUBSCRIBER drives CTA text + Play Billing price selection).

**Structural parity with AdMob** (so the slot doesn't visually jump on swap):
- **Small in-house:** 3 columns — left 58dp (40dp feature icon + 15dp CTA), center (5 rows: hardcoded `"BudgeTrak"` bold-underlined as advertiser, headline, 3-line body), right 58dp (PriceBadge centered; falls back to `UpgradeBadge` if Play Billing prices haven't loaded yet).
- **Medium in-house:** mirrors AdMob — left col with 20dp feature icon + bold `"BudgeTrak"` advertiser, headline, body, CTA at bottom (via `Spacer.weight(1f)`); right Box has the BudgeTrak app icon centered (sized via `@dimen/ad_inhouse_app_icon_size`) + `PriceBadge` overlay at bottom-start mirroring AdMob's price-pill location.

**PriceBadge** displays `vm.paidUpgradePrice` (for PAID-tier ads) or `vm.subscriberPrice` (for SUBSCRIBER-tier ads) from `BillingService.queryProductDetails`. Uses `MaterialTheme.colorScheme.primary` bg + `onPrimary` text (matches the CTA), 12sp bold, 6/4 padding. Larger than AdMob's price pill (10sp at base) because the in-house has no other right-column elements.

**`UpgradeBadge`** retained for fallback (when Play Billing prices haven't loaded). Yellow `#FFCC00` + 1dp black border, 10sp default, matches the AdMob "Ad" pill style.

**`CtaButton`** in in-house renders `text.uppercase()` always (AdMob CTAs use server-provided casing).

**`tightTextStyle()`** Composable: returns a TextStyle with `PlatformTextStyle(includeFontPadding = false)` + `LineHeightStyle(alignment = Center, trim = Both)`. Applied to every Text in the in-house templates with explicit `lineHeight = fontSize × 1.15` so Compose's line spacing matches XML TextView's `includeFontPadding="false"` rendering. Without this, default Compose lineHeight (~1.5×) makes 3-line body overflow at low dp.

**Click handling:** whole banner is clickable; `ad.tier` dictates `vm.launchPaidUpgrade(activity)` vs `vm.launchSubscribe(activity)`.

**Editing the cycle:**
- Copy changes → edit `EnglishStrings.ads` + `SpanishStrings.ads` (`InHouseAdStrings` data class in `AppStrings.kt`). Body budget ~80 chars EN / ~85 chars ES (sized to fit 3-line body at base 400dp / 12sp). Headline budget ~25 chars / 1 line.
- Add/remove/reorder ads → edit `InHouseAds: List<InHouseAd>` in `ui/components/InHouseAd.kt`. New ids need branches in `headlineFor` / `bodyFor` plus matching strings.
- Translation context lives in `TranslationContext.ads` — keep that synchronized when adding strings.

**Anti-piracy benefit:** a free user who blocks app internet to dodge AdMob still sees our upgrade promo cycling through.

## Drawables

- `native_ad_badge_bg.xml` — yellow `#FFCC00` rounded rectangle with **2px black stroke** (3 dp corners). Used for the mandatory "Ad" label. The stroke distinguishes the badge from CTA-colored pills.
- `native_ad_cta_bg.xml` — fallback CTA background (`LightPrimary` blue, 6 dp corners). Overridden at runtime in `MainActivity` with `MaterialTheme.colorScheme.primary` so it follows theme.
- `native_ad_overlay_bg.xml` — translucent `#B3000000` rounded rectangle (3 dp corners). Used as fallback bg for price/store/star pills; overridden at runtime to the CTA color via `GradientDrawable`.

## Status bar & decorative top strip

- Outer Column: `Modifier.fillMaxSize().background(LocalSyncBudgetColors.current.headerBackground).windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))`. The inset-padding strip uses `headerBackground` (always dark in both themes) on every screen, with or without ads.
- `WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false` set in `onCreate` — forces white status-bar icons in both themes. Android's status-bar icon color is binary (light or dark appearance), not a custom shade — light icons are always white-ish, dark are always black-ish. We can't tint to a specific color like `onBackground`.

## Mandatory "Ad" badge

The yellow chip drawn via `native_ad_badge_bg.xml` is required by AdMob policy + FTC native-ad disclosure rules. The text is hard-coded `"Ad"` — DO NOT remove it, hide it, or make its contrast low; that fails AdMob policy review on first scan.

## Ad tappability while dialogs are open (v2.10.20+, 2026-05-11)

`AdAwareDialog` is an **in-tree overlay** rendered inside the main Activity window. Reason: the old separate-Compose-Dialog-window approach absorbed taps in its window bounds, including the visible-but-behind ad bar — `NativeAdView.callToActionView` clicks didn't register while dialogs were open. The in-tree overlay keeps the ad bar outside the dialog's bounds; AdMob clicks pass through normally during open dialogs.

Companion: `MainActivity` manifest has `android:windowSoftInputMode="adjustResize"` so the IME shrinks content (not pans, which would push the ad bar behind the status bar). Dialog content's own `.imePadding()` lifts the dialog above the keyboard within the shrunk area.

Full architecture in `spec_ui_architecture.md` "Dialog system" section.

## Spanish ad targeting (v2.10.20+)

AdMob picks creative language from `Resources.configuration.locale` plus IP geo + Google-account signals. `BudgeTrakApplication.applyAppLocale(context, tag)` sets the per-app Resources locale on Application.onCreate and on every language toggle in MainActivity. AdMob then sees Spanish in its configuration query — main lift is for English-IP-Spanish-language users.

## Production-promotion swap checklist

When promoting from internal/closed to production, do all four:

1. **AdMob Console:** create the production app + **two Native Advanced ad units** (one for the small phone template, one for the medium tablet template). Copy each `ca-app-pub-XXXXXXXX/XXXXXXX` ad unit ID and the production app ID `ca-app-pub-XXXXXXXX~XXXXXXX`.
2. **`AndroidManifest.xml`:** replace TEST `APPLICATION_ID` meta-data with the real app ID.
3. **`MainActivity.kt`:** replace the TEST native ad unit ID in `AdLoader.Builder(this@MainActivity, "<TEST>")`. Tier selection should branch on `isMediumTier` to choose the phone vs tablet ad unit ID.
4. **App-ads.txt:** already published at `https://techadvantagesupport.github.io/app-ads.txt` — covers all ad units under the publisher account.

Optional pre-production:
- `MobileAds.openAdInspector(this) {}` from a debug-only menu item for served-creative diagnostics.
- Add tester deviceIDs to AdMob test devices via `RequestConfiguration.Builder().setTestDeviceIds(...)` so live debugging doesn't accrue impressions / risk invalid-traffic flags.
- Remove or gate the `BudgeTrakApplication.tokenLog("Ad load: ...")` debug line in `MainActivity.update` (already gated by `BuildConfig.DEBUG`).

## Anti-goals (do NOT do these)

- **Don't** revert to AdMob `AdView` + `AdSize` banners. The 4-iteration arc through anchored adaptive / custom AdSize / fixed-size constants in May 2026 confirmed every banner approach has a letterbox or clipping failure mode in at least one device class.
- **Don't** remove the `Ad` badge `TextView` or replace it with a low-contrast / hidden equivalent. AdMob policy requires a clearly visible "Ad" or "Sponsored" label.
- **Don't** auto-refresh faster than 30 s (AdMob's documented minimum) or slower than user attention spans warrant. 60 s is conservative and safe.
- **Don't** load native ads while the app is backgrounded. The `LaunchedEffect` re-keys on `isAppActiveCompose` for exactly this reason.
- **Don't** call `setNativeAd()` without first registering all asset views on the `NativeAdView`. The setters are how AdMob wires click-through.
- **Don't** forget to call `nativeAd.destroy()` before replacing it — abandoned instances leak SDK memory + click handlers. Three `destroy()` callsites in `MainActivity`: inside `forNativeAd` (before swapping to a new ad), inside `DisposableEffect(Unit)` (Activity disposal), inside `DisposableEffect(isMediumTier)` (tier flip).
- **Don't** revert `widthDp` to `remember {}`. Tier-flip robustness depends on `LocalConfiguration.current.screenWidthDp` being reactive — see the three-defense section above.
- **Don't** remove `key(isMediumTier) { AndroidView(...) }`. Without it, `factory` only runs once and a tier flip leaves the old layout XML inflated under a new ad binding.
- **Don't** wire `isAppearanceLightStatusBars` to follow the system theme — the override is intentionally always-white because both light- and dark-mode `headerBackground` colors are dark enough to demand light icons. Android can't tint status bar icons to a custom color — see comment above and `feedback_admob_validator_dp_transition.md`.
- **Don't** bump `ad_icon_margin_bottom`, `ad_body_margin_top`, `ad_cta_padding_v`, or `ad_cta_margin_bottom` at the base tier without also growing `ad_slot_height`. The body 3-line fit at 400dp is calibrated within ~2 dp of the slot height. Test on a 400dp viewport before merging.
- **Don't** chase validator-UI "MediaView too small" warnings that persist across runtime tier flips. See `feedback_admob_validator_dp_transition.md`.

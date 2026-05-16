---
name: Native ad implementation — replaced banner in v2.10.16, continuous-scale + icon/headline restructure on dev 2026-05-15
description: AdMob Native Advanced ad integration in BudgeTrak. Small (<400dp, 70dp fixed) and medium (≥400dp, continuous-scale) templates. Medium tier scales linearly from 400dp base via computeAdMediumDims(widthDp) — Compose-driven, applied at runtime in AndroidView.update lambda (text sizes via setTextSize, sizes/margins via layoutParams, paddings via setPadding). Top section restructured 2026-05-15 — large icon left, advertiser + headline left-justified in vertical column to its right. Five-pill MediaView overlays (Ad badge + AdChoices + store/price/star), theme-aware text color, tier-flip robustness via LocalConfiguration + key() + DisposableEffect cleanup. In-house fallback mirrors the AdMob structure with Play Billing price pill, hardcoded "BudgeTrak" advertiser, and uppercase CTA. Production-promotion swap checklist included.
type: project
originSessionId: 5682369f-ce9a-4adb-978b-3517ce099586
---
## Status
**Native ads in v2.10.16** (2026-05-09). Replaced the banner `AdView` with AdMob Native Advanced ads — adaptive banners couldn't deliver letterbox-free creatives at custom aspect ratios. Native ads sidestep the issue entirely: app renders the layout, AdMob delivers asset data (headline / icon / image / CTA / advertiser / body / mediaContent / price / store / starRating / adChoicesInfo).

**Polished on `feature/ad-polish`** (2026-05-13 → 2026-05-14): 3-column layouts, dimens-based tier scaling (400/600/800dp), all asset bindings wired, theme-aware text, runtime tier-flip robustness, in-house ad redesign with Play Billing price pill. Currently using Google's TEST native ad unit ID `ca-app-pub-3940256099942544/2247696110` — production swap checklist below.

**Top-section restructure + continuous scaling on dev (2026-05-15):** medium tier now scales linearly with `widthDp` via `computeAdMediumDims(widthDp)` (400dp base × `widthDp/400`, floored at 1.0×) — applied at runtime so the layout grows smoothly with display size rather than stepping at the old w600dp/w800dp resource-qualifier breakpoints. `values-w600dp/dimens.xml` and `values-w800dp/dimens.xml` deleted. Top section restructured: large icon (30dp at base, was 20dp) left-aligned at top, vertical column (advertiser + headline both left-justified) to its right, so icon's bottom edge roughly aligns with the bottom of the headline's first row. CTA bumped at base too (13sp/5dp padV, was 11sp/0dp) — the "Don't bump base values" memo from the prior dimens-tight era was overconservative; concrete math shows 32dp slack at worst-case 1-line headline + 3-line body.

## Architecture

- **Position:** outside the `when (vm.currentScreen)` block, inside the outer `Column` in `MainActivity.setContent`. Page navigation only recomposes the `weight(1f)` Box below; the AdView never recomposes between screens.
- **Tier select** (reactive via `LocalConfiguration.current.screenWidthDp` — handles foldable hinge, Settings display-size slider, dev-tools dp swap):
  - `widthDp < 400` → `R.layout.native_ad_small` — 3-col card, 70 dp slot (fixed)
  - `widthDp ≥ 400` → `R.layout.native_ad_medium` — horizontal card with MediaView, every dimension scaled via `computeAdMediumDims(widthDp)` = base × `(widthDp/400).coerceAtLeast(1.0f)`. Slot height 120dp at 400dp → 180dp at 600dp → 240dp at 800dp → 324dp at foldable 1080dp.
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

## Continuous Compose-driven scaling (2026-05-15)

`AdMediumDims` data class + `computeAdMediumDims(widthDp: Int)` in `InHouseAd.kt` is the single source of truth. Scale factor `s = (widthDp / 400f).coerceAtLeast(1.0f)`. Every base value multiplied by `s` and returned as `Float` (dp or sp, named accordingly). At 400dp: `s=1.0` → base values. At 600dp: `s=1.5`. At 800dp: `s=2.0`. At foldable 1080dp: `s=2.7`. No upper clamp — bigger screens get proportionally bigger ads.

| Field | Purpose | Base (400dp) |
|---|---|---|
| `slotHeightDp` | Medium tier ad bar height | 120 |
| `mediaWidthDp` | MediaView width (16:9 to slot height) | 214 |
| `iconSizeDp` | Left-column icon (spans advertiser + headline row 1) | 30 |
| `iconMarginDp` | Icon margins (top/start/end) | 4 |
| `iconMarginBottomDp` | Icon bottom margin | 0 |
| `leftColMarginEndDp` | Gap between left column and MediaView | 4 |
| `headlineSp` | Headline text size | 14 sp |
| `bodySp` | Body text size | 12 sp |
| `bodyMarginTopDp` | Gap above body | 0 |
| `advertiserSp` | Advertiser line | 11 sp |
| `pillSp` | Overlay pill text | 10 sp |
| `pillPaddingHDp` / `pillPaddingVDp` | Pill internal padding | 6 / 2 |
| `pillMarginDp` | Pill external margin (stack spacing) | 4 |
| `badgeSp` | "Ad" badge text size | 10 sp |
| `badgePaddingHDp` / `badgePaddingVDp` | "Ad" badge internal padding | 5 / 1 |
| `ctaSp` | CTA pill text size | 13 sp |
| `ctaPaddingHDp` / `ctaPaddingVDp` | CTA internal padding | 14 / 5 |
| `ctaMarginBottomDp` | Gap below CTA | 3 |
| `inhouseAppIconDp` | In-house app-icon size in medium right Box | 100 |

**Application path:**
- **AdMob (XML-inflated)**: `MainActivity.kt` AndroidView `update` lambda walks the inflated view tree and applies every dim: outer LinearLayout's `layoutParams.height = slotHeightDp.toPx()`, left col's `MarginLayoutParams.marginEnd`, MediaView FrameLayout's `width/height`, icon's `width/height + margins`, every TextView's `setTextSize(SP, value)`, CTA's `setPadding(...)`, etc. Runs on every recomposition (cheap), so `widthDp` changes propagate within one frame.
- **In-house Compose mirror**: `MediumInHouseAd(ad, strings, dims, ...)` takes the `AdMediumDims` and uses `.dp` / `.sp` directly on Compose `Modifier.size(...)`, `Modifier.padding(...)`, `Text(fontSize=...)`.

**Initial XML values still required:** `res/values/dimens.xml` still defines `ad_*` dimens because `native_ad_medium.xml` references them via `@dimen/ad_*`. XML inflates with those base values; the update lambda then overrides every one. Don't delete the base `dimens.xml` entries — XML inflation fails without them.

**`res/values-w600dp/` and `res/values-w800dp/` deleted** (2026-05-15) — obsolete now that scaling is continuous.

## Layout XMLs

### `native_ad_small.xml` — 70dp 3-column

- **Slot:** `match_parent × 70dp`, horizontal LinearLayout
- **Left col (58dp wide):** 5dp paddingTop/Bottom + 25dp icon centered (4dp margin) + 5dp gap + 15dp CTA pill (`paddingV=0` + `includeFontPadding=false` + `gravity=center`). Total exact 70dp.
- **Center col (weight=1, gravity=center):** 5 rows, all `includeFontPadding=false` + `gravity=center`. Order: advertiser (8sp bold, underline set at runtime via `paintFlags`) → headline (9sp bold, maxLines=1) → body (8sp, maxLines=3). Vertically centered as a group.
- **Right col (58dp wide, FrameLayout):** Ad badge top-start + `AdChoicesView` top-end + LinearLayout bottom-center stacking `store / price / star` pills (7sp, `gone` until populated). All with 2-4dp margins to fit 70dp.

### `native_ad_medium.xml` — continuous-scale 2-column with restructured top

XML uses `@dimen/` references for initial-inflation values; the `update` lambda overrides every one based on the runtime `AdMediumDims`.

- **Slot:** `match_parent × @dimen/ad_slot_height`, horizontal LinearLayout (height overridden at runtime).
- **Left col (weight=1, vertical, `marginEnd=@dimen/ad_left_col_margin_end` overridden):**
  - **Top section (horizontal LinearLayout, no `gravity=center_vertical`):** large icon (30dp at base) + vertical column (`weight=1`) containing:
    - **Advertiser** TextView (bold, `maxLines=1`, `ellipsize=end`, underline applied at runtime via `paintFlags`).
    - **Headline** TextView (bold, `gravity=start`, `includeFontPadding=false`, `maxLines=2`).
  - Icon's bottom edge sits at ~the same y as the headline's first-row bottom (depends on font metrics). When headline wraps to 2 lines, row 2 flows below row 1 in the right-of-icon column (icon doesn't span both rows).
  - **Body** (`@dimen/ad_body_text_size`, `gravity=center`, `includeFontPadding=false`, `maxLines=3`, `marginTop=@dimen/ad_body_margin_top`).
  - **Space weight=1** — pushes CTA to bottom regardless of body line count.
  - **CTA Button** (`wrap_content`, `gravity=center`, `includeFontPadding=false`, `marginBottom=@dimen/ad_cta_margin_bottom`).
- **Right col (FrameLayout, `@dimen/ad_media_width × @dimen/ad_slot_height` — both overridden):**
  - **`MediaView`** fills (`match_parent × match_parent`). 16:9 aspect ratio preserved automatically since both width and height scale by the same `s` factor.
  - **Ad badge** TextView at top-end — yellow `#FFCC00` with 2px black stroke via `native_ad_badge_bg.xml`.
  - **Top-start vertical LinearLayout:** `store` pill + `AdChoicesView` directly below. AdChoices placement comes from this explicit view, NOT `setAdChoicesPlacement` (which is ignored when an explicit AdChoicesView is registered).
  - **Bottom-start vertical LinearLayout:** `star` pill (★ X.X format, set at runtime) + `price` pill. Bottom-end stays clear for the SDK's video mute icon.

Both layouts give every asset view a stable `R.id` so `MainActivity.update` can `findViewById` and bind text/colors/dims per recomposition. AdChoicesView is treated like any other asset view (`view.adChoicesView = ...`).

**Width budget at base 400dp** (advertiser/headline column right of icon): left col ≈ 182dp (400 − 214 MediaView − 4 leftColMarginEnd), minus 30dp icon + 8dp icon margins = **~144dp** for advertiser + headline rows. At 11sp bold advertiser ≈ 24 chars before ellipsize; at 14sp bold headline ≈ 20 chars/line, ~40 chars total in 2 lines. Covers AdMob's typical 25-char headline + 25-char advertiser comfortably; long brand names ellipsize.

## MainActivity binding

In `setContent`:

```kotlin
val widthDp = LocalConfiguration.current.screenWidthDp
val isMediumTier = nativeAdEnabled && widthDp >= 400
val nativeAdLayoutId = if (isMediumTier) R.layout.native_ad_medium else R.layout.native_ad_small
val adMediumDims = if (isMediumTier) computeAdMediumDims(widthDp) else null
val adBannerHeight = when {
    !nativeAdEnabled -> 0.dp
    adMediumDims != null -> adMediumDims.slotHeightDp.dp
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
- **update (colors + text):** sets text colors using `pageTextColor.toArgb()` for headline/body/advertiser; sets advertiser underline via `paintFlags or UNDERLINE_TEXT_FLAG`; builds runtime `GradientDrawable` for CTA bg using `ctaBgColor`; sets CTA text color = `ctaTextColor`. Overlay pills (price/store/star) get a `GradientDrawable` bg using `ctaBgColor` and text color = `ctaTextColor` so they match the CTA's theme colors. Star rating formats as `"★ %.1f"`. Each pill's visibility is toggled based on whether AdMob delivered the asset (`isNullOrBlank` / `null`). Ad badge keeps the XML drawable (yellow + black border) — NOT overridden at runtime.
- **update (dimensions, when `adMediumDims != null`):** walks the view tree applying every scaled dim. Outer LinearLayout `layoutParams.height = slotHeightDp.toPx()`; left col `MarginLayoutParams.marginEnd = leftColMarginEndDp.toPx()`; MediaView FrameLayout `width/height`; icon `width/height + 4 margins`; advertiser/headline/body/CTA text sizes via `setTextSize(SP, ...)`; body `topMargin`; CTA `setPadding(...)` + `bottomMargin`; pills + badge `setTextSize` + `setPadding` + 4-margin `setMargins(...)`. **layoutParams must be re-assigned** (`view.layoutParams = (layoutParams as MarginLayoutParams).apply { ... }`) — mutating in place doesn't trigger `requestLayout`. Runs on every recomposition since `widthDp` is captured through Compose state, so dp/density changes propagate within one frame.

In debug builds only, every load logs to `BudgeTrakApplication.tokenLog`:
```
Ad load: tier=medium adChoicesInfo=false advertiser=null icon=true price=FREE store=Google Play star=4.5 body=...
```

This writes to `/Download/BudgeTrak/support/token_log.txt` so it's readable from Termux without ADB. Test ads omit `advertiser` and `adChoicesInfo` — production ads provide both.

## In-house fallback ad

When `AdLoader.onAdFailedToLoad` fires, the AdMob `AndroidView` is replaced by a pure-Compose in-house promo. Five fixed-order ads cycle on each subsequent failure; `inHouseAdIndex` resumes (not resets) across AdMob recoveries.

**Five ad themes (in order):** Receipts (Paid), Exports (Paid), SYNC (Subscriber), Simulation (Paid), OCR (Subscriber). Each entry in `InHouseAds: List<InHouseAd>` has a unique `id`, a Material `ImageVector` (feature icon), and a `tier` (PAID vs SUBSCRIBER drives CTA text + Play Billing price selection).

**Structural parity with AdMob** (so the slot doesn't visually jump on swap):
- **Small in-house:** 3 columns — left 58dp (40dp feature icon + 15dp CTA), center (5 rows: hardcoded `"BudgeTrak"` bold-underlined as advertiser, headline, 3-line body), right 58dp (PriceBadge centered; falls back to `UpgradeBadge` if Play Billing prices haven't loaded yet). Small tier uses fixed dp values, not `AdMediumDims`.
- **Medium in-house:** `MediumInHouseAd(ad, strings, dims: AdMediumDims, ...)` — every size pulled from the passed-in `dims` so it scales identically to the AdMob template. Top section: 30dp-at-base feature icon + vertical column with bold `"BudgeTrak"` advertiser (underlined) and headline (left-justified `TextAlign.Start`, `maxLines=2`). Body below, CTA at bottom via `Spacer.weight(1f)`. Right Box sized to `dims.mediaWidthDp × dims.slotHeightDp` with the BudgeTrak app icon centered (sized via `dims.inhouseAppIconDp`) + `PriceBadge` overlay at bottom-start mirroring AdMob's price-pill location.

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
- **Don't** re-create the `values-w600dp/dimens.xml` + `values-w800dp/dimens.xml` step-function tier system. Continuous scaling via `computeAdMediumDims(widthDp)` replaces it — anything that varied by tier should now be a `s × base` term in that function.
- **Don't** delete entries from `res/values/dimens.xml` for `ad_*`. `native_ad_medium.xml` still references them via `@dimen/ad_*` and inflation fails without them; the `update` lambda overrides those base values at runtime.
- **Don't** mutate `view.layoutParams` in place without re-assigning it (`view.layoutParams = view.layoutParams.apply { ... }`). The View's `setLayoutParams` setter is what triggers `requestLayout` — direct mutation leaves the layout stale.
- **Don't** revert the icon+vertical-column top section of `native_ad_medium.xml`. The icon now spans advertiser + headline-row-1 with both texts left-justified to the right of it; this was an explicit design decision (2026-05-15), not a layout accident. Reverting to a separate icon+advertiser row with a centered headline below would shrink the headline's char budget back to 26 chars/line but at the cost of the icon-anchor visual.
- **Don't** chase validator-UI "MediaView too small" warnings that persist across runtime tier flips. See `feedback_admob_validator_dp_transition.md`.

package com.techadvantage.budgetrak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techadvantage.budgetrak.R
import com.techadvantage.budgetrak.ui.strings.AppStrings

/** TextStyle that mirrors XML TextView `includeFontPadding=false` line spacing —
 *  removes leading half-leading on first/last lines so Compose renders text as
 *  tightly as the AdMob XML templates. Use with explicit `lineHeight ≈ fontSize
 *  × 1.15` to match XML's natural ascent+descent. */
@Composable
private fun tightTextStyle(): TextStyle = LocalTextStyle.current.copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** Read an sp-unit dimension from resources as a Compose TextUnit, preserving
 *  fontScale (system "large fonts" preference) like a Compose `.sp` literal. */
@Composable
private fun textSizeResource(@androidx.annotation.DimenRes id: Int): TextUnit {
    val resources = LocalContext.current.resources
    val typedValue = android.util.TypedValue()
    resources.getValue(id, typedValue, true)
    val px = typedValue.getDimension(resources.displayMetrics)
    val sp = px / resources.displayMetrics.scaledDensity
    return sp.sp
}

/**
 * In-house promotional ad content shown in the banner slot when AdMob
 * fails to fill (offline, no creative available, etc.). Five fixed-order
 * entries cycle on each subsequent failure; index resumes (not resets)
 * across AdMob recoveries so a free user sees variety over a day rather
 * than the same promo every time AdMob hiccups.
 *
 * Anti-piracy side benefit: a free user who blocks the app's internet
 * to dodge AdMob still sees our own upgrade promo instead of a blank
 * slot or stale ad.
 *
 * Layout matches the AdMob templates so the slot doesn't visually jump
 * when swapping between fallback and live ad — small (64 dp, no media)
 * and medium (144 dp, 160×120 media box rendering the app icon).
 */
enum class InHouseAdTier { PAID, SUBSCRIBER }

data class InHouseAd(
    val id: String,
    val icon: ImageVector,
    val tier: InHouseAdTier
)

/** Continuously-scaled medium ad dimensions, derived linearly from the
 *  400dp base values. Scale = `widthDp / 400`, clamped at 1.0 floor. So
 *  600dp → 1.5×, 800dp → 2.0×, foldable open at 1080dp → 2.7×. Replaces
 *  the old step-function `values{-w600dp,-w800dp}/dimens.xml` so ad
 *  elements grow smoothly with screen width like the rest of the app.
 *  Both the AdMob template (applied at runtime in MainActivity's
 *  AndroidView.update) and the in-house Compose mirror read from this. */
data class AdMediumDims(
    val slotHeightDp: Float,
    val mediaWidthDp: Float,
    val iconSizeDp: Float,
    val iconMarginDp: Float,
    val iconMarginBottomDp: Float,
    val advertiserSp: Float,
    val headlineSp: Float,
    val bodySp: Float,
    val bodyMarginTopDp: Float,
    val ctaSp: Float,
    val ctaPaddingHDp: Float,
    val ctaPaddingVDp: Float,
    val ctaMarginBottomDp: Float,
    val pillSp: Float,
    val pillPaddingHDp: Float,
    val pillPaddingVDp: Float,
    val pillMarginDp: Float,
    val badgeSp: Float,
    val badgePaddingHDp: Float,
    val badgePaddingVDp: Float,
    val inhouseAppIconDp: Float,
    val leftColMarginEndDp: Float,
)

fun computeAdMediumDims(widthDp: Int): AdMediumDims {
    // Proportional scaling: `widthDp / 400`, floored at 1.0×, unbounded above.
    // The ad keeps a constant fraction of screen width at every dp — apparent
    // size on a given screen doesn't change with display density. 1.5× at
    // 600dp, 2.0× at 800dp, 2.57× at 1028dp, 2.7× at foldable 1080dp.
    // App content uses a different (gentler) scaler in MainActivity so more
    // fits on tablet portraits.
    val s = (widthDp / 400f).coerceAtLeast(1.0f)
    return AdMediumDims(
        slotHeightDp = 120f * s,
        mediaWidthDp = 214f * s,
        iconSizeDp = 30f * s,
        iconMarginDp = 4f * s,
        iconMarginBottomDp = 0f,
        advertiserSp = 10f * s,
        headlineSp = 14.5f * s,
        bodySp = 11.5f * s,
        bodyMarginTopDp = 0f,
        ctaSp = 13f * s,
        ctaPaddingHDp = 14f * s,
        ctaPaddingVDp = 5f * s,
        ctaMarginBottomDp = 3f * s,
        pillSp = 10f * s,
        pillPaddingHDp = 6f * s,
        pillPaddingVDp = 2f * s,
        pillMarginDp = 4f * s,
        badgeSp = 10f * s,
        badgePaddingHDp = 5f * s,
        badgePaddingVDp = 1f * s,
        inhouseAppIconDp = 100f * s,
        leftColMarginEndDp = 5f * s,
    )
}

/** Fixed display order; we advance through this on each AdMob load failure. */
val InHouseAds: List<InHouseAd> = listOf(
    InHouseAd("receipts",   Icons.Filled.CameraAlt,    InHouseAdTier.PAID),
    InHouseAd("exports",    Icons.Filled.FileDownload, InHouseAdTier.PAID),
    InHouseAd("sync",       Icons.Filled.Groups,       InHouseAdTier.SUBSCRIBER),
    InHouseAd("simulation", Icons.AutoMirrored.Filled.ShowChart, InHouseAdTier.PAID),
    InHouseAd("ocr",        Icons.Filled.AutoAwesome,  InHouseAdTier.SUBSCRIBER),
)

private fun headlineFor(id: String, s: AppStrings): String = when (id) {
    "receipts"   -> s.ads.receiptsHeadline
    "exports"    -> s.ads.exportsHeadline
    "sync"       -> s.ads.syncHeadline
    "simulation" -> s.ads.simulationHeadline
    "ocr"        -> s.ads.ocrHeadline
    else -> ""
}

private fun bodyFor(id: String, s: AppStrings): String = when (id) {
    "receipts"   -> s.ads.receiptsBody
    "exports"    -> s.ads.exportsBody
    "sync"       -> s.ads.syncBody
    "simulation" -> s.ads.simulationBody
    "ocr"        -> s.ads.ocrBody
    else -> ""
}

/** Sealed content type for the unified medium-tier renderer. AdMob branch
 *  carries a NativeAd whose assets drive every TextView/ImageView; in-house
 *  branch carries app-controlled text + a rasterized feature-icon bitmap
 *  (Material ImageVector → Bitmap via rememberImageVectorBitmap). */
sealed class AdMediumContent {
    data class AdMob(
        val nativeAd: com.google.android.gms.ads.nativead.NativeAd,
    ) : AdMediumContent()

    data class InHouse(
        val advertiser: String,
        val headline: String,
        val body: String,
        val ctaText: String,
        val featureIcon: android.graphics.Bitmap?,
        val price: String?,
        val onClick: () -> Unit,
    ) : AdMediumContent()
}

/** Rasterize a Compose ImageVector into a Bitmap so it can be set on an
 *  inflated XML ImageView. Used for the in-house feature icon — Material
 *  ImageVectors are Compose-native and can't go directly into a regular
 *  ImageView. Cached via `remember` keyed on vector/size/tint, so the
 *  bitmap is built once per icon+tier and reused across recompositions. */
@Composable
fun rememberImageVectorBitmap(
    vector: androidx.compose.ui.graphics.vector.ImageVector,
    sizeDp: androidx.compose.ui.unit.Dp,
    tint: Color,
): android.graphics.Bitmap {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val sizePx = with(density) { sizeDp.roundToPx() }.coerceAtLeast(1)
    val painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(vector)
    return androidx.compose.runtime.remember(vector, sizePx, tint, density, layoutDirection, painter) {
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val androidCanvas = android.graphics.Canvas(bitmap)
        val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        scope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = composeCanvas,
            size = androidx.compose.ui.geometry.Size(sizePx.toFloat(), sizePx.toFloat()),
        ) {
            with(painter) {
                draw(
                    size = androidx.compose.ui.geometry.Size(sizePx.toFloat(), sizePx.toFloat()),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
                )
            }
        }
        bitmap
    }
}

/** Apply continuous-scale dimensions + theme colors to every view inside
 *  the inflated `native_ad_medium.xml`. Called by both the AdMob path
 *  (MainActivity update lambda) and the in-house path (MediumInHouseAdView
 *  update lambda) so visual scaling is guaranteed identical. */
fun applyMediumAdDimsAndColors(
    view: com.google.android.gms.ads.nativead.NativeAdView,
    dims: AdMediumDims,
    pageTextArgb: Int,
    ctaBgArgb: Int,
    ctaTextArgb: Int,
) {
    val density = view.resources.displayMetrics.density
    fun Float.toPx(): Int = (this * density).toInt()
    val slotPx = dims.slotHeightDp.toPx()

    val outerLL = view.getChildAt(0) as android.view.ViewGroup
    outerLL.layoutParams = outerLL.layoutParams.apply { height = slotPx }
    val leftCol = outerLL.getChildAt(0)
    leftCol.layoutParams = (leftCol.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
        marginEnd = dims.leftColMarginEndDp.toPx()
    }
    val mediaFrame = outerLL.getChildAt(1)
    mediaFrame.layoutParams = mediaFrame.layoutParams.apply {
        width = dims.mediaWidthDp.toPx()
        height = slotPx
    }

    val iconView = view.findViewById<android.widget.ImageView>(R.id.native_ad_icon)
    val headlineView = view.findViewById<android.widget.TextView>(R.id.native_ad_headline)
    val advertiserView = view.findViewById<android.widget.TextView>(R.id.native_ad_advertiser)
    val bodyView = view.findViewById<android.widget.TextView>(R.id.native_ad_body)
    val ctaView = view.findViewById<android.widget.Button>(R.id.native_ad_cta)
    val priceView = view.findViewById<android.widget.TextView>(R.id.native_ad_price)
    val storeView = view.findViewById<android.widget.TextView>(R.id.native_ad_store)
    val starView = view.findViewById<android.widget.TextView>(R.id.native_ad_star)
    val badgeView = view.findViewById<android.widget.TextView>(R.id.native_ad_badge)
    val inhouseIconView = view.findViewById<android.widget.ImageView>(R.id.native_ad_inhouse_icon)

    iconView?.let { iv ->
        iv.layoutParams = (iv.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
            width = dims.iconSizeDp.toPx()
            height = dims.iconSizeDp.toPx()
            marginStart = dims.iconMarginDp.toPx()
            topMargin = dims.iconMarginDp.toPx()
            marginEnd = dims.iconMarginDp.toPx()
            bottomMargin = dims.iconMarginBottomDp.toPx()
        }
    }
    advertiserView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dims.advertiserSp)
    headlineView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dims.headlineSp)
    bodyView?.let { bv ->
        bv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dims.bodySp)
        bv.layoutParams = (bv.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
            topMargin = dims.bodyMarginTopDp.toPx()
        }
        // Symmetric edge gap: body's left mirrors the left col's marginEnd
        // (right gap from MediaView). Reuses leftColMarginEndDp so both gaps
        // scale together. Advertiser/headline already get the right-side gap
        // for free via the parent left col's marginEnd.
        bv.setPaddingRelative(dims.leftColMarginEndDp.toPx(), 0, 0, 0)
    }
    ctaView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dims.ctaSp)
    ctaView.setPadding(
        dims.ctaPaddingHDp.toPx(),
        dims.ctaPaddingVDp.toPx(),
        dims.ctaPaddingHDp.toPx(),
        dims.ctaPaddingVDp.toPx(),
    )
    ctaView.layoutParams = (ctaView.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
        bottomMargin = dims.ctaMarginBottomDp.toPx()
    }
    val pillMarginPx = dims.pillMarginDp.toPx()
    listOf(priceView, storeView, starView).forEach { pill ->
        pill?.let { p ->
            p.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dims.pillSp)
            p.setPadding(
                dims.pillPaddingHDp.toPx(),
                dims.pillPaddingVDp.toPx(),
                dims.pillPaddingHDp.toPx(),
                dims.pillPaddingVDp.toPx(),
            )
            (p.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mp ->
                mp.setMargins(pillMarginPx, pillMarginPx, pillMarginPx, pillMarginPx)
                p.layoutParams = mp
            }
        }
    }
    badgeView?.let { bv ->
        bv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dims.badgeSp)
        bv.setPadding(
            dims.badgePaddingHDp.toPx(),
            dims.badgePaddingVDp.toPx(),
            dims.badgePaddingHDp.toPx(),
            dims.badgePaddingVDp.toPx(),
        )
        (bv.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mp ->
            mp.setMargins(pillMarginPx, pillMarginPx, pillMarginPx, pillMarginPx)
            bv.layoutParams = mp
        }
    }
    inhouseIconView?.let { iv ->
        iv.layoutParams = iv.layoutParams.apply {
            width = dims.inhouseAppIconDp.toPx()
            height = dims.inhouseAppIconDp.toPx()
        }
    }

    // Theme colors
    headlineView.setTextColor(pageTextArgb)
    advertiserView?.setTextColor(pageTextArgb)
    advertiserView?.paintFlags = (advertiserView?.paintFlags ?: 0) or android.graphics.Paint.UNDERLINE_TEXT_FLAG
    bodyView?.setTextColor(pageTextArgb)

    ctaView.background = android.graphics.drawable.GradientDrawable().apply {
        setColor(ctaBgArgb)
        cornerRadius = 6f * density
    }
    ctaView.setTextColor(ctaTextArgb)

    fun pillBg() = android.graphics.drawable.GradientDrawable().apply {
        setColor(ctaBgArgb)
        cornerRadius = 3f * density
    }
    priceView?.background = pillBg()
    priceView?.setTextColor(ctaTextArgb)
    storeView?.background = pillBg()
    storeView?.setTextColor(ctaTextArgb)
    starView?.background = pillBg()
    starView?.setTextColor(ctaTextArgb)

    if (com.techadvantage.budgetrak.BuildConfig.DEBUG) {
        com.techadvantage.budgetrak.BudgeTrakApplication.tokenLog(
            "applyDims: slot=${dims.slotHeightDp} icon=${dims.iconSizeDp} ctaSp=${dims.ctaSp} ctaPadV=${dims.ctaPaddingVDp} advSp=${dims.advertiserSp} headSp=${dims.headlineSp} bodySp=${dims.bodySp}"
        )
    }
    view.requestLayout()
}

/** Bind content (AdMob asset data OR in-house upgrade promo) to the
 *  inflated `native_ad_medium.xml`. Visibility of AdMob-only views
 *  (MediaView/Ad badge/AdChoicesView/store/star) and the in-house BudgeTrak
 *  app-icon ImageView is toggled per branch. Caller must have already
 *  invoked `applyMediumAdDimsAndColors` so sizes + colors are current. */
fun bindMediumAdContent(
    view: com.google.android.gms.ads.nativead.NativeAdView,
    content: AdMediumContent,
    pageTextArgb: Int,
) {
    val iconView = view.findViewById<android.widget.ImageView>(R.id.native_ad_icon)
    val headlineView = view.findViewById<android.widget.TextView>(R.id.native_ad_headline)
    val advertiserView = view.findViewById<android.widget.TextView>(R.id.native_ad_advertiser)
    val bodyView = view.findViewById<android.widget.TextView>(R.id.native_ad_body)
    val ctaView = view.findViewById<android.widget.Button>(R.id.native_ad_cta)
    val priceView = view.findViewById<android.widget.TextView>(R.id.native_ad_price)
    val storeView = view.findViewById<android.widget.TextView>(R.id.native_ad_store)
    val starView = view.findViewById<android.widget.TextView>(R.id.native_ad_star)
    val badgeView = view.findViewById<android.widget.TextView>(R.id.native_ad_badge)
    val mediaView = view.findViewById<com.google.android.gms.ads.nativead.MediaView>(R.id.native_ad_media)
    val adChoicesView = view.findViewById<com.google.android.gms.ads.nativead.AdChoicesView>(R.id.native_ad_choices)
    val inhouseIconView = view.findViewById<android.widget.ImageView>(R.id.native_ad_inhouse_icon)

    when (content) {
        is AdMediumContent.AdMob -> {
            mediaView?.visibility = android.view.View.VISIBLE
            badgeView?.visibility = android.view.View.VISIBLE
            adChoicesView?.visibility = android.view.View.VISIBLE
            inhouseIconView?.visibility = android.view.View.GONE
            iconView?.clearColorFilter()
            view.setOnClickListener(null)
            view.isClickable = false

            val ad = content.nativeAd
            headlineView.text = ad.headline ?: ""
            advertiserView?.text = ad.advertiser ?: ""
            ctaView.text = ad.callToAction ?: ""
            bodyView?.text = ad.body ?: ""
            ad.icon?.drawable?.let { iconView?.setImageDrawable(it) }
            priceView?.let {
                val p = ad.price
                if (p.isNullOrBlank()) it.visibility = android.view.View.GONE
                else { it.text = p; it.visibility = android.view.View.VISIBLE }
            }
            storeView?.let {
                val s = ad.store
                if (s.isNullOrBlank()) it.visibility = android.view.View.GONE
                else { it.text = s; it.visibility = android.view.View.VISIBLE }
            }
            starView?.let {
                val r = ad.starRating
                if (r == null) it.visibility = android.view.View.GONE
                else { it.text = "★ %.1f".format(r); it.visibility = android.view.View.VISIBLE }
            }
            view.setNativeAd(ad)
        }
        is AdMediumContent.InHouse -> {
            mediaView?.visibility = android.view.View.GONE
            badgeView?.visibility = android.view.View.GONE
            adChoicesView?.visibility = android.view.View.GONE
            storeView?.visibility = android.view.View.GONE
            starView?.visibility = android.view.View.GONE
            inhouseIconView?.visibility = android.view.View.VISIBLE
            iconView?.setColorFilter(pageTextArgb)
            content.featureIcon?.let { iconView?.setImageBitmap(it) }

            headlineView.text = content.headline
            advertiserView?.text = content.advertiser
            ctaView.text = content.ctaText.uppercase()
            bodyView?.text = content.body

            priceView?.let {
                if (content.price.isNullOrBlank()) it.visibility = android.view.View.GONE
                else { it.text = content.price; it.visibility = android.view.View.VISIBLE }
            }

            view.isClickable = true
            view.setOnClickListener { content.onClick() }
            // AdMob no-op when in-house is showing
            view.callToActionView = null
        }
    }
}

@Composable
fun InHouseAdSlot(
    ad: InHouseAd,
    strings: AppStrings,
    isMediumTier: Boolean,
    headerTextColor: Color,
    ctaBgColor: Color,
    ctaTextColor: Color,
    onClick: () -> Unit,
    paidUpgradePrice: String?,
    subscriberPrice: String?,
    mediumDims: AdMediumDims? = null,
    modifier: Modifier = Modifier
) {
    val ctaText = if (ad.tier == InHouseAdTier.PAID) strings.ads.upgradeCta else strings.ads.subscribeCta
    val price = if (ad.tier == InHouseAdTier.PAID) paidUpgradePrice else subscriberPrice
    if (isMediumTier && mediumDims != null) {
        // Medium tier: shared XML inflation path so structural layout
        // changes (icon position, headline alignment, etc.) live in one
        // place. The whole-view click is set inside bindMediumAdContent.
        MediumInHouseAdView(
            ad = ad,
            strings = strings,
            dims = mediumDims,
            headerTextColor = headerTextColor,
            ctaBgColor = ctaBgColor,
            ctaTextColor = ctaTextColor,
            ctaText = ctaText,
            price = price,
            onClick = onClick,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.clickable(onClick = onClick)) {
            SmallInHouseAd(ad, strings, headerTextColor, ctaBgColor, ctaTextColor, ctaText, price)
        }
    }
}

@Composable
private fun MediumInHouseAdView(
    ad: InHouseAd,
    strings: AppStrings,
    dims: AdMediumDims,
    headerTextColor: Color,
    ctaBgColor: Color,
    ctaTextColor: Color,
    ctaText: String,
    price: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val featureIcon = rememberImageVectorBitmap(ad.icon, dims.iconSizeDp.dp, headerTextColor)
    val headlineText = headlineFor(ad.id, strings)
    val bodyText = bodyFor(ad.id, strings)
    val pageTextArgb = headerTextColor.toArgb()
    val ctaBgArgb = ctaBgColor.toArgb()
    val ctaTextArgb = ctaTextColor.toArgb()
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.native_ad_medium, null) as com.google.android.gms.ads.nativead.NativeAdView
        },
        update = { view ->
            applyMediumAdDimsAndColors(view, dims, pageTextArgb, ctaBgArgb, ctaTextArgb)
            bindMediumAdContent(
                view,
                AdMediumContent.InHouse(
                    advertiser = "BudgeTrak",
                    headline = headlineText,
                    body = bodyText,
                    ctaText = ctaText,
                    featureIcon = featureIcon,
                    price = price,
                    onClick = onClick,
                ),
                pageTextArgb,
            )
        },
    )
}

@Composable
private fun PriceBadge(
    price: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    paddingH: androidx.compose.ui.unit.Dp = 6.dp,
    paddingV: androidx.compose.ui.unit.Dp = 4.dp,
) {
    // Mirrors the AdMob price overlay: theme primary/onPrimary so the pill
    // matches the CTA's color scheme. One size up from AdMob's price pill —
    // fills the otherwise-empty 3rd column in the small in-house template.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
            .padding(horizontal = paddingH, vertical = paddingV)
    ) {
        Text(
            text = price,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun UpgradeBadge(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    paddingH: androidx.compose.ui.unit.Dp = 5.dp,
    paddingV: androidx.compose.ui.unit.Dp = 1.dp,
) {
    // Mirrors native_ad_badge_bg.xml — yellow #FFCC00 chip with thin black
    // border + bold black text. Default size matches the AdMob "Ad" pill;
    // small in-house pumps the values up to fill its lonely right column.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFFFFCC00))
            .border(
                width = 1.dp,
                color = Color.Black,
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = paddingH, vertical = paddingV)
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CtaButton(
    text: String,
    bg: Color,
    fg: Color,
    paddingH: Int = 16,
    paddingV: Int = 6,
    ctaSpOverride: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = paddingH.dp, vertical = paddingV.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ctaFontSize = if (ctaSpOverride != TextUnit.Unspecified) ctaSpOverride else 12.sp
        Text(
            text = text.uppercase(),
            color = fg,
            fontSize = ctaFontSize,
            lineHeight = ctaFontSize * 1.15f,
            fontWeight = FontWeight.Bold,
            style = tightTextStyle(),
        )
    }
}

@Composable
private fun SmallInHouseAd(
    ad: InHouseAd,
    strings: AppStrings,
    headerTextColor: Color,
    ctaBgColor: Color,
    ctaTextColor: Color,
    ctaText: String,
    price: String?,
) {
    // Mirrors native_ad_small.xml 3-column 70 dp layout. Left 58 dp: 40 dp
    // feature icon + 15 dp CTA. Center: 5 rows — hardcoded "BudgeTrak" in
    // the advertiser slot (in-house has no advertiser asset; this keeps the
    // 5-row visual structure on swap) + headline + 3-line body. Right 58 dp:
    // Upgrade badge top-start, no data pills (in-house has no shopping data).
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(58.dp)
                .fillMaxHeight()
                .padding(top = 5.dp, bottom = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = ad.icon,
                contentDescription = null,
                tint = headerTextColor,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(5.dp))
            CtaButton(
                ctaText,
                ctaBgColor,
                ctaTextColor,
                paddingH = 8,
                paddingV = 0,
                ctaSpOverride = 9.sp,
                modifier = Modifier.height(15.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "BudgeTrak",
                color = headerTextColor,
                fontSize = 10.sp,
                lineHeight = 11.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = headlineFor(ad.id, strings),
                color = headerTextColor,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = bodyFor(ad.id, strings),
                color = headerTextColor,
                fontSize = 10.sp,
                lineHeight = 11.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .width(58.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // PriceBadge fills the otherwise-empty right column (in-house omits
            // store/stars/Ad badge/AdChoices). Falls back to UpgradeBadge while
            // Play Billing prices are still loading.
            if (price != null) {
                PriceBadge(price)
            } else {
                UpgradeBadge(
                    label = strings.ads.upgradeBadge,
                    fontSize = 12.sp,
                    paddingH = 6.dp,
                    paddingV = 4.dp,
                )
            }
        }
    }
}


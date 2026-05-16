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
    val s = (widthDp / 400f).coerceAtLeast(1.0f)
    return AdMediumDims(
        slotHeightDp = 120f * s,
        mediaWidthDp = 214f * s,
        iconSizeDp = 30f * s,
        iconMarginDp = 4f * s,
        iconMarginBottomDp = 0f,
        advertiserSp = 11f * s,
        headlineSp = 14f * s,
        bodySp = 12f * s,
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
        leftColMarginEndDp = 4f * s,
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
    Box(modifier = modifier.clickable(onClick = onClick)) {
        if (isMediumTier && mediumDims != null) {
            MediumInHouseAd(ad, strings, mediumDims, headerTextColor, ctaBgColor, ctaTextColor, ctaText, price)
        } else {
            SmallInHouseAd(ad, strings, headerTextColor, ctaBgColor, ctaTextColor, ctaText, price)
        }
    }
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

@Composable
private fun MediumInHouseAd(
    ad: InHouseAd,
    strings: AppStrings,
    dims: AdMediumDims,
    headerTextColor: Color,
    ctaBgColor: Color,
    ctaTextColor: Color,
    ctaText: String,
    price: String?,
) {
    // Mirrors native_ad_medium.xml. All sizes scale continuously with screen
    // width via computeAdMediumDims(widthDp) — 400dp base × (widthDp/400)
    // so the layout grows smoothly with display size rather than stepping
    // at w600dp / w800dp breakpoints.
    val iconMargin = dims.iconMarginDp.dp
    val pillMargin = dims.pillMarginDp.dp
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = dims.leftColMarginEndDp.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.padding(
                        start = iconMargin,
                        top = iconMargin,
                        end = iconMargin,
                        bottom = dims.iconMarginBottomDp.dp,
                    )
                ) {
                    Icon(
                        imageVector = ad.icon,
                        contentDescription = null,
                        tint = headerTextColor,
                        modifier = Modifier.size(dims.iconSizeDp.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val advertiserSp = dims.advertiserSp.sp
                    Text(
                        text = "BudgeTrak",
                        color = headerTextColor,
                        fontSize = advertiserSp,
                        lineHeight = advertiserSp * 1.15f,
                        fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = tightTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val headlineSp = dims.headlineSp.sp
                    Text(
                        text = headlineFor(ad.id, strings),
                        color = headerTextColor,
                        fontSize = headlineSp,
                        lineHeight = headlineSp * 1.15f,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = tightTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(dims.bodyMarginTopDp.dp))
            val bodySp = dims.bodySp.sp
            Text(
                text = bodyFor(ad.id, strings),
                color = headerTextColor,
                fontSize = bodySp,
                lineHeight = bodySp * 1.15f,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            CtaButton(
                ctaText,
                ctaBgColor,
                ctaTextColor,
                paddingH = dims.ctaPaddingHDp.toInt(),
                paddingV = dims.ctaPaddingVDp.toInt(),
                ctaSpOverride = dims.ctaSp.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = dims.ctaMarginBottomDp.dp),
            )
        }
        Box(
            modifier = Modifier
                .width(dims.mediaWidthDp.dp)
                .height(dims.slotHeightDp.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(dims.inhouseAppIconDp.dp),
            )
            // Price overlay bottom-start, mirroring AdMob's price pill location.
            if (price != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(pillMargin),
                ) {
                    PriceBadge(price)
                }
            }
        }
    }
}

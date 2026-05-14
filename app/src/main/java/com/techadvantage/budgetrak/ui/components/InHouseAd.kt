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
    modifier: Modifier = Modifier
) {
    val ctaText = if (ad.tier == InHouseAdTier.PAID) strings.ads.upgradeCta else strings.ads.subscribeCta
    val price = if (ad.tier == InHouseAdTier.PAID) paidUpgradePrice else subscriberPrice
    Box(modifier = modifier.clickable(onClick = onClick)) {
        if (isMediumTier) {
            MediumInHouseAd(ad, strings, headerTextColor, ctaBgColor, ctaTextColor, ctaText, price)
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
    headerTextColor: Color,
    ctaBgColor: Color,
    ctaTextColor: Color,
    ctaText: String,
    price: String?,
) {
    // Mirrors native_ad_medium.xml. All sizes (slot height, MediaView width,
    // icon size, padding, text sizes) are resource-driven via
    // res/values{,-w600dp,-w800dp}/dimens.xml so the layout scales up on
    // tablets / foldables / large displays without per-tier branching here.
    val iconMargin = dimensionResource(R.dimen.ad_icon_margin)
    val iconMarginBottom = dimensionResource(R.dimen.ad_icon_margin_bottom)
    val pillMargin = dimensionResource(R.dimen.ad_pill_margin)
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = dimensionResource(R.dimen.ad_left_col_margin_end)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.padding(
                        start = iconMargin,
                        top = iconMargin,
                        end = iconMargin,
                        bottom = iconMarginBottom,
                    )
                ) {
                    Icon(
                        imageVector = ad.icon,
                        contentDescription = null,
                        tint = headerTextColor,
                        modifier = Modifier.size(dimensionResource(R.dimen.ad_icon_size)),
                    )
                }
                val advertiserSp = textSizeResource(R.dimen.ad_advertiser_text_size)
                Text(
                    text = "BudgeTrak",
                    color = headerTextColor,
                    fontSize = advertiserSp,
                    lineHeight = advertiserSp * 1.15f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightTextStyle(),
                    modifier = Modifier
                        .padding(start = pillMargin)
                        .weight(1f),
                )
            }
            val headlineSp = textSizeResource(R.dimen.ad_headline_text_size)
            Text(
                text = headlineFor(ad.id, strings),
                color = headerTextColor,
                fontSize = headlineSp,
                lineHeight = headlineSp * 1.15f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(dimensionResource(R.dimen.ad_body_margin_top)))
            val bodySp = textSizeResource(R.dimen.ad_body_text_size)
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
                paddingH = dimensionResource(R.dimen.ad_cta_padding_h).value.toInt(),
                paddingV = dimensionResource(R.dimen.ad_cta_padding_v).value.toInt(),
                ctaSpOverride = textSizeResource(R.dimen.ad_cta_text_size),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = dimensionResource(R.dimen.ad_cta_margin_bottom)),
            )
        }
        Box(
            modifier = Modifier
                .width(dimensionResource(R.dimen.ad_media_width))
                .height(dimensionResource(R.dimen.ad_slot_height)),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(dimensionResource(R.dimen.ad_inhouse_app_icon_size)),
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

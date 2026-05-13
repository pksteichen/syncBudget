package com.techadvantage.budgetrak.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techadvantage.budgetrak.R
import com.techadvantage.budgetrak.ui.strings.AppStrings

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
    modifier: Modifier = Modifier
) {
    val ctaText = if (ad.tier == InHouseAdTier.PAID) strings.ads.upgradeCta else strings.ads.subscribeCta
    Box(modifier = modifier.clickable(onClick = onClick)) {
        if (isMediumTier) {
            MediumInHouseAd(ad, strings, headerTextColor, ctaBgColor, ctaTextColor, ctaText)
        } else {
            SmallInHouseAd(ad, strings, headerTextColor, ctaBgColor, ctaTextColor, ctaText)
        }
    }
}

@Composable
private fun UpgradeBadge(label: String) {
    // Mirrors native_ad_badge_bg.xml — yellow #FFCC00 rounded chip with bold
    // 10sp black text. Visual continuity with AdMob's badge slot, just with
    // "Upgrade" / "Mejora" copy in place of "Ad" since this is 1st-party.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFFFFCC00))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontSize = 10.sp,
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = paddingH.dp, vertical = paddingV.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
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
) {
    // Matches native_ad_small.xml: horizontal layout, 48 dp feature icon,
    // middle column (badge + 1-line headline), CTA button on the right.
    // No body, no MediaView — fits the 64 dp slot.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ad.icon,
            contentDescription = null,
            tint = headerTextColor,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            UpgradeBadge(strings.ads.upgradeBadge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = headlineFor(ad.id, strings),
                color = headerTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        CtaButton(ctaText, ctaBgColor, ctaTextColor, paddingH = 14, paddingV = 6)
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
) {
    // Mirrors native_ad_medium.xml: 120 dp tall row, no outer padding.
    // Left column (weight 1, vertically centered) — 2-line headline,
    // 2-line body, full-width 40 dp CTA. Right Box 214×120 dp shows the
    // BudgeTrak app icon with the "Upgrade" badge overlaid top-end
    // (mirroring the "Ad" badge that overlays the real MediaView).
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = headlineFor(ad.id, strings),
                color = headerTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = bodyFor(ad.id, strings),
                color = headerTextColor,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            CtaButton(
                ctaText,
                ctaBgColor,
                ctaTextColor,
                paddingV = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .width(214.dp)
                .height(120.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(100.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                UpgradeBadge(strings.ads.upgradeBadge)
            }
        }
    }
}

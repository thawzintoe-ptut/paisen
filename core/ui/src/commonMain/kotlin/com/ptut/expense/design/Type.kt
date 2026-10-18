package com.ptut.expense.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language

/**
 * Tabular figures, on everything.
 *
 * The design marks the money roles tabular, but a slot is not a role: `titleMedium` sets a row's
 * amount and the merchant beside it, so one slot cannot be tabular for one and proportional for
 * the other. Every number in this app is worth aligning anyway — amounts, counts, elapsed times —
 * and the feature only touches digits, so applying it once here is the rule that cannot be
 * forgotten at a call site. It replaces the sixteen `.merge(MoneyTextStyle)` calls that did.
 */
private const val TABULAR_FIGURES = "tnum"

internal fun style(
    family: FontFamily,
    size: Float,
    weight: FontWeight,
    lineHeight: TextUnit,
    tracking: TextUnit = 0.em,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    fontFeatureSettings = TABULAR_FIGURES,
)

/** Material's slots in Geist; [skyTypography] says what it keeps from the table below. */
@Composable
fun ptutTypography(): Typography {
    val sky = skyTypography(geistFamily())
    return if (writtenInBurmese()) sky.inBurmese() else sky
}

/** Read once per composition: `App` rebuilds everything below it when the language changes. */
private fun writtenInBurmese() = AppText.language.value == Language.Burmese

/**
 * Geist has no Myanmar letters, so the phone draws them in its own Myanmar font. Burmese stacks
 * marks above and below the line, so its text takes that font's line height; Geist's fixed ones
 * would let two lines touch. Its tracking is left at zero for the same reason.
 */
private fun TextStyle.inBurmese() = copy(lineHeight = TextUnit.Unspecified, letterSpacing = 0.em)

private fun Typography.inBurmese() = copy(
    displayLarge = displayLarge.inBurmese(),
    displayMedium = displayMedium.inBurmese(),
    displaySmall = displaySmall.inBurmese(),
    headlineLarge = headlineLarge.inBurmese(),
    headlineMedium = headlineMedium.inBurmese(),
    headlineSmall = headlineSmall.inBurmese(),
    titleLarge = titleLarge.inBurmese(),
    titleMedium = titleMedium.inBurmese(),
    titleSmall = titleSmall.inBurmese(),
    bodyLarge = bodyLarge.inBurmese(),
    bodyMedium = bodyMedium.inBurmese(),
    bodySmall = bodySmall.inBurmese(),
    labelLarge = labelLarge.inBurmese(),
    labelMedium = labelMedium.inBurmese(),
    labelSmall = labelSmall.inBurmese(),
)

/** The word roles only; the amounts and the receipt keep their fixed line heights. */
private fun PtutType.inBurmese() = copy(
    screenTitle = screenTitle.inBurmese(),
    sectionTitle = sectionTitle.inBurmese(),
    listHeadline = listHeadline.inBurmese(),
    meta = meta.inBurmese(),
    eyebrow = eyebrow.inBurmese(),
)

/**
 * The handoff's type table on Material's slots. The half-steps are real values, not rounded — the
 * design sets 12.5 and 15.5 and the difference is visible at a row's size.
 *
 * Display sizes carry negative tracking because large text set at zero reads loose; the design
 * puts that between −0.02 and −0.04em and the larger the size, the tighter it goes.
 */
internal fun typographyOf(display: FontFamily, reading: FontFamily): Typography = Typography(
    displayLarge = style(display, 40f, FontWeight.SemiBold, 46.sp, (-0.03).em),
    displayMedium = style(display, 34f, FontWeight.SemiBold, 40.sp, (-0.03).em),
    displaySmall = style(display, 28f, FontWeight.SemiBold, 34.sp, (-0.02).em),
    headlineLarge = style(display, 26f, FontWeight.SemiBold, 32.sp, (-0.02).em),
    headlineMedium = style(display, 24f, FontWeight.SemiBold, 30.sp, (-0.02).em),
    headlineSmall = style(display, 22f, FontWeight.SemiBold, 28.sp, (-0.02).em),
    titleLarge = style(display, 20f, FontWeight.SemiBold, 26.sp, (-0.02).em),
    titleMedium = style(reading, 16f, FontWeight.Medium, 22.sp),
    titleSmall = style(reading, 14.5f, FontWeight.Medium, 20.sp),
    bodyLarge = style(reading, 15f, FontWeight.Normal, 21.sp),
    bodyMedium = style(reading, 13.5f, FontWeight.Normal, 20.sp),
    bodySmall = style(reading, 12.5f, FontWeight.Normal, 17.sp),
    labelLarge = style(reading, 14f, FontWeight.Medium, 19.sp),
    labelMedium = style(reading, 12.5f, FontWeight.Medium, 17.sp),
    labelSmall = style(reading, 11.5f, FontWeight.Medium, 15.sp),
)

/**
 * The roles Material has no slot for, carried the way [PtutColors] carries the extra colours.
 *
 * The three money sizes are separate roles rather than one reused slot on purpose: the hero on
 * Today, the amount on the add sheet and the total on Reports are 52, 48 and 46, and folding any
 * two together is how a size drifts.
 */
@Immutable
data class PtutType(
    val hero: TextStyle,
    /** The `฿` and the satang beside the hero, set smaller so the digits carry the amount. */
    val heroSymbol: TextStyle,
    val heroFraction: TextStyle,
    val sheet: TextStyle,
    val report: TextStyle,
    val display: TextStyle,
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val listHeadline: TextStyle,
    val meta: TextStyle,
    val eyebrow: TextStyle,
    /** The day receipt's Geist Mono (#120, p09): a line, the shop's heading, and the ruled total. */
    val receipt: TextStyle,
    val receiptHeading: TextStyle,
    val receiptTotal: TextStyle,
)

/**
 * Built inside [PtutTheme] rather than held in a top-level `val`: `Font(Res.font.X)` is a
 * composable call, so the family cannot exist before a composition does.
 *
 * The money roles are capped here, once, so every screen that reads them gets the same limit.
 * Their line-heights are in em, so the line box follows the capped size.
 */
@Composable
internal fun rememberPtutType(): PtutType {
    val sky = skyTypeOf(geistFamily(), geistMonoFamily())
    val written = if (writtenInBurmese()) sky.inBurmese() else sky
    return written.cappedFor(LocalDensity.current)
}

private fun PtutType.cappedFor(density: Density) = copy(
    hero = hero.cappedFor(density),
    heroSymbol = heroSymbol.cappedFor(density),
    heroFraction = heroFraction.cappedFor(density),
    sheet = sheet.cappedFor(density),
    report = report.cappedFor(density),
)

/** [moneySize], read through the platform's own sp-to-dp curve and handed back as sp. */
private fun TextStyle.cappedFor(density: Density): TextStyle = with(density) {
    val drawn = moneySize(designSize = fontSize.value, platformSize = fontSize.toDp().value)
    copy(fontSize = drawn.dp.toSp())
}

/**
 * The fallback is stock Material, which is what a build with no bundled font would have used
 * anyway. It is never read in the running app — [PtutTheme] always provides the real set.
 */
val LocalPtutType = staticCompositionLocalOf {
    val fallback = Typography()
    PtutType(
        hero = fallback.displayLarge,
        heroSymbol = fallback.headlineSmall,
        heroFraction = fallback.headlineSmall,
        sheet = fallback.displayLarge,
        report = fallback.displayMedium,
        display = fallback.displaySmall,
        screenTitle = fallback.headlineSmall,
        sectionTitle = fallback.titleLarge,
        listHeadline = fallback.titleMedium,
        meta = fallback.labelMedium,
        eyebrow = fallback.labelSmall,
        receipt = fallback.bodyMedium,
        receiptHeading = fallback.labelLarge,
        receiptTotal = fallback.headlineSmall,
    )
}

val MaterialTheme.ptutType: PtutType
    @Composable
    @ReadOnlyComposable
    get() = LocalPtutType.current

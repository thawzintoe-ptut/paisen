package com.ptut.expense.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ptut.expense.resources.Res
import com.ptut.expense.resources.geist_bold
import com.ptut.expense.resources.geist_light
import com.ptut.expense.resources.geist_medium
import com.ptut.expense.resources.geist_mono_regular
import com.ptut.expense.resources.geist_mono_semibold
import com.ptut.expense.resources.geist_regular
import com.ptut.expense.resources.geist_semibold
import org.jetbrains.compose.resources.Font

/** One family for every Sky role: its cuts run from the 300 the amounts use to the 700 an eyebrow needs. */
@Composable
internal fun geistFamily() = FontFamily(
    Font(Res.font.geist_light, FontWeight.Light),
    Font(Res.font.geist_regular, FontWeight.Normal),
    Font(Res.font.geist_medium, FontWeight.Medium),
    Font(Res.font.geist_semibold, FontWeight.SemiBold),
    Font(Res.font.geist_bold, FontWeight.Bold),
)

/** Geist Mono, the day receipt's face only (#120, p09). Two cuts: the lines and the heading and total. */
@Composable
internal fun geistMonoFamily() = FontFamily(
    Font(Res.font.geist_mono_regular, FontWeight.Normal),
    Font(Res.font.geist_mono_semibold, FontWeight.SemiBold),
)

/**
 * Sky's slots are p02's sizes, line heights and tracking in Geist. Sky draws no 600, so a
 * SemiBold slot drops to Medium.
 */
internal fun skyTypography(geist: FontFamily): Typography =
    typographyOf(display = geist, reading = geist).atMostMedium()

/**
 * Sky's own roles, measured from its artboards where they and p02 disagree (spec 2026-10-09,
 * sections 5 and 6): light, tight amounts and medium titles.
 */
internal fun skyTypeOf(geist: FontFamily, mono: FontFamily = geist) = PtutType(
    hero = style(geist, 52f, FontWeight.Light, 0.92.em, (-0.04).em),
    heroSymbol = style(geist, 26f, FontWeight.Light, 1.2.em),
    heroFraction = style(geist, 30f, FontWeight.Light, 1.2.em),
    sheet = style(geist, 48f, FontWeight.Light, 0.95.em, (-0.04).em),
    report = style(geist, 46f, FontWeight.Light, 1.em, (-0.04).em),
    display = style(geist, 52f, FontWeight.Light, 52.sp, (-0.04).em),
    screenTitle = style(geist, 34f, FontWeight.Medium, 40.sp, (-0.02).em),
    sectionTitle = style(geist, 17.5f, FontWeight.Medium, 23.sp),
    listHeadline = style(geist, 15f, FontWeight.Medium, 20.sp),
    meta = style(geist, 12.5f, FontWeight.Normal, 17.sp),
    eyebrow = style(geist, 11.5f, FontWeight.Bold, 15.sp, 0.08.em),
    // p09's receipt, measured off the artboard: lines at 14, the heading spaced out, the total large.
    receipt = style(mono, 14f, FontWeight.Normal, 22.sp),
    receiptHeading = style(mono, 13f, FontWeight.SemiBold, 18.sp, 0.2.em),
    receiptTotal = style(mono, 26f, FontWeight.SemiBold, 32.sp),
)

private fun Typography.atMostMedium() = copy(
    displayLarge = displayLarge.atMostMedium(),
    displayMedium = displayMedium.atMostMedium(),
    displaySmall = displaySmall.atMostMedium(),
    headlineLarge = headlineLarge.atMostMedium(),
    headlineMedium = headlineMedium.atMostMedium(),
    headlineSmall = headlineSmall.atMostMedium(),
    titleLarge = titleLarge.atMostMedium(),
    titleMedium = titleMedium.atMostMedium(),
    titleSmall = titleSmall.atMostMedium(),
    bodyLarge = bodyLarge.atMostMedium(),
    bodyMedium = bodyMedium.atMostMedium(),
    bodySmall = bodySmall.atMostMedium(),
    labelLarge = labelLarge.atMostMedium(),
    labelMedium = labelMedium.atMostMedium(),
    labelSmall = labelSmall.atMostMedium(),
)

private fun TextStyle.atMostMedium(): TextStyle {
    val weight = fontWeight ?: FontWeight.Normal
    return if (weight > FontWeight.Medium) copy(fontWeight = FontWeight.Medium) else this
}

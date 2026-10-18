package com.ptut.expense.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #150. Sky's slots keep p02's sizes, and Sky's roles take what its artboards draw. Built in the system family: which face fills it is BundledTypeTest's.
 */
class SkyTypeTest {

    private val slots = skyTypography(FontFamily.Default).namedSlots()
    private val type = skyTypeOf(FontFamily.Default)

    @Test
    fun everySlotKeepsItsSizeAndLineHeightAndDrawsNothingHeavierThanMedium() {
        listOf(
            Slot("displayLarge", 40.sp, 46.sp, FontWeight.Medium),
            Slot("displayMedium", 34.sp, 40.sp, FontWeight.Medium),
            Slot("displaySmall", 28.sp, 34.sp, FontWeight.Medium),
            Slot("headlineLarge", 26.sp, 32.sp, FontWeight.Medium),
            Slot("headlineMedium", 24.sp, 30.sp, FontWeight.Medium),
            Slot("headlineSmall", 22.sp, 28.sp, FontWeight.Medium),
            Slot("titleLarge", 20.sp, 26.sp, FontWeight.Medium),
            Slot("titleMedium", 16.sp, 22.sp, FontWeight.Medium),
            Slot("titleSmall", 14.5.sp, 20.sp, FontWeight.Medium),
            Slot("bodyLarge", 15.sp, 21.sp, FontWeight.Normal),
            Slot("bodyMedium", 13.5.sp, 20.sp, FontWeight.Normal),
            Slot("bodySmall", 12.5.sp, 17.sp, FontWeight.Normal),
            Slot("labelLarge", 14.sp, 19.sp, FontWeight.Medium),
            Slot("labelMedium", 12.5.sp, 17.sp, FontWeight.Medium),
            Slot("labelSmall", 11.5.sp, 15.sp, FontWeight.Medium),
        ).forEach { want ->
            val style = slots.getValue(want.name)
            assertEquals(want.size, style.fontSize, "${want.name} size")
            assertEquals(want.lineHeight, style.lineHeight, "${want.name} line height")
            assertEquals(want.weight, style.fontWeight, "${want.name} weight")
        }
    }

    @Test
    fun everySlotAndRoleAsksForTabularFigures() {
        (slots + type.namedRoles()).forEach { (name, style) ->
            assertEquals("tnum", style.fontFeatureSettings, "$name lost its tabular figures")
        }
    }

    @Test
    fun theAmountsAndTheFirstRunHeadlineAreLightAndTight() {
        listOf(
            "hero" to (type.hero to 52.sp),
            "sheet" to (type.sheet to 48.sp),
            "report" to (type.report to 46.sp),
            "display" to (type.display to 52.sp),
        ).forEach { (name, pair) ->
            val (style, size) = pair
            assertEquals(size, style.fontSize, "$name size")
            assertEquals(FontWeight.Light, style.fontWeight, "$name weight")
            assertEquals((-0.04).em, style.letterSpacing, "$name tracking")
        }
    }

    @Test
    fun theTitlesAreMedium() {
        assertEquals(34.sp, type.screenTitle.fontSize)
        assertEquals(17.5.sp, type.sectionTitle.fontSize)
        assertEquals(15.sp, type.listHeadline.fontSize)
        listOf(type.screenTitle, type.sectionTitle, type.listHeadline).forEach {
            assertEquals(FontWeight.Medium, it.fontWeight)
        }
    }

    @Test
    fun theBahtSignAndTheSatangBesideTheHeroAreLight() {
        assertEquals(26.sp, type.heroSymbol.fontSize)
        assertEquals(30.sp, type.heroFraction.fontSize)
        assertEquals(FontWeight.Light, type.heroSymbol.fontWeight)
        assertEquals(FontWeight.Light, type.heroFraction.fontWeight)
    }

    @Test
    fun metaAndTheEyebrowKeepTheirValues() {
        assertEquals(12.5.sp, type.meta.fontSize)
        assertEquals(FontWeight.Normal, type.meta.fontWeight)
        assertEquals(11.5.sp, type.eyebrow.fontSize)
        assertEquals(FontWeight.Bold, type.eyebrow.fontWeight)
        assertEquals(0.08.em, type.eyebrow.letterSpacing)
    }
}

private data class Slot(val name: String, val size: TextUnit, val lineHeight: TextUnit, val weight: FontWeight)

private fun Typography.namedSlots(): Map<String, TextStyle> = mapOf(
    "displayLarge" to displayLarge,
    "displayMedium" to displayMedium,
    "displaySmall" to displaySmall,
    "headlineLarge" to headlineLarge,
    "headlineMedium" to headlineMedium,
    "headlineSmall" to headlineSmall,
    "titleLarge" to titleLarge,
    "titleMedium" to titleMedium,
    "titleSmall" to titleSmall,
    "bodyLarge" to bodyLarge,
    "bodyMedium" to bodyMedium,
    "bodySmall" to bodySmall,
    "labelLarge" to labelLarge,
    "labelMedium" to labelMedium,
    "labelSmall" to labelSmall,
)

private fun PtutType.namedRoles(): Map<String, TextStyle> = mapOf(
    "hero" to hero,
    "heroSymbol" to heroSymbol,
    "heroFraction" to heroFraction,
    "sheet" to sheet,
    "report" to report,
    "display" to display,
    "screenTitle" to screenTitle,
    "sectionTitle" to sectionTitle,
    "listHeadline" to listHeadline,
    "meta" to meta,
    "eyebrow" to eyebrow,
)

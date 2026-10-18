package com.ptut.expense.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * iOS falls back to San Francisco whenever a family fails to resolve, and it does so silently —
 * the screen still renders, at the right sizes, in the wrong face. These read the theme rather
 * than a pixel, because that is the layer where the fallback would happen.
 */
@OptIn(ExperimentalTestApi::class)
class BundledTypeTest {

    @Test
    fun everyMaterialSlotResolvesToTheBundledFamilyRatherThanTheSystemOne() = runComposeUiTest {
        var typography: Typography? = null
        setContent { PtutTheme { typography = MaterialTheme.typography } }

        val slots = typography!!.namedSlots()
        slots.forEach { (name, style) ->
            assertNotNull(style.fontFamily, "$name fell back to the system face")
        }
    }

    /** FR-9's column cannot wobble, and the feature is set once on the slot, never at a call site. */
    @Test
    fun everyMaterialSlotAsksForTabularFigures() = runComposeUiTest {
        var typography: Typography? = null
        setContent { PtutTheme { typography = MaterialTheme.typography } }

        typography!!.namedSlots().forEach { (name, style) ->
            assertEquals("tnum", style.fontFeatureSettings, "$name lost its tabular figures")
        }
    }

    /**
     * The three money sizes the design separates. Folding any two together is the drift this
     * guards, and the hero is the one a font swap breaks first.
     */
    @Test
    fun theThreeMoneySizesStayApartAtTheSizesTheDesignSets() = runComposeUiTest {
        var type: PtutType? = null
        setContent { PtutTheme { type = MaterialTheme.ptutType } }

        assertEquals(52.sp, type!!.hero.fontSize)
        assertEquals(48.sp, type!!.sheet.fontSize)
        assertEquals(46.sp, type!!.report.fontSize)
        assertNotNull(type!!.hero.fontFamily)
        assertEquals("tnum", type!!.hero.fontFeatureSettings)
    }

    @Test
    fun theDisplayRolesCarryTheNegativeTrackingTheDesignAsksFor() = runComposeUiTest {
        var type: PtutType? = null
        setContent { PtutTheme { type = MaterialTheme.ptutType } }

        listOf("hero" to type!!.hero, "sheet" to type!!.sheet, "report" to type!!.report)
            .forEach { (name, style) ->
                assertTrue(style.letterSpacing.value < 0f, "$name is set loose at display size")
            }
        assertTrue(type!!.eyebrow.letterSpacing.value > 0f, "an eyebrow is set open, not tight")
    }

    /** The artboard sets the amounts under a line-height of one, so the row hugs the digits. */
    @Test
    fun theMoneyRolesCarryTheArtboardsLineHeightAndTracking() = runComposeUiTest {
        var type: PtutType? = null
        setContent { PtutTheme { type = MaterialTheme.ptutType } }

        assertEquals(0.92.em, type!!.hero.lineHeight)
        assertEquals((-0.04).em, type!!.hero.letterSpacing)
        assertEquals(0.95.em, type!!.sheet.lineHeight)
        assertEquals((-0.04).em, type!!.sheet.letterSpacing)
        assertEquals(1.em, type!!.report.lineHeight)
    }

    @Test
    fun theHeroSymbolAndSatangSitAtTheArtboardsSizes() = runComposeUiTest {
        var type: PtutType? = null
        setContent { PtutTheme { type = MaterialTheme.ptutType } }

        assertEquals(26.sp, type!!.heroSymbol.fontSize)
        assertEquals(FontWeight.Light, type!!.heroSymbol.fontWeight)
        assertEquals(30.sp, type!!.heroFraction.fontSize)
        assertEquals(FontWeight.Light, type!!.heroFraction.fontWeight)
        assertEquals("tnum", type!!.heroFraction.fontFeatureSettings)
    }

    /** The numpad keys and the Reports affix read `titleLarge`, so the 17.5 is a role of its own. */
    @Test
    fun aSectionTitleIsSeventeenAndAHalfAndLeavesTitleLargeAtTwenty() = runComposeUiTest {
        var type: PtutType? = null
        var typography: Typography? = null
        setContent {
            PtutTheme {
                type = MaterialTheme.ptutType
                typography = MaterialTheme.typography
            }
        }

        assertEquals(17.5.sp, type!!.sectionTitle.fontSize)
        assertEquals(FontWeight.Medium, type!!.sectionTitle.fontWeight)
        assertEquals(20.sp, typography!!.titleLarge.fontSize)
    }

    /** D1: iOS grows text in a straight line, so at 2× the hero would reach 104 without the cap. */
    @Test
    fun atTwiceTheTextSizeTheHeroStopsAtOnePointThreeTimesItsDesignSize() = runComposeUiTest {
        var type: PtutType? = null
        setContent { AtFontScale(2f) { PtutTheme { type = MaterialTheme.ptutType } } }

        assertEquals(67.6f, type!!.hero.fontSize.value * 2f, absoluteTolerance = 0.01f)
        assertEquals(33.8f, type!!.heroSymbol.fontSize.value * 2f, absoluteTolerance = 0.01f)
    }

    @Test
    fun atASmallerTextSizeTheHeroStillDrawsAtItsDesignSize() = runComposeUiTest {
        var type: PtutType? = null
        setContent { AtFontScale(0.85f) { PtutTheme { type = MaterialTheme.ptutType } } }

        assertEquals(52f, type!!.hero.fontSize.value * 0.85f, absoluteTolerance = 0.01f)
    }

    @Test
    fun bodyTextHasTheLineHeightTheArtboardSets() = runComposeUiTest {
        var typography: Typography? = null
        setContent { PtutTheme { typography = MaterialTheme.typography } }

        val body = typography!!.bodyMedium
        assertTrue(body.lineHeight.value / body.fontSize.value >= 1.45f, "body is set at ${body.lineHeight}")
    }

    /** #150. Five cuts from 300 to 700 is Geist, so a missing cut shows. */
    @Test
    fun everyMaterialSlotWearsGeist() = runComposeUiTest {
        var typography: Typography? = null
        setContent { PtutTheme { typography = MaterialTheme.typography } }

        typography!!.namedSlots().forEach { (name, style) ->
            val family = assertIs<FontListFontFamily>(style.fontFamily, "$name fell back to the system face")
            assertEquals(GEIST_WEIGHTS, family.fonts.map { it.weight.weight }, "$name is not set in Geist")
        }
    }
}

private val GEIST_WEIGHTS = listOf(300, 400, 500, 600, 700)

private fun Typography.namedSlots(): List<Pair<String, TextStyle>> = listOf(
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

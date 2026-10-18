package com.ptut.expense.design

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #145, answer (a). Sky has no dark artboards, so [SkyNight] is derived from [Sky] by a stated
 * rule: ink, sky and alert red keep their meaning at night. These are the parts of that rule a
 * later edit must not break on one side only.
 */
class SkyKeepsItsMeaningAtNightTest {

    /** Sky is the one accent, by day and by night. */
    @Test
    fun bothThemesSpendTheSameSky() {
        listOf(
            "accent" to SkyNight.accent,
            "toastCheck" to SkyNight.toastCheck,
            "day toastCheck" to Sky.toastCheck,
        ).forEach { (name, color) -> assertEquals(Sky.accent, color, name) }
    }

    /** Whatever sits on sky is ink, in both themes. */
    @Test
    fun whateverSitsOnSkyIsInk() {
        listOf(
            "accentOn" to Sky.accentOn,
            "night accentOn" to SkyNight.accentOn,
        ).forEach { (name, color) -> assertEquals(Sky.primary, color, name) }
    }

    /** Ink's fills turn light at night, and ink is what sits on them. */
    @Test
    fun inkSitsOnTheNightsLightFills() {
        assertEquals(Sky.primary, SkyNight.onPrimary, "onPrimary")
    }

    /** Alert red is one red, with white on it in both themes. */
    @Test
    fun theWarningIsOneRed() {
        assertEquals(Sky.warn, SkyNight.warn, "warn")
        assertEquals(Sky.warnOn, SkyNight.warnOn, "warnOn")
    }

    /** The chart field turns deep at night, but it is still the sky field. */
    @Test
    fun theNightChartFieldKeepsSkysHue() {
        val drift = abs(hue(SkyNight.heroBg) - hue(Sky.accent))
        assertTrue(drift <= HUE_TOLERANCE, "the night chart field sits ${drift.toInt()}° off sky")
    }
}

/** Degrees the night chart field may sit from sky's own hue. */
private const val HUE_TOLERANCE = 6.0

/** Hue in degrees. Every caller passes a saturated token, so the achromatic case never matters. */
private fun hue(color: Color): Double {
    val r = color.red.toDouble()
    val g = color.green.toDouble()
    val b = color.blue.toDouble()
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (delta == 0.0) return 0.0
    val degrees = when (max) {
        r -> 60 * (((g - b) / delta) % 6)
        g -> 60 * (((b - r) / delta) + 2)
        else -> 60 * (((r - g) / delta) + 4)
    }
    return if (degrees < 0) degrees + 360 else degrees
}

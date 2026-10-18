package com.ptut.expense.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NFR-8 reaches the category colours too. A tint is a fill a glyph sits on, and it also has to be
 * tellable from the surface behind it, so both pairs are measured rather than eyeballed.
 *
 * The floor is 3:1 — WCAG's number for an icon glyph and for a boundary that carries meaning.
 * The family is built well above it, and the assertions are pinned there so a later edit that
 * dims one tint fails here instead of on a phone.
 */
class CategoryTintContrastTest {

    @Test
    fun skyTintsCarryTheirGlyph() = assertNoneBelow("sky", SkyCategoryTints.inkPairs())

    @Test
    fun skyNightTintsCarryTheirGlyph() = assertNoneBelow("sky night", SkyNightCategoryTints.inkPairs())

    /**
     * Both backgrounds a tint is drawn against: the page, and the white card a category row sits on.
     */
    @Test
    fun skyTintsStandOffWhatTheyAreDrawnOn() =
        assertNoneBelow("sky", Sky.categoryTints.groundPairs(Sky))

    @Test
    fun skyNightTintsStandOffWhatTheyAreDrawnOn() =
        assertNoneBelow("sky night", SkyNight.categoryTints.groundPairs(SkyNight))

    /** Sky's reserved signals: alert red means delete or stale data, and sky is its one accent. */
    @Test
    fun noTintIsSkysAlertRedOrItsSky() {
        val reserved = setOf(Sky.warn, Sky.warnInk, SkyNight.warnInk, Sky.accent)
        val worn = (Sky.categoryTints.all + SkyNight.categoryTints.all).map { it.second.fill }

        assertTrue(worn.none { it in reserved }, "a category tint reached a colour Sky reserves")
    }

    /** Six on offer, plus the theme's own primary. A seventh has to be added in both themes. */
    @Test
    fun bothThemesOfferTheSameSix() {
        assertEquals(6, SkyCategoryTints.all.size)
        assertEquals(
            SkyCategoryTints.all.map { it.first },
            SkyNightCategoryTints.all.map { it.first },
        )
    }

    private fun assertNoneBelow(theme: String, pairs: List<Pair<String, Double>>) {
        val short = pairs.filter { it.second < FLOOR }.map { "${it.first} = ${round2(it.second)}:1" }
        assertTrue(short.isEmpty(), "$theme below $FLOOR:1 — ${short.joinToString("; ")}")
    }
}

private const val FLOOR = 3.0

private fun CategoryTints.inkPairs(): List<Pair<String, Double>> =
    all.map { (tint, colors) -> "${tint.storedName} ink on fill" to contrast(colors.on, colors.fill) }

private fun CategoryTints.groundPairs(p: Palette): List<Pair<String, Double>> = all.flatMap { (tint, colors) ->
    listOf(
        "${tint.storedName} fill on surface" to contrast(colors.fill, p.surface),
        "${tint.storedName} chip against an unchosen one" to contrast(colors.fill, p.container),
    )
}

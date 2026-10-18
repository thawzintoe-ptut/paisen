package com.ptut.expense.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * NFR-8 says the app is readable in sunlight, so every pair a screen actually draws is
 * measured here rather than eyeballed against the handoff. A palette edit that dims a pair
 * fails on this machine instead of on a phone at noon.
 *
 * WCAG 2.1: 4.5:1 for body text, 3:1 for text at 24sp or the weight the design calls large,
 * and for icon glyphs.
 */
class PaletteContrastTest {

    /**
     * A card, a chip or a tile has to stay tellable from what it sits on. The floor is the faintest
     * edge a shipped theme draws, so no palette can melt a card.
     */
    @Test
    fun skyBodyTextIsReadable() =
        assertNoneBelow("sky", BODY, bodyPairs(Sky) + warnInkPairs(Sky))

    @Test
    fun skyGlyphsAndLargeTextAreReadable() = assertNoneBelow("sky", LARGE, largePairs(Sky))

    @Test
    fun skyStatesAreTellableApart() = assertNoneBelow("sky", LARGE, statePairs(Sky))

    @Test
    fun skyCardsStandOffTheirGround() = assertNoneBelow("sky", EDGE, edgePairs(Sky))

    @Test
    fun skyNightBodyTextIsReadable() =
        assertNoneBelow("sky night", BODY, bodyPairs(SkyNight) + warnInkPairs(SkyNight))

    @Test
    fun skyNightGlyphsAndLargeTextAreReadable() =
        assertNoneBelow("sky night", LARGE, largePairs(SkyNight))

    @Test
    fun skyNightStatesAreTellableApart() =
        assertNoneBelow("sky night", LARGE, statePairs(SkyNight))

    @Test
    fun skyNightCardsStandOffTheirGround() =
        assertNoneBelow("sky night", EDGE, edgePairs(SkyNight))

    private fun assertNoneBelow(theme: String, floor: Double, pairs: List<Pair<String, Double>>) {
        val short = pairs.filter { it.second < floor }.map { "${it.first} = ${round2(it.second)}:1" }
        assertTrue(short.isEmpty(), "$theme below $floor:1 — ${short.joinToString("; ")}")
    }
}

private const val BODY = 4.5
private const val LARGE = 3.0
private const val EDGE = 1.07

/** Everything that carries a sentence, a label or an amount at body weight. */
private fun bodyPairs(p: Palette) = listOf(
    "onSurface on surface" to contrast(p.onSurface, p.surface),
    "onSurface on container" to contrast(p.onSurface, p.container),
    "onVar on surface" to contrast(p.onVar, p.surface),
    "onVar on container" to contrast(p.onVar, p.container),
    "onPrimary on primary" to contrast(p.onPrimary, p.primary),
    "heroOn on heroBg" to contrast(p.heroOn, p.heroBg),
    "accentOn on accent" to contrast(p.accentOn, p.accent),
    "warnOn on warn" to contrast(p.warnOn, p.warn),
    // The Broken banner: errorContainer is warnBg and onErrorContainer is onSurface.
    "onSurface on warnBg" to contrast(p.onSurface, p.warnBg),
    "toastOn on toastBg" to contrast(p.toastOn, p.toastBg),
    // Drawn but missed until #149: the hero label and the delete dialog sit on containerHigh,
    // the stale banner's second line on warnBg, cards on navBg, sheets on sheetBg.
    "onSurface on containerHigh" to contrast(p.onSurface, p.containerHigh),
    "onVar on containerHigh" to contrast(p.onVar, p.containerHigh),
    "onVar on warnBg" to contrast(p.onVar, p.warnBg),
    "onSurface on navBg" to contrast(p.onSurface, p.navBg),
    "onVar on navBg" to contrast(p.onVar, p.navBg),
    "onSurface on sheetBg" to contrast(p.onSurface, p.sheetBg),
    "onVar on sheetBg" to contrast(p.onVar, p.sheetBg),
    "onSurface on infoBg" to contrast(p.onSurface, p.infoBg),
)

/** Glyphs and the hero amount. */
private fun largePairs(p: Palette) = listOf(
    "ok on surface" to contrast(p.ok, p.surface),
    "primary on surface" to contrast(p.primary, p.surface),
    "heroOn on heroBg" to contrast(p.heroOn, p.heroBg),
    "infoInk on infoBg" to contrast(p.infoInk, p.infoBg),
    "toastCheck on toastBg" to contrast(p.toastCheck, p.toastBg),
    // The pulse dot sits on the page and on the freshness chip, a card.
    "live on surface" to contrast(p.live, p.surface),
    "live on navBg" to contrast(p.live, p.navBg),
)

/**
 * Every surface [PtutColors.warnInk] is drawn on. It exists because alert red is under the body
 * floor on its own tint. These are held to the body floor rather than the glyph one: the ink
 * carries the "not counted" amount and the stale notice's sentence, not only glyphs.
 */
private fun warnInkPairs(p: Palette) = listOf(
    "warnInk on warnBg" to contrast(p.warnInk, p.warnBg),
    "warnInk on surface" to contrast(p.warnInk, p.surface),
    "warnInk on container" to contrast(p.warnInk, p.container),
)

/**
 * Not text on a background: two states of one control, side by side. WCAG 1.4.11 asks 3:1 of a
 * state indicator, and the category chips missed it by a mile — chosen and unchosen were two creams
 * 1.08:1 apart, which is why "chosen" had to be read rather than seen.
 */
private fun statePairs(p: Palette) = listOf(
    "a chosen category chip against an unchosen one" to contrast(p.primary, p.container),
)

/** The three neutral tones against each other: the ground, a card, and the step inside a card. */
private fun edgePairs(p: Palette) = listOf(
    "a card on its ground" to contrast(p.navBg, p.surface),
    "a container on its ground" to contrast(p.container, p.surface),
    "the step on its ground" to contrast(p.containerHigh, p.surface),
    "the step on a card" to contrast(p.containerHigh, p.navBg),
    "the step on a container" to contrast(p.containerHigh, p.container),
)

/** Internal so `CategoryTintContrastTest` measures with this one and not a second copy of it. */
internal fun contrast(foreground: Color, background: Color): Double {
    val a = luminance(foreground)
    val b = luminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

private fun luminance(color: Color): Double =
    0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

private fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

internal fun round2(value: Double) = (value * 100).toInt() / 100.0

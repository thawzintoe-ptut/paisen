package com.ptut.expense.design

/**
 * D1 in #20: a money role grows with the reader's text size up to 1.3× its design size, and never
 * draws smaller than it. NFR-8 keeps amounts the largest thing on screen, and a hero that kept
 * doubling would push the rest of Today off it.
 *
 * [platformSize] is what the platform made of the design size, in dp. Android 14+ bends large text
 * well below its font scale, so the clamp works on that result rather than on the raw scale, which
 * would draw the hero bigger than Android itself does.
 */
internal fun moneySize(designSize: Float, platformSize: Float): Float =
    platformSize.coerceIn(designSize, designSize * MONEY_GROWTH_CAP)

private const val MONEY_GROWTH_CAP = 1.3f

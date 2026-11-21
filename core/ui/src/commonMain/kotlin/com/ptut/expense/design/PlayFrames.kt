package com.ptut.expense.design

import androidx.compose.animation.core.Easing

/**
 * Play's moving parts as arithmetic (#84), so the frames are tested without a widget test, as
 * [pulseFrameAt] is. Each takes the fraction through one move.
 */

/** A stamp pressed onto the page: [STAMP_PRESS_FROM] times its size down to its own, on [easing]. */
fun stampScaleAt(progress: Float, easing: Easing): Float =
    STAMP_PRESS_FROM + (1f - STAMP_PRESS_FROM) * easing.transform(progress.coerceIn(0f, 1f))

/** A card turning over on Y: 0° face down to 180° face up. [easing] may overshoot past 180°. */
fun cardAngleAt(progress: Float, easing: Easing): Float = HALF_TURN * easing.transform(progress.coerceIn(0f, 1f))

/** Past its edge the card shows its back, which is the side the reader turned it to see. */
fun showsBack(angle: Float): Boolean = angle >= QUARTER_TURN

/** A meter never draws outside its track, and a share it cannot know draws empty. */
fun meterShare(fraction: Float): Float = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)

const val STAMP_PRESS_FROM = 1.35f

/** Design V3 turns every earned stamp the same way, so a page of them reads as hand-stamped. */
const val STAMP_TILT = -8f

private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f

package com.ptut.expense.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Page 05's three marks, as arithmetic.
 *
 * The design ships them as Lottie files. This app draws them instead, because `lottie-compose` is
 * Android-only and #24 settled that iOS ships on this app rather than a fork — a mark that existed
 * on one platform and not the other would be a state the reader could only see half of. Keeping
 * the geometry here rather than inside a `Canvas` is what lets the frame numbers be checked
 * against the JSON they came from instead of by eye.
 *
 * Each function takes the fraction through one loop and says what to draw at that instant.
 */

/** The curve all three ease on: `i:{x:.4,y:1} o:{x:.2,y:0}` in each JSON. */
private val MarkEasing: Easing = CubicBezierEasing(0.2f, 0f, 0.4f, 1f)

/** What the pulse's halo looks like at one instant. The dot under it never changes. */
@Immutable
data class PulseFrame(val haloScale: Float, val haloAlpha: Float)

/**
 * `pulse-dot.json`: 96 frames at 60fps. The halo scales 40% to 190% and fades 55% to 0 by frame
 * 78, and the remaining 18 frames are the still dot on its own.
 */
fun pulseFrameAt(progress: Float): PulseFrame {
    val swell = MarkEasing.transform((progress / HALO_SPAN).coerceIn(0f, 1f))
    return PulseFrame(
        haloScale = HALO_FROM + (HALO_TO - HALO_FROM) * swell,
        haloAlpha = HALO_ALPHA * (1f - swell),
    )
}

private const val HALO_SPAN = 78f / 96f
private const val HALO_FROM = 0.40f
private const val HALO_TO = 1.90f
private const val HALO_ALPHA = 0.55f

/** What the check looks like at one instant: how big it is, and how much of it is on. */
@Immutable
data class CheckFrame(val scale: Float, val drawn: Float)

/**
 * `check-draw.json`: 36 frames. The trim runs frames 2 to 24 while the layer scales 86% to 104%
 * at frame 14 and settles at 100% by frame 26.
 *
 * The scale finishing *after* the stroke does is the point: the mark is still arriving as the
 * check completes, so the two read as one gesture rather than as a drawing followed by a bounce.
 */
fun checkFrameAt(progress: Float): CheckFrame = CheckFrame(
    scale = when {
        progress <= SCALE_PEAK_AT ->
            CHECK_FROM + (CHECK_PEAK - CHECK_FROM) * MarkEasing.transform(progress / SCALE_PEAK_AT)

        progress <= SCALE_SETTLE_AT -> {
            val settling = (progress - SCALE_PEAK_AT) / (SCALE_SETTLE_AT - SCALE_PEAK_AT)
            CHECK_PEAK + (1f - CHECK_PEAK) * MarkEasing.transform(settling)
        }

        else -> 1f
    },
    drawn = MarkEasing.transform(
        ((progress - TRIM_FROM) / (TRIM_TO - TRIM_FROM)).coerceIn(0f, 1f),
    ),
)

private const val SCALE_PEAK_AT = 14f / 36f
private const val SCALE_SETTLE_AT = 26f / 36f
private const val CHECK_FROM = 0.86f
private const val CHECK_PEAK = 1.04f
private const val TRIM_FROM = 2f / 36f
private const val TRIM_TO = 24f / 36f

/** What the waiting arc looks like at one instant: how much ring is drawn, and where it points. */
@Immutable
data class ArcFrame(val sweepDegrees: Float, val turnDegrees: Float)

/**
 * `arc-loop.json`: 180 frames. Trim end runs 18% to 68% and back while the layer rotates a full
 * turn and the trim offset adds another half — 540 degrees between them.
 *
 * The gap closes again rather than ramping, which is what stops a ring with no known end reading
 * as a progress bar that keeps resetting.
 */
fun arcFrameAt(progress: Float): ArcFrame {
    val breath = if (progress <= 0.5f) progress * 2f else (1f - progress) * 2f
    val span = ARC_SWEEP_MIN + (ARC_SWEEP_MAX - ARC_SWEEP_MIN) * MarkEasing.transform(breath)
    return ArcFrame(
        sweepDegrees = span * 360f,
        turnDegrees = progress * ARC_TURN_DEGREES,
    )
}

private const val ARC_SWEEP_MIN = 0.18f
private const val ARC_SWEEP_MAX = 0.68f
private const val ARC_TURN_DEGREES = 540f

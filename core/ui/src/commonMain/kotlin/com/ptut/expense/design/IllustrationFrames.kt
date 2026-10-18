package com.ptut.expense.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import kotlin.math.floor

/**
 * #180's three drawings as arithmetic, in their shared 160 by 100 box, y down. The PDF shows one
 * frame of each (`paisen-illus.js`, the real motion, is unread), so every loop passes through that
 * frame and reduced motion holds it: the end of a drop or a flight is an empty slot.
 */

/** p02: "all cubic-bezier(.2,.8,.2,1)". */
private val DrawingEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** One wisp of steam: how far above the PDF's drawing it has risen, and how much of it shows. */
@Immutable
data class Wisp(val rise: Float, val alpha: Float)

/** Empty Today (p24): two wisps over the cup, and the sun's rays breathing. */
@Immutable
data class QuietDayFrame(val first: Wisp, val second: Wisp, val rayAlpha: Float)

fun quietDayFrameAt(progress: Float): QuietDayFrame = QuietDayFrame(
    first = wispAt(progress + FIRST_WISP_PHASE),
    second = wispAt(progress + SECOND_WISP_PHASE),
    rayAlpha = RAY_REST + (RAY_PEAK - RAY_REST) * DrawingEasing.transform(breath(progress)),
)

/** The loop starts on the PDF's frame, so that is the one reduced motion holds. */
val QuietDayStill: QuietDayFrame = quietDayFrameAt(0f)

/** A wisp fades in at the cup and out at the top, so it drops back down while nobody can see it. */
private fun wispAt(phase: Float): Wisp {
    val at = phase - floor(phase)
    val alpha = when {
        at < WISP_FADE_IN -> at / WISP_FADE_IN
        at > 1f - WISP_FADE_OUT -> (1f - at) / WISP_FADE_OUT
        else -> 1f
    }
    return Wisp(rise = WISP_RISE * (at - 0.5f), alpha = alpha)
}

// The PDF draws the second wisp a unit below the first: a sixth of a loop behind it.
private const val FIRST_WISP_PHASE = 0.5f
private const val SECOND_WISP_PHASE = 0.5f - 1f / 6f
private const val WISP_RISE = 6f
private const val WISP_FADE_IN = 0.25f
private const val WISP_FADE_OUT = 0.35f
private const val RAY_REST = 0.4f
private const val RAY_PEAK = 0.8f

/** The delete sheet (p17): where the slip's top edge is, and how much of it shows. */
@Immutable
data class SlipDropFrame(val top: Float, val alpha: Float)

/** The slip appears high, falls faster as it goes, and is gone inside the bin before it restarts. */
fun slipDropFrameAt(progress: Float): SlipDropFrame = when {
    progress < DROP_APPEAR -> SlipDropFrame(top = DROP_FROM, alpha = progress / DROP_APPEAR)

    progress < DROP_LAND -> {
        val fall = (progress - DROP_APPEAR) / (DROP_LAND - DROP_APPEAR)
        SlipDropFrame(top = DROP_FROM + (DROP_TO - DROP_FROM) * fall * fall, alpha = 1f)
    }

    else -> SlipDropFrame(top = DROP_TO, alpha = 0f)
}

/** Where p17 draws the slip: half in, its lower edge behind the lid. */
val SlipDropStill = SlipDropFrame(top = 27.5f, alpha = 1f)

private const val DROP_APPEAR = 0.15f
private const val DROP_LAND = 0.55f
private const val DROP_FROM = 8f

// The bin's mouth is at 56. From 58 the whole 28-tall slip sits behind the bin's fill.
private const val DROP_TO = 58f

/** First run (p23): the small slip's centre, size and opacity, and the two dots beside the folder. */
@Immutable
data class SlipFlightFrame(
    val centreX: Float,
    val centreY: Float,
    val scale: Float,
    val alpha: Float,
    val firstDot: Float,
    val secondDot: Float,
)

/** The slip lifts off the phone's screen, shrinks as it flies, and lands behind the folder's front. */
fun slipFlightFrameAt(progress: Float): SlipFlightFrame {
    val (along, alpha) = when {
        progress < FLIGHT_APPEAR -> 0f to progress / FLIGHT_APPEAR

        progress < FLIGHT_LAND -> {
            val flown = (progress - FLIGHT_APPEAR) / (FLIGHT_LAND - FLIGHT_APPEAR)
            DrawingEasing.transform(flown) to 1f
        }

        else -> 1f to 0f
    }
    return flightAlong(along).copy(
        alpha = alpha,
        firstDot = dotAt(progress),
        secondDot = dotAt(progress + 0.5f),
    )
}

/** Where p23 catches the slip: halfway along, at 80% of its size. */
val SlipFlightStill: SlipFlightFrame = flightAlong(0.5f)

/**
 * A quadratic curve from the phone's screen to inside the folder. Its control point puts the
 * midpoint where p23 draws the slip, 81.4 by 59.5.
 */
private fun flightAlong(t: Float): SlipFlightFrame {
    val from = 1f - t
    return SlipFlightFrame(
        centreX = from * from * 47.5f + 2f * t * from * 84.6f + t * t * 109f,
        centreY = from * from * 50f + 2f * t * from * 58f + t * t * 72f,
        scale = 1f - (1f - FLIGHT_LANDED_SCALE) * t,
        alpha = 1f,
        firstDot = 1f,
        secondDot = 1f,
    )
}

/** A dot twinkles between faint and full, never out, so the drawing never loses a part. */
private fun dotAt(phase: Float): Float = DOT_FAINT + (1f - DOT_FAINT) * DrawingEasing.transform(1f - breath(phase))

private const val FLIGHT_APPEAR = 0.1f
private const val FLIGHT_LAND = 0.65f

// Halfway, the PDF's slip is 80.4% of its size, so it lands at 60.8%.
private const val FLIGHT_LANDED_SCALE = 0.608f
private const val DOT_FAINT = 0.35f

/** 0 at the start and end of a loop, 1 halfway. */
private fun breath(phase: Float): Float {
    val at = phase - floor(phase)
    return if (at <= 0.5f) at * 2f else (1f - at) * 2f
}

/** The empty Shared tab (p34): how far the two halves of the bill have come apart, and the check over them. */
@Immutable
data class SharedSplitFrame(val apart: Float, val checkAlpha: Float)

/** The halves tear apart, hold while the check shows, then come back together for the next loop. */
fun sharedSplitFrameAt(progress: Float): SharedSplitFrame {
    val apart = when {
        progress < SPLIT_OPEN -> DrawingEasing.transform(progress / SPLIT_OPEN)
        progress < SPLIT_HOLD -> 1f
        else -> 1f - DrawingEasing.transform((progress - SPLIT_HOLD) / (1f - SPLIT_HOLD))
    }
    return SharedSplitFrame(apart = apart, checkAlpha = ((apart - CHECK_FROM) / (1f - CHECK_FROM)).coerceIn(0f, 1f))
}

/** Where p34 draws the halves: fully apart, the check showing. */
val SharedSplitStill = SharedSplitFrame(apart = 1f, checkAlpha = 1f)

private const val SPLIT_OPEN = 0.4f
private const val SPLIT_HOLD = 0.75f
private const val CHECK_FROM = 0.8f

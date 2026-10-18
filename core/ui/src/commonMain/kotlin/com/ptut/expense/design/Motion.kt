package com.ptut.expense.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Page 01: four curves, and nothing else. "A fifth curve would be a fifth thing to remember and no
 * one would be able to tell."
 *
 * Built once and shared, because the page's point is that everything rides the same four. A curve
 * rebuilt per call site is a curve somebody eventually types differently.
 */
private val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Front-loaded a touch more than [StandardEasing], so a large surface still feels light coming in. */
private val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)

/**
 * The overshoot, for objects the reader summoned or that demand an answer, such as the dialog.
 * Its control point sits above 1 on purpose — that is the overshoot.
 */
private val OvershootEasing: Easing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)

/**
 * Accelerates away. "Nothing should linger once the user has decided it is done."
 *
 * The one curve with no settle: it is for dismissals and scrim fades, where a soft landing would
 * read as the app being reluctant.
 */
private val ExitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * Every duration and curve the motion page names, in one place. NFR-8.
 *
 * [Duration] rather than a bare `Int`, so the two the spec deliberately separates cannot be
 * confused for each other at a glance: [toastHold] is 2.6 seconds and [undoWindow] is four, and
 * rev 3 page 11b keeps them apart. Two `Int`s named alike are one careless edit apart.
 *
 * The values are constructor defaults rather than constants, so the whole set can be swapped at
 * the theme — which is what makes [LocalReduceMotion] a provider away rather than a rewrite.
 */
@Immutable
data class PtutMotion(
    // ---- page 02 · screen transitions -------------------------------------------------------
    /** A screen going deeper into the same subject. Standard. */
    val push: Duration = 300.milliseconds,
    /**
     * How far the outgoing screen drifts under the incoming one. It "stays visibly underneath,
     * which is what makes back feel like a return instead of a new load".
     */
    val pushParallax: Float = 0.22f,
    /** And how far it dims while it waits there. */
    val pushDim: Float = 0.35f,
    /**
     * A task laid on top of what the reader was doing: 300ms (p02, p10). Material's sheet already shows
     * and hides with tween(300); MotionScheme is internal in material3 1.9, so it cannot be passed (#131).
     */
    val sheet: Duration = 300.milliseconds,
    /** The scrim leads the sheet: the dimming arrives before the surface does. */
    val scrim: Duration = 200.milliseconds,
    /** The one interruption in the app. Spring, scale .86 → 1.03 → 1. */
    val dialog: Duration = 300.milliseconds,

    // ---- page 03 · micro-interactions -------------------------------------------------------
    /** A category chip filling from its leading edge. */
    val chip: Duration = 180.milliseconds,
    /** A numpad key going down. Release is slower — "a numpad that bounces feels imprecise". */
    val keyDown: Duration = 90.milliseconds,
    val keyRelease: Duration = 140.milliseconds,
    /** The nav pill travelling between destinations. It "slides, never jumps". */
    val navIndicator: Duration = 260.milliseconds,
    /** The save toast rising and fading. */
    val toastRise: Duration = 260.milliseconds,
    /** How long a save toast stays: long enough to read seven words. */
    val toastHold: Duration = 2_600.milliseconds,
    /** How long an Undo toast stays: longer than a save toast, so there is time to reach the button. */
    val undoWindow: Duration = 4.seconds,
    /** #216: Undo with TalkBack or VoiceOver on, which reads the line first and then has to reach the button. */
    val spokenUndoWindow: Duration = 20.seconds,
    /** Bars growing from the axis, and the gap between one bar starting and the next. */
    val chart: Duration = 500.milliseconds,
    val chartStagger: Duration = 40.milliseconds,
    /**
     * A money figure's digits rolling to a new value, odometer-style, and the gap between one place
     * starting and the next. Long on purpose: the settle is what reads as weight.
     */
    val numberRoll: Duration = 900.milliseconds,
    val numberStagger: Duration = 40.milliseconds,
    /**
     * The same figure changing between two known values, which a reader does many times a day (day,
     * week, month): short and all at once, so nobody waits to read it (HIG motion).
     */
    val numberChange: Duration = 400.milliseconds,

    // ---- page 05 · the three marks ----------------------------------------------------------
    /** The importer's pulse. Loops only while a scan is genuinely running. */
    val pulse: Duration = 1_600.milliseconds,
    /** The check drawing itself in the toast. Plays once and holds. */
    val checkDraw: Duration = 600.milliseconds,
    /** The bin in a delete toast: stroke on, lid open, lid shut. Plays once and holds. */
    val binDraw: Duration = 700.milliseconds,
    /** The waiting arc: a 3s cycle played at 0.55×, so one turn takes this long. */
    val arcLoop: Duration = 5_455.milliseconds,

    // ---- p17, p23, p24 · the line drawings (#180): "loops under 5s" ------------------------
    val quietDay: Duration = 4_000.milliseconds,
    val slipDrop: Duration = 2_400.milliseconds,
    val slipFlight: Duration = 3_200.milliseconds,
    val sharedSplit: Duration = 3_600.milliseconds,

    // ---- page 01 · the curves themselves ----------------------------------------------------
    val standard: Easing = StandardEasing,
    val emphasized: Easing = EmphasizedEasing,
    val overshoot: Easing = OvershootEasing,
    val exit: Easing = ExitEasing,
) {
    /** The Undo window for this reader: [spokenUndoWindow] while a screen reader is on. */
    fun undoWindowFor(screenReader: Boolean): Duration = if (screenReader) spokenUndoWindow else undoWindow

    companion object {
        /** For the state holders, which own a clock but have no composition to read a local from. */
        val Default = PtutMotion()
    }
}

val LocalPtutMotion = staticCompositionLocalOf { PtutMotion.Default }

val MaterialTheme.ptutMotion: PtutMotion
    @Composable
    @ReadOnlyComposable
    get() = LocalPtutMotion.current

/** Compose animations count in whole milliseconds; the tokens are [Duration] so they read right. */
val Duration.inMillis: Int get() = inWholeMilliseconds.toInt()

/**
 * A duration that is zero when the reader has asked for no motion.
 *
 * Zero rather than a skipped animation, so the call sites stay one shape: every `animateTo` still
 * runs, it just lands on the same frame it started — which is page 06's "holds the last frame"
 * without a second code path per animation to get wrong.
 */
@Composable
fun Duration.orStill(): Int = if (ptutReduceMotion) 0 else inMillis

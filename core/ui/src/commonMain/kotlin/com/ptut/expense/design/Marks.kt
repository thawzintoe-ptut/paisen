package com.ptut.expense.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp

/**
 * Page 05's three marks, drawn. The arithmetic is in [MarkFrames] and tested against the JSON;
 * everything here is a `Canvas` reading it and a clock driving it.
 *
 * All three either take a live flag or play once. "Nothing loops without a live state behind it…
 * A loop with no state is decoration."
 */

/**
 * `pulse-dot.json` — a still dot with a halo swelling out of it, 1.6s.
 *
 * "A still dot and a scanning dot must not look the same — that ambiguity is the whole failure the
 * spec is written against." Which is why [live] is a parameter rather than an assumption: with
 * nothing running this is the still dot, and the two states are then genuinely different marks.
 * So it pulses only while a scan runs, never just because a folder is watched (#131).
 */
@Composable
fun PulseDot(
    live: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = Spacing.xs,
    color: Color = MaterialTheme.ptut.live,
) {
    // Reduced motion holds the dot, which is the frame the loop rests on anyway — so a reader who
    // turned animation off loses the liveness cue but not the colour or the sentence beside it.
    // Branching rather than drawing a loop nobody can see keeps the clock off entirely when the
    // importer is idle, which is nearly always.
    if (live && !ptutReduceMotion) {
        PulsingDot(modifier = modifier, size = size, color = color)
    } else {
        Canvas(modifier.size(size)) { drawCircle(color = color, radius = this.size.minDimension / 2f) }
    }
}

@Composable
private fun PulsingDot(modifier: Modifier, size: Dp, color: Color) {
    val period = MaterialTheme.ptutMotion.pulse.inMillis
    val progress by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo",
    )

    Canvas(modifier.size(size)) {
        val core = this.size.minDimension / 2f
        val frame = pulseFrameAt(progress)
        drawCircle(color = color, radius = core * frame.haloScale, alpha = frame.haloAlpha)
        drawCircle(color = color, radius = core)
    }
}

/**
 * `check-draw.json` — a check stroking itself on, 600ms, once.
 *
 * "Plays once and holds — a check that loops turns a completed action into an unfinished one." So
 * there is no repeat here at all: the [Animatable] runs to 1 and stays, and under reduced motion
 * it starts there.
 */
@Composable
fun CheckDraw(
    modifier: Modifier = Modifier,
    size: Dp = Sizes.toastIcon,
    color: Color = MaterialTheme.ptut.toastOn,
) {
    val millis = MaterialTheme.ptutMotion.checkDraw.orStill()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(millis, easing = LinearEasing)) }

    Canvas(modifier.size(size)) {
        val frame = checkFrameAt(progress.value)
        if (frame.drawn <= 0f) return@Canvas

        val unit = this.size.minDimension / 100f
        val stroke = Path().apply {
            moveTo(24f * unit, 52f * unit)
            lineTo(42f * unit, 70f * unit)
            lineTo(77f * unit, 31f * unit)
        }
        scale(frame.scale, frame.scale, center) {
            drawPath(
                path = stroke.leading(frame.drawn),
                color = color,
                style = Stroke(width = 9f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * The delete toast's mark: the bin strokes itself on, its lid swings open and slams shut. Plays once
 * and holds, like [CheckDraw], and starts finished under reduced motion. No fade anywhere.
 */
@Composable
fun BinDraw(
    modifier: Modifier = Modifier,
    size: Dp = Sizes.toastIcon,
    color: Color = MaterialTheme.ptut.toastOn,
) {
    val millis = MaterialTheme.ptutMotion.binDraw.orStill()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(millis, easing = LinearEasing)) }

    Canvas(modifier.size(size)) {
        val t = progress.value
        val unit = this.size.minDimension / 100f
        val line = Stroke(width = 8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val drawn = (t / BIN_BODY_SHARE).coerceAtMost(1f)
        if (drawn <= 0f) return@Canvas

        val body = Path().apply {
            moveTo(30f * unit, 42f * unit)
            lineTo(34f * unit, 84f * unit)
            lineTo(66f * unit, 84f * unit)
            lineTo(70f * unit, 42f * unit)
            moveTo(44f * unit, 52f * unit)
            lineTo(45f * unit, 74f * unit)
            moveTo(56f * unit, 52f * unit)
            lineTo(55f * unit, 74f * unit)
        }
        drawPath(path = body.leading(drawn), color = color, style = line)

        val lid = Path().apply {
            moveTo(22f * unit, 33f * unit)
            lineTo(78f * unit, 33f * unit)
            moveTo(41f * unit, 33f * unit)
            lineTo(41f * unit, 24f * unit)
            lineTo(59f * unit, 24f * unit)
            lineTo(59f * unit, 33f * unit)
        }
        rotate(binLidAngle(t), pivot = Offset(22f * unit, 33f * unit)) {
            drawPath(path = lid, color = color, style = line)
        }
    }
}

/** How much of [BinDraw]'s run the body takes to stroke on; the lid moves in the rest. */
private const val BIN_BODY_SHARE = 0.4f

/** Shut, swung open, past shut as it slams, then settled. Negative lifts the free end. */
private fun binLidAngle(t: Float): Float = when {
    t < 0.35f -> 0f
    t < 0.6f -> -32f * ((t - 0.35f) / 0.25f)
    t < 0.82f -> -32f + 38f * ((t - 0.6f) / 0.22f)
    else -> 6f * (1f - (t - 0.82f) / 0.18f)
}

/**
 * `arc-loop.json` — a ring whose gap breathes as it turns, 3s of source at 0.55x.
 *
 * "It should be noticed only if you look directly at it." It sits over the empty-state slot at
 * half opacity (p33), where the live state behind it is the app waiting for slips that have not come.
 */
@Composable
fun ArcLoop(
    size: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val period = MaterialTheme.ptutMotion.arcLoop.inMillis
    val still = ptutReduceMotion

    val progress by rememberInfiniteTransition(label = "arc").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "turn",
    )

    Canvas(modifier.size(size)) {
        // p33: reduced motion holds the loop's last frame (#131).
        val frame = arcFrameAt(if (still) 1f else progress)
        val unit = this.size.minDimension / 100f
        val stroke = 7f * unit
        val inset = (100f - 76f) / 2f * unit + stroke / 2f
        drawArc(
            color = color,
            startAngle = frame.turnDegrees - 90f,
            sweepAngle = frame.sweepDegrees,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
            alpha = alpha,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** The leading [fraction] of a path, which is what a Lottie trim modifier draws. */
private fun Path.leading(fraction: Float): Path {
    if (fraction >= 1f) return this
    val measure = PathMeasure().apply { setPath(this@leading, false) }
    return Path().also { measure.getSegment(0f, measure.length * fraction, it) }
}

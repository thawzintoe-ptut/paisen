package com.ptut.expense.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min
import kotlin.time.Duration

/**
 * #180's line drawings, ink on sky: empty Today (p24), the delete sheet (p17) and first run (p23).
 * Coordinates are read from the PDF's own paths, in one 160 by 100 box fitted inside the slot the
 * way an SVG viewBox is. The slot's colour and shape stay with the screen; timing is in
 * [quietDayFrameAt], [slipDropFrameAt] and [slipFlightFrameAt].
 */

/** A slip, a cup with steam and a sun on the ground line. */
@Composable
fun QuietDayArt(modifier: Modifier = Modifier) {
    val progress = loopProgress(MaterialTheme.ptutMotion.quietDay)
    val ink = artInk()
    val cup = remember { cupPath() }
    val handle = remember { handlePath() }
    val wisp = remember { wispPath() }

    Canvas(modifier.fillMaxSize()) {
        val frame = progress?.let { quietDayFrameAt(it.value) } ?: QuietDayStill
        inBox {
            ground(ink)
            drawCircle(ink.paper, SUN_RADIUS, Sun)
            drawCircle(ink.line, SUN_RADIUS, Sun, style = LineStroke)
            rays(ink, frame.rayAlpha)
            slip(ink, left = 31f, top = 37.9f)
            outlined(cup, fill = ink.sky, ink = ink)
            drawPath(handle, ink.line, style = LineStroke)
            steam(wisp, x = 76f, frame.first, ink)
            steam(wisp, x = 90f, frame.second, ink)
        }
    }
}

/** A slip dropping into a bin. */
@Composable
fun SlipDropArt(modifier: Modifier = Modifier) {
    val progress = loopProgress(MaterialTheme.ptutMotion.slipDrop)
    val ink = artInk()
    val bin = remember { binPath() }
    val handle = remember { lidHandlePath() }

    Canvas(modifier.fillMaxSize()) {
        val frame = progress?.let { slipDropFrameAt(it.value) } ?: SlipDropStill
        inBox {
            ground(ink)
            // Drawn before the bin, so the bin's fill hides whatever has fallen in.
            slip(ink, left = 69f, top = frame.top, alpha = frame.alpha)
            outlined(bin, fill = ink.sky, ink = ink)
            Slats.forEach { x -> stroke(ink, Offset(x, 63f), Offset(x, 80f), alpha = SLAT_ALPHA) }
            stroke(ink, Offset(61f, 52f), Offset(99f, 52f))
            drawPath(handle, ink.line, style = LineStroke)
        }
    }
}

/** A slip flying from a phone into a folder, two dots beside it. */
@Composable
fun SlipFlightArt(modifier: Modifier = Modifier) {
    val progress = loopProgress(MaterialTheme.ptutMotion.slipFlight)
    val ink = artInk()
    val folder = remember { folderPath() }

    Canvas(modifier.fillMaxSize()) {
        val frame = progress?.let { slipFlightFrameAt(it.value) } ?: SlipFlightStill
        inBox {
            ground(ink)
            card(fill = ink.paper, ink = ink, left = 30f, top = 22f, width = 35f, height = 64f, corner = 7f)
            stroke(ink, Offset(43f, 28f), Offset(52f, 28f))
            card(fill = ink.pale, ink = ink, left = 37f, top = 37f, width = 21f, height = 26f, corner = 3f)
            // Drawn before the folder, so it lands behind the folder's front.
            scale(frame.scale, pivot = Offset(frame.centreX, frame.centreY)) {
                note(
                    ink = ink,
                    left = frame.centreX - NOTE_WIDTH / 2f,
                    top = frame.centreY - NOTE_HEIGHT / 2f,
                    alpha = frame.alpha,
                )
            }
            outlined(folder, fill = ink.sky, ink = ink)
            drawCircle(ink.line, 2.5f, Offset(118f, 39f), alpha = frame.firstDot)
            drawCircle(ink.line, 1.75f, Offset(130f, 49f), alpha = frame.secondDot)
        }
    }
}

/** Two people and a bill torn between them, for the empty Shared tab (p34, #136). */
@Composable
fun SharedArt(modifier: Modifier = Modifier) {
    val progress = loopProgress(MaterialTheme.ptutMotion.sharedSplit)
    val ink = artInk()
    val ok = MaterialTheme.ptut.ok
    val leftHalf = remember { billHalfPath(left = true) }
    val rightHalf = remember { billHalfPath(left = false) }
    val leftBody = remember { personPath(left = 24f) }
    val rightBody = remember { personPath(left = 106f) }

    Canvas(modifier.fillMaxSize()) {
        val frame = progress?.let { sharedSplitFrameAt(it.value) } ?: SharedSplitStill
        inBox {
            ground(ink)
            drawCircle(ink.line, HEAD_RADIUS, Offset(39f, 39f))
            drawPath(leftBody, ink.line)
            drawCircle(ink.sky, HEAD_RADIUS, Offset(121f, 39f))
            drawCircle(ink.line, HEAD_RADIUS, Offset(121f, 39f), style = LineStroke)
            outlined(rightBody, fill = ink.sky, ink = ink)
            // Closed, the halves meet at the bottom; p34 draws them apart.
            val gap = BILL_CLOSE * (1f - frame.apart)
            translate(left = gap) {
                outlined(leftHalf, fill = ink.paper, ink = ink)
                stroke(ink, Offset(64.97f, 46.21f), Offset(68.95f, 45.79f))
                stroke(ink, Offset(65.6f, 52.18f), Offset(70.57f, 51.65f))
            }
            translate(left = -gap) {
                outlined(rightHalf, fill = ink.paper, ink = ink)
                stroke(ink, Offset(91.05f, 45.79f), Offset(95.02f, 46.21f))
                stroke(ink, Offset(90.42f, 51.76f), Offset(93.41f, 52.07f))
            }
            drawCircle(ok, CHECK_RADIUS, CheckAt, alpha = frame.checkAlpha)
            drawPath(checkPath(), ink.paper, style = Stroke(width = CHECK_LINE, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = frame.checkAlpha)
        }
    }
}

/** The four colours of every drawing, from roles, so night needs no second set. */
@Immutable
private class ArtInk(val line: Color, val paper: Color, val sky: Color, val pale: Color)

@Composable
private fun artInk() = ArtInk(
    line = MaterialTheme.colorScheme.onSurface,
    // tertiaryContainer carries Palette.container, Sky's white.
    paper = MaterialTheme.colorScheme.tertiaryContainer,
    sky = MaterialTheme.ptut.accent,
    pale = MaterialTheme.ptut.barTrack,
)

/** The loop's clock, or null under reduced motion, where each drawing holds its still frame. */
@Composable
private fun loopProgress(period: Duration): State<Float>? {
    if (ptutReduceMotion) return null
    return rememberInfiniteTransition(label = "drawing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period.inMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loop",
    )
}

/** Fits the 160 by 100 box inside the canvas and centres it, as an SVG's "xMidYMid meet" does. */
private inline fun DrawScope.inBox(block: DrawScope.() -> Unit) {
    val unit = min(size.width / BOX_WIDTH, size.height / BOX_HEIGHT)
    translate(left = (size.width - BOX_WIDTH * unit) / 2f, top = (size.height - BOX_HEIGHT * unit) / 2f) {
        scale(unit, pivot = Offset.Zero, block = block)
    }
}

private fun DrawScope.ground(ink: ArtInk) = stroke(ink, Offset(20f, 88f), Offset(140f, 88f), alpha = GROUND_ALPHA)

private fun DrawScope.stroke(ink: ArtInk, from: Offset, to: Offset, alpha: Float = 1f) =
    drawLine(ink.line, from, to, strokeWidth = LINE, cap = StrokeCap.Round, alpha = alpha)

private fun DrawScope.outlined(path: Path, fill: Color, ink: ArtInk) {
    drawPath(path, fill)
    drawPath(path, ink.line, style = LineStroke)
}

private fun DrawScope.card(
    fill: Color,
    ink: ArtInk,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    corner: Float,
    alpha: Float = 1f,
) {
    val at = Offset(left, top)
    val size = Size(width, height)
    drawRoundRect(fill, at, size, CornerRadius(corner), alpha = alpha)
    drawRoundRect(ink.line, at, size, CornerRadius(corner), style = LineStroke, alpha = alpha)
}

/** The slip p17 and p24 share: 22 by 28, three lines of print. */
private fun DrawScope.slip(ink: ArtInk, left: Float, top: Float, alpha: Float = 1f) {
    card(ink.paper, ink, left, top, width = 22f, height = 28f, corner = 3f, alpha = alpha)
    stroke(ink, Offset(left + 5f, top + 7f), Offset(left + 17f, top + 7f), alpha)
    stroke(ink, Offset(left + 5f, top + 12.5f), Offset(left + 13f, top + 12.5f), alpha)
    stroke(ink, Offset(left + 5f, top + 18f), Offset(left + 15f, top + 18f), alpha)
}

/** p23's smaller slip: 15 by 18, two lines of print. */
private fun DrawScope.note(ink: ArtInk, left: Float, top: Float, alpha: Float) {
    card(ink.paper, ink, left, top, NOTE_WIDTH, NOTE_HEIGHT, corner = 2f, alpha = alpha)
    stroke(ink, Offset(left + 3f, top + 6f), Offset(left + 12f, top + 6f), alpha)
    stroke(ink, Offset(left + 3f, top + 11f), Offset(left + 9f, top + 11f), alpha)
}

/** Four short rays, 15 to 19 out from the sun's centre. */
private fun DrawScope.rays(ink: ArtInk, alpha: Float) {
    Compass.forEach { way ->
        stroke(ink, Sun + way * RAY_FROM, Sun + way * RAY_TO, alpha)
    }
}

private fun DrawScope.steam(wisp: Path, x: Float, frame: Wisp, ink: ArtInk) {
    translate(left = x, top = -frame.rise) { drawPath(wisp, ink.line, style = LineStroke, alpha = frame.alpha) }
}

private fun cupPath() = Path().apply {
    moveTo(67f, 59f)
    lineTo(98f, 59f)
    lineTo(98f, 76f)
    arcTo(Rect(76f, 65f, 98f, 87f), startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false)
    lineTo(78f, 87f)
    arcTo(Rect(67f, 65f, 89f, 87f), startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false)
    close()
}

private fun handlePath() = Path().apply {
    moveTo(98f, 64f)
    lineTo(102f, 64f)
    arcTo(Rect(96f, 64f, 108f, 76f), startAngleDegrees = -90f, sweepAngleDegrees = 180f, forceMoveTo = false)
    lineTo(98f, 76f)
}

/** One wisp at x 0, from the cup's rim at y 52 up to 42. */
private fun wispPath() = Path().apply {
    moveTo(0f, 52f)
    cubicTo(-3f, 48f, 3f, 46f, 0f, 42f)
}

private fun binPath() = Path().apply {
    moveTo(65f, 56f)
    lineTo(95f, 56f)
    lineTo(92f, 87f)
    lineTo(68f, 87f)
    close()
}

private fun lidHandlePath() = Path().apply {
    moveTo(75f, 52f)
    lineTo(75f, 48f)
    lineTo(85f, 48f)
    lineTo(85f, 52f)
}

private fun folderPath() = Path().apply {
    moveTo(90f, 56f)
    lineTo(104f, 56f)
    lineTo(108f, 61f)
    lineTo(128f, 61f)
    lineTo(128f, 84f)
    lineTo(90f, 84f)
    close()
}

/** A head-and-shoulders body: up from the ground, a half circle of 15, back down. */
private fun personPath(left: Float) = Path().apply {
    moveTo(left, 88f)
    lineTo(left, 73f)
    arcTo(Rect(left, 58f, left + 30f, 88f), startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false)
    lineTo(left + 30f, 88f)
}

/** One half of the torn bill, as p34 draws it, its torn edge along the bottom. */
private fun billHalfPath(left: Boolean) = Path().apply {
    val points = if (left) {
        listOf(60.05f to 37.67f, 70.99f to 36.53f, 74.96f to 74.32f, 72.21f to 72.09f, 69.49f to 74.89f, 66.74f to 72.67f, 64.02f to 75.47f)
    } else {
        listOf(89.01f to 36.53f, 99.94f to 37.67f, 95.98f to 75.47f, 93.75f to 72.72f, 90.5f to 74.89f, 88.28f to 72.15f, 85.04f to 74.32f)
    }
    moveTo(points.first().first, points.first().second)
    points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    close()
}

private fun checkPath() = Path().apply {
    moveTo(79.1f, 25f)
    lineTo(79.7f, 25.6f)
    lineTo(80.89f, 24.4f)
}

private const val BOX_WIDTH = 160f
private const val BOX_HEIGHT = 100f

/** 1.5 of the box: 3.2dp in Today's 342-wide slot, as p24 measures. */
private const val LINE = 1.5f
private val LineStroke = Stroke(width = LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)

private const val GROUND_ALPHA = 0.18f
private const val SLAT_ALPHA = 0.5f
private val Slats = listOf(74f, 80f, 86f)
private val Sun = Offset(123f, 24.7f)
private const val SUN_RADIUS = 10f
private const val RAY_FROM = 15f
private const val RAY_TO = 19f
private val Compass = listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))
private const val NOTE_WIDTH = 15f
private const val NOTE_HEIGHT = 18f
private const val HEAD_RADIUS = 9f

// How far each half of the bill moves to meet the other at the bottom.
private const val BILL_CLOSE = 5f
private val CheckAt = Offset(80f, 25f)
private const val CHECK_RADIUS = 1.8f
private const val CHECK_LINE = 0.45f

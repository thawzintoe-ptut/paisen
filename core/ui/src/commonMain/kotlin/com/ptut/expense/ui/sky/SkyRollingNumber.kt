package com.ptut.expense.ui.sky

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.ptut.expense.design.PtutMotion
import com.ptut.expense.design.inMillis
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutReduceMotion
import com.ptut.expense.design.ptutType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A hero amount, `฿ 1,600.00`, whose digits roll like an odometer when it changes and count up
 * from 0 the first time it shows. Nothing fades. [symbol] is drawn as given, gap included.
 */
@Composable
fun SkyRollingAmount(
    symbol: String,
    whole: String,
    fraction: String,
    color: Color,
    modifier: Modifier = Modifier,
    fractionColor: Color = color,
) {
    val type = MaterialTheme.ptutType
    Row(modifier) {
        Text(symbol, style = type.heroSymbol, color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
        SkyRollingNumber(whole, type.hero, color, Modifier.alignByBaseline())
        if (fraction.isNotEmpty()) SkyRollingNumber(fraction, type.heroFraction, fractionColor, Modifier.alignByBaseline())
    }
}

/**
 * [text] drawn one character per slot. A digit is a reel 0–9 that turns to its new place: up when
 * the value grows, down when it falls, and the lower places turn more, which reads as a counter.
 * A digit or comma that appears rolls in while its width opens; one that goes rolls out.
 */
@Composable
fun SkyRollingNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val drawn = remember(style, color) { style.copy(color = color) }
    val glyphs = remember(drawn, measurer) { Glyphs { char -> measurer.measure(char.toString(), drawn) } }
    val motion = MaterialTheme.ptutMotion
    val still = ptutReduceMotion
    // The last figure shown, kept across leaving the screen, so coming back does not count up again.
    var shown by rememberSaveable { mutableStateOf("") }
    val reels = remember { Reels(shown) }
    LaunchedEffect(text, still) {
        reels.roll(from = shown, to = text, still = still, motion = motion)
        shown = text
    }
    Spacer(
        modifier
            .layout { measurable, constraints ->
                val width = reels.width(glyphs).roundToInt().coerceAtMost(constraints.maxWidth)
                val height = glyphs.height
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height, mapOf(FirstBaseline to glyphs.baseline, LastBaseline to glyphs.baseline)) {
                    placeable.place(0, 0)
                }
            }
            .drawBehind { reels.draw(this, glyphs) },
    )
}

/** Each character measured once in the figure's style. Tabular figures make every digit as wide. */
private class Glyphs(private val measure: (Char) -> TextLayoutResult) {
    private val cache = HashMap<Char, TextLayoutResult>()

    fun of(char: Char): TextLayoutResult = cache.getOrPut(char) { measure(char) }

    val height: Int get() = of('0').size.height
    val baseline: Int get() = of('0').firstBaseline.roundToInt()

    fun width(char: Char): Int = of(char).size.width
}

/**
 * One slot of the figure, counted from the right so the ones stay the ones. [position] runs on a
 * line that never wraps (12.0 shows 2); [presence] is how far its width has opened.
 */
private class Reel(digit: Boolean, var char: Char, position: Float, presence: Float) {
    var isDigit: Boolean = digit
    val position = Animatable(position)
    val presence = Animatable(presence)

    /** +1 when the value grew, so every slot moves up; -1 when it fell, so every slot moves down. */
    var side: Float = 1f

    /** Rolling out of the figure rather than into it, so it leaves the way the reels turn. */
    var leaving: Boolean = false
}

private class Reels(initial: String) {
    private val slots = mutableStateMapOf<Int, Reel>()

    init {
        initial.reversed().forEachIndexed { place, char -> slots[place] = reelFor(char, presence = 1f) }
    }

    suspend fun roll(from: String, to: String, still: Boolean, motion: PtutMotion) {
        val before = from.digitsValue()
        val after = to.digitsValue()
        val side = if (after >= before) 1f else -1f
        val count = maxOf(to.length, (slots.keys.maxOrNull() ?: -1) + 1)
        val digitsBelow = digitsBelowEach(if (to.length >= from.length) to else from)
        coroutineScope {
            for (place in 0 until count) {
                val target = to.getOrNull(to.length - 1 - place)
                // A digit that appears starts at 0 and counts to its value as it rolls in.
                val reel = slots.getOrPut(place) { reelFor(target?.takeUnless(Char::isDigit) ?: '0', presence = 0f) }
                reel.side = side
                reel.leaving = target == null
                if (target != null) {
                    reel.isDigit = target.isDigit()
                    if (!reel.isDigit) reel.char = target
                }
                val goal = if (target == null || !reel.isDigit) {
                    reel.position.value
                } else {
                    (reel.position.value.roundToInt() + turns(digitsBelow.getOrElse(place) { place }, before, after, target.digitToInt(), reel)) * 1f
                }
                // The first showing counts up at length; a later change is quick and lands together.
                val spec = if (from.isEmpty()) {
                    tween<Float>(
                        durationMillis = motion.numberRoll.inMillis,
                        delayMillis = (count - 1 - place) * motion.numberStagger.inMillis,
                        easing = motion.emphasized,
                    )
                } else {
                    tween<Float>(durationMillis = motion.numberChange.inMillis, easing = motion.emphasized)
                }
                val open = if (target == null) 0f else 1f
                launch { if (still) reel.position.snapTo(goal) else reel.position.animateTo(goal, spec) }
                launch { if (still) reel.presence.snapTo(open) else reel.presence.animateTo(open, spec) }
            }
        }
        slots.keys.filter { it >= to.length }.forEach { slots.remove(it) }
    }

    /**
     * How far one digit's reel turns, signed. It always lands on [target]. The ones turn up to two
     * extra rounds and the tens one, when the value moved that far; higher places go straight there.
     */
    private fun turns(placeValue: Int, before: Long, after: Long, target: Int, reel: Reel): Int {
        val power = POWERS.getOrElse(placeValue) { return 0 }
        val travel = after / power - before / power
        if (travel == 0L) return 0
        val sign = if (travel > 0) 1 else -1
        val current = reel.position.value.roundToInt().mod(DIGITS)
        val rounds = minOf(EXTRA_ROUNDS.getOrElse(placeValue) { 0 }.toLong(), abs(travel) / DIGITS).toInt()
        return sign * (rounds * DIGITS + (sign * (target - current)).mod(DIGITS))
    }

    fun width(glyphs: Glyphs): Float {
        var total = 0f
        for (reel in slots.values) total += glyphs.width(if (reel.isDigit) '0' else reel.char) * reel.presence.value
        return total
    }

    fun draw(scope: DrawScope, glyphs: Glyphs) = with(scope) {
        // The slot is laid out exactly one glyph tall, so the canvas height is the line height.
        val height = size.height
        var x = 0f
        for (place in (slots.keys.maxOrNull() ?: -1) downTo 0) {
            val reel = slots[place] ?: continue
            val full = glyphs.width(if (reel.isDigit) '0' else reel.char)
            val shown = full * reel.presence.value
            val left = x + (shown - full) / 2f
            val lift = (if (reel.leaving) -reel.side else reel.side) * (1f - reel.presence.value) * height
            clipRect(left = x, top = 0f, right = x + shown, bottom = height) {
                if (reel.isDigit) {
                    val turn = reel.position.value
                    val base = floor(turn)
                    val part = turn - base
                    val digit = base.toInt().mod(DIGITS)
                    drawText(glyphs.of('0' + digit), topLeft = Offset(left, lift - part * height))
                    drawText(glyphs.of('0' + (digit + 1).mod(DIGITS)), topLeft = Offset(left, lift + (1f - part) * height))
                } else {
                    drawText(glyphs.of(reel.char), topLeft = Offset(left, lift))
                }
            }
            x += shown
        }
    }

    private fun reelFor(char: Char, presence: Float) =
        Reel(digit = char.isDigit(), char = char, position = if (char.isDigit()) char.digitToInt() * 1f else 0f, presence = presence)
}

private fun String.digitsValue(): Long = filter(Char::isDigit).take(MAX_DIGITS).toLongOrNull() ?: 0L

/** For each slot counted from the right, how many digits sit to its right: its place value. */
private fun digitsBelowEach(text: String): Map<Int, Int> {
    var digits = 0
    return text.reversed().withIndex().associate { (place, char) -> (place to digits).also { if (char.isDigit()) digits++ } }
}

private const val DIGITS = 10
private const val MAX_DIGITS = 18

/** Extra whole rounds a place may turn on a big change: the ones two, the tens one. */
private val EXTRA_ROUNDS = listOf(2, 1)

private val POWERS: List<Long> = generateSequence(1L) { it * DIGITS }.take(MAX_DIGITS).toList()

package com.ptut.expense.domain

import com.ptut.expense.locale.AppText
import kotlin.jvm.JvmInline

/**
 * Money is always an integer count of satang. Floats never touch it.
 *
 * The arithmetic lives here so no caller has to reach for [raw]: a loose `Long` is how a
 * `Double` gets introduced later by someone in a hurry.
 */
@JvmInline
value class Satang(val raw: Long) : Comparable<Satang> {
    operator fun plus(other: Satang): Satang = Satang(raw + other.raw)

    operator fun minus(other: Satang): Satang = Satang(raw - other.raw)

    operator fun unaryMinus(): Satang = Satang(-raw)

    override fun compareTo(other: Satang): Int = raw.compareTo(other.raw)

    /**
     * A share of [other], for a bar or a percent. This is the one place money is allowed to
     * meet a float, and nothing spendable comes back out — the result is a ratio, not an
     * amount. A zero denominator is 0f, because "no spending yet" draws an empty bar.
     */
    fun ratioTo(other: Satang): Float =
        if (other.raw == 0L) 0f else raw.toFloat() / other.raw

    /**
     * The storage format. A money column is an INTEGER count of satang and never a REAL, so
     * the data layer converts through this instead of reaching for [raw].
     */
    fun toMinorUnits(): Long = raw

    companion object {
        val Zero = Satang(0)

        fun ofMinorUnits(minorUnits: Long): Satang = Satang(minorUnits)
    }
}

fun Iterable<Satang>.sum(): Satang {
    var total = 0L
    for (amount in this) total += amount.raw
    return Satang(total)
}

enum class Direction { Income, Expense }

/**
 * Uses U+2212 MINUS, not a hyphen: it is the same width as a digit, so signed amounts stay
 * aligned in a column set with tabular figures.
 */
private fun Direction.sign(): String = when (this) {
    Direction.Income -> "+"
    Direction.Expense -> "−"
}

fun Satang.formatSigned(direction: Direction): String = "${direction.sign()}${format()}"

/**
 * For a net, where the sign comes from the value itself. Never use [format] for a net: it
 * is magnitude-only, so a loss would render identically to a gain.
 */
fun Satang.formatNet(): String = when {
    raw < 0 -> "−${format()}"
    raw > 0 -> "+${format()}"
    else -> format()
}

/**
 * A hero amount set in one size buries the digits among the decoration, so the symbol and
 * the satang are drawn smaller than the baht. The split belongs here, not in a composable,
 * because it has to agree with [format] character for character.
 */
data class MoneyParts(val symbol: String, val whole: String, val fraction: String)

/**
 * [formatSigned] split the way [formatParts] splits an unsigned one. The sign rides on the
 * symbol rather than becoming a fourth span, so a hero still sets three sizes and not four.
 */
fun Satang.formatSignedParts(direction: Direction): MoneyParts =
    formatParts().let { it.copy(symbol = "${direction.sign()}${it.symbol}") }

/**
 * [formatParts] with no sign and the satang only when there are some: "฿642" or "฿642.50". The
 * record screen draws the direction in colour, ruby for spending and green for income.
 */
fun Satang.formatCompactParts(): MoneyParts = formatParts().let { if (it.fraction == ".00") it.copy(fraction = "") else it }

fun Satang.formatParts(): MoneyParts {
    val formatted = format()
    return MoneyParts(
        symbol = formatted.take(1),
        whole = formatted.drop(1).substringBefore('.'),
        fraction = ".${formatted.substringAfter('.')}",
    )
}

/**
 * For contentDescription. TalkBack reads "฿" as nothing useful and "−" as "dash", so an
 * amount is spelled out instead of handed over symbolic.
 */
fun Satang.spoken(): String = AppText.copy.phrases.spokenBaht(format().drop(1))

/**
 * For a sentence, where `฿485.00` reads as machine output. The satang show up only when there
 * are some, so `฿485` and `฿485.50` are both honest. Never use it in a column: without a fixed
 * fraction the digits stop aligning.
 */
fun Satang.formatCompact(): String = format().removeSuffix(".00")

/**
 * For a label under a chart bar, where a four-digit baht figure is wider than the bar it sits
 * on. `฿1,250` becomes `1.2k`; anything under a thousand keeps its digits, because rounding
 * ฿642 to "0.6k" loses the only precision the reader came for.
 *
 * No currency symbol: the hero above the chart has already said what the units are, and seven
 * of these across a week would be seven copies of the same fact.
 */
fun Satang.abbreviated(): String {
    val baht = (if (raw < 0) -raw else raw) / 100
    if (baht < 1_000) return baht.toString()
    val thousands = baht / 100 / 10.0
    return if (baht % 1_000 < 50) "${baht / 1_000}k" else "${thousands}k"
}

/** Magnitude only — the sign is applied by the caller that knows the direction. */
fun Satang.format(): String {
    val absolute = if (raw < 0) -raw else raw
    val baht = absolute / 100
    val satang = absolute % 100
    return "฿${groupThousands(baht)}.${satang.toString().padStart(2, '0')}"
}

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(digit)
        }
    }
}

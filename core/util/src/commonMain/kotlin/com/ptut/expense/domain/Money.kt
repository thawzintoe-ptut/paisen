package com.ptut.expense.domain

import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.currencyCopy
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

    /** The size of a difference, whichever way it went. */
    fun abs(): Satang = if (raw < 0L) Satang(-raw) else this

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

fun Satang.formatSigned(direction: Direction, money: HomeMoney = Home.money): String = "${direction.sign()}${format(money)}"

/**
 * For a net, where the sign comes from the value itself. Never use [format] for a net: it
 * is magnitude-only, so a loss would render identically to a gain.
 */
fun Satang.formatNet(money: HomeMoney = Home.money): String = when {
    raw < 0 -> "−${format(money)}"
    raw > 0 -> "+${format(money)}"
    else -> format(money)
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
fun Satang.formatSignedParts(direction: Direction, money: HomeMoney = Home.money): MoneyParts =
    formatParts(money).let { it.copy(symbol = "${direction.sign()}${it.symbol}") }

/**
 * [formatParts] with no sign and the satang only when there are some: "฿642" or "฿642.50". The
 * record screen draws the direction in colour, ruby for spending and green for income.
 */
fun Satang.formatCompactParts(money: HomeMoney = Home.money): MoneyParts = formatParts(money).let { if (it.fraction.trimEnd('0') == ".") it.copy(fraction = "") else it }

/** #42: the symbol is the home's own, one character or several ("HK$"), and kyat has no fraction. */
fun Satang.formatParts(money: HomeMoney = Home.money): MoneyParts {
    val digits = formatDigits(money)
    return MoneyParts(
        symbol = money.symbol,
        whole = digits.substringBefore('.'),
        fraction = if ('.' in digits) ".${digits.substringAfter('.')}" else "",
    )
}

/**
 * For contentDescription. TalkBack reads "฿" as nothing useful and "−" as "dash", so an
 * amount is spelled out instead of handed over symbolic.
 */
fun Satang.spoken(money: HomeMoney = Home.money): String =
    if (money.code == HomeMoney.Baht.code) AppText.copy.phrases.spokenBaht(formatDigits(money)) else currencyCopy().spoken(formatDigits(money), money.code)

/**
 * For a sentence, where `฿485.00` reads as machine output. The satang show up only when there
 * are some, so `฿485` and `฿485.50` are both honest. Never use it in a column: without a fixed
 * fraction the digits stop aligning.
 */
fun Satang.formatCompact(money: HomeMoney = Home.money): String =
    if (money.digits == 0) format(money) else format(money).removeSuffix("." + "0".repeat(money.digits))

/**
 * For a label under a chart bar, where a four-digit baht figure is wider than the bar it sits
 * on. `฿1,250` becomes `1.2k`; anything under a thousand keeps its digits, because rounding
 * ฿642 to "0.6k" loses the only precision the reader came for.
 *
 * No currency symbol: the hero above the chart has already said what the units are, and seven
 * of these across a week would be seven copies of the same fact.
 */
fun Satang.abbreviated(money: HomeMoney = Home.money): String {
    val whole = (if (raw < 0) -raw else raw) / minorPerWhole(money)
    if (whole < THOUSAND) return whole.toString()
    // Truncated to one decimal of a thousand: 1,299 is "1.2k", never "1.3k".
    val thousands = whole / HUNDRED / TENTHS_PER_ONE
    return if (whole % THOUSAND < HUNDRED / 2) "${whole / THOUSAND}k" else "${thousands}k"
}

private const val THOUSAND = 1_000L
private const val HUNDRED = 100L
private const val TENTHS_PER_ONE = 10.0

/**
 * Magnitude only — the sign is applied by the caller that knows the direction. #42: in the home's
 * money; #48: a shared group passes its own.
 */
fun Satang.format(money: HomeMoney = Home.money): String = "${money.symbol}${formatDigits(money)}"

/** [format] without the symbol: "1,250.00", or "29,500" for kyat. */
fun Satang.formatDigits(money: HomeMoney = Home.money): String {
    val digits = money.digits
    val absolute = if (raw < 0) -raw else raw
    val unit = minorPerWhole(money)
    val whole = groupThousands(absolute / unit)
    return if (digits == 0) whole else "$whole.${(absolute % unit).toString().padStart(digits, '0')}"
}

/** 100 for baht, 1 for kyat: how many minor units one whole home unit holds. */
fun minorPerWhole(money: HomeMoney = Home.money): Long = pow10(money.digits)

/** 10 to the [exponent], as a Long: the one place money's decimal shifts are computed. */
fun pow10(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10 }
    return result
}

/** "1,850,000": [value]'s digits in threes, with no symbol. */
fun groupThousands(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(digit)
        }
    }
}

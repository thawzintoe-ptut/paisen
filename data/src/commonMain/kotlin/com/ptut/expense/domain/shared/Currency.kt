package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/**
 * #181: an expense in another currency, at a rate the user types (decided 13 Oct). Amounts stay
 * in each currency's own minor units and the rate is an exact decimal, so nothing here is a float.
 */

/**
 * [digits] is how many minor units the currency is typed with. The ones Thai travellers carry as
 * cash with no coins in use (VND, KRW, LAK…) take none, whatever ISO 4217 lists. [rateUnit] is how
 * many units the rate is typed for: "฿1.38 per 1,000 ₫" reads better than "฿0.00138 per ₫".
 */
data class Currency(val code: String, val symbol: String, val digits: Int, val rateUnit: Long = 1) {
    val isBaht: Boolean get() = code == Currencies.Thb.code
}

object Currencies {
    val Thb = Currency("THB", "฿", 2)

    val all: List<Currency> = listOf(
        Thb,
        Currency("USD", "$", 2),
        Currency("EUR", "€", 2),
        Currency("GBP", "£", 2),
        Currency("JPY", "¥", 0, rateUnit = 100),
        Currency("CNY", "CN¥", 2),
        Currency("KRW", "₩", 0, rateUnit = 1_000),
        Currency("SGD", "S$", 2),
        Currency("MYR", "RM", 2),
        Currency("VND", "₫", 0, rateUnit = 1_000),
        Currency("LAK", "₭", 0, rateUnit = 1_000),
        Currency("KHR", "៛", 0, rateUnit = 1_000),
        Currency("MMK", "K", 0, rateUnit = 1_000),
        Currency("IDR", "Rp", 0, rateUnit = 1_000),
        Currency("PHP", "₱", 2),
        Currency("HKD", "HK$", 2),
        Currency("TWD", "NT$", 2),
        Currency("AUD", "A$", 2),
        Currency("INR", "₹", 2),
        Currency("CHF", "CHF", 2),
    )

    /** A code this table does not hold still reads back, with two digits, rather than failing. */
    fun of(code: String): Currency = all.firstOrNull { it.code == code } ?: Currency(code, code, 2)
}

/** Baht per one unit of a currency: [unscaled] ÷ 10^[scale], exactly. */
data class Rate(val unscaled: Long, val scale: Int) {
    init {
        require(unscaled > 0) { "A rate is above zero" }
        require(scale in 0..MAX_SCALE) { "A rate keeps at most $MAX_SCALE decimals" }
    }

    /** The rate as the user types it, for [unit] units of the currency: "1.38" per 1,000 ₫. */
    fun typedFor(unit: Long): String {
        val shift = decimalsOf(unit)
        var digits = unscaled
        var decimals = scale - shift
        while (decimals < 0) {
            digits *= 10
            decimals++
        }
        val text = digits.toString().padStart(decimals + 1, '0')
        return if (decimals == 0) text else "${text.dropLast(decimals)}.${text.takeLast(decimals)}".trimEnd('0').trimEnd('.')
    }

    companion object {
        const val MAX_SCALE = 9
        private const val MAX_TYPED_DECIMALS = 6

        /** What the user typed as baht for [unit] units, or null when it is not a positive number. */
        fun parse(text: String, unit: Long = 1): Rate? {
            val clean = text.trim().replace(",", "")
            if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }) return null
            val whole = clean.substringBefore('.')
            val fraction = clean.substringAfter('.', "")
            if (fraction.length > MAX_TYPED_DECIMALS || whole.length > 12) return null
            var unscaled = (whole + fraction).trimStart('0').ifEmpty { "0" }.toLong()
            var scale = fraction.length + decimalsOf(unit)
            if (unscaled == 0L) return null
            while (scale > 0 && unscaled % 10 == 0L) {
                unscaled /= 10
                scale--
            }
            return if (scale > MAX_SCALE) null else Rate(unscaled, scale)
        }
    }
}

/** 1 → 0, 1,000 → 3. The units are always a power of ten. */
private fun decimalsOf(unit: Long): Int {
    var rest = unit
    var count = 0
    while (rest >= 10) {
        rest /= 10
        count++
    }
    return count
}

/**
 * The one rule from an amount in [currency] to baht: amount × rate, rounded half away from zero
 * to the satang. THB needs no rate. Every screen and every balance goes through here (#181).
 */
fun toSatang(amountMinor: Long, currency: Currency, rate: Rate?): Satang {
    if (currency.isBaht) return Satang.ofMinorUnits(amountMinor)
    requireNotNull(rate) { "${currency.code} needs a rate to become baht" }
    // amount × rate × 100 ÷ 10^(digits + scale): the × 100 comes off the divisor, so a long rate never overflows it.
    val shift = currency.digits + rate.scale - SATANG_DIGITS
    val factor = if (shift >= 0) rate.unscaled else rate.unscaled * pow10(-shift)
    val magnitude = mulDivRound(if (amountMinor < 0) -amountMinor else amountMinor, factor, pow10(shift.coerceAtLeast(0)))
    return Satang.ofMinorUnits(if (amountMinor < 0) -magnitude else magnitude)
}

/** a × b ÷ divisor, rounded half up, for a, b ≥ 0. */
internal fun mulDivRound(a: Long, b: Long, divisor: Long): Long {
    val (quotient, remainder) = mulDivRem(a, b, divisor)
    // remainder × 2 ≥ divisor, without doubling anything past a Long.
    return if (remainder >= divisor - remainder) quotient + 1 else quotient
}

/**
 * a × b ÷ divisor, rounded down, and what is left, for a, b ≥ 0. The product is held in 128 bits,
 * so a big VND bill at a nine-decimal rate, or a large Exact split, never passes a Long's range.
 */
internal fun mulDivRem(a: Long, b: Long, divisor: Long): Pair<Long, Long> {
    require(a >= 0 && b >= 0 && divisor > 0)
    val x = a.toULong()
    val y = b.toULong()
    val low = x * y
    val high = multiplyHigh(x, y)
    val d = divisor.toULong()
    var remainder = 0uL
    var quotient = 0uL
    for (bit in PRODUCT_BITS - 1 downTo 0) {
        val next = if (bit >= Long.SIZE_BITS) (high shr (bit - Long.SIZE_BITS)) and 1uL else (low shr bit) and 1uL
        // remainder < divisor < 2^63, so doubling it stays inside 64 bits.
        remainder = (remainder shl 1) or next
        if (remainder >= d) {
            remainder -= d
            check(bit < Long.SIZE_BITS - 1) { "An amount this large does not fit in satang" }
            quotient = quotient or (1uL shl bit)
        }
    }
    return quotient.toLong() to remainder.toLong()
}

/** The upper 64 bits of x × y. */
private fun multiplyHigh(x: ULong, y: ULong): ULong {
    val x0 = x and LOW_32
    val x1 = x shr HALF_BITS
    val y0 = y and LOW_32
    val y1 = y shr HALF_BITS
    val p01 = x0 * y1
    val p10 = x1 * y0
    val middle = ((x0 * y0) shr HALF_BITS) + (p01 and LOW_32) + (p10 and LOW_32)
    return x1 * y1 + (p01 shr HALF_BITS) + (p10 shr HALF_BITS) + (middle shr HALF_BITS)
}

/** A satang is a hundredth of a baht. */
private const val SATANG_DIGITS = 2
private const val PRODUCT_BITS = 128
private const val HALF_BITS = 32
private const val LOW_32 = 0xFFFF_FFFFuL

internal fun pow10(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10 }
    return result
}

/** "₫1,850,000", "$240.00": an amount in its own currency, for a row or a field. */
fun formatMinor(amountMinor: Long, currency: Currency): String {
    val magnitude = if (amountMinor < 0) -amountMinor else amountMinor
    val unit = pow10(currency.digits)
    val whole = groupDigits(magnitude / unit)
    val sign = if (amountMinor < 0) "−" else ""
    if (currency.digits == 0) return "$sign${currency.symbol}$whole"
    val fraction = (magnitude % unit).toString().padStart(currency.digits, '0')
    return "$sign${currency.symbol}$whole.$fraction"
}

/** Typed digits ("1850000", "240.5") to minor units, or null when the text is not an amount. */
fun parseMinor(text: String, currency: Currency): Long? {
    val clean = text.trim().replace(",", "")
    if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }) return null
    val whole = clean.substringBefore('.').ifEmpty { "0" }
    val fraction = clean.substringAfter('.', "")
    if (fraction.length > currency.digits || whole.length > 13) return null
    return whole.toLong() * pow10(currency.digits) + fraction.padEnd(currency.digits, '0').ifEmpty { "0" }.toLong()
}

private fun groupDigits(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(digit)
        }
    }
}

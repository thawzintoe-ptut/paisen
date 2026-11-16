package com.ptut.expense.ui

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.Satang

/** The numpad's state. Kept as typed text, never a Double — money never touches a float. */
data class AmountEntry(
    /** Exactly what was typed, "" for nothing: the text Check slip's keyboard field shows (#204). */
    val typed: String = "",
    /**
     * True while [typed] is a figure the screen supplied rather than one the reader keyed in.
     *
     * A seeded amount already carries its two satang, which is the pad's own digit ceiling, so
     * without this flag every key but backspace was a no-op on the two screens that open on an
     * amount. The reader saw a keypad that did nothing. A keyboard field opens it selected.
     */
    val pending: Boolean = false,
    /** #42: decimals this amount takes: the home's, or a foreign currency's on the Add sheet. Kyat takes none. */
    val digits: Int = Home.money.digits,
) {
    val display: String get() = typed.ifEmpty { "0" }

    private val satangTyped: Int
        get() = typed.substringAfter('.', "").length

    /** The first digit on a supplied figure replaces it, the way a pre-selected value behaves. */
    fun digit(digit: Char): AmountEntry = when {
        pending -> AmountEntry(digit.toString(), digits = digits)
        typed.contains('.') && satangTyped >= digits -> this
        else -> copy(typed = typed + digit)
    }

    fun decimal(): AmountEntry = when {
        digits == 0 -> this
        pending -> AmountEntry("0.", digits = digits)
        typed.contains('.') -> this
        else -> copy(typed = typed.ifEmpty { "0" } + '.')
    }

    /** Backspace corrects rather than replaces, so it is the way into editing a supplied figure. */
    fun backspace(): AmountEntry = AmountEntry(typed.dropLast(1), digits = digits)

    /** The same figure for a currency with [next] decimals; decimals it no longer takes are dropped. */
    fun withDigits(next: Int): AmountEntry {
        if (next == digits) return this
        val whole = typed.substringBefore('.')
        val fraction = typed.substringAfter('.', "").take(next)
        val kept = if (next == 0 || !typed.contains('.')) whole else "$whole.$fraction"
        return copy(typed = kept, digits = next)
    }

    val isEmpty: Boolean get() = typed.isEmpty() || toSatang() == Satang.Zero

    companion object {
        /**
         * Starts the pad on an amount the slip actually stated.
         *
         * FR-7 forbids pre-filling a *guess*, not a reading: a figure the parser lifted off the
         * slip is the thing the reader is being asked to check, and making them retype it invites
         * a typo where there was none. An amount that did not read arrives here as null and the
         * pad stays empty.
         *
         * It arrives [pending], because "check this" only works if disagreeing with it is one key.
         */
        fun of(amount: Satang, digits: Int = Home.money.digits): AmountEntry {
            val minor = amount.toMinorUnits()
            if (digits == 0) return AmountEntry("$minor", pending = true, digits = 0)
            val unit = pow10(digits)
            val fraction = (minor % unit).toString().padStart(digits, '0')
            return AmountEntry("${minor / unit}.$fraction", pending = true, digits = digits)
        }

        /**
         * Text from the number keyboard rather than keys on the pad (#204), held to the pad's rules.
         * A `,` counts as `.`, because some decimal keyboards print one. Null for a letter, a
         * second `.` or a third satang digit, so the field can refuse that key.
         */
        fun ofText(text: String, digits: Int = Home.money.digits): AmountEntry? {
            val typed = text.replace(',', '.')
            val allowed = typed.all { it in '0'..'9' || it == '.' } &&
                typed.count { it == '.' } <= (if (digits == 0) 0 else 1) &&
                typed.substringAfter('.', "").length <= digits
            return if (allowed) AmountEntry(typed, digits = digits) else null
        }

        private fun pow10(exponent: Int): Long {
            var result = 1L
            repeat(exponent) { result *= 10 }
            return result
        }
    }

    /** Parsed digit by digit, so the value never round-trips through a Double. */
    fun toSatang(): Satang {
        if (typed.isEmpty()) return Satang(0)
        val whole = typed.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: return Satang(0)
        if (digits == 0) return Satang(whole)
        val fraction = typed.substringAfter('.', "").padEnd(digits, '0').take(digits).toLongOrNull() ?: 0L
        var unit = 1L
        repeat(digits) { unit *= 10 }
        return Satang(whole * unit + fraction)
    }
}

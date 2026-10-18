package com.ptut.expense.ui

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
) {
    val display: String get() = typed.ifEmpty { "0" }

    private val satangTyped: Int
        get() = typed.substringAfter('.', "").length

    /** The first digit on a supplied figure replaces it, the way a pre-selected value behaves. */
    fun digit(digit: Char): AmountEntry = when {
        pending -> AmountEntry(digit.toString())
        typed.contains('.') && satangTyped >= 2 -> this
        else -> copy(typed = typed + digit)
    }

    fun decimal(): AmountEntry = when {
        pending -> AmountEntry("0.")
        typed.contains('.') -> this
        else -> copy(typed = typed.ifEmpty { "0" } + '.')
    }

    /** Backspace corrects rather than replaces, so it is the way into editing a supplied figure. */
    fun backspace(): AmountEntry = AmountEntry(typed.dropLast(1))

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
        fun of(amount: Satang): AmountEntry {
            val minor = amount.toMinorUnits()
            val satang = (minor % 100).toString().padStart(2, '0')
            return AmountEntry("${minor / 100}.$satang", pending = true)
        }

        /**
         * Text from the number keyboard rather than keys on the pad (#204), held to the pad's rules.
         * A `,` counts as `.`, because some decimal keyboards print one. Null for a letter, a
         * second `.` or a third satang digit, so the field can refuse that key.
         */
        fun ofText(text: String): AmountEntry? {
            val typed = text.replace(',', '.')
            val allowed = typed.all { it in '0'..'9' || it == '.' } &&
                typed.count { it == '.' } <= 1 &&
                typed.substringAfter('.', "").length <= 2
            return if (allowed) AmountEntry(typed) else null
        }
    }

    /** Parsed digit by digit, so the value never round-trips through a Double. */
    fun toSatang(): Satang {
        if (typed.isEmpty()) return Satang(0)
        val baht = typed.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: return Satang(0)
        val satang = typed.substringAfter('.', "").padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        return Satang(baht * 100 + satang)
    }
}

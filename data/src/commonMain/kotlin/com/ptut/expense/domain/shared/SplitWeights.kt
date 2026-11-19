package com.ptut.expense.domain.shared

/** D11: two decimals, as basis points. "33.33" is 3,333. */
fun parsePercent(text: String): Long? {
    val clean = text.trim()
    if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }) return null
    val whole = clean.substringBefore('.').ifEmpty { "0" }
    val fraction = clean.substringAfter('.', "")
    if (fraction.length > 2 || whole.length > 3) return null
    return whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
}

/** Basis points back as typed text: 3,333 is "33.33", 5,000 is "50". */
fun percentText(basisPoints: Long): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return if (fraction == 0L) "$whole" else "$whole.${fraction.toString().padStart(2, '0')}".trimEnd('0')
}

/** Shares count whole: "1.5" is 1, and anything unreadable is 0. */
fun wholeShares(text: String): Long = text.substringBefore('.').toLongOrNull() ?: 0L

/** True when [wholeShares] drops a decimal that was not zero, so "1.5" says it counts as 1. */
fun dropsShareDecimal(text: String): Boolean = text.substringAfter('.', "").any { it != '0' }

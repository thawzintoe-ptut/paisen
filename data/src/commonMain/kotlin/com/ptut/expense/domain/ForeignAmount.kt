package com.ptut.expense.domain

import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.convert

/**
 * What a record cost in another currency (#33), and the rate that turned it into the home money the
 * ledger counts (#44). The record's own amount is that home amount, so every total, report and
 * weekly note reads it unchanged. Same shape as a shared expense in another currency (#181).
 */
data class ForeignAmount(val currency: String, val originalMinor: Long, val rate: Rate) {
    val symbol: String get() = Currencies.of(currency).symbol

    /** The home amount the ledger counts, by the one conversion (FR-9: no floats). */
    val inHome: Satang get() = convert(originalMinor, Currencies.of(currency), rate, Currencies.of(Home.money.code))
}

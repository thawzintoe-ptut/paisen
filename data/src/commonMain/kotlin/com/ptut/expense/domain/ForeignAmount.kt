package com.ptut.expense.domain

import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.toSatang

/**
 * Phase A of the kyat wallets (#33): what a record cost in another currency, and the rate that
 * turned it into the baht the ledger counts. The record's own amount stays baht, so every total,
 * report and weekly note reads it unchanged. Same shape as a shared expense in another currency (#181).
 */
data class ForeignAmount(val currency: String, val originalMinor: Long, val rate: Rate) {
    val symbol: String get() = Currencies.of(currency).symbol

    /** The baht the ledger counts, by the one conversion shared groups use (FR-9: no floats). */
    val baht: Satang get() = toSatang(originalMinor, Currencies.of(currency), rate)
}

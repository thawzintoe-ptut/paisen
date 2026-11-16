package com.ptut.expense.domain.slip

/**
 * Raw slip text to a [SlipRead]. Pure: no clock, no storage, no platform.
 *
 * Hands the text to the first [SlipFormat] that knows its labels. Text no format claims still goes
 * to K PLUS, the one app read before #34, so a slip whose labels the reader garbled is read as it
 * always was.
 */
object SlipParser {

    private val baht: List<SlipFormat> = listOf(KPlusSlip, WaveSlip)
    private val wallets: List<SlipFormat> = listOf(KbzPaySlip, WavePaySlip, AyaPaySlip)
    private val fallback: SlipFormat = KPlusSlip

    /** [withWallets] off reads as before #33: a Myanmar wallet receipt is no slip at all. */
    fun parse(lines: List<String>, withWallets: Boolean = true): SlipRead = formatOf(lines, withWallets).parse(lines)

    fun hasSlipLabels(lines: List<String>, withWallets: Boolean = true): Boolean = formats(withWallets).any { it.hasSlipLabels(lines) }

    private fun formats(withWallets: Boolean): List<SlipFormat> = if (withWallets) baht + wallets else baht

    private fun formatOf(lines: List<String>, withWallets: Boolean): SlipFormat =
        formats(withWallets).firstOrNull { it.recognises(lines) } ?: fallback
}

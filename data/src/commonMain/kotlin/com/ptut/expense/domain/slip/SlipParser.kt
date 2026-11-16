package com.ptut.expense.domain.slip

/**
 * Raw slip text to a [SlipRead]. Pure: no clock, no storage, no platform.
 *
 * Hands the text to the first [SlipFormat] that knows its labels. Text no format claims still goes
 * to K PLUS, the one app read before #34, so a slip whose labels the reader garbled is read as it
 * always was.
 */
object SlipParser {

    private val formats: List<SlipFormat> = listOf(KPlusSlip, WaveSlip)
    private val fallback: SlipFormat = KPlusSlip

    fun parse(lines: List<String>): SlipRead = formatOf(lines).parse(lines)

    fun hasSlipLabels(lines: List<String>): Boolean = formats.any { it.hasSlipLabels(lines) }

    private fun formatOf(lines: List<String>): SlipFormat =
        formats.firstOrNull { it.recognises(lines) } ?: fallback
}

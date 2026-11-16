package com.ptut.expense.domain.slip

/**
 * One app's slip layout. Each app prints its own labels, so each gets its own reader, and
 * [SlipParser] picks the one whose labels it finds (#34).
 */
interface SlipFormat {

    /** Whether [lines] carry this app's own labels, so this reader should take the slip. */
    fun recognises(lines: List<String>): Boolean

    /** Whether [lines] are a slip at all, even when no shop name could be read (#113). */
    fun hasSlipLabels(lines: List<String>): Boolean

    fun parse(lines: List<String>): SlipRead
}

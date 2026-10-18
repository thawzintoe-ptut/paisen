package com.ptut.expense.ui

/** The delete key's label. Callers map it back to an action, so the glyph is public. */
const val BACKSPACE: String = "⌫"

internal val NumpadRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", BACKSPACE),
)

/** The only bridge from a keypad label into [AmountEntry]. Every Sky keypad types through it. */
fun AmountEntry.press(key: String): AmountEntry = when (key) {
    "." -> decimal()
    BACKSPACE -> backspace()
    else -> digit(key.first())
}

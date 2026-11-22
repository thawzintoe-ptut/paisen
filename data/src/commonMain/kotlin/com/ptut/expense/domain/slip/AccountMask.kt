package com.ptut.expense.domain.slip

/** The digits a mask keeps on screen: K PLUS prints `xxx-x-x4821-x`, and four is what people quote. */
private const val MASK_DIGITS = 4

private val DIGITS = Regex("""\d""")

/**
 * J04: an account line as the app names it, `••4821`, or null when fewer than four digits read. The
 * bank's own mask characters and dashes go; only the digits a person could recognise stay.
 */
fun accountMask(line: String): String? {
    val digits = DIGITS.findAll(line).map { it.value }.joinToString("")
    if (digits.length < MASK_DIGITS) return null
    return "••" + digits.takeLast(MASK_DIGITS)
}

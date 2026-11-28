package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.slip.WalletText.after
import com.ptut.expense.domain.slip.WalletText.has
import com.ptut.expense.domain.slip.WalletText.readsAsPayee

/**
 * #35: a KBZPay e-receipt in English, from a public receipt (June 2025):
 *
 * ```
 * KBZ BANK                 E-Receipt
 * -29,500.00 Ks             <- minus: money out
 * Transaction Time   14/06/2025 12:10:15
 * Transaction No.    20 digits
 * Transaction Type   Customer Buy Goods
 * Transfer To        <merchant code> - <shop name>
 * Amount             -29,500.00 Ks
 * Thank you for using KBZPay!
 * ```
 */
object KbzPaySlip : SlipFormat {

    override fun recognises(lines: List<String>): Boolean {
        val trimmed = lines.map { it.trim() }
        return (trimmed.has("KBZPay") || trimmed.has("KBZ BANK")) && trimmed.any { AMOUNT.containsMatchIn(it) }
    }

    override fun hasSlipLabels(lines: List<String>): Boolean = recognises(lines)

    override fun parse(lines: List<String>): SlipRead {
        val trimmed = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val name = trimmed.payee() ?: return SlipRead.Unreadable
        val figure = trimmed.firstNotNullOfOrNull { AMOUNT.find(it) }
        val amount = figure?.let { WalletText.kyat(it.groupValues[2]) }
        val direction = if (figure?.groupValues?.get(1) == "+") Direction.Income else Direction.Expense
        val occurredAt = trimmed.firstNotNullOfOrNull(WalletText::slashedDateTime)
        val reference = trimmed.after("Transaction No")?.replace(" ", "")?.takeIf { REFERENCE.matches(it) }
        return if (amount != null && occurredAt != null && reference != null) {
            SlipRead.Complete(name, amount, occurredAt, reference, currency = WalletText.KYAT, knownDirection = direction)
        } else {
            SlipRead.Partial(name, amount, occurredAt, reference, currency = WalletText.KYAT, knownDirection = direction)
        }
    }

    /** "70169501010 - LimitedCollections Online" on one line or two; the code is never the shop. */
    private fun List<String>.payee(): String? {
        forEachIndexed { index, line ->
            val coded = CODE_THEN_NAME.matchEntire(line) ?: return@forEachIndexed
            val rest = coded.groupValues[1].trim()
            if (rest.readsAsPayee()) return rest
            getOrNull(index + 1)?.takeIf { it.readsAsPayee() }?.let { return it }
        }
        return (after("Transfer To") ?: after("Transfer From"))?.takeIf { it.readsAsPayee() }
    }

    private val AMOUNT = Regex("""([+-]?)\s*(${WalletText.FIGURE})\s*Ks\b""")
    private val CODE_THEN_NAME = Regex("""\d{6,}\s*-\s*(.*)""")
    private val REFERENCE = Regex("""\d{10,}""")
}

package com.ptut.expense.domain.slip

import com.ptut.expense.domain.slip.WalletText.after
import com.ptut.expense.domain.slip.WalletText.has
import com.ptut.expense.domain.slip.WalletText.readsAsPayee

/**
 * #36: an AYA Pay e-receipt in English, from AYA's own guide:
 *
 * ```
 * Successful
 * 1,000 MMK
 * E-Receipt                 AYA Pay
 * Transaction Type  Subscriber Transfer To Wallet
 * Transaction ID    2070…
 * Sender Phone / Sender Name      SU SU
 * Receiver Phone / Receiver Name  KYAW KYAW
 * Date and Time     23 July 2024, 10:06 AM
 * ```
 * It names both people, so money in or out is decided by whose name is yours (#26).
 */
object AyaPaySlip : SlipFormat {

    override fun recognises(lines: List<String>): Boolean {
        val trimmed = lines.map { it.trim() }
        return trimmed.has("AYA Pay") && trimmed.any { AMOUNT.containsMatchIn(it) }
    }

    override fun hasSlipLabels(lines: List<String>): Boolean = recognises(lines)

    override fun parse(lines: List<String>): SlipRead {
        val trimmed = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val receiver = trimmed.after("Receiver Name")?.takeIf { it.readsAsPayee() } ?: return SlipRead.Unreadable
        val sender = trimmed.after("Sender Name")?.takeIf { it.readsAsPayee() }
        val amount = trimmed.firstNotNullOfOrNull { AMOUNT.find(it) }?.let { WalletText.kyat(it.groupValues[1]) }
        val occurredAt = trimmed.firstNotNullOfOrNull(WalletText::dayMonthYearClock)
        val reference = trimmed.after("Transaction ID")?.replace(" ", "")?.takeIf { it.any(Char::isDigit) }
        return if (amount != null && occurredAt != null && reference != null) {
            SlipRead.Complete(receiver, amount, occurredAt, reference, sender = sender, currency = WalletText.KYAT)
        } else {
            SlipRead.Partial(receiver, amount, occurredAt, reference, sender = sender, currency = WalletText.KYAT)
        }
    }

    private val AMOUNT = Regex("""(${WalletText.FIGURE})\s*MMK\b""")
}

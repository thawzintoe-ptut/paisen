package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.slip.WalletText.after
import com.ptut.expense.domain.slip.WalletText.has
import com.ptut.expense.domain.slip.WalletText.readsAsPayee

/**
 * #36: a WavePay receipt in English, from Wave Money's own guide:
 *
 * ```
 * Successful
 * 1,000 Ks
 * To                         <- or From: the direction
 * Myat Myat Su   active
 * 09xxxxxxxxx
 * Type  Account · Fees  0 Ks · Total Amount  1,000 Ks
 * Transaction ID  371515335
 * Date & Time  18 April 2024 • 3:06 PM
 * Note  pocket money
 * ```
 * Distinct from 2C2P WAVE ([WaveSlip]), which is in baht.
 */
object WavePaySlip : SlipFormat {

    override fun recognises(lines: List<String>): Boolean {
        val trimmed = lines.map { it.trim() }
        return trimmed.has("Total Amount") && trimmed.any { AMOUNT.containsMatchIn(it) } && !trimmed.has("THB")
    }

    override fun hasSlipLabels(lines: List<String>): Boolean = recognises(lines)

    override fun parse(lines: List<String>): SlipRead {
        val trimmed = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val toAt = trimmed.indexOfFirst { it.equals("To", ignoreCase = true) }
        val fromAt = trimmed.indexOfFirst { it.equals("From", ignoreCase = true) }
        val at = if (toAt != -1) toAt else fromAt
        val name = if (at == -1) null else trimmed.drop(at + 1).take(NAME_DEPTH).firstOrNull { it.readsAsPayee() && !it.equals("active", true) }
        name ?: return SlipRead.Unreadable
        val amount = trimmed.firstNotNullOfOrNull { AMOUNT.find(it) }?.let { WalletText.kyat(it.groupValues[1]) }
        val occurredAt = trimmed.firstNotNullOfOrNull(WalletText::dayMonthYearClock)
        val reference = trimmed.after("Transaction ID")?.replace(" ", "")?.takeIf { it.any(Char::isDigit) }
        val direction = if (toAt == -1 && fromAt != -1) Direction.Income else Direction.Expense
        val cleanName = name.removeSuffix("active").trim()
        return if (amount != null && occurredAt != null && reference != null) {
            SlipRead.Complete(cleanName, amount, occurredAt, reference, currency = WalletText.KYAT, knownDirection = direction)
        } else {
            SlipRead.Partial(cleanName, amount, occurredAt, reference, currency = WalletText.KYAT, knownDirection = direction)
        }
    }

    private const val NAME_DEPTH = 3
    private val AMOUNT = Regex("""(\d[\d,]*(?:\.\d{1,2})?)\s*Ks\b""")
}

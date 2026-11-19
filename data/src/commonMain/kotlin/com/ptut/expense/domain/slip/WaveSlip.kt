package com.ptut.expense.domain.slip

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.MAX_AMOUNT_WHOLE_DIGITS
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/**
 * The 2C2P WAVE e-receipt (#39): a baht wallet for Myanmar workers in Thailand. Laid out from Wave
 * Money's own picture, since no real receipt has been through ML Kit yet:
 *
 * ```
 * E-Receipt                 2C2P wave
 * Success
 * 16/05/2025, 08:31 AM      <- day first, a Bangkok wall clock
 * Transfer
 * MR. AUNG KO               <- sender: the person holding the phone
 * xxxxxxxxxxx1234
 * MS. THIDA WIN             <- receiver: the counterparty
 * xxxxxxxxxxx5678
 * Amount   1,000.00 THB
 * Fee      0.00 THB
 * Total    1,000.00 THB
 * Note     Transfer
 * ```
 *
 * The picture shows no transaction number, so a receipt without one reads as [SlipRead.Partial]
 * and goes to To check rather than being filed with no duplicate key.
 */
object WaveSlip : SlipFormat {

    override fun recognises(lines: List<String>): Boolean {
        val trimmed = lines.map { it.trim() }
        return trimmed.any { it.contains(BRAND, ignoreCase = true) } ||
            (trimmed.any { it.equals(RECEIPT_HEADER, ignoreCase = true) } && trimmed.any { AMOUNT_SHAPE.containsMatchIn(it) })
    }

    override fun hasSlipLabels(lines: List<String>): Boolean = recognises(lines)

    override fun parse(lines: List<String>): SlipRead {
        val trimmed = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val name = trimmed.counterparty() ?: return SlipRead.Unreadable

        val amount = trimmed.amount()
        val occurredAt = trimmed.firstNotNullOfOrNull(::parseBangkokInstant)
        val reference = trimmed.reference()
        val unsure = if (amount != null && amount.toMinorUnits() % SATANG_PER_BAHT != 0L) setOf(SlipField.Amount) else emptySet()

        return if (amount != null && occurredAt != null && reference != null) {
            SlipRead.Complete(name, amount, occurredAt, reference, unsure)
        } else {
            SlipRead.Partial(name, amount, occurredAt, reference, unsure)
        }
    }

    /**
     * The last name above `Amount`. Sender and receiver print the same name-then-wallet shape, so
     * position decides: the sender comes first, and every receipt is money out, as on K PLUS.
     */
    private fun List<String>.counterparty(): String? {
        val end = indexOfFirst { it.startsWith(AMOUNT_LABEL, ignoreCase = true) }.let { if (it == -1) size else it }
        return subList(0, end).lastOrNull { it.readsAsName() }
    }

    /** The first baht figure at or below `Amount`: the fee and the total always print after it. */
    private fun List<String>.amount(): Satang? {
        val start = indexOfFirst { it.startsWith(AMOUNT_LABEL, ignoreCase = true) }.coerceAtLeast(0)
        return drop(start).firstNotNullOfOrNull { AMOUNT_SHAPE.find(it) }?.let { parseAmount(it.groupValues[1]) }
    }

    /** Read only under a label; a guessed number would become a wrong duplicate key. */
    private fun List<String>.reference(): String? {
        val index = indexOfFirst { line -> REFERENCE_LABELS.any { line.startsWith(it, ignoreCase = true) } }
        if (index == -1) return null
        val sameLine = this[index].substringAfter(':', "").trim().replace(" ", "")
        val value = sameLine.ifEmpty { getOrNull(index + 1)?.replace(" ", "") }
        return value?.takeIf { REFERENCE_SHAPE.matches(it) && it.any(Char::isDigit) }
    }

    private fun String.readsAsName(): Boolean =
        count { it in 'a'..'z' || it in 'A'..'Z' } >= MIN_NAME_LETTERS &&
            !WALLET_SHAPE.matches(this) &&
            !AMOUNT_SHAPE.containsMatchIn(this) &&
            !DATE_SHAPE.containsMatchIn(this) &&
            NOT_NAMES.none { equals(it, ignoreCase = true) } &&
            !contains(BRAND, ignoreCase = true)

    /** `1,000.00` to satang, counted and never divided (FR-9). */
    private fun parseAmount(figure: String): Satang? {
        val digits = figure.replace(",", "")
        if (digits.substringBefore('.').length > MAX_AMOUNT_WHOLE_DIGITS) return null
        val baht = digits.substringBefore('.').toLongOrNull() ?: return null
        val satang = digits.substringAfter('.', "00").padEnd(2, '0').take(2).toLongOrNull() ?: return null
        return Satang.ofMinorUnits(baht * SATANG_PER_BAHT + satang)
    }

    private fun parseBangkokInstant(line: String): Long? {
        val match = DATE_SHAPE.find(line) ?: return null
        val (day, month, year, hour, minute, marker) = match.destructured
        val clock = hour.toInt()
        val hourOfDay = when {
            marker.equals("AM", ignoreCase = true) -> if (clock == NOON) 0 else clock
            else -> if (clock == NOON) NOON else clock + NOON
        }
        return runCatching {
            LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hourOfDay, minute.toInt())
                .toInstant(BangkokTime).toEpochMilliseconds()
        }.getOrNull()
    }

    private const val BRAND = "2C2P"
    private const val RECEIPT_HEADER = "E-Receipt"
    private const val AMOUNT_LABEL = "Amount"
    private const val SATANG_PER_BAHT = 100L
    private const val NOON = 12
    private const val MIN_NAME_LETTERS = 2

    private val REFERENCE_LABELS = listOf("Transaction ID", "Transaction No", "Reference", "Ref No")

    /** Row and screen labels that sit in the name block or near it. */
    private val NOT_NAMES = listOf("E-Receipt", "Success", "Transfer", "Payment", "Top Up", "Note", "Share", "Done")

    private val AMOUNT_SHAPE = Regex("""(\d[\d,]*(?:\.\d{1,2})?)\s*THB""", RegexOption.IGNORE_CASE)
    private val DATE_SHAPE = Regex("""(\d{1,2})/(\d{1,2})/(\d{4}),?\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm])""")

    /** A wallet number, masked or not: digits with mask characters and nothing else. */
    private val WALLET_SHAPE = Regex("""[\dxX*\s-]{6,}""")
    private val REFERENCE_SHAPE = Regex("""[A-Za-z0-9]{6,}""")
}

package com.ptut.expense.domain.slip

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Raw slip text to a [SlipRead]. Pure: no clock, no storage, no platform.
 *
 * Every K PLUS slip prints the same skeleton, so the parser reads by landmark rather than by line
 * number. The landmarks are the labels the app itself renders, which is what makes them stable:
 *
 * ```
 * Payment Completed          <- header. It names the rail, not the direction: all 19 are money out
 * 14 Aug 26  7:58 PM         <- the one date, in the one format
 * MR. SOMCHAI J              <- sender block: name, bank, masked account
 * KBank
 * xxx-x-xxxxx-x
 * NTT DATA Digital Payment   <- recipient block, variable height
 * STATIC11010296
 * 00
 * Transaction ID:
 * 016200000005CPM00005
 * Amount:
 * 50.00 Baht
 * Fee:
 * 0.00 Baht
 * ```
 */
object SlipParser {

    fun parse(lines: List<String>): SlipRead {
        val trimmed = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val name = trimmed.counterparty() ?: return SlipRead.Unreadable

        val amount = trimmed.valueAfter(AMOUNT_LABEL)?.let(::parseAmount)
        val occurredAt = trimmed.dateLine()?.let(::parseBangkokInstant)
        val reference = trimmed.referenceAfterLabel()
        val unsure = unsureFields(name, amount)

        return if (amount != null && occurredAt != null && reference != null) {
            SlipRead.Complete(name.value, amount, occurredAt, reference, unsure)
        } else {
            SlipRead.Partial(name.value, amount, occurredAt, reference, unsure)
        }
    }

    /**
     * Whether the text carries a K PLUS label, so an image with no shop read is still a slip (#113).
     * A Thai-only recipient keeps its labels; a chat screenshot has none and is not queued.
     */
    fun hasSlipLabels(lines: List<String>): Boolean = lines.map { it.trim() }.any { line ->
        line.equals(AMOUNT_LABEL, ignoreCase = true) ||
            line.equals(REFERENCE_LABEL, ignoreCase = true) ||
            line.endsWith(HEADER_SUFFIX, ignoreCase = true)
    }

    /**
     * Where this parser had to choose, rather than where an image was poor.
     *
     * That distinction is the whole limit of the feature. The nineteen recorded slips carry an
     * `uncertain` list each, and most of it is capture quality — the same LAWSON merchant is
     * marked on one photo and clean on the next three. Nothing in the text can recover that; it
     * needs per-block confidence from the reader itself, which lands with #17 and #18.
     *
     * So this marks only what the text can prove: two rules, both pointing at a decision made
     * above. Eight of the nineteen come back with nothing marked, which is the intended shape.
     */
    private fun unsureFields(name: ReadName, amount: Satang?): Set<SlipField> = buildSet {
        // FR-9's own trap. A misread decimal on `.00` is still `.00`; on `28.25` it is a
        // different number, and satang is where this app has promised to be exact.
        if (amount != null && amount.toMinorUnits() % SATANG_PER_BAHT != 0L) add(SlipField.Amount)

        // [joinWrappedName] chose to glue two lines. Its own reason for being careful is that
        // gluing the wrong pair invents a merchant the rule engine will never match again.
        if (name.wrapped) add(SlipField.Counterparty)
    }

    /** The line after [label]: a value sits directly under the label that names it. */
    private fun List<String>.valueAfter(label: String): String? {
        val index = indexOfFirst { it.equals(label, ignoreCase = true) }
        return if (index == -1 || index + 1 > lastIndex) null else this[index + 1]
    }

    /**
     * The transaction id, read under `Transaction ID:` and never hunted for above it.
     *
     * A bill slip prints the biller's own number above this label, sometimes wrapped across two
     * lines, and a parser that hunts for a reference-shaped line there files half a number. Below
     * it, the QR caption (`Scan for`) can sort between label and id, so the first id-shaped line
     * wins, with any space the reader put inside it removed. With none, the next line stands.
     */
    private fun List<String>.referenceAfterLabel(): String? {
        val index = indexOfFirst { it.equals(REFERENCE_LABEL, ignoreCase = true) }
        if (index == -1) return null
        val below = drop(index + 1)
        return below.take(REFERENCE_SEARCH_DEPTH)
            .map { it.replace(" ", "") }
            .firstOrNull { REFERENCE_SHAPE.matches(it) && it.any(Char::isDigit) }
            ?: below.firstOrNull()
    }

    /** The date is the first line that parses as one. The header sits above it and never will. */
    private fun List<String>.dateLine(): String? = take(HEADER_SEARCH_DEPTH)
        .firstOrNull { DATE_SHAPE.matches(it) }

    /**
     * The recipient's name, from the block that starts after the sender's own masked account.
     *
     * Position is the only thing separating the two blocks — a transfer repeats the same
     * name/bank/account shape for both — so the first account-shaped line is the sender's, and
     * anything a parser finds above it belongs to the person holding the phone.
     *
     * Within the block only the first two lines are candidates. K PLUS prints the display name
     * first and at most one alternate-script or company line under it; everything below is codes,
     * bank names and account numbers.
     */
    private fun List<String>.counterparty(): ReadName? {
        val senderAccount = indexOfFirst { MASKED_ID_SHAPE.matches(it) }
        if (senderAccount == -1) return null

        val block = subList(senderAccount + 1, size)
            .takeWhile { !it.equals(REFERENCE_LABEL, ignoreCase = true) }
            .let { it.drop(it.bannerLines()) }
        val candidate = block.take(NAME_CANDIDATE_DEPTH)
            .indexOfFirst { it.readsAsName() }
        if (candidate == -1) return null

        return block.joinWrappedName(candidate)
    }

    /**
     * How many noise lines a banner beside the sender left above the recipient (K PLUS's THAILAND
     * 2026). All of them go when a name follows. When the first readable line is a bank, service
     * or code line instead, the noise right above it is a Thai-only name, and only that one stays.
     */
    private fun List<String>.bannerLines(): Int {
        val readable = indexOfFirst { !it.isReaderNoise() }
        if (readable <= 0) return 0
        val below = getOrNull(readable + 1)
        val isName = this[readable].readsAsName() && (below == null || !MASKED_ID_SHAPE.matches(below))
        return if (isName) readable else readable - 1
    }

    /** [wrapped] records that the name came from two lines, so the caller can say it is unsure. */
    private data class ReadName(val value: String, val wrapped: Boolean)

    /**
     * A company name that ran out of width continues on the next line — `FORTH SMART SERVICE
     * PUBLIC` then `CO.,LTD.`. Only a bare legal suffix counts as that continuation: the line
     * under a merchant is far more often a second, complete name (`SAHA LAWSON CO.,LTD.` under
     * `LAWSON108_P3793 THE LIVIN`) or the recipient's bank, and joining those glues two
     * different things into one merchant the rule engine will never match again.
     */
    private fun List<String>.joinWrappedName(start: Int): ReadName {
        val name = this[start]
        val continuation = getOrNull(start + 1) ?: return ReadName(name, wrapped = false)
        return if (LEGAL_SUFFIX.matches(continuation)) {
            ReadName("$name $continuation", wrapped = true)
        } else {
            ReadName(name, wrapped = false)
        }
    }

    /**
     * Latin letters are the test because C-3 ships no Thai model: a line the reader cannot read
     * is not a name it may guess at. A reference code carries letters too, so a bare run of
     * capitals and digits is excluded — `QN33507WI0187725Y` is not a merchant. So is the text in a
     * recipient's icon: the PromptPay logo reads as `Prompt Pay`, a shop icon as one letter.
     */
    private fun String.readsAsName(): Boolean =
        count { it in 'a'..'z' || it in 'A'..'Z' } >= MIN_NAME_LETTERS &&
            !CODE_SHAPE.matches(this) &&
            !replace(" ", "").startsWith(PROMPTPAY, ignoreCase = true)

    /**
     * FR-9: satang are counted, never divided. `1,387.50` becomes 138750, and the comma is
     * stripped before the split rather than after, so the thousands are not silently dropped.
     */
    private fun parseAmount(line: String): Satang? {
        val digits = line.substringBefore(BAHT_SUFFIX).trim().replace(",", "")
        val baht = digits.substringBefore('.').toLongOrNull() ?: return null
        val satang = when (val fraction = digits.substringAfter('.', missingDelimiterValue = "")) {
            "" -> 0L
            else -> fraction.padEnd(2, '0').take(2).toLongOrNull() ?: return null
        }
        return Satang.ofMinorUnits(baht * SATANG_PER_BAHT + satang)
    }

    /**
     * FR-19: `15 Aug 26  1:06 AM` is a Bangkok wall clock, so it is resolved in Bangkok. Read as
     * UTC it would land on the 14th and bucket into the wrong day.
     */
    private fun parseBangkokInstant(line: String, zone: TimeZone = BangkokTime): Long? {
        val match = DATE_SHAPE.matchEntire(line) ?: return null
        val (day, monthName, year, hour, minute, marker) = match.destructured

        val month = MONTHS.indexOf(monthName.lowercase()) + 1
        if (month == 0) return null

        return LocalDateTime(
            year = YEAR_2000 + year.toInt(),
            month = month,
            day = day.toInt(),
            hour = hour.toInt().toHourOfDay(marker),
            minute = minute.toInt(),
        ).toInstant(zone).toEpochMilliseconds()
    }

    /** 12 AM is midnight and 12 PM is noon — the two the naive `+12` gets backwards. */
    private fun Int.toHourOfDay(marker: String): Int = when {
        marker.equals("AM", ignoreCase = true) -> if (this == NOON) 0 else this
        else -> if (this == NOON) NOON else this + NOON
    }

    private const val REFERENCE_LABEL = "Transaction ID:"
    private const val AMOUNT_LABEL = "Amount:"

    /** `Payment Completed` or `Transfer Completed`: the header names the rail, so only the end is shared. */
    private const val HEADER_SUFFIX = "Completed"
    private const val BAHT_SUFFIX = "Baht"
    private const val SATANG_PER_BAHT = 100L
    private const val YEAR_2000 = 2000
    private const val NOON = 12

    /** The date sits directly under the header; searching further would find nothing but codes. */
    private const val HEADER_SEARCH_DEPTH = 4
    private const val NAME_CANDIDATE_DEPTH = 2

    /** The QR caption, the id, and the caption's second line: as far below the label as the id sits. */
    private const val REFERENCE_SEARCH_DEPTH = 3

    /** A shop icon reads as a single letter; no payee name is that short. */
    private const val MIN_NAME_LETTERS = 2
    private const val PROMPTPAY = "PromptPay"

    private val MONTHS = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
    )

    private val DATE_SHAPE =
        Regex("""(\d{1,2})\s+([A-Za-z]{3})\s+(\d{2})\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm])""")

    /**
     * An account, PromptPay phone or national id, masked or not. Loose on purpose: the reader adds
     * or drops a mask character (`XXX-X-X0415-xX`), and only the line's position matters.
     */
    private val MASKED_ID_SHAPE = Regex("""[\dxX*]+(?:-[\dxX*]+)+""")

    /** A transaction id once its spaces are gone; the caller also wants a digit, which words lack. */
    private val REFERENCE_SHAPE = Regex("""[A-Za-z0-9]{8,}""")

    /** A bare run of capitals and digits with no spaces — a reference, never a merchant. */
    private val CODE_SHAPE = Regex("""[A-Z0-9._\-]*\d[A-Z0-9._\-]*""")

    /** Only these join to the line above. Anything else under a name is a different thing. */
    private val LEGAL_SUFFIX =
        Regex("""(co\.,?\s*ltd\.?|company\s+limited|limited|ltd\.?|pcl\.?|inc\.?)""", RegexOption.IGNORE_CASE)
}

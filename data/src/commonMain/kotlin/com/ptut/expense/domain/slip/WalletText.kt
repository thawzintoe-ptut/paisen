package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant

/**
 * #33: what the Myanmar wallet readers share. Their receipts print kyat with no coins in use, so an
 * amount keeps whole kyat ("29,500.00 Ks" is 29500), and their clocks are Myanmar's, UTC+6:30.
 */
internal object WalletText {

    val Myanmar: TimeZone = UtcOffset(hours = 6, minutes = 30).asTimeZone()

    const val KYAT = "MMK"

    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )

    /** "29,500.00" or "1,000" to whole kyat; anything after the point is dropped (kyat has 0 digits). */
    fun kyat(figure: String): Satang? = figure.replace(",", "").substringBefore('.').toLongOrNull()?.let(Satang::ofMinorUnits)

    /** The line right after the first line that is [label], ignoring case and a trailing colon or dot. */
    fun List<String>.after(label: String): String? {
        val index = indexOfFirst { it.trimEnd(':', '.').equals(label, ignoreCase = true) }
        return if (index == -1) null else getOrNull(index + 1)
    }

    fun List<String>.has(text: String): Boolean = any { it.contains(text, ignoreCase = true) }

    /** "18 April 2024 • 3:06 PM" or "23 July 2024, 10:06 AM", on Myanmar's clock. */
    fun dayMonthYearClock(line: String): Long? {
        val match = DAY_MONTH_YEAR.find(line) ?: return null
        val (day, monthName, year, hour, minute, marker) = match.destructured
        val month = MONTHS.indexOfFirst { it.startsWith(monthName.lowercase()) } + 1
        if (month == 0) return null
        return at(year.toInt(), month, day.toInt(), clock(hour.toInt(), marker), minute.toInt())
    }

    /** "14/06/2025 12:10:15", day first, on Myanmar's clock. */
    fun slashedDateTime(line: String): Long? {
        val match = SLASHED.find(line) ?: return null
        val (day, month, year, hour, minute) = match.destructured
        return at(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
    }

    /** A name: two letters at least and not a run of digits and mask characters (a phone). */
    fun String.readsAsPayee(): Boolean = count { it.isLetter() } >= 2 && !PHONE.matches(this)

    private fun clock(hour: Int, marker: String): Int = when {
        marker.equals("AM", ignoreCase = true) -> if (hour == NOON) 0 else hour
        else -> if (hour == NOON) NOON else hour + NOON
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long? = runCatching {
        LocalDateTime(year, month, day, hour, minute).toInstant(Myanmar).toEpochMilliseconds()
    }.getOrNull()

    private const val NOON = 12
    private val DAY_MONTH_YEAR = Regex("""(\d{1,2})\s+([A-Za-z]{3,})\s+(\d{4})\D{0,4}?(\d{1,2}):(\d{2})\s*([AaPp][Mm])""")
    private val SLASHED = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})\s+(\d{1,2}):(\d{2})""")
    private val PHONE = Regex("""[\dxX*\s+-]{4,}""")
}

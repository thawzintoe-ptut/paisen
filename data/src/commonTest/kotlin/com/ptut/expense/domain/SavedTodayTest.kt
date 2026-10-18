package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedTodayTest {

    private val today = LocalDate(2026, 10, 4)

    @Test
    fun aRowFiledTodayForAnEarlierDayIsSavedToday() {
        val confirmed = row("confirmed", occurred = at(2026, 8, 19, 19, 20), filed = at(2026, 10, 4, 14, 58))

        assertEquals(listOf("confirmed"), listOf(confirmed).savedOn(today).map { it.id })
    }

    /** Today's list already draws it, and one row is drawn once. */
    @Test
    fun aRowDatedTodayIsLeftToTodaysList() {
        val typed = row("typed", occurred = at(2026, 10, 4, 14, 0), filed = at(2026, 10, 4, 14, 0))

        assertEquals(emptyList(), listOf(typed).savedOn(today))
    }

    @Test
    fun aRowFiledYesterdayIsNotSavedToday() {
        val older = row("older", occurred = at(2026, 8, 19, 19, 20), filed = at(2026, 10, 3, 22, 0))

        assertEquals(emptyList(), listOf(older).savedOn(today))
    }

    /** Rows written before schema 9 carry no filing time, and nothing is guessed for them. */
    @Test
    fun aRowWithNoFilingTimeIsNeverSavedToday() {
        val legacy = row("legacy", occurred = at(2026, 8, 19, 19, 20), filed = null)

        assertEquals(emptyList(), listOf(legacy).savedOn(today))
    }

    /** 00:30 on 4 Oct in Bangkok is still 3 Oct in UTC. */
    @Test
    fun aFilingJustAfterBangkokMidnightCountsForTheBangkokDay() {
        val early = row("early", occurred = at(2026, 8, 19, 19, 20), filed = at(2026, 10, 4, 0, 30))

        assertEquals(listOf("early"), listOf(early).savedOn(today).map { it.id })
    }

    @Test
    fun theNewestSaveComesFirst() {
        val first = row("first", occurred = at(2026, 8, 18, 19, 50), filed = at(2026, 10, 4, 9, 0))
        val second = row("second", occurred = at(2026, 8, 19, 19, 20), filed = at(2026, 10, 4, 15, 0))

        assertEquals(listOf("second", "first"), listOf(first, second).savedOn(today).map { it.id })
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(BangkokTime).toEpochMilliseconds()

    private fun row(id: String, occurred: Long, filed: Long?) = Transaction(
        id = id,
        occurredAtEpochMillis = occurred,
        amount = Satang(15_500),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "LAWSON108_P3793 THE LIVIN",
        source = TransactionSource.Scan,
        filedAt = filed,
    )
}

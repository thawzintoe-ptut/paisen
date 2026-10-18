package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class DayBucketTest {

    private val bangkok = TimeZone.of("Asia/Bangkok")

    @Test
    fun bucketsByBangkokMidnightNotUtcMidnight() {
        val lateOnThe14th = at(2026, 8, 14, hour = 23, minute = 30)
        val justAfterMidnight = at(2026, 8, 15, hour = 0, minute = 30)

        val buckets = listOf(
            transaction("a", lateOnThe14th),
            transaction("b", justAfterMidnight),
        ).bucketByDay(bangkok)

        assertEquals(
            listOf(LocalDate(2026, 8, 14), LocalDate(2026, 8, 15)),
            buckets.map { it.date },
        )
    }

    /** The Ledger puts today at the top; the week chart runs Monday forward. Same grouping. */
    @Test
    fun newestFirstReversesTheDaysWithoutRegroupingThem() {
        val rows = listOf(
            transaction("a", at(2026, 8, 14, hour = 23, minute = 30)),
            transaction("b", at(2026, 8, 15, hour = 0, minute = 30)),
        )

        assertEquals(
            rows.bucketByDay(bangkok).map { it.date }.reversed(),
            rows.bucketByDay(bangkok, newestFirst = true).map { it.date },
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(bangkok).toEpochMilliseconds()

    private fun transaction(id: String, epochMillis: Long) = Transaction(
        id = id,
        occurredAtEpochMillis = epochMillis,
        amount = Satang(10_000),
        direction = Direction.Expense,
        categoryId = null,
        counterparty = null,
    )
}

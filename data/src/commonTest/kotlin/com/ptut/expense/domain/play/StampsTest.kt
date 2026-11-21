package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class StampsTest {

    private val wednesday = LocalDate(2026, 11, 25)

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(2026, 11, 25, hour, minute)

    @Test
    fun `a daytime clear earns its weekday`() {
        assertEquals(StampKind.Wednesday, stampFor(at(12), slipsFiledThatDay = 2))
        assertEquals(StampKind.Sunday, stampFor(LocalDateTime(2026, 11, 29, 9, 0), slipsFiledThatDay = 0))
    }

    @Test
    fun `21 59 is a weekday and 22 00 is a Night owl`() {
        assertEquals(StampKind.Wednesday, stampFor(at(21, 59), slipsFiledThatDay = 1))
        assertEquals(StampKind.NightOwl, stampFor(at(22), slipsFiledThatDay = 1))
    }

    @Test
    fun `07 59 is an Early bird and 08 00 is a weekday`() {
        assertEquals(StampKind.EarlyBird, stampFor(at(7, 59), slipsFiledThatDay = 1))
        assertEquals(StampKind.Wednesday, stampFor(at(8), slipsFiledThatDay = 1))
    }

    @Test
    fun `ten slips in a day is a Big sweep whatever the hour`() {
        assertEquals(StampKind.BigSweep, stampFor(at(23), slipsFiledThatDay = 10))
        assertEquals(StampKind.NightOwl, stampFor(at(23), slipsFiledThatDay = 9))
    }

    @Test
    fun `a slip counts on the day it was filed`() {
        val filedToday = row("a", paidOn = LocalDate(2026, 11, 20), filedOn = wednesday)
        val filedBefore = row("b", paidOn = wednesday, filedOn = LocalDate(2026, 11, 24))
        val typed = row("c", paidOn = wednesday, filedOn = null)

        assertEquals(1, listOf(filedToday, filedBefore, typed).slipsFiledOn(wednesday, BangkokTime))
    }

    @Test
    fun `set progress counts each kind once`() {
        val stamps = listOf(
            stamp(StampKind.Monday, "1"),
            stamp(StampKind.Monday, "2"),
            stamp(StampKind.NightOwl, "3"),
        )

        assertEquals(
            listOf(SetProgress(StampSet.Everyday, 1, 7), SetProgress(StampSet.Night, 1, 3), SetProgress(StampSet.Wins, 0, 4)),
            stamps.setProgress(),
        )
    }

    @Test
    fun `no-spend days skip spending days days before the ledger and days to come`() {
        val rows = listOf(
            row("a", paidOn = LocalDate(2026, 11, 3), filedOn = null),
            row("b", paidOn = LocalDate(2026, 11, 5), filedOn = null),
            row("in", paidOn = LocalDate(2026, 11, 6), filedOn = null, direction = Direction.Income),
        )

        val days = rows.noSpendDays("2026-11", today = LocalDate(2026, 11, 7), zone = BangkokTime)

        assertEquals(listOf(4, 6, 7).map { LocalDate(2026, 11, it) }, days)
    }

    private fun stamp(kind: StampKind, ref: String) = Stamp(stampId(kind, ref), kind, ref, wednesday)

    private fun row(id: String, paidOn: LocalDate, filedOn: LocalDate?, direction: Direction = Direction.Expense) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(paidOn.year, paidOn.month, paidOn.day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(5_000),
        direction = direction,
        categoryId = "food",
        counterparty = "Cafe",
        filedAt = filedOn?.let { LocalDateTime(it.year, it.month, it.day, 13, 0).toInstant(BangkokTime).toEpochMilliseconds() },
    )
}

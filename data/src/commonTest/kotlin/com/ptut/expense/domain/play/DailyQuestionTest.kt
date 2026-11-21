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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyQuestionTest {

    private val today = LocalDate(2026, 11, 25)

    @Test
    fun `the closest two categories of the last seven days are asked`() {
        val rows = listOf(
            row("1", "food", 120_000, day = 20),
            row("2", "transport", 110_000, day = 21),
            row("3", "home", 400_000, day = 22),
        )

        val pair = rows.pickQuestion(today, emptySet(), BangkokTime)

        assertEquals(questionKey("cat:food", "cat:transport"), pair?.key)
        assertTrue(assertNotNull(pair).aCostMore)
    }

    @Test
    fun `a pair asked in the last 30 days is not asked again`() {
        val rows = listOf(row("1", "food", 120_000, 20), row("2", "transport", 110_000, 21), row("3", "home", 100_000, 22))

        val pair = rows.pickQuestion(today, setOf(questionKey("cat:food", "cat:transport")), BangkokTime)

        assertEquals(questionKey("cat:home", "cat:transport"), pair?.key)
    }

    @Test
    fun `no close pair no tie and nothing spent ask nothing`() {
        assertNull(listOf(row("1", "food", 100_000, 20), row("2", "transport", 300_000, 21)).pickQuestion(today, emptySet(), BangkokTime))
        assertNull(listOf(row("1", "food", 100_000, 20, shop = "A"), row("2", "transport", 100_000, 21, shop = "B")).pickQuestion(today, emptySet(), BangkokTime))
        assertNull(emptyList<Transaction>().pickQuestion(today, emptySet(), BangkokTime))
    }

    @Test
    fun `today and earlier weeks are left out`() {
        val rows = listOf(row("1", "food", 120_000, 25), row("2", "transport", 110_000, 10), row("3", "home", 115_000, 24))

        assertNull(rows.pickQuestion(today, emptySet(), BangkokTime))
    }

    @Test
    fun `shops are asked when no categories are close`() {
        val rows = listOf(row("1", "food", 120_000, 20, shop = "Lawson"), row("2", "food", 110_000, 21, shop = "7-Eleven"))

        assertEquals(questionKey("shop:7-Eleven", "shop:Lawson"), rows.pickQuestion(today, emptySet(), BangkokTime)?.key)
    }

    @Test
    fun `an answered key rebuilds the same two cards`() {
        val rows = listOf(row("1", "food", 120_000, 20), row("2", "transport", 110_000, 21))

        val pair = rows.questionFor(questionKey("cat:food", "cat:transport"), today, BangkokTime)

        assertEquals(Satang(120_000), pair?.a?.amount)
        assertEquals(Satang(110_000), pair?.b?.amount)
    }

    @Test
    fun `asked keys reach back 30 days`() {
        val days = listOf(
            PlayDay(LocalDate(2026, 10, 26), questionKey = "old"),
            PlayDay(LocalDate(2026, 10, 27), questionKey = "kept"),
        )

        assertEquals(setOf("old", "kept"), days.askedKeys(today))
        assertEquals(setOf("kept"), days.askedKeys(LocalDate(2026, 11, 26)))
    }

    @Test
    fun `a shop whose name holds a bar still rebuilds its cards`() {
        val rows = listOf(row("1", "food", 120_000, 20, shop = "A|B"), row("2", "food", 110_000, 21, shop = "Cafe"))
        val pair = assertNotNull(rows.pickQuestion(today, emptySet(), BangkokTime))

        assertEquals(pair, rows.questionFor(pair.key, today, BangkokTime))
    }

    private fun row(id: String, category: String, minor: Long, day: Int, shop: String? = null) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(2026, 11, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(minor),
        direction = Direction.Expense,
        categoryId = category,
        counterparty = shop,
    )
}

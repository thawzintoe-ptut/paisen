package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyScoreTest {

    private val today = LocalDate(2026, 11, 28)

    @Test
    fun `keeping more than was spent gives that part in full`() {
        val rows = listOf(row("in", today, 100_000, category = "income", direction = Direction.Income), row("out", today, 50_000))

        val kept = moneyScore(rows, emptyList(), today, BangkokTime).parts.single { it.kind == ScorePartKind.KeptMoreThanSpent }

        assertEquals(ScorePart(ScorePartKind.KeptMoreThanSpent, 40, 40, 0), kept)
    }

    @Test
    fun `a part with no data is left out and the score scales to the rest`() {
        val days = (0 until 15).map { PlayDay(today.minus(it, DateTimeUnit.DAY), clearedAtEpochMillis = 1L) }

        val score = moneyScore(emptyList(), days, today, BangkokTime)

        assertEquals(listOf(ScorePartKind.SlipsChecked), score.parts.map { it.kind })
        assertEquals(50, score.total)
        assertEquals(15, score.parts.single().gain)
    }

    @Test
    fun `steady weeks count those near the median week`() {
        val rows = (1..4).map { row("w$it", today.minus(it, DateTimeUnit.WEEK), if (it == 4) 300_000 else 100_000) }

        val steady = moneyScore(rows, emptyList(), today, BangkokTime).parts.single { it.kind == ScorePartKind.SteadyWeeks }

        assertEquals(22, steady.points)
    }

    @Test
    fun `nothing at all scores nothing`() {
        assertEquals(MoneyScore(0, emptyList()), moneyScore(emptyList(), emptyList(), today, BangkokTime))
    }
}

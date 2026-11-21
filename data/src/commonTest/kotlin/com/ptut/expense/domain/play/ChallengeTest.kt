package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeTest {

    /** The week of Monday 23 Nov, with 1,000 of food in each of the two weeks before it. */
    private val monday = LocalDate(2026, 11, 23)
    private val earlier = listOf(row("a", LocalDate(2026, 11, 10), 100_000), row("b", LocalDate(2026, 11, 17), 100_000))

    @Test
    fun `each level asks for less than a usual week rounded down to ten`() {
        assertEquals(
            mapOf(ChallengeLevel.Easy to Satang(95_000), ChallengeLevel.Medium to Satang(85_000), ChallengeLevel.Hard to Satang(70_000)),
            earlier.challengeLevels("food", monday, BangkokTime),
        )
    }

    @Test
    fun `too little history or no spending in the category offers no challenge`() {
        assertNull(listOf(row("a", LocalDate(2026, 11, 17), 100_000)).challengeLevels("food", monday, BangkokTime))
        assertNull(earlier.challengeLevels("transport", monday, BangkokTime))
    }

    @Test
    fun `progress counts the week so far and the days left`() {
        val challenge = Challenge("food", ChallengeLevel.Medium, Satang(85_000))
        val rows = earlier + row("c", LocalDate(2026, 11, 24), 30_000) + row("d", LocalDate(2026, 11, 27), 90_000)

        val progress = rows.challengeProgress(challenge, monday, today = LocalDate(2026, 11, 25), zone = BangkokTime)

        assertEquals(Satang(30_000), progress.used)
        assertEquals(5, progress.daysLeft)
        assertTrue(progress.under)
    }

    @Test
    fun `a week at or under the limit is met over it is not`() {
        val challenge = Challenge("food", ChallengeLevel.Easy, Satang(95_000))

        assertTrue((earlier + row("c", LocalDate(2026, 11, 24), 95_000)).challengeMet(challenge, monday, BangkokTime))
        assertFalse((earlier + row("c", LocalDate(2026, 11, 24), 95_001)).challengeMet(challenge, monday, BangkokTime))
    }

    @Test
    fun `the choices are the categories with the most usual spending`() {
        val rows = earlier + row("t", LocalDate(2026, 11, 10), 20_000, category = "transport") + row("t2", LocalDate(2026, 11, 17), 20_000, category = "transport")

        assertEquals(listOf("food", "transport"), rows.challengeCategories(monday, BangkokTime))
    }

    @Test
    fun `a usual week too small to round offers no challenge`() {
        val small = listOf(row("a", LocalDate(2026, 11, 10), 500), row("b", LocalDate(2026, 11, 17), 500))

        assertNull(small.challengeLevels("food", monday, BangkokTime))
        assertEquals(emptyList(), small.challengeCategories(monday, BangkokTime))
    }
}

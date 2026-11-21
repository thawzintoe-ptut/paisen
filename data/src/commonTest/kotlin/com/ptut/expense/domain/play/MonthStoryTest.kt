package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthStoryTest {

    private val october = listOf(row("o1", LocalDate(2026, 10, 5), 100_000, shop = "Lawson"), row("o2", LocalDate(2026, 10, 6), 50_000, category = "transport"))
    private val november = listOf(
        row("n1", LocalDate(2026, 11, 3), 100_000, shop = "Lawson"),
        row("n2", LocalDate(2026, 11, 4), 150_000, category = "transport"),
        row("n3", LocalDate(2026, 11, 20), 40_000, shop = "New Cafe"),
    )

    @Test
    fun `the story leads with the total against a usual month and names the most unusual category`() {
        val story = (october + november).monthStory("2026-11", BangkokTime)

        assertEquals(StoryCard.Total(Satang(290_000), Satang(140_000)), story[0])
        assertEquals(StoryCard.BiggestShift("transport", Satang(100_000)), story[1])
        assertEquals(StoryCard.NewShop("New Cafe", Satang(40_000)), story[2])
    }

    @Test
    fun `the quietest week starts inside the month and no-spend days are counted`() {
        val story = november.monthStory("2026-11", BangkokTime)

        assertTrue(story.any { it is StoryCard.QuietestWeek && it.monday >= LocalDate(2026, 11, 1) })
        assertTrue(story.any { it is StoryCard.NoSpendDays && it.count > 0 })
    }

    @Test
    fun `a month with nothing spent tells no story`() {
        assertEquals(emptyList(), october.monthStory("2026-11", BangkokTime))
    }
}

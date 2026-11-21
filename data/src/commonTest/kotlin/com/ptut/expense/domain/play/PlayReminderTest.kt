package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayReminderTest {

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 11, day, hour, minute).toInstant(BangkokTime)

    @Test
    fun `any day of the week points to the next Monday at nine`() {
        (24..29).forEach { day ->
            assertEquals(LocalDateTime(2026, 11, 30, 9, 0), nextPlayReminderAt(at(day, 12), BangkokTime).toLocalDateTime(BangkokTime), "the ${day}th")
        }
    }

    @Test
    fun `a Monday before nine is that morning and from nine on it is next week`() {
        assertEquals(LocalDateTime(2026, 11, 23, 9, 0), nextPlayReminderAt(at(23, 8, 59), BangkokTime).toLocalDateTime(BangkokTime))
        assertEquals(LocalDateTime(2026, 11, 30, 9, 0), nextPlayReminderAt(at(23, 9), BangkokTime).toLocalDateTime(BangkokTime))
    }

    @Test
    fun `a Monday reminder asks about the week that just ended`() {
        assertEquals(LocalDate(2026, 11, 23), guessWeekFor(LocalDate(2026, 11, 30)))
    }
}

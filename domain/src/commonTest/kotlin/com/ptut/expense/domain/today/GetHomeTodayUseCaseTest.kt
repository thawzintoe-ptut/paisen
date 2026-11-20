package com.ptut.expense.domain.today

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class GetHomeTodayUseCaseTest {

    @AfterTest
    fun backToBaht() = Home.use(HomeChoice.Default)

    @Test
    fun aBahtLedgerTurnsTheDayAtBangkokMidnight() {
        val today = GetHomeTodayUseCase(clockAt("2026-11-19T17:15:00Z"))

        assertEquals(LocalDate(2026, 11, 20), today())
    }

    @Test
    fun aKyatLedgerIsStillOnTheDayBeforeAtTheSameInstant() {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))

        val today = GetHomeTodayUseCase(clockAt("2026-11-19T17:15:00Z"))

        assertEquals(LocalDate(2026, 11, 19), today())
    }

    @Test
    fun aLaterInstantGivesTheNextHomeDay() {
        val clock = MovableClock(Instant.parse("2026-11-19T16:59:00Z"))
        val today = GetHomeTodayUseCase(clock)
        val before = today()

        clock.at = Instant.parse("2026-11-19T17:01:00Z")

        assertEquals(LocalDate(2026, 11, 19), before)
        assertEquals(LocalDate(2026, 11, 20), today())
    }

    private fun clockAt(iso: String): Clock = MovableClock(Instant.parse(iso))

    private class MovableClock(var at: Instant) : Clock {
        override fun now(): Instant = at
    }
}

package com.ptut.expense.domain.play

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class SettleChallengeUseCaseTest {

    /** Wednesday 9 Dec: the app was last opened in the week of 23 Nov. */
    private val today = LocalDate(2026, 12, 9)
    private val nov23 = LocalDate(2026, 11, 23)
    private val nov30 = LocalDate(2026, 11, 30)

    private fun settle(book: FakePlayBook) = SettleChallengeUseCase(
        book = book,
        transactions = ObserveTransactionsUseCase(FakeLedger()),
        homeToday = GetHomeTodayUseCase(
            object : Clock {
                override fun now(): Instant = today.atStartOfDayIn(HomeTime)
            },
        ),
        playOn = ObservePlayOnUseCase(ObserveAppChoicesUseCase(appSettings()), PlayGate(flagOn = true, debug = true)),
    )

    private fun week(monday: LocalDate) = PlayWeek(monday, challenge = Challenge("food", ChallengeLevel.Easy, Satang(100_000)))

    @Test
    fun `a challenge met in a week the app never opened after still earns its stamp`() = runTest {
        val book = FakePlayBook(weeks = listOf(week(nov23)))

        settle(book)()

        assertEquals(listOf(nov23.toString()), book.stamps.value.filter { it.kind == StampKind.ChallengeMet }.map { it.ref })
    }

    @Test
    fun `every past week is settled once and this week waits`() = runTest {
        val thisMonday = LocalDate(2026, 12, 7)
        val book = FakePlayBook(weeks = listOf(week(nov23), week(nov30), week(thisMonday)))

        settle(book)()
        settle(book)()

        assertEquals(listOf(nov23.toString(), nov30.toString()), book.stamps.value.filter { it.kind == StampKind.ChallengeMet }.map { it.ref }.sorted())
    }
}

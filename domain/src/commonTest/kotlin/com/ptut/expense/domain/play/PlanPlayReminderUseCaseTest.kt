package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class PlanPlayReminderUseCaseTest {

    /** Wednesday 25 Nov; the reminder is Monday 30 Nov, asking about the week of 23 Nov. */
    private val now = LocalDateTime(2026, 11, 25, 12, 0).toInstant(BangkokTime)
    private val earlier = listOf(spend(10), spend(17))

    private class Held : RepeatReminders {
        var plans: List<ReminderPlan>? = null

        override suspend fun replaceAll(plans: List<ReminderPlan>) {
            this.plans = plans
        }

        override fun allowed(): Boolean = true
    }

    private fun plan(held: Held, play: PlayChoices, book: FakePlayBook = FakePlayBook(), rows: List<Transaction> = earlier) = PlanPlayReminderUseCase(
        reminders = held,
        playOn = ObservePlayOnUseCase(ObserveAppChoicesUseCase(appSettings(AppChoices(play = play))), PlayGate(flagOn = true, debug = true)),
        transactions = ObserveTransactionsUseCase(FakeLedger(rows)),
        book = book,
        words = object : PlayReminderWords {
            override val title = "PaiSeN"
            override val body = "Your week is ready. Guess first?"
        },
        clock = object : Clock {
            override fun now(): Instant = now
        },
    )

    @Test
    fun `with the switch on and a guess waiting one reminder goes on Monday at nine`() = runTest {
        val held = Held()

        plan(held, PlayChoices(weeklyReminder = true))()

        val reminder = held.plans.orEmpty().single()
        assertEquals("weekly-2026-11-23", reminder.id)
        assertEquals(LocalDateTime(2026, 11, 30, 9, 0).toInstant(BangkokTime).toEpochMilliseconds(), reminder.atEpochMillis)
    }

    @Test
    fun `the switch off too little data or a week already guessed clears it`() = runTest {
        val off = Held().also { plan(it, PlayChoices())() }
        val thin = Held().also { plan(it, PlayChoices(weeklyReminder = true), rows = listOf(spend(17)))() }
        val played = Held().also {
            plan(it, PlayChoices(weeklyReminder = true), FakePlayBook(weeks = listOf(PlayWeek(LocalDate(2026, 11, 23), guess = Satang(1)))))()
        }

        listOf(off, thin, played).forEach { assertTrue(it.plans.orEmpty().isEmpty()) }
    }

    private fun spend(day: Int) = Transaction(
        id = "t$day",
        occurredAtEpochMillis = LocalDateTime(2026, 11, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(100_000),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = null,
    )
}

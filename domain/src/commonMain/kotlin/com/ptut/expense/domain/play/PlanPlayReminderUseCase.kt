package com.ptut.expense.domain.play

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** The weekly reminder's words. The screens' copy writes them, so the domain holds no sentence. */
interface PlayReminderWords {
    val title: String
    val body: String
}

/**
 * #83 (D15): the one weekly play reminder, Monday 09:00 home time, only while its switch is on and a
 * guess will be waiting that morning. Never about a run (R7). Planned again on every resume and when
 * a switch moves; a single, so two plans at once cannot leave two reminders.
 */
class PlanPlayReminderUseCase(
    private val reminders: RepeatReminders,
    private val playOn: ObservePlayOnUseCase,
    private val transactions: ObserveTransactionsUseCase,
    private val book: PlayBook,
    private val words: PlayReminderWords,
    private val clock: Clock = Clock.System,
) {
    private val planning = Mutex()

    /** The plan last handed to the phone, so a resume that changes nothing writes nothing. */
    private var planned: List<ReminderPlan>? = null

    suspend operator fun invoke() = planning.withLock {
        val plans = listOfNotNull(plan())
        if (plans == planned) return@withLock
        reminders.replaceAll(plans)
        planned = plans
    }

    private suspend fun plan(): ReminderPlan? {
        if (!playOn.now(PlayPart.Reminder) || !playOn.now(PlayPart.Guess)) return null
        val at = nextPlayReminderAt(clock.now())
        val week = guessWeekFor(at.dateIn(HomeTime))
        val rows = transactions(LedgerSpan.All).filterIsInstance<LedgerSnapshot.Rows>().first().transactions
        val played = book.weeks.first().any { it.monday == week && it.guess != null }
        if (played || rows.paisenGuess(week) == null) return null
        return ReminderPlan(id = "weekly-$week", title = words.title, body = words.body, atEpochMillis = at.toEpochMilliseconds())
    }
}

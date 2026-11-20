package com.ptut.expense.domain.shared

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock

/** A repeat reminder's words. The screens' copy writes them, so the domain holds no sentence. */
interface ReminderWords {
    fun title(description: String): String

    /** [amount] is already in [group]'s money. */
    fun body(amount: Satang, group: SharedGroup): String
}

/**
 * #182: adds every repeat due today (Bangkok), then lines up each next reminder. One instance per app, since two runs
 * at once would add the same repeat twice: start, resume, sync and Android's background run share its lock.
 */
class RunDueRepeatsUseCase(
    private val repository: SharedRepository,
    private val reminders: RepeatReminders,
    private val words: ReminderWords,
    private val clock: Clock = Clock.System,
) {
    private val running = Mutex()

    suspend operator fun invoke() = running.withLock {
        val today = clock.now().dateIn(HomeTime)
        repository.addDueRepeats(today)
        if (reminders.allowed()) reminders.replaceAll(plans())
    }

    /** The next reminder of each repeat that asks for one, at 09:00 Bangkok the day before. */
    private fun plans(): List<ReminderPlan> {
        val now = clock.now()
        val today = now.dateIn(HomeTime)
        val snapshot = repository.snapshot.value
        return snapshot.repeats.filter { it.active && it.remind }.mapNotNull { rule ->
            val group = snapshot.group(rule.groupId) ?: return@mapNotNull null
            val at = rule.next(today).map { it.dayBefore().atTime(REMIND_HOUR, 0).toInstant(HomeTime) }.firstOrNull { it > now }
                ?: return@mapNotNull null
            ReminderPlan(
                id = rule.id,
                title = words.title(rule.description),
                body = words.body(convert(rule.amountMinor, Currencies.of(rule.currency), rule.rate, group.money), group),
                atEpochMillis = at.toEpochMilliseconds(),
            )
        }
    }

    private companion object {
        /** The PDF names no hour; decided 13 Oct in #182: 09:00 Bangkok. */
        const val REMIND_HOUR = 9
    }
}

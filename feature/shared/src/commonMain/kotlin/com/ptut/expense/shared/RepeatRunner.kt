package com.ptut.expense.shared

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.dayBefore
import com.ptut.expense.domain.shared.next
import com.ptut.expense.domain.shared.toSatang
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * #182: adds every repeat due today (Bangkok), then lines up each one's next reminder. Runs when
 * the app starts or comes back, after sync pulls, and in Android's background run.
 */
class RepeatRunner(
    private val repository: SharedRepository,
    private val reminders: RepeatReminders,
    private val clock: Clock = Clock.System,
) {
    private val running = Mutex()

    suspend fun run() = running.withLock {
        val today = clock.now().toLocalDateTime(BangkokTime).date
        repository.addDueRepeats(today)
        if (reminders.allowed()) reminders.replaceAll(plans())
    }

    /** The next reminder of each repeat that asks for one, at 09:00 Bangkok the day before. */
    private fun plans(): List<ReminderPlan> {
        val now = clock.now()
        val today = now.toLocalDateTime(BangkokTime).date
        val snapshot = repository.snapshot.value
        val text = AppText.copy.shared.repeat
        return snapshot.repeats.filter { it.active && it.remind }.mapNotNull { rule ->
            val group = snapshot.group(rule.groupId) ?: return@mapNotNull null
            val at = rule.next(today).map { it.dayBefore().atTime(REMIND_HOUR, 0).toInstant(BangkokTime) }.firstOrNull { it > now }
                ?: return@mapNotNull null
            ReminderPlan(
                id = rule.id,
                title = text.reminderTitle(rule.description),
                body = text.reminderBody(rule.amount(), group.name),
                atEpochMillis = at.toEpochMilliseconds(),
            )
        }
    }

    private fun RepeatRule.amount(): String = toSatang(amountMinor, Currencies.of(currency), rate).formatCompact()

    private companion object {
        /** The PDF names no hour; decided 13 Oct in #182: 09:00 Bangkok. */
        const val REMIND_HOUR = 9
    }
}

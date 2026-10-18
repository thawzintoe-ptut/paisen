package com.ptut.expense.platform.notify

import androidx.compose.runtime.Composable
import com.ptut.expense.data.local.PtutDatabaseContext

/** #182: one reminder, the day before a repeat adds itself. */
data class ReminderPlan(val id: String, val title: String, val body: String, val atEpochMillis: Long)

/**
 * #182: local notifications only, no network. The plans are rebuilt from the repeats each time
 * they change or the app comes back, so a stopped repeat's reminder simply is not scheduled again.
 */
interface RepeatReminders {
    /** Replaces every pending repeat reminder with [plans]. */
    suspend fun replaceAll(plans: List<ReminderPlan>)

    /** Whether this phone lets the app post a notification right now. */
    fun allowed(): Boolean
}

/** The app's second permission ask, after photos (#182): made on the first save of a repeat. */
fun interface NotificationAsk {
    /** True when notifications are allowed, asking first if the platform has not been asked yet. */
    suspend fun request(): Boolean
}

@Composable
expect fun rememberNotificationAsk(): NotificationAsk

expect fun repeatReminders(context: PtutDatabaseContext): RepeatReminders

package com.ptut.expense.play

import com.ptut.expense.domain.play.PlayReminderWords
import com.ptut.expense.locale.AppText

/** D15's words, in the language the app is in when the reminder is planned. */
object PlayReminderCopyWords : PlayReminderWords {
    override val title: String get() = AppText.copy.play.reminder.title
    override val body: String get() = AppText.copy.play.reminder.body
}

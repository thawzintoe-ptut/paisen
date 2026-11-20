package com.ptut.expense.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.ReminderWords
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.locale.AppText

/** #182: "Rent repeats tomorrow", in the language the app is in when the reminders are planned. */
object RepeatReminderWords : ReminderWords {
    override fun title(description: String): String = AppText.copy.shared.repeat.reminderTitle(description)

    override fun body(amount: Satang, group: SharedGroup): String =
        AppText.copy.shared.repeat.reminderBody(amount.formatCompact(group.moneyFormat()), group.name)
}

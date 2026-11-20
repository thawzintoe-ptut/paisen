package com.ptut.expense.domain.today

import com.ptut.expense.domain.DayPart
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.UsualDay
import com.ptut.expense.domain.WeeklyCheckIn
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayPartAt
import com.ptut.expense.domain.expensesOn
import com.ptut.expense.domain.lastWeek
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.spentOn
import com.ptut.expense.domain.usualDay
import com.ptut.expense.domain.weeklyCheckIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Instant

/** p07 and p08's card under the tabs (#121, #122). [day] is the day its receipt opens on. */
sealed interface MoodRecap {
    val day: LocalDate

    /** Yesterday came to [spent]; [usual] is a usual day of its weekday, or null with too little history. */
    data class Morning(override val day: LocalDate, val spent: Satang, val usual: UsualDay?) : MoodRecap

    /** Today so far: [purchases] of them, [spent] in all. */
    data class Evening(override val day: LocalDate, val purchases: Int, val spent: Satang) : MoodRecap
}

/** Morning recaps yesterday, evening wraps today, the day between has no card; none for a day with no spending. */
class GetMoodRecapUseCase {
    operator fun invoke(rows: List<Transaction>, now: Instant): MoodRecap? {
        val today = now.dateIn(HomeTime)
        return when (dayPartAt(now)) {
            DayPart.Morning -> {
                val yesterday = today.minus(1, DateTimeUnit.DAY)
                val spent = rows.spentOn(yesterday)
                MoodRecap.Morning(yesterday, spent, rows.usualDay(yesterday)).takeIf { spent > Satang.Zero }
            }

            DayPart.Evening -> {
                val purchases = rows.expensesOn(today).size
                MoodRecap.Evening(today, purchases, rows.spentOn(today)).takeIf { purchases > 0 }
            }

            DayPart.Day -> null
        }
    }
}

/** #20's look back at last week, with what #21's target and #22's note add to it. */
data class WeeklyLookBack(
    val check: WeeklyCheckIn,
    /** #22: the note speaks for this Monday: it is on and was not hidden this week. */
    val noted: Boolean,
    /** #21: last week beside the weekly target, or null with none set. */
    val target: TargetWeek?,
)

/** #20: on a Monday only, and only after a week with spending. */
class GetWeeklyCheckInUseCase {
    operator fun invoke(
        rows: List<Transaction>,
        today: LocalDate,
        target: WeeklyTarget?,
        noteOn: Boolean,
        hiddenWeek: String?,
    ): WeeklyLookBack? {
        if (today.dayOfWeek != DayOfWeek.MONDAY) return null
        val check = rows.weeklyCheckIn(today) ?: return null
        return WeeklyLookBack(
            check = check,
            // Hiding takes the note off this Monday; the card from #20 stays either way.
            noted = noteOn && hiddenWeek != today.toString(),
            target = target?.let { TargetWeek(it.categoryId, rows.lastWeek(it, today)) },
        )
    }
}

/** #22: "Just hide this week". The note shows on a Monday only, so today is the Monday it hides. */
class HideWeeklyNoteUseCase(
    private val settings: AppSettings? = null,
    private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase(),
) {
    operator fun invoke() {
        settings?.hideNoteWeek(homeToday().toString())
    }
}

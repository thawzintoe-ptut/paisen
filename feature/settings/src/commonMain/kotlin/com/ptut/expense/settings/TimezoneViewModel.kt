package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.dayLabel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class TimezoneUiState(
    val zone: String,
    /** The day the totals on Today are being counted into, named the way the Ledger names one. */
    val today: String,
    /** The boundary as two dates rather than as a rule, so it can be checked against a calendar. */
    val window: String,
)

/**
 * FR-19: a day is a Bangkok day, and it is not the traveller's day.
 *
 * There is no picker and there is no device zone on this screen. Every total in the app is bucketed
 * against [BangkokTime], so offering a choice would mean re-bucketing history — and reading the
 * phone's own zone is the exact call `VerifySourceRules` refuses in source.
 *
 * A state holder rather than a screen of constants because one line is not constant: which day it
 * is now is a clock reading, and the screen exists to show that boundary against a real date.
 */
class TimezoneViewModel(clock: Clock = Clock.System) : ViewModel() {

    val uiState: TimezoneUiState = clock.now().toLocalDateTime(BangkokTime).date.let { today ->
        val tomorrow = today.plus(DatePeriod(days = 1))
        TimezoneUiState(
            zone = "Asia/Bangkok",
            today = today.dayLabel(),
            window = "${today.dayAndMonth()} 00:00 → ${tomorrow.dayAndMonth()} 00:00",
        )
    }
}

package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.play.ObserveStampBookUseCase
import com.ptut.expense.domain.play.StampBook
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** One cell of p04's month: null [day] is a blank before the 1st. Nothing here is ever red (R7). */
data class CalendarDayUiModel(val day: String?, val noSpend: Boolean, val today: Boolean, val label: String)

data class StampSetUiModel(val name: String, val line: String, val count: String, val stamps: List<StampUiModel>)

data class StampBookUiState(
    val title: String,
    val noSpendTitle: String,
    val noSpendLine: String,
    val weekLetters: List<String>,
    val calendar: List<CalendarDayUiModel>,
    val nextSetTitle: String,
    /** The first set not yet complete, or null once every set is. */
    val nextSet: SetProgressUiModel?,
    val sets: List<StampSetUiModel>,
)

/** #88, p04: this month's no-spend days, the next set to finish, and every set with its empty slots. */
class StampBookViewModel(observeStampBook: ObserveStampBookUseCase) : ViewModel() {

    val uiState: StateFlow<StampBookUiState?> = observeStampBook()
        .map { it.asUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)
}

private fun StampBook.asUiState(): StampBookUiState {
    val text = AppText.copy.play.stamps
    val next = sets.firstOrNull { !it.complete }
    return StampBookUiState(
        title = text.bookTitle,
        noSpendTitle = text.noSpendTitle,
        noSpendLine = text.noSpendCount(noSpendDays.size),
        weekLetters = AppText.copy.play.run.weekLetters,
        calendar = calendar(),
        nextSetTitle = next?.let { text.nextSet(it.set.label(text)) } ?: text.allSets,
        nextSet = next?.asUiModel(text),
        sets = sets.map { progress ->
            StampSetUiModel(
                name = progress.set.label(text),
                line = progress.asUiModel(text).line,
                count = progress.asUiModel(text).count,
                // The latest of each kind, so a kind earned twice shows once.
                stamps = StampKind.entries.filter { it.set == progress.set }.map { kind ->
                    stamps.lastOrNull { it.kind == kind }?.asBookUiModel(text) ?: kind.asEmptySlot(text)
                },
            )
        },
    )
}

/** Monday-first cells for the month of [StampBook.today], blanks before the 1st. */
private fun StampBook.calendar(): List<CalendarDayUiModel> {
    val text = AppText.copy.play.stamps
    val yearMonth = YearMonth(today.year, today.month)
    val first = yearMonth.firstDay
    val blanks = List(first.dayOfWeek.isoDayNumber - 1) { CalendarDayUiModel(day = null, noSpend = false, today = false, label = "") }
    val days = generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= yearMonth.lastDay }.map { day ->
        val noSpend = day in noSpendDays
        CalendarDayUiModel(
            day = day.day.toString(),
            noSpend = noSpend,
            today = day == today,
            label = if (noSpend) text.noSpendLabel(day.dayLabel()) else day.dayLabel(),
        )
    }
    return blanks + days.toList()
}

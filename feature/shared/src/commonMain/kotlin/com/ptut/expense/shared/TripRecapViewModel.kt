package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.format
import com.ptut.expense.domain.play.GetTripRecapUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.domain.weekdayAndDay
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** One day's bar: its share of the busiest day, and what a screen reader says. */
data class RecapBarUiModel(val fraction: Float, val label: String)

sealed interface TripRecapUiState {
    data object Missing : TripRecapUiState

    data class None(val title: String, val line: String) : TripRecapUiState

    data class Loaded(
        val title: String,
        val perPersonPerDay: String,
        val perPersonLabel: String,
        val line: String,
        val total: String,
        val bars: List<RecapBarUiModel>,
        val nextTripTitle: String,
        val nextTripAmount: String,
        val nextTrip: String,
        /** Null for a trip in other money: a jar fills from the ledger, which counts in the home money. */
        val startJar: String?,
        /** #92: the jar a "Start a jar" opens, named for the trip and filled with the estimate ("1200.00"). */
        val jarName: String,
        val jarTarget: String,
        val share: String,
        /** FR-97.4: the share card hides amounts unless Settings › Play shows them. */
        val shareAmounts: Boolean,
    ) : TripRecapUiState
}

/** #97 (p13): the trip's cost per person per day as the headline, a bar per day, and a trip like it ahead. */
class TripRecapViewModel(
    private val groupId: String,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val getRecap: GetTripRecapUseCase,
    shareAmounts: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    val uiState: StateFlow<TripRecapUiState> = combine(observeSnapshot(), shareAmounts) { snapshot, amounts ->
        val group = snapshot.group(groupId) ?: return@combine TripRecapUiState.Missing
        val text = AppText.copy.play.group
        val (recap, next) = getRecap(snapshot, groupId) ?: return@combine TripRecapUiState.None(text.recapTitle, text.recapNone)
        val money = group.moneyFormat()
        val busiest = recap.byDay.maxOf { it.second }
        TripRecapUiState.Loaded(
            title = group.name,
            perPersonPerDay = recap.perPersonPerDay.format(money),
            perPersonLabel = text.perPersonPerDay,
            line = text.recapLine(recap.days, recap.people),
            total = text.recapTotal(recap.total.format(money)),
            bars = recap.byDay.map { (day, amount) -> RecapBarUiModel(amount.ratioTo(busiest), text.dayBar(day.weekdayAndDay(), amount.format(money))) },
            nextTripTitle = text.nextTripTitle,
            nextTripAmount = next.estimate.format(money),
            nextTrip = text.nextTrip(next.estimate.format(money), next.perWeek.format(money), next.weeks),
            startJar = text.startJar.takeIf { group.currency == Home.money.code },
            jarName = group.name,
            jarTarget = group.money.typedText(next.estimate.toMinorUnits()),
            share = text.share,
            shareAmounts = amounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), TripRecapUiState.Missing)
}

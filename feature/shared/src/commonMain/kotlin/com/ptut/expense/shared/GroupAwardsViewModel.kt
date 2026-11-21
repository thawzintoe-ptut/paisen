package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.isoMonth
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.play.GetGroupAwardsUseCase
import com.ptut.expense.domain.shared.AwardKind
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.SyncHealth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One title, who it went to, and the number behind it (FR-96.4). */
data class AwardUiModel(val title: String, val name: String, val line: String, val icon: String)

/** [awards] is empty while the phone is behind or the month has too little; [line] says which. */
data class GroupAwardsUiState(val title: String, val awards: List<AwardUiModel>, val line: String?)

/** #96 (p12): the month's kind titles. Praise only, never who owes most (R7), and the same on every up-to-date phone. */
class GroupAwardsViewModel(
    private val groupId: String,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    private val getAwards: GetGroupAwardsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) : ViewModel() {

    val uiState: StateFlow<GroupAwardsUiState?> = combine(observeSnapshot(), observeSyncStatus(groupId)) { snapshot, status ->
        val group = snapshot.group(groupId) ?: return@combine null
        val text = AppText.copy.play.group
        val today = homeToday()
        val title = text.awardsTitle(today.month.longName())
        if (status.health !in UP_TO_DATE) return@combine GroupAwardsUiState(title, emptyList(), text.awardsBehind)
        val awards = getAwards(snapshot, groupId, today.isoMonth()).map { award ->
            AwardUiModel(
                title = text.awardNames[award.kind.name].orEmpty(),
                name = group.displayName(award.memberId),
                line = when (award.kind) {
                    AwardKind.FastestToSettle -> text.fastestLine(award.count)
                    AwardKind.KeptTheBooks -> text.booksLine(award.count)
                    AwardKind.SteadyPayer -> text.steadyLine(award.count)
                },
                icon = when (award.kind) {
                    AwardKind.FastestToSettle -> "bolt"
                    AwardKind.KeptTheBooks -> "menu_book"
                    AwardKind.SteadyPayer -> "calendar_month"
                },
            )
        }
        GroupAwardsUiState(title, awards, text.awardsNone.takeIf { awards.isEmpty() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)
}

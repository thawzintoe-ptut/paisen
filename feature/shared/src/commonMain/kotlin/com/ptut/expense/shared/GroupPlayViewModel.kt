package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.play.EarnAllSquareUseCase
import com.ptut.expense.domain.play.PlayPart
import com.ptut.expense.domain.play.ReactToPaymentUseCase
import com.ptut.expense.domain.play.SetTripEndedUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.ReactionKind
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.allSquareAt
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.SyncHealth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** #95: shown once every net is zero; the stamp is earned the same moment. */
data class AllSquareUiModel(val title: String, val line: String, val stampFace: String, val stampCaption: String)

/** #95 (D10): the three words, which one is mine, and who sent each one already. */
data class PaymentReactionsUiModel(val options: List<ReactionOptionUiModel>, val sent: List<String>, val title: String)

data class ReactionOptionUiModel(val kind: ReactionKind, val label: String, val chosen: Boolean)

data class GroupPlayUiState(
    val allSquare: AllSquareUiModel? = null,
    /** #97: null where a trip end does not apply; true once a member marked the trip ended. */
    val tripEnded: Boolean? = null,
    /** By payment id, only in a shared group. */
    val reactions: Map<String, PaymentReactionsUiModel> = emptyMap(),
)

/**
 * #95, #97: play on one group, beside [GroupDetailViewModel] so the group screen itself is unchanged
 * with play off. Group results show only on a phone that is up to date (FR-93.3, FR-96.3).
 */
class GroupPlayViewModel(
    private val groupId: String,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    private val playOn: (PlayPart) -> Boolean,
    private val earnAllSquare: EarnAllSquareUseCase,
    private val reactToPayment: ReactToPaymentUseCase,
    private val setTripEnded: SetTripEndedUseCase,
) : ViewModel() {

    val uiState: StateFlow<GroupPlayUiState> = combine(observeSnapshot(), observeSyncStatus(groupId)) { snapshot, status ->
        snapshot to (status.health in UP_TO_DATE)
    }
        // Earned while the group is open (FR-95.1); the stamp's fixed id keeps it to one.
        // A failed write must not end the screen's state; the next change tries again.
        .onEach { (snapshot, upToDate) -> if (upToDate && playOn(PlayPart.AllSquare)) runSuspendCatching { earnAllSquare(snapshot, groupId) } }
        .map { (snapshot, upToDate) -> state(snapshot, upToDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), GroupPlayUiState())

    fun react(paymentId: String, kind: ReactionKind?) {
        viewModelScope.launch { runSuspendCatching { reactToPayment(groupId, paymentId, kind) } }
    }

    fun endTrip(ended: Boolean) {
        viewModelScope.launch { runSuspendCatching { setTripEnded(groupId, ended) } }
    }

    private fun state(snapshot: SharedSnapshot, upToDate: Boolean): GroupPlayUiState {
        val group = snapshot.group(groupId) ?: return GroupPlayUiState()
        val text = AppText.copy.play.group
        val me = group.meMemberId?.let(group::member)
        return GroupPlayUiState(
            allSquare = AllSquareUiModel(text.allSquare, text.allSquareLine, AppText.copy.play.stamps.faces["AllSquare"].orEmpty(), AppText.copy.play.stamps.captionWin)
                .takeIf { upToDate && playOn(PlayPart.AllSquare) && me?.playOff != true && snapshot.allSquareAt(groupId) != null },
            tripEnded = (group.endedOn != null).takeIf { group.type == GroupType.Trip && playOn(PlayPart.TripRecap) },
            reactions = if (group.remote && playOn(PlayPart.Reactions)) reactionsOf(snapshot, group, me?.claimedByUid) else emptyMap(),
        )
    }

    private fun reactionsOf(snapshot: SharedSnapshot, group: SharedGroup, myUid: String?): Map<String, PaymentReactionsUiModel> {
        val text = AppText.copy.play.group
        val byPayment = snapshot.reactions.filter { it.groupId == groupId }.groupBy { it.paymentId }
        return snapshot.payments.filter { it.groupId == groupId }.associate { payment ->
            val sent = byPayment[payment.id].orEmpty()
            val mine = sent.firstOrNull { it.authorUid == myUid }?.kind
            payment.id to PaymentReactionsUiModel(
                options = ReactionKind.entries.map { ReactionOptionUiModel(it, text.reactions[it.name].orEmpty(), chosen = it == mine) },
                sent = sent.map { reaction ->
                    val name = group.members.firstOrNull { it.claimedByUid == reaction.authorUid }?.name ?: AppText.copy.shared.someone
                    text.reactedBy(text.reactions[reaction.kind.name].orEmpty(), name)
                },
                title = text.react,
            )
        }
    }
}

/** A group only on this phone is always up to date with itself. */

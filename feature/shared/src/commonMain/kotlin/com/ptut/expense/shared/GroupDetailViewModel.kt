package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.shared.AcknowledgeRefusedUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GetGroupDetailUseCase
import com.ptut.expense.domain.shared.GetSyncAccessUseCase
import com.ptut.expense.domain.shared.ObserveRefusedChangesUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.PollSyncUseCase
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.PutBackPaymentUseCase
import com.ptut.expense.domain.shared.RemovePaymentUseCase
import com.ptut.expense.domain.shared.SavePromptPayUseCase
import com.ptut.expense.domain.shared.SyncGroupNowUseCase
import com.ptut.expense.domain.shared.TrackGroupSeenUseCase
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.RefusedChange
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * #137: one group. The sky field holds the members, your balance and the spend in the group's
 * money (#48); the feed lists expenses and payments by day, each in the currency it was paid in and
 * what it means for you in the group's money (p27).
 */
class GroupDetailViewModel(
    private val groupId: String,
    private val categories: List<Category>,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    getSyncAccess: GetSyncAccessUseCase,
    /** #189: while the group is open, what other members add shows up within 15 seconds; iOS has no push yet. */
    pollSync: PollSyncUseCase,
    trackSeen: TrackGroupSeenUseCase,
    private val getGroupDetail: GetGroupDetailUseCase,
    private val syncGroupNow: SyncGroupNowUseCase,
    private val acknowledgeRefused: AcknowledgeRefusedUseCase,
    observeRefused: ObserveRefusedChangesUseCase = ObserveRefusedChangesUseCase(),
    private val savePromptPay: SavePromptPayUseCase,
    /** Not named removePayment: a call by that name reaches this screen's own [removePayment]. */
    private val removePaymentUseCase: RemovePaymentUseCase,
    private val putBackPayment: PutBackPaymentUseCase,
    private val clock: Clock = Clock.System,
    /** #218: Remove payment says so, with Undo. */
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
    /** Where the snapshot is mapped; tests hand in their own. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val mapper = GroupDetailMapper(categories)

    private val access = getSyncAccess()

    /** The mark as it stood when the group opened; rows others wrote after it are new this visit. */
    private val seenBefore = MutableStateFlow<Long?>(null)

    init {
        // The mark follows what is on screen, so a row that arrives while it is open is not new next time.
        viewModelScope.launch { trackSeen(groupId).collect { seenBefore.value = it } }
    }

    // Polling rides on the status flow, so the combine below stays typed.
    private val polledStatus = combine(observeSyncStatus(groupId), pollSync(groupId)) { status, _ -> status }

    val uiState: StateFlow<GroupDetailUiState> = combine(observeSnapshot(), polledStatus, seenBefore, observeRefused()) { snapshot, status, seen, refused ->
        getGroupDetail(snapshot, groupId, seen)?.let { detail ->
            mapper.loaded(detail, status, syncAvailable = access.available).copy(addAgain = refused.firstOrNull { it.groupId == groupId }?.addAgain())
        } ?: GroupDetailUiState.Missing
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), GroupDetailUiState.Missing)

    /** Only an expense can be entered again: it has a name and an amount in a money this build knows. */
    private fun RefusedChange.addAgain(): AddAgainUiModel? {
        val minor = amountMinor ?: return null
        val code = currency ?: return null
        if (title.isBlank()) return null
        return AddAgainUiModel(amount = Currencies.of(code).typedText(minor), description = title)
    }

    private val tabState = MutableStateFlow(GroupTab.Expenses)
    val tab: StateFlow<GroupTab> = tabState

    private val activityLooked = MutableStateFlow<Long?>(null)

    /** #64: Activity counts what others did after this; leaving the tab moves it to now and clears the count. */
    val activitySince: StateFlow<Long?> = combine(seenBefore, activityLooked) { seen, looked -> looked ?: seen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun showTab(next: GroupTab) {
        if (tabState.value == GroupTab.Activity && next != GroupTab.Activity) {
            activityLooked.value = clock.now().toEpochMilliseconds()
        }
        tabState.value = next
    }

    val signedIn: StateFlow<Boolean> = access.signedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), access.signedInNow)

    private val refreshing = MutableStateFlow(false)

    /** True only while a pull-to-refresh runs; the polling's own syncs never show the spinner. */
    val isRefreshing: StateFlow<Boolean> = refreshing

    /** Pull-to-refresh: syncs now instead of at the next tick. A second pull while one runs does nothing. */
    fun refresh() {
        if (!access.available) return
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                syncGroupNow(groupId)
            } finally {
                refreshing.value = false
            }
        }
    }

    /** #199: true when [text] is a PromptPay ID (or empty, to take it off) and was saved. */
    fun setPromptPay(text: String): Boolean {
        val id = if (text.isBlank()) null else PromptPay.normalize(text) ?: return false
        viewModelScope.launch { savePromptPay(groupId, id) }
        return true
    }

    /** #218: takes a recorded payment out of the balances. Undo puts it back. */
    fun removePayment(paymentId: String) {
        val putBackPayment = putBackPayment
        viewModelScope.launch {
            removePaymentUseCase(paymentId)
            haptics?.deleted()
            // The shell runs the Undo, so it holds only the id.
            toasts?.show(
                ToastMessage(
                    text = AppText.copy.shared.payment.removed,
                    tone = ToastTone.Deleted,
                    action = ToastAction(AppText.copy.common.undo) { putBackPayment(paymentId) },
                ),
            )
        }
    }

    override fun onCleared() {
        // Only a group still here has shown its refusals. One that left this phone while the screen
        // was open leaves them for the Shared tab, the one place that can still say what did not go.
        if (uiState.value is GroupDetailUiState.Loaded) acknowledgeRefused(groupId)
    }
}

package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.newestRowAt
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncHealth
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * #137: one group. The sky field holds the members, your balance and the spend in the group's
 * money (#48); the feed lists expenses and payments by day, each in the currency it was paid in and
 * what it means for you in the group's money (p27).
 */
class GroupDetailViewModel(
    private val groupId: String,
    private val repository: SharedRepository,
    private val categories: List<Category>,
    private val sync: SharedSync? = null,
    private val clock: Clock = Clock.System,
    /** #203: rebuilds repeat reminders once a group has gone from this phone. */
    private val runner: RepeatRunner? = null,
    /** #218: Remove payment says so, with Undo. */
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
    /** Where the snapshot is mapped; tests hand in their own. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val mapper = GroupDetailMapper(categories)

    private val status = sync?.status(groupId) ?: flowOf(GroupSyncStatus(SyncHealth.Local, 0))

    // #189: while the group is open, what other members add shows up within 15 seconds. iOS has no
    // push yet, so this is how an iPhone hears of it.
    private val polling = sync?.takeIf { it.available }?.let { syncPolling(it.signedIn, runner, SYNC_INTERVAL) { it.syncGroupOutcome(groupId) } } ?: flowOf(Unit)

    /** The mark as it stood when the group opened; rows others wrote after it are new this visit. */
    private val seenBefore = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            seenBefore.value = repository.snapshot.first { it.group(groupId) != null }.group(groupId)?.lastSeenAtEpochMillis
            // The mark follows what is on screen, so a row that arrives while it is open is not new next time.
            repository.snapshot
                // Never past this phone's own clock: a member's phone stamps its rows, and one dated
                // ahead would hold the mark there and keep every later row from looking new.
                .map { it.newestRowAt(groupId)?.coerceAtMost(clock.now().toEpochMilliseconds()) }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { repository.markSeen(groupId, it) }
        }
    }

    val uiState: StateFlow<GroupDetailUiState> = combine(repository.snapshot, status, polling, seenBefore) { snapshot, status, _, seen ->
        val today = clock.now().dateIn(HomeTime)
        snapshot.group(groupId)?.let { group -> mapper.loaded(snapshot, group, seen, today, status, syncAvailable = sync?.available == true) } ?: GroupDetailUiState.Missing
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), GroupDetailUiState.Missing)

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

    val signedIn: StateFlow<Boolean> = (sync?.signedIn ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), sync?.isSignedIn == true)

    private val refreshing = MutableStateFlow(false)

    /** True only while a pull-to-refresh runs; the polling's own syncs never show the spinner. */
    val isRefreshing: StateFlow<Boolean> = refreshing

    /** Pull-to-refresh: syncs now instead of at the next tick. A second pull while one runs does nothing. */
    fun refresh() {
        val sync = sync?.takeIf { it.available } ?: return
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                syncOnce(runner) { sync.syncGroupOutcome(groupId) }
            } finally {
                refreshing.value = false
            }
        }
    }

    /** #199: true when [text] is a PromptPay ID (or empty, to take it off) and was saved. */
    fun setPromptPay(text: String): Boolean {
        val id = if (text.isBlank()) null else PromptPay.normalize(text) ?: return false
        viewModelScope.launch { repository.setPromptPay(groupId, id) }
        return true
    }

    /** #218: takes a recorded payment out of the balances. Undo puts it back. */
    fun removePayment(paymentId: String) {
        val repository = repository
        viewModelScope.launch {
            repository.removePayment(paymentId)
            haptics?.deleted()
            // The shell runs the Undo, so it holds only the id.
            toasts?.show(
                ToastMessage(
                    text = AppText.copy.shared.payment.removed,
                    tone = ToastTone.Deleted,
                    action = ToastAction(AppText.copy.common.undo) { repository.putBackPayment(paymentId) },
                ),
            )
        }
    }

    override fun onCleared() {
        sync?.acknowledgeRefused(groupId)
    }

    private companion object {
        val SYNC_INTERVAL = 15.seconds
    }
}

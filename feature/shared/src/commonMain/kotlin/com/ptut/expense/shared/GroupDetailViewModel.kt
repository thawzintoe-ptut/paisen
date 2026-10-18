package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.anchorLine
import com.ptut.expense.domain.shared.currentExpenses
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.myNet
import com.ptut.expense.domain.shared.nets
import com.ptut.expense.domain.shared.newestRowAt
import com.ptut.expense.domain.shared.paymentsIn
import com.ptut.expense.domain.shared.running
import com.ptut.expense.domain.shared.toSatang
import com.ptut.expense.domain.shared.totalSpend
import com.ptut.expense.domain.shared.whoPaysWhom
import com.ptut.expense.domain.sum
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.InviteLinks
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncHealth
import com.ptut.expense.sync.syncMessage
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** [joined] is false while a member is only a name nobody has claimed (#190). */
data class MemberChipUiModel(val id: String, val label: String, val isMe: Boolean, val joined: Boolean = true, val name: String = label)

/** One row of p27's feed: an expense, or a payment with its neutral tile. */
sealed interface GroupFeedRow {
    val id: String

    data class Expense(
        val id_: String,
        val icon: String,
        val title: String,
        val detail: String,
        val word: String,
        val amount: String,
        val tone: BalanceTone,
        /** Another member wrote it after the newest row shown last visit (research finding 5). */
        val isNew: Boolean = false,
    ) : GroupFeedRow {
        override val id get() = id_
    }

    data class Payment(
        val id_: String,
        val title: String,
        val detail: String,
        val amount: String,
        /** #218: for the payment's sheet. "Fri 14 Aug · 18:20", and who recorded it, when. */
        val occurred: String = "",
        val recorded: String = "",
        val isNew: Boolean = false,
    ) : GroupFeedRow {
        override val id get() = id_
    }
}

data class GroupDay(val label: String, val rows: List<GroupFeedRow>)

sealed interface GroupDetailUiState {
    data object Missing : GroupDetailUiState

    data class Loaded(
        val groupId: String,
        val name: String,
        val members: List<MemberChipUiModel>,
        val balanceLabel: String,
        val balance: Satang,
        val tone: BalanceTone,
        val spendLine: String,
        val settled: Boolean,
        /** Where SETTLE UP goes: the first payment that involves you, or null to show Who pays whom. */
        val settleWith: Pair<String, String>?,
        val days: List<GroupDay>,
        /** #189: "Up to date", "Offline · 3 changes waiting", or null for a group not shared yet. */
        val syncLine: String? = null,
        /** #190: whether this build can share at all, and whether this phone may change the link. */
        val canInvite: Boolean = false,
        val isCreator: Boolean = false,
        val invite: InviteUiModel? = null,
        /** #201: "Mali hasn't joined yet", said in words beside the faded chips. */
        val notJoinedLine: String? = null,
        /** #182: the repeats still running, each opening #185 to change or stop it. */
        val repeats: List<RepeatRowUiModel> = emptyList(),
        /** Edit group starts from these. */
        val type: GroupType = GroupType.Other,
        val icon: String = "",
        /** #199: this phone's own PromptPay ID in the group, masked, or null when none is set. */
        val myPromptPay: String? = null,
        /** What the menu's last item does to this group, see [GroupRemoval]. */
        val removal: GroupRemoval = GroupRemoval.DeleteHere,
        /** #218: a shared group on a phone whose session ended. The hero offers Sign in. */
        val signedOut: Boolean = false,
        /** #218: everyone but you, for Remove someone; and the people Add people can pick from. */
        val others: List<GroupPersonUiModel> = emptyList(),
        val canRemovePeople: Boolean = false,
        val addable: List<Person> = emptyList(),
        /** #218: a removed payment leaves a line in Activity only where the group syncs. */
        val isShared: Boolean = false,
    ) : GroupDetailUiState
}

/** One name in the group besides yours. [settled] is false while they owe or are owed here. */
data class GroupPersonUiModel(val id: String, val name: String, val settled: Boolean)

/**
 * How a group goes. One only on this phone is deleted here; a shared one is deleted for everyone by
 * the account that created it, and left by everyone else, whose copy only goes from their phone.
 */
enum class GroupRemoval { DeleteHere, DeleteForEveryone, Leave }

/**
 * Edit group and Delete group: working while the server answers, then an error or done. [waiting]
 * counts changes not sent yet when Leave stopped to ask first (#218).
 */
data class GroupActionState(val working: Boolean = false, val error: String? = null, val waiting: Int = 0)

/** "Rent" and "The 1st of each month · ฿18,000". */
data class RepeatRowUiModel(val id: String, val name: String, val line: String)

/** #190's invite sheet. [link] is what goes to LINE; [code] is the same, typed by hand. */
data class InviteUiModel(val code: String, val link: String, val active: Boolean)

data class InviteActionState(val working: Boolean = false, val error: String? = null)

/**
 * #137: one group. The sky field holds the members, your balance and the spend in THB; the feed
 * lists expenses and payments by day, each in the currency it was paid in and what it means for
 * you in THB (p27).
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
) : ViewModel() {

    private val status = sync?.status(groupId) ?: flowOf(GroupSyncStatus(SyncHealth.Local, 0))

    // #189: while the group is open, what other members add shows up within a minute.
    private val polling = sync?.takeIf { it.available }?.let { syncPolling(it, runner, SYNC_INTERVAL) } ?: flowOf(Unit)

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
        snapshot.group(groupId)?.let { group -> snapshot.loaded(group, seen).withSync(group, status) } ?: GroupDetailUiState.Missing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState.Missing)

    private val action = MutableStateFlow(InviteActionState())
    val inviteAction: StateFlow<InviteActionState> = action

    private val groupAction = MutableStateFlow(GroupActionState())
    val groupActionState: StateFlow<GroupActionState> = groupAction

    private val removed = MutableStateFlow(false)

    /** True once the group has gone from this phone; the shell leaves the screen on it. */
    val isRemoved: StateFlow<Boolean> = removed

    val signedIn: StateFlow<Boolean> = (sync?.signedIn ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sync?.isSignedIn == true)

    /** #199: true when [text] is a PromptPay ID (or empty, to take it off) and was saved. */
    fun setPromptPay(text: String): Boolean {
        val id = if (text.isBlank()) null else PromptPay.normalize(text) ?: return false
        viewModelScope.launch { repository.setPromptPay(groupId, id) }
        return true
    }

    fun editGroup(name: String, type: GroupType, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.editGroup(groupId, name, type, icon) }
    }

    /**
     * Asked first in a sheet; there is no undo, so the sheet is the safeguard. Leave sends what waits
     * first and asks again before [anyway] drops what still could not go (#218).
     */
    fun removeGroup(anyway: Boolean = false) {
        val state = uiState.value as? GroupDetailUiState.Loaded ?: return
        viewModelScope.launch {
            groupAction.value = GroupActionState(working = true)
            groupAction.value = try {
                when (state.removal) {
                    GroupRemoval.DeleteHere -> repository.deleteGroup(groupId)
                    GroupRemoval.DeleteForEveryone -> requireNotNull(sync).deleteForEveryone(groupId)
                    GroupRemoval.Leave -> requireNotNull(sync).leaveGroup(groupId, dropUnsent = anyway)
                }
                runCatching { runner?.run() }
                removed.value = true
                GroupActionState()
            } catch (waiting: ChangesWaiting) {
                GroupActionState(waiting = waiting.count)
            } catch (failure: Exception) {
                GroupActionState(error = failure.syncMessage())
            }
        }
    }

    /** #218: names added after Create. In a shared group they sync, and each joins from the link. */
    fun addPeople(members: List<NewMember>) {
        if (members.none { it.name.isNotBlank() }) return
        viewModelScope.launch { repository.addMembers(groupId, members) }
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

    /** #218: archives one name here; past splits keep it. The repository holds the creator-only rule. */
    fun removeMember(memberId: String) {
        viewModelScope.launch { repository.removeMember(groupId, memberId) }
    }

    fun dismissGroupError() {
        groupAction.value = GroupActionState()
    }

    override fun onCleared() {
        sync?.acknowledgeRefused(groupId)
    }

    /** Shares the group the first time, then shows its link (#190). The caller has signed in. */
    fun invite() = runInvite { sync?.share(groupId) }

    fun newLink() = runInvite { sync?.newInvite(groupId) }

    fun switchOff() = runInvite { sync?.switchOffInvite(groupId) }

    private fun runInvite(work: suspend () -> Unit) {
        viewModelScope.launch {
            action.value = InviteActionState(working = true)
            action.value = try {
                work()
                InviteActionState()
            } catch (failure: Exception) {
                InviteActionState(error = failure.syncMessage())
            }
        }
    }

    private fun GroupDetailUiState.Loaded.withSync(group: SharedGroup, status: GroupSyncStatus): GroupDetailUiState.Loaded = copy(
        members = members.map { chip -> chip.copy(joined = !group.remote || chip.isMe || group.member(chip.id)?.claimedByUid != null) },
        notJoinedLine = members
            .filterNot { chip -> !group.remote || chip.isMe || group.member(chip.id)?.claimedByUid != null }
            .map { it.name }
            .joinedLine(),
        syncLine = when {
            // #194: said once, then cleared when the screen goes.
            status.refused > 0 -> AppText.copy.shared.detail.refused(status.refused)

            status.health == SyncHealth.Local -> null

            status.health == SyncHealth.SignedOut -> AppText.copy.shared.sync.signedOutGroup(status.waiting)

            status.health == SyncHealth.UpToDate -> AppText.copy.shared.detail.upToDate

            status.health == SyncHealth.Waiting -> AppText.copy.shared.changesWaiting(status.waiting)

            else -> AppText.copy.shared.offlineWaiting(status.waiting)
        },
        canInvite = sync?.available == true,
        isCreator = !group.remote || group.creatorUid == null || group.creatorUid == group.member(group.meMemberId.orEmpty())?.claimedByUid,
        removal = when {
            !group.remote -> GroupRemoval.DeleteHere
            group.creatorUid != null && group.creatorUid == group.member(group.meMemberId.orEmpty())?.claimedByUid -> GroupRemoval.DeleteForEveryone
            else -> GroupRemoval.Leave
        },
        invite = group.inviteCode?.let { InviteUiModel(it, InviteLinks.link(it), group.inviteActive) },
        signedOut = status.health == SyncHealth.SignedOut,
        // The same rule the repository and Firestore's rules hold: only the creator removes a name.
        canRemovePeople = !group.remote || (group.creatorUid != null && group.creatorUid == group.member(group.meMemberId.orEmpty())?.claimedByUid),
    )

    private fun SharedSnapshot.loaded(group: SharedGroup, seen: Long?): GroupDetailUiState.Loaded {
        val text = AppText.copy.shared
        val you = AppText.copy.common.you
        val net = myNet(group.id)
        val me = group.meMemberId
        val settlement = whoPaysWhom(group.id)
        val today = clock.now().toLocalDateTime(BangkokTime).date
        val balances = nets(group.id)
        val expenseRows = currentExpenses(group.id).map { it.occurredAtEpochMillis to it.row(group, seen) }
        val paymentRows = paymentsIn(group.id).map { payment ->
            val author = payment.authorMemberId?.let { group.displayName(it) } ?: text.someone
            payment.occurredAtEpochMillis to GroupFeedRow.Payment(
                id_ = payment.id,
                title = text.detail.paid(group.displayName(payment.fromMemberId), group.displayName(payment.toMemberId)),
                detail = text.detail.settlement(payment.method.label()),
                amount = payment.amount.format(),
                occurred = payment.occurredAtEpochMillis.dayAndTime(),
                recorded = text.log.recordedBy(author, payment.createdAtEpochMillis.dayAndTime()),
                isNew = isNewTo(group, seen, payment.authorMemberId, payment.createdAtEpochMillis),
            )
        }
        val days = (expenseRows + paymentRows)
            .sortedByDescending { it.first }
            .groupBy { (at, _) -> Instant.fromEpochMilliseconds(at).dateIn(BangkokTime) }
            .map { (date, rows) -> GroupDay(if (date == today) AppText.copy.common.today.uppercase() else date.dayLabel().uppercase(), rows.map { it.second }) }

        return GroupDetailUiState.Loaded(
            groupId = group.id,
            name = group.name,
            isShared = group.remote,
            members = group.activeMembers
                .sortedBy { !group.isMe(it.id) }
                .map { MemberChipUiModel(it.id, if (group.isMe(it.id)) you else it.initial(), group.isMe(it.id), name = if (group.isMe(it.id)) you else it.name) },
            balanceLabel = when (net.tone()) {
                BalanceTone.Owed -> text.detail.owedHere
                BalanceTone.Owe -> text.detail.oweHere
                BalanceTone.Settled -> text.detail.settledHere
            },
            balance = net.magnitude(),
            tone = net.tone(),
            spendLine = text.detail.spendLine(group.type == GroupType.Trip, totalSpend(group.id).formatCompact()),
            settled = balances.values.all { it == Satang.Zero },
            settleWith = settlement.transfers.firstOrNull { it.fromMemberId == me || it.toMemberId == me }
                ?.let { it.fromMemberId to it.toMemberId },
            days = days,
            type = group.type,
            icon = group.icon,
            myPromptPay = group.member(me.orEmpty())?.promptPayId?.let(PromptPay::masked),
            repeats = repeats.filter { it.groupId == group.id && it.running(today) }.map { rule ->
                RepeatRowUiModel(
                    id = rule.id,
                    name = rule.description,
                    line = "${rule.anchorLine()} · ${toSatang(rule.amountMinor, Currencies.of(rule.currency), rule.rate).formatCompact()}",
                )
            },
            others = group.activeMembers.filterNot { group.isMe(it.id) }.map { member ->
                GroupPersonUiModel(member.id, member.name, settled = (balances[member.id] ?: Satang.Zero) == Satang.Zero)
            },
            addable = people.filter { person -> !person.archived && group.activeMembers.none { it.personId == person.id } },
        )
    }

    /** "Tom paid ₫1,850,000", then what it means for you in THB: you owe, you lent, or not involved. */
    private fun SharedExpense.row(group: SharedGroup, seen: Long?): GroupFeedRow.Expense {
        val text = AppText.copy.shared
        val me = group.meMemberId
        val mine = shares.filter { it.memberId == me }.map { it.satang }.sum()
        val effect = (if (payerMemberId == me) satang else Satang.Zero) - mine
        val involved = payerMemberId == me || shares.any { it.memberId == me }
        return GroupFeedRow.Expense(
            id_ = id,
            icon = categories.firstOrNull { it.id == categoryId }?.icon ?: DEFAULT_ICON,
            title = description,
            detail = text.detail.payerPaid(group.displayName(payerMemberId), formatMinor(amountMinor, Currencies.of(currency))),
            word = when {
                !involved -> text.detail.notInvolved
                effect > Satang.Zero -> text.detail.youLent
                effect < Satang.Zero -> text.youOwe
                else -> text.detail.yourShare
            },
            amount = (if (effect == Satang.Zero) mine else effect.magnitude()).format(),
            tone = if (involved) effect.tone() else BalanceTone.Settled,
            isNew = isNewTo(group, seen, authorMemberId, createdAtEpochMillis),
        )
    }

    /** Never on a first visit, when everything would be new; never for this phone's own rows. */
    private fun isNewTo(group: SharedGroup, seen: Long?, author: String?, createdAt: Long): Boolean =
        seen != null && author != null && !group.isMe(author) && createdAt > seen

    private companion object {
        const val DEFAULT_ICON = "receipt_long"
        val SYNC_INTERVAL = 60.seconds
    }
}

fun PaymentMethod.label(): String {
    val text = AppText.copy.shared
    return when (this) {
        PaymentMethod.BankTransfer -> text.methodBankTransfer
        PaymentMethod.Cash -> text.methodCash
        PaymentMethod.PromptPay -> "PromptPay"
        PaymentMethod.OtherApp -> text.methodOtherApp
    }
}

/** "Mali hasn't joined yet", "Mali and Nok haven't joined yet", or "3 people haven't joined yet". */
private fun List<String>.joinedLine(): String? {
    val text = AppText.copy.shared.detail
    return when (size) {
        0 -> null
        1 -> text.hasNotJoined(single())
        2 -> text.twoHaveNotJoined(first(), last())
        else -> text.manyHaveNotJoined(size)
    }
}

/** "Fri 14 Aug · 18:20", in Bangkok time. */
private fun Long.dayAndTime(): String = "${dateIn(BangkokTime).dayLabel()} · ${timeOfDayIn(BangkokTime)}"

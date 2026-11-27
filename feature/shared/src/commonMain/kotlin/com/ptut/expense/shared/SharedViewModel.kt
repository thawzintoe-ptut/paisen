package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.format
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.CreateGroupUseCase
import com.ptut.expense.domain.shared.CreatePersonGroupUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.DismissRefusedChangeUseCase
import com.ptut.expense.domain.shared.GetSharedHomeUseCase
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.ObserveRefusedChangesUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.Overall
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.PollSyncUseCase
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedHome
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.balanceKey
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.domain.shared.withMe
import com.ptut.expense.domain.usage.UsageEvent
import com.ptut.expense.domain.usage.UsageLog
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.RefusedChange
import com.ptut.expense.sync.SyncHealth
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SharedGroupRowUiModel(
    val id: String,
    val name: String,
    val icon: String,
    /** A home's tile is ink, a trip's sky (p26). */
    val inkTile: Boolean,
    val detail: String,
    val word: String,
    val amount: String,
    val tone: BalanceTone,
    /** "Mali added 2": what others wrote since this phone last opened the group (research finding 5). */
    val news: String? = null,
)

/**
 * p26's top for one money: the net, what it counts in (#48), and its two halves as one line,
 * "You're owed ฿1,800 · you owe ฿250". [oweLine] is null while you owe nobody in this money.
 * [showHalves] is false when one half is zero: the line would only repeat the net above it.
 */
data class OverallUiModel(
    val label: String,
    val amount: Satang,
    val money: HomeMoney,
    val tone: BalanceTone,
    val owedLine: String,
    val oweLine: String?,
    val showHalves: Boolean = oweLine != null,
)

/** The one payment to make next: the largest you owe, and the record-payment screen it opens. */
data class PayNextUiModel(
    val title: String,
    val line: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    /** Record payment's prefill, as the routes carry amounts ("350.00"): the same figure as [title]. */
    val amount: String,
)

/** #194: one change a group refused and that group has since left this phone, as "QA4 didn't accept: Dinner ฿80.00". */
data class LostChangeUiModel(val id: String, val line: String)

sealed interface SharedUiState {
    /** What groups no longer here would not take; a member removed while offline reads theirs here. */
    val lost: List<LostChangeUiModel>

    /** p34: before any group exists. */
    data class Empty(val people: List<Person>, override val lost: List<LostChangeUiModel> = emptyList()) : SharedUiState

    data class Ready(
        /** One per money the groups count in, the home's first; two moneys are never one figure (#48). */
        val overall: List<OverallUiModel>,
        val groups: List<SharedGroupRowUiModel>,
        /** "Aye, Mali": everyone you split with, under the People row that opens them. */
        val peopleLine: String,
        val knownPeople: List<Person>,
        /** Null while you owe nobody. */
        val payNext: PayNextUiModel? = null,
        val query: String,
        /** #196: "2 changes waiting to sync", or null when everything is up to date. */
        val syncLine: String? = null,
        /** #218: shared groups on a phone whose session ended. The line offers Sign in. */
        val signedOut: Boolean = false,
        override val lost: List<LostChangeUiModel> = emptyList(),
    ) : SharedUiState
}

/**
 * #136: the v2 home. One question first, "am I up or down overall", then groups, then people.
 * Every figure comes from the log through the derivations in `domain/shared` (p36).
 */
class SharedViewModel(
    observeSnapshot: ObserveSharedSnapshotUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    /** #196: the tab pulls while it is open, so its totals are never older than half a minute. */
    pollSync: PollSyncUseCase,
    private val getSharedHome: GetSharedHomeUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val createPersonGroup: CreatePersonGroupUseCase,
    observeRefused: ObserveRefusedChangesUseCase = ObserveRefusedChangesUseCase(),
    private val dismissRefused: DismissRefusedChangeUseCase = DismissRefusedChangeUseCase(),
    private val toasts: ToastMessages? = null,
    /** #115: a count per group made here. */
    private val usage: UsageLog = UsageLog.None,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Polling rides on the status flow, so the five-flow combine below stays typed.
    private val polledStatus = combine(observeSyncStatus(), pollSync()) { status, _ -> status }

    val uiState: StateFlow<SharedUiState> = combine(observeSnapshot(), query, polledStatus, observeRefused()) { snapshot, query, status, refused ->
        // A group still here shows its own refusals on its screen; only the ones left behind show here.
        val here = snapshot.groups.map { it.id }.toSet()
        val lost = refused.filterNot { it.groupId in here }.map { it.lostLine() }
        if (snapshot.groups.isEmpty()) {
            SharedUiState.Empty(snapshot.people, lost)
        } else {
            getSharedHome(snapshot, query).ready(snapshot, query).copy(syncLine = status.line(), signedOut = status.health == SyncHealth.SignedOut, lost = lost)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), SharedUiState.Empty(emptyList()))

    fun dismissLost(id: String) {
        viewModelScope.launch { dismissRefused(id) }
    }

    private fun RefusedChange.lostLine(): LostChangeUiModel {
        val text = AppText.copy.shared
        val group = groupName ?: text.group
        val amount = amountMinor?.let { minor -> currency?.let { formatMinor(minor, Currencies.of(it)) } }
        val what = listOfNotNull(title.takeIf { it.isNotBlank() }, amount).joinToString(" ")
        return LostChangeUiModel(id, if (what.isEmpty()) text.home.lostChange(group) else text.home.lost(group, what))
    }

    /** Quiet when all is sent: the line is there only while something waits or was refused. */
    private fun GroupSyncStatus.line(): String? {
        val text = AppText.copy.shared
        return when {
            health == SyncHealth.SignedOut -> text.sync.signedOutHome
            health == SyncHealth.EndingElsewhere -> text.sync.endingElsewhere(waiting)
            refused > 0 -> text.home.refused(refused)
            health == SyncHealth.Waiting -> text.changesWaiting(waiting)
            health == SyncHealth.Offline -> text.offlineWaiting(waiting)
            else -> null
        }
    }

    fun search(text: String) {
        query.value = text
    }

    /** #141: a group, then its detail. [onCreated] gets the new group's id. #48: [currency] stays the group's for good. */
    fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String, onCreated: (String) -> Unit) {
        // A second tap while the first is saving would make a second group.
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                runSuspendCatching { createGroupUseCase(name, type, icon, members, currency) }
                    .onSuccess { usage.log(UsageEvent.GroupCreated) }
                    .onSuccess(onCreated)
                    .onFailure { toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error)) }
            } finally {
                creating = false
            }
        }
    }

    private var creating = false

    /** #141: a one-off bill with one person is a group of two behind the scenes, named after them. */
    fun addPerson(name: String, personId: String?, onCreated: (String) -> Unit) {
        if (adding) return
        adding = true
        viewModelScope.launch {
            try {
                runSuspendCatching { createPersonGroup(name, personId, GroupType.Other.defaultIcon()) }
                    .onSuccess { usage.log(UsageEvent.GroupCreated) }
                    .onSuccess(onCreated)
                    .onFailure { toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error)) }
            } finally {
                adding = false
            }
        }
    }

    private var adding = false

    private fun SharedHome.ready(snapshot: SharedSnapshot, query: String): SharedUiState.Ready {
        val text = AppText.copy.shared
        val groupRows = groups.map { summary ->
            val group = summary.group
            val detail = buildList {
                add(if (summary.others.size == 1) text.home.youAnd(summary.others.single().name) else text.home.peopleCount(group.activeMembers.size))
                summary.currencies.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                // p26's "rent repeats 1st" (#136, #185).
                summary.repeat?.let { add(it.shortLine()) }
            }.joinToString(Separators.DOT)
            SharedGroupRowUiModel(
                id = group.id,
                name = group.name,
                icon = group.icon,
                inkTile = group.type == GroupType.Flat,
                detail = detail,
                word = when (summary.net.tone()) {
                    BalanceTone.Owed -> text.youAreOwed
                    BalanceTone.Owe -> text.youOwe
                    BalanceTone.Settled -> text.settledUp
                },
                amount = summary.net.magnitude().format(group.moneyFormat()),
                tone = summary.net.tone(),
                news = summary.newFrom.takeIf { it.isNotEmpty() }?.let { authors ->
                    authors.distinct().singleOrNull()?.let { text.home.addedSince(group.displayName(it), authors.size) }
                        ?: text.home.newSince(authors.size)
                },
            )
        }
        return SharedUiState.Ready(
            overall = overall.map { it.uiModel() },
            groups = groupRows,
            peopleLine = people.map { it.name }.distinct().joinToString(", "),
            knownPeople = knownPeople,
            payNext = payNext(snapshot),
            query = query,
        )
    }

    /**
     * The largest debt in the home's money, else in the first money you owe in; the line counts
     * everyone else you owe. Amounts in two moneys are never compared (#48).
     */
    private fun SharedHome.payNext(snapshot: SharedSnapshot): PayNextUiModel? {
        val owed = people.filter { it.amount < Satang.Zero }
        val homeCode = overall.firstOrNull()?.currency
        val first = owed.filter { it.currency == homeCode }.minByOrNull { it.amount } ?: owed.minByOrNull { it.amount } ?: return null
        // The group where this person's balance with you is most negative is where the payment goes.
        val (group, member, debt) = first.groupIds.mapNotNull { id -> snapshot.group(id)?.takeIf { it.meMemberId != null } }
            .flatMap { group ->
                val balances = snapshot.withMe(group.id)
                group.members.filter { it.balanceKey() == first.key && !group.isMe(it.id) && (balances[it.id] ?: Satang.Zero) < Satang.Zero }
                    .map { member -> Triple(group, member, balances.getValue(member.id)) }
            }
            .minByOrNull { it.third } ?: return null
        val text = AppText.copy.shared.home
        // One row per person per money, so count people, not rows.
        val others = owed.map { it.key }.distinct().count { it != first.key }
        return PayNextUiModel(
            title = text.pay(member.name, debt.magnitude().format(group.moneyFormat())),
            line = if (others == 0) text.onlyPayment else text.andMore(others),
            groupId = group.id,
            fromMemberId = checkNotNull(group.meMemberId),
            toMemberId = member.id,
            amount = group.money.typedText(debt.magnitude().toMinorUnits()),
        )
    }
}

private fun Overall.uiModel(): OverallUiModel {
    val text = AppText.copy.shared.home
    val money = Currencies.of(currency).toHomeMoney()
    return OverallUiModel(
        label = when (net.tone()) {
            BalanceTone.Owed -> text.overallOwed
            BalanceTone.Owe -> text.overallOwe
            BalanceTone.Settled -> text.allSettledUp
        },
        amount = net.magnitude(),
        money = money,
        tone = net.tone(),
        owedLine = text.owedLine(owedToYou.format(money)),
        oweLine = text.oweLine(youOwe.format(money)).takeIf { youOwe > Satang.Zero },
        showHalves = owedToYou > Satang.Zero && youOwe > Satang.Zero,
    )
}

/** "rent repeats 1st" for a monthly repeat, "internet repeats weekly" for the rest. */
private fun RepeatRule.shortLine(): String {
    val name = description.lowercase()
    val text = AppText.copy.shared.home
    return when (frequency) {
        RepeatFrequency.Monthly -> text.repeatsMonthly(name, start.day.ordinal(), start.day)
        RepeatFrequency.Weekly -> text.repeatsWeekly(name)
        RepeatFrequency.TwoWeeks -> text.repeatsTwoWeeks(name)
        RepeatFrequency.Yearly -> text.repeatsYearly(name)
    }
}

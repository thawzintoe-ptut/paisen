package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.Overall
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.currenciesOf
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.myNet
import com.ptut.expense.domain.shared.newFromOthers
import com.ptut.expense.domain.shared.overall
import com.ptut.expense.domain.shared.peopleBalances
import com.ptut.expense.domain.shared.running
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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

data class SharedPersonRowUiModel(val key: String, val name: String, val initial: String, val word: String, val amount: String, val tone: BalanceTone)

/** p26's top for one money: the net, its two halves, and what they count in (#48). */
data class OverallUiModel(val label: String, val amount: Satang, val money: HomeMoney, val tone: BalanceTone, val owedToYou: String, val youOwe: String)

sealed interface SharedUiState {
    /** p34: before any group exists. */
    data class Empty(val people: List<Person>) : SharedUiState

    data class Ready(
        /** One per money the groups count in, the home's first; two moneys are never one figure (#48). */
        val overall: List<OverallUiModel>,
        val groups: List<SharedGroupRowUiModel>,
        val people: List<SharedPersonRowUiModel>,
        val knownPeople: List<Person>,
        val query: String,
        /** #196: "2 changes waiting to sync", or null when everything is up to date. */
        val syncLine: String? = null,
        /** #218: shared groups on a phone whose session ended. The line offers Sign in. */
        val signedOut: Boolean = false,
    ) : SharedUiState
}

/**
 * #136: the v2 home. One question first, "am I up or down overall", then groups, then people.
 * Every figure comes from the log through the derivations in `domain/shared` (p36).
 */
class SharedViewModel(
    private val repository: SharedRepository,
    /** #196: the tab pulls while it is open, so its totals are never older than half a minute. */
    private val sync: SharedSync? = null,
    private val runner: RepeatRunner? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val status = sync?.overallStatus() ?: flowOf(GroupSyncStatus(SyncHealth.Local, 0))

    private val polling = sync?.takeIf { it.available }?.let { syncPolling(it.signedIn, runner, SYNC_INTERVAL, it::syncAllOutcome) } ?: flowOf(Unit)

    val uiState: StateFlow<SharedUiState> = combine(repository.snapshot, query, status, polling) { snapshot, query, status, _ ->
        if (snapshot.groups.isEmpty()) {
            SharedUiState.Empty(snapshot.people)
        } else {
            snapshot.ready(query).copy(syncLine = status.line(), signedOut = status.health == SyncHealth.SignedOut)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), SharedUiState.Empty(emptyList()))

    /** Quiet when all is sent: the line is there only while something waits or was refused. */
    private fun GroupSyncStatus.line(): String? {
        val text = AppText.copy.shared
        return when {
            health == SyncHealth.SignedOut -> text.sync.signedOutHome
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
        viewModelScope.launch { onCreated(repository.createGroup(name, type, icon, members, currency)) }
    }

    /** #141: a one-off bill with one person is a group of two behind the scenes, named after them. */
    fun addPerson(name: String, personId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createPersonGroup(name, personId)) }
    }

    private fun SharedSnapshot.ready(query: String): SharedUiState.Ready {
        val text = AppText.copy.shared
        val match = query.trim().lowercase()
        val today = clock.now().dateIn(HomeTime)
        val groupRows = groups.map { group ->
            val net = myNet(group.id)
            val others = group.activeMembers.filterNot { group.isMe(it.id) }
            val detail = buildList {
                add(if (others.size == 1) text.home.youAnd(others.single().name) else text.home.peopleCount(group.activeMembers.size))
                currenciesOf(group.id).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                // p26's "rent repeats 1st" (#136, #185).
                repeats.firstOrNull { it.groupId == group.id && it.running(today) }?.let { add(it.shortLine()) }
            }.joinToString(Separators.DOT)
            SharedGroupRowUiModel(
                id = group.id,
                name = group.name,
                icon = group.icon,
                inkTile = group.type == GroupType.Flat,
                detail = detail,
                word = when (net.tone()) {
                    BalanceTone.Owed -> text.youAreOwed
                    BalanceTone.Owe -> text.youOwe
                    BalanceTone.Settled -> text.settledUp
                },
                amount = net.magnitude().format(group.moneyFormat()),
                tone = net.tone(),
                news = newFromOthers(group).takeIf { it.isNotEmpty() }?.let { authors ->
                    authors.distinct().singleOrNull()?.let { text.home.addedSince(group.displayName(it), authors.size) }
                        ?: text.home.newSince(authors.size)
                },
            )
        }.filter { match.isEmpty() || it.name.lowercase().contains(match) }
        val peopleRows = peopleBalances().map { person ->
            SharedPersonRowUiModel(
                key = "${person.key}:${person.currency}",
                name = person.name,
                initial = initialOf(person.name),
                word = person.amount.tone().personWord(),
                amount = person.amount.magnitude().format(Currencies.of(person.currency).toHomeMoney()),
                tone = person.amount.tone(),
            )
        }.filter { match.isEmpty() || it.name.lowercase().contains(match) }
        // Nobody to owe or be owed by yet: one settled line in the home's money.
        val totals = overall().ifEmpty { listOf(Overall(Home.money.code, Satang.Zero, Satang.Zero, Satang.Zero)) }
        return SharedUiState.Ready(
            overall = totals.map { it.uiModel() },
            groups = groupRows,
            people = peopleRows,
            knownPeople = people.filterNot { it.archived },
            query = query,
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
        owedToYou = owedToYou.format(money),
        youOwe = youOwe.format(money),
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

private val SYNC_INTERVAL = 60.seconds

/** #141: one person is a group of two behind the scenes, named after them. Shared and People both add one. */
internal suspend fun SharedRepository.createPersonGroup(name: String, personId: String?): String =
    createGroup(name.trim(), GroupType.Other, GroupType.Other.defaultIcon(), listOf(NewMember(name, personId)))

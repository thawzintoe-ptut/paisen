package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.currenciesOf
import com.ptut.expense.domain.shared.myNet
import com.ptut.expense.domain.shared.newFromOthers
import com.ptut.expense.domain.shared.ordinal
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
import kotlinx.datetime.toLocalDateTime
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

sealed interface SharedUiState {
    /** p34: before any group exists. */
    data class Empty(val people: List<Person>) : SharedUiState

    data class Ready(
        val overallLabel: String,
        val overall: Satang,
        val tone: BalanceTone,
        val owedToYou: String,
        val youOwe: String,
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

    private val polling = sync?.takeIf { it.available }?.let { syncPolling(it, runner, SYNC_INTERVAL) } ?: flowOf(Unit)

    val uiState: StateFlow<SharedUiState> = combine(repository.snapshot, query, status, polling) { snapshot, query, status, _ ->
        if (snapshot.groups.isEmpty()) {
            SharedUiState.Empty(snapshot.people)
        } else {
            snapshot.ready(query).copy(syncLine = status.line(), signedOut = status.health == SyncHealth.SignedOut)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharedUiState.Empty(emptyList()))

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

    /** #141: a group, then its detail. [onCreated] gets the new group's id. */
    fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createGroup(name, type, icon, members)) }
    }

    /** #141: a one-off bill with one person is a group of two behind the scenes, named after them. */
    fun addPerson(name: String, personId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createGroup(name.trim(), GroupType.Other, GroupType.Other.defaultIcon(), listOf(NewMember(name, personId))))
        }
    }

    private fun SharedSnapshot.ready(query: String): SharedUiState.Ready {
        val text = AppText.copy.shared
        val totals = overall()
        val match = query.trim().lowercase()
        val today = clock.now().toLocalDateTime(BangkokTime).date
        val groupRows = groups.map { group ->
            val net = myNet(group.id)
            val others = group.activeMembers.filterNot { group.isMe(it.id) }
            val detail = buildList {
                add(if (others.size == 1) text.home.youAnd(others.single().name) else text.home.peopleCount(group.activeMembers.size))
                currenciesOf(group.id).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                // p26's "rent repeats 1st" (#136, #185).
                repeats.firstOrNull { it.groupId == group.id && it.running(today) }?.let { add(it.shortLine()) }
            }.joinToString(" · ")
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
                amount = net.magnitude().format(),
                tone = net.tone(),
                news = newFromOthers(group).takeIf { it.isNotEmpty() }?.let { authors ->
                    authors.distinct().singleOrNull()?.let { text.home.addedSince(group.displayName(it), authors.size) }
                        ?: text.home.newSince(authors.size)
                },
            )
        }.filter { match.isEmpty() || it.name.lowercase().contains(match) }
        val peopleRows = peopleBalances().map { person ->
            SharedPersonRowUiModel(
                key = person.key,
                name = person.name,
                initial = person.name.trim().firstOrNull()?.uppercase() ?: "?",
                word = when (person.amount.tone()) {
                    BalanceTone.Owed -> text.owesYou
                    BalanceTone.Owe -> text.youOwe
                    BalanceTone.Settled -> text.settledUp
                },
                amount = person.amount.magnitude().format(),
                tone = person.amount.tone(),
            )
        }.filter { match.isEmpty() || it.name.lowercase().contains(match) }
        return SharedUiState.Ready(
            overallLabel = when (totals.net.tone()) {
                BalanceTone.Owed -> text.home.overallOwed
                BalanceTone.Owe -> text.home.overallOwe
                BalanceTone.Settled -> text.home.allSettledUp
            },
            overall = totals.net.magnitude(),
            tone = totals.net.tone(),
            owedToYou = totals.owedToYou.format(),
            youOwe = totals.youOwe.format(),
            groups = groupRows,
            people = peopleRows,
            knownPeople = people.filterNot { it.archived },
            query = query,
        )
    }
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

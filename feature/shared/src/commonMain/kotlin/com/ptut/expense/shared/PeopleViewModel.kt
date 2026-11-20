package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.CreatePersonGroupUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GetPeopleBalancesUseCase
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.PersonSummary
import com.ptut.expense.domain.shared.SetPersonArchivedUseCase
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One person (Sky p31): their groups and one balance across them, one row per money those groups
 * count in (#48). [personId] null is a name only.
 */
data class PeopleRowUiModel(
    val key: String,
    val personId: String?,
    val name: String,
    val initial: String,
    val detail: String,
    val word: String,
    val amount: String,
    val tone: BalanceTone,
    val archived: Boolean,
)

data class PeopleUiState(
    val rows: List<PeopleRowUiModel> = emptyList(),
    val knownPeople: List<Person> = emptyList(),
    val query: String = "",
)

/**
 * #141: everyone you split with, behind "People >" on Shared. A person with history is archived,
 * never deleted: they fade, keep their past splits, and join no new one (#56, #133).
 */
class PeopleViewModel(
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val getPeopleBalances: GetPeopleBalancesUseCase,
    private val setPersonArchived: SetPersonArchivedUseCase,
    private val createPersonGroup: CreatePersonGroupUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<PeopleUiState> = combine(observeSnapshot(), query) { snapshot, query ->
        val people = getPeopleBalances(snapshot, query)
        PeopleUiState(rows = people.rows.map { it.row() }, knownPeople = people.knownPeople, query = query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), PeopleUiState())

    fun search(text: String) {
        query.value = text
    }

    fun setArchived(personId: String, archived: Boolean) {
        viewModelScope.launch { setPersonArchived(personId, archived) }
    }

    /** The dashed ADD PERSON row: a one-off bill, a group of two named after them, like Shared's. */
    fun addPerson(name: String, personId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(createPersonGroup(name, personId, GroupType.Other.defaultIcon())) }
    }

    private fun PersonSummary.row(): PeopleRowUiModel {
        val text = AppText.copy.shared
        return when (this) {
            is PersonSummary.InGroups -> PeopleRowUiModel(
                key = "${balance.key}:${balance.currency}",
                personId = person?.id,
                name = balance.name,
                initial = initialOf(balance.name),
                detail = if (archived) text.person.archived else groupNames.joinToString(", "),
                word = balance.amount.tone().personWord(),
                amount = balance.amount.magnitude().format(Currencies.of(balance.currency).toHomeMoney()),
                tone = balance.amount.tone(),
                archived = archived,
            )

            is PersonSummary.InNoGroup -> PeopleRowUiModel(
                key = person.id,
                personId = person.id,
                name = person.name,
                initial = initialOf(person.name),
                detail = if (person.archived) text.person.archived else text.person.inNoGroup,
                word = BalanceTone.Settled.personWord(),
                amount = Satang.Zero.format(Home.money),
                tone = BalanceTone.Settled,
                archived = person.archived,
            )
        }
    }
}

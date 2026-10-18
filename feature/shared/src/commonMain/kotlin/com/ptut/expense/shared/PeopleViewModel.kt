package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.peopleBalances
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One person (Sky p31): their groups and one balance across them. [personId] null is a name only. */
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
class PeopleViewModel(private val repository: SharedRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<PeopleUiState> = combine(repository.snapshot, query) { snapshot, query ->
        PeopleUiState(rows = snapshot.rows(query), knownPeople = snapshot.people.filterNot { it.archived }, query = query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    fun search(text: String) {
        query.value = text
    }

    fun setArchived(personId: String, archived: Boolean) {
        viewModelScope.launch { repository.setPersonArchived(personId, archived) }
    }

    /** The dashed ADD PERSON row: a one-off bill, a group of two named after them, like Shared's. */
    fun addPerson(name: String, personId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createGroup(name.trim(), GroupType.Other, GroupType.Other.defaultIcon(), listOf(NewMember(name, personId))))
        }
    }

    private fun SharedSnapshot.rows(query: String): List<PeopleRowUiModel> {
        val text = AppText.copy.shared
        val byId = people.associateBy { it.id }
        val balances = peopleBalances()
        val withGroups = balances.map { balance ->
            val person = byId[balance.key]
            val archived = person?.archived == true
            PeopleRowUiModel(
                key = balance.key,
                personId = person?.id,
                name = balance.name,
                initial = balance.name.initial(),
                detail = if (archived) text.person.archived else balance.groupIds.mapNotNull { group(it)?.name }.distinct().joinToString(", "),
                word = balance.amount.word(),
                amount = balance.amount.magnitude().format(),
                tone = balance.amount.tone(),
                archived = archived,
            )
        }
        // A person made in a group that was later forgotten (#187) still belongs in the list.
        val alone = people.filter { person -> balances.none { it.key == person.id } }.map { person ->
            PeopleRowUiModel(
                key = person.id,
                personId = person.id,
                name = person.name,
                initial = person.name.initial(),
                detail = if (person.archived) text.person.archived else text.person.inNoGroup,
                word = Satang.Zero.word(),
                amount = Satang.Zero.format(),
                tone = BalanceTone.Settled,
                archived = person.archived,
            )
        }
        val match = query.trim().lowercase()
        return (withGroups + alone)
            .filter { match.isEmpty() || it.name.lowercase().contains(match) }
            .sortedBy { it.archived }
    }

    private fun Satang.word(): String {
        val text = AppText.copy.shared
        return when (tone()) {
            BalanceTone.Owed -> text.owesYou
            BalanceTone.Owe -> text.youOwe
            BalanceTone.Settled -> text.settledUp
        }
    }

    private fun String.initial(): String = trim().firstOrNull()?.uppercase() ?: "?"
}

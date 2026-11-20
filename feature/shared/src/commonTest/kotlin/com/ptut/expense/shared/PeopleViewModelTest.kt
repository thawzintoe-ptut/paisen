package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.CreatePersonGroupUseCase
import com.ptut.expense.domain.shared.GetPeopleBalancesUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.SetPersonArchivedUseCase
import com.ptut.expense.domain.shared.SharedSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PeopleViewModelTest : ViewModelTest() {

    /** #48: one person in a baht group and a kyat group is two rows, each naming its own groups. */
    @Test
    fun `a person in groups of two moneys gets a row per money`() = runTest {
        val repository = FakeSharedRepository()
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(flat12, yangonTrip),
            people = emptyList(),
            expenseVersions = listOf(flat12.dinner(paidBy = "aye", wholeMinor = 48_000), yangonTrip.dinner(paidBy = "me2", wholeMinor = 48_000)),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = PeopleViewModel(
            observeSnapshot = ObserveSharedSnapshotUseCase(repository),
            getPeopleBalances = GetPeopleBalancesUseCase(),
            setPersonArchived = SetPersonArchivedUseCase(repository),
            createPersonGroup = CreatePersonGroupUseCase(repository),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val rows = viewModel.uiState.value.rows
        assertEquals(listOf("name:aye:MMK", "name:aye:THB"), rows.map { it.key })
        assertEquals(listOf("Yangon trip" to "K24,000", "Flat 12" to "฿240.00"), rows.map { it.detail to it.amount })
        assertEquals(listOf(BalanceTone.Owed, BalanceTone.Owe), rows.map { it.tone })
    }
}

package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.SharedSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SharedViewModelTest : ViewModelTest() {

    /** #48: ฿240 and K24,000 are not ฿24,240, so the home shows one figure per money, the home's first. */
    @Test
    fun `a baht group and a kyat group give two overall lines and two rows for the same person`() = runTest {
        val repository = FakeSharedRepository()
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(yangonTrip, flat12),
            people = emptyList(),
            expenseVersions = listOf(flat12.dinner(paidBy = "me", wholeMinor = 48_000), yangonTrip.dinner(paidBy = "me2", wholeMinor = 48_000)),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = SharedViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val ready = assertIs<SharedUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("THB" to "฿240.00", "MMK" to "K24,000"), ready.overall.map { it.money.code to it.owedToYou })
        assertEquals(listOf("Overall, you are owed", "Overall, you are owed"), ready.overall.map { it.label })
        assertEquals(listOf("Aye" to "K24,000", "Aye" to "฿240.00"), ready.people.map { it.name to it.amount })
    }
}

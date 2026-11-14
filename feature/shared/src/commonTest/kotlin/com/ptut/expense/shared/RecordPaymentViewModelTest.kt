package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordPaymentViewModelTest : ViewModelTest() {

    /** #6: the second tap lands while the first payment is still writing. */
    @Test
    fun `two fast taps on Record add one payment`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = RecordPaymentViewModel(groupId = "g1", fromMemberId = "aye", toMemberId = "me", repository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        runCurrent()

        var closed = 0
        viewModel.record { closed++ }
        viewModel.record { closed++ }
        runCurrent()
        repository.gate.complete(Unit)
        runCurrent()

        assertEquals(1, repository.payments.size)
        assertEquals(1, closed)
    }
}

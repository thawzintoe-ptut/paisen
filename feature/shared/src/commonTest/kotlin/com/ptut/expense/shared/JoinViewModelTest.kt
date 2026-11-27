package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.GetSyncAccessUseCase
import com.ptut.expense.domain.shared.JoinGroupUseCase
import com.ptut.expense.domain.shared.OpenInviteUseCase
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JoinViewModelTest : ViewModelTest() {

    @Test
    fun `an invite in a build without sync says there is nothing to join and asks no sign-in`() = runTest {
        val viewModel = JoinViewModel("ABCDEFGHJ", GetSyncAccessUseCase(), OpenInviteUseCase(), JoinGroupUseCase())

        viewModel.retry()
        viewModel.pick(memberId = null)
        runCurrent()

        assertEquals(JoinUiState.Unavailable, viewModel.uiState.value)
    }
}

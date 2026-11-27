package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.GetSyncAccessUseCase
import com.ptut.expense.domain.shared.JoinGroupUseCase
import com.ptut.expense.domain.shared.OpenInviteUseCase
import com.ptut.expense.sync.JoinException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JoinViewModelTest : ViewModelTest() {

    @Test
    fun `an invite in a build without sync says there is nothing to join and asks no sign-in`() = runTest {
        val viewModel = JoinViewModel("ABCDEFGHJ", GetSyncAccessUseCase(), OpenInviteUseCase(), JoinGroupUseCase())

        viewModel.retry()
        viewModel.pick(memberId = null)
        runCurrent()

        assertEquals(JoinUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `a signed-in phone sees the group's names and joins as the one it picks`() = runTest {
        val sync = FakeSync()
        val viewModel = joinViewModel(sync)
        runCurrent()

        val choose = assertIs<JoinUiState.Choose>(viewModel.uiState.value)
        assertEquals(listOf(JoinNameUiModel("m2", "Aye")), choose.names)

        viewModel.pick("m2")
        runCurrent()

        assertEquals(listOf<String?>("m2"), sync.joins)
        assertEquals(JoinUiState.Done("g1"), viewModel.uiState.value)
    }

    @Test
    fun `two fast taps on a name claim it once`() = runTest {
        val sync = FakeSync().apply { joinGate = CompletableDeferred() }
        val viewModel = joinViewModel(sync)
        runCurrent()

        viewModel.pick("m2")
        viewModel.pick("m2")
        runCurrent()
        sync.joinGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf<String?>("m2"), sync.joins)
    }

    @Test
    fun `a signed-out phone asks to sign in and opens the invite once it has`() = runTest {
        val sync = FakeSync(signedIn = false)
        val viewModel = joinViewModel(sync)
        runCurrent()
        assertEquals(JoinUiState.NeedsSignIn, viewModel.uiState.value)

        sync.signedInNow.value = true
        runCurrent()

        assertIs<JoinUiState.Choose>(viewModel.uiState.value)
    }

    @Test
    fun `an invite that cannot open says why and joins nothing`() = runTest {
        val sync = FakeSync().apply { choice = Result.failure(JoinException("This invite has expired.")) }
        val viewModel = joinViewModel(sync)
        runCurrent()

        assertIs<JoinUiState.Failed>(viewModel.uiState.value)
        viewModel.pick("m2")
        runCurrent()
        assertEquals(emptyList(), sync.joins)
    }

    private fun joinViewModel(sync: FakeSync) =
        JoinViewModel("ABCDEFGHJ", GetSyncAccessUseCase(sync), OpenInviteUseCase(sync), JoinGroupUseCase(sync))
}

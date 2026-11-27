package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.DeleteAccountUseCase
import com.ptut.expense.domain.shared.ObserveAccountUseCase
import com.ptut.expense.domain.shared.SignInUseCase
import com.ptut.expense.domain.shared.SignOutUseCase
import com.ptut.expense.domain.shared.SyncAllNowUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.auth.ProviderToken
import com.ptut.expense.sync.auth.SignInCancelled
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AccountViewModelTest : ViewModelTest() {

    @Test
    fun `a signed-in account shows its email and provider and signs out`() = runTest {
        val auth = FakeAuth(signedIn = true)
        val sync = FakeSync()
        val viewModel = accountViewModel(auth, sync)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals("thaw@example.com · Google", assertIs<AccountUiState.SignedIn>(viewModel.uiState.value).line)

        viewModel.signOut()
        runCurrent()

        assertEquals(1, sync.signOuts)
        assertEquals(null, assertIs<AccountUiState.SignedIn>(viewModel.uiState.value).error)
    }

    @Test
    fun `sign-out with changes not sent yet says how many and keeps the account`() = runTest {
        val sync = FakeSync().apply { signOutFailure = ChangesWaiting(2) }
        val viewModel = accountViewModel(FakeAuth(signedIn = true), sync)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.signOut()
        runCurrent()

        assertEquals(AppText.copy.shared.account.notSentYet(2), assertIs<AccountUiState.SignedIn>(viewModel.uiState.value).error)
    }

    @Test
    fun `signing in closes the sheet and a cancelled sheet says nothing`() = runTest {
        val auth = FakeAuth()
        val viewModel = SignInViewModel(SignInUseCase(auth))
        var signedIn = 0

        viewModel.signIn({ throw SignInCancelled() }) { signedIn++ }
        runCurrent()
        assertEquals(SignInUiState(), viewModel.uiState.value)

        viewModel.signIn({ ProviderToken("google.com", "id") }) { signedIn++ }
        runCurrent()

        assertEquals(1, signedIn)
        assertEquals(SignInUiState(), viewModel.uiState.value)
    }

    @Test
    fun `a sign-in with no network says so and keeps the sheet open`() = runTest {
        val auth = FakeAuth().apply { answer = { throw IllegalStateException("Unable to resolve host") } }
        val viewModel = SignInViewModel(SignInUseCase(auth))
        var signedIn = 0

        viewModel.signIn({ ProviderToken("google.com", "id") }) { signedIn++ }
        runCurrent()

        assertEquals(0, signedIn)
        assertEquals(AppText.copy.shared.sync.offline, viewModel.uiState.value.error)
    }

    private fun accountViewModel(auth: FakeAuth, sync: FakeSync) = AccountViewModel(
        observeAccount = ObserveAccountUseCase(auth),
        signOutUseCase = SignOutUseCase(auth, sync),
        deleteAccountUseCase = DeleteAccountUseCase(auth, sync),
        syncAllNow = SyncAllNowUseCase(sync),
    )
}

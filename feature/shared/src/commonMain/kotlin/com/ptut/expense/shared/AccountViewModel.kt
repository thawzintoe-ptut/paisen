package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncProblem
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.syncMessage
import com.ptut.expense.sync.syncProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AccountUiState {
    /** This build names no Firebase project: Settings shows no account at all. */
    data object Hidden : AccountUiState

    data object SignedOut : AccountUiState

    data class SignedIn(
        val line: String,
        val working: Boolean = false,
        val error: String? = null,
        /** Firebase deletes only after a recent sign-in, so the reader signs in once more. */
        val needsFreshSignIn: Boolean = false,
    ) : AccountUiState
}

/** #187: who this phone shares as, sign out, and Apple 5.1.1(v)'s in-app account deletion. */
class AccountViewModel(private val auth: AuthRepository?, private val sync: SharedSync?) : ViewModel() {

    private data class Work(val working: Boolean = false, val error: String? = null, val needsFreshSignIn: Boolean = false)

    private val work = MutableStateFlow(Work())

    val uiState: StateFlow<AccountUiState> = combine(auth?.account ?: flowOf(Account.SignedOut), work) { account, work ->
        when {
            auth == null || !auth.available -> AccountUiState.Hidden

            account is Account.SignedIn -> AccountUiState.SignedIn(
                line = "${account.email ?: account.name}${Separators.DOT}${if (account.provider == APPLE_PROVIDER) "Apple" else "Google"}",
                working = work.working,
                error = work.error,
                needsFreshSignIn = work.needsFreshSignIn,
            )

            else -> AccountUiState.SignedOut
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), AccountUiState.Hidden)

    /** Shared groups leave this phone with the account and come back with it; your own spending stays. */
    fun signOut() = runAccountAction {
        if (sync != null) sync.signOut() else auth?.signOut()
    }

    fun deleteAccount() = runAccountAction {
        if (sync != null) sync.deleteAccount() else auth?.deleteAccount()
    }

    /** Right after signing in here, so the account's shared groups come back now, not at the next app open. */
    fun syncNow() {
        viewModelScope.launch { runSuspendCatching { sync?.syncAll() } }
    }

    fun dismissFreshSignIn() {
        work.value = Work()
    }

    private fun runAccountAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            work.value = Work(working = true)
            work.value = try {
                block()
                Work()
            } catch (waiting: ChangesWaiting) {
                Work(error = AppText.copy.shared.account.notSentYet(waiting.count))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure.syncProblem() == SyncProblem.NeedsFreshSignIn) {
                    Work(needsFreshSignIn = true)
                } else {
                    Work(error = failure.syncMessage())
                }
            }
        }
    }
}

/** Firebase's provider id for Sign in with Apple; any other provider here is Google. */
private const val APPLE_PROVIDER = "apple.com"

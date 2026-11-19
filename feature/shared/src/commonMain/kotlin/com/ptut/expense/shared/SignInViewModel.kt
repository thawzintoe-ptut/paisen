package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.SyncProblem
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.ProviderToken
import com.ptut.expense.sync.auth.SignInCancelled
import com.ptut.expense.sync.syncProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SignInUiState(val working: Boolean = false, val error: String? = null)

/** #187: turns the phone's Google or Apple token into a Firebase account. */
class SignInViewModel(private val auth: AuthRepository?) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = state

    fun signIn(obtain: suspend () -> ProviderToken, onSignedIn: () -> Unit) {
        val auth = auth ?: return
        viewModelScope.launch {
            state.value = SignInUiState(working = true)
            state.value = try {
                auth.signIn(obtain())
                onSignedIn()
                SignInUiState()
            } catch (cancelled: SignInCancelled) {
                SignInUiState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val text = AppText.copy.shared
                SignInUiState(error = if (failure.syncProblem() == SyncProblem.Offline) text.sync.offline else text.signInSheet.didNotFinish)
            }
        }
    }
}

package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.shared.SignInResult
import com.ptut.expense.domain.shared.SignInUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.auth.ProviderToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SignInUiState(val working: Boolean = false, val error: String? = null)

/** #187: turns the phone's Google or Apple token into a Firebase account. */
class SignInViewModel(private val signInUseCase: SignInUseCase) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = state

    fun signIn(obtain: suspend () -> ProviderToken, onSignedIn: () -> Unit) {
        viewModelScope.launch {
            state.value = SignInUiState(working = true)
            val text = AppText.copy.shared
            state.value = when (signInUseCase(obtain)) {
                SignInResult.SignedIn -> {
                    onSignedIn()
                    SignInUiState()
                }

                SignInResult.Cancelled -> SignInUiState()

                SignInResult.Offline -> SignInUiState(error = text.sync.offline)

                SignInResult.Failed -> SignInUiState(error = text.signInSheet.didNotFinish)
            }
        }
    }
}

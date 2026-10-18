package com.ptut.expense.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.sync.SyncProblem
import com.ptut.expense.sync.auth.AppleSignInButton
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.ProviderToken
import com.ptut.expense.sync.auth.SignInCancelled
import com.ptut.expense.sync.auth.rememberSignInLauncher
import com.ptut.expense.sync.sharedDataRegion
import com.ptut.expense.sync.syncProblem
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

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
            } catch (failure: Exception) {
                val text = AppText.copy.shared
                SignInUiState(error = if (failure.syncProblem() == SyncProblem.Offline) text.sync.offline else text.signInSheet.didNotFinish)
            }
        }
    }
}

/**
 * #187 and #191: asked only when someone shares a group or opens an invite. The privacy notice
 * comes before the first sign-in, so nobody signs in without knowing what leaves the phone.
 */
@Composable
fun SignInSheet(reason: String, onSignedIn: () -> Unit, onDismiss: () -> Unit) {
    val viewModel = koinViewModel<SignInViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberSignInLauncher()
    val text = LocalCopy.current.shared.signInSheet
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        SkyNoticeCard(
            title = text.leavesTitle,
            text = text.leavesBody(sharedDataRegion),
            icon = PtutIcon.Lock.image,
            modifier = Modifier.padding(top = Spacing.md),
        )
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        }
        if (state.working) {
            Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SkyProgress()
                Text(text.signingIn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SkyButton(
            text.google,
            onClick = { viewModel.signIn(launcher::google, onSignedIn) },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
        if (launcher.offersApple) {
            AppleSignInButton(
                onClick = { viewModel.signIn(launcher::apple, onSignedIn) },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
        }
    }
}

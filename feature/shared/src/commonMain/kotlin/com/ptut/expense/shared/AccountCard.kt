package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.syncMessage
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyTextAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

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
                line = "${account.email ?: account.name} · ${if (account.provider == "apple.com") "Apple" else "Google"}",
                working = work.working,
                error = work.error,
                needsFreshSignIn = work.needsFreshSignIn,
            )

            else -> AccountUiState.SignedOut
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState.Hidden)

    /** Shared groups leave this phone with the account and come back with it; your own spending stays. */
    fun signOut() = run {
        if (sync != null) sync.signOut() else auth?.signOut()
    }

    fun deleteAccount() = run {
        if (sync != null) sync.deleteAccount() else auth?.deleteAccount()
    }

    /** Right after signing in here, so the account's shared groups come back now, not at the next app open. */
    fun syncNow() {
        viewModelScope.launch { runCatching { sync?.syncAll() } }
    }

    fun dismissFreshSignIn() {
        work.value = Work()
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            work.value = Work(working = true)
            work.value = try {
                block()
                Work()
            } catch (waiting: ChangesWaiting) {
                Work(error = AppText.copy.shared.account.notSentYet(waiting.count))
            } catch (failure: Exception) {
                if (failure.message.orEmpty().contains(TOO_OLD)) {
                    Work(needsFreshSignIn = true)
                } else {
                    Work(error = failure.syncMessage())
                }
            }
        }
    }

    private companion object {
        const val TOO_OLD = "CREDENTIAL_TOO_OLD"
    }
}

/** Settings' account card (#187). Absent when the build has no Firebase project. */
@Composable
fun AccountCard(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<AccountViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val text = LocalCopy.current
    val words = text.shared.account
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val current = state
    if (current == AccountUiState.Hidden) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r22)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
    ) {
        Text(words.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        when (current) {
            is AccountUiState.SignedIn -> {
                Text(words.signedInAs(current.line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                current.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                }
                // Two quiet text actions: neither is something a person does often, and delete asks again.
                // Offset by the action's own inset, so the words line up with the line above.
                Row(
                    modifier = Modifier.padding(top = Spacing.xs).offset(x = -Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SkyTextAction(label = words.signOut, onClick = { sheet = SHEET_SIGN_OUT }, enabled = !current.working)
                    SkyTextAction(
                        label = words.deleteAccount,
                        onClick = { sheet = SHEET_DELETE },
                        enabled = !current.working,
                        ink = MaterialTheme.ptut.warnInk,
                    )
                }
            }

            else -> {
                Text(
                    words.notSignedIn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SkyButton(text.shared.signIn, onClick = { sheet = SHEET_SIGN_IN }, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
            }
        }
    }

    val freshSignIn = (current as? AccountUiState.SignedIn)?.needsFreshSignIn == true
    when {
        freshSignIn -> SignInSheet(
            reason = words.freshSignIn,
            onSignedIn = viewModel::deleteAccount,
            onDismiss = viewModel::dismissFreshSignIn,
        )

        sheet == SHEET_SIGN_IN -> SignInSheet(
            reason = words.signInReason,
            onSignedIn = {
                sheet = null
                viewModel.syncNow()
            },
            onDismiss = { sheet = null },
        )

        sheet == SHEET_DELETE -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(words.deleteAsk, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                words.deleteBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SkyButton(
                words.deleteAccount,
                onClick = {
                    sheet = null
                    viewModel.deleteAccount()
                },
                kind = SkyButtonKind.Danger,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            )
            SkyButton(text.common.keepIt, onClick = { sheet = null }, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
        }

        // #218: asked first. Sign-out still sends what waits, and stops rather than lose it.
        sheet == SHEET_SIGN_OUT -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(words.signOutAsk, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                words.signOutBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SkyButton(
                words.signOut,
                onClick = {
                    sheet = null
                    viewModel.signOut()
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            )
            SkyButton(text.common.cancel, onClick = { sheet = null }, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
        }
    }
}

/** Shown once, after a newer sign-in on another phone signed this one out. */
@Composable
fun SignedOutElsewhereSheet(onDismiss: () -> Unit) {
    val text = LocalCopy.current
    val words = text.shared.account
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(words.endedTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(
            words.endedBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(text.common.done, onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
    }
}

private const val SHEET_SIGN_IN = "sign-in"
private const val SHEET_DELETE = "delete"
private const val SHEET_SIGN_OUT = "sign-out"

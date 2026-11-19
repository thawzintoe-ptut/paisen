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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyTextAction
import org.koin.compose.viewmodel.koinViewModel

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

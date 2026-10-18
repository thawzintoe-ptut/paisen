package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.notify.rememberNotificationAsk
import com.ptut.expense.sync.push.pushConfigured
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyProgress
import com.ptut.expense.ui.sky.SkyTopBar

/** #190: an opened invite. Sign in, then "Which one are you?" from the names nobody has claimed. */
@Composable
fun JoinView(
    uiState: JoinUiState,
    onBack: () -> Unit,
    onPick: (memberId: String?) -> Unit,
    onOpenGroup: () -> Unit,
    onRetry: () -> Unit,
    onJoined: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current
    val words = text.shared.join
    var signIn by rememberSaveable { mutableStateOf(uiState == JoinUiState.NeedsSignIn) }
    // #218: a name is someone's balances, so a mis-tap asks before it claims one.
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    val ask = rememberNotificationAsk()
    LaunchedEffect(uiState) {
        if (uiState !is JoinUiState.Done) return@LaunchedEffect
        // The others' changes reach this phone as notifications, so joining is when the app asks.
        if (pushConfigured) ask.request()
        onJoined(uiState.groupId)
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SkyTopBar(title = words.title, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            when (uiState) {
                JoinUiState.Unavailable -> SkyIntro(words.unavailable)

                JoinUiState.NeedsSignIn -> {
                    SkyIntro(words.signInFirst)
                    SkyButton(text.shared.signIn, onClick = { signIn = true }, modifier = Modifier.fillMaxWidth())
                }

                JoinUiState.Loading, is JoinUiState.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SkyProgress()
                    SkyIntro(words.opening)
                }

                is JoinUiState.Choose -> {
                    Text(words.whichOne, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    SkyIntro(words.pickYours(uiState.groupName))
                    uiState.names.forEach { name ->
                        NameRow(name.name, enabled = !uiState.joining, onClick = { asking = name.memberId })
                    }
                    SkyButton(
                        words.notOnList,
                        onClick = { onPick(null) },
                        kind = SkyButtonKind.Outline,
                        enabled = !uiState.joining,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    )
                }

                is JoinUiState.AlreadyIn -> {
                    Text(words.alreadyIn(uiState.groupName), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    SkyIntro(words.joinedAs(uiState.memberName))
                    SkyButton(words.openGroup, onClick = onOpenGroup, enabled = !uiState.opening, modifier = Modifier.fillMaxWidth())
                }

                is JoinUiState.Failed -> {
                    SkyIntro(uiState.message)
                    SkyButton(text.common.tryAgain, onClick = onRetry, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (signIn && uiState == JoinUiState.NeedsSignIn) {
        SignInSheet(
            reason = words.signInReason,
            onSignedIn = { signIn = false },
            onDismiss = { signIn = false },
        )
    }
    val picked = (uiState as? JoinUiState.Choose)?.names?.firstOrNull { it.memberId == asking }
    if (picked != null) {
        SkyBottomSheet(onDismissRequest = { asking = null }) {
            Text(words.joinAsAsk(picked.name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                words.joinAsBody(picked.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SkyButton(
                words.joinAs(picked.name),
                onClick = {
                    asking = null
                    onPick(picked.memberId)
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            )
            SkyButton(words.pickAgain, onClick = { asking = null }, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
        }
    }
}

@Composable
private fun NameRow(name: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PtutRadii.r22)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .tappable(PtutRadii.r22, Role.Button, enabled, onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = SkySizes.transactionRow)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        MemberAvatar(label = name.trim().firstOrNull()?.uppercase() ?: "?", isMe = false)
        Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

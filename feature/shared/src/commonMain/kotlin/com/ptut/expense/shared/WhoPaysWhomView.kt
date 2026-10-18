package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkySwitch
import com.ptut.expense.ui.sky.SkyTopBar

/** #184 (p30): the count first, then one card per payment, then the Simplify debts switch. */
@Composable
fun WhoPaysWhomView(
    uiState: WhoPaysWhomUiState,
    onBack: () -> Unit,
    onSimplify: (Boolean) -> Unit,
    onSettle: (from: String, to: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.shared
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SkyTopBar(title = text.whoPaysWhom, onBack = onBack)
        val state = uiState as? WhoPaysWhomUiState.Loaded ?: return@Column GroupGone(status = text.detail.goneStatus)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = Spacing.md).padding(bottom = Spacing.md)) {
                Text(text.settle.toSettle(state.groupName), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.headline,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                if (state.simplify && state.pairwiseCount > state.simplifiedCount) {
                    Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text.settle.between(state.pairwiseCount),
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(PtutIcon.ChevronRight.image, contentDescription = text.settle.then, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.cardGlyph))
                        Text(text.settle.afterSimplifying(state.simplifiedCount), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Column(Modifier.padding(horizontal = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                Spacer(Modifier.height(Spacing.xs))
                state.cards.forEach { card -> PaymentCard(card, onClick = { onSettle(card.fromId, card.toId) }) }
                state.currencyNote?.let { SkyNoticeCard(text = it, icon = PtutIcon.Info.image) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PtutRadii.r22)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text.settle.simplify, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    SkySwitch(checked = state.simplify, onCheckedChange = onSimplify, label = text.settle.simplify)
                }
            }
        }
        state.firstMine?.let { card ->
            SkyButton(
                text.settle.settleAPayment,
                onClick = { onSettle(card.fromId, card.toId) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
            )
        }
    }
}

/** p30: payer, a dashed line and an arrow, payee, then the sentence and the amount. */
@Composable
private fun PaymentCard(card: PaymentCardUiModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PtutRadii.r22)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .tappable(PtutRadii.r22, Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = SkySizes.transactionRow)
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MemberAvatar(label = card.fromLabel, isMe = card.fromIsMe)
        SkyHairline(Modifier.weight(1f))
        Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.cardGlyph))
        MemberAvatar(label = card.toLabel, isMe = card.toIsMe)
        Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
            Text(card.sentence, style = MaterialTheme.typography.bodySmall, color = card.tone.ink(), maxLines = 1)
            Text(card.amount, style = MaterialTheme.typography.titleMedium, color = card.tone.ink())
        }
    }
}

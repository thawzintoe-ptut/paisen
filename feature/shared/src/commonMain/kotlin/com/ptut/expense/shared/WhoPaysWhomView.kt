package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.share.encodePngOffMain
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkySwitch
import com.ptut.expense.ui.sky.SkyTopBar
import kotlinx.coroutines.launch

/** #184 (p30): the count first, then one card per payment, then the Simplify debts switch. */
@Composable
fun WhoPaysWhomView(
    uiState: WhoPaysWhomUiState,
    onBack: () -> Unit,
    onSimplify: (Boolean) -> Unit,
    onSettle: (from: String, to: String) -> Unit,
    modifier: Modifier = Modifier,
    /** #93: "Settle my part", with the plan's amount. */
    onSettleMine: (from: String, to: String, amount: String) -> Unit = { _, _, _ -> },
    /** The "Pay me" card. Off until the shell turns it on. */
    offerPayMe: Boolean = false,
    onPromptPay: (String) -> Boolean = { false },
) {
    val text = LocalCopy.current.shared
    // The payer whose card is open. Read from the state each time, so a saved ID turns the ID sheet into the card.
    var askingFrom by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SkyTopBar(title = text.whoPaysWhom, onBack = onBack)
        val state = uiState as? WhoPaysWhomUiState.Loaded ?: return@Column GroupGone(status = text.detail.goneStatus)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            WhoPaysWhomContent(state, onSimplify = onSimplify, onSettle = onSettle, onSettleMine = onSettleMine) { card ->
                if (offerPayMe && card.toIsMe) {
                    SkyButton(text.settle.askToPay(card.fromName), onClick = { askingFrom = card.fromId }, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        state.cards.firstOrNull { it.toIsMe && it.fromId == askingFrom }?.let { asked ->
            val card = asked.payMe
            if (card == null) {
                PromptPaySheet(current = null, onSave = onPromptPay, onDismiss = { askingFrom = null })
            } else {
                PayMeSheet(card, onDismiss = { askingFrom = null })
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

/** p30's count, cards and Simplify switch; also the group screen's Balances tab (#64). */
@Composable
internal fun WhoPaysWhomContent(
    state: WhoPaysWhomUiState.Loaded,
    onSimplify: (Boolean) -> Unit,
    onSettle: (from: String, to: String) -> Unit,
    modifier: Modifier = Modifier,
    onSettleMine: (from: String, to: String, amount: String) -> Unit = { _, _, _ -> },
    belowCard: @Composable (PaymentCardUiModel) -> Unit = {},
) {
    val text = LocalCopy.current.shared
    Column(modifier) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = Spacing.md).padding(bottom = Spacing.md)) {
            Text(text.settle.toSettle(state.groupName), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.headline,
                style = MaterialTheme.ptutType.pageTitle,
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
            state.plan?.let { plan -> SettlePlan(plan, onSettleMine) }
            // #93: with the plan on, my part leads; the rest keep the order the group settles them in.
            (state.plan?.cards ?: state.cards).forEach { card ->
                PaymentCard(card, onClick = { onSettle(card.fromId, card.toId) }, badge = state.plan?.badge?.takeIf { card == state.plan.myPart })
                belowCard(card)
            }
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
}

/** #93 (p14): how few payments settle everyone, and "Settle my part" with the plan's amount. */
@Composable
private fun SettlePlan(plan: SettlePlanUiModel, onSettleMine: (from: String, to: String, amount: String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(PtutRadii.r20).background(MaterialTheme.colorScheme.tertiaryContainer).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.size(PlaySizes.aiDisc).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("P", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(plan.headline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        Text(plan.savesLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val mine = plan.myPart
        val amount = plan.myAmount
        if (mine != null && amount != null) {
            SkyButton(
                plan.settleMyPart,
                onClick = { onSettleMine(mine.fromId, mine.toId, amount) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}

/** p30: payer, a dashed line and an arrow, payee, then the sentence and the amount. */
@Composable
private fun PaymentCard(card: PaymentCardUiModel, onClick: () -> Unit, badge: String? = null) {
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
        SkyHairline(Modifier.weight(0.4f))
        Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.cardGlyph))
        MemberAvatar(label = card.toLabel, isMe = card.toIsMe)
        // Burmese sentences run long ("hgyugtyu က sheilarzune ကို ပေးမယ်"): two lines, never cut.
        Column(Modifier.weight(1.6f), horizontalAlignment = Alignment.End) {
            Text(card.sentence, style = MaterialTheme.typography.bodySmall, color = card.tone.ink(), maxLines = 2, textAlign = TextAlign.End)
            Text(card.amount, style = MaterialTheme.typography.titleMedium, color = card.tone.ink())
            // #93: ruby, because it is money going out (R3).
            badge?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.ptut.onSignature,
                    modifier = Modifier.padding(top = Spacing.xxs).clip(CircleShape).background(MaterialTheme.ptut.signature).padding(horizontal = Spacing.xs, vertical = Spacing.s2),
                )
            }
        }
    }
}

/** The card as it will arrive, then the share sheet. What is drawn here is the picture that is sent. */
@Composable
private fun PayMeSheet(card: PayMeCard, onDismiss: () -> Unit) {
    val text = LocalCopy.current.shared.settle
    val banks = rememberBankApps()
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    SkyBottomSheet(onDismissRequest = onDismiss) {
        PayMeCardPicture(
            card,
            modifier = Modifier
                .padding(top = Spacing.sm)
                .align(Alignment.CenterHorizontally)
                .clip(PtutRadii.r22)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        )
        SkyButton(
            text.sendCard,
            onClick = { scope.launch { banks.sharePng(layer.toImageBitmap().encodePngOffMain(), card.shareText) } },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

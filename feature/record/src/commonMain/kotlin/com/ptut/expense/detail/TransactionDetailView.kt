package com.ptut.expense.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.SlipDropArt
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.categoryTint
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.Copy
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.rememberScreenReader
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyCheckbox
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyRollingAmount
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * The record in Sky (p16): the category in its circle, the shop and the signed amount, four rows,
 * the "Why" card, then DELETE beside EDIT. Delete asks on a sheet (p17, #147 R9), on both platforms.
 *
 * Kept beyond the artboard (#146): the notices that say the slip image is gone, which totals move and that
 * the shop rule stays (K16), the shop-rule screen behind the Why card (K15), the undo toast and the
 * pop after a delete, the Missing state and one spoken amount.
 */
@Composable
fun TransactionDetailView(
    uiState: TransactionDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAskToDelete: () -> Unit,
    onKeepIt: () -> Unit,
    onConfirmDelete: () -> Unit,
    onOpenShopRule: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** #186: share into the picked group, open the group it is shared in, and the delete box. */
    onShare: (groupId: String) -> Unit = {},
    onOpenSharedGroup: (groupId: String) -> Unit = {},
    onSetAlsoRemove: (Boolean) -> Unit = {},
) {
    val text = LocalCopy.current.record
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        when (uiState) {
            TransactionDetailUiState.Loading -> SkyTopBar(title = "", onBack = onBack)

            TransactionDetailUiState.Missing -> Column {
                SkyTopBar(title = "", onBack = onBack)
                SkyEmptyState(
                    headline = text.gone,
                    body = text.goneBody,
                    status = text.goneStatus,
                    statusIcon = PtutIcon.Check.image,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
                )
            }

            is TransactionDetailUiState.Loaded -> {
                var picking by rememberSaveable { mutableStateOf(false) }
                Record(uiState, onBack, onEdit, onAskToDelete, onOpenShopRule, onShare = { picking = true }, onOpenSharedGroup = onOpenSharedGroup)
                if (uiState.confirmingDelete) {
                    DeleteSheet(state = uiState, onKeepIt = onKeepIt, onConfirmDelete = onConfirmDelete, onSetAlsoRemove = onSetAlsoRemove)
                }
                if (picking) {
                    ShareGroupSheet(
                        groups = uiState.shareGroups,
                        onPick = {
                            picking = false
                            onShare(it)
                        },
                        onDismiss = { picking = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun Record(
    state: TransactionDetailUiState.Loaded,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenShopRule: (String) -> Unit,
    onShare: () -> Unit,
    onOpenSharedGroup: (String) -> Unit,
) {
    val copy = LocalCopy.current
    val text = copy.record
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        SkyTopBar(
            title = if (state.isIncome) copy.common.income else text.purchase,
            onBack = onBack,
            trailing = { SkyBarIconAction(icon = PtutIcon.Edit, label = copy.common.edit, onClick = onEdit) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tint = categoryTint(state.categoryColor)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(top = Spacing.md).size(DETAIL_CIRCLE).clip(CircleShape).background(tint.fill),
            ) {
                Icon(ptutIcon(state.categoryIcon), contentDescription = null, tint = tint.on, modifier = Modifier.size(SkySizes.barGlyph))
            }
            Text(
                text = state.merchant,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.s12),
            )
            SignedAmount(state)

            Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                SkyHairline()
                state.rows.forEach { row -> SkyKeyValueRow(label = row.label.skyLabel(copy), value = row.value) }
            }

            // #186 (D5): a purchase already in History goes to a group from here, once.
            val sharedIn = state.sharedGroupId
            when {
                sharedIn != null -> ShareRow(label = text.sharedIn(state.sharedGroupName.orEmpty()), onClick = { onOpenSharedGroup(sharedIn) })
                state.canShare -> ShareRow(label = text.shareThisCost, onClick = onShare)
            }

            // The Why card (p16), and the only way to the shop-rule screen (#146, K15).
            state.shopRule?.let { rule ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                        .clip(PtutRadii.r16)
                        .tappable(PtutRadii.r16, Role.Button) { onOpenShopRule(rule.shop) },
                ) {
                    SkyNoticeCard(
                        title = text.whyFiled(rule.filesUnder),
                        text = rule.why,
                        icon = PtutIcon.AutoAwesome.image,
                    )
                }
            }

            // What a delete does, said before the button rather than after (K16).
            SkyNoticeCard(
                text = state.consequence,
                icon = PtutIcon.Info.image,
                modifier = Modifier.padding(top = Spacing.s12),
            )
            // Beside the other notice, on pale sky (K16): a delete never undoes the shop's sorting.
            SkyNoticeCard(
                text = state.ruleFooter,
                icon = PtutIcon.AutoAwesome.image,
                modifier = Modifier.padding(top = Spacing.s12, bottom = Spacing.md),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(label = copy.common.delete, onClick = onDelete, kind = SkyButtonKind.DangerOutline, modifier = Modifier.weight(1f))
            SkyButton(label = copy.common.edit, onClick = onEdit, modifier = Modifier.weight(1.6f))
        }
    }
}

/** "฿642.50": the symbol smaller, the satang in grey only when there are some, ruby or green by direction. */
@Composable
private fun SignedAmount(state: TransactionDetailUiState.Loaded) {
    SkyRollingAmount(
        symbol = state.amount.symbol,
        whole = state.amount.whole,
        fraction = state.amount.fraction,
        color = if (state.isIncome) MaterialTheme.ptut.ok else MaterialTheme.ptut.signatureInk,
        fractionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs).clearAndSetSemantics { contentDescription = state.spoken },
    )
}

/**
 * p17: the illustration slot, the question, the money and the totals it moves, then KEEP IT beside
 * a filled DELETE. Dismissing the sheet is keeping the row.
 */
@Composable
private fun DeleteSheet(
    state: TransactionDetailUiState.Loaded,
    onKeepIt: () -> Unit,
    onConfirmDelete: () -> Unit,
    onSetAlsoRemove: (Boolean) -> Unit,
) {
    val copy = LocalCopy.current
    val text = copy.record
    SkyBottomSheet(onDismissRequest = onKeepIt) {
        Box(
            Modifier
                .padding(top = Spacing.md)
                .fillMaxWidth()
                .aspectRatio(SkySizes.deleteIllustrationRatio)
                .clip(PtutRadii.r22)
                // p17 draws this slot paler than the empty state's: infoBg's #EEF7FD exactly.
                .background(MaterialTheme.ptut.infoBg),
        ) { SlipDropArt() }
        Text(
            text = if (state.isIncome) text.deleteIncome else text.deletePurchase,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = "${state.deleteMoves} ${text.undoUntilNext}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        // #186 (D9): ticked, the group's copy goes too; unticked, the group keeps it.
        state.sharedGroupName?.let { group ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                SkyCheckbox(checked = state.alsoRemoveFromGroup, onCheckedChange = onSetAlsoRemove, label = text.alsoRemoveFrom(group))
                Text(
                    text.alsoRemoveFrom(group),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(label = copy.common.keepIt, onClick = onKeepIt, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton(label = copy.common.delete, onClick = onConfirmDelete, kind = SkyButtonKind.Danger, modifier = Modifier.weight(1f))
        }
    }
}

/** #186: "Share this cost", or "Shared in Hanoi & Saigon >" once it is. */
@Composable
private fun ShareRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .clip(PtutRadii.r16)
            .background(MaterialTheme.colorScheme.surface)
            .tappable(PtutRadii.r16, Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = SkySizes.transactionRow)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Icon(PtutIcon.Group.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.cardGlyph))
    }
}

/** #186: which group the purchase goes to. Add expense then opens with it filled in and locked. */
@Composable
private fun ShareGroupSheet(groups: List<ShareGroupUiModel>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.record
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.shareThisCost, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        if (groups.isEmpty()) {
            Text(
                text.shareNoGroups,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        groups.forEach { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs)
                    .tappable(PtutRadii.r12, Role.Button) { onPick(group.id) }
                    .semantics(mergeDescendants = true) {}
                    .heightIn(min = SkySizes.transactionRow),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(group.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The record's row names as p16 writes them. */
private fun String.skyLabel(copy: Copy): String = when (this) {
    "CATEGORY" -> copy.common.category
    "NOTE" -> copy.common.note
    "DATE" -> copy.record.whenLabel
    "SOURCE" -> copy.record.fromLabel
    "REFERENCE" -> copy.record.referenceLabel
    else -> lowercase().replaceFirstChar { it.uppercase() }
}

private val DETAIL_CIRCLE = SkySizes.rowCircle * 1.4f

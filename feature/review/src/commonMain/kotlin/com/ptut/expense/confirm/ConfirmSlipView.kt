package com.ptut.expense.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.slip.SlipField
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.locale.SlipsCopy
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.KeyboardAmountField
import com.ptut.expense.ui.SlipPicture
import com.ptut.expense.ui.SlipPictureViewer
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyConfidenceBadge
import com.ptut.expense.ui.sky.SkyEditableKeyValueRow
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkySubChipBox
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * Confirm in Sky, "Check slip" (p13): the slip beside the amount read off it and a confidence
 * badge, three rows, the category chips, then NOT MINE beside CONFIRM.
 *
 * The number keyboard opens when the amount did not read or the amount is tapped (#147 R3, #204),
 * so an unread slip can still be saved. Kept beyond the artboard (#161): the rule note before the rule exists (FR-13,
 * K13), the close X because this is a form, and no account number field. The slip is the real
 * picture under p13's "K PLUS · 14 Aug" caption, and a tap opens it full screen (#118).
 *
 * Stateless: [ConfirmSlipViewModel] holds what is typed, so a rotation does not lose the amount
 * someone read off a slip they may no longer have in front of them.
 *
 * #217: only the bar while the slip is awaited, and a message with a way back once it has gone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmSlipView(
    uiState: ConfirmSlipUiState,
    onType: (AmountEntry) -> Unit,
    onCategory: (Category) -> Unit,
    onSubCategory: (SubCategory) -> Unit,
    onSave: () -> Unit,
    onNotMine: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShopName: (String) -> Unit = {},
) {
    val text = LocalCopy.current.slips
    val common = LocalCopy.current.common
    var viewing by rememberSaveable { mutableStateOf(false) }
    if (viewing) SlipPictureViewer(id = uiState.slipId, onClose = { viewing = false })
    val focus = LocalFocusManager.current
    // #216: the keyboard goes, so the hint and the chips it points at can both be seen.
    LaunchedEffect(uiState.askForCategory) { if (uiState.askForCategory) focus.clearFocus() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .navigationBarsPadding()
            // Confirm rides above the keyboard, so typing the amount needs no scroll (#204).
            .imePadding()
            // The iOS decimal pad has no Done key, so a tap anywhere else puts the keyboard away.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
    ) {
        SkyTopBar(
            title = text.checkSlip,
            onBack = onClose,
            backIcon = PtutIcon.Close,
            backLabel = text.closeWithoutSaving,
        )
        when (uiState.phase) {
            ConfirmSlipPhase.Loading -> return@Column

            ConfirmSlipPhase.Missing -> {
                SkyEmptyState(
                    headline = text.slipGoneHeadline,
                    body = text.slipGoneBody,
                    status = text.slipGoneStatus,
                    statusIcon = PtutIcon.Check.image,
                    cta = common.back,
                    onCta = onClose,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
                )
                return@Column
            }

            ConfirmSlipPhase.Ready -> Unit
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SlipPicture(
                    id = uiState.slipId,
                    shape = PtutRadii.r20,
                    maxSide = Sizes.slipPanelHeight,
                    caption = uiState.pictureCaption,
                    onOpen = { viewing = true },
                    modifier = Modifier.width(Sizes.slipPanelWidth).height(Sizes.slipPanelHeight),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = if (uiState.amountUnread) text.amountNotRead else text.readFromSlip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("฿", style = MaterialTheme.ptutType.sheet, color = MaterialTheme.colorScheme.onSurface)
                        KeyboardAmountField(
                            entry = uiState.entry,
                            onType = onType,
                            label = uiState.amountLabel,
                            openOnStart = uiState.amountUnread,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SkyConfidenceBadge(label = uiState.confidence)
                }
            }

            Column {
                SkyHairline()
                // The shop is named where it is shown: a read name can be put right in place, and
                // noise or a slip with no shop starts empty with what it was read as for a hint.
                SkyEditableKeyValueRow(
                    label = text.paidTo.marked(SlipField.Counterparty in uiState.lowConfidence, text),
                    value = uiState.shopName,
                    onValueChange = onShopName,
                    placeholder = uiState.merchant.ifBlank { text.shopNameExample },
                )
                SkyKeyValueRow(label = uiState.whenLabel, value = uiState.date)
                SkyKeyValueRow(
                    label = text.reference.marked(SlipField.Reference in uiState.lowConfidence, text),
                    value = uiState.reference.ifBlank { "—" },
                )
            }

            // Research finding 2: a Thai name the reader could not read is named once, in the row above.
            if (uiState.nameable) {
                Column {
                    Text(text.whatIsThisShop, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text.shopReadAsNoise,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }

            Text(common.category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                uiState.categories.forEach { category ->
                    SkyChip(
                        label = category.name,
                        selected = category == uiState.category,
                        onClick = { onCategory(category) },
                    )
                }
            }

            // Between the chips and the rule note: the note stays about the parent (R5 in #147).
            if (uiState.subCategories.isNotEmpty()) {
                SkySubChipBox(
                    options = uiState.subCategories,
                    name = SubCategory::name,
                    selected = uiState.subCategory,
                    onSelect = onSubCategory,
                )
            }

            // FR-13, before the rule exists: a rule found out about afterwards was never agreed to.
            SkyNoticeCard(
                text = uiState.ruleNote,
                icon = if (uiState.shopRead) PtutIcon.AutoAwesome.image else PtutIcon.Info.image,
            )
        }
        // #216: beside Confirm and above the keyboard, where a toast could not be seen.
        uiState.categoryHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ptut.warnInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkyButton(
                label = text.notMine,
                onClick = onNotMine,
                kind = SkyButtonKind.Outline,
                enabled = !uiState.saving,
                modifier = Modifier.weight(1f),
            )
            // Live without a category, so the press can say what is missing (FR-11).
            SkyButton(
                label = text.confirm,
                onClick = onSave,
                enabled = uiState.canSave,
                modifier = Modifier.weight(1.6f),
            )
        }
    }
}

/** A field the parser had to judge says so, so it is checked against the picture first. */
private fun String.marked(unsure: Boolean, text: SlipsCopy): String = if (unsure) text.checkIt(this) else this

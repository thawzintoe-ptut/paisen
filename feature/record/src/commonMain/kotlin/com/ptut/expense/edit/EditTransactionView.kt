package com.ptut.expense.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutType
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.locale.Copy
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.KeyboardAmountField
import com.ptut.expense.ui.MemoSheet
import com.ptut.expense.ui.NoteRow
import com.ptut.expense.ui.sky.AskBeforeLeaving
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyConfirmAction
import com.ptut.expense.ui.sky.SkyDateTimeRow
import com.ptut.expense.ui.sky.SkyDiscardSheet
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkySubChipBox
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * Edit in Sky, "Edit purchase" (p18): the amount on an ink rule with a pencil, typed with the number
 * keyboard (#220), the category chips, the read-only evidence, and the note. Save sits in the bar and
 * stays inert until a value changes.
 *
 * Kept beyond the artboard (#146): "Read from the slip as ฿…" under the amount (K14), the notice, the shop
 * and reference shown read-only, the Missing state, and "Close without saving". There is no
 * "Also sort future … slips" box: that waits past v1 (#147, R5).
 */
@Composable
fun EditTransactionView(
    uiState: EditTransactionUiState,
    onClose: () -> Unit,
    onType: (AmountEntry) -> Unit,
    onCategory: (Category) -> Unit,
    onSubCategory: (SubCategory) -> Unit,
    onTime: (Long) -> Unit,
    onOpenMemo: () -> Unit,
    onMemo: (String) -> Unit,
    onCloseMemo: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.record
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        when (uiState) {
            EditTransactionUiState.Loading -> EditBar(title = text.editPurchase, canSave = false, onClose = onClose, onSave = onSave)

            EditTransactionUiState.Missing -> Column {
                EditBar(title = text.editPurchase, canSave = false, onClose = onClose, onSave = onSave)
                SkyEmptyState(
                    headline = text.gone,
                    body = text.goneEditBody,
                    status = text.goneEditStatus,
                    statusIcon = PtutIcon.Check.image,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
                )
            }

            is EditTransactionUiState.Loaded -> {
                // #214: ✕, back and the iOS edge swipe ask before a changed form is dropped.
                var askDiscard by rememberSaveable { mutableStateOf(false) }
                val leave: () -> Unit = {
                    if (uiState.changed) {
                        askDiscard = true
                    } else {
                        onClose()
                    }
                }
                AskBeforeLeaving(changed = uiState.changed, onAsk = { askDiscard = true })
                Form(uiState, leave, onType, onCategory, onSubCategory, onTime, onOpenMemo, onSave)
                if (uiState.editingMemo) MemoSheet(memo = uiState.memo, onMemo = onMemo, onDismiss = onCloseMemo)
                if (askDiscard) {
                    SkyDiscardSheet(
                        onKeep = { askDiscard = false },
                        onDiscard = {
                            askDiscard = false
                            onClose()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditBar(title: String, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    SkyTopBar(
        title = title,
        onBack = onClose,
        backIcon = PtutIcon.Close,
        backLabel = LocalCopy.current.record.closeWithoutSaving,
        trailing = { SkyConfirmAction(enabled = canSave, onClick = onSave) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: EditTransactionUiState.Loaded,
    onClose: () -> Unit,
    onType: (AmountEntry) -> Unit,
    onCategory: (Category) -> Unit,
    onSubCategory: (SubCategory) -> Unit,
    onTime: (Long) -> Unit,
    onOpenMemo: () -> Unit,
    onSave: () -> Unit,
) {
    val copy = LocalCopy.current
    val focus = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // The keyboard pushes the form up rather than covering the chips under the amount.
            .imePadding()
            // The iOS decimal pad has no Done key, so a tap outside the field puts the keyboard away.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
    ) {
        EditBar(
            title = if (state.isIncome) copy.record.editIncome else copy.record.editPurchase,
            canSave = state.canSave,
            onClose = onClose,
            onSave = onSave,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            UnderlinedAmount(state = state, onType = onType)

            Text(copy.common.category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.categories.forEach { category ->
                    SkyChip(
                        label = category.name,
                        selected = category.id == state.categoryId,
                        onClick = { onCategory(category) },
                    )
                }
            }
            if (state.subCategories.isNotEmpty()) {
                SkySubChipBox(
                    options = state.subCategories,
                    name = SubCategory::name,
                    selected = state.subCategories.firstOrNull { it.id == state.subCategoryId },
                    onSelect = onSubCategory,
                )
            }

            Column {
                SkyHairline()
                // Evidence, not preferences: what the row already says, never typed over.
                state.evidence.forEach { row -> SkyKeyValueRow(label = row.label.skyLabel(copy), value = row.value) }
                SkyDateTimeRow(epochMillis = state.occurredAtEpochMillis, onPick = onTime)
                NoteRow(memo = state.memo, onOpenMemo = onOpenMemo)
            }

            SkyNoticeCard(text = state.notice, icon = PtutIcon.Info.image)
            state.alsoChanges?.let { SkyNoticeCard(text = it, icon = PtutIcon.Group.image, modifier = Modifier.padding(top = Spacing.s12)) }
            Box(Modifier.height(Spacing.sm))
        }
    }
}

/**
 * p18: the amount large on an ink rule, the pencil at its end. #220: typed with the number
 * keyboard, as Add and Check slip are since f2f05f8, so the stored figure opens selected.
 */
@Composable
private fun UnderlinedAmount(state: EditTransactionUiState.Loaded, onType: (AmountEntry) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val copy = LocalCopy.current
    Column {
        Text(copy.common.amount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("฿ ", style = MaterialTheme.ptutType.heroSymbol, color = ink)
            KeyboardAmountField(
                entry = state.entry,
                onType = onType,
                label = copy.record.amountSpoken(state.entry.display),
                openOnStart = false,
                modifier = Modifier.weight(1f),
            )
            Icon(PtutIcon.Edit.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.barGlyph))
        }
        Box(Modifier.fillMaxWidth().height(SkySizes.hairline).background(ink))
        state.slipRead?.let { read ->
            Text(read, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/** The evidence rows' names as p18 writes them. */
private fun String.skyLabel(copy: Copy): String = when (this) {
    "MERCHANT" -> copy.record.shopLabel
    "DATE & TIME" -> copy.record.whenLabel
    "REFERENCE" -> copy.record.referenceLabel
    else -> lowercase().replaceFirstChar { it.uppercase() }
}

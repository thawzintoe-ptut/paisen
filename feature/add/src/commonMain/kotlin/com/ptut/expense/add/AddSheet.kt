package com.ptut.expense.add

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.agent.SpeechLanguage
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.locale.CommonCopy
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.KeyboardAmountField
import com.ptut.expense.ui.MemoSheet
import com.ptut.expense.ui.NoteRow
import com.ptut.expense.ui.sky.SkyAskField
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyDateTimeRow
import com.ptut.expense.ui.sky.SkySegmentedToggle
import com.ptut.expense.ui.sky.SkySubChipBox

/**
 * The add sheet in Sky (p10): the amount beside Expense / Income, the reader's own categories in
 * their order, and SAVE. No title: the amount is the subject. The amount is typed with the number
 * keyboard, which opens with the sheet, the way Check slip's does (#204); p10's keypad is gone.
 *
 * Stateless: [AddViewModel] holds what is typed, so the sheet can be closed and reopened, rotated
 * or recomposed without the amount going with it. It keeps the hint that says why SAVE is dark
 * (#146, K10).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSheet(
    uiState: AddUiState,
    onType: (AmountEntry) -> Unit,
    onKind: (Direction) -> Unit,
    onCategory: (Category) -> Unit,
    onSubCategory: (SubCategory) -> Unit,
    onTime: (Long) -> Unit,
    onOpenMemo: () -> Unit,
    onMemo: (String) -> Unit,
    onCloseMemo: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** #207: a typed sentence fills the sheet. Shown only while [AddUiState.quickAdd] says so. */
    onQuickAdd: (String) -> Unit = {},
    onAskText: (String) -> Unit = {},
    /** #212: the mic. Opened with the sheet, and whatever it was hearing is dropped when the sheet closes. */
    onOpenVoice: () -> Unit = {},
    onCloseVoice: () -> Unit = {},
    onListen: () -> Unit = {},
    onStopListening: () -> Unit = {},
    onVoiceLanguage: (SpeechLanguage) -> Unit = {},
) {
    if (uiState.editingMemo) MemoSheet(memo = uiState.memo, onMemo = onMemo, onDismiss = onCloseMemo)
    val common = LocalCopy.current.common
    val focus = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // The iOS decimal pad has no Done key, so a tap outside the field puts the keyboard away.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            // With the keyboard up, a large text size can outgrow what is left of the screen.
            .verticalScroll(rememberScrollState())
            .padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        // Not on p10: above the amount, because what it reads fills everything below it.
        if (uiState.quickAdd) {
            DisposableEffect(Unit) {
                onOpenVoice()
                onDispose(onCloseVoice)
            }
            QuickAdd(
                uiState = uiState,
                onAskText = onAskText,
                onQuickAdd = onQuickAdd,
                onListen = onListen,
                onStopListening = onStopListening,
                onVoiceLanguage = onVoiceLanguage,
            )
        }

        // Wraps rather than squeezing: at a large text size the toggle drops under the amount (#20).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DraftAmount(entry = uiState.entry, onType = onType)
            SkySegmentedToggle(
                options = DirectionOrder.map { it.label(common) },
                selected = DirectionOrder.indexOf(uiState.kind),
                onSelect = { onKind(DirectionOrder[it]) },
            )
        }

        // Wraps rather than scrolls: a category has to be chosen before SAVE lights up.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            uiState.categories.forEach { category ->
                SkyChip(
                    label = category.name,
                    selected = uiState.category == category,
                    onClick = { onCategory(category) },
                    height = SkySizes.sheetChip,
                )
            }
        }

        // Only once a parent with sub-categories is chosen, so the common path is unchanged (#111).
        if (uiState.subCategories.isNotEmpty()) {
            SkySubChipBox(
                options = uiState.subCategories,
                name = SubCategory::name,
                selected = uiState.subCategory,
                onSelect = onSubCategory,
            )
        }

        // Not on p10: the note names the row on Home and History (#211), so it is offered here too.
        NoteRow(memo = uiState.memo, onOpenMemo = onOpenMemo)

        SkyDateTimeRow(epochMillis = uiState.occurredAtEpochMillis, onPick = onTime)

        // Only the line that explains a dark SAVE: Sky draws no hint otherwise (K10).
        if (uiState.category == null && !uiState.entry.isEmpty) {
            Text(
                text = uiState.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SkyButton(
            label = common.save,
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * #207's field and #212's mic. The mic shows only for a language this phone hears offline; the
 * switch keeps all three, so one it cannot hear says so in a line and typing still works.
 */
@Composable
private fun QuickAdd(
    uiState: AddUiState,
    onAskText: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onVoiceLanguage: (SpeechLanguage) -> Unit,
) {
    val text = LocalCopy.current.add
    val voice = uiState.voice
    val listening = voice?.listening == true
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SkyAskField(
            text = uiState.askText,
            onText = onAskText,
            onSend = onQuickAdd,
            placeholder = if (listening) text.listening else text.askPlaceholder,
            enabled = !uiState.reading && !listening,
            sendLabel = text.send,
            modifier = Modifier.fillMaxWidth(),
            trailing = if (voice?.canListen == true) {
                {
                    SkyBarIconAction(
                        icon = if (listening) PtutIcon.Stop else PtutIcon.Mic,
                        label = if (listening) text.stopListening else text.sayIt,
                        onClick = if (listening) onStopListening else onListen,
                    )
                }
            } else {
                null
            },
        )
        // #216: in the sheet, because a toast is drawn under it. Polite, so a screen reader says it too.
        uiState.askNote?.let { note ->
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.problem) MaterialTheme.ptut.warnInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (voice != null) {
            SkySegmentedToggle(
                options = SpeechLanguage.entries.map { it.label },
                selected = SpeechLanguage.entries.indexOf(voice.language),
                onSelect = { onVoiceLanguage(SpeechLanguage.entries[it]) },
            )
            voice.unavailableNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Expense leads because it is what almost every hand-typed row turns out to be. */
private val DirectionOrder = listOf(Direction.Expense, Direction.Income)

private fun Direction.label(common: CommonCopy): String = when (this) {
    Direction.Expense -> common.expense
    Direction.Income -> common.income
}

/** Shows exactly what was typed. Formatting mid-entry moves the digits under the thumb. */
@Composable
private fun DraftAmount(entry: AmountEntry, onType: (AmountEntry) -> Unit) {
    val common = LocalCopy.current.common
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "฿",
            style = MaterialTheme.ptutType.heroSymbol,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = Spacing.xxs, bottom = Spacing.xs),
        )
        KeyboardAmountField(entry = entry, onType = onType, label = common.amount, openOnStart = true, fillWidth = false)
    }
}

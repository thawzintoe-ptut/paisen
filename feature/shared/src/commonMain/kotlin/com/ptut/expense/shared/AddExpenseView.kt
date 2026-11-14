package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.AskBeforeLeaving
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyDateTimeRow
import com.ptut.expense.ui.sky.SkyDiscardSheet
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkySegmentedToggle
import com.ptut.expense.ui.sky.SkyTopBar
import com.ptut.expense.ui.sky.SkyWarningBanner

/** The actions Add expense and Split options send back to [AddExpenseViewModel]. */
data class AddExpenseActions(
    val onClose: () -> Unit,
    val onAmount: (String) -> Unit,
    val onRate: (String) -> Unit,
    val onCurrency: (Currency) -> Unit,
    val onDescription: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onTime: (Long) -> Unit,
    val onCategory: (String) -> Unit,
    val onPayer: (String) -> Unit,
    val onQuickSplit: (String, QuickSplit) -> Unit,
    val onQuickPayer: (String) -> Unit,
    val onOpenSplit: (Boolean) -> Unit,
    val onSplitMode: (SplitMode) -> Unit,
    val onWeight: (String, String) -> Unit,
    val onGiveLeft: () -> Unit,
    val onSave: () -> Unit,
    val onRemove: () -> Unit,
)

/** #183 (p28), with #138's Split options (p29) over it while it is open. */
@Composable
fun AddExpenseView(uiState: AddExpenseUiState, actions: AddExpenseActions, modifier: Modifier = Modifier) {
    val text = LocalCopy.current
    when (uiState) {
        AddExpenseUiState.Missing -> Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
            SkyTopBar(title = text.shared.expense.addExpense, onBack = actions.onClose, backIcon = PtutIcon.Close, backLabel = text.common.close)
            SkyEmptyState(
                headline = text.shared.thisGroupIsGone,
                body = text.shared.expense.goneBody,
                status = text.shared.expense.goneStatus,
                statusIcon = PtutIcon.Check.image,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
            )
        }

        is AddExpenseUiState.Ready -> if (uiState.form.splitOpen) {
            SplitOptions(uiState, actions, modifier)
        } else {
            Form(uiState, actions, modifier)
        }
    }
}

@Composable
private fun Form(state: AddExpenseUiState.Ready, actions: AddExpenseActions, modifier: Modifier = Modifier) {
    val text = LocalCopy.current
    val words = text.shared.expense
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val form = state.form
    val asksFirst = state.changed && !form.saving
    // #214: back, the iOS swipe and ✕ ask before a changed form is lost.
    AskBeforeLeaving(changed = asksFirst, onAsk = { sheet = SHEET_DISCARD })
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        SkyTopBar(
            title = if (state.isEdit) words.editExpense else words.addExpense,
            onBack = { if (asksFirst) sheet = SHEET_DISCARD else actions.onClose() },
            backIcon = PtutIcon.Close,
            backLabel = text.common.close,
            trailing = if (state.isEdit) {
                { SkyBarIconAction(icon = PtutIcon.Delete, label = words.removeThis, onClick = { sheet = SHEET_REMOVE }) }
            } else {
                null
            },
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Pill(text = state.groupLine, icon = PtutIcon.Work, onClick = null)
            Pill(
                text = "${form.currency.code} ${form.currency.symbol}",
                icon = null,
                onClick = if (form.locked) null else ({ sheet = SHEET_CURRENCY }),
                sky = true,
                modifier = Modifier.padding(top = Spacing.md),
            )
            AmountField(symbol = form.currency.symbol, text = form.amountText, onText = actions.onAmount, readOnly = form.locked, modifier = Modifier.padding(top = Spacing.sm))
            state.lockedLine?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.xs))
            }
            if (state.rateUnitLabel != null) {
                Row(
                    modifier = Modifier.padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(state.rateUnitLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberField(text = form.rateText, onText = actions.onRate, label = state.rateUnitLabel, modifier = Modifier.width(SkySizes.rateField))
                }
            }
            state.bahtLine?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.xs))
            }
            Spacer(Modifier.height(Spacing.md))
            SkyHairline()
            IconRow(icon = PtutIcon.Edit) {
                SharedTextField(value = form.description, onValueChange = actions.onDescription, label = words.whatFor, placeholder = state.categoryName ?: words.whatForHint)
            }
            // #186: a locked date still shows; it only stops opening the picker.
            SkyDateTimeRow(epochMillis = form.occurredAtEpochMillis, onPick = actions.onTime, enabled = !form.locked)
            IconRow(icon = state.categoryIcon?.let { PtutIcon.named(it) } ?: PtutIcon.ReceiptLong, onClick = if (form.locked) null else ({ sheet = SHEET_CATEGORY })) {
                Text(state.categoryName ?: words.pickCategory, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (!form.locked) Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
            }
            IconRow(icon = PtutIcon.ListAlt) {
                SharedTextField(value = form.note, onValueChange = actions.onNote, label = text.common.note, placeholder = words.addNote)
            }
            PaidAndSplit(state, onClick = { sheet = SHEET_QUICK }, modifier = Modifier.padding(top = Spacing.md))
            Spacer(Modifier.height(Spacing.lg))
        }
        SkyButton(
            words.saveExpense,
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = if (state.hint != null) Spacing.xs else Spacing.md),
        )
        // #216: a dark Save says what it still needs.
        state.hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    when (sheet) {
        SHEET_DISCARD -> SkyDiscardSheet(
            onKeep = { sheet = null },
            onDiscard = {
                sheet = null
                actions.onClose()
            },
        )

        SHEET_CURRENCY -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(words.currency, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.currencies.forEach { currency ->
                    ChoiceRow(
                        text = "${currency.code}  ${currency.symbol}",
                        selected = currency == form.currency,
                        onClick = {
                            sheet = null
                            actions.onCurrency(currency)
                        },
                    )
                }
            }
        }

        SHEET_CATEGORY -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(text.common.category, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.categories.forEach { category ->
                    ChoiceRow(
                        text = category.name,
                        selected = category.id == form.categoryId,
                        icon = PtutIcon.named(category.icon),
                        onClick = {
                            sheet = null
                            actions.onCategory(category.id)
                        },
                    )
                }
            }
        }

        SHEET_QUICK -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            QuickSplitSheet(
                state = state,
                onPayer = actions.onQuickPayer,
                onChoice = { payerId, choice ->
                    sheet = null
                    actions.onQuickSplit(payerId, choice)
                },
                onMore = {
                    sheet = null
                    actions.onOpenSplit(true)
                },
            )
        }

        SHEET_REMOVE -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(words.removeAsk, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                words.removeBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SkyButton(
                words.remove,
                onClick = {
                    sheet = null
                    actions.onRemove()
                },
                kind = SkyButtonKind.Danger,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
    }
}

/** The grey card: who paid and how it splits in one line, and what that means for you under it. */
@Composable
private fun PaidAndSplit(state: AddExpenseUiState.Ready, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.shared.expense
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r22)
            .background(MaterialTheme.colorScheme.surface)
            .tappable(PtutRadii.r22, Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
    ) {
        Text(text.paidAndSplit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(state.quick.summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
        }
        state.resultLine?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/** The quick choices: a chip per payer in a group of three or more, the rows, then More options. */
@Composable
private fun QuickSplitSheet(
    state: AddExpenseUiState.Ready,
    onPayer: (String) -> Unit,
    onChoice: (String, QuickSplit) -> Unit,
    onMore: () -> Unit,
) {
    val text = LocalCopy.current.shared
    val quick = state.quick
    Text(text.expense.paidAndSplit, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
    state.lockedLine?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
    }
    if (quick.chips.isNotEmpty()) {
        Text(text.paidBy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            quick.chips.forEach { chip -> SkyChip(label = chip.name, selected = chip.selected, onClick = { onPayer(chip.memberId) }) }
        }
    }
    quick.rows.forEach { row ->
        TwoLineChoiceRow(title = row.label, detail = row.result, owe = row.owe, selected = row.selected, onClick = { onChoice(row.payerId, row.choice) })
    }
    SkyHairline()
    TwoLineChoiceRow(title = text.expense.moreOptions, detail = quick.moreHint, owe = false, selected = quick.moreSelected, onClick = onMore, chevron = true)
}

@Composable
private fun TwoLineChoiceRow(title: String, detail: String?, owe: Boolean, selected: Boolean, onClick: () -> Unit, chevron: Boolean = false) {
    val chosen = LocalCopy.current.shared.chosen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (owe) MaterialTheme.ptut.signatureInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        if (selected) Icon(PtutIcon.Check.image, contentDescription = chosen, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
        if (chevron) Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
    }
}

/** p29: four modes, one row per member, the live check, and DONE once it adds up. */
@Composable
private fun SplitOptions(state: AddExpenseUiState.Ready, actions: AddExpenseActions, modifier: Modifier = Modifier) {
    val text = LocalCopy.current
    val split = state.split
    val modes = SplitMode.entries
    var payerOpen by rememberSaveable { mutableStateOf(false) }
    CloseOnBack { actions.onOpenSplit(false) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        SkyTopBar(title = split.title, onBack = { actions.onOpenSplit(false) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SkySizes.transactionRow)
                    .then(if (state.form.locked) Modifier else Modifier.tappable(PtutRadii.r12, Role.Button) { payerOpen = true }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(text.shared.paidBy, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(state.payerName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!state.form.locked) Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
            }
            SkySegmentedToggle(
                options = modes.map { it.label() },
                selected = modes.indexOf(split.mode),
                onSelect = { actions.onSplitMode(modes[it]) },
            )
            split.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SkySizes.transactionRow).padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                ) {
                    MemberAvatar(label = row.name.take(1).uppercase(), isMe = row.isMe)
                    Text(row.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (split.mode == SplitMode.Equally) {
                        Text(row.part, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            WeightField(
                                text = row.text,
                                onText = { actions.onWeight(row.memberId, it) },
                                suffix = when (split.mode) {
                                    SplitMode.Percent -> "%"
                                    SplitMode.Shares -> "×"
                                    else -> ""
                                },
                                label = text.shared.expense.weightLabel(row.name, split.mode.label()),
                            )
                            // #218: what that person pays, under their field.
                            row.detail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
                            }
                        }
                    }
                }
                SkyHairline()
            }
            if (split.leftTitle != null && split.leftBody != null) {
                SkyWarningBanner(
                    title = split.leftTitle,
                    body = split.leftBody,
                    actionLabel = split.giveToLabel,
                    onAction = if (split.giveToLabel != null) actions.onGiveLeft else null,
                    modifier = Modifier.padding(top = Spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (split.mode == SplitMode.Exact || split.mode == SplitMode.Percent) {
                Progress(split.progress, modifier = Modifier.padding(top = Spacing.md))
            }
            Text(
                text.shared.expense.splitHelp,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        SkyButton(
            text.common.done,
            onClick = { actions.onOpenSplit(false) },
            enabled = split.complete,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
        )
    }
    if (payerOpen) {
        SkyBottomSheet(onDismissRequest = { payerOpen = false }) {
            Text(text.shared.paidBy, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            state.payers.forEach { payer ->
                ChoiceRow(
                    text = payer.name,
                    selected = payer.name == state.payerName,
                    onClick = {
                        payerOpen = false
                        actions.onPayer(payer.memberId)
                    },
                )
            }
        }
    }
}

@Composable
private fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(SkySizes.shareBar).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth(fraction).height(SkySizes.shareBar).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

/** p28's centre: the amount in its own currency, typed with the number keyboard. */
@Composable
private fun AmountField(symbol: String, text: String, onText: (String) -> Unit, modifier: Modifier = Modifier, readOnly: Boolean = false) {
    val label = LocalCopy.current.common.amount
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, style = MaterialTheme.ptutType.heroSymbol, color = MaterialTheme.colorScheme.onSurface)
        Box(contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("0", style = MaterialTheme.ptutType.hero, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = text,
                onValueChange = onText,
                readOnly = readOnly,
                singleLine = true,
                textStyle = MaterialTheme.ptutType.hero.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(SkySizes.amountField).semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun NumberField(text: String, onText: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .heightIn(min = SkySizes.search)
            .clip(PtutRadii.r12)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.s12, vertical = Spacing.s10)
            .semantics { contentDescription = label },
    )
}

/** p29: a grey field; the one being edited gets an ink outline. */
@Composable
private fun WeightField(text: String, onText: (String) -> Unit, suffix: String, label: String) {
    var focused by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .width(SkySizes.weightField)
            .heightIn(min = SkySizes.search)
            .clip(PtutRadii.r14)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (focused) Modifier.border(SkySizes.chipBorder, MaterialTheme.colorScheme.onSurface, PtutRadii.r14) else Modifier)
            .padding(horizontal = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
        )
        if (suffix.isNotEmpty()) Text(suffix, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A pill-shaped chip: the group line, the sky currency chip, or a value with a chevron. */
@Composable
private fun Pill(
    text: String,
    icon: PtutIcon?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    sky: Boolean = false,
    chevron: Boolean = onClick != null && !sky,
    white: Boolean = false,
) {
    Row(
        modifier = modifier
            .heightIn(min = SkySizes.search)
            .clip(CircleShape)
            .background(
                when {
                    sky -> MaterialTheme.colorScheme.primaryContainer
                    white -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .then(if (onClick != null) Modifier.tappable(CircleShape, Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        icon?.let { Icon(it.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph)) }
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (chevron) Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
        if (sky && onClick != null) Icon(PtutIcon.ChevronDown.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
    }
}

/** A plain row with its icon on the left, ruled underneath (p28). */
@Composable
private fun IconRow(icon: PtutIcon, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .then(if (onClick != null) Modifier.tappable(PtutRadii.r12, Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(icon.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.barGlyph))
        content()
    }
    SkyHairline()
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit, icon: PtutIcon? = null) {
    val chosen = LocalCopy.current.shared.chosen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        icon?.let { Icon(it.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.barGlyph)) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (selected) Icon(PtutIcon.Check.image, contentDescription = chosen, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
    }
}

/** #214: back and the iOS swipe close Split options and return to the form, not leave the screen. */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CloseOnBack(onBack: () -> Unit) {
    BackHandler(enabled = true, onBack = onBack)
}

private const val SHEET_CURRENCY = "currency"
private const val SHEET_CATEGORY = "category"
private const val SHEET_QUICK = "quick"
private const val SHEET_REMOVE = "remove"
private const val SHEET_DISCARD = "discard"

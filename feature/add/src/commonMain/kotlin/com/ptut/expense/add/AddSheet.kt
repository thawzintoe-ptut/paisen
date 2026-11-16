package com.ptut.expense.add

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.agent.SpeechLanguage
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.locale.CommonCopy
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.KeyboardAmountField
import com.ptut.expense.ui.MemoSheet
import com.ptut.expense.ui.sky.SkyAskField
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyDateTimePicker
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyRateRow
import com.ptut.expense.ui.sky.SkySegmentedToggle
import com.ptut.expense.ui.sky.SkySubChipBox

/**
 * The add sheet in Sky (p10), read top to bottom: Expense / Income, the amount, the reader's own
 * categories in their order, then the note and the time. No title: the amount is the subject. The
 * amount is typed with the number keyboard, which opens with the sheet, the way Check slip's does (#204).
 *
 * SAVE sits under the scrolling form rather than in it, so the number keyboard never covers it.
 * Stateless: [AddViewModel] holds what is typed, so the sheet can be closed and reopened, rotated
 * or recomposed without the amount going with it.
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
    onDownloadVoice: () -> Unit = {},
    /** The yes or no to [AddUiState.askingOnline], asked before a language is first heard online. */
    onAgreeOnline: () -> Unit = {},
    onDismissOnlineAsk: () -> Unit = {},
    onCurrency: (Currency) -> Unit = {},
    onRate: (String) -> Unit = {},
    onOpenRate: () -> Unit = {},
    onCloseRate: (keep: Boolean) -> Unit = {},
) {
    if (uiState.editingMemo) MemoSheet(memo = uiState.memo, onMemo = onMemo, onDismiss = onCloseMemo)
    uiState.askingOnline?.let { language -> OnlineAsk(language = language, onListen = onAgreeOnline, onTypeInstead = onDismissOnlineAsk) }
    val common = LocalCopy.current.common
    val shell = LocalCopy.current.shell
    val focus = LocalFocusManager.current
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    if (uiState.editingRate) RateSheet(uiState = uiState, onRate = onRate, onClose = onCloseRate)
    if (choosingCurrency) {
        CurrencySheet(chosen = uiState.currency, onPick = {
            onCurrency(it)
            choosingCurrency = false
        }, onDismiss = { choosingCurrency = false })
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // The iOS decimal pad has no Done key, so a tap outside the field puts the keyboard away.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            .padding(top = Spacing.sm),
    ) {
        Column(
            // Scrolls under SAVE: with the keyboard up, a large text size can outgrow what is left of the screen.
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            // Not on p10: first, because what it reads fills everything below it.
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
                    onDownloadVoice = onDownloadVoice,
                )
            }

            // Before the amount, because the kind decides which categories are offered under it.
            SkySegmentedToggle(
                options = DirectionOrder.map { it.label(common) },
                selected = DirectionOrder.indexOf(uiState.kind),
                onSelect = { onKind(DirectionOrder[it]) },
            )

            DraftAmount(
                entry = uiState.entry,
                kind = uiState.kind,
                onType = onType,
                symbol = uiState.currency.symbol,
                onSymbol = if (uiState.offersCurrencies) ({ choosingCurrency = true }) else null,
            )

            // Anything but baht counts as baht at a rate: one line says what it comes to, and a tap changes the rate.
            uiState.rateLine?.let { line -> RateLine(line = line, asking = uiState.needsRate, onClick = onOpenRate) }

            // Wraps rather than scrolls: a category has to be chosen before SAVE lights up.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                uiState.categories.forEach { category ->
                    SkyChip(
                        label = category.name,
                        selected = uiState.category == category,
                        onClick = { onCategory(category) },
                        height = SkySizes.sheetChip,
                        icon = ptutIcon(category.icon),
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

            // Optional, so both share one line. The note names the row on Home and History (#211).
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DetailButton(
                    icon = PtutIcon.Edit,
                    name = common.note,
                    value = uiState.memo ?: shell.addANote,
                    onClick = onOpenMemo,
                    modifier = Modifier.weight(1f),
                )
                SkyDateTimePicker(epochMillis = uiState.occurredAtEpochMillis, onPick = onTime) { value, open ->
                    DetailButton(
                        icon = PtutIcon.CalendarMonth,
                        name = shell.dateAndTime,
                        value = value,
                        onClick = open,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // What still stops a save: grey once an amount is typed, warning ink after SAVE was tapped too early.
        uiState.missing?.takeIf { uiState.triedSave || !uiState.entry.isEmpty }?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.triedSave) MaterialTheme.ptut.warnInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s12).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // Lit even before it can save: a dark key gives no reason and no answer to a tap.
        SkyButton(
            label = uiState.saveLabel,
            onClick = onSave,
            enabled = !uiState.saving,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
        )
    }
}

/**
 * #207's field and #212's mic. The voice language is a pill beside the mic, and what each language
 * can do is said in the sheet it opens, so none of it takes room on the add sheet itself.
 */
@Composable
private fun QuickAdd(
    uiState: AddUiState,
    onAskText: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onVoiceLanguage: (SpeechLanguage) -> Unit,
    onDownloadVoice: () -> Unit,
) {
    val text = LocalCopy.current.add
    val voice = uiState.voice
    val listening = voice?.listening == true
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SkyAskField(
            text = uiState.askText,
            onText = onAskText,
            onSend = onQuickAdd,
            placeholder = if (listening) text.listening else text.askPlaceholder,
            enabled = !uiState.reading && !listening,
            sendLabel = text.send,
            modifier = Modifier.fillMaxWidth(),
            trailing = if (voice != null) {
                {
                    VoiceControls(
                        voice = voice,
                        onChooseLanguage = { choosingLanguage = true },
                        onListen = onListen,
                        onStopListening = onStopListening,
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
    }
    if (choosingLanguage && voice != null) {
        VoiceLanguageSheet(
            voice = voice,
            onPick = onVoiceLanguage,
            onDownload = onDownloadVoice,
            onDismiss = { choosingLanguage = false },
        )
    }
}

/** The language pill and the mic. A mic that cannot hear the chosen language opens the sheet that says why. */
@Composable
private fun VoiceControls(
    voice: QuickAddVoice,
    onChooseLanguage: () -> Unit,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
) {
    val text = LocalCopy.current.add
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!voice.listening) VoiceLanguagePill(language = voice.language, onClick = onChooseLanguage)
        SkyBarIconAction(
            icon = if (voice.listening) PtutIcon.Stop else PtutIcon.Mic,
            label = if (voice.listening) text.stopListening else text.sayIt,
            onClick = when {
                voice.listening -> onStopListening
                voice.canListen -> onListen
                else -> onChooseLanguage
            },
        )
    }
}

/** "မြန်မာ ⌄": painted small inside a 44 tap area. */
@Composable
private fun VoiceLanguagePill(language: SpeechLanguage, onClick: () -> Unit) {
    val text = LocalCopy.current.add
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = Sizes.minTouchTarget)
            .tappable(CircleShape, Role.Button, onClick = onClick)
            .semantics { contentDescription = text.voiceLanguageLabel(language.fullName) },
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Icon(PtutIcon.ChevronDown.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.subChipTick))
        }
    }
}

/** The three languages, each with what the mic does for it here, laid out like Settings' Language. */
@Composable
private fun VoiceLanguageSheet(
    voice: QuickAddVoice,
    onPick: (SpeechLanguage) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = LocalCopy.current
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            copy.add.voiceLanguageTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        SkyGroupedCard(
            rows = SpeechLanguage.entries.map<SpeechLanguage, @Composable () -> Unit> { language ->
                { VoiceLanguageRow(language = language, voice = voice, onPick = onPick) }
            },
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (voice.canDownload) {
            SkyButton(
                label = copy.add.downloadVoice(voice.language.fullName),
                onClick = onDownload,
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
        SkyButton(copy.common.done, onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

@Composable
private fun VoiceLanguageRow(language: SpeechLanguage, voice: QuickAddVoice, onPick: (SpeechLanguage) -> Unit) {
    val chosen = language == voice.language
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.keyValueRow)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = { onPick(language) })
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(language.fullName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                voice.noteFor(language) ?: LocalCopy.current.add.voiceOnPhone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chosen) {
            Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsGlyph))
        }
    }
}

/** Asked once per language, the moment the mic would first send the voice to Google. */
@Composable
private fun OnlineAsk(language: SpeechLanguage, onListen: () -> Unit, onTypeInstead: () -> Unit) {
    val text = LocalCopy.current.add
    SkyBottomSheet(onDismissRequest = onTypeInstead) {
        Text(
            text.onlineAskTitle(language.fullName),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text.voiceOnline(language.fullName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(text.listenOnline, onClick = onListen, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SkyButton(text.typeInstead, onClick = onTypeInstead, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

/**
 * An optional detail: its glyph and its value, or what a tap adds. Filled rather than outlined like
 * the category chips, since it opens a picker instead of choosing.
 */
@Composable
private fun DetailButton(icon: PtutIcon, name: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .tappable(CircleShape, Role.Button, onClick = onClick)
            .semantics { contentDescription = "$name, $value" }
            .padding(horizontal = Spacing.s14),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.chipGlyph))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Expense leads because it is what almost every hand-typed row turns out to be. */
private val DirectionOrder = listOf(Direction.Expense, Direction.Income)

private fun Direction.label(common: CommonCopy): String = when (this) {
    Direction.Expense -> common.expense
    Direction.Income -> common.income
}

/**
 * Shows exactly what was typed: formatting mid-entry moves the digits under the thumb. The figure
 * takes the kind's ink, as the rows do, and the toggle above still names the kind in words.
 */
@Composable
private fun DraftAmount(
    entry: AmountEntry,
    kind: Direction,
    onType: (AmountEntry) -> Unit,
    symbol: String = "฿",
    onSymbol: (() -> Unit)? = null,
) {
    val common = LocalCopy.current.common
    val ink = if (kind == Direction.Income) MaterialTheme.ptut.ok else MaterialTheme.ptut.signatureInk
    val currencyTitle = currencyCopy().currencyTitle
    Row(verticalAlignment = Alignment.Bottom) {
        // A tap on the sign opens the currency list, in builds that offer it; a 44 tap area around it.
        Text(
            text = symbol,
            style = MaterialTheme.ptutType.heroSymbol,
            color = ink,
            modifier = Modifier
                .padding(end = Spacing.xxs, bottom = Spacing.xs)
                .then(
                    if (onSymbol == null) {
                        Modifier
                    } else {
                        Modifier
                            .sizeIn(minWidth = Sizes.minTouchTarget, minHeight = Sizes.minTouchTarget)
                            .semantics { contentDescription = "$currencyTitle, $symbol" }
                            .tappable(PtutRadii.r12, Role.Button, onClick = onSymbol)
                    },
                ),
        )
        KeyboardAmountField(entry = entry, onType = onType, label = common.amount, openOnStart = true, color = ink)
    }
}

/** Every currency the app can turn into baht, as the shared groups offer them. */
@Composable
private fun CurrencySheet(chosen: Currency, onPick: (Currency) -> Unit, onDismiss: () -> Unit) {
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            currencyCopy().currencyTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.sm)) {
            Currencies.all.forEach { currency ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SkySizes.transactionRow)
                        .tappable(PtutRadii.r12, Role.Button) { onPick(currency) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${currency.code}  ${currency.symbol}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (currency == chosen) {
                        Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
                    }
                }
            }
        }
    }
}

/** The foreign amount in baht and its rate, or a prompt for one; a 44 tap target that opens [RateSheet]. */
@Composable
private fun RateLine(line: String, asking: Boolean, onClick: () -> Unit) {
    val ink = if (asking) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .heightIn(min = Sizes.minTouchTarget)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(line, style = MaterialTheme.typography.bodyMedium, color = ink)
        Icon(PtutIcon.Edit.image, contentDescription = null, tint = ink, modifier = Modifier.size(SkySizes.cardGlyph))
    }
}

/** The rate in its own sheet, as the note is, so the keyboard never covers the amount. Cancel puts the old rate back. */
@Composable
private fun RateSheet(uiState: AddUiState, onRate: (String) -> Unit, onClose: (keep: Boolean) -> Unit) {
    val common = LocalCopy.current.common
    SkyBottomSheet(onDismissRequest = { onClose(false) }) {
        Text(
            currencyCopy().rateFor(uiState.currency.code),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        SkyRateRow(
            rateText = uiState.rateText,
            unit = uiState.rateUnit,
            bahtLine = uiState.bahtLine,
            onRate = onRate,
            showLabel = false,
            focusOnStart = true,
            modifier = Modifier.padding(top = Spacing.s12),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(label = common.cancel, onClick = { onClose(false) }, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton(label = common.done, onClick = { onClose(true) }, modifier = Modifier.weight(1.6f))
        }
    }
}

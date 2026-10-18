package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.shared.RepeatEnd
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatKind
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.notify.rememberNotificationAsk
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkySegmentedToggle
import com.ptut.expense.ui.sky.SkySwitch
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.SkyTopBar
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class RepeatExpenseActions(
    val onBack: () -> Unit,
    val onKind: (RepeatKind) -> Unit,
    val onAmount: (String) -> Unit,
    val onFrequency: (RepeatFrequency) -> Unit,
    val onStart: (LocalDate) -> Unit,
    val onEnd: (RepeatEnd) -> Unit,
    val onRemind: (Boolean) -> Unit,
    val onPayer: (String) -> Unit,
    /** The notification answer comes with it: a refusal still saves, with the reminder off (#182). */
    val onSave: (allowed: Boolean) -> Unit,
    val onStop: () -> Unit,
)

/**
 * #185 (p33): "Rent · Ari flat", the amount at hero size, who paid and how it splits, then How
 * often, the type chips, On, Ends, the reminder, NEXT THREE, and SAVE · REPEATS MONTHLY.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatExpenseView(uiState: RepeatExpenseUiState, actions: RepeatExpenseActions, modifier: Modifier = Modifier) {
    val text = LocalCopy.current
    val words = text.shared.repeat
    val state = uiState as? RepeatExpenseUiState.Ready
    if (state == null) {
        Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
            SkyTopBar(title = words.title, onBack = actions.onBack)
            GroupGone(status = text.shared.expense.goneStatus)
        }
        return
    }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val ask = rememberNotificationAsk()
    val scope = rememberCoroutineScope()
    val form = state.form
    val frequencies = RepeatFrequency.entries

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        SkyTopBar(title = words.title, onBack = actions.onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Row(modifier = Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text("฿", style = MaterialTheme.ptutType.heroSymbol, color = MaterialTheme.colorScheme.onSurface)
                BasicTextField(
                    value = form.amountText,
                    onValueChange = actions.onAmount,
                    singleLine = true,
                    textStyle = MaterialTheme.ptutType.hero.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { field ->
                        if (form.amountText.isEmpty()) Text("0", style = MaterialTheme.ptutType.hero, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    },
                    modifier = Modifier.semantics { contentDescription = text.common.amount },
                )
            }
            Text(
                state.payLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = Spacing.xxs)
                    .tappable(PtutRadii.r12, Role.Button) { sheet = SHEET_PAYER }
                    .padding(Spacing.xs),
            )

            Label(words.howOften)
            SkySegmentedToggle(
                options = frequencies.map { it.label() },
                selected = frequencies.indexOf(form.frequency),
                onSelect = { actions.onFrequency(frequencies[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                RepeatKind.entries.forEach { kind ->
                    SkyChip(label = kind.label(), selected = kind == form.kind, onClick = { actions.onKind(kind) })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
                    .clip(PtutRadii.r22)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.sm),
            ) {
                SkyKeyValueRow(label = words.on, value = state.anchorLine, modifier = Modifier.tappable(PtutRadii.r12, Role.Button) { sheet = SHEET_START })
                SkyKeyValueRow(label = words.ends, value = state.endLine, modifier = Modifier.tappable(PtutRadii.r12, Role.Button) { sheet = SHEET_END })
                Row(Modifier.fillMaxWidth().heightIn(min = SkySizes.transactionRow), verticalAlignment = Alignment.CenterVertically) {
                    Text(words.remind, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    SkySwitch(checked = form.remind, onCheckedChange = actions.onRemind, label = words.remind)
                }
            }

            if (state.next.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                        .clip(PtutRadii.r22)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(Spacing.sm),
                ) {
                    Text(words.nextThree, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    state.next.forEachIndexed { index, row ->
                        if (index > 0) SkyHairline()
                        Row(Modifier.fillMaxWidth().heightIn(min = SkySizes.keyValueRow), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Text(row.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (state.isEdit && state.active) {
                SkyTextAction(label = words.stop, onClick = { sheet = SHEET_STOP }, enabled = true, modifier = Modifier.padding(top = Spacing.sm))
            }
        }
        SkyButton(
            state.saveLabel,
            onClick = {
                scope.launch {
                    // #182: asked on the first save with the reminder on; the answer goes with the save.
                    val allowed = !form.remind || ask.request()
                    actions.onSave(allowed)
                }
            },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
        )
    }

    when (sheet) {
        // #218: no Undo follows a stop, so it is asked first.
        SHEET_STOP -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(
                words.stopAsk(form.description.ifBlank { form.kind.label() }),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Text(words.stopBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
            Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                SkyButton(text.common.keepIt, onClick = { sheet = null }, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
                SkyButton(
                    words.stop,
                    onClick = {
                        sheet = null
                        actions.onStop()
                    },
                    kind = SkyButtonKind.Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SHEET_PAYER -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(text.shared.paidBy, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            state.payers.forEach { payer ->
                SheetChoice(payer.name, selected = payer.memberId == form.payerMemberId) {
                    sheet = null
                    actions.onPayer(payer.memberId)
                }
            }
        }

        SHEET_END -> SkyBottomSheet(onDismissRequest = { sheet = null }) {
            Text(words.ends, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            SheetChoice(words.never, selected = form.end == RepeatEnd.Never) {
                sheet = null
                actions.onEnd(RepeatEnd.Never)
            }
            ENDS_AFTER.forEach { times ->
                SheetChoice(words.afterTimes(times), selected = form.end == RepeatEnd.After(times)) {
                    sheet = null
                    actions.onEnd(RepeatEnd.After(times))
                }
            }
            SheetChoice(words.onADate, selected = form.end is RepeatEnd.OnDate) { sheet = SHEET_END_DATE }
        }

        SHEET_START, SHEET_END_DATE -> {
            val picking = sheet
            val zone = remember { TimeZone.UTC }
            // The first date is today or later; the end date is the first date or later.
            val notBefore = if (picking == SHEET_END_DATE) form.start ?: state.earliestStart else state.earliestStart
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = maxOf(form.start ?: notBefore, notBefore).atStartOfDayIn(zone).toEpochMilliseconds(),
                selectableDates = remember(notBefore) { OnOrAfter(notBefore) },
            )
            DatePickerDialog(
                onDismissRequest = { sheet = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
                                if (picking == SHEET_START) actions.onStart(date) else actions.onEnd(RepeatEnd.OnDate(date))
                            }
                            sheet = null
                        },
                    ) { Text(text.common.done) }
                },
                dismissButton = { TextButton(onClick = { sheet = null }) { Text(text.common.cancel) } },
            ) { DatePicker(state = pickerState) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun SheetChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val chosen = LocalCopy.current.shared.chosen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .heightIn(min = SkySizes.transactionRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (selected) Text(chosen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The end date can't come before the first date. Material's picker speaks in UTC midnights. */
@OptIn(ExperimentalMaterial3Api::class)
private class OnOrAfter(private val first: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date >= first
}

private const val SHEET_PAYER = "payer"
private const val SHEET_STOP = "stop"
private const val SHEET_END = "end"
private const val SHEET_START = "start"
private const val SHEET_END_DATE = "end-date"

/** The Ends sheet's counts: a lease, a year, two years. */
private val ENDS_AFTER = listOf(6, 12, 24)

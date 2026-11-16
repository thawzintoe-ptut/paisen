package com.ptut.expense.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.domain.Home
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyRateRow

/** The currency the Add sheet opens on, laid out like the language list. */
@Composable
fun AddCurrencyView(
    uiState: AddCurrencyUiState,
    onBack: () -> Unit,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = currencyCopy()
    CurrencyListScreen(title = text.startsIn, intro = text.startsInIntro(Home.money.code), options = uiState.options, onBack = onBack, onChoose = onChoose, modifier = modifier)
}

/** #41: the ledger's home currency. [note] says why a choice did not apply straight away. */
@Composable
fun HomeCurrencyView(
    uiState: HomeCurrencyUiState,
    onBack: () -> Unit,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onRate: (String) -> Unit = {},
    onConfirmConvert: () -> Unit = {},
    onCancelConvert: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    uiState.converting?.let { ConvertSheet(it, onRate, onConfirmConvert, onCancelConvert, onExport) }
    CurrencyListScreen(
        title = currencyCopy().homeTitle,
        intro = uiState.intro,
        options = uiState.options,
        onBack = onBack,
        onChoose = onChoose,
        modifier = modifier,
        note = uiState.note,
    )
}

/** #47: the rate a ledger with rows converts at, and a way to keep a copy before it does. */
@Composable
private fun ConvertSheet(converting: ConvertUiModel, onRate: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit, onExport: () -> Unit) {
    val words = currencyCopy()
    SkyBottomSheet(onDismissRequest = onCancel) {
        Text(converting.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        Text(words.convertBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        SkyRateRow(
            rateText = converting.rateText,
            unit = converting.unit,
            homeLine = converting.example,
            onRate = onRate,
            focusOnStart = true,
            homeSymbol = converting.newSymbol,
            note = null,
            modifier = Modifier.padding(top = Spacing.s12),
        )
        converting.problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.ptut.warnInk, modifier = Modifier.padding(top = Spacing.xs)) }
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            SkyButton(label = words.backupFirst, onClick = onExport, kind = SkyButtonKind.Outline, enabled = !converting.working, modifier = Modifier.weight(1f))
            SkyButton(label = words.convertAction, onClick = onConfirm, enabled = !converting.working, modifier = Modifier.weight(1f))
        }
    }
}

/** A currency list with one row ticked, shared by Add starts in and Home currency. */
@Composable
private fun CurrencyListScreen(
    title: String,
    intro: String,
    options: List<CurrencyOptionUiModel>,
    onBack: () -> Unit,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        item { SkyIntro(intro) }
        note?.let { item { SkyNoticeCard(text = it, icon = PtutIcon.Info.image, modifier = Modifier.padding(horizontal = Spacing.sm)) } }
        item { SkyGroupTitle(currencyCopy().currencyTitle) }
        item {
            SkyGroupedCard(
                rows = options.map<CurrencyOptionUiModel, @Composable () -> Unit> { option ->
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SkySizes.keyValueRow)
                                .selectable(selected = option.chosen, role = Role.RadioButton, onClick = { onChoose(option.code) })
                                .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s14),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (option.chosen) {
                                Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsGlyph))
                            }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
    }
}

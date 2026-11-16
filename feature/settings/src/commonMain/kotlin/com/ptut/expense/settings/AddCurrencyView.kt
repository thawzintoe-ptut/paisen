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
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen

/** The currency the Add sheet opens on, laid out like the language list. */
@Composable
fun AddCurrencyView(
    uiState: AddCurrencyUiState,
    onBack: () -> Unit,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = currencyCopy()
    SkyListScreen(title = text.startsIn, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.startsInIntro) }
        item { SkyGroupTitle(text.currencyTitle) }
        item {
            SkyGroupedCard(
                rows = uiState.options.map<AddCurrencyOptionUiModel, @Composable () -> Unit> { option ->
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

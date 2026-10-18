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
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen

/** English or Burmese, each named in its own language, laid out like Keep unread slips. */
@Composable
fun LanguageView(
    uiState: LanguageUiState,
    onBack: () -> Unit,
    onChoose: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.language
    SkyListScreen(title = text.title, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.intro) }
        item { SkyGroupTitle(text.groupTitle) }
        item {
            SkyGroupedCard(
                rows = uiState.options.map<LanguageOptionUiModel, @Composable () -> Unit> { option ->
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SkySizes.keyValueRow)
                                .selectable(selected = option.chosen, role = Role.RadioButton, onClick = { onChoose(option.language) })
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

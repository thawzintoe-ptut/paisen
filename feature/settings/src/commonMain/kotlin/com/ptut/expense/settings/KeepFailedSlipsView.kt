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
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyNoticeCard

/**
 * NFR-4: how long a slip that could not be filed keeps its picture.
 *
 * The choice is made against what is actually waiting, not against a rule in the abstract — the
 * count and the soonest deadline sit above the options, because "7 days" means nothing until the
 * reader knows something is sitting there with two of them left.
 *
 * C-3 is stated here too. Settings used to carry a slip-language row that only stated a fact, and
 * this is the screen where that fact has consequences the reader is already looking at.
 */
@Composable
fun KeepFailedSlipsView(
    uiState: KeepFailedSlipsUiState,
    onBack: () -> Unit,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.settings
    SkyListScreen(title = text.keepUnreadSlips, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.keepIntro) }
        item {
            SkyNoticeCard(
                title = uiState.kept,
                text = uiState.deadline ?: text.cleanSlipDeleted,
                icon = PtutIcon.Inbox.image,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        // C-3, where the reader meets the slips a language mismatch produced.
        item {
            SkyNoticeCard(
                title = text.englishSlipsTitle,
                text = text.englishSlipsText,
                icon = PtutIcon.Translate.image,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        item { SkyGroupTitle(text.keepThemFor) }
        item {
            // `selectable` with a radio role, so a screen reader hears one of four choices.
            SkyGroupedCard(
                rows = uiState.options.map<RetentionOptionUiModel, @Composable () -> Unit> { option ->
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SkySizes.keyValueRow)
                                .selectable(selected = option.chosen, role = Role.RadioButton, onClick = { onChoose(option.days) })
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
        item {
            Text(
                text = text.noForever,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}

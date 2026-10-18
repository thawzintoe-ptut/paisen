package com.ptut.expense.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyNoticeCard

/**
 * FR-19: every total in this app is counted into a Bangkok day.
 *
 * There is no picker here and there will not be one. The screen exists because the row promised
 * something behind it, and because a reader in another country deserves to be told once, plainly,
 * why yesterday's dinner landed on the day it did — rather than finding out from a total.
 */
@Composable
fun TimezoneView(
    uiState: TimezoneUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.settings
    SkyListScreen(title = text.timezone, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.timezoneIntro) }
        item {
            SkyNoticeCard(
                title = uiState.zone,
                text = text.timezoneFixed,
                icon = PtutIcon.Public.image,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        item { SkyGroupTitle(text.dayYouAreIn) }
        item {
            // The boundary against a real date: the one line here that is not a constant.
            SkyGroupedCard(
                rows = listOf {
                    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
                        Text(uiState.today, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = uiState.window,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.s2),
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        item {
            Text(
                text = text.timezoneFootnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}

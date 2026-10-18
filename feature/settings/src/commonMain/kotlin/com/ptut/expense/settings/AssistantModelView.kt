package com.ptut.expense.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyNoticeCard

/**
 * #207: what the model does, how big it is, and where it comes from, said before a download and
 * not after. Without it the app works as before: Quick add stays hidden and Siri still fills forms.
 */
@Composable
fun AssistantModelView(
    uiState: AssistantModelUiState,
    onAct: (AssistantAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.settings
    SkyListScreen(title = text.assistant, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.assistantIntro) }
        item {
            SkyNoticeCard(
                title = uiState.status,
                text = text.assistantPrivacy,
                icon = PtutIcon.AutoAwesome.image,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        uiState.action?.let { action ->
            item {
                SkyButton(
                    label = if (action == AssistantAction.Download) text.downloadOnWifi else text.removeFromPhone,
                    onClick = { onAct(action) },
                    kind = if (action == AssistantAction.Download) SkyButtonKind.Ink else SkyButtonKind.DangerOutline,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                )
            }
        }
        item {
            Text(
                text = text.shortCommands,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}

package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy

/**
 * #214: system back and the iOS edge swipe ask first while [changed] is true. Off otherwise, so an
 * untouched form still closes at once and the platform's own back stays in charge.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AskBeforeLeaving(changed: Boolean, onAsk: () -> Unit) {
    BackHandler(enabled = changed, onBack = onAsk)
}

/** #214: "Discard changes?" before a changed form closes. Keep editing is the first and safe choice. */
@Composable
fun SkyDiscardSheet(onKeep: () -> Unit, onDiscard: () -> Unit) {
    val text = LocalCopy.current.common
    SkyBottomSheet(onDismissRequest = onKeep) {
        Text(text.discardTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(
            text.discardBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(text.keepEditing, onClick = onKeep, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SkyButton(text.discard, onClick = onDiscard, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

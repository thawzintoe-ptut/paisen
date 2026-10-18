package com.ptut.expense.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind

/**
 * Today's Fix when scans fail: what went wrong, and the exact choice to make on the system page
 * before it opens, because the app cannot open the Photos row itself.
 */
@Composable
fun AllowAllPhotosSheet(steps: String, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.home
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text.allowAllTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text.allowAllBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            steps,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.s12),
        )
        SkyButton(text.openSettings, onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SkyButton(text.notNow, onClick = onDismiss, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

package com.ptut.expense.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.noteCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind

/**
 * #22: "That's not right" on the weekly note. Planned and a wrong category both lead to last
 * week's spending in History, where each record can be fixed; hiding drops the note for this week.
 */
@Composable
fun NoteNotRightSheet(onPlanned: () -> Unit, onWrongCategory: () -> Unit, onHideWeek: () -> Unit, onDismiss: () -> Unit) {
    val words = noteCopy()
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            words.whatsOff,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            words.sheetHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(words.somePlanned, onClick = onPlanned, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SkyButton(words.wrongCategory, onClick = onWrongCategory, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
        SkyButton(words.hideWeek, onClick = onHideWeek, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

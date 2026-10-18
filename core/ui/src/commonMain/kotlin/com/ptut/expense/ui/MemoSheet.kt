package com.ptut.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind

/** The note line on Edit purchase and the add sheet (#211): the note itself, or "Add a note". */
@Composable
fun NoteRow(memo: String?, onOpenMemo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.keyValueRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onOpenMemo)
            .padding(vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(LocalCopy.current.common.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            text = memo ?: LocalCopy.current.shell.addANote,
            style = MaterialTheme.typography.titleSmall,
            color = if (memo == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A sheet for the note, so a soft keyboard opens away from the keypad and never covers the amount
 * (#147, R6). Edit purchase and the add sheet share it (#211).
 */
@Composable
fun MemoSheet(memo: String?, onMemo: (String) -> Unit, onDismiss: () -> Unit) {
    // Saveable, so the half-typed note survives a rotation the way the record behind it does.
    var text by rememberSaveable(memo) { mutableStateOf(memo.orEmpty()) }
    val noteLabel = LocalCopy.current.common.note
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(LocalCopy.current.common.note, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(top = Spacing.s12)
                .fillMaxWidth()
                .heightIn(min = SkySizes.search)
                .clip(PtutRadii.r16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.sm),
        ) {
            if (text.isEmpty()) {
                Text(LocalCopy.current.shell.notePlaceholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = noteLabel },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(label = LocalCopy.current.common.cancel, onClick = onDismiss, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton(label = LocalCopy.current.shell.keepNote, onClick = { onMemo(text) }, modifier = Modifier.weight(1.6f))
        }
    }
}

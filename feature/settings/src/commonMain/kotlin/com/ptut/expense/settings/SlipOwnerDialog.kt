package com.ptut.expense.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.incomeSlipCopy

/** #26: your name as slips print it. Empty saves as "learn it again from my own slips". */
@Composable
fun SlipOwnerDialog(current: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val words = incomeSlipCopy()
    var typed by rememberSaveable { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(words.ownerTitle) },
        text = {
            Column {
                Text(words.ownerHint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.padding(top = Spacing.s12),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(typed.trim().ifEmpty { null }) }) { Text(words.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(words.cancel) } },
    )
}

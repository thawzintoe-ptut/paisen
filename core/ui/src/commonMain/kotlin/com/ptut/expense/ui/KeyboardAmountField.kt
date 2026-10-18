package com.ptut.expense.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.ptut.expense.design.ptutType

/**
 * The amount, typed with the number keyboard (#204): Check slip opens it on an unread amount or a
 * tap, the add sheet as soon as it opens. A supplied amount opens selected, so the first digit
 * replaces it, the way [AmountEntry.pending] works on a pad.
 *
 * [fillWidth] is false where the field sits beside other controls, as the add sheet's amount sits
 * beside Expense / Income, so it is as wide as its digits.
 */
@Composable
fun KeyboardAmountField(
    entry: AmountEntry,
    onType: (AmountEntry) -> Unit,
    label: String,
    openOnStart: Boolean,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(entry.typed)) }
    var focused by remember { mutableStateOf(false) }
    val open = {
        field = TextFieldValue(entry.typed, entry.selection())
        focusRequester.requestFocus()
    }
    val openNow by rememberUpdatedState(open)
    LaunchedEffect(Unit) { if (openOnStart) openNow() }

    val ink = MaterialTheme.colorScheme.onSurface
    Box(contentAlignment = Alignment.CenterStart, modifier = modifier) {
        if (entry.typed.isEmpty()) Text("0", style = MaterialTheme.ptutType.sheet, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            // Follows the entry if it changed under the field, so the two never disagree.
            value = if (field.text == entry.typed) field else TextFieldValue(entry.typed, entry.selection()),
            onValueChange = { next ->
                val typed = AmountEntry.ofText(next.text) ?: return@BasicTextField
                field = next.copy(text = typed.typed)
                if (typed.typed != entry.typed) onType(typed)
            },
            singleLine = true,
            textStyle = MaterialTheme.ptutType.sheet.copy(color = ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
        )
        // Until the field has focus, a tap selects the figure rather than dropping a cursor in it.
        if (!focused) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { openNow() } })
    }
}

/** A supplied figure is selected whole; one being typed keeps the cursor at its end. */
private fun AmountEntry.selection(): TextRange = if (pending) TextRange(0, typed.length) else TextRange(typed.length)

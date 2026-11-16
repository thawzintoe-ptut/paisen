package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.currencyCopy

/**
 * Baht for one [unit] of another currency, typed: "Rate [8.2] ฿ per 1,000 K", then what the entry
 * saves as. [onTodaysRate] draws a link to a page that shows the day's rate, where one is known.
 */
@Composable
fun SkyRateRow(
    rateText: String,
    unit: String,
    bahtLine: String?,
    onRate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTodaysRate: (() -> Unit)? = null,
    /** Off inside a sheet whose title already names the rate. */
    showLabel: Boolean = true,
    /** On in a sheet opened to type the rate, so the keyboard comes up with it. */
    focusOnStart: Boolean = false,
) {
    val words = currencyCopy()
    val perUnit = words.perUnit(unit)
    val focus = remember { FocusRequester() }
    if (focusOnStart) LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (showLabel) Text(words.rateLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = rateText,
                onValueChange = { typed -> onRate(typed.filter { it.isDigit() || it == '.' }) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .focusRequester(focus)
                    .width(SkySizes.rateField)
                    .heightIn(min = SkySizes.search)
                    .clip(PtutRadii.r12)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.s12, vertical = Spacing.s10)
                    .semantics { contentDescription = "${words.rateLabel}, $perUnit" },
            )
            Text(perUnit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        bahtLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(words.rateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onTodaysRate?.let { SkyTextAction(label = words.todaysRate, onClick = it, enabled = true) }
    }
}

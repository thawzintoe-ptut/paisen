package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable

/** One row a [SkyChoiceSheet] offers: [id] comes back on a tap, [label] is drawn. */
data class SkyChoice(val id: String, val label: String)

/** A titled list with the [chosen] row ticked. The currency lists on Add and first run use it. */
@Composable
fun SkyChoiceSheet(title: String, options: List<SkyChoice>, chosen: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.sm)) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SkySizes.transactionRow)
                        .semantics { selected = option.id == chosen }
                        .tappable(PtutRadii.r12, Role.RadioButton) { onPick(option.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (option.id == chosen) {
                        Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
                    }
                }
            }
        }
    }
}

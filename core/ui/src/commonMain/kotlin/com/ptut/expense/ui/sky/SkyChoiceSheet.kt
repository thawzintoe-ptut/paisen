package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy

/** One row a [SkyChoiceSheet] offers: [id] comes back on a tap, [label] is drawn. */
data class SkyChoice(val id: String, val label: String)

/** A titled list with the [chosen] row ticked. The currency lists on Add and first run use it. */
@Composable
fun SkyChoiceSheet(title: String, options: List<SkyChoice>, chosen: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.sm)) {
            options.forEach { option ->
                SkyChoiceRow(label = option.label, selected = option.id == chosen, onClick = { onPick(option.id) })
            }
        }
    }
}

/** How a [SkyChoiceRow] marks the chosen row: a tick, or the word "Chosen" in quiet ink. */
enum class SkyChoiceMark { Tick, Word }

/**
 * One row of a pick-one list. A screen reader hears a radio button and whether it is selected, so the
 * mark itself stays silent. With a [detail] line it takes the grouped-card row's padding and sizes.
 */
@Composable
fun SkyChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PtutIcon? = null,
    detail: String? = null,
    mark: SkyChoiceMark = SkyChoiceMark.Tick,
) {
    val inCard = detail != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (inCard) SkySizes.keyValueRow else SkySizes.transactionRow)
            .semantics { this.selected = selected }
            .tappable(PtutRadii.r12, Role.RadioButton, onClick = onClick)
            .then(if (inCard) Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.s12) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(if (inCard) Spacing.s14 else Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = MaterialTheme.colorScheme.onSurface
        icon?.let { Icon(it.image, contentDescription = null, tint = ink, modifier = Modifier.size(SkySizes.barGlyph)) }
        if (detail == null) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = ink)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            when (mark) {
                SkyChoiceMark.Tick -> Icon(
                    PtutIcon.Check.image,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(if (inCard) Sizes.settingsGlyph else SkySizes.cardGlyph),
                )

                SkyChoiceMark.Word -> Text(
                    LocalCopy.current.shared.chosen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

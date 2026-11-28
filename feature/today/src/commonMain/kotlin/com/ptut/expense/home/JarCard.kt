package com.ptut.expense.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyRollingNumber

/** J01: the jar card. Its head opens the jar; on a good day the leftover is one button away. */
@Composable
internal fun JarCard(
    jar: JarCardUiModel,
    onOpen: (ShelfItemUiModel) -> Unit,
    onPutIn: (goalId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r24)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.s18),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().tappable(PtutRadii.r16, Role.Button) { onOpen(jar.open) },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(jar.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    SkyRollingNumber(
                        text = "${jar.saved.symbol}${jar.saved.whole}${jar.saved.fraction}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = "${jar.saved.symbol}${jar.saved.whole}${jar.saved.fraction} ${jar.ofTarget}" },
                    )
                    Text(jar.ofTarget, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.settingsTrailing))
        }
        // #130: the jar is the app's most colourful thing, so its meter runs from sky into ink.
        PlayMeter(
            fraction = jar.fraction,
            label = jar.label,
            fillBrush = Brush.horizontalGradient(listOf(MaterialTheme.ptut.accent, MaterialTheme.colorScheme.primary)),
        )
        jar.plan?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        jar.thisWeek?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        jar.nudge?.let { nudge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .clip(PtutRadii.r16)
                    .background(MaterialTheme.ptut.infoBg)
                    .padding(horizontal = Spacing.s14, vertical = Spacing.s12),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PtutIcon.AutoAwesome.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsTrailing))
                Text(nudge.boldening(jar.nudgeAmount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        jar.putIn?.let { SkyButton(label = it, onClick = { onPutIn(jar.goalId) }, modifier = Modifier.fillMaxWidth()) }
    }
}

/** J01: the amount inside the nudge is the part to read, so it is set bold where it appears. */
private fun String.boldening(part: String?): AnnotatedString = buildAnnotatedString {
    val at = part?.let { indexOf(it) } ?: -1
    if (part == null || at < 0) {
        append(this@boldening)
        return@buildAnnotatedString
    }
    append(substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
    append(substring(at + part.length))
}

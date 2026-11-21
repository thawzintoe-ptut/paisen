package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.ui.sky.SkyListScreen

/** #96 (p12): one card per title, a sky medal, the name and the number behind it. */
@Composable
fun GroupAwardsView(uiState: GroupAwardsUiState?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SkyListScreen(title = uiState?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        uiState ?: return@SkyListScreen
        uiState.line?.let { line ->
            item { Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.md)) }
        }
        uiState.awards.forEach { award -> item(key = award.title) { AwardCard(award) } }
    }
}

@Composable
private fun AwardCard(award: AwardUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .clip(PtutRadii.r20)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PlaySizes.medal).clip(CircleShape).background(MaterialTheme.ptut.accent)) {
            Icon(ptutIcon(award.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.settingsGlyph))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(award.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(award.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(award.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

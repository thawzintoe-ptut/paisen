package com.ptut.expense.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.play.ChallengeLevel
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.play.PlayStamp
import com.ptut.expense.ui.play.StampTone
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyListScreen

/** #91 (p05): the week's challenge, if picked, then the choices by category at three levels. */
@Composable
fun ChallengeView(uiState: ChallengeUiState?, onPick: (String, ChallengeLevel) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SkyListScreen(title = uiState?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        uiState ?: return@SkyListScreen
        uiState.current?.let { current -> item { CurrentChallenge(current) } }
        uiState.none?.let { line -> item { PlayLine(line, Modifier.padding(horizontal = Spacing.md)) } }
        if (uiState.groups.isNotEmpty()) item { SkyGroupTitle(uiState.pick, Modifier.padding(top = Spacing.xs)) }
        uiState.groups.forEach { group ->
            item(key = group.name) {
                Row(Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ptutIcon(group.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsGlyph))
                    Text(group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            group.options.forEach { option -> item(key = "${option.categoryId}-${option.level}") { ChallengeOption(option, onPick) } }
        }
        item {
            // "If you make it": the stamp still to earn, so the reward is seen before the pick.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).padding(top = Spacing.xs).clip(PtutRadii.r20).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(uiState.ifMade.uppercase(), style = MaterialTheme.ptutType.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
                    PlayStamp(face = uiState.stampFace, caption = uiState.stampCaption, tone = StampTone.Empty, contentDescription = uiState.note)
                    Text(uiState.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CurrentChallenge(current: CurrentChallengeUiModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).clip(PtutRadii.r20).background(MaterialTheme.colorScheme.tertiaryContainer).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(current.line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        PlayMeter(fraction = current.fraction, label = current.progress)
        Text(current.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        current.over?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** `.pl-option`: a checked option wears an ink ring; nothing is ever ruby here. */
@Composable
private fun ChallengeOption(option: ChallengeOptionUiModel, onPick: (String, ChallengeLevel) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .heightIn(min = PlaySizes.option)
            .tappable(PtutRadii.r18, Role.RadioButton) { onPick(option.categoryId, option.level) }
            .clip(PtutRadii.r18)
            .background(scheme.tertiaryContainer)
            .then(if (option.picked) Modifier.border(PlaySizes.dayRing, scheme.primary, PtutRadii.r18) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        // `.pl-radio`: an outline ring, filled thick in ink once picked.
        Box(
            Modifier
                .size(PlaySizes.radio)
                .border(if (option.picked) PlaySizes.radioPicked else PlaySizes.dayRing, if (option.picked) scheme.primary else scheme.onSurfaceVariant, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(option.levelLabel, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(option.line, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text(option.limit, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
    }
}

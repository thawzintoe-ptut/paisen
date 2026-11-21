package com.ptut.expense.play

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.ui.play.PlayStamp
import com.ptut.expense.ui.play.StampTone
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyListScreen

/** #98 (p20): one step at a time with its one action, then the seal. */
@Composable
fun CloseMonthView(uiState: CloseMonthUiState?, onNext: () -> Unit, onSeal: () -> Unit, onOpenSlips: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SkyListScreen(title = uiState?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        uiState ?: return@SkyListScreen
        uiState.steps.forEachIndexed { index, step ->
            item(key = step.title) {
                CloseStepCard(
                    number = index + 1,
                    step = step,
                    done = index < uiState.at,
                    current = index == uiState.at,
                    onOpenSlips = onOpenSlips,
                    onNext = onNext,
                    last = uiState.last,
                    next = uiState.next,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.sealed != null) {
                    PlayStamp(face = uiState.stampFace, caption = uiState.stampCaption, tone = StampTone.Sky, contentDescription = uiState.sealed, pressed = true)
                    Text(uiState.sealed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    SkyButton(uiState.seal, onClick = onSeal, enabled = uiState.last, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** `.pl-card` with a numbered disc: ink with a tick once done; only the open step shows its action. */
@Composable
private fun CloseStepCard(number: Int, step: CloseStepUiModel, done: Boolean, current: Boolean, onOpenSlips: () -> Unit, onNext: () -> Unit, last: Boolean, next: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).clip(PtutRadii.r20).background(scheme.tertiaryContainer).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(PlaySizes.dayDot).clip(CircleShape).background(if (done) scheme.primary else scheme.surfaceContainerHigh),
            ) {
                if (done) {
                    Icon(PtutIcon.Check.image, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(Sizes.settingsGlyph))
                } else {
                    Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                step.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
            }
        }
        if (current) {
            step.action?.let { SkyButton(it, onClick = onOpenSlips, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth()) }
            if (!last) SkyButton(next, onClick = onNext, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth())
        }
    }
}

package com.ptut.expense.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.inMillis
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutReduceMotion
import com.ptut.expense.design.stampScaleAt
import com.ptut.expense.design.tappable
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyRollingNumber

/** #104 (S04, M02): the jar and its meter, and above it the sky card while today can be counted. */
@Composable
internal fun JarCard(
    jar: JarCardUiModel,
    onOpen: (ShelfItemUiModel) -> Unit,
    onAnswer: (goalId: String, count: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        jar.under?.let { under -> UnderUsualCard(under, onAnswer) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tappable(PtutRadii.r20, Role.Button) { onOpen(jar.open) }
                .clip(PtutRadii.r20)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(jar.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                SkyRollingNumber(
                    text = "${jar.saved.symbol}${jar.saved.whole}${jar.saved.fraction}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "${jar.saved.symbol}${jar.saved.whole}${jar.saved.fraction} ${jar.ofTarget}" },
                )
                PlayMeter(fraction = jar.fraction, label = jar.label)
                Text(jar.ofTarget, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (jar.pressed) CountedStamp(jar.counted)
        }
    }
}

/** The sky card: how far under a usual day today sits, with Count it and Not today. */
@Composable
private fun UnderUsualCard(under: UnderUsualUiModel, onAnswer: (goalId: String, count: Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .background(MaterialTheme.ptut.infoBg)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(under.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(under.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkyButton(label = under.count, onClick = { onAnswer(under.goalId, true) })
            SkyButton(label = under.notToday, onClick = { onAnswer(under.goalId, false) }, kind = SkyButtonKind.Outline)
        }
    }
}

/** The stamp Count it presses, 1.35× to its size as play's stamps land; with reduced motion it is simply there. */
@Composable
private fun CountedStamp(label: String) {
    val motion = MaterialTheme.ptutMotion
    val still = ptutReduceMotion
    var landed by rememberSaveable { mutableStateOf(still) }
    val progress = remember { Animatable(if (landed) 1f else 0f) }
    LaunchedEffect(still) {
        if (still || landed) return@LaunchedEffect
        progress.animateTo(1f, tween(durationMillis = motion.stampPress.inMillis, easing = motion.linear))
        landed = true
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Sizes.toastIcon * STAMP_SIZE)
            .scale(if (landed || still) 1f else stampScaleAt(progress.value, motion.press))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(Sizes.toastIcon))
    }
}

/** The stamp is half as wide again as the tick a caught slip wears. */
private const val STAMP_SIZE = 1.5f

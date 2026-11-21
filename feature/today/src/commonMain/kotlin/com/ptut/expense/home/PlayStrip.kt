package com.ptut.expense.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.play.DayMark
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind

/** #85, Main's run card: the run, its freezes, this week's seven days and the next step. */
@Composable
internal fun PlayRunCard(
    strip: PlayStripUiModel,
    onOpenStamps: () -> Unit,
    onTurnStamp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .background(scheme.tertiaryContainer)
            .padding(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            Column(Modifier.weight(1f)) {
                Text(strip.eyebrow.uppercase(), style = MaterialTheme.ptutType.eyebrow, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s10)) {
                    Text(strip.runTitle, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                    strip.freezes?.let { FreezeToken(it) }
                }
            }
            SkyButton(label = strip.stampsLabel, onClick = onOpenStamps, kind = SkyButtonKind.Outline)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            strip.dots.forEach { DayDot(it) }
        }
        Text(
            text = strip.nextLine,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s12),
        )
        strip.stampId?.let { id ->
            SkyButton(
                label = strip.turnLabel,
                onClick = { onTurnStamp(id) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
        }
    }
}

/** A freeze is a sky pill with ink words: a thing kept, never a warning. */
@Composable
private fun FreezeToken(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
        modifier = Modifier
            .heightIn(min = PlaySizes.token)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.accent)
            .padding(horizontal = Spacing.s10),
    ) {
        Icon(PtutIcon.VerifiedUser.image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.statusGlyph))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** A missed day is a plain blank, never red (R7). Today wears an ink ring with a gap of page inside it. */
@Composable
private fun DayDot(dot: DayDotUiModel) {
    val scheme = MaterialTheme.colorScheme
    val (fill, ink) = when (dot.mark) {
        DayMark.Cleared -> scheme.primary to scheme.onPrimary
        DayMark.Frozen -> MaterialTheme.ptut.accent to scheme.primary
        DayMark.Missed, DayMark.Today, DayMark.Ahead -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        modifier = Modifier.clearAndSetSemantics { contentDescription = dot.label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PlaySizes.dayDot)
                .then(
                    if (dot.mark == DayMark.Today) {
                        Modifier.border(PlaySizes.dayRing, scheme.primary, CircleShape).padding(PlaySizes.dayRing * 2)
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape)
                .background(fill),
        ) {
            when {
                dot.mark == DayMark.Cleared -> Icon(PtutIcon.Check.image, contentDescription = null, tint = ink, modifier = Modifier.size(Sizes.statusGlyph))
                dot.mark == DayMark.Frozen -> Icon(PtutIcon.VerifiedUser.image, contentDescription = null, tint = ink, modifier = Modifier.size(Sizes.statusGlyph))
                dot.waiting != null -> Text("${dot.waiting}", style = MaterialTheme.typography.labelSmall, color = ink)
            }
        }
        Text(dot.letter, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

/** #87, Main's ink Play card: the whole card opens today's question. */
@Composable
internal fun PlayQuestionCard(card: PlayQuestionCardUiModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tappable(PtutRadii.r20, Role.Button, onClick = onOpen)
            .clip(PtutRadii.r20)
            .background(scheme.primary)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.s14),
    ) {
        Text(card.eyebrow.uppercase(), style = MaterialTheme.ptutType.eyebrow, color = scheme.onPrimary.copy(alpha = EYEBROW_ALPHA))
        Text(card.title, style = MaterialTheme.typography.titleLarge, color = scheme.onPrimary)
        Text(
            text = card.action.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.ptut.accent).padding(horizontal = Spacing.md, vertical = Spacing.s10),
        )
    }
}

/** #89: PaiSeN's one line, its disc and its number; the tap opens the rows behind it (FR-89.2). */
@Composable
internal fun CoachRow(coach: CoachLineUiModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PlaySizes.step)
            .clearAndSetSemantics { contentDescription = coach.label }
            .tappable(PtutRadii.r16, Role.Button, onClick = onOpen)
            .clip(PtutRadii.r16)
            .background(scheme.surface)
            .padding(horizontal = Spacing.s12, vertical = Spacing.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s10),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PlaySizes.aiDisc).clip(CircleShape).background(scheme.primary)) {
            Text(coach.disc, style = MaterialTheme.typography.labelMedium, color = scheme.onPrimary)
        }
        Text(coach.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.settingsTrailing))
    }
}

/** The eyebrow on the ink card reads at four fifths, as the artboard sets it. */
private const val EYEBROW_ALPHA = 0.8f

/** #91: the week's challenge with its meter; the tap opens p05. */
@Composable
internal fun ChallengeRow(row: ChallengeRowUiModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tappable(PtutRadii.r20, Role.Button, onClick = onOpen)
            .clip(PtutRadii.r20)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(row.line, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        PlayMeter(fraction = row.fraction, label = row.progress)
        Text(row.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The ways into the extras, one pill each, scrolling sideways when they outgrow the row. */
@Composable
internal fun PlayShelf(items: List<ShelfItemUiModel>, onOpen: (ShelfItemUiModel) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
                modifier = Modifier
                    .heightIn(min = PlaySizes.step)
                    .tappable(CircleShape, Role.Button) { onOpen(item) }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = Spacing.s12),
            ) {
                Icon(ptutIcon(item.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.statusGlyph))
                Text(item.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

package com.ptut.expense.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.STAMP_TILT
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.play.PlayStamp
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyListScreen

/** Three stamps a row in the book (p04). */
private const val STAMPS_PER_ROW = 3

private const val DAYS_PER_ROW = 7

/** #88, p04: the month's no-spend days, the next set, then each set with its empty slots. */
@Composable
fun StampBookView(uiState: StampBookUiState?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SkyListScreen(title = uiState?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        uiState ?: return@SkyListScreen
        item { NoSpendCalendar(uiState, Modifier.padding(horizontal = Spacing.sm)) }
        item { NextSetCard(uiState, Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.s12)) }
        uiState.sets.forEach { set ->
            item(key = set.name) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(set.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(set.count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            set.stamps.chunked(STAMPS_PER_ROW).forEachIndexed { index, row ->
                item(key = "${set.name}-$index") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                    ) {
                        row.forEach { stamp -> PlayStamp(face = stamp.face, caption = stamp.caption, tone = stamp.tone, contentDescription = stamp.label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoSpendCalendar(state: StampBookUiState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().clip(PtutRadii.r20).background(scheme.tertiaryContainer).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.s6),
    ) {
        Text(state.noSpendTitle, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(state.noSpendLine, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s6)) {
            state.weekLetters.forEach { letter ->
                Text(letter, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        state.calendar.chunked(DAYS_PER_ROW).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s6)) {
                week.forEach { cell -> CalendarCell(cell, Modifier.weight(1f)) }
                repeat(DAYS_PER_ROW - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarCell(cell: CalendarDayUiModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (cell.day == null) {
        Spacer(modifier.height(PlaySizes.step))
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(PlaySizes.step)
            .clearAndSetSemantics { contentDescription = cell.label }
            .clip(PtutRadii.r12)
            .then(if (cell.noSpend) Modifier.background(MaterialTheme.ptut.accent) else Modifier)
            .then(if (cell.today) Modifier.border(PlaySizes.dayRing, scheme.primary, PtutRadii.r12) else Modifier),
    ) {
        if (cell.noSpend) {
            Box(Modifier.padding(Spacing.xxs).rotate(STAMP_TILT).border(PlaySizes.stampInnerRing, scheme.primary, PtutRadii.r12).padding(Spacing.xxs)) {
                Text(cell.day, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
        } else {
            Text(cell.day, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        }
    }
}

@Composable
private fun NextSetCard(state: StampBookUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ptut
    Column(
        modifier = modifier.fillMaxWidth().clip(PtutRadii.r20).background(colors.heroBg).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.nextSetTitle, style = MaterialTheme.typography.titleMedium, color = colors.heroOn, modifier = Modifier.weight(1f))
            state.nextSet?.let { Text(it.count, style = MaterialTheme.typography.titleMedium, color = colors.heroOn) }
        }
        state.nextSet?.let { set ->
            PlayMeter(fraction = set.fraction, label = set.line, track = colors.heroInner)
            Text(set.note, style = MaterialTheme.typography.bodyMedium, color = colors.heroOn)
        }
    }
}

package com.ptut.expense.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutType
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * The day receipt (p09, #120): white paper on the grey page, perforated top and bottom, set in
 * Geist Mono. Each line is time, shop, a dotted leader and the amount; then a subtotal per
 * category, a ruled TOTAL, and the usual-day line. The offline promise sits under the paper.
 */
@Composable
fun DayReceiptView(uiState: DayReceiptUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.receipt
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SkyTopBar(title = (uiState as? DayReceiptUiState.Loaded)?.title.orEmpty(), onBack = onBack)
        if (uiState is DayReceiptUiState.Loaded) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Paper(uiState)
                Text(
                    text = text.keptOnPhone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun Paper(state: DayReceiptUiState.Loaded) {
    val text = LocalCopy.current.receipt
    val paper = MaterialTheme.colorScheme.tertiaryContainer
    val page = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val type = MaterialTheme.ptutType
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .perforated(paper = paper, page = page)
            .padding(horizontal = Spacing.md, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PAISEN", style = type.receiptHeading, color = ink)
        Text(state.heading, style = type.receipt, color = faint, modifier = Modifier.padding(top = Spacing.xxs))
        DashedRule(color = faint, modifier = Modifier.padding(vertical = Spacing.s12))

        if (state.empty != null) {
            Text(state.empty, style = type.receipt, color = faint, textAlign = TextAlign.Center)
            return@Column
        }

        state.lines.forEach { line -> ReceiptLine(line, paper = paper, ink = ink, faint = faint) }
        DashedRule(color = faint, modifier = Modifier.padding(vertical = Spacing.s12))
        state.subtotals.forEach { subtotal ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                Text(subtotal.category, style = type.receipt, color = faint, modifier = Modifier.weight(1f))
                Text(subtotal.amount, style = type.receipt, color = faint)
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = Spacing.s12).height(Spacing.s2).background(ink))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.total, style = type.receiptHeading, color = ink, modifier = Modifier.weight(1f))
            Text(state.total, style = type.receiptTotal, color = ink)
        }
        state.closing?.let { closing ->
            Text(closing, style = type.receipt, color = faint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

/** Read as one sentence, "08:12, 7-Eleven Sathon, 128.00 baht", never the dots. */
@Composable
private fun ReceiptLine(line: ReceiptLineUiModel, paper: Color, ink: Color, faint: Color) {
    val type = MaterialTheme.ptutType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
            .clearAndSetSemantics { contentDescription = line.spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line.time, style = type.receipt, color = faint)
        Spacer(Modifier.width(Spacing.s6))
        // The leader runs under the shop's name; the name's paper-coloured box covers its start.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            DottedLeader(color = faint, modifier = Modifier.fillMaxWidth())
            Text(
                text = line.shop,
                style = type.receipt,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(paper).padding(end = Spacing.s6),
            )
        }
        Text(
            text = line.amount,
            style = type.receipt,
            color = ink,
            modifier = Modifier.background(paper).padding(start = Spacing.s6),
        )
    }
}

@Composable
private fun DottedLeader(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.height(Spacing.s2)) {
        val dot = Spacing.s2.toPx()
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = dot / 2,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dot / 2, dot)),
        )
    }
}

@Composable
private fun DashedRule(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(Spacing.s2)) {
        val dash = Spacing.xxs.toPx()
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height / 2,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
    }
}

/** The paper, with a row of page-coloured bites along the top and bottom edges: a torn-off receipt. */
private fun Modifier.perforated(paper: Color, page: Color): Modifier = drawWithContent {
    drawRect(paper)
    drawContent()
    val radius = Spacing.xxs.toPx()
    val step = radius * 3
    var x = radius
    while (x < size.width) {
        drawCircle(color = page, radius = radius, center = Offset(x, 0f))
        drawCircle(color = page, radius = radius, center = Offset(x, size.height))
        x += step
    }
}

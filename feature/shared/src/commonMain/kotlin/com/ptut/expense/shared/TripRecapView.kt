package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.platform.share.encodePngOffMain
import com.ptut.expense.platform.share.rememberShareSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyListScreen
import kotlinx.coroutines.launch

/** The smallest a day's bar draws, so a quiet day still shows it was there. */
private const val MIN_BAR = 0.04f

/** #97 (p13): the headline per person per day, a bar a day, the next trip, and the card to share. */
@Composable
fun TripRecapView(
    uiState: TripRecapUiState,
    onStartJar: (name: String, target: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val share = rememberShareSheet()
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val title = when (uiState) {
        TripRecapUiState.Missing -> ""
        is TripRecapUiState.None -> uiState.title
        is TripRecapUiState.Loaded -> uiState.title
    }
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        when (uiState) {
            TripRecapUiState.Missing -> Unit

            is TripRecapUiState.None -> item { Text(uiState.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.md)) }

            is TripRecapUiState.Loaded -> {
                item { RecapCard(uiState, showAmounts = true, modifier = Modifier.padding(horizontal = Spacing.sm)) }
                item { NextTripCard(uiState, onStartJar, Modifier.padding(horizontal = Spacing.sm)) }
                item {
                    // The picture that is sent, shown before it goes: amounts only when Settings › Play shows them.
                    RecapCard(
                        uiState,
                        showAmounts = uiState.shareAmounts,
                        modifier = Modifier
                            .padding(horizontal = Spacing.xl)
                            .padding(top = Spacing.md)
                            .drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            },
                    )
                }
                item {
                    SkyButton(
                        uiState.share,
                        onClick = { scope.launch { share.sharePng(layer.toImageBitmap().encodePngOffMain()) } },
                        kind = SkyButtonKind.Outline,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapCard(state: TripRecapUiState.Loaded, showAmounts: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ptut
    Column(
        modifier = modifier.fillMaxWidth().clip(PtutRadii.r20).background(colors.heroBg).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (showAmounts) {
            Text(state.perPersonPerDay, style = MaterialTheme.typography.displaySmall, color = colors.heroOn)
            Text(state.perPersonLabel, style = MaterialTheme.typography.bodyMedium, color = colors.heroOn)
        }
        Text(state.line, style = MaterialTheme.typography.titleMedium, color = colors.heroOn)
        if (showAmounts) Text(state.total, style = MaterialTheme.typography.bodySmall, color = colors.heroOn)
        Row(
            modifier = Modifier.fillMaxWidth().height(SkySizes.chart / 2).padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.bars.forEach { bar ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(bar.fraction.coerceAtLeast(MIN_BAR))
                        .clearAndSetSemantics { contentDescription = bar.label }
                        .clip(PtutRadii.r12)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** p13: PaiSeN's card under the recap: the next trip's cost, the plan in a line, and a jar to start. */
@Composable
private fun NextTripCard(state: TripRecapUiState.Loaded, onStartJar: (name: String, target: String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().clip(PtutRadii.r20).background(scheme.tertiaryContainer).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.size(PlaySizes.aiDisc).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                Text("P", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimary)
            }
            Text(state.nextTripTitle, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Text(state.nextTripAmount, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Text(state.nextTrip, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        state.startJar?.let { label ->
            SkyButton(label, onClick = { onStartJar(state.jarName, state.jarTarget) }, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs))
        }
    }
}

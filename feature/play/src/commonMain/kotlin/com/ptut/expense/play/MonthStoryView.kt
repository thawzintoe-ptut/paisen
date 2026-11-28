package com.ptut.expense.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.platform.share.encodePngOffMain
import com.ptut.expense.platform.share.rememberShareSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkySwitchRow
import kotlinx.coroutines.launch

/** The smallest card on screen, so five cards read as a story and not a list. */
private const val CARD_HEIGHT_STEPS = 6

/** #90 (p03): bars on top for the five cards, one card at a time, then share with amounts hidden by default. */
@Composable
fun MonthStoryView(uiState: MonthStoryUiState?, onNext: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val share = rememberShareSheet()
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // Starts from Settings › Play's choice; this screen's switch changes it for this story only.
    var showAmounts by rememberSaveable(uiState?.shareAmounts) { mutableStateOf(uiState?.shareAmounts == true) }
    SkyListScreen(title = uiState?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        uiState ?: return@SkyListScreen
        uiState.none?.let { line -> item { PlayLine(line, Modifier.padding(horizontal = Spacing.md)) } }
        val card = uiState.card ?: return@SkyListScreen
        item {
            // `.pl-bars`: five steps, the read ones filled.
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                uiState.cards.indices.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(PlaySizes.bar)
                            .clip(CircleShape)
                            .background(if (index <= uiState.index) MaterialTheme.colorScheme.primary else MaterialTheme.ptut.heroInner),
                    )
                }
            }
        }
        item {
            // The card on screen is the picture that is sent (FR-90.3), so it is recorded here, not drawn twice.
            StoryCardFace(
                card,
                showAmount = showAmounts,
                hidden = uiState.hidden,
                modifier = Modifier
                    .padding(horizontal = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            )
        }
        item { SkyButton(uiState.next, onClick = onNext, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) }
        item {
            SkySwitchRow(
                label = uiState.showAmounts,
                hint = uiState.showAmountsHint,
                checked = showAmounts,
                onCheckedChange = { showAmounts = it },
                modifier = Modifier.padding(horizontal = Spacing.md),
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

@Composable
private fun StoryCardFace(card: StoryCardUiModel, showAmount: Boolean, modifier: Modifier = Modifier, hidden: String = "") {
    val colors = MaterialTheme.ptut
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = PlaySizes.step * CARD_HEIGHT_STEPS).clip(PtutRadii.r28).background(colors.heroBg).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // p03: the title first, then its figure big, then the line under it.
        Text(if (showAmount) card.title else card.hiddenTitle, style = MaterialTheme.typography.titleLarge, color = colors.heroOn)
        card.figure?.let { figure ->
            Text(
                text = if (showAmount) figure else hidden,
                style = MaterialTheme.ptutType.hero.copy(fontSize = 40.sp),
                color = colors.heroOn,
            )
        }
        // A line names amounts, so it goes while they are hidden rather than repeating the mask.
        if (showAmount) card.line?.let { line -> Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.heroOn) }
    }
}

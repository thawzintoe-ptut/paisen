package com.ptut.expense.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.format
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.locale.SlipsCopy
import com.ptut.expense.ui.DisplayAmount
import com.ptut.expense.ui.SlipPicture
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyStatusPill
import com.ptut.expense.ui.sky.SkyStatusPillTone
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * The review queue in Sky, "To check" (p12): a headline count, the line that says none of it is
 * counted yet, and one card per slip with Check slip and an outlined discard.
 *
 * Kept beyond the artboard (#146): the retention note with its days left (K11), the filled AMOUNT UNCLEAR
 * pill (K12), the date, time and reference line, and "Nothing needs you" when it is empty. An
 * unread amount says "not counted", never a guessed digit (#147, R1).
 */
@Composable
fun ReviewView(
    state: ReviewUiState,
    onOpenSlip: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.slips
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SkyTopBar(title = text.reviewTitle, onBack = onBack)
        if (state.slips.isEmpty()) {
            SkyEmptyState(
                headline = text.reviewEmptyHeadline,
                body = text.reviewEmptyBody,
                status = text.reviewEmptyStatus(state.filedAutomatically),
                statusIcon = PtutIcon.Bolt.image,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm, bottom = Spacing.xs)) {
                    Text(
                        text = text.reviewHeadline(state.slips.size),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = text.reviewNotCounted,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                    // NFR-4, with the days the oldest has left (K11).
                    Text(
                        text = state.retentionNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            items(state.slips, key = { it.id }) { slip ->
                QueuedSlipCard(
                    slip = slip,
                    onOpen = { onOpenSlip(slip.id) },
                    onDiscard = { onDiscard(slip.id) },
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }

            item { Box(Modifier.height(SkySizes.pillClearance)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueuedSlipCard(
    slip: QueuedSlipUi,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.slips
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r24)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SlipPicture(
                id = slip.id,
                shape = PtutRadii.r16,
                maxSide = Sizes.slipThumb,
                modifier = Modifier.width(Sizes.slipThumbWidth).height(Sizes.slipThumb),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        SkyStatusPill(text.notCountedPill)
                        // Filled so it gets noticed before the button under it is pressed (K12).
                        if (slip.reason == ReviewReason.AmountUnclear) {
                            SkyStatusPill(text.amountUnclearPill, tone = SkyStatusPillTone.Loud)
                        }
                    }
                    (slip.amount as? DisplayAmount.Exact)?.let { exact ->
                        Text(
                            text = exact.amount.format(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
                Text(
                    text = slip.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Text(
                    text = slip.reason.line(text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = slip.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (slip.amount == DisplayAmount.NotCounted) {
                    // Where the amount would be: an unread one is never a zero (FR-7).
                    Text(
                        text = text.notCounted,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkyButton(label = text.checkSlip, onClick = onOpen, modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(SkySizes.button)
                    .semantics { contentDescription = text.discardSlip }
                    .tappable(CircleShape, Role.Button, onClick = onDiscard)
                    .border(SkySizes.buttonBorder, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Icon(
                    imageVector = PtutIcon.Delete.image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SkySizes.barGlyph),
                )
            }
        }
    }
}

/** p12's reason line, from fields the slip has: there is no bank field, so none is named. */
private fun ReviewReason.line(text: SlipsCopy): String = when (this) {
    ReviewReason.NewMerchant -> text.reasonNewShop
    ReviewReason.AmountUnclear -> text.reasonAmountHard
    ReviewReason.Unreadable -> text.reasonUnreadable
}

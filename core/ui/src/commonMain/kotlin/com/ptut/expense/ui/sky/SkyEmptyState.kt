package com.ptut.expense.ui.sky

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import com.ptut.expense.design.ArcLoop
import com.ptut.expense.design.PtutBrandMark
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut

/**
 * FR-20 in Sky (p24): an illustration slot, a light headline, one sentence and the status line that
 * proves the importer is alive. [status] stays required, as in [com.ptut.expense.ui.EmptyState].
 */
@Composable
fun SkyEmptyState(
    headline: String,
    body: String,
    status: String,
    statusIcon: ImageVector,
    modifier: Modifier = Modifier,
    /** The drawing (#180). A screen with none shows the app's own mark, so the slot never reads as blank. */
    illustration: (@Composable () -> Unit)? = null,
    /** The arc over the slot at half opacity (p33), for a screen waiting on slips that have not come. */
    waiting: Boolean = false,
    /** Null where waiting is the right thing to do: a button there says the reader is at fault. */
    cta: String? = null,
    onCta: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        IllustrationSlot(illustration = illustration, waiting = waiting)
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.statusGlyph),
            )
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (cta != null && onCta != null) {
            SkyButton(
                label = cta,
                onClick = onCta,
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
    }
}

@Composable
private fun IllustrationSlot(illustration: (@Composable () -> Unit)?, waiting: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(SkySizes.illustrationRatio)
            .clip(PtutRadii.r28)
            // barTrack is Sky's pale sky (#D8EFFB by day, the artboard's #D6E7F1 to the eye) and a
            // deep sky tint by night, so the empty slot needs no role of its own.
            .background(MaterialTheme.ptut.barTrack),
    ) {
        if (illustration != null) {
            illustration()
        } else {
            Image(
                painter = rememberVectorPainter(PtutBrandMark),
                contentDescription = null,
                modifier = Modifier.size(SkySizes.emptyStateMark),
            )
        }
        if (waiting) ArcLoop(size = SkySizes.waitingArc, alpha = WAITING_ARC_ALPHA)
    }
}

/** p33: "Sits over the empty-state illustration at half opacity." */
private const val WAITING_ARC_ALPHA = 0.5f

package com.ptut.expense.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind

/**
 * The way into the pipeline before #17 and #18 exist. Only a debug build binds what this needs,
 * so a release build never draws it — the panel is not hidden, it is absent.
 */
@Composable
fun DebugSlipPanel(
    summary: String,
    onInjectReadable: () -> Unit,
    onInjectSameMerchant: () -> Unit,
    onInjectUnreadable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("DEBUG · INJECT A SLIP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Runs the real importer over a transcript. Confirm the first slip, then " +
                "inject the second from the same shop to watch it file itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkyButton("Readable", onClick = onInjectReadable, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton("Same shop", onClick = onInjectSameMerchant, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton("Thai only", onClick = onInjectUnreadable, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

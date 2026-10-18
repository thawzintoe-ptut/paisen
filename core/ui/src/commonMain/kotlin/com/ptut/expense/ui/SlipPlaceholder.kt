package com.ptut.expense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut

/**
 * Where a slip picture goes while it loads, or when it is gone ([SlipPicture]).
 *
 * Striped rather than a receipt glyph: an icon reads as "this is a receipt", which the reader
 * already knows, while a pattern reads as "a picture belongs here and is not loaded". Both the
 * queue thumbnail and the confirm panel draw it, at different sizes.
 */
@Composable
fun SlipPlaceholder(shape: Shape, modifier: Modifier = Modifier, caption: String? = null) {
    val stripe = MaterialTheme.ptut.stripe
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val gap = Spacing.xs.toPx()
            val height = size.height
            // Each line rises by the same distance it runs, which is what makes it 45 degrees.
            var x = -height
            while (x < size.width) {
                drawLine(
                    color = stripe,
                    start = Offset(x, height),
                    end = Offset(x + height, 0f),
                    strokeWidth = Spacing.s2.toPx(),
                )
                x += gap
            }
        }

        if (caption != null) SlipCaption(caption)
    }
}

/** The strip under a slip picture or its placeholder (p13): "K PLUS · 14 Aug", or why it is missing. */
@Composable
internal fun SlipCaption(text: String) {
    Surface(color = MaterialTheme.ptut.slipCap, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(Spacing.xxs),
        )
    }
}

package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy

/**
 * History's search (p15): a grey pill with the magnifier and the hint inside it. The field is named
 * by its hint, which a screen reader otherwise loses once the hint is drawn over. Typed text brings a
 * clear button, and the keyboard's Search key puts the keyboard away over the results (#220).
 */
@Composable
fun SkySearchField(
    query: String,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearLabel: String = LocalCopy.current.history.clearSearch,
) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val focus = LocalFocusManager.current
    Row(
        modifier = modifier
            .heightIn(min = SkySizes.search)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = Spacing.sm, end = if (query.isEmpty()) Spacing.sm else Spacing.none),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PtutIcon.Search.image,
            contentDescription = null,
            tint = hint,
            modifier = Modifier.size(SkySizes.searchGlyph),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = hint,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Sizes.minTouchTarget)
                    .tappable(CircleShape, Role.Button) { onSearch("") },
            ) {
                Icon(
                    imageVector = PtutIcon.Close.image,
                    contentDescription = clearLabel,
                    tint = hint,
                    modifier = Modifier.size(SkySizes.searchGlyph),
                )
            }
        }
    }
}

/**
 * #207's Quick add: [SkySearchField]'s grey pill with the sparkles, sent with the keyboard's Send
 * key. The caller holds [text], so words the mic heard (#212) land in the same field; [trailing]
 * is where the mic sits. The field rests while [enabled] is false so one sentence is read once.
 * Once it holds words, a send arrow named [sendLabel] shows before the mic (#220).
 */
@Composable
fun SkyAskField(
    text: String,
    onText: (String) -> Unit,
    onSend: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    sendLabel: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val sendable = enabled && text.isNotBlank()
    Row(
        modifier = modifier
            .heightIn(min = SkySizes.search)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = Spacing.sm, end = if (trailing == null && !sendable) Spacing.sm else Spacing.none),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PtutIcon.AutoAwesome.image,
            contentDescription = null,
            tint = hint,
            modifier = Modifier.size(SkySizes.searchGlyph),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = hint,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onText,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
            )
        }
        if (sendable) {
            SkyBarIconAction(
                icon = PtutIcon.ArrowBack,
                label = sendLabel,
                onClick = { onSend(text) },
                // The icon set has no send glyph, so the back arrow is turned to point forward.
                modifier = Modifier.graphicsLayer { rotationZ = HALF_TURN },
            )
        }
        trailing?.invoke()
    }
}

private const val HALF_TURN = 180f

/** "+ NEW CATEGORY" (p19): a slot the reader may fill, so a dashed pill rather than a filled one. */
@Composable
fun SkyDashedAddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dash = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.addRow)
            .semantics { contentDescription = label }
            .tappable(CircleShape, Role.Button, onClick = onClick)
            .drawBehind {
                val stroke = SkySizes.hairline.toPx()
                drawRoundRect(
                    color = dash,
                    // Inset by half the stroke, or its outer half falls outside the box.
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = size.copy(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(SkySizes.dash.toPx(), SkySizes.dashGap.toPx())),
                    ),
                )
            }
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s10, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PtutIcon.Add.image,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(SkySizes.addGlyph),
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = SkyLabelTracking),
            color = ink,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

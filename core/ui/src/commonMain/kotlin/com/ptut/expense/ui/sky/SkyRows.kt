package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.categoryTint
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.DEFAULT_COLOR
import com.ptut.expense.domain.Direction
import com.ptut.expense.locale.LocalCopy

/**
 * One transaction on Home and in History (p05, p15): a category circle, the shop over its meta, the
 * amount over its time. Takes strings, like [com.ptut.expense.ui.TransactionRow], so both lists reuse it.
 */
@Composable
fun SkyTransactionRow(
    merchant: String,
    meta: String,
    amount: String,
    icon: String,
    direction: Direction,
    modifier: Modifier = Modifier,
    time: String? = null,
    categoryColor: String = DEFAULT_COLOR,
    /** FR-21: null where no record stands behind the row, so it neither looks nor acts tappable. */
    onClick: (() -> Unit)? = null,
) {
    val tint = categoryTint(categoryColor)
    val type = MaterialTheme.ptutType
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier.semantics(mergeDescendants = true) {}
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                },
            )
            .heightIn(min = SkySizes.transactionRow)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(tint.fill),
        ) {
            Icon(
                imageVector = ptutIcon(icon),
                contentDescription = null,
                tint = tint.on,
                modifier = Modifier.size(SkySizes.rowGlyph),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(merchant, style = type.listHeadline, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            val income = direction == Direction.Income
            val ink = if (income) MaterialTheme.ptut.ok else MaterialTheme.ptut.signatureInk
            val record = LocalCopy.current.record
            // Colour alone would leave red-green colour blindness and screen readers guessing: income
            // also carries an arrow, and every amount is spoken with its direction.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$amount, ${if (income) record.received else record.spent}" },
            ) {
                if (income) {
                    Icon(
                        imageVector = PtutIcon.CallReceived.image,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.padding(end = Spacing.xxs).size(SkySizes.moneyInArrow),
                    )
                }
                Text(text = amount, style = type.listHeadline, color = ink)
            }
            if (time != null) {
                Text(time, style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A label and its value on confirm and detail (p13, p16), with the hairline under it. */
@Composable
fun SkyKeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .heightIn(min = SkySizes.keyValueRow)
            .drawBehind {
                val weight = SkySizes.hairline.toPx()
                drawRect(rule, topLeft = Offset(0f, size.height - weight), size = Size(size.width, weight))
            }
            .padding(vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * [SkyKeyValueRow] with its value typed in place: a slip's shop on confirm (p13). The pencil says it
 * can change, and the field is named by [label] once [placeholder] is drawn over it. A tap anywhere
 * on the row, pencil and label included, puts the cursor in the field: a pencil-sized button would
 * fall under the 44pt floor, and a 44pt one would make this row taller than the rows beside it.
 */
@Composable
fun SkyEditableKeyValueRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.keyValueRow)
            .drawBehind {
                val weight = SkySizes.hairline.toPx()
                drawRect(rule, topLeft = Offset(0f, size.height - weight), size = Size(size.width, weight))
            }
            .tappable(RectangleShape, Role.Button) {
                field.requestFocus()
                keyboard?.show()
            }
            .padding(vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = hint)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleSmall,
                    color = hint,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .semantics { contentDescription = label },
            )
        }
        Icon(
            imageVector = PtutIcon.Edit.image,
            contentDescription = null,
            tint = hint,
            modifier = Modifier.size(SkySizes.searchGlyph),
        )
    }
}

/** The rule above the first key-value row, so a group of them is closed at both ends (p13, p16). */
@Composable
fun SkyHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(SkySizes.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

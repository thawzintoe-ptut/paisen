package com.ptut.expense.design

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

/**
 * A tap target whose ripple takes the shape it is drawn in.
 *
 * `Modifier.clickable` draws its indication in the node's own rectangle. On a rounded control that
 * clips nothing, that is a rectangle with the control's corners hanging out of it — the ripple
 * says the target is square when it is not. Clipping first is the whole fix, and the ordering is
 * the part that is easy to get wrong: a `clip` *after* the `clickable` clips the content and
 * leaves the indication exactly where it was.
 */
fun Modifier.tappable(
    shape: Shape,
    role: Role? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clip(shape).clickable(enabled = enabled, role = role, onClick = onClick)

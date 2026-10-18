package com.ptut.expense.ui.sky

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptutMotion

/**
 * Sky's switch (p21): an ink track with a light knob when on, painted 44 by 26 in a 44 square target.
 * A null [onCheckedChange] draws the state only, for a row that toggles as a whole.
 */
@Composable
fun SkySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.ptutMotion
    val travel = SkySizes.switchWidth - SkySizes.switchKnob - SkySizes.switchInset * 2
    val knobAt by animateDpAsState(
        targetValue = if (checked) travel else Spacing.none,
        animationSpec = tween(motion.chip.orStill(), easing = motion.standard),
        label = "sky switch knob",
    )
    val track by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = tween(motion.chip.orStill(), easing = motion.standard),
        label = "sky switch track",
    )
    ToggleTarget(checked, onCheckedChange, Role.Switch, label, modifier) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .size(SkySizes.switchWidth, SkySizes.switchHeight)
                .clip(CircleShape)
                .background(track)
                .padding(SkySizes.switchInset),
        ) {
            Box(
                Modifier
                    .offset(x = knobAt)
                    .size(SkySizes.switchKnob)
                    .clip(CircleShape)
                    .background(if (checked) scheme.onPrimary else scheme.tertiaryContainer),
            )
        }
    }
}

/**
 * Sky's checkbox (p18, p22): ink with a tick, painted 24 in a 44 square target. Unticked, its edge
 * is `outline` rather than the artboard's #C9CED3, which is 1.6:1 and would hide the control.
 */
@Composable
fun SkyCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(SkySizes.checkboxCorner)
    ToggleTarget(checked, onCheckedChange, Role.Checkbox, label, modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SkySizes.checkbox)
                .clip(shape)
                .background(if (checked) scheme.primary else Color.Transparent)
                .border(SkySizes.hairline, if (checked) Color.Transparent else scheme.outline, shape),
        ) {
            if (checked) {
                Icon(
                    imageVector = PtutIcon.Check.image,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(SkySizes.checkboxTick),
                )
            }
        }
    }
}

/** No ripple: the control's own fill changing is the answer, and a 44 square ripple outgrows it. */
@Composable
private fun ToggleTarget(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    role: Role,
    label: String?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = Sizes.minTouchTarget, minHeight = Sizes.minTouchTarget)
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics { if (label != null) contentDescription = label }
                        .toggleable(
                            value = checked,
                            interactionSource = null,
                            indication = null,
                            role = role,
                            onValueChange = onCheckedChange,
                        )
                },
            ),
    ) {
        content()
    }
}

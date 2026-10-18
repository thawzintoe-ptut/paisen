package com.ptut.expense.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.BinDraw
import com.ptut.expense.design.CheckDraw
import com.ptut.expense.design.GlassSurface
import com.ptut.expense.design.LocalShellBackdrop
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.ui.sky.SkyTextAction

/**
 * Renders the one toast the app has at a time. It takes the message rather than the channel, so
 * the timing lives in a state holder that outlives this composition and can be tested without one.
 */
@Composable
fun ToastHost(
    message: ToastMessage?,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The words have to outlive the message for as long as the exit runs, or the toast empties
    // itself before it has finished leaving.
    var lastShown by remember { mutableStateOf<ToastMessage?>(null) }
    LaunchedEffect(message) { if (message != null) lastShown = message }

    val motion = MaterialTheme.ptutMotion
    val rise = motion.toastRise.orStill()
    val leave = motion.scrim.orStill()

    AnimatedVisibility(
        visible = message != null,
        // The toast takes the emphasized curve page 01 assigns it, and leaves on the exit one:
        // "nothing should linger once the user has decided it is done".
        enter = fadeIn(tween(rise, easing = motion.emphasized)) +
            slideInVertically(tween(rise, easing = motion.emphasized)) { height -> height },
        exit = fadeOut(tween(leave, easing = motion.exit)),
        modifier = modifier,
    ) {
        val tone = lastShown?.tone ?: ToastTone.Success
        val container = when (tone) {
            ToastTone.Success, ToastTone.Deleted -> MaterialTheme.ptut.toastBg
            ToastTone.Error -> MaterialTheme.colorScheme.errorContainer
        }
        val content = when (tone) {
            ToastTone.Success, ToastTone.Deleted -> MaterialTheme.ptut.toastOn
            ToastTone.Error -> MaterialTheme.colorScheme.onErrorContainer
        }
        val action = lastShown?.action

        // #216: Polite, so TalkBack and VoiceOver read the line and its Undo when it rises.
        Surface(
            shape = PtutRadii.r22,
            color = Color.Transparent,
            contentColor = content,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            // iOS glass over the page. An error stays solid, so a refusal is never read through glass.
            GlassSurface(
                backdrop = LocalShellBackdrop.current.takeIf { tone != ToastTone.Error },
                shape = PtutRadii.r22,
                fill = container,
                tint = TOAST_GLASS_TINT,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.s12),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (tone) {
                        // Page 05: the check "draws in the toast as it rises. Plays once and holds."
                        // Keyed on the message so a second save redraws it rather than showing a
                        // finished check for something that has only just happened.
                        ToastTone.Success -> key(lastShown) {
                            CheckDraw(size = Sizes.toastIcon, color = MaterialTheme.ptut.toastCheck)
                        }

                        ToastTone.Deleted -> key(lastShown) {
                            BinDraw(size = Sizes.toastIcon, color = MaterialTheme.ptut.toastOn)
                        }

                        ToastTone.Error -> Icon(
                            imageVector = PtutIcon.Error.image,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.toastIcon),
                        )
                    }

                    // weight, not wrap: the draining bar below fills this column's width, and without
                    // a weight that fill claimed the whole row — which pushed the Undo button off the
                    // edge and left it wrapping its own label one letter per line.
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = lastShown?.text.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    // FR-21's undo. Drawn from [lastShown] like the words, so it does not vanish
                    // half-way through the exit and leave the pill visibly re-laying itself out.
                    action?.let { SkyTextAction(label = it.label, onClick = onAction, enabled = true, ink = content) }
                }
            }
        }
    }
}

/**
 * How much of the toast's own colour lies over the glass. The toast carries text, which needs 4.5:1:
 * at 0.75 white text keeps 4.86:1 over a white chip in night mode, and 7.26:1 in day mode.
 */
private const val TOAST_GLASS_TINT = 0.75f

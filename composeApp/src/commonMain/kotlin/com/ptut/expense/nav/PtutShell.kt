package com.ptut.expense.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.GlassBackdrop
import com.ptut.expense.design.GlassSurface
import com.ptut.expense.design.LocalGlassOn
import com.ptut.expense.design.LocalShellBackdrop
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.glassSource
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.rememberGlassBackdrop
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy

/**
 * The chrome around the nav graph: one floating pill over the content, the same on both platforms
 * (p05: "iOS: same layout"). Sky has no bottom bar, no FAB and no add button in a header.
 *
 * The content runs to the bottom of the screen and scrolls under the pill, which is why every list
 * ends on [com.ptut.expense.design.SkySizes.pillClearance]. [slot] is the destination to light,
 * and null hides the pill: pushed screens are tasks, and the pill would cover the controls that
 * finish them (#155).
 *
 * The pill animates here, outside [PtutNavHost], so the NavHost keeps its default transitions and
 * iOS keeps its edge back-swipe.
 *
 * [frosted] blurs what scrolls under the pill (trend 05, #123). The content is recorded into one
 * layer that draws as usual, and the pill draws the same layer again, blurred, behind its glyphs.
 */
@Composable
fun PtutShell(
    slot: BarDestination?,
    /** The colour under the status bar, so a screen with a coloured top reaches the top edge. */
    statusBarTint: Color,
    onSelect: (BarDestination) -> Unit,
    onAdd: () -> Unit,
    toast: @Composable () -> Unit,
    frosted: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    // The slot the pill last showed, so it keeps lighting it while it slides away.
    var shown by remember { mutableStateOf(slot ?: BarDestination.Today) }
    if (slot != null) shown = slot

    val motion = MaterialTheme.ptutMotion
    val move = motion.navIndicator.orStill()

    val tint by animateColorAsState(statusBarTint, tween(move, easing = motion.standard), label = "status bar tint")

    CompositionLocalProvider(LocalGlassOn provides frosted) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(tint))
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                content(WindowInsets.systemBars.asPaddingValues())
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = SkySizes.pillMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Above the pill, where a toast has always risen from the chrome. #216: and above the
                // keyboard when one is up; the pill stays put under it.
                Box(Modifier.imePadding()) { CompositionLocalProvider(LocalShellBackdrop provides backdrop) { toast() } }
                AnimatedVisibility(
                    visible = slot != null,
                    enter = fadeIn(tween(move, easing = motion.standard)) +
                        slideInVertically(tween(move, easing = motion.standard)) { it },
                    exit = fadeOut(tween(move, easing = motion.exit)) +
                        slideOutVertically(tween(move, easing = motion.exit)) { it },
                ) {
                    NavPill(
                        selected = shown,
                        onSelect = onSelect,
                        onAdd = onAdd,
                        backdrop = backdrop,
                    )
                }
            }
        }
    }
}

/** Where each slot sits in the pill, counted in slots. Add takes the middle one. */
private val PILL_ORDER: List<BarDestination?> =
    listOf(BarDestination.Today, BarDestination.Reports, null, BarDestination.Shared, BarDestination.Settings)

@Composable
private fun NavPill(
    selected: BarDestination,
    onSelect: (BarDestination) -> Unit,
    onAdd: () -> Unit,
    /** What scrolls under the pill; off draws it solid. */
    backdrop: GlassBackdrop,
) {
    val step = SkySizes.pillSlot + SkySizes.pillGap
    val still = MaterialTheme.ptutMotion.navIndicator.orStill() == 0
    val at by animateDpAsState(
        targetValue = step * PILL_ORDER.indexOf(selected),
        // A spatial spring: the ink travels between slots rather than reappearing in the next one.
        animationSpec = if (still) snap() else spring(dampingRatio = INDICATOR_DAMPING, stiffness = INDICATOR_STIFFNESS),
        label = "pill indicator",
    )
    GlassSurface(
        backdrop = backdrop,
        shape = CircleShape,
        fill = MaterialTheme.colorScheme.tertiaryContainer,
        tint = FROST_TINT,
        modifier = Modifier.shadow(
            SkySizes.pillShadow,
            CircleShape,
            ambientColor = MaterialTheme.ptut.pillShadow,
            spotColor = MaterialTheme.ptut.pillShadow,
        ),
    ) {
        Box(Modifier.padding(SkySizes.pillGap)) {
            // The ink circle behind the lit slot, laid out once and moved, so it slides under the glyphs.
            Box(
                Modifier
                    // Held inside the end slots: the spring's give past Settings would be cut by the pill.
                    .graphicsLayer { translationX = at.coerceIn(Spacing.none, step * PILL_ORDER.lastIndex).toPx() }
                    .size(SkySizes.pillSlot)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(SkySizes.pillGap),
            ) {
                PILL_ORDER.forEach { destination ->
                    if (destination == null) {
                        AddSlot(onAdd = onAdd)
                    } else {
                        DestinationSlot(
                            destination = destination,
                            selected = destination == selected,
                            onGlass = backdrop.enabled,
                            onSelect = { onSelect(destination) },
                        )
                    }
                }
            }
        }
    }
}

/** [onGlass]: the frosted pill lets more of the page through, so its glyphs take the stronger ink (see [FROST_TINT]). */
@Composable
private fun DestinationSlot(destination: BarDestination, selected: Boolean, onGlass: Boolean, onSelect: () -> Unit) {
    PillGlyph(
        icon = destination.icon,
        tint = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            onGlass -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .semantics { contentDescription = destination.label }
            // No ripple: the ink moving under the glyph is the answer to the tap.
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ),
    )
}

/** The one ruby object on the screen, so nothing else competes with it. */
@Composable
private fun AddSlot(onAdd: () -> Unit) {
    val addLabel = LocalCopy.current.shell.addByHand
    PillGlyph(
        icon = PtutIcon.Add.image,
        tint = MaterialTheme.ptut.onSignature,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.ptut.signature)
            .semantics { contentDescription = addLabel }
            .tappable(CircleShape, Role.Button, onClick = onAdd),
    )
}

@Composable
private fun PillGlyph(icon: ImageVector, tint: Color, modifier: Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(SkySizes.pillSlot)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(SkySizes.pillGlyph))
    }
}

/** Kept from the FAB it replaces, so a screen reader still says what the plus does. */

/**
 * How much of the pill's own fill lies over the blur. At 0.8 the grey glyphs kept 3:1 but the blur
 * did not show, so the switch looked broken. At this tint the glyphs on glass take onSurface, which
 * keeps 3.6:1 over the worst solid colour that can scroll under (night, a white chip), above #123's 3:1.
 */
private const val FROST_TINT = 0.55f

/** M3 Expressive's default spatial spring: it settles with a little give. */
private const val INDICATOR_DAMPING = 0.6f
private const val INDICATOR_STIFFNESS = 380f

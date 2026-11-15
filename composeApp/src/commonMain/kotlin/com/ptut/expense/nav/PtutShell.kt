package com.ptut.expense.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** The pill's slots in order. Add takes the middle one. */
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
    val night = MaterialTheme.colorScheme.background.luminance() < NIGHT_LUMINANCE
    GlassSurface(
        backdrop = backdrop,
        shape = CircleShape,
        // The card colour in both themes: lighter than the night ground, and dark enough that the ruby Add
        // keeps 3:1 against it (on containerHigh it fell to 2.5). At night a denser tint, so rows scrolling
        // under it stop reading through.
        fill = MaterialTheme.colorScheme.tertiaryContainer,
        tint = if (night) NIGHT_FROST_TINT else FROST_TINT,
        modifier = Modifier.shadow(
            SkySizes.pillShadow,
            CircleShape,
            ambientColor = MaterialTheme.ptut.pillShadow,
            spotColor = MaterialTheme.ptut.pillShadow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(SkySizes.pillGap).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SkySizes.pillTabGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PILL_ORDER.forEach { destination ->
                if (destination == null) {
                    AddSlot(onAdd = onAdd)
                } else {
                    DestinationSlot(
                        destination = destination,
                        selected = destination == selected,
                        onGlass = backdrop.enabled,
                        night = night,
                        onSelect = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

/**
 * A destination: its glyph in an indicator over its name, so the pill says where you are in words
 * as well as ink. By day the indicator is the ink pill with a white glyph; at night a soft sky
 * tint with a sky glyph, because a white disc there glared and read as a second Add.
 *
 * [onGlass]: the frosted pill lets more of the page through, so idle glyphs take the stronger ink (see [FROST_TINT]).
 */
@Composable
private fun DestinationSlot(
    destination: BarDestination,
    selected: Boolean,
    onGlass: Boolean,
    night: Boolean,
    onSelect: () -> Unit,
) {
    val motion = MaterialTheme.ptutMotion
    val move = motion.navIndicator.orStill()
    val idle = if (onGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorFill = if (night) MaterialTheme.ptut.accent.copy(alpha = NIGHT_INDICATOR_ALPHA) else MaterialTheme.colorScheme.primary
    val litGlyph = if (night) MaterialTheme.ptut.accent else MaterialTheme.colorScheme.onPrimary

    // The indicator grows out from the glyph (M3 Expressive's spatial spring), and the inks follow it.
    val lit by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (move == 0) snap() else spring(dampingRatio = INDICATOR_DAMPING, stiffness = INDICATOR_STIFFNESS),
        label = "pill indicator",
    )
    val glyph by animateColorAsState(if (selected) litGlyph else idle, tween(move, easing = motion.standard), label = "pill glyph")
    val word by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else idle,
        tween(move, easing = motion.standard),
        label = "pill label",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkySizes.pillLabelGap, Alignment.CenterVertically),
        modifier = Modifier
            .semantics { contentDescription = destination.label }
            // No ripple: the indicator growing under the glyph is the answer to the tap.
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            )
            .widthIn(min = SkySizes.pillTabMinWidth)
            .heightIn(min = SkySizes.pillTabHeight)
            .padding(horizontal = Spacing.xxs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = SkySizes.pillIndicatorWidth, height = SkySizes.pillIndicatorHeight),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = lit
                        alpha = lit.coerceIn(0f, 1f)
                    }
                    .background(indicatorFill, CircleShape),
            )
            Icon(imageVector = destination.icon, contentDescription = null, tint = glyph, modifier = Modifier.size(SkySizes.pillGlyph))
        }
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium,
            color = word,
            maxLines = 1,
            softWrap = false,
            // The slot already says it; the word would be read twice.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The one ruby object on the screen, so nothing else competes with it. */
@Composable
private fun AddSlot(onAdd: () -> Unit) {
    val addLabel = LocalCopy.current.shell.addByHand
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SkySizes.pillAdd)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.signature)
            .semantics { contentDescription = addLabel }
            .tappable(CircleShape, Role.Button, onClick = onAdd),
    ) {
        Icon(
            imageVector = PtutIcon.Add.image,
            contentDescription = null,
            tint = MaterialTheme.ptut.onSignature,
            modifier = Modifier.size(SkySizes.pillGlyph),
        )
    }
}

/**
 * How much of the pill's own fill lies over the blur by day. At 0.8 the grey glyphs kept 3:1 but the blur
 * did not show, so the switch looked broken. At this tint the glyphs on glass take onSurface, which
 * keeps 3.6:1 over the worst solid colour that can scroll under, above #123's 3:1.
 */
private const val FROST_TINT = 0.55f

/**
 * The same at night. At 0.55 the light rows under a dark pill read straight through it, names and
 * amounts competing with the glyphs; at 0.88 they are a soft shape and the glyphs and names stay clear.
 */
private const val NIGHT_FROST_TINT = 0.88f

/** Below this the ground is a night one (SkyNight's is #121316; Sky's and SkyEvening's are near white). */
private const val NIGHT_LUMINANCE = 0.5f

/** The night indicator: sky at this alpha over the pill, so the lit slot glows rather than glares. */
private const val NIGHT_INDICATOR_ALPHA = 0.22f

/** M3 Expressive's default spatial spring: it settles with a little give. */
private const val INDICATOR_DAMPING = 0.6f
private const val INDICATOR_STIFFNESS = 380f

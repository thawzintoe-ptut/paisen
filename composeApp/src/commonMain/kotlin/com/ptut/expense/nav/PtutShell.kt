package com.ptut.expense.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import com.ptut.expense.design.GlassBackdrop
import com.ptut.expense.design.GlassSurface
import com.ptut.expense.design.LocalGlassOn
import com.ptut.expense.design.LocalShellBackdrop
import com.ptut.expense.design.PRESS_SCALE_COMPACT
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

/** From one slot's start to the next. */
private val PILL_STEP = SkySizes.pillSlot + SkySizes.pillGap

/**
 * The pill (p05): glyphs alone, the lit one on an ink disc, ruby Add in the middle. The disc is drawn
 * once behind the slots and travels between them; each glyph is drawn again in the lit ink, clipped to
 * wherever the disc is, so it changes colour as the disc covers it rather than fading.
 */
@Composable
private fun NavPill(
    selected: BarDestination,
    onSelect: (BarDestination) -> Unit,
    onAdd: () -> Unit,
    /** What scrolls under the pill; off draws it solid. */
    backdrop: GlassBackdrop,
) {
    val night = MaterialTheme.colorScheme.background.luminance() < NIGHT_LUMINANCE
    val still = MaterialTheme.ptutMotion.navIndicator.orStill() == 0
    val disc = rememberTravellingDisc(slot = PILL_ORDER.indexOf(selected), still = still)
    val ink = MaterialTheme.colorScheme.primary
    GlassSurface(
        backdrop = backdrop,
        shape = CircleShape,
        // The card colour in both themes: lighter than the night ground, and dark enough that the ruby Add
        // keeps 3:1 against it. At night a denser tint, so rows scrolling under it stop reading through.
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
            modifier = Modifier
                .padding(SkySizes.pillGap)
                // Read in the draw phase only, so the disc moves without recomposing the pill.
                .drawBehind {
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(disc.left.toPx(), 0f),
                        size = Size(disc.width.toPx(), size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SkySizes.pillGap),
        ) {
            PILL_ORDER.forEachIndexed { index, destination ->
                if (destination == null) {
                    AddSlot(onAdd = onAdd, still = still)
                } else {
                    DestinationSlot(
                        destination = destination,
                        selected = destination == selected,
                        onGlass = backdrop.enabled,
                        disc = disc,
                        at = PILL_STEP * index,
                        still = still,
                        onSelect = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

/**
 * The ink disc's two edges, each on its own spring. The edge facing the new slot leads and the other
 * follows a beat behind, so the disc stretches into a capsule on the way and settles round with a give.
 */
@Stable
private class TravellingDisc(start: Dp) {
    private val leftEdge = Animatable(start, Dp.VectorConverter)
    private val rightEdge = Animatable(start + SkySizes.pillSlot, Dp.VectorConverter)

    /** Held inside the end slots, so the springs' give past Today or Settings is never cut by the pill. */
    val right: Dp get() = rightEdge.value.coerceIn(SkySizes.pillSlot, PILL_STEP * PILL_ORDER.lastIndex + SkySizes.pillSlot)

    /** Never closer to [right] than [MIN_DISC], so a trailing edge springing past its rest squashes the disc rather than crossing it. */
    val left: Dp get() = leftEdge.value.coerceIn(Spacing.none, PILL_STEP * PILL_ORDER.lastIndex).coerceAtMost(right - SkySizes.pillSlot * MIN_DISC)

    val width: Dp get() = right - left

    suspend fun travelTo(start: Dp, still: Boolean) = coroutineScope {
        val end = start + SkySizes.pillSlot
        if (still) {
            leftEdge.snapTo(start)
            rightEdge.snapTo(end)
            return@coroutineScope
        }
        val rightward = start > leftEdge.value
        launch { leftEdge.animateTo(start, edgeSpring(leads = !rightward)) }
        launch { rightEdge.animateTo(end, edgeSpring(leads = rightward)) }
    }
}

@Composable
private fun rememberTravellingDisc(slot: Int, still: Boolean): TravellingDisc {
    val disc = remember { TravellingDisc(PILL_STEP * slot) }
    LaunchedEffect(slot, still) { disc.travelTo(PILL_STEP * slot, still) }
    return disc
}

private fun edgeSpring(leads: Boolean) = spring(
    dampingRatio = if (leads) LEAD_DAMPING else TRAIL_DAMPING,
    stiffness = if (leads) LEAD_STIFFNESS else TRAIL_STIFFNESS,
    visibilityThreshold = Dp.VisibilityThreshold,
)

/**
 * One destination: its glyph in the idle ink, and again in the lit ink clipped to the disc. [at] is
 * where this slot starts in the pill. [onGlass]: the frosted pill lets more of the page through, so
 * idle glyphs take the stronger ink (see [FROST_TINT]).
 */
@Composable
private fun DestinationSlot(
    destination: BarDestination,
    selected: Boolean,
    onGlass: Boolean,
    disc: TravellingDisc,
    at: Dp,
    still: Boolean,
    onSelect: () -> Unit,
) {
    val idle = if (onGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val pop = rememberPop(selected = selected, still = still)
    val clip = remember { Path() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SkySizes.pillSlot)
            .semantics { contentDescription = destination.label }
            // No ripple: the disc travelling to the glyph is the answer to the tap.
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ),
    ) {
        PillGlyph(icon = destination.icon, tint = idle, scale = { pop.value })
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val left = (disc.left - at).toPx()
                    clip.reset()
                    clip.addRoundRect(RoundRect(left, 0f, left + disc.width.toPx(), size.height, CornerRadius(size.height / 2)))
                    clipPath(clip) { this@drawWithContent.drawContent() }
                },
        ) {
            PillGlyph(icon = destination.icon, tint = MaterialTheme.colorScheme.onPrimary, scale = { pop.value })
        }
    }
}

/** The glyph that lights dips and springs back to size, so the tap is felt to land. Not on the first frame. */
@Composable
private fun rememberPop(selected: Boolean, still: Boolean): Animatable<Float, AnimationVector1D> {
    val pop = remember { Animatable(1f) }
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected && settled.value && !still) {
            pop.snapTo(POP_FROM)
            pop.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS))
        }
        settled.value = true
    }
    return pop
}

/** The one ruby object on the screen, so nothing else competes with it. It gives under the finger, and the plus turns a quarter. */
@Composable
private fun AddSlot(onAdd: () -> Unit, still: Boolean) {
    val addLabel = LocalCopy.current.shell.addByHand
    var turns by remember { mutableIntStateOf(0) }
    val turn by animateFloatAsState(
        targetValue = turns * QUARTER_TURN,
        animationSpec = if (still) snap() else spring(dampingRatio = TURN_DAMPING, stiffness = TURN_STIFFNESS),
        label = "add turn",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SkySizes.pillSlot)
            .tappable(CircleShape, Role.Button, pressScale = PRESS_SCALE_COMPACT) {
                turns++
                onAdd()
            }
            .clip(CircleShape)
            .background(MaterialTheme.ptut.signature)
            .semantics { contentDescription = addLabel },
    ) {
        PillGlyph(icon = PtutIcon.Add.image, tint = MaterialTheme.ptut.onSignature, rotation = { turn })
    }
}

@Composable
private fun PillGlyph(icon: ImageVector, tint: Color, scale: () -> Float = { 1f }, rotation: () -> Float = { 0f }) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(SkySizes.pillGlyph)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
                rotationZ = rotation()
            },
    )
}

/**
 * How much of the pill's own fill lies over the blur by day. At 0.8 the grey glyphs kept 3:1 but the blur
 * did not show, so the switch looked broken. At this tint the glyphs on glass take onSurface, which
 * keeps 3.6:1 over the worst solid colour that can scroll under, above #123's 3:1.
 */
private const val FROST_TINT = 0.55f

/**
 * The same at night. At 0.55 the light rows under a dark pill read straight through it, names and
 * amounts competing with the glyphs; at 0.88 they are a soft shape and the glyphs stay clear.
 */
private const val NIGHT_FROST_TINT = 0.88f

/** Below this the ground is a night one (SkyNight's is #121316; Sky's and SkyEvening's are near white). */
private const val NIGHT_LUMINANCE = 0.5f

/** The disc's leading edge: quick and nearly still on arrival. */
private const val LEAD_DAMPING = 0.8f
private const val LEAD_STIFFNESS = 700f

/** The trailing edge: a beat behind, with M3 Expressive's give. */
private const val TRAIL_DAMPING = 0.62f
private const val TRAIL_STIFFNESS = 300f

/** The narrowest the disc squashes to, as a share of a slot. */
private const val MIN_DISC = 0.8f

/** A lit glyph dips to this and springs back with a small bounce. */
private const val POP_FROM = 0.86f
private const val POP_DAMPING = 0.45f
private const val POP_STIFFNESS = 600f

private const val QUARTER_TURN = 90f
private const val TURN_DAMPING = 0.6f
private const val TURN_STIFFNESS = 400f

package com.ptut.expense.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.GlassBackdrop
import com.ptut.expense.design.GlassSurface
import com.ptut.expense.design.LocalGlassOn
import com.ptut.expense.design.LocalShellBackdrop
import com.ptut.expense.design.NavGlyph
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.glassSource
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.rememberGlassBackdrop
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.CappedFontScale

/**
 * The chrome around the nav graph: a floating tab capsule with Add beside it (V2 N01–N05, #106),
 * the same on both platforms.
 *
 * The content runs to the bottom of the screen and scrolls under the bar, which is why every list
 * ends on [com.ptut.expense.design.SkySizes.pillClearance]. [slot] is the tab to light, and null
 * hides the bar: pushed screens are tasks, and the bar would cover the controls that finish them
 * (#155). Sheets are drawn by their own window, so one always covers the bar (#109).
 *
 * The bar animates here, outside [PtutNavHost], so the NavHost keeps its default transitions and
 * iOS keeps its edge back-swipe.
 *
 * [frosted] blurs what scrolls under the capsule (trend 05, #123). The content is recorded into one
 * layer that draws as usual, and the capsule draws the same layer again, blurred, behind its tabs.
 * [lively] is the reader's Play switch: off, the bar moves as it does under reduce motion (#107).
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
    lively: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    // The tab the bar last lit, so it keeps lighting it while it slides away.
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
                    .padding(bottom = SkySizes.navMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Above the bar, where a toast has always risen from the chrome. #216, #109: and above
                // the keyboard when one is up; the bar stays put under it.
                Box(Modifier.imePadding()) { CompositionLocalProvider(LocalShellBackdrop provides backdrop) { toast() } }
                AnimatedVisibility(
                    visible = slot != null,
                    enter = fadeIn(tween(move, easing = motion.standard)) +
                        slideInVertically(tween(move, easing = motion.standard)) { it },
                    exit = fadeOut(tween(move, easing = motion.exit)) +
                        slideOutVertically(tween(move, easing = motion.exit)) { it },
                ) {
                    NavBar(
                        selected = shown,
                        onSelect = onSelect,
                        onAdd = onAdd,
                        backdrop = backdrop,
                        still = !lively || move == 0,
                    )
                }
            }
        }
    }
}

/**
 * The capsule and Add, side by side (N01). Ruby belongs to Add alone: the lens is an ink tint, so
 * the two never compete.
 */
@Composable
private fun NavBar(
    selected: BarDestination,
    onSelect: (BarDestination) -> Unit,
    onAdd: () -> Unit,
    /** What scrolls under the capsule; off draws it solid. */
    backdrop: GlassBackdrop,
    still: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SkySizes.navSide),
        horizontalArrangement = Arrangement.spacedBy(SkySizes.navAddGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCapsule(
            selected = selected,
            onSelect = onSelect,
            backdrop = backdrop,
            still = still,
            modifier = Modifier.weight(1f),
        )
        AddButton(onAdd = onAdd, still = still)
    }
}

/**
 * Four tabs on glass. The lens is drawn once behind them and slides between them on one spring
 * (#107, board 08b); it is read in the draw phase only, so its frames never recompose the bar.
 */
@Composable
private fun NavCapsule(
    selected: BarDestination,
    onSelect: (BarDestination) -> Unit,
    backdrop: GlassBackdrop,
    still: Boolean,
    modifier: Modifier = Modifier,
) {
    val night = MaterialTheme.colorScheme.background.luminance() < NIGHT_LUMINANCE
    val tabs = BarDestination.entries
    val lens = rememberLens(slot = tabs.indexOf(selected), still = still)
    val ink = MaterialTheme.colorScheme.onSurface
    val lensFill = ink.copy(alpha = if (night) NIGHT_LENS_FILL else LENS_FILL)
    val light = MaterialTheme.ptut.glassHighlight
    // iOS glass: the lens catches light on its top edge and sinks into shadow along the bottom.
    val lensRim = if (night) {
        Brush.verticalGradient(listOf(ink.copy(alpha = NIGHT_LENS_EDGE), ink.copy(alpha = NIGHT_LENS_EDGE)))
    } else {
        Brush.verticalGradient(listOf(light.copy(alpha = LENS_RIM_TOP), ink.copy(alpha = LENS_RIM_BOTTOM)))
    }
    // By day the white capsule sits on white cards, so a hairline gives it an edge.
    val edgeModifier = if (night) Modifier else Modifier.border(SkySizes.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    GlassSurface(
        backdrop = backdrop,
        shape = CircleShape,
        // The card colour in both themes: lighter than the night ground. At night a denser tint, so
        // rows scrolling under it stop reading through.
        fill = MaterialTheme.colorScheme.tertiaryContainer,
        tint = if (night) NIGHT_FROST_TINT else FROST_TINT,
        modifier = modifier.navShadow().then(edgeModifier),
    ) {
        Row(
            modifier = Modifier
                .padding(SkySizes.navInset)
                .drawBehind {
                    val width = size.width / tabs.size
                    val corner = CornerRadius(size.height / 2)
                    val left = lens.value * width
                    drawRoundRect(lensFill, Offset(left, 0f), Size(width, size.height), corner)
                    val edge = SkySizes.hairline.toPx()
                    drawRoundRect(
                        brush = lensRim,
                        topLeft = Offset(left + edge / 2, edge / 2),
                        size = Size(width - edge, size.height - edge),
                        cornerRadius = corner,
                        style = Stroke(edge),
                    )
                }
                .selectableGroup(),
        ) {
            tabs.forEach { destination ->
                NavTab(
                    destination = destination,
                    selected = destination == selected,
                    onGlass = backdrop.enabled,
                    still = still,
                    onSelect = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The lens's place, counted in tabs. One spring with a small give; it never stretches. */
@Composable
private fun rememberLens(slot: Int, still: Boolean): Animatable<Float, AnimationVector1D> {
    val lens = remember { Animatable(slot * ONE_TAB) }
    LaunchedEffect(slot, still) {
        if (still) {
            lens.snapTo(slot * ONE_TAB)
        } else {
            lens.animateTo(slot * ONE_TAB, spring(dampingRatio = LENS_DAMPING, stiffness = LENS_STIFFNESS))
        }
    }
    return lens
}

/**
 * One tab: its glyph over its label. The selected one swaps to the solid glyph with a short pop
 * and its label darkens to weight 600. [onGlass]: the frosted capsule lets more of the page
 * through, so idle tabs take the stronger ink (see [FROST_TINT]).
 */
@Composable
private fun NavTab(
    destination: BarDestination,
    selected: Boolean,
    onGlass: Boolean,
    still: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = if (onGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val ink by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else idle,
        animationSpec = if (still) snap() else tween(LABEL_INK_MS, easing = MaterialTheme.ptutMotion.standard),
        label = "tab ink",
    )
    val pop = rememberPop(selected = selected, still = still)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && !still) TAB_PRESS_SCALE else 1f,
        animationSpec = if (still) snap() else spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS),
        label = "tab press",
    )
    val label = destination.label
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkySizes.navLabelGap, Alignment.CenterVertically),
        modifier = modifier
            .heightIn(min = SkySizes.navTab)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .semantics { contentDescription = label }
            // No ripple: the lens sliding to the tab is the answer to the tap.
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            )
            .padding(horizontal = SkySizes.navTabPadding),
    ) {
        Icon(
            imageVector = if (selected) destination.glyph.solid else destination.glyph.outline,
            contentDescription = null,
            tint = ink,
            modifier = Modifier
                .size(SkySizes.navGlyph)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
        // Four tabs share the bar's width, so a label stops growing before it is cut to "အစီ…".
        CappedFontScale(max = NAV_LABEL_FONT_SCALE) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** The glyph that lights grows from 0.8 with a short bounce, so the tap is felt to land. Not on the first frame. */
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
private fun AddButton(onAdd: () -> Unit, still: Boolean) {
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
            .size(SkySizes.navAdd)
            .navShadow()
            .tappable(CircleShape, Role.Button, pressScale = if (still) 1f else ADD_PRESS_SCALE) {
                if (!still) turns++
                onAdd()
            }
            .background(MaterialTheme.ptut.signature)
            .glassSheen(MaterialTheme.ptut.glassHighlight)
            .semantics { contentDescription = addLabel },
    ) {
        Icon(
            imageVector = NavGlyph.Add,
            contentDescription = null,
            tint = MaterialTheme.ptut.onSignature,
            modifier = Modifier
                .size(SkySizes.navAddGlyph)
                .graphicsLayer { rotationZ = turn },
        )
    }
}

/** The capsule's light on Add's ruby: a sheen over the top half and a rim bright at the top. */
private fun Modifier.glassSheen(light: Color): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(0f to light.copy(alpha = ADD_SHEEN), ADD_SHEEN_DEPTH to Color.Transparent))
    val edge = SkySizes.hairline.toPx()
    drawCircle(
        brush = Brush.verticalGradient(listOf(light.copy(alpha = ADD_RIM_TOP), Color.Transparent)),
        radius = size.minDimension / 2 - edge / 2,
        style = Stroke(edge),
    )
}

/** The one shadow the capsule and Add share, so the two float at the same height. */
@Composable
private fun Modifier.navShadow(): Modifier = shadow(
    SkySizes.navShadow,
    CircleShape,
    ambientColor = MaterialTheme.ptut.pillShadow,
    spotColor = MaterialTheme.ptut.pillShadow,
)

/**
 * How much of the capsule's own fill lies over the blur by day. At 0.55 the black cards under it
 * showed as grey smears; at 0.72 they blur to soft shapes, as iOS glass lets them, and the idle
 * tabs on onSurface keep #123's 3:1.
 */
/** How far a tab's label follows the reader's font setting: past this it no longer fits beside three others. */
private const val NAV_LABEL_FONT_SCALE = 1.15f
private const val FROST_TINT = 0.72f

/**
 * The same at night. At 0.55 the light rows under a dark capsule read straight through it; at 0.88
 * they are a soft shape and the tabs stay clear.
 */
private const val NIGHT_FROST_TINT = 0.88f

/** Below this the ground is a night one (SkyNight's is #121316; Sky's and SkyEvening's are near white). */
private const val NIGHT_LUMINANCE = 0.5f

/**
 * The lens, as an iOS glass platter: ink at 7% by day under a rim that is white at the top and a
 * faint ink at the bottom; the night ink at 12% with a 6% rim.
 */
private const val LENS_FILL = 0.07f
private const val LENS_RIM_TOP = 0.9f
private const val LENS_RIM_BOTTOM = 0.06f

/** Add's glass: enough light to read as glass on ruby, not enough to pale the plus's 8.1:1. */
private const val ADD_SHEEN = 0.18f
private const val ADD_SHEEN_DEPTH = 0.5f
private const val ADD_RIM_TOP = 0.5f
private const val NIGHT_LENS_FILL = 0.12f
private const val NIGHT_LENS_EDGE = 0.06f

/** The lens counts its place in tabs: the draw phase multiplies by one tab's width. */
private const val ONE_TAB = 1f

/** Board 08b's one spring: a small give past the tab, then settled. */
private const val LENS_DAMPING = 0.72f
private const val LENS_STIFFNESS = 380f

/** N01: the label darkens over 200ms as the lens lands. */
private const val LABEL_INK_MS = 200

/** A lit glyph grows from this and settles with a short bounce (08b: 0.8 to 1). */
private const val POP_FROM = 0.8f
private const val POP_DAMPING = 0.5f
private const val POP_STIFFNESS = 500f

/** N01: a tab gives to 0.94 under the finger, Add to 0.92. */
private const val TAB_PRESS_SCALE = 0.94f
private const val ADD_PRESS_SCALE = 0.92f
private const val PRESS_DAMPING = 0.8f
private const val PRESS_STIFFNESS = 900f

private const val QUARTER_TURN = 90f
private const val TURN_DAMPING = 0.6f
private const val TURN_STIFFNESS = 400f

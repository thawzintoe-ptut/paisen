package com.ptut.expense.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * The app's [androidx.navigation.compose.NavHost], wrapped so the two platforms can disagree
 * about how a screen arrives without the graph being written twice.
 *
 * Page 02 wants a push: 300ms on the standard curve, the outgoing screen drifting 22% and dimming
 * rather than leaving. Android gets exactly that. iOS keeps the platform's own, and the reason is
 * the page's own rule — "Chrome stays native… A shipped animation cannot beat the feel of the
 * system it is imitating."
 *
 * There is a mechanical reason underneath it too. `NavHost.uikit.kt` compares the transitions it
 * is handed against its defaults *by identity*, and only when all four match does it hand the
 * host an edge-limited back gesture and the iOS blackout under the outgoing screen. Those
 * defaults are already a push — slide in from the start edge, outgoing parallaxed 30% — so on
 * iOS the choice is between the page's 22% and a back-swipe that only fires from the correct
 * edge. The gesture wins; 8% of parallax is not worth a right-edge swipe that pops a screen.
 */
@Composable
expect fun PtutNavHost(
    navController: NavHostController,
    startDestination: PtutRoute,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
)

/**
 * Whether this move is between two entries of the bottom bar rather than deeper into one.
 *
 * Page 02 hangs meaning on direction: "Horizontal means deeper into the same subject." Four tabs
 * are not deeper than each other, so sliding between them would say something untrue — a reader
 * who saw Reports push in from the right would expect back to return to Today, and it does not.
 * Siblings cross-fade instead; the travelling nav pill is what carries that move.
 */
internal fun isSiblingMove(from: PtutRoute?, to: PtutRoute?): Boolean =
    from.isBarDestination() && to.isBarDestination()

private fun PtutRoute?.isBarDestination(): Boolean =
    this != null && BarDestination.entries.any { it.route::class == this::class }

/** The same question, asked of the destinations a transition scope actually hands over. */
internal fun androidx.navigation.NavDestination.isBarDestination(): Boolean =
    BarDestination.entries.any { hasRoute(it.route::class) }

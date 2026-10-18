package com.ptut.expense.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/**
 * iOS keeps the platform's own transitions, and passes none of its own on purpose.
 *
 * `NavHost.uikit.kt` only wires the edge-limited back gesture and the blackout under the outgoing
 * screen while all four transitions are identically its defaults — and those defaults are already
 * page 02's push, slide in from the start edge with the outgoing screen parallaxed behind. Naming
 * them here, even with the same values, would fail that identity check and cost the gesture.
 */
@Composable
actual fun PtutNavHost(
    navController: NavHostController,
    startDestination: PtutRoute,
    modifier: Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        builder = builder,
    )
}

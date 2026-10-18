package com.ptut.expense.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.ptut.expense.design.PtutMotion
import com.ptut.expense.design.inMillis
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptutMotion

/**
 * Android draws page 02's push: 300ms on the standard curve, and the screen being left behind
 * drifts 22% of the way and dims to 35% rather than leaving.
 *
 * "It stays visibly underneath, which is what makes back feel like a return instead of a new
 * load." Coming back reverses it — the screen underneath slides its 22% forward again from 35%,
 * so the two halves of a push and a pop are the same movement read in opposite directions.
 *
 * A move between two bar entries cross-fades instead. Page 02 spends direction on hierarchy, and
 * four tabs are not deeper than each other; the travelling pill in the bar is what carries that.
 */
@Composable
actual fun PtutNavHost(
    navController: NavHostController,
    startDestination: PtutRoute,
    modifier: Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val motion = MaterialTheme.ptutMotion
    val push = motion.push.orStill()
    val leave = motion.scrim.orStill()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { if (siblings()) fade(push, motion) else pushIn(push, motion) },
        exitTransition = { if (siblings()) fadeAway(leave, motion) else pushUnder(push, motion) },
        popEnterTransition = { if (siblings()) fade(push, motion) else pullBack(push, motion) },
        popExitTransition = { if (siblings()) fadeAway(leave, motion) else pushAway(leave, motion) },
        builder = builder,
    )
}

/** Both ends of the move are entries of the bottom bar, so neither is deeper than the other. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.siblings(): Boolean =
    initialState.destination.isBarDestination() && targetState.destination.isBarDestination()

/** The arriving screen, in from the trailing edge. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushIn(
    millis: Int,
    motion: PtutMotion,
): EnterTransition = slideIntoContainer(
    towards = SlideDirection.Start,
    animationSpec = tween(millis, easing = motion.standard),
)

/** The screen it covers, which drifts and dims but never leaves. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushUnder(
    millis: Int,
    motion: PtutMotion,
): ExitTransition = slideOutOfContainer(
    towards = SlideDirection.Start,
    animationSpec = tween(millis, easing = motion.standard),
    targetOffset = { width -> (width * motion.pushParallax).toInt() },
) + fadeOut(tween(millis, easing = motion.standard), targetAlpha = motion.pushDim)

/** Coming back: the screen underneath returns from where it drifted to, and brightens. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pullBack(
    millis: Int,
    motion: PtutMotion,
): EnterTransition = slideIntoContainer(
    towards = SlideDirection.End,
    animationSpec = tween(millis, easing = motion.standard),
    initialOffset = { width -> (width * motion.pushParallax).toInt() },
) + fadeIn(tween(millis, easing = motion.standard), initialAlpha = motion.pushDim)

/**
 * And the screen being dismissed leaves outright, on the exit curve rather than the standard one:
 * "nothing should linger once the user has decided it is done".
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushAway(
    millis: Int,
    motion: PtutMotion,
): ExitTransition = slideOutOfContainer(
    towards = SlideDirection.End,
    animationSpec = tween(millis, easing = motion.exit),
)

/** Sibling tabs cross-fade. Nothing moves sideways, because nothing got deeper. */
private fun fade(millis: Int, motion: PtutMotion): EnterTransition =
    fadeIn(tween(millis, easing = motion.standard))

private fun fadeAway(millis: Int, motion: PtutMotion): ExitTransition =
    fadeOut(tween(millis, easing = motion.exit))

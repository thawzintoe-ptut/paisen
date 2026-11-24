package com.ptut.expense.di

/**
 * Whether this is a debug build, passed in by the platform entry point.
 *
 * Common code cannot see `BuildConfig` and Kotlin/Native answers a different way, so the fact
 * crosses the seam the same way the database directory does — the entry point knows it, and
 * nothing in `commonMain` has to reach for an API it cannot see.
 *
 * [debug] gates one thing: the injectable slip source behind the debug menu. A release build never
 * binds it, so there is no path to a fake slip in a shipped app. [version] and [device] go into the
 * Send feedback email and nowhere else.
 */
data class AppBuild(val debug: Boolean, val version: String = "", val device: String = "")

/**
 * #116: the two release switches as this launch read them, for a graph that has to hide a way in.
 * [v2] is the Should column and [later] the Nice one; both are on in a debug build.
 */
data class ReleaseSwitches(val v2: Boolean, val later: Boolean)

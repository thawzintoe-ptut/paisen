package com.ptut.expense.di

/**
 * Whether this is a debug build, passed in by the platform entry point.
 *
 * Common code cannot see `BuildConfig` and Kotlin/Native answers a different way, so the fact
 * crosses the seam the same way the database directory does — the entry point knows it, and
 * nothing in `commonMain` has to reach for an API it cannot see.
 *
 * It gates one thing: the injectable slip source behind the debug menu. A release build never
 * binds it, so there is no path to a fake slip in a shipped app.
 */
data class AppBuild(val debug: Boolean)

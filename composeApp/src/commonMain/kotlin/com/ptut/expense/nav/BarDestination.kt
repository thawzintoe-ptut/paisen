package com.ptut.expense.nav

import com.ptut.expense.design.NavGlyph
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.ShellCopy

/**
 * The capsule's four tabs, in the order it draws them (#106); Add sits outside the capsule. The
 * enum owns the order so the bar cannot disagree with itself, and each entry names the route it
 * goes to rather than the bar deciding by position.
 *
 * Review and Activity are deliberately absent: they are pushed routes, not destinations.
 */
enum class BarDestination(
    val route: PtutRoute,
    private val labelIn: (ShellCopy) -> String,
    val glyph: NavGlyph,
) {
    Today(PtutRoute.Today, ShellCopy::tabToday, NavGlyph.Today),
    Reports(PtutRoute.Reports, ShellCopy::tabReports, NavGlyph.Reports),
    Shared(PtutRoute.Shared, ShellCopy::tabShared, NavGlyph.Shared),
    Settings(PtutRoute.Settings(), ShellCopy::tabSettings, NavGlyph.Settings),
    ;

    /** The tab's label, drawn under its glyph and read aloud. */
    val label: String get() = labelIn(AppText.copy.shell)
}

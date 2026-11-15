package com.ptut.expense.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.ShellCopy

/**
 * The pill's four destinations, in the order it draws them (#144, N1): add sits between Reports and
 * History. The enum owns the order so the pill cannot disagree with itself, and each entry names the
 * route it goes to rather than the pill deciding by position.
 *
 * Review and Activity are deliberately absent: they are pushed routes, not destinations.
 */
enum class BarDestination(
    val route: PtutRoute,
    private val labelIn: (ShellCopy) -> String,
    val icon: ImageVector,
) {
    Today(PtutRoute.Today, ShellCopy::tabToday, PtutIcon.Home.image),
    Reports(PtutRoute.Reports, ShellCopy::tabReports, PtutIcon.BarChart.image),
    Shared(PtutRoute.Shared, ShellCopy::tabShared, PtutIcon.Group.image),
    Settings(PtutRoute.Settings(), ShellCopy::tabSettings, PtutIcon.Settings.image),
    ;

    /** What a screen reader says for the slot (N6). The pill draws the glyph alone. */
    val label: String get() = labelIn(AppText.copy.shell)
}

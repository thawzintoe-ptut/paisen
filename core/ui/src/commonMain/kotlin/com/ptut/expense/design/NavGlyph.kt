package com.ptut.expense.design

import androidx.compose.ui.graphics.vector.ImageVector
import com.ptut.expense.design.icons.SoftIcons

/**
 * The nav capsule's glyphs (V2 N06, #108): an outline for a tab that is not selected, solid with
 * the detail cut out for the one that is, so colour alone never marks it.
 */
enum class NavGlyph(val outline: ImageVector, val solid: ImageVector) {
    Today(SoftIcons.House, SoftIcons.TodaySolid),
    Reports(SoftIcons.Chart, SoftIcons.ReportsSolid),
    Shared(SoftIcons.People, SoftIcons.SharedSolid),
    Settings(SoftIcons.Hexagon, SoftIcons.SettingsSolid),
    ;

    companion object {
        /** Add stays a plain plus, drawn heavier so it reads on ruby. */
        val Add: ImageVector get() = SoftIcons.NavAdd
    }
}

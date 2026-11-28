package com.ptut.expense.settings

import com.ptut.expense.nav.PtutRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDestinationTest {

    /**
     * The screen used to carry a slip-language row stating a fact nobody could act on from here.
     * First run is where that instruction belongs, so the row went — and nothing may bring it back
     * without a destination, because the enum has to name one.
     */
    @Test
    fun `every row names a destination except the switches and feedback`() {
        assertEquals(
            listOf(
                // #101: the graph adds the open goal's id.
                PtutRoute.GoalJar(),
                PtutRoute.SettingsAdvanced(),
                PtutRoute.PhotoFolders,
                PtutRoute.BackgroundImport,
                PtutRoute.KeepFailedSlips,
                // #26: opens a small dialog, not a screen.
                null,
                PtutRoute.Activity,
                PtutRoute.Categories,
                // #121, #20, #22, #123, #25 and #115: rows with no screen behind them; the row's tap changes it.
                null,
                null,
                null,
                null,
                null,
                null,
                PtutRoute.Language,
                PtutRoute.ExportData,
                PtutRoute.ImportData,
                PtutRoute.Timezone,
                PtutRoute.HomeCurrency,
                PtutRoute.AssistantModel,
                // Opens the phone's mail app, not a screen.
                null,
                // The store app and the share sheet.
                null,
                null,
                PtutRoute.Licences,
                // #83: Settings › Play.
                PtutRoute.PlaySettings,
                // The sign-in sheet and the help list open over Settings.
                null,
                null,
            ),
            SettingsAction.entries.map { it.destination() },
        )
    }
}

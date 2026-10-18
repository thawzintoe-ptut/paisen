package com.ptut.expense.settings

import com.ptut.expense.domain.ImportFix
import com.ptut.expense.nav.PtutRoute

/** Settings, opened at the row that owns this fix rather than at the top of the screen. */
fun ImportFix.settingsRoute(): PtutRoute.Settings = PtutRoute.Settings(owningRow().name)

/** The row [PtutRoute.Settings] was opened at, or null when the bar opened it. */
fun PtutRoute.Settings.focusedRow(): SettingsAction? =
    focusRow?.let { name -> SettingsAction.entries.firstOrNull { it.name == name } }

/**
 * Where each row goes.
 *
 * Exhaustive on purpose: a row added to [SettingsAction] without a destination is a compile error
 * here rather than a tap that does nothing on a screen where every other row opens something.
 */
fun SettingsAction.destination(): PtutRoute? = when (this) {
    SettingsAction.SlipSource -> PtutRoute.PhotoFolders

    SettingsAction.BackgroundImport -> PtutRoute.BackgroundImport

    SettingsAction.KeepFailedSlips -> PtutRoute.KeepFailedSlips

    SettingsAction.Activity -> PtutRoute.Activity

    SettingsAction.Categories -> PtutRoute.Categories

    // Switches with no screen behind them: the row's tap flips it (#121, #123).
    SettingsAction.MoodByTimeOfDay, SettingsAction.Transparency -> null

    SettingsAction.ExportLedger -> PtutRoute.ExportData

    SettingsAction.ImportLedger -> PtutRoute.ImportData

    SettingsAction.Timezone -> PtutRoute.Timezone

    SettingsAction.Language -> PtutRoute.Language

    SettingsAction.Assistant -> PtutRoute.AssistantModel

    SettingsAction.Licences -> PtutRoute.Licences
}

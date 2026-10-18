package com.ptut.expense.settings

import com.ptut.expense.domain.ImportFix

/**
 * The lever that owns each fix, in one place so Today's notice and Settings cannot disagree about
 * which row the reader was sent to.
 */
fun ImportFix.owningRow(): SettingsAction = when (this) {
    ImportFix.GrantPhotoAccess, ImportFix.AllowAllPhotos -> SettingsAction.SlipSource
    ImportFix.ResumeWatching -> SettingsAction.BackgroundImport
}

/**
 * Whether the row opens a screen. The two switches flip in place (#121, #123). The app module maps
 * each row that opens one to its route, in `SettingsRoutes.kt`.
 */
fun SettingsAction.opensScreen(): Boolean = when (this) {
    SettingsAction.MoodByTimeOfDay, SettingsAction.Transparency -> false

    SettingsAction.SlipSource,
    SettingsAction.BackgroundImport,
    SettingsAction.KeepFailedSlips,
    SettingsAction.Activity,
    SettingsAction.Categories,
    SettingsAction.ExportLedger,
    SettingsAction.ImportLedger,
    SettingsAction.Timezone,
    SettingsAction.Language,
    SettingsAction.Assistant,
    SettingsAction.Licences,
    -> true
}

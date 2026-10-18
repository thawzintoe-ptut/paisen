package com.ptut.expense.settings

import com.ptut.expense.locale.AppText

/**
 * C-5: fifteen minutes is WorkManager's floor and OEM battery managers stretch it, which is why
 * the row says a cadence and no copy anywhere promises instant.
 */
actual fun slipImportCadence(): SlipImportCadence = AppText.copy.settings.let { text ->
    SlipImportCadence(
        // Shown only when nothing is watched: the row lists the watched folders otherwise (#124).
        slipSource = text.noFolderWatched,
        // "On" and not "off" when nothing has run yet: the job is scheduled, the ledger is simply new,
        // and saying off would blame a setting for that.
        importRhythm = text.importOn,
        importIsAdjustable = true,
        importExplanation = text.androidImportExplanation,
    )
}

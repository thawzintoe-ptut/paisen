package com.ptut.expense.settings

import com.ptut.expense.locale.AppText

/**
 * C-4: iOS hands an app no periodic window it can rely on, so the catch-up happens when the app
 * comes back to the foreground. The source row carries the reason and the import row carries the
 * fact, which keeps either line short enough to read at a glance.
 *
 * [SlipImportCadence.importIsAdjustable] is false because there is nothing behind the row: a switch
 * there would turn off a job that does not exist.
 */
actual fun slipImportCadence(): SlipImportCadence = AppText.copy.settings.let { text ->
    SlipImportCadence(
        // Shown only when nothing is watched: the row lists the watched albums otherwise (#124).
        slipSource = text.noAlbumWatched,
        importRhythm = text.onEachAppOpen,
        importIsAdjustable = false,
        importExplanation = text.iosImportExplanation,
    )
}

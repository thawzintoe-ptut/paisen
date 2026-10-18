package com.ptut.expense.settings

import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettingsStore
import platform.Foundation.NSUserDefaults
import kotlin.time.Duration.Companion.days

private const val BACKGROUND_IMPORT = "ptut-background-import"
private const val RETENTION_DAYS = "ptut-retention-days"
private const val MOOD_BY_TIME_OF_DAY = "ptut-mood-by-time-of-day"
private const val TRANSPARENCY = "ptut-transparency"

/**
 * The iOS half of [ImportSettingsStore]. Both keys are written together, so a zero read back from
 * `integerForKey` means nobody has written one — `NSUserDefaults` answers 0 for a missing key and
 * has no way to tell that apart from a stored 0, which is not a retention this app offers.
 */
class UserDefaultsImportSettingsStore : ImportSettingsStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): ImportChoices {
        val shipped = ImportChoices()
        val storedDays = defaults.integerForKey(RETENTION_DAYS).toInt()
        return ImportChoices(
            backgroundImport = defaults.objectForKey(BACKGROUND_IMPORT)
                ?.let { defaults.boolForKey(BACKGROUND_IMPORT) }
                ?: shipped.backgroundImport,
            retention = if (storedDays > 0) storedDays.days else shipped.retention,
            moodByTimeOfDay = defaults.objectForKey(MOOD_BY_TIME_OF_DAY)
                ?.let { defaults.boolForKey(MOOD_BY_TIME_OF_DAY) }
                ?: shipped.moodByTimeOfDay,
            transparency = defaults.objectForKey(TRANSPARENCY)
                ?.let { defaults.boolForKey(TRANSPARENCY) }
                ?: shipped.transparency,
        )
    }

    override fun write(choices: ImportChoices) {
        defaults.setBool(choices.backgroundImport, BACKGROUND_IMPORT)
        defaults.setInteger(choices.retention.inWholeDays, RETENTION_DAYS)
        defaults.setBool(choices.moodByTimeOfDay, MOOD_BY_TIME_OF_DAY)
        defaults.setBool(choices.transparency, TRANSPARENCY)
    }
}

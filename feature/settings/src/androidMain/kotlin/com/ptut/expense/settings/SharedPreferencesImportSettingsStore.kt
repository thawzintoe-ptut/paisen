package com.ptut.expense.settings

import android.content.Context
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettingsStore
import kotlin.time.Duration.Companion.days

private const val CHOICES_FILE = "ptut-import-settings"
private const val BACKGROUND_IMPORT = "background-import"
private const val RETENTION_DAYS = "retention-days"
private const val MOOD_BY_TIME_OF_DAY = "mood-by-time-of-day"
private const val TRANSPARENCY = "transparency"

/**
 * Two values in their own file, so clearing it by hand puts the shipped defaults back.
 *
 * The stored form is whole days rather than a serialised [kotlin.time.Duration]: the picker only
 * offers whole days, and an integer is the one shape both platforms' stores hold natively.
 */
class SharedPreferencesImportSettingsStore(context: Context) : ImportSettingsStore {

    private val choices = context.applicationContext
        .getSharedPreferences(CHOICES_FILE, Context.MODE_PRIVATE)

    override fun read(): ImportChoices {
        val shipped = ImportChoices()
        return ImportChoices(
            backgroundImport = choices.getBoolean(BACKGROUND_IMPORT, shipped.backgroundImport),
            retention = choices
                .getInt(RETENTION_DAYS, shipped.retention.inWholeDays.toInt())
                .days,
            moodByTimeOfDay = choices.getBoolean(MOOD_BY_TIME_OF_DAY, shipped.moodByTimeOfDay),
            transparency = choices.getBoolean(TRANSPARENCY, shipped.transparency),
        )
    }

    override fun write(choices: ImportChoices) {
        this.choices.edit()
            .putBoolean(BACKGROUND_IMPORT, choices.backgroundImport)
            .putInt(RETENTION_DAYS, choices.retention.inWholeDays.toInt())
            .putBoolean(MOOD_BY_TIME_OF_DAY, choices.moodByTimeOfDay)
            .putBoolean(TRANSPARENCY, choices.transparency)
            .apply()
    }
}

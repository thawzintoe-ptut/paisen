package com.ptut.expense.settings

import android.content.Context
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.domain.slip.SlipOwner
import com.ptut.expense.locale.NoteTone
import kotlin.time.Duration.Companion.days

private const val CHOICES_FILE = "ptut-import-settings"
private const val BACKGROUND_IMPORT = "background-import"
private const val RETENTION_DAYS = "retention-days"
private const val MOOD_BY_TIME_OF_DAY = "mood-by-time-of-day"
private const val TRANSPARENCY = "transparency"
private const val APP_LOCK = "app-lock"
private const val WEEKLY_CHECK_IN = "weekly-check-in"
private const val TARGET_CATEGORY = "weekly-target-category"
private const val TARGET_SATANG = "weekly-target-satang"
private const val OWNER_NAME = "slip-owner-name"
private const val OWNER_CANDIDATE = "slip-owner-candidate"
private const val OWNER_SEEN = "slip-owner-seen"
private const val NOTE_TONE = "note-tone"
private const val HIDDEN_NOTE_WEEK = "hidden-note-week"
private const val ADD_CURRENCY = "add-currency"
private const val HOME_CURRENCY = "home-currency"
private const val HOME_ZONE = "home-zone"

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
            appLock = choices.getBoolean(APP_LOCK, shipped.appLock),
            weeklyCheckIn = choices.getBoolean(WEEKLY_CHECK_IN, shipped.weeklyCheckIn),
            weeklyTarget = choices.getString(TARGET_CATEGORY, null)?.let { id ->
                choices.getLong(TARGET_SATANG, 0L).takeIf { it > 0L }?.let { WeeklyTarget(id, Satang.ofMinorUnits(it)) }
            },
            slipOwner = SlipOwner(
                name = choices.getString(OWNER_NAME, null),
                candidate = choices.getString(OWNER_CANDIDATE, null),
                seen = choices.getInt(OWNER_SEEN, 0),
            ),
            noteTone = NoteTone.entries.firstOrNull { it.name == choices.getString(NOTE_TONE, null) } ?: shipped.noteTone,
            hiddenNoteWeek = choices.getString(HIDDEN_NOTE_WEEK, null),
            addCurrency = choices.getString(ADD_CURRENCY, null),
            homeCurrency = choices.getString(HOME_CURRENCY, null),
            homeZone = choices.getString(HOME_ZONE, null),
        )
    }

    override fun write(choices: ImportChoices) {
        this.choices.edit()
            .putBoolean(BACKGROUND_IMPORT, choices.backgroundImport)
            .putInt(RETENTION_DAYS, choices.retention.inWholeDays.toInt())
            .putBoolean(MOOD_BY_TIME_OF_DAY, choices.moodByTimeOfDay)
            .putBoolean(TRANSPARENCY, choices.transparency)
            .putBoolean(APP_LOCK, choices.appLock)
            .putBoolean(WEEKLY_CHECK_IN, choices.weeklyCheckIn)
            .putString(TARGET_CATEGORY, choices.weeklyTarget?.categoryId)
            .putLong(TARGET_SATANG, choices.weeklyTarget?.amount?.toMinorUnits() ?: 0L)
            .putString(OWNER_NAME, choices.slipOwner.name)
            .putString(OWNER_CANDIDATE, choices.slipOwner.candidate)
            .putInt(OWNER_SEEN, choices.slipOwner.seen)
            .putString(NOTE_TONE, choices.noteTone.name)
            .putString(HIDDEN_NOTE_WEEK, choices.hiddenNoteWeek)
            .putString(ADD_CURRENCY, choices.addCurrency)
            .putString(HOME_CURRENCY, choices.homeCurrency)
            .putString(HOME_ZONE, choices.homeZone)
            .apply()
    }
}

package com.ptut.expense.settings

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.domain.slip.SlipOwner
import platform.Foundation.NSUserDefaults
import kotlin.time.Duration.Companion.days

private const val BACKGROUND_IMPORT = "ptut-background-import"
private const val RETENTION_DAYS = "ptut-retention-days"
private const val MOOD_BY_TIME_OF_DAY = "ptut-mood-by-time-of-day"
private const val TRANSPARENCY = "ptut-transparency"
private const val APP_LOCK = "ptut-app-lock"
private const val WEEKLY_CHECK_IN = "ptut-weekly-check-in"
private const val TARGET_CATEGORY = "ptut-weekly-target-category"
private const val TARGET_SATANG = "ptut-weekly-target-satang"
private const val OWNER_NAME = "ptut-slip-owner-name"
private const val OWNER_CANDIDATE = "ptut-slip-owner-candidate"
private const val OWNER_SEEN = "ptut-slip-owner-seen"

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
            appLock = defaults.boolForKey(APP_LOCK),
            weeklyCheckIn = defaults.objectForKey(WEEKLY_CHECK_IN)
                ?.let { defaults.boolForKey(WEEKLY_CHECK_IN) }
                ?: shipped.weeklyCheckIn,
            weeklyTarget = defaults.stringForKey(TARGET_CATEGORY)?.let { id ->
                defaults.integerForKey(TARGET_SATANG).takeIf { it > 0L }?.let { WeeklyTarget(id, Satang.ofMinorUnits(it)) }
            },
            slipOwner = SlipOwner(
                name = defaults.stringForKey(OWNER_NAME),
                candidate = defaults.stringForKey(OWNER_CANDIDATE),
                seen = defaults.integerForKey(OWNER_SEEN).toInt(),
            ),
        )
    }

    override fun write(choices: ImportChoices) {
        defaults.setBool(choices.backgroundImport, BACKGROUND_IMPORT)
        defaults.setInteger(choices.retention.inWholeDays, RETENTION_DAYS)
        defaults.setBool(choices.moodByTimeOfDay, MOOD_BY_TIME_OF_DAY)
        defaults.setBool(choices.transparency, TRANSPARENCY)
        defaults.setBool(choices.appLock, APP_LOCK)
        defaults.setBool(choices.weeklyCheckIn, WEEKLY_CHECK_IN)
        writeOptional(choices.slipOwner.name, OWNER_NAME)
        writeOptional(choices.slipOwner.candidate, OWNER_CANDIDATE)
        defaults.setInteger(choices.slipOwner.seen.toLong(), OWNER_SEEN)
        val target = choices.weeklyTarget
        if (target == null) {
            defaults.removeObjectForKey(TARGET_CATEGORY)
            defaults.removeObjectForKey(TARGET_SATANG)
        } else {
            defaults.setObject(target.categoryId, TARGET_CATEGORY)
            defaults.setInteger(target.amount.toMinorUnits(), TARGET_SATANG)
        }
    }

    private fun writeOptional(value: String?, key: String) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}

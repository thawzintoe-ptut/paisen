package com.ptut.expense.settings

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.playOnWhenUnset
import com.ptut.expense.domain.slip.SlipOwner
import com.ptut.expense.locale.NoteTone
import com.ptut.expense.onboarding.UserDefaultsOnboardingFlag
import com.ptut.expense.platform.files.documentsDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
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
private const val NOTE_TONE = "ptut-note-tone"
private const val HIDDEN_NOTE_WEEK = "ptut-hidden-note-week"

// "Add starts in" was dropped; the next write clears what older builds saved.
private const val ADD_CURRENCY = "ptut-add-currency"
private const val HOME_CURRENCY = "ptut-home-currency"
private const val HOME_ZONE = "ptut-home-zone"
private const val PLAY_ON = "ptut-play-on"
private const val PLAY_RUN = "ptut-play-run"
private const val PLAY_GUESS = "ptut-play-guess"
private const val PLAY_CHALLENGES = "ptut-play-challenges"
private const val PLAY_GROUP_STAMPS = "ptut-play-group-stamps"
private const val PLAY_SHARE_AMOUNTS = "ptut-play-share-amounts"
private const val PLAY_WEEKLY_REMINDER = "ptut-play-weekly-reminder"
private const val OWNER_FILE = "slip-owner.json"
private const val NAME_FIELD = "name"
private const val CANDIDATE_FIELD = "candidate"

/**
 * The iOS half of [AppSettingsStore]. Every key is written together, so a zero retention read back
 * from `integerForKey` means nobody has written one — `NSUserDefaults` answers 0 for a missing key and
 * has no way to tell that apart from a stored 0, which is not a retention this app offers.
 *
 * M13: the slip owner's real name lives in [ownerFile] under Documents, which iCloud and computer
 * backups skip; `NSUserDefaults` is in those backups.
 */
class UserDefaultsAppSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val ownerFile: Path = requireNotNull(documentsDirectory().path) { "iOS documents directory has no path" }.toPath() / OWNER_FILE,
    private val firstRunFinished: () -> Boolean = { UserDefaultsOnboardingFlag().hasFinished() },
) : AppSettingsStore {

    private val files = FileSystem.SYSTEM

    override fun read(): AppChoices {
        moveOwnerOutOfDefaults()
        val shipped = AppChoices()
        val owner = readOwner()
        val storedDays = defaults.integerForKey(RETENTION_DAYS).toInt()
        return AppChoices(
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
            slipOwner = owner.copy(seen = defaults.integerForKey(OWNER_SEEN).toInt()),
            noteTone = NoteTone.entries.firstOrNull { it.name == defaults.stringForKey(NOTE_TONE) } ?: shipped.noteTone,
            hiddenNoteWeek = defaults.stringForKey(HIDDEN_NOTE_WEEK),
            homeCurrency = defaults.stringForKey(HOME_CURRENCY),
            homeZone = defaults.stringForKey(HOME_ZONE),
            play = shipped.play.let { play ->
                PlayChoices(
                    on = readPlayOn(),
                    run = readBool(PLAY_RUN, play.run),
                    guess = readBool(PLAY_GUESS, play.guess),
                    challenges = readBool(PLAY_CHALLENGES, play.challenges),
                    groupStamps = readBool(PLAY_GROUP_STAMPS, play.groupStamps),
                    shareAmounts = readBool(PLAY_SHARE_AMOUNTS, play.shareAmounts),
                    weeklyReminder = readBool(PLAY_WEEKLY_REMINDER, play.weeklyReminder),
                )
            },
        )
    }

    /** `boolForKey` answers false for a missing key, so a switch that ships on reads its default until written. */
    private fun readBool(key: String, shipped: Boolean): Boolean =
        defaults.objectForKey(key)?.let { defaults.boolForKey(key) } ?: shipped

    private fun readPlayOn(): Boolean {
        if (defaults.objectForKey(PLAY_ON) != null) return defaults.boolForKey(PLAY_ON)
        val on = playOnWhenUnset(firstRunFinished())
        if (!on) defaults.setBool(false, PLAY_ON)
        return on
    }

    override fun write(choices: AppChoices) {
        moveOwnerOutOfDefaults()
        defaults.setBool(choices.backgroundImport, BACKGROUND_IMPORT)
        defaults.setInteger(choices.retention.inWholeDays, RETENTION_DAYS)
        defaults.setBool(choices.moodByTimeOfDay, MOOD_BY_TIME_OF_DAY)
        defaults.setBool(choices.transparency, TRANSPARENCY)
        defaults.setBool(choices.appLock, APP_LOCK)
        defaults.setBool(choices.weeklyCheckIn, WEEKLY_CHECK_IN)
        writeOwner(choices.slipOwner.name, choices.slipOwner.candidate)
        defaults.setInteger(choices.slipOwner.seen.toLong(), OWNER_SEEN)
        defaults.setObject(choices.noteTone.name, NOTE_TONE)
        writeOptional(choices.hiddenNoteWeek, HIDDEN_NOTE_WEEK)
        defaults.removeObjectForKey(ADD_CURRENCY)
        writeOptional(choices.homeCurrency, HOME_CURRENCY)
        writeOptional(choices.homeZone, HOME_ZONE)
        defaults.setBool(choices.play.on, PLAY_ON)
        defaults.setBool(choices.play.run, PLAY_RUN)
        defaults.setBool(choices.play.guess, PLAY_GUESS)
        defaults.setBool(choices.play.challenges, PLAY_CHALLENGES)
        defaults.setBool(choices.play.groupStamps, PLAY_GROUP_STAMPS)
        defaults.setBool(choices.play.shareAmounts, PLAY_SHARE_AMOUNTS)
        defaults.setBool(choices.play.weeklyReminder, PLAY_WEEKLY_REMINDER)
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

    /** Builds before M13 kept the name in `NSUserDefaults`; it moves once, and a file already there wins. */
    private fun moveOwnerOutOfDefaults() {
        val name = defaults.stringForKey(OWNER_NAME)
        val candidate = defaults.stringForKey(OWNER_CANDIDATE)
        if (name == null && candidate == null) return
        if (files.exists(ownerFile) || writeOwner(name, candidate)) {
            defaults.removeObjectForKey(OWNER_NAME)
            defaults.removeObjectForKey(OWNER_CANDIDATE)
        }
    }

    /** A missing or unreadable file reads as no owner learned yet, as a fresh install does. */
    private fun readOwner(): SlipOwner = runCatching {
        val fields = Json.parseToJsonElement(files.read(ownerFile) { readUtf8() }).jsonObject
        SlipOwner(
            name = fields[NAME_FIELD]?.jsonPrimitive?.contentOrNull,
            candidate = fields[CANDIDATE_FIELD]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrDefault(SlipOwner())

    private fun writeOwner(name: String?, candidate: String?): Boolean = runCatching {
        if (name == null && candidate == null) {
            files.delete(ownerFile)
        } else {
            val fields = buildJsonObject {
                name?.let { put(NAME_FIELD, JsonPrimitive(it)) }
                candidate?.let { put(CANDIDATE_FIELD, JsonPrimitive(it)) }
            }
            files.write(ownerFile) { writeUtf8(fields.toString()) }
        }
    }.isSuccess
}

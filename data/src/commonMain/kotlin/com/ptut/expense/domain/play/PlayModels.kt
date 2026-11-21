package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate

/** Whole percent, for the share-of-usual rules play works in. */
const val PERCENT = 100L

/** A day of play (#85, #87), keyed by its date in the home zone. Only what the reader did is kept. */
data class PlayDay(
    val date: LocalDate,
    val clearedAtEpochMillis: Long? = null,
    val answeredAtEpochMillis: Long? = null,
    /** The pair asked, as [questionKey] builds it, for the 30-day rule that no pair comes back. */
    val questionKey: String? = null,
    val answeredRight: Boolean? = null,
    /** Which card was tapped, so an answered pair draws the pick the reader made, not one worked back from right or wrong. */
    val pickedFirst: Boolean? = null,
)

/** A week of play (#86, #91), keyed by its Monday. */
data class PlayWeek(
    val monday: LocalDate,
    /** Fixed before the reader guesses; never changed after. */
    val paisenGuess: Satang? = null,
    val guess: Satang? = null,
    /** The week's spend at the reveal. Later edits to the ledger do not change the result. */
    val total: Satang? = null,
    val challenge: Challenge? = null,
)

/** #91: spend less than [limit] on [categoryId] this week. */
data class Challenge(val categoryId: String, val level: ChallengeLevel, val limit: Satang)

enum class ChallengeLevel { Easy, Medium, Hard }

/** #90, #98: a month of play. [month] is ISO `yyyy-MM`. */
data class PlayMonth(val month: String, val storySeenAtEpochMillis: Long? = null, val sealedAtEpochMillis: Long? = null)

/** p04's three sets. */
enum class StampSet { Everyday, Night, Wins }

/**
 * #88: what a stamp shows. A cleared tray earns its weekday, or a rare one from a pattern, never
 * from chance (D3). The wins are earned outside the tray.
 */
enum class StampKind(val set: StampSet, val rare: Boolean) {
    Monday(StampSet.Everyday, false),
    Tuesday(StampSet.Everyday, false),
    Wednesday(StampSet.Everyday, false),
    Thursday(StampSet.Everyday, false),
    Friday(StampSet.Everyday, false),
    Saturday(StampSet.Everyday, false),
    Sunday(StampSet.Everyday, false),

    /** Cleared at 22:00 or later. */
    NightOwl(StampSet.Night, true),

    /** Cleared before 08:00. */
    EarlyBird(StampSet.Night, true),

    /** Ten or more slips filed on the day. */
    BigSweep(StampSet.Night, true),
    BeatPaiSeN(StampSet.Wins, false),
    ChallengeMet(StampSet.Wins, false),
    MonthClosed(StampSet.Wins, false),
    AllSquare(StampSet.Wins, false),
}

/** [id] is `<kind>:<ref>`, so earning the same thing twice keeps one row. */
data class Stamp(
    val id: String,
    val kind: StampKind,
    val ref: String,
    val earnedOn: LocalDate,
    /** Null while the card is face down. */
    val revealedAtEpochMillis: Long? = null,
)

fun stampId(kind: StampKind, ref: String): String = "${kind.name}:$ref"

/** #92: a goal jar. No money moves; the ledger's own rows fill it. */
data class Goal(
    val id: String,
    val name: String,
    val target: Satang,
    val currency: String,
    /** Income less spending from this day fills the jar. */
    val start: LocalDate,
    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
)

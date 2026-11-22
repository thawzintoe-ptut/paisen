package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Play's facts on this phone (#82). Each write that earns something is one transaction with a fixed
 * id, so a crash or a second tap never earns twice. Nothing here leaves the phone.
 */
interface PlayBook {

    val days: Flow<List<PlayDay>>
    val weeks: Flow<List<PlayWeek>>
    val months: Flow<List<PlayMonth>>
    val stamps: Flow<List<Stamp>>
    val goals: Flow<List<Goal>>
    val goalDays: Flow<List<GoalDay>>
    val deposits: Flow<List<GoalDeposit>>
    val seenAccounts: Flow<List<SeenAccount>>

    /** #85, #88: the first sighting of an empty tray on [day], and that day's stamp. False when already cleared. */
    suspend fun markCleared(day: LocalDate, atEpochMillis: Long, stamp: StampKind): Boolean

    /** #87: the first answer of [day] is kept; a second does nothing. */
    suspend fun answer(day: LocalDate, questionKey: String, right: Boolean, pickedFirst: Boolean, atEpochMillis: Long)

    /** #86: stores [guess] for the week unless one is there, and returns the stored one. */
    suspend fun fixPaisenGuess(monday: LocalDate, guess: Satang): Satang

    /** #86: the reader's guess and the week's total, once; earns BeatPaiSeN on [on] only when [youCloser]. */
    suspend fun revealGuess(monday: LocalDate, guess: Satang, total: Satang, youCloser: Boolean, on: LocalDate)

    /** #88: the card turned over. A second turn keeps the first time. */
    suspend fun reveal(stampId: String, atEpochMillis: Long)

    /** #91, #95: a stamp earned outside the tray. False when it was earned before. */
    suspend fun earn(kind: StampKind, ref: String, on: LocalDate): Boolean

    /** #91: one challenge a week; picking again the same week replaces it. */
    suspend fun pickChallenge(monday: LocalDate, challenge: Challenge)

    /** #98: seals [month] and earns MonthClosed in the same transaction. */
    suspend fun seal(month: String, atEpochMillis: Long, on: LocalDate)

    /** #90: the story was read through once. */
    suspend fun storySeen(month: String, atEpochMillis: Long)

    /** #92 */
    suspend fun saveGoal(goal: Goal)

    suspend fun archiveGoal(goalId: String, atEpochMillis: Long)

    /** #104: Count it or Not today for [GoalDay.day]. The first answer for a day stands. */
    suspend fun answerDay(day: GoalDay)

    /** J02: money into a jar. False when a deposit with this id is there already. */
    suspend fun deposit(deposit: GoalDeposit): Boolean

    /** J02: takes back a deposit the reader said they made; one PaiSeN read from a slip stays. */
    suspend fun removeSaidDeposit(id: String)

    /** J04: where the jar's money goes, asked once. */
    suspend fun chooseAccount(goalId: String, account: String)

    /** J04: a slip moved money to one of the reader's own accounts; the count grows by one. */
    suspend fun seeAccount(mask: String, holder: String, atEpochMillis: Long)
}

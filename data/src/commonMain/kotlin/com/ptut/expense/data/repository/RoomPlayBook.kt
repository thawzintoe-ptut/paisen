package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.PlayDao
import com.ptut.expense.data.local.entity.PlayDayEntity
import com.ptut.expense.data.local.entity.PlayGoalDayEntity
import com.ptut.expense.data.local.entity.PlayGoalDepositEntity
import com.ptut.expense.data.local.entity.PlayGoalEntity
import com.ptut.expense.data.local.entity.PlayMonthEntity
import com.ptut.expense.data.local.entity.PlayStampEntity
import com.ptut.expense.data.local.entity.PlayWeekEntity
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.play.Challenge
import com.ptut.expense.domain.play.ChallengeLevel
import com.ptut.expense.domain.play.DepositSource
import com.ptut.expense.domain.play.Goal
import com.ptut.expense.domain.play.GoalDay
import com.ptut.expense.domain.play.GoalDeposit
import com.ptut.expense.domain.play.PlayBook
import com.ptut.expense.domain.play.PlayDay
import com.ptut.expense.domain.play.PlayMonth
import com.ptut.expense.domain.play.PlayWeek
import com.ptut.expense.domain.play.SeenAccount
import com.ptut.expense.domain.play.Stamp
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.play.stampId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** #82: play's facts in Room. A row this build cannot read, such as a stamp kind from a newer one, is skipped. */
class RoomPlayBook(private val play: PlayDao) : PlayBook {

    override val days: Flow<List<PlayDay>> = play.observeDays().map { rows -> rows.mapNotNull { it.toDay() } }
    override val weeks: Flow<List<PlayWeek>> = play.observeWeeks().map { rows -> rows.mapNotNull { it.toWeek() } }
    override val months: Flow<List<PlayMonth>> = play.observeMonths().map { rows -> rows.map { it.toMonth() } }
    override val stamps: Flow<List<Stamp>> = play.observeStamps().map { rows -> rows.mapNotNull { it.toStamp() } }
    override val goals: Flow<List<Goal>> = play.observeGoals().map { rows -> rows.mapNotNull { it.toGoal() } }
    override val goalDays: Flow<List<GoalDay>> = play.observeGoalDays().map { rows -> rows.mapNotNull { it.toGoalDay() } }
    override val deposits: Flow<List<GoalDeposit>> = play.observeDeposits().map { rows -> rows.mapNotNull { it.toDeposit() } }
    override val seenAccounts: Flow<List<SeenAccount>> = play.observeSeenAccounts().map { rows -> rows.map { SeenAccount(it.mask, it.holder, it.seenCount, it.lastSeenEpochMillis) } }

    override suspend fun markCleared(day: LocalDate, atEpochMillis: Long, stamp: StampKind): Boolean =
        play.clear(day.toString(), atEpochMillis, stampRow(stamp, ref = day.toString(), on = day))

    override suspend fun answer(day: LocalDate, questionKey: String, right: Boolean, pickedFirst: Boolean, atEpochMillis: Long) =
        play.answer(day.toString(), questionKey, right, pickedFirst, atEpochMillis)

    override suspend fun fixPaisenGuess(monday: LocalDate, guess: Satang): Satang =
        Satang.ofMinorUnits(play.fixPaisenGuess(monday.toString(), guess.toMinorUnits()))

    override suspend fun revealGuess(monday: LocalDate, guess: Satang, total: Satang, youCloser: Boolean, on: LocalDate) =
        play.revealGuess(
            monday = monday.toString(),
            guess = guess.toMinorUnits(),
            total = total.toMinorUnits(),
            stamp = if (youCloser) stampRow(StampKind.BeatPaiSeN, ref = monday.toString(), on = on) else null,
        )

    override suspend fun reveal(stampId: String, atEpochMillis: Long) = play.reveal(stampId, atEpochMillis)

    override suspend fun earn(kind: StampKind, ref: String, on: LocalDate): Boolean = play.earn(stampRow(kind, ref, on)) != NOT_INSERTED

    override suspend fun pickChallenge(monday: LocalDate, challenge: Challenge) =
        play.pickChallenge(monday.toString(), challenge.categoryId, challenge.level.name, challenge.limit.toMinorUnits())

    override suspend fun seal(month: String, atEpochMillis: Long, on: LocalDate) =
        play.seal(month, atEpochMillis, stampRow(StampKind.MonthClosed, ref = month, on = on))

    override suspend fun storySeen(month: String, atEpochMillis: Long) = play.storySeen(month, atEpochMillis)

    override suspend fun saveGoal(goal: Goal) = play.writeGoal(goal.toEntity())

    override suspend fun archiveGoal(goalId: String, atEpochMillis: Long) = play.archiveGoal(goalId, atEpochMillis)

    override suspend fun answerDay(day: GoalDay) =
        play.countDay(PlayGoalDayEntity(day.goalId, day.day.toString(), day.usual?.toMinorUnits(), day.atEpochMillis))

    override suspend fun deposit(deposit: GoalDeposit): Boolean = play.deposit(deposit.toEntity()) != NOT_INSERTED

    override suspend fun removeSaidDeposit(id: String) = play.removeSaidDeposit(id)

    override suspend fun chooseAccount(goalId: String, account: String) = play.chooseAccount(goalId, account)

    override suspend fun seeAccount(mask: String, holder: String, atEpochMillis: Long) = play.seeAccount(mask, holder, atEpochMillis)

    private fun stampRow(kind: StampKind, ref: String, on: LocalDate) =
        PlayStampEntity(id = stampId(kind, ref), kind = kind.name, ref = ref, earnedOn = on.toString())
}

/** What Room's insert answers when IGNORE kept the row already there. */
private const val NOT_INSERTED = -1L

private fun String.toDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun PlayDayEntity.toDay(): PlayDay? = date.toDateOrNull()?.let { day ->
    PlayDay(day, clearedAtEpochMillis, answeredAtEpochMillis, questionKey, answeredRight, pickedFirst)
}

private fun PlayWeekEntity.toWeek(): PlayWeek? = monday.toDateOrNull()?.let { day ->
    val level = ChallengeLevel.entries.firstOrNull { it.name == challengeLevel }
    PlayWeek(
        monday = day,
        paisenGuess = paisenGuessMinor?.let(Satang::ofMinorUnits),
        guess = guessMinor?.let(Satang::ofMinorUnits),
        total = totalMinor?.let(Satang::ofMinorUnits),
        challenge = if (challengeCategoryId != null && level != null && challengeLimitMinor != null) {
            Challenge(challengeCategoryId, level, Satang.ofMinorUnits(challengeLimitMinor))
        } else {
            null
        },
    )
}

private fun PlayMonthEntity.toMonth() = PlayMonth(month, storySeenAtEpochMillis, sealedAtEpochMillis)

private fun PlayStampEntity.toStamp(): Stamp? {
    val known = StampKind.entries.firstOrNull { it.name == kind } ?: return null
    val day = earnedOn.toDateOrNull() ?: return null
    return Stamp(id, known, ref, day, revealedAtEpochMillis)
}

private fun PlayGoalEntity.toGoal(): Goal? = startDate.toDateOrNull()?.let { day ->
    Goal(id, name, Satang.ofMinorUnits(targetMinor), currency, day, createdAtEpochMillis, archivedAtEpochMillis, savingsAccount)
}

private fun PlayGoalDepositEntity.toDeposit(): GoalDeposit? {
    val day = date.toDateOrNull() ?: return null
    val from = DepositSource.entries.firstOrNull { it.name == source } ?: return null
    return GoalDeposit(id, goalId, Satang.ofMinorUnits(amountMinor), day, atEpochMillis, from, account)
}

private fun GoalDeposit.toEntity() = PlayGoalDepositEntity(
    id = id,
    goalId = goalId,
    amountMinor = amount.toMinorUnits(),
    date = day.toString(),
    atEpochMillis = atEpochMillis,
    source = source.name,
    account = account,
)

private fun PlayGoalDayEntity.toGoalDay(): GoalDay? = date.toDateOrNull()?.let { day ->
    GoalDay(goalId, day, usualMinor?.let(Satang::ofMinorUnits), atEpochMillis)
}

private fun Goal.toEntity() = PlayGoalEntity(
    id = id,
    name = name,
    targetMinor = target.toMinorUnits(),
    currency = currency,
    startDate = start.toString(),
    createdAtEpochMillis = createdAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
    savingsAccount = savingsAccount,
)

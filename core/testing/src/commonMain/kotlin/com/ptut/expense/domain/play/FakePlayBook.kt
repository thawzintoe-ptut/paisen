package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** [PlayBook] in memory, with the Room book's once-only rules, so a test can hold a screen to them. */
class FakePlayBook(
    days: List<PlayDay> = emptyList(),
    weeks: List<PlayWeek> = emptyList(),
    months: List<PlayMonth> = emptyList(),
    stamps: List<Stamp> = emptyList(),
    goals: List<Goal> = emptyList(),
    goalDays: List<GoalDay> = emptyList(),
    deposits: List<GoalDeposit> = emptyList(),
    seenAccounts: List<SeenAccount> = emptyList(),
) : PlayBook {

    private val dayRows = MutableStateFlow(days)
    private val weekRows = MutableStateFlow(weeks)
    private val monthRows = MutableStateFlow(months)
    private val stampRows = MutableStateFlow(stamps)
    private val goalRows = MutableStateFlow(goals)
    private val goalDayRows = MutableStateFlow(goalDays)
    private val depositRows = MutableStateFlow(deposits)
    private val seenRows = MutableStateFlow(seenAccounts)

    override val days: StateFlow<List<PlayDay>> = dayRows
    override val weeks: StateFlow<List<PlayWeek>> = weekRows
    override val months: StateFlow<List<PlayMonth>> = monthRows
    override val stamps: StateFlow<List<Stamp>> = stampRows
    override val goals: StateFlow<List<Goal>> = goalRows
    override val goalDays: StateFlow<List<GoalDay>> = goalDayRows
    override val deposits: StateFlow<List<GoalDeposit>> = depositRows
    override val seenAccounts: StateFlow<List<SeenAccount>> = seenRows

    override suspend fun markCleared(day: LocalDate, atEpochMillis: Long, stamp: StampKind): Boolean {
        val row = dayRows.value.firstOrNull { it.date == day }
        if (row?.clearedAtEpochMillis != null) return false
        writeDay((row ?: PlayDay(day)).copy(clearedAtEpochMillis = atEpochMillis))
        earn(stamp, day.toString(), day)
        return true
    }

    override suspend fun answer(day: LocalDate, questionKey: String, right: Boolean, pickedFirst: Boolean, atEpochMillis: Long) {
        val row = dayRows.value.firstOrNull { it.date == day }
        if (row?.answeredAtEpochMillis != null) return
        writeDay((row ?: PlayDay(day)).copy(answeredAtEpochMillis = atEpochMillis, questionKey = questionKey, answeredRight = right, pickedFirst = pickedFirst))
    }

    override suspend fun fixPaisenGuess(monday: LocalDate, guess: Satang): Satang {
        val week = weekRows.value.firstOrNull { it.monday == monday }
        week?.paisenGuess?.let { return it }
        writeWeek((week ?: PlayWeek(monday)).copy(paisenGuess = guess))
        return guess
    }

    override suspend fun revealGuess(monday: LocalDate, guess: Satang, total: Satang, youCloser: Boolean, on: LocalDate) {
        val week = weekRows.value.firstOrNull { it.monday == monday } ?: PlayWeek(monday)
        if (week.guess != null) return
        writeWeek(week.copy(guess = guess, total = total))
        if (youCloser) earn(StampKind.BeatPaiSeN, monday.toString(), on)
    }

    override suspend fun reveal(stampId: String, atEpochMillis: Long) {
        stampRows.update { rows -> rows.map { if (it.id == stampId && it.revealedAtEpochMillis == null) it.copy(revealedAtEpochMillis = atEpochMillis) else it } }
    }

    override suspend fun earn(kind: StampKind, ref: String, on: LocalDate): Boolean {
        val id = stampId(kind, ref)
        if (stampRows.value.any { it.id == id }) return false
        stampRows.update { it + Stamp(id, kind, ref, on) }
        return true
    }

    override suspend fun pickChallenge(monday: LocalDate, challenge: Challenge) {
        writeWeek((weekRows.value.firstOrNull { it.monday == monday } ?: PlayWeek(monday)).copy(challenge = challenge))
    }

    override suspend fun seal(month: String, atEpochMillis: Long, on: LocalDate) {
        val row = monthRows.value.firstOrNull { it.month == month }
        if (row?.sealedAtEpochMillis != null) return
        writeMonth((row ?: PlayMonth(month)).copy(sealedAtEpochMillis = atEpochMillis))
        earn(StampKind.MonthClosed, month, on)
    }

    override suspend fun storySeen(month: String, atEpochMillis: Long) {
        val row = monthRows.value.firstOrNull { it.month == month }
        if (row?.storySeenAtEpochMillis != null) return
        writeMonth((row ?: PlayMonth(month)).copy(storySeenAtEpochMillis = atEpochMillis))
    }

    override suspend fun saveGoal(goal: Goal) {
        goalRows.update { rows -> rows.filterNot { it.id == goal.id } + goal }
    }

    override suspend fun archiveGoal(goalId: String, atEpochMillis: Long) {
        goalRows.update { rows -> rows.map { if (it.id == goalId && it.archivedAtEpochMillis == null) it.copy(archivedAtEpochMillis = atEpochMillis) else it } }
    }

    private fun writeDay(day: PlayDay) = dayRows.update { rows -> (rows.filterNot { it.date == day.date } + day).sortedBy { it.date } }

    private fun writeWeek(week: PlayWeek) = weekRows.update { rows -> (rows.filterNot { it.monday == week.monday } + week).sortedBy { it.monday } }

    private fun writeMonth(month: PlayMonth) = monthRows.update { rows -> (rows.filterNot { it.month == month.month } + month).sortedBy { it.month } }

    override suspend fun answerDay(day: GoalDay) {
        goalDayRows.update { rows -> if (rows.any { it.goalId == day.goalId && it.day == day.day }) rows else rows + day }
    }

    override suspend fun deposit(deposit: GoalDeposit): Boolean {
        if (depositRows.value.any { it.id == deposit.id }) return false
        depositRows.update { it + deposit }
        return true
    }

    override suspend fun removeSaidDeposit(id: String) {
        depositRows.update { rows -> rows.filterNot { it.id == id && it.source == DepositSource.Said } }
    }

    override suspend fun chooseAccount(goalId: String, account: String) {
        goalRows.update { rows -> rows.map { if (it.id == goalId) it.copy(savingsAccount = account) else it } }
    }

    override suspend fun seeAccount(mask: String, holder: String, atEpochMillis: Long) {
        seenRows.update { rows ->
            val seen = rows.firstOrNull { it.mask == mask }
            rows.filterNot { it.mask == mask } + SeenAccount(mask, holder, (seen?.count ?: 0) + 1, atEpochMillis)
        }
    }
}

package com.ptut.expense.domain.play

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.mondayOfWeekOf
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.usualDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun LedgerSnapshot.rows(): List<Transaction> = (this as? LedgerSnapshot.Rows)?.transactions.orEmpty()

private suspend fun ObserveTransactionsUseCase.rowsNow(): List<Transaction> =
    invoke(LedgerSpan.All).filterIsInstance<LedgerSnapshot.Rows>().first().transactions

/** #91 (p05): this week's choices by category, and the challenge already picked, if any. */
data class ChallengeChoices(val monday: LocalDate, val today: LocalDate, val options: List<Pair<String, Map<ChallengeLevel, Satang>>>, val picked: Challenge?, val progress: ChallengeProgress?)

class GetChallengeChoicesUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    operator fun invoke(): Flow<ChallengeChoices> = combine(book.weeks, transactions(LedgerSpan.All)) { weeks, ledger ->
        val today = homeToday()
        val monday = mondayOfWeekOf(today)
        val rows = ledger.rows()
        val picked = weeks.firstOrNull { it.monday == monday }?.challenge
        ChallengeChoices(
            monday = monday,
            today = today,
            options = rows.challengeCategories(monday).mapNotNull { id -> rows.challengeLevels(id, monday)?.let { id to it } },
            picked = picked,
            progress = picked?.let { rows.challengeProgress(it, monday, today) },
        )
    }.distinctUntilChanged()
}

/** #91: one a week; picking again the same week replaces it. False when the category has no level to offer. */
class PickChallengeUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    suspend operator fun invoke(categoryId: String, level: ChallengeLevel): Boolean {
        val monday = mondayOfWeekOf(homeToday())
        val limit = transactions.rowsNow().challengeLevels(categoryId, monday)?.get(level) ?: return false
        book.pickChallenge(monday, Challenge(categoryId, level, limit))
        return true
    }
}

/**
 * #91: a past week's challenge earns its stamp the first time the app opens after the week, if it was
 * met. Every week gone by is settled, so a week the app slept through still counts.
 */
class SettleChallengeUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
    private val playOn: ObservePlayOnUseCase,
) {
    suspend operator fun invoke() {
        if (!playOn.now(PlayPart.Challenge)) return
        val today = homeToday()
        val thisMonday = mondayOfWeekOf(today)
        val earned = book.stamps.first().filter { it.kind == StampKind.ChallengeMet }.map { it.ref }.toSet()
        val open = book.weeks.first().filter { it.monday < thisMonday && it.challenge != null && it.monday.toString() !in earned }
        if (open.isEmpty()) return
        val rows = transactions.rowsNow()
        open.forEach { week ->
            val challenge = week.challenge ?: return@forEach
            if (rows.challengeMet(challenge, week.monday)) book.earn(StampKind.ChallengeMet, week.monday.toString(), today)
        }
    }
}

/** #92 (p06): one jar, how full and when, and how skipping each of the biggest habits moves the date. */
data class GoalJarState(val goal: Goal, val pace: GoalPace, val whatIf: List<Pair<String, GoalPace>>)

class ObserveGoalJarUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    operator fun invoke(goalId: String?): Flow<GoalJarState?> = combine(book.goals, book.goalDays, transactions(LedgerSpan.All)) { goals, days, ledger ->
        val goal = goals.firstOrNull { it.id == goalId && it.archivedAtEpochMillis == null } ?: return@combine null
        val today = homeToday()
        val rows = ledger.rows()
        GoalJarState(
            goal,
            rows.goalPace(goal, days, today),
            rows.challengeCategories(mondayOfWeekOf(today)).map { it to rows.goalPaceWithout(goal, days, it, today) },
        )
    }

    /** The one jar Today offers: the newest that is not put away. */
    fun current(): Flow<Goal?> = book.goals.map { goals -> goals.lastOrNull { it.archivedAtEpochMillis == null } }
}

/**
 * #104: Count it or Not today on Today's card. Count it keeps today's usual as it stands; the jar reads
 * the day's spending again later. False when today is not under its usual, so nothing was counted.
 */
class AnswerGoalDayUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(goalId: String, count: Boolean): Boolean {
        val today = homeToday()
        val usual = if (count) {
            val day = transactions.rowsNow().usualDay(today) ?: return false
            if (day.spent >= day.usual) return false
            day.usual
        } else {
            null
        }
        book.answerDay(GoalDay(goalId, today, usual, clock.now().toEpochMilliseconds()))
        return count
    }
}

/** #92: a new jar starts today, in the home money; saving an existing one keeps its start. */
@OptIn(ExperimentalUuidApi::class)
class SaveGoalUseCase(
    private val book: PlayBook,
    private val homeToday: GetHomeTodayUseCase,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(goalId: String?, name: String, target: Satang): String {
        val existing = goalId?.let { id -> book.goals.first().firstOrNull { it.id == id } }
        val goal = existing?.copy(name = name.trim(), target = target) ?: Goal(
            id = Uuid.random().toString(),
            name = name.trim(),
            target = target,
            currency = Home.money.code,
            start = homeToday(),
            createdAtEpochMillis = clock.now().toEpochMilliseconds(),
        )
        book.saveGoal(goal)
        return goal.id
    }
}

class ArchiveGoalUseCase(private val book: PlayBook, private val clock: Clock = Clock.System) {
    suspend operator fun invoke(goalId: String) = book.archiveGoal(goalId, clock.now().toEpochMilliseconds())
}

/** #90 (p03): the month's cards, and whether the story was read through before. */
data class MonthStoryState(val month: String, val cards: List<StoryCard>, val seen: Boolean)

class GetMonthStoryUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    operator fun invoke(month: String): Flow<MonthStoryState> = combine(book.months, transactions(LedgerSpan.All)) { months, ledger ->
        MonthStoryState(month, ledger.rows().monthStory(month, today = homeToday()), months.any { it.month == month && it.storySeenAtEpochMillis != null })
    }.distinctUntilChanged()
}

class MarkStorySeenUseCase(private val book: PlayBook, private val clock: Clock = Clock.System) {
    suspend operator fun invoke(month: String) = book.storySeen(month, clock.now().toEpochMilliseconds())
}

/** #98 (p20): the month's checks, the slips still waiting, and whether it is sealed. */
data class MonthCloseState(val month: String, val close: MonthClose, val rows: List<Transaction>, val waitingSlips: Int, val sealed: Boolean)

class GetMonthCloseUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val queued: ObserveQueuedSlipsUseCase,
) {
    operator fun invoke(month: String): Flow<MonthCloseState> = combine(book.months, transactions(LedgerSpan.All), queued()) { months, ledger, slips ->
        val rows = ledger.rows()
        MonthCloseState(month, rows.monthClose(month), rows, slips.waitingIn(month), months.any { it.month == month && it.sealedAtEpochMillis != null })
    }
}

/** #98: sealing keeps every row editable; it marks the month and earns Month closed once. */
class SealMonthUseCase(private val book: PlayBook, private val homeToday: GetHomeTodayUseCase, private val clock: Clock = Clock.System) {
    suspend operator fun invoke(month: String) = book.seal(month, clock.now().toEpochMilliseconds(), homeToday())
}

/** #99 (p21) */
class GetMoneyScoreUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    operator fun invoke(): Flow<MoneyScore> = combine(book.days, transactions(LedgerSpan.All)) { days, ledger -> moneyScore(ledger.rows(), days, homeToday()) }
        .distinctUntilChanged()
}

/** FR-98.3: the months sealed, ISO `yyyy-MM`, for History to mark; none while closing the month is off. */
class ObserveSealedMonthsUseCase(private val book: PlayBook, private val playOn: ObservePlayOnUseCase) {
    operator fun invoke(): Flow<Set<String>> = combine(playOn(PlayPart.CloseMonth), book.months) { on, months ->
        if (on) months.filter { it.sealedAtEpochMillis != null }.map { it.month }.toSet() else emptySet()
    }
}

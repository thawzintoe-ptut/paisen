package com.ptut.expense.domain.play

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeTime
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
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

/**
 * J02: one jar, what went in and when it fills at this pace. [leftover] is how far today sits under a
 * usual day, the first amount Put money in offers; [thisWeek] what went in since Monday (J05).
 */
data class GoalJarState(val goal: Goal, val pace: GoalPace, val deposits: List<GoalDeposit>, val leftover: Satang?, val thisWeek: Satang)

class ObserveGoalJarUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
    private val clock: Clock = Clock.System,
) {
    operator fun invoke(goalId: String?): Flow<GoalJarState?> = combine(book.goals, book.deposits, transactions(LedgerSpan.All)) { goals, deposits, ledger ->
        val goal = goals.firstOrNull { it.id == goalId && it.archivedAtEpochMillis == null } ?: return@combine null
        val today = homeToday()
        GoalJarState(
            goal = goal,
            pace = deposits.goalPace(goal, today),
            deposits = deposits.filter { it.goalId == goal.id }.sortedWith(compareByDescending<GoalDeposit> { it.day }.thenByDescending { it.atEpochMillis }),
            leftover = ledger.rows().leftoverToday(clock.now().toLocalDateTime(HomeTime)),
            thisWeek = deposits.savedThisWeek(goal, today),
        )
    }

    /** The one jar Today offers: the newest that is not put away. */
    fun current(): Flow<Goal?> = book.goals.map { goals -> goals.lastOrNull { it.archivedAtEpochMillis == null } }
}

/** The day's spending is mostly in by six in the evening; before then a leftover is only a morning with no buys yet. */
const val LEFTOVER_FROM_HOUR = 18

/**
 * J01: what today kept under a usual day of its weekday, from [LEFTOVER_FROM_HOUR] on. Null earlier, when
 * the day is not under, or no usual exists yet: real money moves, so a morning never offers the whole day.
 */
fun List<Transaction>.leftoverToday(now: LocalDateTime): Satang? {
    if (now.hour < LEFTOVER_FROM_HOUR) return null
    return usualDay(now.date)?.let { if (it.spent < it.usual) it.usual - it.spent else null }
}

/** J03: the reader moved money themself (or keeps cash); the jar takes their word once per tap. */
@OptIn(ExperimentalUuidApi::class)
class PutMoneyInUseCase(
    private val book: PlayBook,
    private val homeToday: GetHomeTodayUseCase,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(goal: Goal, amount: Satang) {
        if (amount <= Satang.Zero) return
        book.deposit(
            GoalDeposit(
                id = "said:" + Uuid.random().toString(),
                goalId = goal.id,
                amount = amount,
                day = homeToday(),
                atEpochMillis = clock.now().toEpochMilliseconds(),
                source = DepositSource.Said,
                account = goal.savingsAccount?.takeIf { it != CASH_ACCOUNT },
            ),
        )
    }
}

/** J02: a deposit tapped in by mistake comes back out; money a slip proved moved stays. */
class RemoveSaidDepositUseCase(private val book: PlayBook) {
    suspend operator fun invoke(depositId: String) = book.removeSaidDeposit(depositId)
}

/** J02's Undo: the same deposit, id and day, goes back in. */
class PutBackDepositUseCase(private val book: PlayBook) {
    suspend operator fun invoke(deposit: GoalDeposit) = book.deposit(deposit)
}

/** J04: where the jar's money goes, asked once; [CASH_ACCOUNT] for a reader who keeps cash. */
class ChooseSavingsAccountUseCase(private val book: PlayBook) {
    suspend operator fun invoke(goalId: String, account: String) = book.chooseAccount(goalId, account)
}

/** J04: the reader's own accounts their slips moved money to, most used first. */
class ObserveOwnAccountsUseCase(private val book: PlayBook) {
    operator fun invoke(): Flow<List<SeenAccount>> = book.seenAccounts
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

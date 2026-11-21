package com.ptut.expense.domain.play

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.isoMonth
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.mondayOfWeekOf
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** What Today shows of play, each part already gated: a null part draws nothing. */
data class TodayPlay(
    /** #85: the run, or null with the run switched off. */
    val run: PlayRun? = null,
    /** #88: today's face-down stamp, or null. */
    val stampWaiting: String? = null,
    /** #87: true while today's question waits for its answer. */
    val question: Boolean = false,
    /** #89: the one line that stands out today, or null. */
    val coach: CoachLine? = null,
    /** #86: last week's Monday while its guess waits; the weekly card opens the guess first. */
    val guessWeek: LocalDate? = null,
    /** #91: this week's challenge and how it stands, or null with none picked. */
    val challenge: Challenge? = null,
    val challengeProgress: ChallengeProgress? = null,
    /** The shelf of ways in, each null or false while its part is off or has nothing to show. */
    val shelf: PlayShelf = PlayShelf(),
) {
    companion object {
        val Off = TodayPlay()
    }
}

/**
 * Today's ways into the extras. [storyMonth] is last month during the first week of a month, until its
 * story was read; [closeMonth] the month to seal, from its last three days to a week into the next.
 */
data class PlayShelf(
    val challenge: Boolean = false,
    val jar: Boolean = false,
    val goalId: String? = null,
    val storyMonth: String? = null,
    val closeMonth: String? = null,
    val score: Boolean = false,
) {
    val isEmpty: Boolean get() = !challenge && !jar && storyMonth == null && closeMonth == null && !score
}

/**
 * Today's play, folded from the stored facts and the ledger on every change, off the main thread.
 * It folds again at each midnight in the home zone, so a screen left open does not show yesterday.
 */
class ObserveTodayPlayUseCase(
    private val playOn: ObservePlayOnUseCase,
    private val book: PlayBook,
    private val homeToday: GetHomeTodayUseCase,
    private val transactions: ObserveTransactionsUseCase? = null,
    private val clock: Clock = Clock.System,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    operator fun invoke(): Flow<TodayPlay> = combine(
        combine(playOn(PlayPart.Run), playOn(PlayPart.Stamps), playOn(PlayPart.Question), playOn(PlayPart.Coach), playOn(PlayPart.Guess), ::Parts),
        combine(playOn(PlayPart.Challenge), playOn(PlayPart.GoalJar), playOn(PlayPart.Story), playOn(PlayPart.CloseMonth), playOn(PlayPart.Score), ::Extras),
        combine(book.days, book.stamps, book.weeks, book.months, book.goals, ::Facts),
        transactions?.invoke(LedgerSpan.All) ?: flowOf(LedgerSnapshot.Rows(emptyList())),
        homeDays(clock),
    ) { parts, extras, facts, ledger, _ ->
        val (days, stamps, weeks, months, goals) = facts
        val today = homeToday()
        val rows = (ledger as? LedgerSnapshot.Rows)?.transactions.orEmpty()
        val thisMonday = mondayOfWeekOf(today)
        val lastMonday = thisMonday.minus(1, DateTimeUnit.WEEK)
        val challenge = weeks.firstOrNull { it.monday == thisMonday }?.challenge.takeIf { extras.challenge }
        val answered = days.any { it.date == today && it.answeredAtEpochMillis != null }
        TodayPlay(
            run = days.playRun(today).takeIf { parts.run },
            stampWaiting = stamps.waitingOn(today)?.id.takeIf { parts.stamps },
            question = parts.question && !answered && rows.pickQuestion(today, days.askedKeys(today)) != null,
            // Not takeIf: the coach pass walks every row per category, so it must not run while off.
            coach = if (parts.coach) rows.coachLine(today) else null,
            guessWeek = lastMonday.takeIf {
                parts.guess && weeks.none { week -> week.monday == lastMonday && week.guess != null } && rows.paisenGuess(lastMonday) != null
            },
            challenge = challenge,
            challengeProgress = challenge?.let { rows.challengeProgress(it, thisMonday, today) },
            shelf = PlayShelf(
                challenge = extras.challenge,
                jar = extras.jar,
                goalId = goals.lastOrNull { it.archivedAtEpochMillis == null }?.id.takeIf { extras.jar },
                storyMonth = storyMonth(today, months).takeIf { extras.story && it != null && rows.monthStory(it).isNotEmpty() },
                closeMonth = closeMonth(today, months).takeIf { extras.close },
                score = extras.score,
            ),
        )
    }.distinctUntilChanged().flowOn(work)

    private data class Parts(val run: Boolean, val stamps: Boolean, val question: Boolean, val coach: Boolean, val guess: Boolean)

    private data class Extras(val challenge: Boolean, val jar: Boolean, val story: Boolean, val close: Boolean, val score: Boolean)

    private data class Facts(val days: List<PlayDay>, val stamps: List<Stamp>, val weeks: List<PlayWeek>, val months: List<PlayMonth>, val goals: List<Goal>)
}

/** Today in the home zone, and again at each midnight, for folds that otherwise only follow the data. */
internal fun homeDays(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.now()
        val today = now.toLocalDateTime(HomeTime).date
        emit(today)
        delay(today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(HomeTime) - now)
    }
}

/**
 * The stamp Today offers face down: the newest earned today and not yet turned. A win stamp's ref
 * names its week or group, so the day it was earned is what ties it to today.
 */
internal fun List<Stamp>.waitingOn(today: LocalDate): Stamp? = lastOrNull { it.earnedOn == today && it.revealedAtEpochMillis == null }

/** The month a story is offered for: last month, in the first week of this one, until it was read. */
internal fun storyMonth(today: LocalDate, months: List<PlayMonth>): String? {
    if (today.day > STORY_DAYS) return null
    val last = today.minus(1, DateTimeUnit.MONTH).isoMonth()
    return last.takeIf { months.none { it.month == last && it.storySeenAtEpochMillis != null } }
}

/** The month to close: this one from its last three days, or last month for a week after, until sealed. */
internal fun closeMonth(today: LocalDate, months: List<PlayMonth>): String? {
    val lastDay = today.plus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }.minus(1, DateTimeUnit.DAY)
    val month = when {
        today.day > lastDay.day - CLOSE_EARLY_DAYS -> today.isoMonth()
        today.day <= STORY_DAYS -> today.minus(1, DateTimeUnit.MONTH).isoMonth()
        else -> return null
    }
    return month.takeIf { months.none { it.month == month && it.sealedAtEpochMillis != null } }
}

/** The first week of a month offers last month's story and close. */
private const val STORY_DAYS = 7

/** The last three days of a month offer its close. */
private const val CLOSE_EARLY_DAYS = 3

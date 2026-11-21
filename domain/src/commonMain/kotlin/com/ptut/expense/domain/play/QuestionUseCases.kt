package com.ptut.expense.domain.play

import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/**
 * #87: today's question as it stands. [answeredRight] is null until it is answered; once it is, [pair]
 * is the pair that was asked, rebuilt from its key, so the answered screen shows the same two cards.
 */
data class DailyQuestion(
    /** The day the question belongs to, so an answer just after midnight still lands on it. */
    val day: LocalDate,
    val pair: QuestionPair?,
    val answeredRight: Boolean?,
    val pickedFirst: Boolean?,
    val freezes: Int,
    val freezeProgress: Int,
    /** FR-87.4: "Open today's stamp" shows only when the tray was cleared today too. */
    val stampWaiting: String?,
)

class GetDailyQuestionUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    operator fun invoke(): Flow<DailyQuestion> = combine(book.days, book.stamps, transactions(LedgerSpan.All)) { days, stamps, ledger ->
        val today = homeToday()
        val rows = (ledger as? LedgerSnapshot.Rows)?.transactions.orEmpty()
        val answered = days.firstOrNull { it.date == today }?.takeIf { it.answeredAtEpochMillis != null }
        val run = days.playRun(today)
        DailyQuestion(
            day = today,
            pair = answered?.questionKey?.let { rows.questionFor(it, today) } ?: rows.pickQuestion(today, days.askedKeys(today)).takeIf { answered == null },
            answeredRight = answered?.answeredRight,
            pickedFirst = answered?.pickedFirst,
            freezes = run.freezes,
            freezeProgress = run.freezeProgress,
            stampWaiting = stamps.waitingOn(today)?.id,
        )
    }
}

/** #87: the first answer of [day] is kept; it counts toward a freeze whether right or not (D3). */
class AnswerDailyQuestionUseCase(
    private val book: PlayBook,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(day: LocalDate, key: String, right: Boolean, pickedFirst: Boolean) = book.answer(day, key, right, pickedFirst, clock.now().toEpochMilliseconds())
}

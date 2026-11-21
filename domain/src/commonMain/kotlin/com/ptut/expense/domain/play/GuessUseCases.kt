package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** #86: PaiSeN's guess, fixed before the reader's; the slider; the record; and the week if it was revealed before. */
data class GuessStart(
    val paisen: PaisenGuess,
    val range: GuessRange,
    val record: GuessRecord,
    /** Set once this week was guessed: the screen opens on the reveal. */
    val revealed: GuessResult?,
)

/**
 * #86: PaiSeN's guess is stored before the slider shows, so nothing the reader does can move it.
 * Null with fewer than two earlier weeks.
 */
class StartGuessUseCase(private val book: PlayBook, private val transactions: ObserveTransactionsUseCase) {
    suspend operator fun invoke(monday: LocalDate): GuessStart? {
        val rows = transactions(LedgerSpan.All).filterIsInstance<LedgerSnapshot.Rows>().first().transactions
        val guess = rows.paisenGuess(monday) ?: return null
        val fixed = book.fixPaisenGuess(monday, guess.amount)
        val weeks = book.weeks.first()
        val week = weeks.firstOrNull { it.monday == monday }
        val revealed = week?.let { played ->
            val you = played.guess ?: return@let null
            val total = played.total ?: return@let null
            guessResult(total, you, fixed)
        }
        return GuessStart(guess.copy(amount = fixed), guess.copy(amount = fixed).range(), weeks.guessRecord(), revealed)
    }
}

/** #86: the reader's guess against the week's spend, kept once; a closer guess earns Beat PaiSeN. */
class RevealGuessUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    suspend operator fun invoke(monday: LocalDate, guess: Satang): GuessResult {
        val rows = transactions(LedgerSpan.All).filterIsInstance<LedgerSnapshot.Rows>().first().transactions
        val paisen = book.fixPaisenGuess(monday, rows.paisenGuess(monday)?.amount ?: Satang.Zero)
        val result = guessResult(rows.weekSpend(monday), guess, paisen)
        book.revealGuess(monday, guess, result.total, result.youCloser, homeToday())
        // The first reveal stands: a second one shows what was kept, so it never says "you won" with no stamp.
        val kept = book.weeks.first().firstOrNull { it.monday == monday }
        val keptGuess = kept?.guess ?: return result
        val keptTotal = kept.total ?: return result
        return guessResult(keptTotal, keptGuess, paisen)
    }
}

package com.ptut.expense.domain.play

import com.ptut.expense.domain.isoMonth
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** #88: one stamp and how far its set has come, or null once the stamp is gone. */
data class StampReveal(val stamp: Stamp, val set: SetProgress)

class ObserveStampUseCase(private val book: PlayBook) {
    operator fun invoke(stampId: String): Flow<StampReveal?> = book.stamps.map { stamps ->
        stamps.firstOrNull { it.id == stampId }?.let { stamp -> StampReveal(stamp, stamps.setProgress().first { it.set == stamp.kind.set }) }
    }
}

/** #88: the card turned over. Only the first turn is kept. */
class RevealStampUseCase(private val book: PlayBook, private val clock: Clock = Clock.System) {
    suspend operator fun invoke(stampId: String) = book.reveal(stampId, clock.now().toEpochMilliseconds())
}

/** p04: this month's no-spend days, each set's progress, and every stamp earned. */
data class StampBook(
    val today: LocalDate,
    /** ISO `yyyy-MM` of [today]. */
    val month: String,
    val noSpendDays: List<LocalDate>,
    val sets: List<SetProgress>,
    val stamps: List<Stamp>,
)

class ObserveStampBookUseCase(
    private val book: PlayBook,
    private val transactions: ObserveTransactionsUseCase,
    private val homeToday: GetHomeTodayUseCase,
) {
    operator fun invoke(): Flow<StampBook> = combine(book.stamps, transactions(LedgerSpan.All)) { stamps, ledger ->
        val today = homeToday()
        val month = today.isoMonth()
        val rows = (ledger as? LedgerSnapshot.Rows)?.transactions.orEmpty()
        StampBook(today, month, rows.noSpendDays(month, today), stamps.setProgress(), stamps)
    }
}

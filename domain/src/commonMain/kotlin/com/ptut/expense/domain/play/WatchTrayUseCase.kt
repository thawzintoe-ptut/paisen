package com.ptut.expense.domain.play

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.today.ObserveScanningUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Long enough for the importer to start the scan a launch begins with. */
private val TRAY_SETTLE = 3.seconds

/**
 * #85 (D2): today counts the first time the app sees an empty tray, with no scan running. Slips a
 * discard is holding count as gone, so an Undo that brings one back never reopens the day.
 *
 * Runs only while the app is on screen (no background job), and is a single so one watcher writes;
 * the lock keeps a resume that overlaps the last one from earning twice.
 */
class WatchTrayUseCase(
    private val queued: ObserveQueuedSlipsUseCase,
    private val scanning: ObserveScanningUseCase,
    private val playOn: ObservePlayOnUseCase,
    private val transactions: ObserveTransactionsUseCase,
    private val book: PlayBook,
    private val clock: Clock = Clock.System,
    /**
     * How long the tray must stay empty with no scan before it counts. On a cold start the importer
     * has not begun yet; a scan that starts inside this window cancels the mark until it ends.
     */
    private val settle: Duration = TRAY_SETTLE,
) {
    private val lock = Mutex()

    suspend operator fun invoke() {
        combine(queued(), scanning(), playOn(PlayPart.Run), days()) { slips, scan, on, day ->
            day.takeIf { slips.isEmpty() && !scan && on }
        }
            .distinctUntilChanged()
            .collectLatest { day ->
                if (day == null) return@collectLatest
                delay(settle)
                mark(day)
            }
    }

    private suspend fun mark(day: LocalDate) = lock.withLock {
        val now = clock.now()
        val at = now.toLocalDateTime(HomeTime)
        // The day turned while the tray was being read: the next day's emission marks that one.
        if (at.date != day) return@withLock
        // Most resumes find the day already counted; that costs one small read, not a walk of the ledger.
        if (book.days.first().any { it.date == day && it.clearedAtEpochMillis != null }) return@withLock
        val rows = transactions(LedgerSpan.All).filterIsInstance<LedgerSnapshot.Rows>().first().transactions
        book.markCleared(day, now.toEpochMilliseconds(), stampFor(at, rows.slipsFiledOn(day)))
    }

    /** Today, and again each midnight in the home zone, so a tray left clear overnight counts the new day. */
    private fun days(): Flow<LocalDate> = homeDays(clock)
}

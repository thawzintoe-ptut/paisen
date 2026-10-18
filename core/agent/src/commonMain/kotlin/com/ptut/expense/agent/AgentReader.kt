package com.ptut.expense.agent

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.rangeContaining
import com.ptut.expense.domain.startOfDayEpochMillis
import com.ptut.expense.domain.sum
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * #205's read tools. They run at once and change nothing, so Siri's "How much did I spend" answers
 * without opening the app (#206).
 *
 * Reads the recent rows, which reach back to the start of last month: enough for a month to date.
 */
class AgentReader(
    private val ledger: LedgerRepository,
    private val clock: Clock = Clock.System,
) {

    /** spend_summary: money out since the window began, on Bangkok's calendar (FR-19). */
    suspend fun spent(window: SpendWindow): Satang =
        rowsIn(window).filter { it.direction == Direction.Expense }.map { it.amount }.sum()

    /** find_transactions: newest first, matched on the row's name and its category. */
    suspend fun find(call: ReadCall.FindTransactions): List<Transaction> {
        val rows = call.window?.let { rowsIn(it) } ?: rows()
        return rows
            .filter { call.categoryId == null || it.categoryId == call.categoryId }
            .filter { row ->
                val text = call.text?.takeIf { it.isNotBlank() } ?: return@filter true
                listOfNotNull(row.memo, row.counterparty).any { it.contains(text, ignoreCase = true) }
            }
            .sortedByDescending { it.occurredAtEpochMillis }
    }

    private suspend fun rowsIn(window: SpendWindow): List<Transaction> {
        val since = startOf(window)
        return rows().filter { it.occurredAtEpochMillis >= since }
    }

    /** Waits for the first answer: a cold start from Siri can ask before the table has. */
    private suspend fun rows(): List<Transaction> =
        (ledger.recentTransactions.first { it is LedgerSnapshot.Rows } as LedgerSnapshot.Rows).transactions

    private fun startOf(window: SpendWindow): Long {
        val today = clock.now().toLocalDateTime(BangkokTime).date
        val start = when (window) {
            SpendWindow.Today -> today
            SpendWindow.Week -> ReportPeriod.Week.rangeContaining(today).start
            SpendWindow.Month -> ReportPeriod.Month.rangeContaining(today).start
        }
        return start.startOfDayEpochMillis()
    }
}

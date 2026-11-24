package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.ImportRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which rows a screen reads: today's, the recent window, or the whole ledger. */
enum class LedgerSpan { Today, Recent, All }

/** The ledger's rows as they change, unanswered until Room has answered. */
class ObserveTransactionsUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(span: LedgerSpan): StateFlow<LedgerSnapshot> = when (span) {
        LedgerSpan.Today -> ledger.todaysTransactions
        LedgerSpan.Recent -> ledger.recentTransactions
        LedgerSpan.All -> ledger.allTransactions
    }

    /**
     * One row, held as a snapshot of at most that row. Seeded from the whole ledger's current
     * answer, so a tap before the one-row query answers still finds the row.
     */
    fun one(transactionId: String, scope: CoroutineScope): StateFlow<LedgerSnapshot> =
        ledger.observeTransaction(transactionId)
            .map { row -> LedgerSnapshot.Rows(listOfNotNull(row)) }
            .stateIn(scope, SharingStarted.Eagerly, ledger.allTransactions.value.only(transactionId))

    private fun LedgerSnapshot.only(transactionId: String): LedgerSnapshot = when (this) {
        LedgerSnapshot.Unanswered -> this
        is LedgerSnapshot.Rows -> LedgerSnapshot.Rows(listOfNotNull(rowWithId(transactionId)))
    }
}

/** FR-5: the import journal, newest first. */
class ObserveImportRunsUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(): StateFlow<List<ImportRun>> = ledger.importRuns
}

/** When an import last succeeded, or null when none ever has. */
class ObserveLastImportUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(): StateFlow<Long?> = ledger.sinceLastImportMillis
}

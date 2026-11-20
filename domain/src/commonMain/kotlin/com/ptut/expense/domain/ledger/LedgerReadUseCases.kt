package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.ImportRun
import kotlinx.coroutines.flow.StateFlow

/** Which rows a screen reads: today's, the recent window, or the whole ledger. */
enum class LedgerSpan { Today, Recent, All }

/** The ledger's rows as they change, unanswered until Room has answered. */
class ObserveTransactionsUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(span: LedgerSpan): StateFlow<LedgerSnapshot> = when (span) {
        LedgerSpan.Today -> ledger.todaysTransactions
        LedgerSpan.Recent -> ledger.recentTransactions
        LedgerSpan.All -> ledger.allTransactions
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

package com.ptut.expense.domain.backup

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** What an export would hold: how many rows, and the home day of the oldest, null with none. */
data class LedgerHolding(val transactions: Int, val oldest: LocalDate?)

/** FR-22: the ledger counted rather than promised, or null while it has not answered. */
class ObserveLedgerHoldingUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(): Flow<LedgerHolding?> = ledger.allTransactions.map { snapshot ->
        when (snapshot) {
            LedgerSnapshot.Unanswered -> null

            is LedgerSnapshot.Rows -> LedgerHolding(
                transactions = snapshot.transactions.size,
                oldest = snapshot.transactions.minOfOrNull { it.occurredAtEpochMillis }?.dateIn(HomeTime),
            )
        }
    }
}

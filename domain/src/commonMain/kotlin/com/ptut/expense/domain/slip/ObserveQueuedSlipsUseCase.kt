package com.ptut.expense.domain.slip

import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ledger.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** #219: the slips waiting for review, less the ones a discard is holding until its Undo leaves the screen. */
class ObserveQueuedSlipsUseCase(
    private val ledger: LedgerRepository,
    private val pendingDiscards: Flow<Set<String>>,
) {
    operator fun invoke(): Flow<List<QueuedSlip>> =
        combine(ledger.queuedSlips, pendingDiscards) { slips, pending -> slips.filterNot { it.id in pending } }
}

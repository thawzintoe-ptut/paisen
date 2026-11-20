package com.ptut.expense.domain.settings

import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ledger.LedgerRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * NFR-4: every slip still keeping its picture, a discard waiting on its Undo included: the picture
 * stays until the sweep, so Keep failed slips counts it.
 */
class ObserveKeptSlipsUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(): StateFlow<List<QueuedSlip>> = ledger.queuedSlips
}

/**
 * NFR-4: how long a slip the reader has not confirmed keeps its picture. Only a length
 * [RetentionChoices] offers is kept; any other is ignored. A shorter one takes effect on the next sweep.
 */
class SetSlipRetentionUseCase(private val settings: AppSettings) {
    operator fun invoke(days: Int) {
        RetentionChoices.firstOrNull { it.inWholeDays.toInt() == days }?.let(settings::setRetention)
    }
}

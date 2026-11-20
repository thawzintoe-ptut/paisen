package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SlipDebt
import com.ptut.expense.domain.shared.debtsPaidBy
import com.ptut.expense.importer.SlipDiscards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One queued slip as the queue holds it now, without waiting: null before Room has answered, or once it has gone. */
class GetQueuedSlipUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(slipId: String): QueuedSlip? = ledger.queuedSlips.value.firstOrNull { it.id == slipId }
}

/**
 * #217: one queued slip as the queue changes, or null while it is not there, so a slip discarded
 * on another screen says it has gone. A slip whose discard waits on its Undo is still there.
 */
class ObserveSlipInQueueUseCase(private val ledger: LedgerRepository) {
    operator fun invoke(slipId: String): Flow<QueuedSlip?> =
        ledger.queuedSlips.map { queue -> queue.firstOrNull { it.id == slipId } }.distinctUntilChanged()
}

/**
 * "A slip closes the debt": the group debts a home-money slip going out could have paid. [enabled] is the switch it
 * sits behind until it has been tried with real groups.
 */
class FindSlipDebtsUseCase(
    private val enabled: Boolean = false,
    /** The debts of exactly this amount, the slip's payee only ordering them. */
    private val owed: (Satang, String?) -> List<SlipDebt> = { _, _ -> emptyList() },
) {
    /** Over the groups on this phone; none in a build without them. */
    constructor(enabled: Boolean, shared: SharedRepository?) :
        this(enabled, { amount, payee -> shared?.snapshot?.value?.debtsPaidBy(amount, payee).orEmpty() })

    operator fun invoke(slip: QueuedSlip): List<SlipDebt> {
        if (!enabled) return emptyList()
        val amount = slip.amount?.takeIf { slip.direction == Direction.Expense && slip.currency == Home.money.code } ?: return emptyList()
        return owed(amount, slip.merchant)
    }
}

/**
 * #219: Discard and Not mine. The slip leaves the screen at once and the toast offers Undo; the row
 * and its picture go only once that Undo has left the screen.
 */
class DiscardSlipWithUndoUseCase(private val discards: SlipDiscards) {
    suspend operator fun invoke(slipId: String) = discards.discard(slipId)
}

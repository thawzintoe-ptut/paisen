package com.ptut.expense.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.EvidenceRetention
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.evidenceDaysRemaining
import com.ptut.expense.domain.isAutomatic
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.metaLine
import com.ptut.expense.domain.slip.shownShop
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.DisplayAmount
import com.ptut.expense.ui.Haptics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * The review queue's state holder. It turns queued slips into cards and owns the one action the
 * screen offers on its own: discarding, which waits out an Undo before it runs (#219).
 *
 * FR-7 is the whole point of the screen, so the amount crosses as a [DisplayAmount]: nothing here
 * can hand a composable a null and let it decide what a missing number looks like.
 */
class ReviewViewModel(
    ledger: LedgerRepository,
    private val discards: SlipDiscards,
    /**
     * NFR-4's window as the reader set it. Read when the screen is built rather than observed: a
     * note that changed the deadline under the reader's thumb would be harder to trust, not easier.
     */
    private val retention: Duration = EvidenceRetention,
    private val clock: Clock = Clock.System,
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
) : ViewModel() {

    val uiState: StateFlow<ReviewUiState> = combine(
        ledger.queuedSlips,
        ledger.recentTransactions,
        discards.pending,
    ) { slips, snapshot, pending -> slips.filterNot { it.id in pending }.toUiState(snapshot.filedRows()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = emptyList<QueuedSlip>().toUiState(emptyList()),
        )

    /**
     * FR-8. The card leaves at once and comes back on Undo. The row and its picture go when the
     * Undo window closes, and [SlipDiscards] does that, not this class.
     */
    fun discard(id: String) {
        viewModelScope.launch {
            discards.discard(id)
            haptics.deleted()
        }
    }

    /**
     * This screen has no loading state and does not need one: the ledger feeds a single count,
     * and a queue card is worth drawing before that number arrives. Nothing is claimed while the
     * query is out — zero filed is what an unanswered ledger honestly supports, and it corrects
     * itself on the first emission.
     */
    private fun LedgerSnapshot.filedRows(): List<Transaction> = when (this) {
        LedgerSnapshot.Unanswered -> emptyList()
        is LedgerSnapshot.Rows -> transactions
    }

    private fun List<QueuedSlip>.toUiState(transactions: List<Transaction>) = ReviewUiState(
        slips = map { slip ->
            QueuedSlipUi(
                id = slip.id,
                // p12 names a slip with no shop read this way.
                merchant = slip.merchant.takeUnless { it.isBlank() }?.let { shownShop(it, AppText.copy.slips.unknownShop) }
                    ?: AppText.copy.slips.unknownShop,
                meta = slip.metaLine(),
                amount = DisplayAmount.of(slip.amount),
                reason = slip.reason,
            )
        },
        retentionNote = retentionNote(),
        filedAutomatically = transactions.count { it.source.isAutomatic },
    )

    /**
     * NFR-4, said with the number that applies to this queue rather than the number in the spec.
     *
     * The soonest to expire is the one worth naming: it is the deadline the reader actually has,
     * and quoting the newest slip's would promise time the oldest does not have.
     */
    private fun List<QueuedSlip>.retentionNote(): String {
        val soonest = minOfOrNull {
            evidenceDaysRemaining(it.capturedAtEpochMillis, clock.now().toEpochMilliseconds(), retention)
        }
        return AppText.copy.slips.retentionNote(retention.inWholeDays, soonest)
    }

    private companion object {
        /** Survives a rotation without keeping the query open behind a screen nobody is on. */
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

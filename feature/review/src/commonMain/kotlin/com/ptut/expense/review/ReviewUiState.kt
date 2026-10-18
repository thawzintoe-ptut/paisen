package com.ptut.expense.review

import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.ui.DisplayAmount

/**
 * One queue card, ready to draw. It carries no [com.ptut.expense.domain.QueuedSlip]: the screen's
 * job is to render, and a domain object passed straight through means the next change to the
 * ledger's shape reaches into a composable.
 */
data class QueuedSlipUi(
    val id: String,
    val merchant: String,
    val meta: String,
    val amount: DisplayAmount,
    val reason: ReviewReason,
)

/**
 * [retentionNote] is a whole sentence rather than a day count, so the promise the screen makes
 * about someone's evidence is written where a test can read it.
 */
data class ReviewUiState(
    val slips: List<QueuedSlipUi>,
    val retentionNote: String,
    /**
     * FR-20: an empty queue must prove the importer is alive rather than just look bare, so the
     * empty state names how many slips it filed without asking.
     */
    val filedAutomatically: Int,
)

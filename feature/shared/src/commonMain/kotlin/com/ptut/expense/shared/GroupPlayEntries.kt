package com.ptut.expense.shared

import com.ptut.expense.domain.shared.ReactionKind

/**
 * #94–#97: play's ways in from a group's menu. Each is null while its part is off, so the menu is
 * as it was without play.
 */
data class GroupPlayEntries(
    val onWheel: (() -> Unit)? = null,
    val onAwards: (() -> Unit)? = null,
    val onRecap: (() -> Unit)? = null,
    /** #97: null where a trip end does not apply; true once a member marked the trip ended. */
    val tripEnded: Boolean? = null,
    val onTripEnded: (Boolean) -> Unit = {},
    /** #95 */
    val allSquare: AllSquareUiModel? = null,
    val reactions: Map<String, PaymentReactionsUiModel> = emptyMap(),
    val onReact: (paymentId: String, kind: ReactionKind?) -> Unit = { _, _ -> },
)

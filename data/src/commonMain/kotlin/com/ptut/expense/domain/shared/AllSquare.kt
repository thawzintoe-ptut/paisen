package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/**
 * #95: the payment that brought every net in the group to zero, or null while anyone owes, or when
 * nothing was ever shared: payments that only cancel each other settle no expense, so earn nothing.
 * Its id names the stamp, so every phone earns the same one once.
 */
fun SharedSnapshot.allSquareAt(groupId: String): String? {
    if (currentExpenses(groupId).isEmpty()) return null
    if (nets(groupId).values.any { it != Satang.Zero }) return null
    return paymentsIn(groupId).maxWithOrNull(compareBy<SharedPayment>({ it.createdAtEpochMillis }, { it.occurredAtEpochMillis }, { it.id }))?.id
}

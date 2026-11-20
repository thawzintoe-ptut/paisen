package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.runSuspendCatching
import kotlin.time.Clock

/** D8: a removal adds a log entry; the expense's own transaction, if any, stays in History. */
class RemoveSharedExpenseUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(expenseId: String) = repository.removeExpense(expenseId)
}

/** Undo of a removal or a new expense's save: the expense comes back as it was. */
class RestoreSharedExpenseUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(expenseId: String) = repository.restoreExpense(expenseId)
}

/** What Record payment hands over. Null [occurredAtEpochMillis] records it at the moment of saving. */
data class PaymentInput(
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Satang,
    val method: PaymentMethod,
    val occurredAtEpochMillis: Long?,
)

/** #135: a payment between two members. The new payment's id, or the failure that kept it from being written. */
class RecordPaymentUseCase(
    private val repository: SharedRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(input: PaymentInput): Result<String> = runSuspendCatching {
        repository.recordPayment(
            PaymentDraft(
                groupId = input.groupId,
                fromMemberId = input.fromMemberId,
                toMemberId = input.toMemberId,
                amount = input.amount,
                method = input.method,
                occurredAtEpochMillis = input.occurredAtEpochMillis ?: clock.now().toEpochMilliseconds(),
            ),
        )
    }
}

/** #218: a recorded payment comes out of the balances. */
class RemovePaymentUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(paymentId: String) = repository.removePayment(paymentId)
}

/** #218: Undo of a removed payment, or of a new one's record. */
class PutBackPaymentUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(paymentId: String) = repository.putBackPayment(paymentId)
}

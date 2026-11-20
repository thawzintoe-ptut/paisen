package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.SlipDebt
import com.ptut.expense.importer.SlipImporter
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What Check slip hands over: the slip, the reader's choices, and the amount already read into numbers. */
data class SlipConfirmation(
    val slip: QueuedSlip,
    val categoryId: String,
    val subCategoryId: String?,
    /** The home amount the ledger counts. */
    val amount: Satang,
    /** #33: a foreign slip's original amount and the rate typed for it; null for a home slip. */
    val foreign: ForeignAmount?,
    /** The shop name as the reader left it. */
    val typedShop: String,
    /** True when the slip read a shop, which then stays the rule's key whatever name is typed over it. */
    val shopRead: Boolean,
    val planned: Boolean,
)

/**
 * FR-7: a slip becomes a row, its shop a rule, and it leaves the queue. A read shop stays the rule's key so its next
 * slip matches, a typed name is what rows show (or the shop when none was read), and only spending can be planned (#22).
 */
@OptIn(ExperimentalUuidApi::class)
class ConfirmSlipUseCase(
    private val importer: SlipImporter,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    /** The slip as filed, or the failure that kept it in the queue. */
    suspend operator fun invoke(confirmation: SlipConfirmation): Result<ConfirmedSlip> = runSuspendCatching {
        val slip = confirmation.slip
        val typed = confirmation.typedShop.trim()
        val foreign = confirmation.foreign
        val confirmed = ConfirmedSlip(
            transactionId = newId(),
            ruleId = newId(),
            queuedSlipId = slip.id,
            counterparty = if (confirmation.shopRead) slip.merchant else typed.ifEmpty { null },
            categoryId = confirmation.categoryId,
            subCategoryId = confirmation.subCategoryId,
            amount = confirmation.amount,
            occurredAtEpochMillis = slip.occurredAtEpochMillis,
            reference = slip.note.takeIf { it.isNotBlank() },
            confirmedAtEpochMillis = clock.now().toEpochMilliseconds(),
            // What the parser read, not what the reader typed over it, at the typed rate for a foreign slip.
            // FR-7 kept this null when the figure did not read, and null is what screen 11c wants too.
            readAmount = if (foreign == null) slip.amount else slip.amount?.let { ForeignAmount(slip.currency, it.toMinorUnits(), foreign.rate).inHome },
            folder = slip.sourceFolder,
            displayName = typed.takeIf { confirmation.shopRead && it.isNotEmpty() && it != slip.merchant },
            direction = slip.direction,
            planned = confirmation.planned && slip.direction == Direction.Expense,
            foreign = foreign,
        )
        importer.confirm(confirmed)
        confirmed
    }
}

/**
 * "A slip closes the debt": the debt is recorded as paid by bank transfer at the slip's own time.
 * [record] is the group payment write; a build with no shared groups passes one that does nothing.
 */
class PaySlipDebtUseCase(private val record: suspend (PaymentDraft) -> Unit = {}) {
    suspend operator fun invoke(slip: QueuedSlip, debt: SlipDebt): Result<Unit> = runSuspendCatching {
        record(PaymentDraft(debt.groupId, debt.fromMemberId, debt.toMemberId, debt.amount, PaymentMethod.BankTransfer, slip.occurredAtEpochMillis))
    }
}

/** A slip leaves the queue with no row and no rule, at once (no Undo window). */
class DiscardSlipUseCase(private val importer: SlipImporter) {
    suspend operator fun invoke(slipId: String) = importer.discard(slipId)
}

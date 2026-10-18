package com.ptut.expense.domain

/** How the row got here. The ledger shows "auto" for anything an importer filed. */
enum class TransactionSource { Scan, Share, Manual }

val TransactionSource.isAutomatic: Boolean
    get() = this != TransactionSource.Manual

data class Transaction(
    val id: String,
    val occurredAtEpochMillis: Long,
    val amount: Satang,
    val direction: Direction,
    val categoryId: String?,
    /**
     * FR-13's finer label, or null on a row nobody filed that way. Removing a sub-category nulls
     * this and touches nothing else, which is how the spending stays in the parent.
     */
    val subCategoryId: String? = null,
    val counterparty: String?,
    // Manual by default: only an importer may claim a row was automatic.
    val source: TransactionSource = TransactionSource.Manual,
    /**
     * The slip's own reference number, or null on a row nobody imported.
     *
     * The column has held this since schema 1 — it is half of the F3 content key that stops a
     * re-photographed slip filing twice — but nothing above the data layer could read it until
     * the detail screen had to show it. Adding it here is a mapping, not a migration.
     */
    val reference: String? = null,
    /**
     * When someone last corrected this row, or null while nobody has.
     *
     * Separate from [source] rather than a sixth value on it: an auto-filed row that was later
     * corrected is still auto-filed, and one enum cannot carry both facts. The Ledger's Edited
     * filter reads this field; #53 is what stamps it on save.
     */
    val editedAt: Long? = null,
    /**
     * What the reader wrote about this row, or null while nobody has. Screen 11c is the only
     * thing that sets it.
     *
     * Named apart from [QueuedSlip.note], which holds slip meta — a date and a reference number
     * the review queue renders as one line — and never user text. #26 left this name open exactly
     * so the two could not be confused, and one word covering both is how they would be.
     */
    val memo: String? = null,
    /**
     * The amount as the parser read it off the slip, or null on a row no slip produced.
     *
     * Kept beside [amount] rather than derived from it: once a correction lands, [amount] is the
     * reader's figure and the slip's own is gone. Page 11c draws both — "Read from the slip as
     * ฿642.50" under a corrected figure is the whole reason this field exists.
     */
    val slipAmount: Satang? = null,
    /**
     * When this row entered the ledger, or null on a row written before schema 9.
     *
     * Not [occurredAtEpochMillis]: a slip confirmed today can be a payment from last month, and
     * Today lists rows by when the money moved. This is how Home can still show what was saved.
     */
    val filedAt: Long? = null,
    /** The watched folder the slip came from, "Screenshots", or null where none did (#170). */
    val sourceFolder: String? = null,
    /** The shop rule that filed this row without asking, or null (#117). What the Why card explains. */
    val ruleId: String? = null,
)

val Transaction.isEdited: Boolean get() = editedAt != null

/**
 * A row before the ledger has given it an identity. The id is stamped by the repository, and so is
 * the instant unless the add sheet picked one: "when" for a hand-typed row is "now" by default.
 *
 * [categoryId] is not nullable. FR-14 wants a category on both kinds, and a draft that could carry
 * none would push that rule out into every caller (plan §G item 6).
 */
data class TransactionDraft(
    val amount: Satang,
    val direction: Direction,
    val categoryId: String,
    /** FR-13's finer label, optional. One that is not under [categoryId] is not stored (#110). */
    val subCategoryId: String? = null,
    val counterparty: String? = null,
    val source: TransactionSource = TransactionSource.Manual,
    /** The time the add sheet picked, or null for now. */
    val occurredAtEpochMillis: Long? = null,
    /** The add sheet's note (#211), already trimmed, or null for none. It becomes the row's name. */
    val memo: String? = null,
)

data class PeriodSummary(
    val income: Satang,
    val expense: Satang,
    val net: Satang,
    val count: Int,
)

fun List<Transaction>.summarise(): PeriodSummary {
    var income = Satang.Zero
    var expense = Satang.Zero
    forEach { transaction ->
        when (transaction.direction) {
            Direction.Income -> income += transaction.amount
            Direction.Expense -> expense += transaction.amount
        }
    }
    return PeriodSummary(
        income = income,
        expense = expense,
        net = income - expense,
        count = size,
    )
}

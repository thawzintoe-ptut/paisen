package com.ptut.expense.domain

/**
 * The fields screen 11c may change, carried as one value.
 *
 * One value rather than three parameters, because the question the screen asks all the time is
 * "has anything actually changed" — and against a value that is a single equality check. #53's
 * watch-out is that Save is a dirty indicator and not a focus tracker: typing `485` over `485`
 * changes nothing, and neither does re-picking the category that was already picked.
 *
 * Merchant and reference are deliberately absent. They are evidence — what the slip said — and
 * an edit that could rewrite them would erase the thing it is correcting. The time can change.
 */
data class TransactionEdit(
    val amount: Satang,
    val categoryId: String?,
    val memo: String?,
    /** Goes with [categoryId]: a new parent without a new sub-category leaves none (#110). */
    val subCategoryId: String? = null,
    /** When the money moved. Null keeps the stored time. */
    val occurredAtEpochMillis: Long? = null,
) {
    companion object {
        /**
         * Trims, and turns a memo of nothing but spaces into no memo at all. Without it a stray
         * space would count as a change and light Save on a row nobody has altered.
         */
        fun of(
            amount: Satang,
            categoryId: String?,
            memo: String?,
            subCategoryId: String? = null,
            occurredAtEpochMillis: Long? = null,
        ): TransactionEdit =
            TransactionEdit(
                amount = amount,
                categoryId = categoryId,
                memo = memo?.trim()?.ifEmpty { null },
                subCategoryId = subCategoryId,
                occurredAtEpochMillis = occurredAtEpochMillis,
            )
    }
}

/** What the row holds now, in the shape an edit is compared against. */
fun Transaction.asEdit(): TransactionEdit =
    TransactionEdit.of(amount, categoryId, memo, subCategoryId, occurredAtEpochMillis)

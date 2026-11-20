package com.ptut.expense.domain.agent

import com.ptut.expense.agent.ProposedAction
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.under

/** The Add form's picks that a sentence can change. The amount stays with the form, which types it. */
data class TransactionPicks(
    val kind: Direction,
    val category: Category?,
    val subCategory: SubCategory?,
    val occurredAtEpochMillis: Long?,
    val memo: String?,
)

/** [TransactionPicks] after a sentence, with the chips they now offer. */
data class MergedPicks(
    val picks: TransactionPicks,
    /** The categories offered for the merged kind. */
    val categories: List<Category>,
    /** The merged category's own sub-categories. */
    val subCategories: List<SubCategory>,
)

/**
 * #220: what a sentence read goes into the form; what it did not mention stays. Only income or a named category moves
 * the kind, since the grammar defaults to expense, and a category the reader no longer has is left unpicked.
 */
fun mergeProposedTransaction(
    action: ProposedAction.AddTransaction,
    current: TransactionPicks,
    categoriesFor: (Direction) -> List<Category>,
    subCategories: List<SubCategory>,
): MergedPicks {
    val kind = if (action.direction == Direction.Income || action.categoryId != null) action.direction else current.kind
    val offered = categoriesFor(kind)
    val named = offered.firstOrNull { it.id == action.categoryId }
    val category = named ?: current.category.takeIf { kind == current.kind }
    val underCategory = subCategories.under(category?.id)
    return MergedPicks(
        picks = TransactionPicks(
            kind = kind,
            category = category,
            subCategory = underCategory.firstOrNull { it.id == action.subCategoryId }
                ?: current.subCategory.takeIf { category == current.category },
            occurredAtEpochMillis = action.occurredAtEpochMillis ?: current.occurredAtEpochMillis,
            memo = action.memo ?: current.memo,
        ),
        categories = offered,
        subCategories = underCategory,
    )
}

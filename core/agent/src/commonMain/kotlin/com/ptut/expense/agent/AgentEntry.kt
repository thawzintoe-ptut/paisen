package com.ptut.expense.agent

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.shared.SharedGroup

/**
 * #206: what Siri and the Shortcuts app hand over. Their arguments are words, so
 * they go through the same normaliser and grammar a typed sentence does, and each one ends as a
 * [ProposedAction] in [AgentInbox]. Nothing saves here: the sheet that opens does, after Confirm.
 *
 * [categories], [subCategories] and [groups] are read on each call, because a Shortcut can run long
 * after launch.
 */
class AgentEntry(
    private val normaliser: NormaliserSource,
    private val reader: AgentReader,
    private val categories: () -> List<Category>,
    private val subCategories: () -> List<SubCategory>,
    private val groups: () -> List<SharedGroup>,
    private val grammar: CommandGrammar = CommandGrammar(),
) {

    /**
     * Siri's "Add expense": an amount as Swift formatted it, and a category and note as spoken. The
     * category word may name a sub-category, which brings its parent.
     */
    suspend fun addExpense(amount: String?, category: String?, note: String?) {
        val text = normaliser.get()
        val spending = categories().forDirection(Direction.Expense)
        val choice = category?.let(text::normalise)?.let { grammar.choiceIn(it, spending, subCategories()) }
        AgentInbox.offer(
            ProposedAction.AddTransaction(
                amount = amount?.let(grammar::amountOf),
                categoryId = choice?.category?.id,
                subCategoryId = choice?.subCategory?.id,
                memo = note?.let(text::normalise)?.ifEmpty { null },
            ),
        )
    }

    /** "Add shared expense": a group by its name, which Siri hears as words, not as an id. */
    suspend fun addSharedExpense(group: String?, amount: String?, description: String?) {
        val text = normaliser.get()
        val name = group?.let(text::normalise).orEmpty()
        val all = groups()
        val match = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: all.firstOrNull { name.isNotEmpty() && it.name.contains(name, ignoreCase = true) }
        AgentInbox.offer(
            ProposedAction.AddGroupExpense(
                groupId = match?.id,
                amount = amount?.let(grammar::amountOf),
                description = description?.let(text::normalise)?.ifEmpty { null },
            ),
        )
    }

    /** "How much did I spend": answered in place, read only, with no sheet. */
    suspend fun spent(window: SpendWindow): Satang = reader.spent(window)

    /** The same, with the period as Siri and Gemini say it: `today`, `week` or `month`. Anything else is today. */
    suspend fun spent(period: String): Satang =
        spent(SpendWindow.entries.firstOrNull { it.name.equals(period, ignoreCase = true) } ?: SpendWindow.Today)
}

package com.ptut.expense.domain.shared

import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/** What Add expense hands over to save: the form already read into numbers. */
data class SharedExpenseInput(
    val expenseId: String?,
    val groupId: String,
    val description: String,
    /** The category's name as every member reads it (the table's language), for a description left blank. */
    val categoryName: String?,
    /** Null saves it at the moment of saving. */
    val occurredAtEpochMillis: Long?,
    val categoryId: String?,
    val subCategoryId: String?,
    val note: String,
    val currency: String,
    val amountMinor: Long,
    val rate: Rate?,
    val payerMemberId: String,
    val splitMode: SplitMode,
    val weights: Map<String, Long>,
    /** #186: share this existing transaction rather than writing a new one for "you paid". */
    val linkTransactionId: String?,
    /** The version an edit opened on, so a save sees another phone's change or removal first. */
    val baseVersionId: String?,
)

sealed interface SaveExpenseResult {
    /** [title] is the name every member sees, the description or the category's name (#183). */
    data class Saved(val expenseId: String, val title: String) : SaveExpenseResult

    /** Another phone changed or removed the expense since the form opened; the next save writes over it. */
    data class Changed(val latestVersionId: String, val removed: Boolean) : SaveExpenseResult

    /** Nothing was written (#76): the form keeps what was typed. */
    data object Failed : SaveExpenseResult
}

/** #183: a new or edited expense in a group, named, cut to the text limits, and timed now unless a time was chosen. */
class SaveSharedExpenseUseCase(
    private val repository: SharedRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(input: SharedExpenseInput): SaveExpenseResult {
        val title = ExpenseTitle.shown(note = null, input.description, input.categoryName)
        return try {
            val id = repository.saveExpense(
                ExpenseDraft(
                    expenseId = input.expenseId,
                    groupId = input.groupId,
                    description = title.take(SharedTextLimits.DESCRIPTION),
                    occurredAtEpochMillis = input.occurredAtEpochMillis ?: clock.now().toEpochMilliseconds(),
                    categoryId = input.categoryId,
                    subCategoryId = input.subCategoryId,
                    note = input.note.take(SharedTextLimits.NOTE),
                    currency = input.currency,
                    amountMinor = input.amountMinor,
                    rate = input.rate,
                    payerMemberId = input.payerMemberId,
                    splitMode = input.splitMode,
                    weights = input.weights,
                    linkTransactionId = input.linkTransactionId,
                    baseVersionId = input.baseVersionId,
                ),
            )
            SaveExpenseResult.Saved(id, title)
        } catch (changed: ExpenseChanged) {
            SaveExpenseResult.Changed(changed.latestVersionId, changed.removed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SaveExpenseResult.Failed
        }
    }
}

package com.ptut.expense.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.asEdit
import com.ptut.expense.domain.displayName
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.format
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.rowWithId
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.sharedExpenseOf
import com.ptut.expense.domain.under
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.MetaGlyph
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.TransactionMetaRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** FR-21's correction, on the fields a person can honestly know better than the slip. */
class EditTransactionViewModel(
    private val transactionId: String,
    private val ledger: LedgerRepository,
    /**
     * The categories the reader holds, filtered to the row's own direction. Read here rather than
     * from a compiled-in list, so a category they made is offered like any other.
     */
    private val known: List<Category> = Category.seed(),
    private val subCategories: List<SubCategory> = emptyList(),
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
    /** #186: the group a shared purchase lives in follows the save. Null in a screen test's graph. */
    private val shared: SharedRepository? = null,
    /** #216: "Updated …" once the save lands, as Add says "Saved …". Null in a screen test's graph. */
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    /**
     * What has been typed, or null while nothing has.
     *
     * Null is what lets the screen open on the record without a seeding pass: with nothing typed
     * the mapper reads the row itself, so the first frame already shows ฿642.50. The moment
     * anything is typed this takes over, and a later ledger emission can no longer move the digits
     * under the reader's thumb.
     */
    private val typed = MutableStateFlow<EditDraft?>(null)

    /**
     * The note is typed away from the keypad, because a soft keyboard opening under the amount
     * would cover the digits the reader is checking.
     */
    private val editingMemo = MutableStateFlow(false)

    private val stored = MutableStateFlow(false)

    /** Set before the save's launch, so a second tap in the same frame finds it already true. */
    private var saving = false

    /**
     * True once the correction is durable. The shell leaves on this rather than on the tap:
     * popping first destroys this holder, and a cancelled `viewModelScope` would take the write
     * with it.
     */
    val isSaved: StateFlow<Boolean> = stored.asStateFlow()

    val uiState: StateFlow<EditTransactionUiState> =
        combine(ledger.allTransactions, typed, editingMemo, shared?.snapshot ?: flowOf(SharedSnapshot.Empty)) { rows, typed, memo, groups ->
            when (val state = screenFor(rows, typed, memo)) {
                is EditTransactionUiState.Loaded -> state.copy(
                    alsoChanges = groups.sharedExpenseOf(transactionId)?.let { groups.group(it.groupId) }?.let { AppText.copy.record.alsoChanges(it.name) },
                )

                else -> state
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS),
                initialValue = EditTransactionUiState.Loading,
            )

    fun type(entry: AmountEntry) = change { it.copy(entry = entry) }

    /** A sub-category belongs to one parent (p34), so a new parent leaves it behind. */
    fun choose(category: Category) = change { draft ->
        draft.copy(
            categoryId = category.id,
            subCategoryId = draft.subCategoryId.takeIf { category.id == draft.categoryId },
        )
    }

    /** Optional, so a second tap takes it off again (#111). */
    fun chooseSubCategory(subCategory: SubCategory) = change { draft ->
        draft.copy(subCategoryId = subCategory.id.takeIf { it != draft.subCategoryId })
    }

    /** When the money moved, picked on the phone's own clock. Every row's time can change. */
    fun setTime(epochMillis: Long) = change { it.copy(occurredAtEpochMillis = epochMillis) }

    fun openMemo() {
        editingMemo.value = true
    }

    fun closeMemo() {
        editingMemo.value = false
    }

    /** Takes the note and closes the sheet, because the sheet exists only to collect it. */
    fun setMemo(memo: String) {
        change { it.copy(memo = memo) }
        editingMemo.value = false
    }

    /**
     * Writes the correction, then says it is done.
     *
     * Both guards are the rule the Save button draws: nothing is written for a row that has gone,
     * and nothing is written when the values already match what is stored. The second is not
     * belt-and-braces — it is what keeps "inert until something changes" true if the button ever
     * stops enforcing it.
     */
    fun save() {
        val record = record() ?: return
        val draft = onScreen(record)
        val edit = draft.toEdit()
        // #214: an emptied amount is not ฿0.00, and a second tap while the first writes is not a second write.
        if (edit == record.asEdit() || draft.entry.isEmpty || saving || stored.value) return

        saving = true
        viewModelScope.launch {
            ledger.applyEdit(record.id, edit)
            // O4: the change flows one way, from History to the shared copy.
            shared?.followTransaction(record.id)
            haptics.saved()
            val edited = record.copy(categoryId = edit.categoryId, subCategoryId = edit.subCategoryId, memo = edit.memo)
            toasts?.show(ToastMessage(AppText.copy.record.updated(edited.displayName(known, subCategories))))
            stored.value = true
        }
    }

    /**
     * An empty ledger is Room before it has answered, not a row that went: the reader arrived here
     * from the record, so there was one. Reading it as missing would flash "gone" on every cold
     * open, ahead of the row they came to correct.
     */
    private fun screenFor(
        snapshot: LedgerSnapshot,
        typed: EditDraft?,
        editingMemo: Boolean,
    ): EditTransactionUiState {
        val rows = when (snapshot) {
            LedgerSnapshot.Unanswered -> return EditTransactionUiState.Loading
            is LedgerSnapshot.Rows -> snapshot.transactions
        }
        val record = rows.firstOrNull { it.id == transactionId }
            ?: return EditTransactionUiState.Missing

        val draft = typed ?: record.asDraft()
        val changed = draft.toEdit() != record.asEdit()
        return EditTransactionUiState.Loaded(
            entry = draft.entry,
            slipRead = record.slipRead(),
            categories = known.forDirection(record.direction),
            categoryId = draft.categoryId,
            subCategories = subCategories.under(draft.categoryId),
            subCategoryId = draft.subCategoryId,
            memo = draft.memo,
            occurredAtEpochMillis = draft.occurredAtEpochMillis,
            evidence = record.evidence(),
            notice = record.notice(),
            canSave = changed && !draft.entry.isEmpty,
            changed = changed,
            editingMemo = editingMemo,
            isIncome = record.direction == Direction.Income,
        )
    }

    /** Every change starts from what is on screen, which is the record until someone types. */
    private fun change(edit: (EditDraft) -> EditDraft) {
        typed.value = edit(onScreen(record() ?: return))
    }

    /** What the screen is showing: whatever has been typed, or the record while nothing has. */
    private fun onScreen(record: Transaction): EditDraft = typed.value ?: record.asDraft()

    private fun record(): Transaction? = ledger.allTransactions.value.rowWithId(transactionId)

    /**
     * What the slip said, drawn but never editable. The spec's reason is worth keeping: they are
     * evidence, not preferences — a correction that could rewrite them would erase the thing it is
     * being measured against. The time is no longer one of them: it is a field on the form.
     *
     * A row nobody photographed has no reference, so that line goes rather than rendering a field
     * with nothing in it. The shop line goes the same way on a row with no shop name (#210).
     */
    private fun Transaction.evidence(): List<TransactionMetaRow> = listOfNotNull(
        counterparty?.let { TransactionMetaRow("MERCHANT", it, icon = MetaGlyph.Merchant) },
        reference?.let { TransactionMetaRow("REFERENCE", it, icon = MetaGlyph.Tag) },
    )

    /**
     * Names the fields that stay as read, and that the ledger will mark the row. A row nobody
     * photographed gets the true version of the same promise rather than one about a slip it
     * never had.
     *
     * It names only what [evidence] actually draws. A slip can carry an amount and no reference,
     * and promising that a reference stays as read while no such row is on screen is the same
     * confident wrong answer as claiming a slip that never existed.
     */
    private fun Transaction.notice(): String {
        val text = AppText.copy.record
        val marked = text.markedEdited
        if (slipAmount == null && reference == null) {
            return "${text.nothingFromSlip} $marked"
        }
        val asRead = when {
            counterparty == null && reference == null -> return "${text.amountFromSlip} $marked"
            counterparty == null -> text.referenceStays
            reference == null -> text.shopStays
            else -> text.bothStay
        }
        // p34: sorting is not retroactive, and the screen says so where the category changes.
        val sorting = counterparty?.let { text.sortingStays(it) }
        return listOfNotNull(asRead, marked, sorting).joinToString(" ")
    }

    /** Spec page 11c's line under the amount. The figure is the slip's, never the one being typed. */
    private fun Transaction.slipRead(): String? =
        slipAmount?.let { AppText.copy.record.readFromSlip(it.format()) }

    private fun Transaction.asDraft() = EditDraft(
        entry = AmountEntry.of(amount),
        categoryId = categoryId,
        memo = memo,
        subCategoryId = subCategoryId,
        occurredAtEpochMillis = occurredAtEpochMillis,
    )

    private companion object {
        /** Long enough to survive a rotation without restarting the collection. */
        const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}

/**
 * The editable fields as the screen holds them. The sub-category has no chips here until #111;
 * it is carried so a save of anything else leaves it where it was.
 *
 * The amount is an [AmountEntry] and not a figure because the keypad has to show exactly what was
 * typed. [toEdit] is where it becomes money, which is also where `642.5` and `642.50` stop being
 * two different things.
 */
private data class EditDraft(
    val entry: AmountEntry,
    val categoryId: String?,
    val memo: String?,
    val subCategoryId: String?,
    val occurredAtEpochMillis: Long,
) {
    fun toEdit(): TransactionEdit =
        TransactionEdit.of(entry.toSatang(), categoryId, memo, subCategoryId, occurredAtEpochMillis)
}

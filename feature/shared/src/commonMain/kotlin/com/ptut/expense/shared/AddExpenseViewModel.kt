package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.ExpenseChanged
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.ExpenseTitle
import com.ptut.expense.domain.shared.LogOrder
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SharedTextLimits
import com.ptut.expense.domain.shared.SplitMember
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.membersToSplit
import com.ptut.expense.domain.shared.money
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.inTableLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * #183's Add expense and #138's Split options, one holder for both: the split is part of the
 * expense being written, and DONE only hands it back. Nothing is stored until SAVE EXPENSE.
 */
class AddExpenseViewModel(
    private val groupId: String,
    private val expenseId: String?,
    private val repository: SharedRepository,
    private val categories: List<Category>,
    private val clock: Clock = Clock.System,
    /** #186: a purchase from History, shared from its detail screen. */
    private val transactionId: String? = null,
    /** #197: the same word and haptic a v1 save gives. Null in a screen test's graph. */
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
    /** #206: an amount ("600.00") and a description from Siri or Quick add, for a new expense only. */
    private val prefillAmount: String? = null,
    private val prefillDescription: String? = null,
) : ViewModel() {

    private val form = MutableStateFlow(ExpenseForm())

    private val mapper = AddExpenseUiMapper(categories, isEdit = expenseId != null)

    /** The form as it opened, so a close can tell whether anything would be lost (#214). */
    private val opened = MutableStateFlow<ExpenseForm?>(null)

    /** The version an edit opened on, so a save sees another phone's change or removal first. */
    private var base: String? = null

    private val saved = MutableStateFlow(false)

    /** True once the expense is saved; the shell leaves on this. */
    val isSaved: StateFlow<Boolean> = saved.asStateFlow()

    init {
        viewModelScope.launch {
            load()
            opened.value = form.value.content
        }
    }

    val uiState: StateFlow<AddExpenseUiState> = combine(repository.snapshot, form, opened) { snapshot, form, opened ->
        val group = snapshot.group(groupId) ?: return@combine AddExpenseUiState.Missing
        mapper.ready(group, form, membersOf(snapshot, group, form.splitMode)).copy(changed = opened != null && form.content != opened)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), AddExpenseUiState.Missing)

    /** An edit starts from the latest version; a new expense from You paying, split equally (#183). */
    private suspend fun load() {
        val snapshot = repository.snapshot.first { it.group(groupId) != null }
        val group = snapshot.group(groupId) ?: return
        val existing = latestOf(snapshot)
        val seed = transactionId?.let { repository.transactionSeed(it) }
        if (existing == null && seed != null) {
            form.value = ExpenseForm.fromSeed(seed, group, categories)
            if (form.value.currency.code != group.currency) prefillRate(form.value.currency, group)
            return
        }
        if (existing == null) {
            form.update {
                it.copy(
                    currency = group.money,
                    payerMemberId = group.meMemberId,
                    categoryId = categories.firstOrNull()?.id,
                    amountText = prefillAmount ?: it.amountText,
                    description = prefillDescription ?: it.description,
                    loaded = true,
                )
            }
            return
        }
        // Removed on another phone before this opened: the form shows what it was, and a save brings it back.
        if (existing.removed) toasts?.show(ToastMessage(text = AppText.copy.shared.expense.openedRemoved))
        base = existing.versionId
        form.value = ExpenseForm.fromExisting(existing, linked = existing.id in snapshot.links)
    }

    fun setAmount(text: String) = unlocked { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) }

    fun setRate(text: String) = form.update { it.copy(rateText = text.filter { c -> c.isDigit() || c == '.' }) }

    /** #181: a new currency keeps the split mode, clears Exact amounts, and pre-fills the last rate used. */
    fun setCurrency(currency: Currency) {
        if (form.value.locked) return
        form.update {
            it.copy(
                currency = currency,
                rateText = "",
                weightTexts = if (it.splitMode == SplitMode.Exact) emptyMap() else it.weightTexts,
            )
        }
        val group = repository.snapshot.value.group(groupId) ?: return
        if (currency.code == group.currency) return
        prefillRate(currency, group)
    }

    /** The last rate saved from [currency] into this group's money, while the field is still empty. */
    private fun prefillRate(currency: Currency, group: SharedGroup) {
        viewModelScope.launch {
            val last = repository.lastRate(currency.code, into = group.currency) ?: return@launch
            form.update { if (it.currency == currency && it.rateText.isEmpty()) it.copy(rateText = last.typedFor(currency.rateUnit)) else it }
        }
    }

    fun setDescription(text: String) = form.update { it.copy(description = text.take(SharedTextLimits.DESCRIPTION)) }

    fun setNote(text: String) = form.update { it.copy(note = text.take(SharedTextLimits.NOTE)) }

    fun setTime(epochMillis: Long) = unlocked { it.copy(occurredAtEpochMillis = epochMillis) }

    fun setCategory(id: String) = form.update { if (it.categoryLocked) it else it.copy(categoryId = id, subCategoryId = null) }

    fun setPayer(memberId: String) = unlocked { it.copy(payerMemberId = memberId) }

    /** A row in the sheet: payer and split change together. A locked payer stays (#186). */
    fun setQuickSplit(payerId: String, choice: QuickSplit) {
        val members = currentMembers() ?: return
        form.update { if (it.locked && it.payerMemberId != payerId) it else it.withQuick(payerId, choice, members) }
    }

    /** A payer chip: Owed the full amount moves with the payer, any other split keeps its parts. */
    fun setQuickPayer(payerId: String) {
        val snapshot = repository.snapshot.value
        val group = snapshot.group(groupId) ?: return
        val members = membersOf(snapshot, group, form.value.splitMode)
        unlocked {
            if (it.quickIn(group, members) == QuickSplit.OwedInFull) it.withQuick(payerId, QuickSplit.OwedInFull, members) else it.copy(payerMemberId = payerId)
        }
    }

    private fun currentMembers(): List<SplitMember>? {
        val snapshot = repository.snapshot.value
        return snapshot.group(groupId)?.let { membersOf(snapshot, it, form.value.splitMode) }
    }

    private fun unlocked(change: (ExpenseForm) -> ExpenseForm) = form.update { if (it.locked) it else change(it) }

    fun openSplit(open: Boolean) = form.update { it.copy(splitOpen = open) }

    fun setSplitMode(mode: SplitMode) = form.update { if (it.splitMode == mode) it else it.copy(splitMode = mode, weightTexts = emptyMap(), lastEdited = null) }

    fun setWeight(memberId: String, text: String) =
        form.update { it.copy(weightTexts = it.weightTexts + (memberId to text.filter { c -> c.isDigit() || c == '.' }.take(MAX_WEIGHT_CHARS)), lastEdited = memberId) }

    /** p29's GIVE TO: whatever is left goes on the field edited last. */
    fun giveLeftToLastEdited() {
        val state = uiState.value as? AddExpenseUiState.Ready ?: return
        val target = state.form.lastEdited ?: return
        val snapshot = repository.snapshot.value
        val group = snapshot.group(groupId) ?: return
        val check = state.form.checkIn(group, membersOf(snapshot, group, state.form.splitMode)) ?: return
        val given = state.form.weightOf(target) + check.left
        form.update { it.copy(weightTexts = it.weightTexts + (target to it.weightText(given))) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val state = uiState.value as? AddExpenseUiState.Ready ?: return
        // The form, not uiState: uiState catches up a frame later, and the second tap comes sooner.
        if (!state.canSave || form.value.saving) return
        val form = state.form
        val group = repository.snapshot.value.group(groupId) ?: return
        val title = titleOf(form)
        // Worked out before Save goes dark, so nothing below can leave it dark with no hint.
        val amount = form.amountMinor() ?: return
        val payer = form.payerMemberId ?: group.meMemberId ?: return
        this.form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val text = AppText.copy.shared.expense
            val savedId = try {
                repository.saveExpense(
                    ExpenseDraft(
                        expenseId = expenseId,
                        groupId = groupId,
                        description = title.take(SharedTextLimits.DESCRIPTION),
                        occurredAtEpochMillis = form.occurredAtEpochMillis ?: clock.now().toEpochMilliseconds(),
                        categoryId = form.categoryId,
                        subCategoryId = form.subCategoryId,
                        note = form.note.take(SharedTextLimits.NOTE),
                        currency = form.currency.code,
                        amountMinor = amount,
                        rate = form.rateIn(group),
                        payerMemberId = payer,
                        splitMode = form.splitMode,
                        weights = form.weightsIn(group),
                        linkTransactionId = transactionId,
                        baseVersionId = base,
                    ),
                )
            } catch (changed: ExpenseChanged) {
                // Said once: the next Save writes this form over what the other phone did.
                base = changed.latestVersionId
                this@AddExpenseViewModel.form.update { it.copy(saving = false) }
                toasts?.show(ToastMessage(text = if (changed.removed) text.removedElsewhere else text.changedElsewhere))
                return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // As Record payment (#76): Save comes back and the form keeps what was typed.
                this@AddExpenseViewModel.form.update { it.copy(saving = false) }
                toasts?.show(ToastMessage(text = text.didntSave))
                return@launch
            }
            haptics?.saved()
            // "Added Dinner · you are owed ฿320": the result line's meaning, said once more.
            val meaning = state.meaning
            toasts?.show(
                ToastMessage(
                    text = if (expenseId != null) {
                        text.updated
                    } else {
                        listOfNotNull(text.added(title), meaning).joinToString(Separators.DOT)
                    },
                    // #66: Undo takes back only a new expense; an edit has no version to step back to here.
                    action = if (expenseId == null) ToastAction(AppText.copy.common.undo) { repository.removeExpense(savedId) } else null,
                ),
            )
            saved.value = true
            onSaved()
        }
    }

    /** D8: a removal adds a log entry; your own transaction, if any, stays in History. */
    fun remove(onRemoved: () -> Unit) {
        val id = expenseId ?: return
        val name = (uiState.value as? AddExpenseUiState.Ready)?.form?.description?.trim().orEmpty()
        val repository = repository
        viewModelScope.launch {
            repository.removeExpense(id)
            haptics?.deleted()
            // #197: the shell runs the Undo, because this screen closes as soon as it is removed.
            toasts?.show(
                ToastMessage(
                    text = AppText.copy.shared.expense.removed(name),
                    tone = ToastTone.Deleted,
                    action = ToastAction(AppText.copy.common.undo) { repository.restoreExpense(id) },
                ),
            )
            onRemoved()
        }
    }

    /**
     * The people still in the group, and on an edit everyone with a part in the version being edited.
     * An edit of an Equally split keeps only those, as the repository does when it saves.
     */
    private fun membersOf(snapshot: SharedSnapshot, group: SharedGroup, mode: SplitMode): List<SplitMember> =
        group.membersToSplit(mode, sharers = latestOf(snapshot)?.shares.orEmpty().mapTo(mutableSetOf()) { it.memberId })

    /** The newest version of the expense being edited, a removed one too; null for a new expense. */
    private fun latestOf(snapshot: SharedSnapshot): SharedExpense? =
        expenseId?.let { id -> snapshot.expenseVersions.filter { it.groupId == groupId && it.id == id }.maxWithOrNull(LogOrder) }

    /**
     * What the expense is called: the typed description, or the category's name when none was typed.
     * Every member reads that name, so an installed category goes in as its English name, not this phone's language.
     */
    private fun titleOf(form: ExpenseForm): String =
        ExpenseTitle.shown(note = null, form.description, categories.firstOrNull { it.id == form.categoryId }?.inTableLanguage()?.name)
}

/** "1234567890123.45": the longest amount an Exact field takes, and plenty of shares. */
private const val MAX_WEIGHT_CHARS = 16

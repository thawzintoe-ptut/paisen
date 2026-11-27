package com.ptut.expense.shared

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.ExpenseStart
import com.ptut.expense.domain.shared.GetLastGroupRateUseCase
import com.ptut.expense.domain.shared.LoadExpenseDraftUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.RemoveSharedExpenseUseCase
import com.ptut.expense.domain.shared.RestoreSharedExpenseUseCase
import com.ptut.expense.domain.shared.SaveExpenseResult
import com.ptut.expense.domain.shared.SaveSharedExpenseUseCase
import com.ptut.expense.domain.shared.SharedExpenseInput
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SharedTextLimits
import com.ptut.expense.domain.shared.SplitMember
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.splitMembersFor
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.inTableLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * #183's Add expense and #138's Split options, one holder for both: the split is part of the
 * expense being written, and DONE only hands it back. Nothing is stored until SAVE EXPENSE.
 */
class AddExpenseViewModel(
    private val groupId: String,
    private val expenseId: String?,
    private val categories: List<Category>,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val loadDraft: LoadExpenseDraftUseCase,
    private val getLastRate: GetLastGroupRateUseCase,
    private val saveExpense: SaveSharedExpenseUseCase,
    private val removeExpense: RemoveSharedExpenseUseCase,
    private val restoreExpense: RestoreSharedExpenseUseCase,
    /** #186: a purchase from History, shared from its detail screen. */
    private val transactionId: String? = null,
    /** #197: the same word and haptic a v1 save gives. Null in a screen test's graph. */
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
    /** #206: an amount ("600.00") and a description from Siri or Quick add, for a new expense only. */
    private val prefillAmount: String? = null,
    private val prefillDescription: String? = null,
    /** #94: the wheel's pick pays; a member no longer in the group falls back to you. */
    private val prefillPayer: String? = null,
    /** The half-typed expense, kept through Android killing the process. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val snapshot = observeSnapshot()

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
            restoreDraft()
            combine(form, saved) { form, done -> form.content.takeUnless { done || it == opened.value } }.collect(::keepDraft)
        }
    }

    private fun key(field: String) = "expense.$groupId.${expenseId ?: transactionId ?: "new"}.$field"

    /** After [load], so the loaded expense or prefill never overwrites what was typed. */
    private fun restoreDraft() {
        val description = savedState.get<String>(key(DESCRIPTION)) ?: return
        form.update { current ->
            val restored = current.copy(
                description = description,
                note = savedState.get<String>(key(NOTE)).orEmpty(),
                categoryId = if (current.categoryLocked) current.categoryId else savedState.get<String>(key(CATEGORY)),
                subCategoryId = if (current.categoryLocked) current.subCategoryId else savedState.get<String>(key(SUB)),
                rateText = savedState.get<String>(key(RATE)).orEmpty(),
                splitMode = SplitMode.entries.firstOrNull { it.name == savedState.get<String>(key(MODE)) } ?: current.splitMode,
                weightTexts = savedState.get<String>(key(WEIGHTS)).orEmpty().split(';').filter { '=' in it }
                    .associate { it.substringBefore('=') to it.substringAfter('=') },
            )
            if (current.locked) {
                restored
            } else {
                restored.copy(
                    amountText = savedState.get<String>(key(AMOUNT)).orEmpty(),
                    currency = savedState.get<String>(key(CURRENCY))?.let(Currencies::of) ?: current.currency,
                    occurredAtEpochMillis = savedState.get<Long>(key(TIME)),
                    payerMemberId = savedState.get<String>(key(PAYER)) ?: current.payerMemberId,
                )
            }
        }
    }

    private fun keepDraft(form: ExpenseForm?) {
        if (form == null) {
            FIELDS.forEach { savedState.remove<Any>(key(it)) }
            return
        }
        savedState[key(DESCRIPTION)] = form.description
        savedState[key(NOTE)] = form.note
        savedState[key(CATEGORY)] = form.categoryId
        savedState[key(SUB)] = form.subCategoryId
        savedState[key(RATE)] = form.rateText
        savedState[key(MODE)] = form.splitMode.name
        savedState[key(WEIGHTS)] = form.weightTexts.entries.joinToString(";") { "${it.key}=${it.value}" }
        savedState[key(AMOUNT)] = form.amountText
        savedState[key(CURRENCY)] = form.currency.code
        savedState[key(TIME)] = form.occurredAtEpochMillis
        savedState[key(PAYER)] = form.payerMemberId
    }

    val uiState: StateFlow<AddExpenseUiState> = combine(snapshot, form, opened) { snapshot, form, opened ->
        val group = snapshot.group(groupId) ?: return@combine AddExpenseUiState.Missing
        mapper.ready(group, form, membersOf(snapshot, group, form.splitMode)).copy(changed = opened != null && form.content != opened)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), AddExpenseUiState.Missing)

    /** An edit starts from the latest version; a new expense from You paying, split equally (#183). */
    private suspend fun load() {
        when (val start = loadDraft(groupId, expenseId, transactionId) ?: return) {
            is ExpenseStart.FromTransaction -> {
                form.value = ExpenseForm.fromSeed(start.seed, start.group, categories)
                if (form.value.currency.code != start.group.currency) prefillRate(form.value.currency, start.group)
            }

            is ExpenseStart.New -> form.update {
                it.copy(
                    currency = start.group.money,
                    payerMemberId = prefillPayer?.takeIf { id -> start.group.activeMembers.any { it.id == id } } ?: start.group.meMemberId,
                    categoryId = categories.firstOrNull()?.id,
                    amountText = prefillAmount ?: it.amountText,
                    description = prefillDescription ?: it.description,
                    loaded = true,
                )
            }

            is ExpenseStart.Existing -> {
                // Removed on another phone before this opened: the form shows what it was, and a save brings it back.
                if (start.expense.removed) toasts?.show(ToastMessage(text = AppText.copy.shared.expense.openedRemoved))
                base = start.expense.versionId
                form.value = ExpenseForm.fromExisting(start.expense, linked = start.linked)
            }
        }
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
        val group = snapshot.value.group(groupId) ?: return
        if (currency.code == group.currency) return
        prefillRate(currency, group)
    }

    /** The last rate saved from [currency] into this group's money, while the field is still empty. */
    private fun prefillRate(currency: Currency, group: SharedGroup) {
        viewModelScope.launch {
            val last = getLastRate(currency.code, into = group.currency) ?: return@launch
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
        val current = snapshot.value
        val group = current.group(groupId) ?: return
        val members = membersOf(current, group, form.value.splitMode)
        unlocked {
            if (it.quickIn(group, members) == QuickSplit.OwedInFull) it.withQuick(payerId, QuickSplit.OwedInFull, members) else it.copy(payerMemberId = payerId)
        }
    }

    private fun currentMembers(): List<SplitMember>? {
        val current = snapshot.value
        return current.group(groupId)?.let { membersOf(current, it, form.value.splitMode) }
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
        val current = snapshot.value
        val group = current.group(groupId) ?: return
        val check = state.form.checkIn(group, membersOf(current, group, state.form.splitMode)) ?: return
        val given = state.form.weightOf(target) + check.left
        form.update { it.copy(weightTexts = it.weightTexts + (target to it.weightText(given))) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val state = uiState.value as? AddExpenseUiState.Ready ?: return
        // The form, not uiState: uiState catches up a frame later, and the second tap comes sooner.
        if (!state.canSave || form.value.saving) return
        val form = state.form
        val group = snapshot.value.group(groupId) ?: return
        // Worked out before Save goes dark, so nothing below can leave it dark with no hint.
        val amount = form.amountMinor() ?: return
        val payer = form.payerMemberId ?: group.meMemberId ?: return
        val input = SharedExpenseInput(
            expenseId = expenseId,
            groupId = groupId,
            description = form.description,
            // Every member reads the title, so an installed category goes in as its English name, not this phone's language.
            categoryName = categories.firstOrNull { it.id == form.categoryId }?.inTableLanguage()?.name,
            occurredAtEpochMillis = form.occurredAtEpochMillis,
            categoryId = form.categoryId,
            subCategoryId = form.subCategoryId,
            note = form.note,
            currency = form.currency.code,
            amountMinor = amount,
            rate = form.rateIn(group),
            payerMemberId = payer,
            splitMode = form.splitMode,
            weights = form.weightsIn(group),
            linkTransactionId = transactionId,
            baseVersionId = base,
        )
        this.form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val text = AppText.copy.shared.expense
            when (val result = saveExpense(input)) {
                is SaveExpenseResult.Changed -> {
                    // Said once: the next Save writes this form over what the other phone did.
                    base = result.latestVersionId
                    this@AddExpenseViewModel.form.update { it.copy(saving = false) }
                    toasts?.show(ToastMessage(text = if (result.removed) text.removedElsewhere else text.changedElsewhere))
                }

                SaveExpenseResult.Failed -> {
                    // As Record payment (#76): Save comes back and the form keeps what was typed.
                    this@AddExpenseViewModel.form.update { it.copy(saving = false) }
                    toasts?.show(ToastMessage(text = text.didntSave))
                }

                is SaveExpenseResult.Saved -> {
                    haptics?.saved()
                    val removeExpense = removeExpense
                    // "Added Dinner · you are owed ฿320": the result line's meaning, said once more.
                    toasts?.show(
                        ToastMessage(
                            text = if (expenseId != null) {
                                text.updated
                            } else {
                                listOfNotNull(text.added(result.title), state.meaning).joinToString(Separators.DOT)
                            },
                            // #66: Undo takes back only a new expense; an edit has no version to step back to here.
                            action = if (expenseId == null) ToastAction(AppText.copy.common.undo) { removeExpense(result.expenseId) } else null,
                        ),
                    )
                    saved.value = true
                    onSaved()
                }
            }
        }
    }

    private var removing = false

    /** D8: a removal adds a log entry; your own transaction, if any, stays in History. */
    fun remove(onRemoved: () -> Unit) {
        val id = expenseId ?: return
        val name = (uiState.value as? AddExpenseUiState.Ready)?.form?.description?.trim().orEmpty()
        val restoreExpense = restoreExpense
        if (removing) return
        removing = true
        viewModelScope.launch {
            if (runSuspendCatching { removeExpense(id) }.isFailure) {
                toasts?.show(ToastMessage(text = AppText.copy.common.notDone, tone = ToastTone.Error))
                removing = false
                return@launch
            }
            haptics?.deleted()
            // #197: the shell runs the Undo, because this screen closes as soon as it is removed.
            toasts?.show(
                ToastMessage(
                    text = AppText.copy.shared.expense.removed(name),
                    tone = ToastTone.Deleted,
                    action = ToastAction(AppText.copy.common.undo) { restoreExpense(id) },
                ),
            )
            onRemoved()
        }
    }

    private fun membersOf(snapshot: SharedSnapshot, group: SharedGroup, mode: SplitMode): List<SplitMember> = snapshot.splitMembersFor(group, mode, expenseId)

    private companion object {
        const val DESCRIPTION = "description"
        const val NOTE = "note"
        const val CATEGORY = "category"
        const val SUB = "sub"
        const val RATE = "rate"
        const val MODE = "mode"
        const val WEIGHTS = "weights"
        const val AMOUNT = "amount"
        const val CURRENCY = "currency"
        const val TIME = "time"
        const val PAYER = "payer"
        val FIELDS = listOf(DESCRIPTION, NOTE, CATEGORY, SUB, RATE, MODE, WEIGHTS, AMOUNT, CURRENCY, TIME, PAYER)
    }
}

/** "1234567890123.45": the longest amount an Exact field takes, and plenty of shares. */
private const val MAX_WEIGHT_CHARS = 16

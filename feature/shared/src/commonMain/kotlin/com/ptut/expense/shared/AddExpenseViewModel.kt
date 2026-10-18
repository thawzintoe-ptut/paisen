package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.ExpenseChanged
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.LogOrder
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitCheck
import com.ptut.expense.domain.shared.SplitMember
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.membersToSplit
import com.ptut.expense.domain.shared.parseMinor
import com.ptut.expense.domain.shared.split
import com.ptut.expense.domain.shared.toSatang
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** What the person is typing. Everything shown is worked out from this and the group (#183, #138). */
data class ExpenseForm(
    val currency: Currency = Currencies.Thb,
    val amountText: String = "",
    val rateText: String = "",
    val description: String = "",
    val occurredAtEpochMillis: Long? = null,
    val categoryId: String? = null,
    val note: String = "",
    val payerMemberId: String? = null,
    val splitMode: SplitMode = SplitMode.Equally,
    /** Per member, what was typed in Split options, in the mode's own unit. */
    val weightTexts: Map<String, String> = emptyMap(),
    /** The field edited last, for GIVE TO (p29). */
    val lastEdited: String? = null,
    val splitOpen: Boolean = false,
    val loaded: Boolean = false,
    /** #186 (O4): amount, currency, date, payer and category come from History. The description stays editable. */
    val locked: Boolean = false,
    val subCategoryId: String? = null,
    /** #214: true from the tap until the screen leaves, so two fast taps save one expense. */
    val saving: Boolean = false,
) {
    /** What the person typed or picked, without where the screen is or what it is doing. */
    val content: ExpenseForm get() = copy(lastEdited = null, splitOpen = false, saving = false)
}

/** [detail] is #218's line under a Percent or Shares field: the amount, and a dropped decimal. */
data class SplitRowUiModel(val memberId: String, val name: String, val isMe: Boolean, val text: String, val part: String, val detail: String? = null)

data class SplitUiModel(
    val title: String,
    val mode: SplitMode,
    val rows: List<SplitRowUiModel>,
    /** p29's check card, or null when the split adds up. */
    val leftTitle: String?,
    val leftBody: String?,
    val giveToLabel: String?,
    val progress: Float,
    val complete: Boolean,
)

sealed interface AddExpenseUiState {
    data object Missing : AddExpenseUiState

    data class Ready(
        val form: ExpenseForm,
        val isEdit: Boolean,
        val groupLine: String,
        val currencies: List<Currency>,
        val amountShown: String,
        /** "≈ ฿2,560 at ฿1.38 per 1,000 ₫, saved with the expense", or null for THB. */
        val bahtLine: String?,
        val rateUnitLabel: String?,
        val categories: List<Category>,
        val categoryName: String?,
        val categoryIcon: String?,
        val payers: List<SplitRowUiModel>,
        val payerName: String,
        val splitLabel: String,
        val resultLine: String?,
        val split: SplitUiModel,
        val canSave: Boolean,
        /** #186: why the locked fields can't change here, or null when nothing is locked. */
        val lockedLine: String? = null,
        /** #216: what is missing while Save is dark, or null. */
        val hint: String? = null,
        /** #214: something differs from how the screen opened, so leaving asks first. */
        val changed: Boolean = false,
    ) : AddExpenseUiState
}

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

    /** The form as it opened, so a close can tell whether anything would be lost (#214). */
    private val opened = MutableStateFlow<ExpenseForm?>(null)

    /** The version an edit opened on, so a save sees another phone's change or removal first. */
    private var base: String? = null

    init {
        viewModelScope.launch {
            load()
            opened.value = form.value.content
        }
    }

    val uiState: StateFlow<AddExpenseUiState> = combine(repository.snapshot, form, opened) { snapshot, form, opened ->
        val group = snapshot.group(groupId) ?: return@combine AddExpenseUiState.Missing
        ready(group, form, membersOf(snapshot, group)).copy(changed = opened != null && form.content != opened)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExpenseUiState.Missing)

    /** An edit starts from the latest version; a new expense from You paying, split equally (#183). */
    private suspend fun load() {
        val snapshot = repository.snapshot.first { it.group(groupId) != null }
        val group = snapshot.group(groupId) ?: return
        val existing = latestOf(snapshot)
        val seed = transactionId?.let { repository.transactionSeed(it) }
        if (existing == null && seed != null) {
            form.value = ExpenseForm(
                amountText = formatMinor(seed.amount.toMinorUnits(), Currencies.Thb).filter { it.isDigit() || it == '.' },
                description = seed.description,
                occurredAtEpochMillis = seed.occurredAtEpochMillis,
                categoryId = seed.categoryId ?: categories.firstOrNull()?.id,
                subCategoryId = seed.subCategoryId,
                payerMemberId = group.meMemberId,
                locked = true,
                loaded = true,
            )
            return
        }
        if (existing == null) {
            form.update {
                it.copy(
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
        val currency = Currencies.of(existing.currency)
        form.value = ExpenseForm(
            currency = currency,
            amountText = formatMinor(existing.amountMinor, currency).filter { it.isDigit() || it == '.' },
            rateText = existing.rate?.typedFor(currency.rateUnit).orEmpty(),
            description = existing.description,
            occurredAtEpochMillis = existing.occurredAtEpochMillis,
            categoryId = existing.categoryId,
            note = existing.note.orEmpty(),
            payerMemberId = existing.payerMemberId,
            splitMode = existing.splitMode,
            weightTexts = existing.shares.associate { share ->
                share.memberId to when (existing.splitMode) {
                    SplitMode.Exact -> formatMinor(share.weight, currency).filter { it.isDigit() || it == '.' }
                    SplitMode.Percent -> percentText(share.weight)
                    else -> share.weight.toString()
                }
            },
            loaded = true,
            // O4: a linked expense's amount, date, payer and category change in History only.
            locked = existing.id in snapshot.links,
            subCategoryId = existing.subCategoryId,
        )
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
        if (currency.isBaht) return
        viewModelScope.launch {
            val last = repository.lastRate(currency.code) ?: return@launch
            form.update { if (it.currency == currency && it.rateText.isEmpty()) it.copy(rateText = last.typedFor(currency.rateUnit)) else it }
        }
    }

    fun setDescription(text: String) = form.update { it.copy(description = text) }

    fun setNote(text: String) = form.update { it.copy(note = text) }

    fun setTime(epochMillis: Long) = unlocked { it.copy(occurredAtEpochMillis = epochMillis) }

    fun setCategory(id: String) = unlocked { it.copy(categoryId = id, subCategoryId = null) }

    fun setPayer(memberId: String) = unlocked { it.copy(payerMemberId = memberId) }

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
        val check = check(group, state.form, membersOf(snapshot, group)) ?: return
        val current = weightOf(state.form, target)
        val given = current + check.left
        form.update { it.copy(weightTexts = it.weightTexts + (target to textOf(it, given))) }
    }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value as? AddExpenseUiState.Ready ?: return
        // The form, not uiState: uiState catches up a frame later, and the second tap comes sooner.
        if (!state.canSave || form.value.saving) return
        val form = state.form
        val group = repository.snapshot.value.group(groupId) ?: return
        // Worked out before Save goes dark, so nothing below can leave it dark with no hint.
        val amount = amountOf(form) ?: return
        val payer = form.payerMemberId ?: group.meMemberId ?: return
        this.form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val text = AppText.copy.shared.expense
            try {
                repository.saveExpense(
                    ExpenseDraft(
                        expenseId = expenseId,
                        groupId = groupId,
                        description = form.description,
                        occurredAtEpochMillis = form.occurredAtEpochMillis ?: clock.now().toEpochMilliseconds(),
                        categoryId = form.categoryId,
                        subCategoryId = form.subCategoryId,
                        note = form.note,
                        currency = form.currency.code,
                        amountMinor = amount,
                        rate = rateOf(form),
                        payerMemberId = payer,
                        splitMode = form.splitMode,
                        weights = weightsOf(group, form),
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
            }
            haptics?.saved()
            // "Added Dinner · you are owed ฿320": the result line's meaning, said once more.
            val meaning = state.resultLine?.substringAfterLast(" · ")
            toasts?.show(
                ToastMessage(
                    text = if (expenseId != null) {
                        text.updated
                    } else {
                        listOfNotNull(text.added(form.description.trim()), meaning).joinToString(" · ")
                    },
                ),
            )
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

    /** The people still in the group, and on an edit everyone with a part in the version being edited. */
    private fun membersOf(snapshot: SharedSnapshot, group: SharedGroup): List<SplitMember> =
        group.membersToSplit(keeping = latestOf(snapshot)?.shares.orEmpty().mapTo(mutableSetOf()) { it.memberId })

    /** The newest version of the expense being edited, a removed one too; null for a new expense. */
    private fun latestOf(snapshot: SharedSnapshot): SharedExpense? =
        expenseId?.let { id -> snapshot.expenseVersions.filter { it.groupId == groupId && it.id == id }.maxWithOrNull(LogOrder) }

    private fun ready(group: SharedGroup, form: ExpenseForm, members: List<SplitMember>): AddExpenseUiState.Ready {
        val text = AppText.copy.shared.expense
        val you = AppText.copy.common.you
        val amount = amountOf(form)
        val rate = rateOf(form)
        val baht = amount?.takeIf { form.currency.isBaht || rate != null }?.let { toSatang(it, form.currency, rate) }
        val check = check(group, form, members)
        val payerId = form.payerMemberId ?: group.meMemberId
        val category = categories.firstOrNull { it.id == form.categoryId }
        val unit = form.currency.rateUnit
        val missing = missingFor(form, amount, baht, check)

        return AddExpenseUiState.Ready(
            form = form,
            isEdit = expenseId != null,
            groupLine = text.groupLine(group.name, members.size),
            currencies = Currencies.all,
            amountShown = form.amountText.ifEmpty { "0" },
            bahtLine = if (form.currency.isBaht) {
                null
            } else if (baht == null) {
                text.typeBaht(if (unit == 1L) "1" else formatUnit(unit), form.currency.symbol)
            } else {
                text.bahtAtRate(baht.formatCompact(), form.rateText, if (unit == 1L) form.currency.symbol else "${formatUnit(unit)} ${form.currency.symbol}")
            },
            rateUnitLabel = if (form.currency.isBaht) null else text.ratePer(if (unit == 1L) "1" else formatUnit(unit), form.currency.code),
            categories = categories,
            categoryName = category?.name,
            categoryIcon = category?.icon,
            payers = members.map { SplitRowUiModel(it.id, if (it.isMe) you else it.name, it.isMe, "", "") },
            payerName = payerId?.let { group.displayName(it) } ?: you,
            splitLabel = text.splitLabel(form.splitMode.label(), members.size),
            resultLine = resultLine(group, form, check, baht),
            split = splitModel(group, form, members, check, amount),
            canSave = form.loaded && !form.saving && missing == null,
            lockedLine = if (form.locked) text.locked else null,
            hint = missing.takeIf { form.loaded && !form.saving },
        )
    }

    /** #216: the first thing Save still needs, in the order the form asks for it; null when nothing. */
    private fun missingFor(form: ExpenseForm, amount: Long?, baht: Satang?, check: SplitCheck?): String? {
        val text = AppText.copy.shared.expense
        return when {
            amount == null || amount <= 0 -> text.hintAmount
            baht == null -> text.hintRate
            form.description.isBlank() -> text.hintDescription
            form.categoryId == null -> text.hintCategory
            check?.complete != true -> text.hintSplit
            else -> null
        }
    }

    /** "₫462,500 each · you owe Tom ฿640" (p28): the split, and what it means for you. */
    private fun resultLine(group: SharedGroup, form: ExpenseForm, check: SplitCheck?, baht: Satang?): String? {
        if (check == null || !check.complete || baht == null) return null
        val me = group.meMemberId ?: return null
        val text = AppText.copy.shared
        val payer = form.payerMemberId ?: me
        val parts = check.parts
        val each = parts.values.distinct().singleOrNull()?.let { text.expense.each(formatMinor(it, form.currency)) }
        val myShareMinor = parts[me] ?: 0L
        val amount = amountOf(form) ?: return each
        val myShareBaht = if (amount == 0L) Satang.Zero else toSatang(myShareMinor, form.currency, rateOf(form))
        val meaning = when {
            payer == me && myShareMinor < amount -> text.expense.owedAmount((baht - myShareBaht).formatCompact())
            payer != me && myShareMinor > 0 -> text.expense.oweTo(group.displayName(payer), myShareBaht.formatCompact())
            payer != me -> text.expense.notInThis
            else -> text.allYours
        }
        return listOfNotNull(each, meaning).joinToString(" · ")
    }

    private fun splitModel(group: SharedGroup, form: ExpenseForm, members: List<SplitMember>, check: SplitCheck?, amount: Long?): SplitUiModel {
        val total = amount ?: 0L
        val left = check?.left ?: 0L
        val showCheck = check != null && !check.complete && (form.splitMode == SplitMode.Exact || form.splitMode == SplitMode.Percent)
        val lastName = form.lastEdited?.let { group.displayName(it) }
        val text = AppText.copy.shared.expense
        return SplitUiModel(
            title = text.splitTitle(formatMinor(total, form.currency)),
            mode = form.splitMode,
            rows = members.map { member ->
                SplitRowUiModel(
                    memberId = member.id,
                    name = if (member.isMe) AppText.copy.common.you else member.name,
                    isMe = member.isMe,
                    text = form.weightTexts[member.id].orEmpty(),
                    part = check?.parts?.get(member.id)?.let { formatMinor(it, form.currency) } ?: formatMinor(0, form.currency),
                    detail = weightDetail(form, form.weightTexts[member.id].orEmpty(), check?.parts?.get(member.id) ?: 0L).takeIf { amount != null },
                )
            },
            leftTitle = if (!showCheck) {
                null
            } else if (form.splitMode == SplitMode.Exact) {
                if (left >= 0) text.leftToAssign(formatMinor(left, form.currency)) else text.tooMuch(formatMinor(-left, form.currency))
            } else {
                if (left >= 0) text.leftToAssign("${percentText(left)}%") else text.tooMuch("${percentText(-left)}%")
            },
            leftBody = if (!showCheck) {
                null
            } else if (form.splitMode == SplitMode.Exact) {
                text.splitSoFar(formatMinor(check?.assigned ?: 0, form.currency), formatMinor(total, form.currency))
            } else {
                text.splitSoFar("${percentText(check?.assigned ?: 0)}%", "100%")
            },
            giveToLabel = if (showCheck && lastName != null && left > 0) text.giveTo(lastName) else null,
            progress = when {
                check == null -> 0f
                check.target > 0 -> (check.assigned.coerceAtLeast(0).toFloatRatio(check.target))
                else -> if (check.complete) 1f else 0f
            },
            complete = check?.complete == true,
        )
    }

    /**
     * #218: under a Percent or Shares field, what that person pays ("฿320", or "$12.00 · ≈ ฿410"),
     * and "1.5 counts as 1" when Shares drops a decimal.
     */
    private fun weightDetail(form: ExpenseForm, typed: String, partMinor: Long): String? {
        if (form.splitMode != SplitMode.Percent && form.splitMode != SplitMode.Shares) return null
        val rate = rateOf(form)
        val pays = listOfNotNull(
            formatMinor(partMinor, form.currency),
            rate?.takeIf { !form.currency.isBaht }?.let { "≈ ${toSatang(partMinor, form.currency, it).formatCompact()}" },
        ).joinToString(" · ")
        val dropped = form.splitMode == SplitMode.Shares && typed.substringAfter('.', "").any { it != '0' }
        val whole = (typed.substringBefore('.').toLongOrNull() ?: 0L).toString()
        return if (dropped) "${AppText.copy.shared.expense.countsAs(typed, whole)} · $pays" else pays
    }

    private fun check(group: SharedGroup, form: ExpenseForm, members: List<SplitMember>): SplitCheck? {
        val amount = amountOf(form) ?: return null
        return split(amount, form.splitMode, members, weightsOf(group, form))
    }

    private fun weightsOf(group: SharedGroup, form: ExpenseForm): Map<String, Long> =
        group.members.associate { it.id to weightOf(form, it.id) }.filterValues { it > 0 }

    private fun weightOf(form: ExpenseForm, memberId: String): Long {
        val text = form.weightTexts[memberId].orEmpty()
        return when (form.splitMode) {
            SplitMode.Equally -> 1L
            SplitMode.Exact -> parseMinor(text, form.currency) ?: 0L
            SplitMode.Percent -> parsePercent(text) ?: 0L
            SplitMode.Shares -> text.substringBefore('.').toLongOrNull() ?: 0L
        }
    }

    private fun textOf(form: ExpenseForm, weight: Long): String = when (form.splitMode) {
        SplitMode.Exact -> formatMinor(weight, form.currency).filter { it.isDigit() || it == '.' }
        SplitMode.Percent -> percentText(weight)
        else -> weight.toString()
    }

    private fun amountOf(form: ExpenseForm): Long? = parseMinor(form.amountText, form.currency)

    private fun rateOf(form: ExpenseForm): Rate? =
        if (form.currency.isBaht) null else Rate.parse(form.rateText, form.currency.rateUnit)
}

/** D11: two decimals, as basis points. "33.33" is 3,333. */
internal fun parsePercent(text: String): Long? {
    val clean = text.trim()
    if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }) return null
    val whole = clean.substringBefore('.').ifEmpty { "0" }
    val fraction = clean.substringAfter('.', "")
    if (fraction.length > 2 || whole.length > 3) return null
    return whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
}

internal fun percentText(basisPoints: Long): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return if (fraction == 0L) "$whole" else "$whole.${fraction.toString().padStart(2, '0')}".trimEnd('0')
}

private fun formatUnit(unit: Long): String = unit.toString().reversed().chunked(3).joinToString(",").reversed()

/** A progress bar's fill, the one place a count meets a float: nothing spendable comes back out. */
private fun Long.toFloatRatio(of: Long): Float = if (of <= 0L) 0f else (this * PROGRESS_STEPS / of).coerceIn(0L, PROGRESS_STEPS) / PROGRESS_STEPS_F

private const val PROGRESS_STEPS = 1_000L

/** "1234567890123.45": the longest amount an Exact field takes, and plenty of shares. */
private const val MAX_WEIGHT_CHARS = 16
private const val PROGRESS_STEPS_F = 1_000f

fun SplitMode.label(): String {
    val text = AppText.copy.shared
    return when (this) {
        SplitMode.Equally -> text.modeEqually
        SplitMode.Exact -> text.modeExact
        SplitMode.Percent -> text.modePercent
        SplitMode.Shares -> text.modeShares
    }
}

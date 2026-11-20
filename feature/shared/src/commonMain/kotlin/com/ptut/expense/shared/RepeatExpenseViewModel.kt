package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.LoadRepeatDraftUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.RepeatEnd
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatInput
import com.ptut.expense.domain.shared.RepeatKind
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SaveRepeatUseCase
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.StopRepeatUseCase
import com.ptut.expense.domain.shared.equalPartOf
import com.ptut.expense.domain.shared.membersToSplit
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.next
import com.ptut.expense.domain.shared.parseMinor
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** What is being set up. The split is equal among today's members, as p33's "split 50 / 50" draws it. */
data class RepeatForm(
    val kind: RepeatKind = RepeatKind.Rent,
    val description: String = RepeatKind.Rent.label(),
    val amountText: String = "",
    val frequency: RepeatFrequency = RepeatFrequency.Monthly,
    val start: LocalDate? = null,
    val end: RepeatEnd = RepeatEnd.Never,
    val remind: Boolean = true,
    val payerMemberId: String? = null,
    val loaded: Boolean = false,
    /** #214: true from the tap until the screen leaves, so two fast taps save one repeat. */
    val saving: Boolean = false,
) {
    /** What the person can change, so a back can tell whether anything would be lost (#6). */
    val content: RepeatForm get() = copy(saving = false)
}

data class RepeatDateUiModel(val date: String, val meaning: String)

sealed interface RepeatExpenseUiState {
    data object Missing : RepeatExpenseUiState

    data class Ready(
        val form: RepeatForm,
        val isEdit: Boolean,
        val active: Boolean,
        /** "Rent · Ari flat" (p33). */
        val title: String,
        /** #48: the group's own money, drawn before the amount. */
        val symbol: String,
        val amountShown: String,
        /** "You paid · split 50 / 50 with Mint". */
        val payLine: String,
        val payers: List<SplitRowUiModel>,
        val anchorLine: String,
        val endLine: String,
        val next: List<RepeatDateUiModel>,
        /** "Save · repeats monthly": the label follows the frequency. */
        val saveLabel: String,
        val canSave: Boolean,
        /** Today in Bangkok: a repeat starts no earlier, or saving would add dates already gone. */
        val earliestStart: LocalDate,
        /** #6: something differs from how the screen opened, so leaving asks first. */
        val changed: Boolean = false,
    ) : RepeatExpenseUiState
}

/**
 * #185 (p33): set up rent, a bill or a subscription that adds itself. The NEXT THREE card shows
 * the dates #182 will add and what each means for you, so the repeat is predictable before it is
 * saved. Opened from the group's "···" menu, and from a repeat's row on the group screen to edit it.
 */
class RepeatExpenseViewModel(
    private val groupId: String,
    private val ruleId: String?,
    private val categories: List<Category>,
    private val toasts: ToastMessages,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val loadDraft: LoadRepeatDraftUseCase,
    private val homeToday: GetHomeTodayUseCase,
    private val saveRepeat: SaveRepeatUseCase,
    private val stopRepeat: StopRepeatUseCase,
) : ViewModel() {

    private val snapshot = observeSnapshot()

    private val form = MutableStateFlow(RepeatForm())
    private val opened = MutableStateFlow<RepeatForm?>(null)
    private val done = MutableStateFlow(false)

    /** True once the repeat is saved or stopped; the shell leaves on this. */
    val isDone: StateFlow<Boolean> = done.asStateFlow()

    val uiState: StateFlow<RepeatExpenseUiState> = combine(snapshot, form, opened) { snapshot, form, opened ->
        val group = snapshot.group(groupId) ?: return@combine RepeatExpenseUiState.Missing
        ready(group, form, snapshot.repeats.firstOrNull { it.id == ruleId }?.active ?: true)
            .copy(changed = opened != null && form.content != opened)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), RepeatExpenseUiState.Missing)

    init {
        viewModelScope.launch {
            load()
            opened.value = form.value.content
        }
    }

    private suspend fun load() {
        val (group, rule, firstOfNextMonth) = loadDraft(groupId, ruleId) ?: return
        form.value = if (rule == null) {
            RepeatForm(start = firstOfNextMonth, payerMemberId = group.meMemberId, loaded = true)
        } else {
            RepeatForm(
                kind = rule.kind,
                description = rule.description,
                amountText = group.money.typedText(rule.amountMinor),
                frequency = rule.frequency,
                start = rule.start,
                end = rule.end,
                remind = rule.remind,
                payerMemberId = rule.payerMemberId,
                loaded = true,
            )
        }
    }

    fun setKind(kind: RepeatKind) = form.update { it.copy(kind = kind, description = kind.label()) }

    fun setAmount(text: String) = form.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) }

    fun setFrequency(frequency: RepeatFrequency) = form.update { it.copy(frequency = frequency) }

    fun setStart(date: LocalDate) = form.update { it.copy(start = date) }

    fun setEnd(end: RepeatEnd) = form.update { it.copy(end = end) }

    fun setRemind(on: Boolean) = form.update { it.copy(remind = on) }

    fun setPayer(memberId: String) = form.update { it.copy(payerMemberId = memberId) }

    /**
     * [allowed] is the notification answer the screen got before calling (#182): a refusal still
     * saves the repeat, with the reminder off and a line saying why.
     */
    fun save(allowed: Boolean) {
        val state = uiState.value as? RepeatExpenseUiState.Ready ?: return
        // The form, not uiState: uiState catches up a frame later, and the second tap comes sooner.
        if (!state.canSave || form.value.saving) return
        val group = snapshot.value.group(groupId) ?: return
        val form = state.form
        val remind = form.remind && allowed
        val start = form.start ?: return
        val amountMinor = parseMinor(form.amountText, group.money) ?: return
        val payer = form.payerMemberId ?: group.meMemberId ?: return
        val input = RepeatInput(
            ruleId = ruleId,
            groupId = groupId,
            kind = form.kind,
            frequency = form.frequency,
            start = start,
            end = form.end,
            remind = remind,
            description = form.description,
            amountMinor = amountMinor,
            payerMemberId = payer,
            categories = categories,
        )
        this.form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = saveRepeat(input) {
                if (form.remind && !allowed) toasts.show(ToastMessage(text = AppText.copy.shared.repeat.noReminder))
            }
            if (saved.isFailure) {
                // The form stays as typed and Save unlocks, so a second tap can try again.
                toasts.show(ToastMessage(text = AppText.copy.shared.repeat.didntSave, tone = ToastTone.Error))
                this@RepeatExpenseViewModel.form.update { it.copy(saving = false) }
                return@launch
            }
            done.value = true
        }
    }

    fun stop() {
        val id = ruleId ?: return
        viewModelScope.launch {
            stopRepeat(id)
            done.value = true
        }
    }

    private fun ready(group: SharedGroup, form: RepeatForm, active: Boolean): RepeatExpenseUiState.Ready {
        val members = group.membersToSplit()
        val amount = parseMinor(form.amountText, group.money)
        val payer = form.payerMemberId ?: group.meMemberId
        val preview = form.start?.let { start ->
            RepeatRule(
                id = "", groupId = groupId, kind = form.kind, frequency = form.frequency, start = start, end = form.end,
                remind = form.remind, active = true, lastAdded = null, description = form.description, categoryId = null,
                currency = group.currency, amountMinor = amount ?: 0L, rate = null, payerMemberId = payer.orEmpty(),
                splitMode = SplitMode.Equally, weights = members.associate { it.id to 1L }, createdAtEpochMillis = 0L,
            )
        }
        val today = homeToday()
        val text = AppText.copy.shared
        val you = AppText.copy.common.you
        return RepeatExpenseUiState.Ready(
            form = form,
            isEdit = ruleId != null,
            active = active,
            title = "${form.description.ifBlank { form.kind.label() }}${Separators.DOT}${group.name}",
            symbol = group.money.symbol,
            amountShown = form.amountText.ifEmpty { "0" },
            payLine = buildString {
                append(if (payer == null || group.isMe(payer)) text.repeat.youPaid else text.repeat.paid(group.displayName(payer)))
                append(Separators.DOT)
                // The person who did not pay: from Mali's phone, Thaw paying splits "with you".
                val partner = members.singleOrNull { it.id != payer }
                append(
                    when {
                        members.size == 1 -> text.allYours
                        members.size == 2 && partner != null -> text.repeat.splitHalf(if (partner.isMe) you else partner.name)
                        else -> text.repeat.splitEqually(members.size)
                    },
                )
            },
            payers = members.map { SplitRowUiModel(it.id, if (it.isMe) you else it.name, it.isMe, "", "") },
            anchorLine = preview?.anchorLine() ?: text.repeat.pickADay,
            endLine = when (val end = form.end) {
                RepeatEnd.Never -> text.repeat.never
                is RepeatEnd.After -> text.repeat.afterTimes(end.times)
                is RepeatEnd.OnDate -> text.repeat.onDate(end.last.dayLabel())
            },
            next = preview?.takeIf { amount != null && amount > 0 }?.next(today)?.map { date ->
                RepeatDateUiModel(date = date.dayLabel(), meaning = meaningFor(group, preview, amount ?: 0L))
            }.orEmpty(),
            saveLabel = text.repeat.save(form.frequency.word()),
            canSave = form.loaded && !form.saving && amount != null && amount > 0 && form.description.isNotBlank() && form.start != null && members.isNotEmpty(),
            earliestStart = today,
        )
    }

    /** "Mint owes you ฿9,000", or what you owe the payer: the same for every date of a fixed amount. */
    private fun meaningFor(group: SharedGroup, rule: RepeatRule, amount: Long): String {
        val mine = group.equalPartOf(amount, group.meMemberId)
        val others = group.membersToSplit().filterNot { it.isMe }
        val text = AppText.copy.shared.repeat
        val money = group.moneyFormat()
        return when {
            group.isMe(rule.payerMemberId) && others.size == 1 -> text.owesYou(others.single().name, (Satang.ofMinorUnits(amount) - mine).formatCompact(money))
            group.isMe(rule.payerMemberId) -> text.youAreOwed((Satang.ofMinorUnits(amount) - mine).formatCompact(money))
            else -> text.youOwe(group.displayName(rule.payerMemberId), mine.formatCompact(money))
        }
    }
}

fun RepeatKind.label(): String {
    val text = AppText.copy.shared.repeat
    return when (this) {
        RepeatKind.Rent -> text.kindRent
        RepeatKind.Utilities -> text.kindUtilities
        RepeatKind.Internet -> text.kindInternet
        RepeatKind.Subscription -> text.kindSubscription
    }
}

/** p33's chips set a name and an icon; the icon is the category's, so each kind files under one. */
fun RepeatKind.icon(): String = when (this) {
    RepeatKind.Rent -> "home"
    RepeatKind.Utilities -> "bolt"
    RepeatKind.Internet -> "wifi"
    RepeatKind.Subscription -> "sync"
}

fun RepeatFrequency.label(): String {
    val text = AppText.copy.shared.repeat
    return when (this) {
        RepeatFrequency.Weekly -> text.freqWeekly
        RepeatFrequency.TwoWeeks -> text.freqTwoWeeks
        RepeatFrequency.Monthly -> text.freqMonthly
        RepeatFrequency.Yearly -> text.freqYearly
    }
}

private fun RepeatFrequency.word(): String {
    val text = AppText.copy.shared.repeat
    return when (this) {
        RepeatFrequency.Weekly -> text.weekly
        RepeatFrequency.TwoWeeks -> text.everyTwoWeeks
        RepeatFrequency.Monthly -> text.monthly
        RepeatFrequency.Yearly -> text.yearly
    }
}

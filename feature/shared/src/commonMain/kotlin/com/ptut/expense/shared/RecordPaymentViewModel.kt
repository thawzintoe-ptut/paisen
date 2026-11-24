package com.ptut.expense.shared

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.GetPaymentBetweenUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.PaymentBetween
import com.ptut.expense.domain.shared.PaymentInput
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.RecordPaymentUseCase
import com.ptut.expense.domain.shared.RemovePaymentUseCase
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.parseMinor
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.domain.usage.UsageEvent
import com.ptut.expense.domain.usage.UsageLog
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentForm(
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.BankTransfer,
    val occurredAtEpochMillis: Long? = null,
    val loaded: Boolean = false,
    /** #214: true from the tap until the screen leaves, so two fast taps record one payment. */
    val saving: Boolean = false,
    /** #76: the last save threw; what was typed stays, and the button retries. */
    val failed: Boolean = false,
) {
    /** What the person can change, so a close can tell whether anything would be lost (#6). */
    val content: PaymentForm get() = copy(saving = false, failed = false)
}

/** The share sheet's button and text when the money comes to you: "Ask Thaw to pay". */
data class AskToPayUiModel(val label: String, val message: String)

sealed interface RecordPaymentUiState {
    data object Missing : RecordPaymentUiState

    data class Ready(
        val form: PaymentForm,
        val fromName: String,
        val fromLabel: String,
        val fromIsMe: Boolean,
        val toName: String,
        val toLabel: String,
        val toIsMe: Boolean,
        val groupName: String,
        /** #48: the group's own money, drawn before the amount. */
        val symbol: String,
        val fullBalanceLine: String,
        val afterThis: String,
        val buttonLabel: String,
        val canSave: Boolean,
        /** #199: the PromptPay QR's text when you pay someone who added an ID, else null. */
        val promptPayQr: String? = null,
        /** "Pay Mali by PromptPay ••••••5678". */
        val promptPayLine: String? = null,
        val askToPay: AskToPayUiModel? = null,
        /** #6: something differs from how the screen opened, so leaving asks first. */
        val changed: Boolean = false,
        /** #76: why the last save didn't land, or null. */
        val failedTitle: String? = null,
        val failedBody: String? = null,
    ) : RecordPaymentUiState
}

/**
 * #139 (p31): a payment adds a record and never edits an expense. The full balance between the
 * two is filled in, so paying part of it takes one tap. No money moves through PaiSen.
 */
class RecordPaymentViewModel(
    private val groupId: String,
    private val fromMemberId: String,
    private val toMemberId: String,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val getPaymentBetween: GetPaymentBetweenUseCase,
    private val recordPayment: RecordPaymentUseCase,
    private val removePayment: RemovePaymentUseCase,
    /** #197: a word, a haptic and Undo, as a v1 save gives. Null in a screen test's graph. */
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
    /** #115: a count per payment recorded. */
    private val usage: UsageLog = UsageLog.None,
    /** #93: the settle plan's amount ("350.00"); it wins over what is owed. */
    private val prefillAmount: String? = null,
    /** The half-typed payment, kept through Android killing the process. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val snapshot = observeSnapshot()

    private val form = MutableStateFlow(PaymentForm())
    private val opened = MutableStateFlow<PaymentForm?>(null)
    private val saved = MutableStateFlow(false)

    /** True once the payment is recorded; the shell leaves on this. */
    val isSaved: StateFlow<Boolean> = saved.asStateFlow()

    init {
        viewModelScope.launch {
            val (group, owed) = between(snapshot.first { it.group(groupId) != null }) ?: return@launch
            val owedText = if (owed > Satang.Zero) plain(owed, group) else ""
            form.update { it.copy(amountText = prefillAmount?.filter { c -> c.isDigit() || c == '.' } ?: owedText, loaded = true) }
            opened.value = form.value.content
            restoreDraft()
            combine(form, saved) { form, done -> form.content.takeUnless { done || it == opened.value } }.collect(::keepDraft)
        }
    }

    private fun key(field: String) = "payment.$groupId.$fromMemberId.$toMemberId.$field"

    /** After the owed amount is filled, so it never overwrites what was typed. */
    private fun restoreDraft() {
        val amount = savedState.get<String>(key(AMOUNT)) ?: return
        form.update { current ->
            current.copy(
                amountText = amount,
                method = PaymentMethod.entries.firstOrNull { it.name == savedState.get<String>(key(METHOD)) } ?: current.method,
                occurredAtEpochMillis = savedState.get<Long>(key(TIME)),
            )
        }
    }

    private fun keepDraft(form: PaymentForm?) {
        if (form == null) {
            listOf(AMOUNT, METHOD, TIME).forEach { savedState.remove<Any>(key(it)) }
            return
        }
        savedState[key(AMOUNT)] = form.amountText
        savedState[key(METHOD)] = form.method.name
        savedState[key(TIME)] = form.occurredAtEpochMillis
    }

    val uiState: StateFlow<RecordPaymentUiState> = combine(snapshot, form, opened) { snapshot, form, opened ->
        val between = between(snapshot) ?: return@combine RecordPaymentUiState.Missing
        val (group, owed) = between
        val paying = amountOf(form, group)
        val fromName = group.displayName(fromMemberId)
        val toName = group.displayName(toMemberId)
        val left = owed - (paying ?: Satang.Zero)
        val money = group.moneyFormat()
        val text = AppText.copy.shared.payment
        RecordPaymentUiState.Ready(
            form = form,
            fromName = fromName,
            fromLabel = group.member(fromMemberId)?.initial().orEmpty(),
            fromIsMe = group.isMe(fromMemberId),
            toName = toName,
            toLabel = group.member(toMemberId)?.initial().orEmpty(),
            toIsMe = group.isMe(toMemberId),
            groupName = group.name,
            symbol = money.symbol,
            fullBalanceLine = if (owed > Satang.Zero) text.fullBalance else text.nothingOwed,
            afterThis = when {
                left == Satang.Zero -> if (group.isMe(fromMemberId)) text.youAreSettled else text.isSettled(fromName)
                left > Satang.Zero -> text.stillOwes(fromName, toName, left.formatCompact(money))
                else -> text.owes(toName, fromName, (-left).formatCompact(money))
            },
            // 44c: the button keeps its amount after a failure; Retry sits in the error itself.
            buttonLabel = text.record((paying ?: Satang.Zero).formatCompact(money)),
            canSave = form.loaded && !form.saving && paying != null && paying > Satang.Zero,
            promptPayQr = between.payeePromptPay?.takeIf { paying != null && paying > Satang.Zero }?.let { PromptPay.payload(it, paying ?: Satang.Zero) },
            promptPayLine = between.payeePromptPay?.let { text.payBy(toName, PromptPay.masked(it)) },
            askToPay = askToPay(between, fromName),
            changed = opened != null && form.content != opened,
            failedTitle = if (form.failed) text.didntSave((paying ?: Satang.Zero).formatCompact(money)) else null,
            failedBody = if (form.failed) text.keptTyped else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), RecordPaymentUiState.Missing)

    fun setAmount(text: String) = form.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) }

    fun setMethod(method: PaymentMethod) = form.update { it.copy(method = method) }

    fun setTime(epochMillis: Long) = form.update { it.copy(occurredAtEpochMillis = epochMillis) }

    fun record(onDone: () -> Unit = {}) {
        val state = uiState.value as? RecordPaymentUiState.Ready ?: return
        // The form, not uiState: uiState catches up a frame later, and the second tap comes sooner.
        if (!state.canSave || form.value.saving) return
        val group = snapshot.value.group(groupId) ?: return
        val amount = amountOf(state.form, group) ?: return
        val removePayment = removePayment
        form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val input = PaymentInput(groupId, fromMemberId, toMemberId, amount, state.form.method, state.form.occurredAtEpochMillis)
            val id = recordPayment(input).getOrElse {
                form.update { it.copy(saving = false, failed = true) }
                return@launch
            }
            haptics?.saved()
            usage.log(UsageEvent.PaymentRecorded)
            toasts?.show(
                ToastMessage(
                    text = AppText.copy.shared.payment.recorded,
                    action = ToastAction(AppText.copy.common.undo) { removePayment(id) },
                ),
            )
            saved.value = true
            onDone()
        }
    }

    /** Only when the money comes to you and something is still owed. */
    private fun askToPay(between: PaymentBetween, fromName: String): AskToPayUiModel? {
        if (!between.canAskToPay) return null
        val group = between.group
        val text = AppText.copy.shared.payment
        val promptPay = between.askPromptPay?.let { "\n" + text.askPromptPay(it) }.orEmpty()
        return AskToPayUiModel(label = text.askToPay(fromName), message = text.askMessage(group.name, fromName, between.owed.format(group.moneyFormat())) + promptPay)
    }

    private fun between(snapshot: SharedSnapshot): PaymentBetween? = getPaymentBetween(snapshot, groupId, fromMemberId, toMemberId)

    private fun amountOf(form: PaymentForm, group: SharedGroup): Satang? = parseMinor(form.amountText, group.money)?.let { Satang.ofMinorUnits(it) }

    private fun plain(amount: Satang, group: SharedGroup): String = group.money.typedText(amount.toMinorUnits())

    private companion object {
        const val AMOUNT = "amount"
        const val METHOD = "method"
        const val TIME = "time"
    }
}

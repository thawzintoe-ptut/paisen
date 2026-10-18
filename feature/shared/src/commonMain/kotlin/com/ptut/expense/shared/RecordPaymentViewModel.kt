package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.parseMinor
import com.ptut.expense.domain.shared.whoPaysWhom
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class PaymentForm(
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.BankTransfer,
    val occurredAtEpochMillis: Long? = null,
    val loaded: Boolean = false,
    /** #214: true from the tap until the screen leaves, so two fast taps record one payment. */
    val saving: Boolean = false,
)

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
        val fullBalanceLine: String,
        val afterThis: String,
        val buttonLabel: String,
        val canSave: Boolean,
        /** #199: the PromptPay QR's text when you pay someone who added an ID, else null. */
        val promptPayQr: String? = null,
        /** "Pay Mali by PromptPay ••••••5678". */
        val promptPayLine: String? = null,
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
    private val repository: SharedRepository,
    private val clock: Clock = Clock.System,
    /** #197: a word, a haptic and Undo, as a v1 save gives. Null in a screen test's graph. */
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
) : ViewModel() {

    private val form = MutableStateFlow(PaymentForm())

    init {
        viewModelScope.launch {
            val snapshot = repository.snapshot.first { it.group(groupId) != null }
            val owed = owedBetween(snapshot)
            form.update { it.copy(amountText = if (owed > Satang.Zero) plain(owed) else "", loaded = true) }
        }
    }

    val uiState: StateFlow<RecordPaymentUiState> = combine(repository.snapshot, form) { snapshot, form ->
        val group = snapshot.group(groupId) ?: return@combine RecordPaymentUiState.Missing
        val owed = owedBetween(snapshot)
        val paying = amountOf(form)
        val fromName = group.displayName(fromMemberId)
        val toName = group.displayName(toMemberId)
        val left = owed - (paying ?: Satang.Zero)
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
            fullBalanceLine = if (owed > Satang.Zero) text.fullBalance else text.nothingOwed,
            afterThis = when {
                left == Satang.Zero -> if (group.isMe(fromMemberId)) text.youAreSettled else text.isSettled(fromName)
                left > Satang.Zero -> text.stillOwes(fromName, toName, left.formatCompact())
                else -> text.owes(toName, fromName, (-left).formatCompact())
            },
            buttonLabel = text.record((paying ?: Satang.Zero).formatCompact()),
            canSave = form.loaded && !form.saving && paying != null && paying > Satang.Zero,
            promptPayQr = payeePromptPay(group)?.takeIf { paying != null && paying > Satang.Zero }?.let { PromptPay.payload(it, paying ?: Satang.Zero) },
            promptPayLine = payeePromptPay(group)?.let { text.payBy(toName, PromptPay.masked(it)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordPaymentUiState.Missing)

    fun setAmount(text: String) = form.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) }

    fun setMethod(method: PaymentMethod) = form.update { it.copy(method = method) }

    fun setTime(epochMillis: Long) = form.update { it.copy(occurredAtEpochMillis = epochMillis) }

    fun record(onDone: () -> Unit) {
        val state = uiState.value as? RecordPaymentUiState.Ready ?: return
        // The form, not uiState: uiState catches up a frame later, and the second tap comes sooner.
        if (!state.canSave || form.value.saving) return
        val amount = amountOf(state.form) ?: return
        val repository = repository
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = repository.recordPayment(
                PaymentDraft(
                    groupId = groupId,
                    fromMemberId = fromMemberId,
                    toMemberId = toMemberId,
                    amount = amount,
                    method = state.form.method,
                    occurredAtEpochMillis = state.form.occurredAtEpochMillis ?: clock.now().toEpochMilliseconds(),
                ),
            )
            haptics?.saved()
            toasts?.show(
                ToastMessage(
                    text = AppText.copy.shared.payment.recorded,
                    action = ToastAction(AppText.copy.common.undo) { repository.removePayment(id) },
                ),
            )
            onDone()
        }
    }

    /** #199: only when you are the one paying; a QR to pay yourself would be no use. */
    private fun payeePromptPay(group: SharedGroup): String? =
        group.member(toMemberId)?.promptPayId?.takeIf { group.isMe(fromMemberId) }

    /** What [fromMemberId] owes [toMemberId] in the payments the group would settle with (#135). */
    private fun owedBetween(snapshot: SharedSnapshot): Satang =
        snapshot.whoPaysWhom(groupId).transfers
            .filter { it.fromMemberId == fromMemberId && it.toMemberId == toMemberId }
            .fold(Satang.Zero) { total, transfer -> total + transfer.amount }

    private fun amountOf(form: PaymentForm): Satang? = parseMinor(form.amountText, Currencies.Thb)?.let { Satang.ofMinorUnits(it) }

    private fun plain(amount: Satang): String = amount.format().drop(1).replace(",", "")
}

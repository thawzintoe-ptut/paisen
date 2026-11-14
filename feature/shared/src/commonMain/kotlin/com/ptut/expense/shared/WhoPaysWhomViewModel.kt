package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.currenciesOf
import com.ptut.expense.domain.shared.whoPaysWhom
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentCardUiModel(
    val fromId: String,
    val toId: String,
    val fromLabel: String,
    val fromIsMe: Boolean,
    val toLabel: String,
    val toIsMe: Boolean,
    val sentence: String,
    val amount: String,
    val tone: BalanceTone,
    /** The payer's name, for "Ask Aye to pay" on a debt owed to me. */
    val fromName: String = "",
    /** The card to send. Null on a debt that is not owed to me, and until my PromptPay ID is saved. */
    val payMe: PayMeCard? = null,
)

sealed interface WhoPaysWhomUiState {
    data object Missing : WhoPaysWhomUiState

    data class Loaded(
        val groupName: String,
        val headline: String,
        val pairwiseCount: Int,
        val simplifiedCount: Int,
        val simplify: Boolean,
        val cards: List<PaymentCardUiModel>,
        /** p30's note, when the group used more than baht. */
        val currencyNote: String?,
        /** SETTLE A PAYMENT opens the first card that involves you. */
        val firstMine: PaymentCardUiModel?,
    ) : WhoPaysWhomUiState
}

/** #184: the payments that settle one group, from #135, and the switch back to the original pairings. */
class WhoPaysWhomViewModel(private val groupId: String, private val repository: SharedRepository) : ViewModel() {

    val uiState: StateFlow<WhoPaysWhomUiState> = repository.snapshot.map { snapshot ->
        val group = snapshot.group(groupId) ?: return@map WhoPaysWhomUiState.Missing
        val settlement = snapshot.whoPaysWhom(groupId)
        val text = AppText.copy.shared.settle
        val you = AppText.copy.common.you
        val cards = settlement.transfers.map { transfer ->
            val from = group.displayName(transfer.fromMemberId)
            val to = group.displayName(transfer.toMemberId)
            PaymentCardUiModel(
                fromId = transfer.fromMemberId,
                toId = transfer.toMemberId,
                fromLabel = if (group.isMe(transfer.fromMemberId)) you else group.member(transfer.fromMemberId)?.initial().orEmpty(),
                fromIsMe = group.isMe(transfer.fromMemberId),
                toLabel = if (group.isMe(transfer.toMemberId)) you else group.member(transfer.toMemberId)?.initial().orEmpty(),
                toIsMe = group.isMe(transfer.toMemberId),
                sentence = when {
                    group.isMe(transfer.toMemberId) -> text.paysYou(from)
                    group.isMe(transfer.fromMemberId) -> text.youPay(to)
                    else -> text.pays(from, to)
                },
                amount = transfer.amount.format(),
                fromName = from,
                payMe = payMeCard(group, transfer),
                tone = when {
                    group.isMe(transfer.toMemberId) -> BalanceTone.Owed
                    group.isMe(transfer.fromMemberId) -> BalanceTone.Owe
                    else -> BalanceTone.Settled
                },
            )
        }
        val foreign = snapshot.currenciesOf(groupId).filter { it != Currencies.Thb.code }
        WhoPaysWhomUiState.Loaded(
            groupName = group.name,
            headline = if (cards.isEmpty()) text.allSettled else text.payments(cards.size),
            pairwiseCount = settlement.pairwiseCount,
            simplifiedCount = settlement.simplifiedCount,
            simplify = group.simplifyDebts,
            cards = cards,
            currencyNote = foreign.takeIf { it.isNotEmpty() }?.let(text.converted),
            firstMine = cards.firstOrNull { it.fromIsMe || it.toIsMe },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoPaysWhomUiState.Missing)

    /** True when [text] is a PromptPay ID and was saved for my name in this group, as on the group's own sheet. */
    fun setPromptPay(text: String): Boolean {
        val id = PromptPay.normalize(text) ?: return false
        viewModelScope.launch { repository.setPromptPay(groupId, id) }
        return true
    }

    fun setSimplify(on: Boolean) {
        viewModelScope.launch { repository.setSimplifyDebts(groupId, on) }
    }
}

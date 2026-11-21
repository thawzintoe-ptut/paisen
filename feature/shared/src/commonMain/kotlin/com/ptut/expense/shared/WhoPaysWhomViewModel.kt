package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.GetSettlementUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SavePromptPayUseCase
import com.ptut.expense.domain.shared.SetSimplifyDebtsUseCase
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.SyncHealth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
        /** p30's note, when the group used more than its own money. */
        val currencyNote: String?,
        /** SETTLE A PAYMENT opens the first card that involves you. */
        val firstMine: PaymentCardUiModel?,
        /** #93: the plan, while Simplify debts is on and this phone is up to date (D7). */
        val plan: SettlePlanUiModel? = null,
    ) : WhoPaysWhomUiState
}

/**
 * #93 (p14): how few payments settle everyone. My part first with its badge, the rest neutral in the
 * order the group settles them; nobody is ranked (R7).
 */
data class SettlePlanUiModel(
    val headline: String,
    val savesLine: String,
    val badge: String,
    val cards: List<PaymentCardUiModel>,
    /** What I pay, or null when I pay nothing. */
    val myPart: PaymentCardUiModel?,
    /** Record payment's prefill, as the routes carry amounts ("350.00"). */
    val myAmount: String?,
    val settleMyPart: String,
)

/** #184: the payments that settle one group, from #135, and the switch back to the original pairings. */
class WhoPaysWhomViewModel(
    private val groupId: String,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val getSettlement: GetSettlementUseCase,
    private val savePromptPay: SavePromptPayUseCase,
    private val setSimplifyDebts: SetSimplifyDebtsUseCase,
    /** Where the snapshot is mapped; tests hand in their own. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
    /** #93: the group's sync health; a phone that is behind shows no plan. */
    observeSyncStatus: ObserveSyncStatusUseCase = ObserveSyncStatusUseCase(),
    /** #93: Play and its settle plan are on, read when the screen is built. */
    private val planOn: Boolean = false,
) : ViewModel() {

    val uiState: StateFlow<WhoPaysWhomUiState> = combine(observeSnapshot(), observeSyncStatus(groupId)) { snapshot, status ->
        val (group, settlement, foreign) = getSettlement(snapshot, groupId) ?: return@combine WhoPaysWhomUiState.Missing
        val text = AppText.copy.shared.settle
        val you = AppText.copy.common.you
        val money = group.moneyFormat()
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
                amount = transfer.amount.format(money),
                fromName = from,
                payMe = payMeCard(group, transfer),
                tone = when {
                    group.isMe(transfer.toMemberId) -> BalanceTone.Owed
                    group.isMe(transfer.fromMemberId) -> BalanceTone.Owe
                    else -> BalanceTone.Settled
                },
            )
        }
        WhoPaysWhomUiState.Loaded(
            groupName = group.name,
            headline = if (cards.isEmpty()) text.allSettled else text.payments(cards.size),
            pairwiseCount = settlement.pairwiseCount,
            simplifiedCount = settlement.simplifiedCount,
            simplify = group.simplifyDebts,
            cards = cards,
            currencyNote = foreign.takeIf { it.isNotEmpty() }?.let { text.converted(it, group.currency) },
            firstMine = cards.firstOrNull { it.fromIsMe || it.toIsMe },
            plan = if (planOn && group.simplifyDebts && status.health in UP_TO_DATE) plan(cards, settlement.pairwiseCount, settlement.transfers.firstOrNull { group.isMe(it.fromMemberId) }?.amount?.let { group.money.typedText(it.toMinorUnits()) }) else null,
        )
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), WhoPaysWhomUiState.Missing)

    /** True when [text] is a PromptPay ID and was saved for my name in this group, as on the group's own sheet. */
    fun setPromptPay(text: String): Boolean {
        val id = PromptPay.normalize(text) ?: return false
        viewModelScope.launch { savePromptPay(groupId, id) }
        return true
    }

    fun setSimplify(on: Boolean) {
        viewModelScope.launch { setSimplifyDebts(groupId, on) }
    }

    private fun plan(cards: List<PaymentCardUiModel>, pairwise: Int, myAmount: String?): SettlePlanUiModel? {
        if (cards.isEmpty()) return null
        val text = AppText.copy.play.group
        val mine = cards.firstOrNull { it.fromIsMe }
        return SettlePlanUiModel(
            headline = text.planHeadline(cards.size),
            savesLine = if (pairwise > cards.size) text.planSaves(pairwise) else text.planFewest,
            badge = text.yourPart,
            cards = listOfNotNull(mine) + cards.filterNot { it == mine },
            myPart = mine,
            myAmount = myAmount.takeIf { mine != null },
            settleMyPart = text.settleMyPart,
        )
    }
}

/** A group only on this phone is always up to date with itself. */

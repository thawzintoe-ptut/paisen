package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.format
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.play.GetMonthCloseUseCase
import com.ptut.expense.domain.play.MonthCloseState
import com.ptut.expense.domain.play.SealMonthUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

/** One of p20's three steps: what it checks, what it found, and its one action. */
data class CloseStepUiModel(val title: String, val lines: List<String>, val action: String?)

data class CloseMonthUiState(
    val title: String,
    val stepLabel: String,
    val step: CloseStepUiModel?,
    val last: Boolean,
    val next: String,
    val seal: String,
    val sealed: String?,
    /** Every step, so the screen lists all three with the current one open (p20). */
    val steps: List<CloseStepUiModel> = emptyList(),
    val at: Int = 0,
    val stampFace: String = "",
    val stampCaption: String = "",
    val canSeal: Boolean = false,
    /** Why the seal is off; null once it can be pressed. */
    val sealBlocked: String? = null,
)

/** #98 (p20): three steps with one action each, then the seal. Sealing never locks a row. */
class CloseMonthViewModel(
    private val month: String,
    getClose: GetMonthCloseUseCase,
    private val sealMonth: SealMonthUseCase,
    categories: Flow<List<Category>> = flowOf(emptyList()),
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    private val step = MutableStateFlow(0)

    val uiState: StateFlow<CloseMonthUiState?> = combine(getClose(month), step, categories) { close, at, named -> close.asUiState(at, named) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)

    fun next() {
        step.value = (step.value + 1).coerceAtMost(STEPS - 1)
    }

    fun seal() {
        // The seal is read back from the book, so a failed write leaves it unsealed on screen.
        viewModelScope.launch { runSuspendCatching { sealMonth(month) }.onFailure { toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error)) } }
    }

    private fun MonthCloseState.asUiState(at: Int, categories: List<Category>): CloseMonthUiState {
        val text = AppText.copy.play.extras
        val yearMonth = runCatching { YearMonth.parse(month) }.getOrNull()
        val name = yearMonth?.let { "${it.month.longName()} ${it.year}" } ?: month
        val shop: (String) -> Pair<String, String> = { id -> rows.firstOrNull { it.id == id }?.let { row -> (row.counterparty ?: row.categoryId?.let { categories.named(it).first } ?: name) to row.amount.format() } ?: (id to "") }
        val steps = listOf(
            CloseStepUiModel(
                title = text.closeSlips,
                lines = listOf(if (waitingSlips > 0) text.closeSlipsWaiting(waitingSlips) else text.closeSlipsDone),
                action = text.closeSlipsAction.takeIf { waitingSlips > 0 },
            ),
            CloseStepUiModel(
                title = text.closeBig,
                lines = (
                    close.duplicates.map { (first, _) -> shop(first).let { (s, a) -> text.closeDuplicate(s, a) } } +
                        close.bigCosts.map { cost ->
                            val why = listOfNotNull(text.closeWhyRisen.takeIf { cost.risen }, text.closeWhyLarge.takeIf { cost.large }).joinToString(", ")
                            shop(cost.id).let { (s, a) -> text.closeBigCost(s, a, why) }
                        }
                    ).ifEmpty { listOf(text.closeNothingOdd) },
                action = null,
            ),
            CloseStepUiModel(
                title = text.closeCompare,
                lines = listOf(
                    close.versusLastMonth.let { versus ->
                        when {
                            versus == null -> text.closeFirst
                            versus > Satang.Zero -> text.closeMore(versus.format())
                            versus < Satang.Zero -> text.closeLess((-versus).format())
                            else -> text.closeSame
                        }
                    },
                ),
                action = null,
            ),
        )
        return CloseMonthUiState(
            title = text.closeTitle(name),
            stepLabel = text.closeStep(at + 1, STEPS),
            step = steps.getOrNull(at).takeIf { !sealed },
            last = at == STEPS - 1,
            next = text.closeNext,
            seal = text.closeSeal,
            sealed = text.closeSealed.takeIf { sealed },
            steps = steps,
            at = if (sealed) STEPS else at,
            stampFace = runCatching { YearMonth.parse(month) }.getOrNull()?.let { AppText.copy.calendar.monthsShort[it.month.ordinal] } ?: month,
            stampCaption = text.closeStampCaption,
            canSeal = at == STEPS - 1 && waitingSlips == 0,
            sealBlocked = when {
                waitingSlips > 0 -> text.closeSealSlipsFirst(waitingSlips, yearMonth?.month?.longName() ?: month)
                at < STEPS - 1 -> text.closeSealStepsFirst
                else -> null
            },
        )
    }
}

private const val STEPS = 3

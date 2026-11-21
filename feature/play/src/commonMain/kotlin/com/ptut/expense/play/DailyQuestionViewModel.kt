package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.format
import com.ptut.expense.domain.play.ANSWERS_PER_FREEZE
import com.ptut.expense.domain.play.AnswerDailyQuestionUseCase
import com.ptut.expense.domain.play.DailyQuestion
import com.ptut.expense.domain.play.GetDailyQuestionUseCase
import com.ptut.expense.domain.play.MAX_FREEZES
import com.ptut.expense.domain.play.QuestionSide
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** One of p07's two cards. [amount] shows only after the tap (FR-87.3); [right] turns the card sky. */
data class QuestionCardUiModel(
    val name: String,
    val icon: String,
    val amount: String?,
    val count: String?,
    val right: Boolean,
    val picked: Boolean,
    val label: String,
)

sealed interface QuestionUiState {
    data object Loading : QuestionUiState

    data class None(val title: String, val line: String) : QuestionUiState

    data class Asking(
        val title: String,
        val ask: String,
        val a: QuestionCardUiModel,
        val b: QuestionCardUiModel,
        val local: String,
        internal val key: String,
        internal val aCostMore: Boolean,
        internal val day: LocalDate,
        val picked: String = "",
        val disc: String = "",
    ) : QuestionUiState

    data class Answered(
        val title: String,
        val ask: String,
        val a: QuestionCardUiModel,
        val b: QuestionCardUiModel,
        val verdict: String,
        val line: String,
        val freezeLine: String,
        val stampId: String?,
        val stampLabel: String,
        val end: String,
        val local: String,
        val picked: String = "",
        val disc: String = "",
    ) : QuestionUiState
}

/** #87, p07: one question a day, answered in one tap; right or wrong, it counts toward a freeze. */
class DailyQuestionViewModel(
    getDailyQuestion: GetDailyQuestionUseCase,
    private val answer: AnswerDailyQuestionUseCase,
    categories: Flow<List<Category>>,
) : ViewModel() {

    private var answering = false

    val uiState: StateFlow<QuestionUiState> = combine(getDailyQuestion(), categories) { question, named -> question.asUiState(named) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), QuestionUiState.Loading)

    /** The first pick of the day stands; a second tap while it saves is ignored. */
    fun pick(first: Boolean) {
        val asking = uiState.value as? QuestionUiState.Asking ?: return
        if (answering) return
        answering = true
        viewModelScope.launch {
            try {
                runSuspendCatching { answer(asking.day, asking.key, right = first == asking.aCostMore, pickedFirst = first) }
            } finally {
                answering = false
            }
        }
    }
}

private fun DailyQuestion.asUiState(categories: List<Category>): QuestionUiState {
    val text = AppText.copy.play.question
    val local = AppText.copy.play.local
    val pair = pair ?: return QuestionUiState.None(text.title, text.none)
    val right = answeredRight
    if (right == null) {
        return QuestionUiState.Asking(
            title = text.title,
            ask = text.ask,
            a = pair.a.card(categories, shown = false, costMore = false, picked = false),
            b = pair.b.card(categories, shown = false, costMore = false, picked = false),
            local = local,
            key = pair.key,
            aCostMore = pair.aCostMore,
            day = day,
            picked = text.paisenPicked,
            disc = AppText.copy.play.coach.disc,
        )
    }
    // Days answered before the pick was kept fall back to working it out from right or wrong.
    val pickedA = pickedFirst ?: (right == pair.aCostMore)
    val (aName, _) = pair.a.named(categories)
    val (bName, _) = pair.b.named(categories)
    return QuestionUiState.Answered(
        title = text.title,
        ask = text.ask,
        a = pair.a.card(categories, shown = true, costMore = pair.aCostMore, picked = pickedA),
        b = pair.b.card(categories, shown = true, costMore = !pair.aCostMore, picked = !pickedA),
        verdict = if (right) text.right else text.wrong,
        line = text.cameTo(aName, pair.a.amount.format(), bName, pair.b.amount.format()),
        freezeLine = when {
            freezes >= MAX_FREEZES -> text.freezesFull
            freezeProgress == 0 -> text.freezeFilled(freezes)
            else -> text.freezeProgress(freezeProgress, ANSWERS_PER_FREEZE)
        },
        stampId = stampWaiting,
        stampLabel = text.openStamp,
        end = text.end,
        local = local,
        picked = text.paisenPicked,
        disc = AppText.copy.play.coach.disc,
    )
}

private fun QuestionSide.card(categories: List<Category>, shown: Boolean, costMore: Boolean, picked: Boolean): QuestionCardUiModel {
    val text = AppText.copy.play.question
    val (name, icon) = named(categories)
    return QuestionCardUiModel(
        name = name,
        icon = icon,
        amount = amount.format().takeIf { shown },
        count = text.rows(count).takeIf { shown },
        right = shown && costMore,
        picked = picked,
        label = if (shown) "$name, ${amount.format()}" else text.pickLabel(name),
    )
}

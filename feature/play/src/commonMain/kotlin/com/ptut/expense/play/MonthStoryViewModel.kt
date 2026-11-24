package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.format
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.play.GetMonthStoryUseCase
import com.ptut.expense.domain.play.MarkStorySeenUseCase
import com.ptut.expense.domain.play.StoryCard
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

/** One card: its line, the number it is built from (R8), and whether the number may go on a shared card. */
data class StoryCardUiModel(val title: String, val figure: String?, val line: String?, val hiddenTitle: String = title)

data class MonthStoryUiState(
    val title: String,
    val cards: List<StoryCardUiModel>,
    val index: Int,
    val next: String,
    val share: String,
    /** FR-90.3: amounts on the shared picture only when Settings › Play says so. */
    val shareAmounts: Boolean,
    val hidden: String,
    val none: String?,
    val showAmounts: String = AppText.copy.play.extras.storyShowAmounts,
    val showAmountsHint: String = AppText.copy.play.extras.storyShowAmountsHint,
) {
    val card: StoryCardUiModel? get() = cards.getOrNull(index)
}

/** #90 (p03): five cards at the start of a month; a tap goes on, and reading to the end marks it read. */
class MonthStoryViewModel(
    private val month: String,
    getStory: GetMonthStoryUseCase,
    private val markSeen: MarkStorySeenUseCase,
    categories: Flow<List<Category>> = flowOf(emptyList()),
    shareAmounts: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    private val index = MutableStateFlow(0)

    val uiState: StateFlow<MonthStoryUiState?> = combine(getStory(month), index, categories, shareAmounts) { story, at, named, amounts ->
        val text = AppText.copy.play.extras
        val cards = story.cards.map { it.asUiModel(named) }
        MonthStoryUiState(
            title = text.storyTitle(monthName(story.month)),
            cards = cards,
            index = at.coerceAtMost((cards.size - 1).coerceAtLeast(0)),
            next = if (at >= cards.size - 1) text.storyDone else text.storyNext,
            share = text.storyShare,
            shareAmounts = amounts,
            hidden = "${Home.money.symbol} •••",
            none = text.storyNone.takeIf { cards.isEmpty() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)

    /** False on the last card, where the story is marked read and the screen closes. */
    fun next(): Boolean {
        val cards = uiState.value?.cards ?: return false
        if (index.value < cards.size - 1) {
            index.value += 1
            return true
        }
        viewModelScope.launch { runSuspendCatching { markSeen(month) } }
        return false
    }

    private fun monthName(iso: String): String =
        runCatching { YearMonth.parse(iso) }.getOrNull()?.let { "${it.month.longName()} ${it.year}" } ?: iso
}

private fun StoryCard.asUiModel(categories: List<Category>): StoryCardUiModel {
    val text = AppText.copy.play.extras
    return when (this) {
        is StoryCard.Total -> StoryCardUiModel(
            title = text.storyTotalHidden,
            hiddenTitle = text.storyTotalHidden,
            figure = spent.format(),
            line = versusUsual.let { versus ->
                when {
                    versus == null -> text.storyTotalFirst
                    versus > Satang.Zero -> text.storyTotalMore(versus.format())
                    else -> text.storyTotalLess((-versus).format())
                }
            },
        )

        is StoryCard.BiggestShift -> StoryCardUiModel(
            title = text.storyShift(categories.named(categoryId).first),
            figure = difference.abs().format(),
            line = if (difference > Satang.Zero) text.storyShiftMore(difference.format()) else text.storyShiftLess((-difference).format()),
        )

        is StoryCard.NewShop -> StoryCardUiModel(text.storyNewShop(name), amount.format(), line = null, hiddenTitle = text.storyNewShopHidden)

        is StoryCard.QuietestWeek -> StoryCardUiModel(text.storyQuiet(monday.dayLabel()), spent.format(), line = null)

        is StoryCard.NoSpendDays -> StoryCardUiModel(text.storyNoSpend(count), figure = null, line = null)
    }
}

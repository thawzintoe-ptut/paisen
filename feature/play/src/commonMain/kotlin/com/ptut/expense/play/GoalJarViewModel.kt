package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.MoneyParts
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.formatCompactParts
import com.ptut.expense.domain.play.ArchiveGoalUseCase
import com.ptut.expense.domain.play.GoalJarState
import com.ptut.expense.domain.play.GoalPace
import com.ptut.expense.domain.play.ObserveGoalJarUseCase
import com.ptut.expense.domain.play.PERCENT
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.AmountEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhatIfUiModel(val categoryId: String, val label: String, val line: String)

sealed interface GoalJarUiState {
    /** A new jar: its name and amount, prefilled from a trip's estimate when one sent the reader here. */
    data class Editing(val title: String, val nameLabel: String, val name: String, val targetLabel: String, val target: String, val symbol: String, val save: String, val canSave: Boolean, val noMoney: String) : GoalJarUiState

    data class Jar(
        val title: String,
        val name: String,
        /** What the jar holds, drawn as a rolling hero amount (p06). */
        val saved: MoneyParts,
        val ofTarget: String,
        val fraction: Float,
        val label: String,
        val keptTitle: String,
        val keptLine: String,
        /** "+฿460" a week; null while there is no income or the pace adds nothing. */
        val kept: String?,
        val paceTitle: String,
        val pace: String,
        val whatIf: List<WhatIfUiModel>,
        val chosen: String?,
        val noMoney: String,
        val archive: String,
    ) : GoalJarUiState

    /** Put away, or never there. */
    data object Missing : GoalJarUiState
}

/**
 * #92 (p06): a jar that fills from what the ledger keeps. No money moves (FR-92.1). The jar's water and
 * its slosh are the view's; this holds the numbers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalJarViewModel(
    goalId: String?,
    name: String?,
    target: String?,
    private val observeJar: ObserveGoalJarUseCase,
    private val saveGoal: SaveGoalUseCase,
    private val archiveGoal: ArchiveGoalUseCase,
    categories: Flow<List<Category>> = flowOf(emptyList()),
) : ViewModel() {

    private val id = MutableStateFlow(goalId)
    private val form = MutableStateFlow(JarForm(name.orEmpty(), target.orEmpty()))
    private val chosen = MutableStateFlow<String?>(null)
    private var saving = false

    val uiState: StateFlow<GoalJarUiState?> = combine(
        id.flatMapLatest { current -> current?.let { observeJar(it) } ?: flowOf(null) },
        id,
        form,
        chosen,
        categories,
    ) { jar, current, (typedName, typedTarget), what, named ->
        when {
            current == null -> editing(typedName, typedTarget)
            jar == null -> GoalJarUiState.Missing
            else -> jar.asUiState(what, named)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)

    fun setName(text: String) = form.update { it.copy(name = text) }

    fun setTarget(text: String) = form.update { it.copy(target = text.filter { c -> c.isDigit() || c == '.' }) }

    fun save() {
        val (typedName, typedTarget) = form.value
        val target = targetOf(typedTarget) ?: return
        if (typedName.isBlank() || saving) return
        saving = true
        viewModelScope.launch {
            runSuspendCatching { saveGoal(null, typedName, target) }.onSuccess { id.value = it }
            saving = false
        }
    }

    /** FR-92.3: what skipping [categoryId] would do; tapping it again goes back. */
    fun whatIf(categoryId: String) {
        chosen.value = categoryId.takeIf { it != chosen.value }
    }

    fun archive(onDone: () -> Unit) {
        val current = id.value ?: return
        viewModelScope.launch {
            runSuspendCatching { archiveGoal(current) }.onSuccess { onDone() }
        }
    }

    private fun editing(typedName: String, typedTarget: String): GoalJarUiState.Editing {
        val text = AppText.copy.play.extras
        return GoalJarUiState.Editing(
            title = text.jarTitle,
            nameLabel = text.jarName,
            name = typedName,
            targetLabel = text.jarTarget,
            target = typedTarget,
            symbol = Home.money.symbol,
            save = text.jarSave,
            canSave = typedName.isNotBlank() && targetOf(typedTarget) != null,
            noMoney = text.jarNoMoney,
        )
    }

    private fun targetOf(typed: String): Satang? = AmountEntry.ofText(typed)?.toSatang()?.takeIf { it > Satang.Zero }

    private fun GoalJarState.asUiState(what: String?, categories: List<Category>): GoalJarUiState.Jar {
        val text = AppText.copy.play.extras
        val fraction = pace.saved.ratioTo(goal.target).coerceIn(0f, 1f)
        return GoalJarUiState.Jar(
            title = text.jarTitle,
            name = goal.name,
            saved = pace.saved.formatCompactParts(),
            ofTarget = text.jarOf(goal.target.formatCompact(), (fraction * PERCENT).toInt()),
            fraction = fraction,
            label = text.jarLabel((fraction * PERCENT).toInt()),
            keptTitle = text.jarKept,
            keptLine = text.jarKeptLine,
            kept = pace.perWeek?.let { "+${it.formatCompact()}" },
            paceTitle = text.jarPaceTitle,
            pace = pace.line(goal.target),
            whatIf = whatIf.map { (categoryId, other) ->
                val name = categories.named(categoryId).first
                WhatIfUiModel(categoryId, text.jarWhatIf(name), other.reachOn?.let { text.jarWhatIfDate(name, it.dayLabel()) } ?: text.jarWhatIfNone(name))
            },
            chosen = what,
            noMoney = text.jarNoMoney,
            archive = text.jarArchive,
        )
    }

    private fun GoalPace.line(target: Satang): String {
        val text = AppText.copy.play.extras
        val day = reachOn
        return when {
            fills(target) -> text.jarFull
            !hasDays -> text.jarNoDays
            day == null -> text.jarNoPace
            else -> text.jarPace(day.dayLabel())
        }
    }
}

private data class JarForm(val name: String, val target: String)

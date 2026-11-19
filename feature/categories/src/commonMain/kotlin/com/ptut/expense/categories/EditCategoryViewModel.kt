package com.ptut.expense.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.suggestedTarget
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.targetCopy
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Screen 12a. Every edit here is non-destructive, and the two that look destructive say what they
 * will actually do before they are tapped.
 *
 * A rename writes one row. History follows it without being touched, because a transaction holds
 * a category id and never a name — FR-12's reason for the id being stable in the first place.
 */
@OptIn(ExperimentalUuidApi::class)
class EditCategoryViewModel(
    private val categoryId: String,
    ledger: LedgerRepository,
    private val categories: CategoryRepository,
    /** NEW CATEGORY: nothing is stored under [categoryId] until Save, so closing leaves no trace (#116). */
    private val isNew: Boolean = false,
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
    private val newId: () -> String = { Uuid.random().toString() },
    /** #21: where the one weekly target lives. Null in a graph with no settings: the field is not drawn. */
    private val settings: AppSettings? = null,
    /** Where a failed save or delete is said. Null in a graph that binds none. */
    private val toasts: ToastMessages? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /** Null until the reader types. Absent means "as stored", which is not the same as blank. */
    private val typedName = MutableStateFlow<String?>(null)
    private val chosenIcon = MutableStateFlow<String?>(null)
    private val chosenDirection = MutableStateFlow<Direction?>(null)
    private val chosenColor = MutableStateFlow<String?>(null)
    private val subEdits = MutableStateFlow(SubCategoryEdits())
    private val movedCount = MutableStateFlow(0)
    private val rulesMoved = MutableStateFlow(0)

    /** Whole home units as typed (baht, kyat), held until Save like the name. Null means "as stored". */
    private val typedTarget = MutableStateFlow<String?>(null)

    private val closed = MutableStateFlow(false)

    /** True once the category is gone, so the screen can leave rather than render a missing row. */
    val isDeleted: StateFlow<Boolean> = closed.asStateFlow()

    /**
     * [movedCount] is a source and not a field read inside the block: the count is one suspend
     * query away and the first emission beats it every time, so a screen that only read it would
     * promise "nothing moves" over a category holding forty rows and never correct itself.
     */
    val uiState: StateFlow<EditCategoryUiState> = combine(
        combine(ledger.allTransactions, categories.categories, categories.subCategories, ::Stored),
        typedName,
        chosenIcon,
        combine(chosenDirection, chosenColor, subEdits, ::Choices),
        combine(movedCount, rulesMoved, settings?.choices?.map { it.weeklyTarget } ?: flowOf(null), typedTarget) { moved, rules, stored, typed ->
            Counts(moved, rules, stored, typed)
        },
    ) { stored, typed, icon, choices, counts -> state(stored, typed, icon, choices, counts) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = EditCategoryUiState(),
        )

    init {
        viewModelScope.launch {
            movedCount.value = categories.transactionCount(categoryId)
            rulesMoved.value = categories.shopRuleCount(categoryId)
        }
    }

    fun rename(name: String) {
        typedName.value = name
    }

    fun chooseIcon(icon: String) {
        chosenIcon.value = icon
    }

    /**
     * Money out or money in. Nothing already filed here moves: a transaction carries its own
     * direction, so this only decides which chips the sheets offer from now on.
     */
    fun chooseDirection(direction: Direction) {
        chosenDirection.value = direction
    }

    /** FR-12's "re-iconed", for the colour beside the glyph. Nothing is written until Save. */
    fun chooseColor(color: String) {
        chosenColor.value = color
    }

    /** #21: digits only, at most seven, so the field can hold nothing that is not a whole amount. */
    fun typeTarget(typed: String) {
        typedTarget.value = typed.filter(Char::isDigit).take(MAX_TARGET_DIGITS)
    }

    /** Saves everything the screen holds together: one screen, one Save, one write. */
    fun save() = viewModelScope.launch {
        val known = categories.categories.value
        val held = known.firstOrNull { it.id == categoryId } ?: draft(known) ?: return@launch
        val name = typedName.value?.trim().orEmpty().ifEmpty { held.name }
        val edits = subEdits.value

        // A failed write keeps every edit on screen, so Save can be tapped again.
        runSuspendCatching {
            categories.save(
                held.copy(
                    name = name,
                    icon = chosenIcon.value ?: held.icon,
                    direction = chosenDirection.value ?: held.direction,
                    color = chosenColor.value ?: held.color,
                ),
                subCategories = edits.toSave(categories.subCategories.value),
                removedSubCategoryIds = edits.removed.toList(),
            )
        }.onFailure { return@launch saysItFailed() }
        saveTarget()
        haptics.saved()
        typedName.value = null
        chosenIcon.value = null
        chosenDirection.value = null
        chosenColor.value = null
        subEdits.value = SubCategoryEdits()
    }

    /** Empty clears the target only when it is this category's; another category's stays. */
    private fun saveTarget() {
        val typed = typedTarget.value ?: return
        val store = settings ?: return
        val wholeUnits = typed.toLongOrNull()
        when {
            wholeUnits != null && wholeUnits > 0 -> store.setWeeklyTarget(WeeklyTarget(categoryId, Satang.ofMinorUnits(wholeUnits * minorPerWhole())))
            store.choices.value.weeklyTarget?.categoryId == categoryId -> store.setWeeklyTarget(null)
        }
        typedTarget.value = null
    }

    /** Held until Save, like the name (#116). */
    fun addSubCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        subEdits.update { it.copy(added = it.added + SubCategory(id = newId(), categoryId = categoryId, name = trimmed)) }
    }

    /**
     * FR-13 again: the id is kept, so every transaction filed under this label stays filed under
     * it. Renaming is an upsert on the same row and never a delete followed by a fresh one.
     */
    fun renameSubCategory(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        subEdits.update { edits ->
            if (edits.added.any { it.id == id }) {
                edits.copy(added = edits.added.map { if (it.id == id) it.copy(name = trimmed) else it })
            } else {
                edits.copy(renamed = edits.renamed + (id to trimmed))
            }
        }
    }

    /** FR-13: the label goes, the spending stays in the parent. Nothing is deleted but the label. */
    fun removeSubCategory(id: String) {
        subEdits.update { edits ->
            if (edits.added.any { it.id == id }) {
                edits.copy(added = edits.added.filterNot { it.id == id })
            } else {
                edits.copy(renamed = edits.renamed - id, removed = edits.removed + id)
            }
        }
    }

    /** FR-18: only ever called with an answer to where the transactions go. */
    fun delete(moveTo: String) = viewModelScope.launch {
        runSuspendCatching { categories.delete(categoryId, moveTo) }.onFailure { return@launch saysItFailed() }
        // #21: the target goes with its category.
        if (settings?.choices?.value?.weeklyTarget?.categoryId == categoryId) settings.setWeeklyTarget(null)
        haptics.deleted()
        closed.value = true
    }

    private suspend fun saysItFailed() {
        toasts?.show(ToastMessage(AppText.copy.settings.didNotWork, tone = ToastTone.Error))
    }

    /** What NEW CATEGORY opens on: last in the reader's order, so no category they know moves. */
    private fun draft(known: List<Category>): Category? = if (!isNew) {
        null
    } else {
        val last = known.maxOfOrNull { it.sortOrder } ?: -1
        Category(id = categoryId, name = AppText.copy.categories.newCategory, icon = "label", sortOrder = last + 1)
    }

    private fun state(
        read: Stored,
        typed: String?,
        icon: String?,
        choices: Choices,
        counts: Counts,
    ): EditCategoryUiState {
        val snapshot = read.snapshot
        val known = read.known
        val subs = read.subs
        val direction = choices.direction
        val color = choices.color
        val edits = choices.edits
        val stored = known.firstOrNull { it.id == categoryId }
        val held = stored ?: draft(known) ?: return EditCategoryUiState()
        // All time, not this month: a count is what a sub-category holds (p20).
        val rows = when (snapshot) {
            LedgerSnapshot.Unanswered -> emptyList()
            is LedgerSnapshot.Rows -> snapshot.transactions
        }
        val mine = subs.filter { it.categoryId == categoryId }
        val myTarget = counts.target?.takeIf { it.categoryId == categoryId }
        val edited = isEdited(held, typed, icon, direction, color, edits.changes(mine)) ||
            counts.typedTarget?.let { it.toLongOrNull() != myTarget?.amount?.wholeUnits() } == true
        // Never itself, and never the other direction: delete must not turn spending into income.
        val destinations = known.filter { it.id != categoryId && it.direction == held.direction }

        return EditCategoryUiState(
            loaded = true,
            name = typed ?: held.name,
            icon = icon ?: held.icon,
            direction = direction ?: held.direction,
            color = color ?: held.color,
            subCategories = edits.applyTo(mine).map {
                SubCategoryRowUiModel(
                    id = it.id,
                    name = it.name,
                    purchases = rows.count { row -> row.subCategoryId == it.id }.purchases(),
                )
            },
            dirty = stored == null || edited,
            edited = edited,
            deleteConsequence = consequence(counts.moved, counts.rules),
            deleteDestinations = destinations.map { CategoryChoiceUiModel(it.id, it.name, it.icon, it.color) },
            // A draft has nothing to delete, and the last of its direction has nowhere to send its rows.
            canDelete = stored != null && destinations.isNotEmpty(),
            showsTarget = settings != null && (direction ?: held.direction) == Direction.Expense,
            target = counts.typedTarget ?: myTarget?.amount?.wholeUnits()?.toString().orEmpty(),
            targetHint = rows.suggestedTarget(categoryId, clock.now().dateIn(HomeTime))?.let { targetCopy().usualWeek(it.formatCompact()) } ?: targetCopy().fieldHint,
            targetNote = counts.target?.takeIf { it.categoryId != categoryId && !counts.typedTarget.isNullOrEmpty() }?.let { other ->
                targetCopy().replaces(other.amount.formatCompact(), known.firstOrNull { it.id == other.categoryId }?.name.orEmpty())
            },
        )
    }

    /**
     * A blank name is not a change worth offering: save falls back to the stored one, so a lit
     * button over an empty field would report a write that never happened.
     */
    private fun isEdited(held: Category, typed: String?, icon: String?, direction: Direction?, color: String?, subCategoriesChanged: Boolean): Boolean =
        (typed != null && typed.isNotBlank() && typed.trim() != held.name) ||
            (icon != null && icon != held.icon) ||
            (direction != null && direction != held.direction) ||
            (color != null && color != held.color) ||
            subCategoriesChanged

    /** #219: the shop rules move with the rows, so the line says so before the tap. */
    private fun consequence(count: Int, rules: Int): String {
        val text = AppText.copy.categories
        return when {
            count == 0 && rules == 0 -> text.nothingMoves
            count == 0 -> text.askWhereRulesGo(rules)
            rules == 0 -> text.askWhereTheyGo(count)
            else -> "${text.askWhereTheyGo(count)} ${text.rulesGoToo(rules)}"
        }
    }

    private fun Int.purchases(): String = AppText.copy.categories.purchases(this)
}

/** Sub-category edits the screen holds until Save (#116). [added] rows are not stored yet. */
private data class SubCategoryEdits(
    val added: List<SubCategory> = emptyList(),
    val renamed: Map<String, String> = emptyMap(),
    val removed: Set<String> = emptySet(),
) {
    fun applyTo(stored: List<SubCategory>): List<SubCategory> =
        stored.filterNot { it.id in removed }.map { sub -> renamed[sub.id]?.let { sub.copy(name = it) } ?: sub } + added

    fun changes(stored: List<SubCategory>): Boolean =
        added.isNotEmpty() ||
            stored.any { it.id in removed } ||
            stored.any { sub -> renamed[sub.id]?.let { it != sub.name } == true }

    /** The rows to upsert: the new ones, and stored ones under their new names. */
    fun toSave(stored: List<SubCategory>): List<SubCategory> =
        added + stored.mapNotNull { sub -> renamed[sub.id]?.takeIf { it != sub.name }?.let { sub.copy(name = it) } }
}

/** What the screen reads from storage, by name rather than as a Triple. */
private data class Stored(val snapshot: LedgerSnapshot, val known: List<Category>, val subs: List<SubCategory>)

/** The direction, colour and sub-category edits the reader is holding until Save. */
private data class Choices(val direction: Direction?, val color: String?, val edits: SubCategoryEdits)

/** What the screen counts beside the category itself: rows and rules that would move, and the target. */
private data class Counts(val moved: Int, val rules: Int, val target: WeeklyTarget?, val typedTarget: String?)

private const val MAX_TARGET_DIGITS = 7

private fun Satang.wholeUnits(): Long = toMinorUnits() / minorPerWhole()

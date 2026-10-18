package com.ptut.expense.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    /** Null until the reader types. Absent means "as stored", which is not the same as blank. */
    private val typedName = MutableStateFlow<String?>(null)
    private val chosenIcon = MutableStateFlow<String?>(null)
    private val chosenDirection = MutableStateFlow<Direction?>(null)
    private val chosenColor = MutableStateFlow<String?>(null)
    private val subEdits = MutableStateFlow(SubCategoryEdits())
    private val movedCount = MutableStateFlow(0)
    private val rulesMoved = MutableStateFlow(0)

    private val closed = MutableStateFlow(false)

    /** True once the category is gone, so the screen can leave rather than render a missing row. */
    val isDeleted: StateFlow<Boolean> = closed.asStateFlow()

    /**
     * [movedCount] is a source and not a field read inside the block: the count is one suspend
     * query away and the first emission beats it every time, so a screen that only read it would
     * promise "nothing moves" over a category holding forty rows and never correct itself.
     */
    val uiState: StateFlow<EditCategoryUiState> = combine(
        combine(ledger.allTransactions, categories.categories, categories.subCategories, ::Triple),
        typedName,
        chosenIcon,
        combine(chosenDirection, chosenColor, subEdits, ::Triple),
        combine(movedCount, rulesMoved, ::Pair),
    ) { held, typed, icon, chosen, moved ->
        state(held.first, held.second, held.third, typed, icon, chosen.first, chosen.second, chosen.third, moved.first, moved.second)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
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

    /** Saves everything the screen holds together: one screen, one Save, one write. */
    fun save() = viewModelScope.launch {
        val known = categories.categories.value
        val held = known.firstOrNull { it.id == categoryId } ?: draft(known) ?: return@launch
        val name = typedName.value?.trim().orEmpty().ifEmpty { held.name }
        val edits = subEdits.value

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
        haptics.saved()
        typedName.value = null
        chosenIcon.value = null
        chosenDirection.value = null
        chosenColor.value = null
        subEdits.value = SubCategoryEdits()
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
        categories.delete(categoryId, moveTo)
        haptics.deleted()
        closed.value = true
    }

    /** What NEW CATEGORY opens on: last in the reader's order, so no category they know moves. */
    private fun draft(known: List<Category>): Category? = if (!isNew) {
        null
    } else {
        val last = known.maxOfOrNull { it.sortOrder } ?: -1
        Category(id = categoryId, name = AppText.copy.categories.newCategory, icon = "label", sortOrder = last + 1)
    }

    private fun state(
        snapshot: LedgerSnapshot,
        known: List<Category>,
        subs: List<SubCategory>,
        typed: String?,
        icon: String?,
        direction: Direction?,
        color: String?,
        edits: SubCategoryEdits,
        moved: Int,
        rules: Int,
    ): EditCategoryUiState {
        val stored = known.firstOrNull { it.id == categoryId }
        val held = stored ?: draft(known) ?: return EditCategoryUiState()
        // All time, not this month: a count is what a sub-category holds (p20).
        val rows = when (snapshot) {
            LedgerSnapshot.Unanswered -> emptyList()
            is LedgerSnapshot.Rows -> snapshot.transactions
        }
        val mine = subs.filter { it.categoryId == categoryId }
        // A blank name is not a change worth offering: save falls back to the stored one, so
        // a lit button over an empty field would report a write that never happened.
        val edited = (typed != null && typed.isNotBlank() && typed.trim() != held.name) ||
            (icon != null && icon != held.icon) ||
            (direction != null && direction != held.direction) ||
            (color != null && color != held.color) ||
            edits.changes(mine)
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
            deleteConsequence = consequence(moved, rules),
            deleteDestinations = destinations.map { CategoryChoiceUiModel(it.id, it.name, it.icon, it.color) },
            // A draft has nothing to delete, and the last of its direction has nowhere to send its rows.
            canDelete = stored != null && destinations.isNotEmpty(),
        )
    }

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

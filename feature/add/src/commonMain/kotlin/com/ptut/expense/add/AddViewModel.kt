package com.ptut.expense.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.agent.AgentInbox
import com.ptut.expense.agent.AgentLoop
import com.ptut.expense.agent.AgentOutcome
import com.ptut.expense.agent.AgentReader
import com.ptut.expense.agent.Heard
import com.ptut.expense.agent.ProposedAction
import com.ptut.expense.agent.ReadCall
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.agent.SpeechLanguage
import com.ptut.expense.agent.SpendWindow
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.under
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the sheet draws. It holds [AmountEntry] rather than a formatted string because the numpad
 * has to show exactly what was typed — formatting mid-entry moves the digits under the thumb.
 */
data class AddUiState(
    val entry: AmountEntry = AmountEntry(),
    val kind: Direction = Direction.Expense,
    val category: Category? = null,
    /** The chips for [kind]. A salary has no business being offered Groceries. */
    val categories: List<Category> = Category.seed().forDirection(Direction.Expense),
    /** The chosen category's own sub-categories. Empty until one with any is chosen (#111). */
    val subCategories: List<SubCategory> = emptyList(),
    val subCategory: SubCategory? = null,
    /** When the money moved, or null for now. Picked on the phone's own clock. */
    val occurredAtEpochMillis: Long? = null,
    /** The note, trimmed, or null for none (#211). It becomes the row's name. */
    val memo: String? = null,
    /** True while the note sheet is open over this one. */
    val editingMemo: Boolean = false,
    /** True between the tap and the row being durable. */
    val saving: Boolean = false,
    /** True once a row has been written. The shell closes the sheet on it. */
    val saved: Boolean = false,
    /** #207: the Quick add field, shown once a model is on the phone, and in a debug build for the grammar. */
    val quickAdd: Boolean = false,
    /** True while a typed sentence is being read. */
    val reading: Boolean = false,
    /** What is in the Quick add field: typed, or heard by the mic (#212). */
    val askText: String = "",
    /** #212: the mic. Null until this phone is known to hear at least one language offline. */
    val voice: QuickAddVoice? = null,
    /**
     * #216: Quick add's answer or why it failed, drawn under the field. A toast would sit under
     * this sheet, where nobody sees it.
     */
    val askNote: AskNote? = null,
) {
    /**
     * FR-14: an amount and a category. Both kinds, so income cannot skip the rule (plan §G item 6).
     * Dark while [saving] too, because two taps on one row is two rows.
     */
    val canSave: Boolean get() = !entry.isEmpty && category != null && !saving

    /** #214: something typed or picked that closing the sheet would lose, so closing asks first. */
    val changed: Boolean
        get() = !entry.isEmpty || category != null || memo != null || occurredAtEpochMillis != null || askText.isNotBlank()

    /**
     * Says why the save key is dark when it is, and what the row will do when it is not. A disabled
     * key with no sentence beside it reads as a broken app rather than as a missing choice.
     */
    val hint: String get() = when {
        category == null && !entry.isEmpty -> AppText.copy.add.pickCategoryHint
        kind == Direction.Income -> AppText.copy.add.incomeHint
        else -> AppText.copy.add.byHandHint
    }
}

/**
 * #212: the mic in Quick add and its EN · ไทย · မြန်မာ switch. Every language stays on the switch,
 * so one this phone cannot hear offline says so instead of disappearing.
 */
data class QuickAddVoice(
    val language: SpeechLanguage,
    /** The languages this phone can hear with the network off. */
    val heard: Set<SpeechLanguage>,
    val listening: Boolean = false,
) {
    val canListen: Boolean get() = language in heard

    val unavailableNote: String? get() = if (canListen) null else AppText.copy.add.voiceUnavailable(language.label)
}

/** #216: one line under Quick add. [problem] is a sentence not read or a mic that heard nothing. */
data class AskNote(val text: String, val problem: Boolean = false)

/**
 * The add sheet's state holder, and the first thing in the app that writes.
 *
 * It outlives the sheet — the shell resolves it, so a save cannot be cancelled half-written by the
 * sheet closing on top of it — which is why [reset] exists: the design opens the sheet with the
 * amount cleared, the category cleared and the kind back to expense every time.
 */
class AddViewModel(
    private val ledger: LedgerRepository,
    private val toasts: ToastMessages,
    private val categories: (Direction) -> List<Category> = { Category.seed().forDirection(it) },
    private val subCategories: () -> List<SubCategory> = { emptyList() },
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
    /** #207: reads a typed sentence. Null in a graph that binds no agent. */
    private val agent: AgentLoop? = null,
    /** Answers a question typed into Quick add. */
    private val reader: AgentReader? = null,
    /** Asked on each open, because the model can arrive or go while the shell lives. */
    private val offerQuickAdd: () -> Boolean = { false },
    /** #212: on-device speech for Quick add. Null in a graph that binds none. */
    private val speech: SpeechInput? = null,
) : ViewModel() {

    private val state = MutableStateFlow(fresh(voice = null))
    val uiState: StateFlow<AddUiState> = state.asStateFlow()

    private var listening: Job? = null

    fun reset() {
        state.value = fresh()
    }

    fun type(entry: AmountEntry) = state.update { it.copy(entry = entry) }

    /**
     * The chosen category goes with the kind. Income and spending share no category, so keeping
     * one across the switch would file a salary under Groceries without anyone choosing that.
     */
    fun setKind(kind: Direction) = state.update {
        it.copy(
            kind = kind,
            category = null,
            categories = categories(kind),
            subCategories = emptyList(),
            subCategory = null,
        )
    }

    /** The mic carries over: which languages this phone hears does not change between opens. */
    private fun fresh(voice: QuickAddVoice? = state.value.voice) = AddUiState(
        categories = categories(Direction.Expense),
        quickAdd = agent != null && offerQuickAdd(),
        voice = voice?.copy(listening = false),
    )

    /**
     * #205: the sheet as Siri or a Shortcut filled it, from a cleared sheet. The amount arrives
     * [AmountEntry.pending], so the first key replaces it, as on Check slip. A category the reader
     * no longer has is left unpicked rather than guessed.
     */
    fun prefill(action: ProposedAction.AddTransaction) {
        state.value = fresh()
        fill(action)
    }

    /**
     * #220: what a sentence read goes into the sheet, and what it did not mention stays. The
     * grammar says expense when no word says otherwise, so only income or a named category moves
     * the kind, and a kind that moves takes the category with it, as [setKind] does.
     */
    private fun fill(action: ProposedAction.AddTransaction) = state.update { current ->
        val kind = if (action.direction == Direction.Income || action.categoryId != null) action.direction else current.kind
        val offered = categories(kind)
        val named = offered.firstOrNull { it.id == action.categoryId }
        val category = named ?: current.category.takeIf { kind == current.kind }
        val underCategory = subCategories().under(category?.id)
        current.copy(
            entry = action.amount?.let(AmountEntry::of) ?: current.entry,
            kind = kind,
            categories = offered,
            category = category,
            subCategories = underCategory,
            subCategory = underCategory.firstOrNull { it.id == action.subCategoryId }
                ?: current.subCategory.takeIf { category == current.category },
            occurredAtEpochMillis = action.occurredAtEpochMillis ?: current.occurredAtEpochMillis,
            memo = action.memo ?: current.memo,
        )
    }

    /**
     * #207: a sentence in Burmese, Thai or English. What it adds fills this sheet; what it asks of
     * another screen goes to [AgentInbox], and the shell closes this sheet to open that one. A
     * question is answered in a line under the field (#216). Nothing is saved here: SAVE still does that.
     */
    fun quickAdd(text: String) {
        val loop = agent ?: return
        if (text.isBlank() || state.value.reading) return
        state.update { it.copy(reading = true, askText = text, askNote = null) }
        viewModelScope.launch {
            val outcome = loop.run(text)
            val (call, missing) = when (outcome) {
                is AgentOutcome.Understood -> outcome.call to emptySet<String>()
                is AgentOutcome.Partly -> outcome.call to outcome.missing
                AgentOutcome.NotUnderstood -> null to emptySet<String>()
            }
            // A sentence not read stays in the field, so it can be fixed rather than typed again.
            state.update { it.copy(reading = false, askText = if (call == null) text else "") }
            when (call) {
                is ProposedAction.AddTransaction -> {
                    fill(call)
                    if (missing.isNotEmpty()) {
                        note(AskNote(AppText.copy.add.notRead("amount" in missing, "category" in missing), problem = true))
                    }
                }

                is ProposedAction -> AgentInbox.offer(call)

                is ReadCall -> reader?.let { note(AskNote(answer(it, call))) }

                null -> note(AskNote(AppText.copy.add.couldNotRead, problem = true))
            }
        }
    }

    private fun note(note: AskNote) = state.update { it.copy(askNote = note) }

    /** The last answer goes once the field is typed in again, since it no longer matches what is there. */
    fun typeAsk(text: String) = state.update { it.copy(askText = text, askNote = null) }

    /**
     * #212: asked each time the sheet opens, because a language pack can arrive while the shell
     * lives. A phone that hears none of the three shows no mic.
     */
    fun openVoice() {
        val input = speech ?: return
        if (!state.value.quickAdd) return
        viewModelScope.launch {
            val heard = input.onDeviceLanguages()
            val voice = if (heard.isEmpty()) null else QuickAddVoice(language = input.preferredLanguage(), heard = heard)
            state.update { it.copy(voice = voice) }
        }
    }

    fun chooseVoiceLanguage(language: SpeechLanguage) {
        speech?.rememberLanguage(language)
        state.update { it.copy(voice = it.voice?.copy(language = language)) }
    }

    /** The words land in the field and are read as typed words are. Nothing saves without SAVE. */
    fun listen() {
        val input = speech ?: return
        val voice = state.value.voice ?: return
        if (voice.listening || !voice.canListen || state.value.reading) return
        state.update { it.copy(voice = voice.copy(listening = true), askNote = null) }
        listening = viewModelScope.launch {
            val heard = input.listen(voice.language)
            state.update { it.copy(voice = it.voice?.copy(listening = false)) }
            when (heard) {
                is Heard.Words -> quickAdd(heard.text)

                // #216: one line on both phones, so silence never looks like a mic that broke.
                Heard.Nothing -> note(AskNote(AppText.copy.add.didNotCatch, problem = true))

                Heard.NoPermission -> note(AskNote(AppText.copy.add.allowMicrophone, problem = true))

                is Heard.Failed -> note(AskNote(heard.reason, problem = true))
            }
        }
    }

    /** The mic tapped again: stop listening and read what was said. */
    fun stopListening() {
        speech?.stop()
    }

    /** The sheet closed: drop whatever was being heard, so nothing fills a sheet nobody sees. */
    fun closeVoice() {
        listening?.cancel()
        listening = null
        state.update { it.copy(voice = it.voice?.copy(listening = false)) }
    }

    private suspend fun answer(reader: AgentReader, call: ReadCall): String = when (call) {
        is ReadCall.SpendSummary -> call.window.spentLine(reader.spent(call.window).formatCompact())
        is ReadCall.FindTransactions -> AppText.copy.add.rowsMatch(reader.find(call).size)
    }

    private fun SpendWindow.spentLine(amount: String): String = when (this) {
        SpendWindow.Today -> AppText.copy.add.spentToday(amount)
        SpendWindow.Week -> AppText.copy.add.spentThisWeek(amount)
        SpendWindow.Month -> AppText.copy.add.spentThisMonth(amount)
    }

    /**
     * Tapping the chosen chip again clears it, so a mis-tap is undone where it was made. Any pick
     * clears the sub-category: it belongs to one parent (p34). The table is read on each pick, as
     * the categories are on each switch, because this holder lives as long as the shell.
     */
    fun choose(category: Category) = state.update {
        val chosen = if (it.category == category) null else category
        it.copy(category = chosen, subCategories = subCategories().under(chosen?.id), subCategory = null)
    }

    /** Optional, so a second tap takes it off again (#111). */
    fun chooseSubCategory(subCategory: SubCategory) = state.update {
        it.copy(subCategory = if (it.subCategory == subCategory) null else subCategory)
    }

    /** Income or spending from earlier, typed in now. */
    fun setTime(epochMillis: Long) = state.update { it.copy(occurredAtEpochMillis = epochMillis) }

    fun openMemo() = state.update { it.copy(editingMemo = true) }

    fun closeMemo() = state.update { it.copy(editingMemo = false) }

    /** Takes the note and closes its sheet. A blank note is no note, as on Edit purchase. */
    fun setMemo(memo: String) = state.update { it.copy(memo = memo.trim().ifEmpty { null }, editingMemo = false) }

    /**
     * Writes the row, then says so. Nothing happens without a category: the key that calls this is
     * disabled without one, and this second guard is what keeps that rule true if it ever is not.
     *
     * [AddUiState.saving] is set before the launch, not inside it, so a second tap in the same frame
     * finds it already true rather than writing the row twice.
     *
     * A write that throws is left to throw. Every reason it could — a missing category row, a
     * duplicate id — is a bug in this app rather than something the person saving can act on, and
     * the repository's other failures are all disk failures the app cannot honestly recover from.
     */
    fun save() {
        val current = state.value
        val category = current.category ?: return
        if (current.entry.isEmpty || current.saving) return

        val amount = current.entry.toSatang()
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            ledger.record(
                TransactionDraft(
                    amount = amount,
                    direction = current.kind,
                    categoryId = category.id,
                    subCategoryId = current.subCategory?.id,
                    occurredAtEpochMillis = current.occurredAtEpochMillis,
                    memo = current.memo,
                ),
            )
            // formatCompact, not format: the design's line is `Saved ฿485 · Food & drink`, and
            // `฿485.00` in a sentence reads as a machine reporting rather than a receipt.
            haptics.saved()
            toasts.show(ToastMessage(AppText.copy.add.saved(amount.formatCompact(), category.name)))
            state.update { it.copy(saving = false, saved = true) }
        }
    }
}

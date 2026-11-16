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
import com.ptut.expense.agent.VoiceDownload
import com.ptut.expense.agent.VoiceLanguages
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.parseMinor
import com.ptut.expense.domain.shared.rateUnitLabel
import com.ptut.expense.domain.under
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.currencyCopy
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
    /** #207: the Quick add field. The grammar reads short commands with no model, so any graph with an agent shows it. */
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
    /** The language whose first online listen waits on a yes, since the voice would go to Google. */
    val askingOnline: SpeechLanguage? = null,
    /** True once SAVE was tapped before the row could be written, so [missing] is said as a warning. */
    val triedSave: Boolean = false,
    /** The amount's currency. Anything but the home asks for a rate and saves its home amount. */
    val currency: Currency = Currencies.of(Home.money.code),
    /** The currency switch, in builds that offer it. */
    val offersCurrencies: Boolean = false,
    /** Home money for one [Currency.rateUnit], as typed; opens on the last rate saved. */
    val rateText: String = "",
    /** The typed amount at the typed rate, or null while either is not a number. */
    val foreign: ForeignAmount? = null,
    /** "Saved as ฿241.90" under the rate. */
    val homeLine: String? = null,
    /** True while the rate sheet is open over this one. */
    val editingRate: Boolean = false,
) {
    /**
     * The one line under a foreign amount, and the way into the rate: "฿417.50 · ฿33.4 per $", or
     * "Set the rate for USD" while there is none. Null in the home money.
     */
    val rateLine: String?
        get() {
            if (currency.isHome) return null
            val words = currencyCopy()
            val rate = Rate.parse(rateText, currency.rateUnit) ?: return words.setRate(currency.code)
            val typed = rate.typedFor(currency.rateUnit)
            return foreign?.let { words.converted(it.inHome.format(), Home.money.symbol, typed, rateUnit) } ?: words.rateOnly(Home.money.symbol, typed, rateUnit)
        }

    /** No rate yet, so the line under the amount asks rather than reports. */
    val needsRate: Boolean get() = !currency.isHome && Rate.parse(rateText, currency.rateUnit) == null

    /** "1,000 K": what one rate is typed for. */
    val rateUnit: String get() = currency.rateUnitLabel()

    /** What the row will hold in the home money: the typed amount, or the foreign amount at its rate. */
    private val inHome: Satang get() = foreign?.inHome ?: entry.toSatang()

    /**
     * What still stops a save, amount first, or null once the row can be written. FR-14: both kinds
     * need an amount and a category, so income cannot skip the rule (plan §G item 6).
     */
    val missing: String?
        get() = when {
            entry.isEmpty -> AppText.copy.add.typeAmountHint
            !currency.isHome && parseMinor(entry.typed, currency) == null -> AppText.copy.add.typeAmountHint
            category == null -> AppText.copy.add.pickCategoryHint
            !currency.isHome && foreign == null -> currencyCopy().askRate
            else -> null
        }

    /** "Save ฿60" once the row can be written, so the key says what it will write. */
    val saveLabel: String
        get() = if (missing == null) AppText.copy.add.saveAmount(inHome.formatCompact()) else AppText.copy.common.save

    /** #214: something typed or picked that closing the sheet would lose, so closing asks first. */
    val changed: Boolean
        get() = !entry.isEmpty || category != null || memo != null || occurredAtEpochMillis != null || askText.isNotBlank()
}

/**
 * #212: the mic in Quick add and its EN · ไทย · မြန်မာ switch. Every language stays on the switch,
 * so one this phone cannot hear says so instead of disappearing.
 */
data class QuickAddVoice(
    val language: SpeechLanguage,
    /** The languages this phone can hear with the network off. */
    val heard: Set<SpeechLanguage>,
    val listening: Boolean = false,
    /** Packs the phone can fetch, so the switch offers a download instead of a dead end. */
    val downloadable: Set<SpeechLanguage> = emptySet(),
    val downloading: Set<SpeechLanguage> = emptySet(),
    /** Heard by the phone's online speech service: the voice then leaves the phone. */
    val online: Set<SpeechLanguage> = emptySet(),
) {
    /** An offline pack wins, so the voice goes online only for a language the phone cannot hear itself. */
    val usesOnline: Boolean get() = language !in heard && language in online

    val canListen: Boolean get() = language in heard || usesOnline

    /** Offered even where online works, because a pack keeps the voice on the phone. */
    val canDownload: Boolean get() = language in downloadable

    val isDownloading: Boolean get() = language in downloading

    /** A row in the voice language sheet: what the mic does for [choice], or null where the phone hears it offline. */
    fun noteFor(choice: SpeechLanguage): String? = AppText.copy.add.let { text ->
        when {
            choice in heard -> null
            choice in downloading -> text.voiceDownloading(choice.fullName)
            choice in online -> text.voiceOnline(choice.fullName)
            choice in downloadable -> text.voiceDownloadable(choice.fullName)
            else -> text.voiceUnavailable(choice.fullName)
        }
    }

    fun with(languages: VoiceLanguages): QuickAddVoice = copy(
        heard = languages.heard,
        downloadable = languages.downloadable,
        downloading = languages.downloading,
        online = languages.online,
    )
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
    /** #212: on-device speech for Quick add. Null in a graph that binds none. */
    private val speech: SpeechInput? = null,
    /** Debug builds first: the amount can be typed in any of [Currencies.all]. */
    private val offersCurrencies: Boolean = false,
    /** The currency a fresh sheet opens on: the Settings choice, or the phone's country. */
    private val startingCurrency: () -> Currency = { Currencies.of(Home.money.code) },
    /** The last rate saved for a currency, shared with slips and groups. */
    private val lastRate: suspend (String) -> Rate? = { null },
    private val saveRate: suspend (String, Rate) -> Unit = { _, _ -> },
) : ViewModel() {

    private val state = MutableStateFlow(fresh(voice = null))
    val uiState: StateFlow<AddUiState> = state.asStateFlow()

    private var listening: Job? = null

    fun reset() {
        state.value = fresh()
        loadRate(state.value.currency)
    }

    fun type(entry: AmountEntry) = state.update { it.copy(entry = entry).priced() }

    /**
     * A new currency starts on its own last rate; what was typed as the amount stays. One with no
     * rate saved opens the rate sheet, since nothing can be saved in it without one.
     */
    fun chooseCurrency(currency: Currency) {
        if (!offersCurrencies || currency == state.value.currency) return
        state.update { it.copy(currency = currency, entry = it.entry.withDigits(currency.digits), rateText = "", editingRate = false).priced() }
        loadRate(currency, askIfNone = true)
    }

    fun typeRate(text: String) = state.update { it.copy(rateText = text).priced() }

    /** The rate typed in its sheet shows live; Cancel puts back the one there was. */
    fun openRate() {
        if (state.value.currency.isHome) return
        rateBefore = state.value.rateText
        state.update { it.copy(editingRate = true) }
    }

    fun closeRate(keep: Boolean) = state.update {
        val kept = if (keep) it else it.copy(rateText = rateBefore).priced()
        kept.copy(editingRate = false)
    }

    private var rateBefore: String = ""

    private fun loadRate(currency: Currency, askIfNone: Boolean = false) {
        if (currency.isHome) return
        viewModelScope.launch {
            val last = lastRate(currency.code)
            if (last == null) {
                if (askIfNone && state.value.currency == currency) openRate()
                return@launch
            }
            state.update { if (it.currency == currency && it.rateText.isEmpty()) it.copy(rateText = last.typedFor(currency.rateUnit)).priced() else it }
        }
    }

    private fun AddUiState.priced(): AddUiState {
        if (currency.isHome) return copy(foreign = null, homeLine = null)
        val rate = Rate.parse(rateText, currency.rateUnit)
        val minor = parseMinor(entry.typed, currency)?.takeIf { it > 0 }
        val priced = if (rate != null && minor != null) ForeignAmount(currency.code, minor, rate) else null
        return copy(foreign = priced, homeLine = priced?.let { currencyCopy().savedAs(it.inHome.format()) })
    }

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
        quickAdd = agent != null,
        voice = voice?.copy(listening = false),
        currency = start(),
        entry = AmountEntry(digits = start().digits),
        offersCurrencies = offersCurrencies,
    )

    /** The Settings choice or the phone's, in builds that offer currencies; the home's everywhere else. */
    private fun start(): Currency = if (offersCurrencies) startingCurrency() else home()

    private fun home(): Currency = Currencies.of(Home.money.code)

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
            // A sentence's amount is read as home money (#45), so a heard figure puts the sheet back in home.
            currency = if (action.amount != null) home() else current.currency,
            kind = kind,
            categories = offered,
            category = category,
            subCategories = underCategory,
            subCategory = underCategory.firstOrNull { it.id == action.subCategoryId }
                ?: current.subCategory.takeIf { category == current.category },
            occurredAtEpochMillis = action.occurredAtEpochMillis ?: current.occurredAtEpochMillis,
            memo = action.memo ?: current.memo,
        ).priced()
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
     * lives. Only a phone with no offline recogniser at all shows no mic; any other gets the switch
     * and a line on what it can do.
     */
    fun openVoice() {
        val input = speech ?: return
        if (!state.value.quickAdd) return
        viewModelScope.launch {
            val languages = input.languages()
            val voice = if (!languages.hasRecogniser) {
                null
            } else {
                QuickAddVoice(
                    language = input.preferredLanguage(),
                    heard = languages.heard,
                    downloadable = languages.downloadable,
                    downloading = languages.downloading,
                    online = languages.online,
                )
            }
            state.update { it.copy(voice = voice) }
        }
    }

    /** The phone fetches the pack itself. Whatever it answers, the switch then shows the phone as it is. */
    fun downloadVoice() {
        val input = speech ?: return
        val language = state.value.voice?.takeIf { it.canDownload }?.language ?: return
        state.update { current ->
            current.copy(
                voice = current.voice?.let { it.copy(downloadable = it.downloadable - language, downloading = it.downloading + language) },
                askNote = null,
            )
        }
        viewModelScope.launch {
            val result = input.download(language)
            val now = input.languages()
            state.update {
                it.copy(
                    voice = it.voice?.with(now),
                    askNote = if (result == VoiceDownload.Failed) AskNote(AppText.copy.add.voiceDownloadFailed(language.fullName), problem = true) else it.askNote,
                )
            }
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
        // The voice would leave the phone, so the first time for each language asks before the mic opens.
        if (voice.usesOnline && !input.agreedOnline(voice.language)) {
            state.update { it.copy(askingOnline = voice.language) }
            return
        }
        state.update { it.copy(voice = voice.copy(listening = true), askNote = null) }
        listening = viewModelScope.launch {
            val heard = if (voice.usesOnline) input.listenOnline(voice.language) else input.listen(voice.language)
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

    /** Yes to [AddUiState.askingOnline]: kept for that language on this phone, and the mic opens. */
    fun agreeOnline() {
        val language = state.value.askingOnline ?: return
        speech?.agreeOnline(language)
        state.update { it.copy(askingOnline = null) }
        listen()
    }

    /** Type instead: nothing is kept, so the next tap on the mic asks again. */
    fun dismissOnlineAsk() = state.update { it.copy(askingOnline = null) }

    /** The mic tapped again: stop listening and read what was said. */
    fun stopListening() {
        speech?.stop()
    }

    /** The sheet closed: drop whatever was being heard, so nothing fills a sheet nobody sees. */
    fun closeVoice() {
        listening?.cancel()
        listening = null
        state.update { it.copy(voice = it.voice?.copy(listening = false), askingOnline = null) }
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
     * Writes the row, then says so. Nothing is written without an amount and a category (FR-14): the
     * key stays lit, so a tap without them only sets [AddUiState.triedSave] and the sheet says why.
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
        if (current.saving) return
        val category = current.category
        if (current.missing != null || category == null) {
            // SAVE stays lit, so a tap too early is answered with what is missing. A missing rate
            // is answered with the sheet that takes it, once the amount and category are there.
            val askRate = current.needsRate && !current.entry.isEmpty && category != null
            state.update { it.copy(triedSave = true) }
            if (askRate) openRate()
            return
        }

        val foreign = current.foreign?.takeUnless { current.currency.isHome }
        val amount = foreign?.inHome ?: current.entry.toSatang()
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
                    foreign = foreign,
                ),
            )
            foreign?.let { saveRate(it.currency, it.rate) }
            // formatCompact, not format: the design's line is `Saved ฿485 · Food & drink`, and
            // `฿485.00` in a sentence reads as a machine reporting rather than a receipt.
            haptics.saved()
            toasts.show(ToastMessage(AppText.copy.add.saved(amount.formatCompact(), category.name)))
            state.update { it.copy(saving = false, saved = true) }
        }
    }
}

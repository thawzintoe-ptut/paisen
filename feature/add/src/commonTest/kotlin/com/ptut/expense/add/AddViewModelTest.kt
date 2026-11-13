package com.ptut.expense.add

import com.ptut.expense.ViewModelTest
import com.ptut.expense.agent.AgentLoop
import com.ptut.expense.agent.Heard
import com.ptut.expense.agent.NormaliserSource
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.agent.SpeechLanguage
import com.ptut.expense.agent.UnavailableLanguageModel
import com.ptut.expense.agent.VoiceDownload
import com.ptut.expense.agent.VoiceLanguages
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Food = Category("food", "Food & drink", "restaurant")
private val Groceries = Category.seed().first { it.id == "groceries" }
private val Transport = Category.seed().first { it.id == "transport" }
private val Supermarket = SubCategory("supermarket", "groceries", "Supermarket")
private val Market = SubCategory("market", "groceries", "Market")
private val Bts = SubCategory("bts", "transport", "BTS")

class AddViewModelTest : ViewModelTest() {

    /**
     * The gap the direction column closes. The split used to be two lists compiled into
     * `Category.kt`, so a category the reader made was in neither and never reached this sheet —
     * it could be named on History and still be impossible to file anything under.
     */
    @Test
    fun `a category the reader made is offered under its own kind`() = runTest {
        val gym = Category("gym", "Gym", "more_horiz")
        val tips = Category("tips", "Tips", "payments", Direction.Income)
        val held = Category.seed() + gym + tips

        val viewModel = AddViewModel(
            ledger = RecordingLedger(),
            toasts = ToastMessages(),
            categories = { held.forDirection(it) },
        )
        runCurrent()

        assertTrue(gym in viewModel.uiState.value.categories)
        assertFalse(tips in viewModel.uiState.value.categories)

        viewModel.setKind(Direction.Income)
        runCurrent()

        assertTrue(tips in viewModel.uiState.value.categories)
        assertFalse(gym in viewModel.uiState.value.categories)
    }

    /**
     * Income and spending share no category, so a choice made under one kind cannot survive the
     * switch — keeping it would file a salary under Groceries with nobody having chosen that.
     */
    @Test
    fun `switching to income swaps the chips and drops the category already chosen`() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())
        viewModel.choose(Category.spending.first())
        runCurrent()

        viewModel.setKind(Direction.Income)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("Income"), state.categories.map { it.name })
        assertNull(state.category)
        assertFalse(state.canSave, "no category is chosen yet, so the key stays dark")
    }

    @Test
    fun aSaveWritesTheTypedAmountAgainstTheChosenCategory() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertEquals(
            TransactionDraft(
                amount = Satang(48_500),
                direction = Direction.Expense,
                categoryId = "food",
            ),
            ledger.recorded.single(),
        )
    }

    /** FR-14 and plan §G item 6: no category, no row — and nothing claiming one was written. */
    @Test
    fun aSaveWithNoCategoryWritesNothingAndSaysNothing() = runTest {
        val ledger = RecordingLedger()
        val toasts = ToastMessages()
        val said = collect(toasts)
        val viewModel = AddViewModel(ledger = ledger, toasts = toasts)

        viewModel.type(entryOf("485"))
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty(), "a row was written without a category")
        assertTrue(said.isEmpty(), "the app claimed a save it never made")
        assertFalse(viewModel.uiState.value.saved, "the sheet would have closed on nothing")
    }

    @Test
    fun `no sub-category is offered before a category is picked`() = runTest {
        val viewModel = withSubCategories()

        assertEquals(emptyList(), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `picking a category offers its own sub-categories`() = runTest {
        val viewModel = withSubCategories()

        viewModel.choose(Groceries)

        assertEquals(listOf(Supermarket, Market), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `a category with no sub-categories offers none`() = runTest {
        val viewModel = withSubCategories()

        viewModel.choose(Food)

        assertEquals(emptyList(), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `picking another category clears the sub-category`() = runTest {
        val viewModel = withSubCategories()
        viewModel.choose(Groceries)
        viewModel.chooseSubCategory(Supermarket)

        viewModel.choose(Transport)

        val state = viewModel.uiState.value
        assertNull(state.subCategory)
        assertEquals(listOf(Bts), state.subCategories)
    }

    @Test
    fun `switching to income clears the sub-category`() = runTest {
        val viewModel = withSubCategories()
        viewModel.choose(Groceries)
        viewModel.chooseSubCategory(Supermarket)

        viewModel.setKind(Direction.Income)

        val state = viewModel.uiState.value
        assertNull(state.subCategory)
        assertEquals(emptyList(), state.subCategories)
    }

    @Test
    fun `tapping the chosen sub-category again clears it`() = runTest {
        val viewModel = withSubCategories()
        viewModel.choose(Groceries)

        viewModel.chooseSubCategory(Supermarket)
        viewModel.chooseSubCategory(Supermarket)

        assertNull(viewModel.uiState.value.subCategory)
    }

    @Test
    fun `a save files the row under the chosen sub-category`() = runTest {
        val ledger = RecordingLedger()
        val viewModel = withSubCategories(ledger)

        viewModel.type(entryOf("485"))
        viewModel.choose(Groceries)
        viewModel.chooseSubCategory(Supermarket)
        viewModel.save()
        runCurrent()

        assertEquals("supermarket", ledger.recorded.single().subCategoryId)
    }

    private fun withSubCategories(ledger: RecordingLedger = RecordingLedger()) = AddViewModel(
        ledger = ledger,
        toasts = ToastMessages(),
        subCategories = { listOf(Supermarket, Market, Bts) },
    )

    @Test
    fun aSaveWithNoAmountWritesNothing() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty())
    }

    /** `Satang.format()` pads the satang; the design's line is `Saved ฿485 · Food & drink`. */
    @Test
    fun theToastNamesWholeBahtAndTheCategory() = runTest {
        val toasts = ToastMessages()
        val said = collect(toasts)
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = toasts)

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertEquals(ToastMessage("Saved ฿485 · Food & drink"), said.single())
    }

    @Test
    fun incomeIsWrittenAsIncomeRatherThanAsANegativeExpense() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.setKind(Direction.Income)
        viewModel.type(entryOf("4500"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertEquals(Direction.Income, ledger.recorded.single().direction)
    }

    /** Two taps on one row would otherwise be two rows: the write is not instant. */
    @Test
    fun aSecondTapWhileTheFirstSaveIsStillWritingAddsNothing() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.save()
        viewModel.save()
        runCurrent()

        assertEquals(1, ledger.recorded.size)
    }

    @Test
    fun tappingTheChosenCategoryAgainClearsItSoAMisTapIsUndoneWhereItWasMade() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.choose(Food)

        assertEquals(null, viewModel.uiState.value.category)
        assertFalse(viewModel.uiState.value.canSave)
    }

    /** The design reopens the sheet cleared, and this holder outlives the sheet. */
    @Test
    fun resetClearsTheAmountTheCategoryAndTheKind() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        viewModel.setKind(Direction.Income)
        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.reset()

        assertEquals(AddUiState(), viewModel.uiState.value)
    }

    @Test
    fun theSaveKeySaysWhyItIsDarkOnceAnAmountIsTypedWithNoCategory() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        assertEquals("Added by hand · no slip for this", viewModel.uiState.value.hint)

        viewModel.type(entryOf("485"))

        assertEquals("Pick a category to save this", viewModel.uiState.value.hint)
    }

    private fun entryOf(typed: String): AmountEntry =
        typed.fold(AmountEntry()) { entry, key -> if (key == '.') entry.decimal() else entry.digit(key) }

    @Test
    fun `a phone with no recogniser shows no voice`() = runTest {
        val viewModel = quickAdd(FakeSpeech(VoiceLanguages(heard = emptySet(), hasRecogniser = false)))

        viewModel.openVoice()
        runCurrent()

        assertNull(viewModel.uiState.value.voice)
    }

    /** Some phones' recognisers cannot list their packs. Hiding the mic there left no reason on screen. */
    @Test
    fun `a recogniser with no pack it can hear or fetch says so instead of hiding`() = runTest {
        val viewModel = quickAdd(FakeSpeech(VoiceLanguages(heard = emptySet())))

        viewModel.openVoice()
        runCurrent()

        val voice = assertNotNull(viewModel.uiState.value.voice)
        assertFalse(voice.canListen)
        assertFalse(voice.canDownload)
        assertTrue(assertNotNull(voice.note).contains("English"))
    }

    /** Google offers no offline Burmese, so Burmese is heard online or not at all. */
    @Test
    fun `a language the phone hears only online gets the mic and says the voice goes to Google`() = runTest {
        val viewModel = quickAdd(FakeSpeech(VoiceLanguages(heard = emptySet(), online = setOf(SpeechLanguage.Burmese)), preferred = SpeechLanguage.Burmese))

        viewModel.openVoice()
        runCurrent()

        val voice = assertNotNull(viewModel.uiState.value.voice)
        assertTrue(voice.canListen)
        assertTrue(voice.usesOnline)
        assertTrue(assertNotNull(voice.note).contains("Google"))
    }

    @Test
    fun `an offline pack is used before online for the same language`() = runTest {
        val viewModel = quickAdd(
            FakeSpeech(VoiceLanguages(heard = setOf(SpeechLanguage.English), online = setOf(SpeechLanguage.English))),
        )

        viewModel.openVoice()
        runCurrent()

        val voice = assertNotNull(viewModel.uiState.value.voice)
        assertTrue(voice.canListen)
        assertFalse(voice.usesOnline)
        assertNull(voice.note)
    }

    @Test
    fun `listening in a language heard only online asks for online recognition`() = runTest {
        val speech = FakeSpeech(VoiceLanguages(heard = emptySet(), online = setOf(SpeechLanguage.Burmese)), preferred = SpeechLanguage.Burmese)
        val viewModel = quickAdd(speech)
        viewModel.openVoice()
        runCurrent()

        viewModel.listen()
        runCurrent()

        assertEquals(listOf(SpeechLanguage.Burmese to true), speech.listens)
    }

    /** The emulator's case: a recogniser with no pack yet. Hiding the mic left no way to get one. */
    @Test
    fun `a language the phone can download is offered instead of hiding the mic`() = runTest {
        val viewModel = quickAdd(FakeSpeech(VoiceLanguages(heard = emptySet(), downloadable = setOf(SpeechLanguage.English))))

        viewModel.openVoice()
        runCurrent()

        val voice = assertNotNull(viewModel.uiState.value.voice)
        assertFalse(voice.canListen)
        assertTrue(voice.canDownload)
        assertTrue(assertNotNull(voice.note).contains("English"))
    }

    @Test
    fun `a finished download turns the mic on`() = runTest {
        val speech = FakeSpeech(
            VoiceLanguages(heard = emptySet(), downloadable = setOf(SpeechLanguage.English)),
            download = VoiceDownload.Ready,
        )
        val viewModel = quickAdd(speech)
        viewModel.openVoice()
        runCurrent()

        viewModel.downloadVoice()
        runCurrent()

        assertTrue(assertNotNull(viewModel.uiState.value.voice).canListen)
        assertEquals(listOf(SpeechLanguage.English), speech.downloads)
    }

    @Test
    fun `a download the phone only scheduled shows as downloading`() = runTest {
        val viewModel = quickAdd(
            FakeSpeech(
                VoiceLanguages(heard = emptySet(), downloadable = setOf(SpeechLanguage.English)),
                download = VoiceDownload.Started,
            ),
        )
        viewModel.openVoice()
        runCurrent()

        viewModel.downloadVoice()
        runCurrent()

        val voice = assertNotNull(viewModel.uiState.value.voice)
        assertTrue(voice.isDownloading)
        assertFalse(voice.canDownload)
        assertFalse(voice.canListen)
    }

    @Test
    fun `a failed download says so and keeps the offer`() = runTest {
        val viewModel = quickAdd(
            FakeSpeech(
                VoiceLanguages(heard = emptySet(), downloadable = setOf(SpeechLanguage.English)),
                download = VoiceDownload.Failed,
            ),
        )
        viewModel.openVoice()
        runCurrent()

        viewModel.downloadVoice()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(assertNotNull(state.voice).canDownload)
        val note = assertNotNull(state.askNote)
        assertTrue(note.problem)
        // Named, because the switch may show another language by the time the phone answers.
        assertTrue(note.text.contains("English"))
    }

    /** Quick add shows only with an agent; these tests never read a sentence with it. */
    private fun quickAdd(speech: SpeechInput) = AddViewModel(
        ledger = RecordingLedger(),
        toasts = ToastMessages(),
        agent = AgentLoop(
            normaliser = NormaliserSource { null },
            model = UnavailableLanguageModel(),
            context = { error("The voice tests read no sentence") },
        ),
        speech = speech,
    )

    /** Everything the toast seam carried during the test, in order. */
    private fun TestScope.collect(toasts: ToastMessages): List<ToastMessage> =
        mutableListOf<ToastMessage>().also { seen -> backgroundScope.launch { toasts.messages.toList(seen) } }
}

/** A recogniser whose packs the test sets. A finished download moves the language into `heard`. */
private class FakeSpeech(
    private var languages: VoiceLanguages,
    private val download: VoiceDownload = VoiceDownload.Failed,
    private val preferred: SpeechLanguage = SpeechLanguage.English,
) : SpeechInput {
    val downloads = mutableListOf<SpeechLanguage>()

    /** Each listen, and whether it asked for online recognition. */
    val listens = mutableListOf<Pair<SpeechLanguage, Boolean>>()

    override suspend fun onDeviceLanguages(): Set<SpeechLanguage> = languages.heard

    override suspend fun languages(): VoiceLanguages = languages

    override suspend fun download(language: SpeechLanguage): VoiceDownload {
        downloads += language
        languages = when (download) {
            VoiceDownload.Ready -> languages.copy(heard = languages.heard + language, downloadable = languages.downloadable - language)
            VoiceDownload.Started -> languages.copy(downloadable = languages.downloadable - language, downloading = languages.downloading + language)
            VoiceDownload.Failed -> languages
        }
        return download
    }

    override fun preferredLanguage(): SpeechLanguage = preferred

    override fun rememberLanguage(language: SpeechLanguage) = Unit

    override suspend fun listen(language: SpeechLanguage): Heard = Heard.Nothing.also { listens += language to false }

    override suspend fun listenOnline(language: SpeechLanguage): Heard = Heard.Nothing.also { listens += language to true }

    override fun stop() = Unit
}

/** A ledger with nothing in it that remembers what it was asked to write. */
private class RecordingLedger : LedgerRepository {
    val recorded = mutableListOf<TransactionDraft>()

    override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))

    override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(null)
    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(emptyList())

    /** The add sheet never deletes, so a call here is a screen reaching past its job. */

    override suspend fun delete(transactionId: String): Unit = error("The add sheet never deletes")

    override suspend fun restore(transaction: Transaction): Unit = error("The add sheet never deletes")

    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
        error("The add sheet never edits")

    override suspend fun record(draft: TransactionDraft) {
        recorded += draft
    }
}

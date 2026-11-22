package com.ptut.expense.add

import com.ptut.expense.ViewModelTest
import com.ptut.expense.agent.AgentContext
import com.ptut.expense.agent.AgentLoop
import com.ptut.expense.agent.AgentReader
import com.ptut.expense.agent.Heard
import com.ptut.expense.agent.NormaliserSource
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.agent.SpeechLanguage
import com.ptut.expense.agent.UnavailableLanguageModel
import com.ptut.expense.agent.VoiceDownload
import com.ptut.expense.agent.VoiceLanguages
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.agent.AnswerQuickAddQuestionUseCase
import com.ptut.expense.domain.agent.InterpretQuickAddUseCase
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.ledger.GetLastRateUseCase
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.RateBook
import com.ptut.expense.domain.ledger.RecordTransactionUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.time.Instant

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

    /** Hick's law: three chips, the ones most filed, and the chosen one leads once there is one. */
    @Test
    fun `the chips lead with what is filed most and then with what was chosen`() = runTest {
        val viewModel = AddViewModel(
            ledger = RecordingLedger(),
            toasts = ToastMessages(),
            categoryUsage = flowOf(mapOf("transport" to 5, "bills" to 3, "food" to 1)),
        )
        runCurrent()

        assertEquals(listOf("transport", "bills", "food"), viewModel.uiState.value.chips.shown.map { it.id })
        assertEquals(3, viewModel.uiState.value.chips.moreCount)

        viewModel.choose(Category.seed().first { it.id == "shopping" })

        assertEquals(listOf("shopping", "transport", "bills"), viewModel.uiState.value.chips.shown.map { it.id })
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

    @Test
    fun `a renamed category stays selected and saves under its new name`() = runTest {
        var held = Category.seed()
        val ledger = RecordingLedger()
        val toasts = ToastMessages()
        val viewModel = AddViewModel(ledger = ledger, toasts = toasts, categories = { held.forDirection(it) })
        runCurrent()

        viewModel.type(entryOf("485"))
        viewModel.choose(Groceries)
        held = held.map { if (it.id == "groceries") it.copy(name = "Market run") else it }
        viewModel.save()
        runCurrent()

        assertEquals("groceries", ledger.recorded.single().categoryId)
        assertEquals("Market run", viewModel.uiState.value.category?.name)
    }

    @Test
    fun `a deleted category clears the selection and nothing is saved`() = runTest {
        var held = Category.seed()
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages(), categories = { held.forDirection(it) })
        runCurrent()

        viewModel.type(entryOf("485"))
        viewModel.choose(Groceries)
        held = held.filterNot { it.id == "groceries" }
        viewModel.save()
        runCurrent()

        assertNull(viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.triedSave)
        assertTrue(ledger.recorded.isEmpty())
    }

    // Another currency

    private fun inCurrencies(
        ledger: RecordingLedger = RecordingLedger(),
        rates: MutableMap<String, Rate> = mutableMapOf(),
    ) = AddViewModel(
        ledger = ledger,
        toasts = ToastMessages(),
        offersCurrencies = true,
        rates = MapRates(rates),
    )

    @Test
    fun aDollarAmountSavesItsBahtAndKeepsTheDollarsAndTheRate() = runTest {
        val ledger = RecordingLedger()
        val rates = mutableMapOf<String, Rate>()
        val viewModel = inCurrencies(ledger, rates = rates)

        viewModel.chooseCurrency(Currencies.of("USD"))
        viewModel.type(entryOf("12.50"))
        viewModel.typeRate("33.4")
        viewModel.choose(Food)
        assertEquals("Saved as ฿417.50", viewModel.uiState.value.homeLine)
        viewModel.save()
        runCurrent()

        val draft = ledger.recorded.single()
        assertEquals(Satang(41_750), draft.amount)
        assertEquals("USD", draft.foreign?.currency)
        assertEquals(1_250L, draft.foreign?.originalMinor)
        assertEquals(Rate.parse("33.4"), rates["USD"])
    }

    @Test
    fun anotherCurrencyWithNoRateSavesNothingAndAsksForOne() = runTest {
        val ledger = RecordingLedger()
        val viewModel = inCurrencies(ledger)

        viewModel.chooseCurrency(Currencies.of("MMK"))
        viewModel.type(entryOf("29500"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty())
        assertEquals("Type the rate first", viewModel.uiState.value.missing)
    }

    @Test
    fun aFreshSheetOpensOnTheHomeCurrencyEvenAfterAForeignEntry() = runTest {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))
        val viewModel = inCurrencies(rates = mutableMapOf("USD" to Rate.parse("33.4")!!))
        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()

        viewModel.reset()
        runCurrent()

        assertEquals("MMK", viewModel.uiState.value.currency.code)
        assertEquals("", viewModel.uiState.value.rateText)
    }

    @Test
    fun aCurrencyWithNoSavedRateOpensTheRateSheetAndTheLineAsksForOne() = runTest {
        val viewModel = inCurrencies()

        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()

        assertTrue(viewModel.uiState.value.editingRate)
        assertEquals("Set the rate for USD", viewModel.uiState.value.rateLine)
    }

    @Test
    fun aCurrencyWithASavedRateStaysOnTheSheetAndTheLineSaysWhatItComesTo() = runTest {
        val viewModel = inCurrencies(rates = mutableMapOf("USD" to Rate.parse("33.4")!!))

        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()
        viewModel.type(entryOf("12.50"))

        assertFalse(viewModel.uiState.value.editingRate)
        assertEquals("฿417.50 · ฿33.4 per $", viewModel.uiState.value.rateLine)
    }

    @Test
    fun cancelInTheRateSheetPutsTheOldRateBack() = runTest {
        val viewModel = inCurrencies(rates = mutableMapOf("USD" to Rate.parse("33.4")!!))
        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()

        viewModel.openRate()
        viewModel.typeRate("40")
        viewModel.closeRate(keep = false)

        assertEquals("33.4", viewModel.uiState.value.rateText)
        assertFalse(viewModel.uiState.value.editingRate)
    }

    @Test
    fun saveWithNoRateOpensTheRateSheetOnceAmountAndCategoryAreThere() = runTest {
        val ledger = RecordingLedger()
        val viewModel = inCurrencies(ledger)
        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()
        viewModel.closeRate(keep = false)

        viewModel.type(entryOf("12.50"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty())
        assertTrue(viewModel.uiState.value.editingRate)
    }

    @Test
    fun anotherCurrencyTakesTheTypedAmountInItsOwnDecimalsAndHomeTakesItBack() = runTest {
        val viewModel = inCurrencies(rates = mutableMapOf("MMK" to Rate.parse("8.2", 1_000)!!))
        viewModel.type(entryOf("12.50"))

        viewModel.chooseCurrency(Currencies.of("MMK"))
        runCurrent()

        assertEquals(AmountEntry("12", digits = 0), viewModel.uiState.value.entry)
        assertNotNull(viewModel.uiState.value.foreign)

        viewModel.chooseCurrency(Currencies.Thb)

        assertEquals(AmountEntry("12", digits = 2), viewModel.uiState.value.entry)
        assertNull(viewModel.uiState.value.foreign)
        assertNull(viewModel.uiState.value.homeLine)
        assertNull(viewModel.uiState.value.rateLine)
    }

    @Test
    fun aBuildWithoutCurrenciesStaysInBaht() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        viewModel.reset()
        viewModel.chooseCurrency(Currencies.of("USD"))

        assertEquals(Currencies.Thb, viewModel.uiState.value.currency)
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
        assertEquals("Pick a category to save this", viewModel.uiState.value.missing)
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
    fun theSheetSaysWhatStillStopsASaveAmountFirst() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        assertEquals("Type an amount to save this", viewModel.uiState.value.missing)

        viewModel.type(entryOf("485"))
        assertEquals("Pick a category to save this", viewModel.uiState.value.missing)

        viewModel.choose(Food)
        assertNull(viewModel.uiState.value.missing)
    }

    /** SAVE stays lit, so a tap too early has to answer with what is missing and write nothing. */
    @Test
    fun aSaveTappedTooEarlyWritesNothingAndMarksWhatIsMissing() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty())
        assertTrue(viewModel.uiState.value.triedSave)
        assertEquals("Pick a category to save this", viewModel.uiState.value.missing)
    }

    @Test
    fun `the save key names the amount once the row can be written`() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        viewModel.type(entryOf("60"))
        assertEquals("Save", viewModel.uiState.value.saveLabel)

        viewModel.choose(Food)
        assertEquals("Save ฿60", viewModel.uiState.value.saveLabel)
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
        assertTrue(assertNotNull(voice.noteFor(voice.language)).contains("English"))
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
        assertTrue(assertNotNull(voice.noteFor(voice.language)).contains("Google"))
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
        assertNull(voice.noteFor(voice.language))
    }

    @Test
    fun `listening in a language heard only online asks for online recognition`() = runTest {
        val speech = FakeSpeech(
            VoiceLanguages(heard = emptySet(), online = setOf(SpeechLanguage.Burmese)),
            preferred = SpeechLanguage.Burmese,
            agreed = mutableSetOf(SpeechLanguage.Burmese),
        )
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
        assertTrue(assertNotNull(voice.noteFor(voice.language)).contains("English"))
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

    @Test
    fun `the first listen in a language heard online asks before the mic opens`() = runTest {
        val speech = onlineBurmese()
        val viewModel = quickAdd(speech)
        viewModel.openVoice()
        runCurrent()

        viewModel.listen()
        runCurrent()

        assertEquals(SpeechLanguage.Burmese, viewModel.uiState.value.askingOnline)
        assertTrue(speech.listens.isEmpty())
    }

    @Test
    fun `a yes to the online ask opens the mic and is not asked again`() = runTest {
        val speech = onlineBurmese()
        val viewModel = quickAdd(speech)
        viewModel.openVoice()
        runCurrent()
        viewModel.listen()

        viewModel.agreeOnline()
        runCurrent()
        viewModel.listen()
        runCurrent()

        assertNull(viewModel.uiState.value.askingOnline)
        assertEquals(setOf(SpeechLanguage.Burmese), speech.agreed)
        assertEquals(listOf(SpeechLanguage.Burmese to true, SpeechLanguage.Burmese to true), speech.listens)
    }

    @Test
    fun `type instead opens no mic and the next tap asks again`() = runTest {
        val speech = onlineBurmese()
        val viewModel = quickAdd(speech)
        viewModel.openVoice()
        runCurrent()
        viewModel.listen()

        viewModel.dismissOnlineAsk()
        runCurrent()

        assertNull(viewModel.uiState.value.askingOnline)
        assertTrue(speech.listens.isEmpty())
        viewModel.listen()
        assertEquals(SpeechLanguage.Burmese, viewModel.uiState.value.askingOnline)
    }

    @Test
    fun `a language heard on the phone never asks`() = runTest {
        val speech = FakeSpeech(VoiceLanguages(heard = setOf(SpeechLanguage.English), online = setOf(SpeechLanguage.English)))
        val viewModel = quickAdd(speech)
        viewModel.openVoice()
        runCurrent()

        viewModel.listen()
        runCurrent()

        assertNull(viewModel.uiState.value.askingOnline)
        assertEquals(listOf(SpeechLanguage.English to false), speech.listens)
    }

    @Test
    fun `each language in the voice sheet says how this phone hears it`() = runTest {
        val viewModel = quickAdd(
            FakeSpeech(
                VoiceLanguages(
                    heard = setOf(SpeechLanguage.English),
                    downloadable = setOf(SpeechLanguage.Thai),
                    online = setOf(SpeechLanguage.Burmese),
                ),
            ),
        )
        viewModel.openVoice()
        runCurrent()

        val voice = assertNotNull(viewModel.uiState.value.voice)
        assertNull(voice.noteFor(SpeechLanguage.English))
        assertTrue(assertNotNull(voice.noteFor(SpeechLanguage.Thai)).contains("Download"))
        assertTrue(assertNotNull(voice.noteFor(SpeechLanguage.Burmese)).contains("Google"))
    }

    @Test
    fun `a reader that throws unlocks Quick add and keeps the sentence`() = runTest {
        val viewModel = AddViewModel(
            ledger = RecordingLedger(),
            toasts = ToastMessages(),
            agent = AgentLoop(normaliser = NormaliserSource { null }, model = UnavailableLanguageModel(), context = { error("disk") }),
        )

        viewModel.quickAdd("coffee 50")
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.reading)
        assertEquals("coffee 50", state.askText)
        assertEquals(AskNote("Could not read that. Fill it in below.", problem = true), state.askNote)
    }

    @Test
    fun `a question whose answer throws says it could not read it`() = runTest {
        val unreadable = object : LedgerRepository by RecordingLedger() {
            override val recentTransactions: StateFlow<LedgerSnapshot> get() = error("disk")
        }
        val viewModel = AddViewModel(
            ledger = RecordingLedger(),
            toasts = ToastMessages(),
            agent = AgentLoop(normaliser = NormaliserSource { null }, model = UnavailableLanguageModel(), context = { known() }),
            reader = AgentReader(unreadable),
        )

        viewModel.quickAdd("how much today")
        runCurrent()

        assertFalse(viewModel.uiState.value.reading)
        assertEquals(true, viewModel.uiState.value.askNote?.problem)
    }

    @Test
    fun `a rate that fails to save still saves the row and unlocks the sheet`() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(
            ledger = ledger,
            toasts = ToastMessages(),
            offersCurrencies = true,
            rates = object : RateBook {
                override suspend fun lastRate(currency: String): Rate? = null

                override suspend fun saveRate(currency: String, rate: Rate) = error("disk")
            },
        )
        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()
        viewModel.type(entryOf("12.50"))
        viewModel.typeRate("33.4")
        viewModel.choose(Food)

        viewModel.save()
        runCurrent()

        assertEquals(1, ledger.recorded.size)
        assertTrue(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `the rate sheet replaces the note sheet and a late note close leaves it open`() = runTest {
        val viewModel = inCurrencies(rates = mutableMapOf("USD" to Rate.parse("33.4")!!))
        viewModel.chooseCurrency(Currencies.of("USD"))
        runCurrent()

        viewModel.openMemo()
        viewModel.openRate()
        assertFalse(viewModel.uiState.value.editingMemo)
        assertTrue(viewModel.uiState.value.editingRate)

        viewModel.closeMemo()
        assertEquals(AddOverlay.Rate, viewModel.uiState.value.overlay)

        viewModel.closeRate(keep = true)
        assertEquals(AddOverlay.None, viewModel.uiState.value.overlay)
    }

    private fun known() = AgentContext(categories = Category.seed(), subCategories = emptyList(), groups = emptyList(), rowIds = emptySet(), now = Instant.parse("2026-10-15T05:00:00Z"))

    private fun onlineBurmese() = FakeSpeech(VoiceLanguages(heard = emptySet(), online = setOf(SpeechLanguage.Burmese)), preferred = SpeechLanguage.Burmese)

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

/** Builds the sheet over this file's fakes, as Koin builds it over the app's graph. */
private fun AddViewModel(
    ledger: LedgerRepository,
    toasts: ToastMessages,
    categories: (Direction) -> List<Category> = { Category.seed().forDirection(it) },
    subCategories: () -> List<SubCategory> = { emptyList() },
    agent: AgentLoop? = null,
    reader: AgentReader? = null,
    speech: SpeechInput? = null,
    offersCurrencies: Boolean = false,
    rates: RateBook? = null,
    categoryUsage: Flow<Map<String, Int>> = flowOf(emptyMap()),
): AddViewModel = AddViewModel(
    toasts = toasts,
    categories = categories,
    subCategories = subCategories,
    categoryUsage = categoryUsage,
    interpretQuickAdd = agent?.let(::InterpretQuickAddUseCase),
    answerQuestion = AnswerQuickAddQuestionUseCase(reader),
    speech = speech,
    offersCurrencies = offersCurrencies,
    getLastRate = GetLastRateUseCase(rates),
    recordTransaction = RecordTransactionUseCase(ledger, rates),
)

/** A recogniser whose packs the test sets. A finished download moves the language into `heard`. */
private class FakeSpeech(
    private var languages: VoiceLanguages,
    private val download: VoiceDownload = VoiceDownload.Failed,
    private val preferred: SpeechLanguage = SpeechLanguage.English,
    /** The languages the person already let go online. */
    val agreed: MutableSet<SpeechLanguage> = mutableSetOf(),
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

    override fun agreedOnline(language: SpeechLanguage): Boolean = language in agreed

    override fun agreeOnline(language: SpeechLanguage) {
        agreed += language
    }

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

/** The last rate per currency, in a map the test can read back. */
private class MapRates(private val rates: MutableMap<String, Rate>) : RateBook {
    override suspend fun lastRate(currency: String): Rate? = rates[currency]

    override suspend fun saveRate(currency: String, rate: Rate) {
        rates[currency] = rate
    }
}

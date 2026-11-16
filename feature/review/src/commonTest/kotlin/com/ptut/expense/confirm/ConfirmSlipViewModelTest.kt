package com.ptut.expense.confirm

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.SlipDebt
import com.ptut.expense.domain.slip.SlipField
import com.ptut.expense.domain.slip.SlipParser
import com.ptut.expense.domain.slip.SlipRead
import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.BACKSPACE
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.press
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private val Owed = Satang(24_000)

class ConfirmSlipViewModelTest : ViewModelTest() {

    private val now = LocalDateTime(2026, 8, 14, 19, 41).toInstant(BangkokTime)
    private val food = Category.seed().first { it.id == "food" }
    private val groceries = Category.seed().first { it.id == "groceries" }
    private val supermarket = SubCategory("supermarket", "groceries", "Supermarket")
    private val bts = SubCategory("bts", "transport", "BTS")

    @Test
    fun `an amount the slip stated starts on the pad so it is checked and not retyped`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)))

        val state = viewModel.uiState.value
        assertEquals("485.00", state.entry.display)
        assertFalse(state.amountUnread)
        assertEquals("Amount", state.amountLabel)
    }

    /**
     * The pad opened on the slip's figure and then refused every key but backspace: a seeded
     * entry already carries its two satang. "Check this" is only a check if disagreeing works.
     */
    @Test
    fun `the amount the slip stated can be typed over on the pad that shows it`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)))

        val corrected = "282".fold(viewModel.uiState.value.entry) { entry, key ->
            entry.press(key.toString())
        }

        assertEquals("282", corrected.display)
        assertEquals(Satang(28_200), corrected.toSatang())
    }

    @Test
    fun `backspace corrects the slip's figure in place rather than clearing it`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)))

        val corrected = viewModel.uiState.value.entry.press(BACKSPACE).press("9")

        assertEquals("485.09", corrected.display)
    }

    @Test
    fun `an amount that did not read leaves the pad empty and says so`() = runTest {
        val viewModel = viewModel(slip(amount = null))

        val state = viewModel.uiState.value
        assertEquals("0", state.entry.display)
        assertTrue(state.amountUnread)
        assertEquals("Amount · not read", state.amountLabel)
    }

    // FR-13

    @Test
    fun `the rule is disclosed before it exists and names the merchant it will file`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)))

        assertEquals(
            "Every later slip from MK Restaurant Rama 9 will file itself here. " +
                "Past transactions are left alone.",
            viewModel.uiState.value.ruleNote,
        )
    }

    // The refusal

    @Test
    fun `save with no category writes nothing and says why beside Confirm`() = runTest {
        val toasts = ToastMessages()
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(amount = Satang(48_500)), store = store, toasts = toasts)

        viewModel.save()
        runCurrent()

        assertEquals(emptyList(), store.confirmed)
        assertEquals("Pick a category to save this slip", viewModel.uiState.value.categoryHint)
    }

    @Test
    fun `choosing a category only selects — nothing is written until save`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(amount = Satang(48_500)), store = store)

        viewModel.choose(food)
        runCurrent()

        assertEquals(food, viewModel.uiState.value.category)
        assertEquals(emptyList(), store.confirmed)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `tapping the chosen chip again clears it so a mis-tap is undone where it was made`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)))

        viewModel.choose(food)
        viewModel.choose(food)

        assertNull(viewModel.uiState.value.category)
    }

    // Sub-categories — #111

    @Test
    fun `picking a category offers its own sub-categories and none before`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)), subCategories = listOf(supermarket, bts))
        assertEquals(emptyList(), viewModel.uiState.value.subCategories)

        viewModel.choose(groceries)

        assertEquals(listOf(supermarket), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `picking another category clears the sub-category`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)), subCategories = listOf(supermarket, bts))
        viewModel.choose(groceries)
        viewModel.chooseSubCategory(supermarket)

        viewModel.choose(food)

        assertNull(viewModel.uiState.value.subCategory)
        assertEquals(emptyList(), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `tapping the chosen sub-category again clears it`() = runTest {
        val viewModel = viewModel(slip(amount = Satang(48_500)), subCategories = listOf(supermarket))
        viewModel.choose(groceries)

        viewModel.chooseSubCategory(supermarket)
        viewModel.chooseSubCategory(supermarket)

        assertNull(viewModel.uiState.value.subCategory)
    }

    @Test
    fun `save files the row under the chosen sub-category`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(
            slip(amount = Satang(48_500)),
            store = store,
            subCategories = listOf(supermarket),
        )

        viewModel.choose(groceries)
        viewModel.chooseSubCategory(supermarket)
        viewModel.save()
        runCurrent()

        assertEquals("supermarket", store.confirmed.single().subCategoryId)
    }

    // The save — FR-11

    @Test
    fun `save files the row and remembers the merchant and drops the queue entry with its picture`() =
        runTest {
            val source = FakeSlipSource()
            val store = InMemoryImportStore()
            source.add("q1", "irrelevant", now.toEpochMilliseconds())
            val viewModel = viewModel(slip(amount = Satang(48_500)), source = source, store = store)

            viewModel.choose(food)
            viewModel.save()
            runCurrent()

            val confirmed = store.confirmed.single()
            assertEquals("q1", confirmed.queuedSlipId)
            assertEquals("MK Restaurant Rama 9", confirmed.counterparty)
            assertEquals("food", confirmed.categoryId)
            assertEquals(Satang(48_500), confirmed.amount)
            assertEquals("0142K9", confirmed.reference)
            assertTrue(source.remaining.isEmpty(), "the picture outlived the row it became")
            assertTrue(viewModel.uiState.value.saved)
        }

    @Test
    fun `a read shop name comes filled in and a change is saved as the name rows show`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(amount = Satang(48_500)), store = store)
        runCurrent()
        assertEquals("MK Restaurant Rama 9", viewModel.uiState.value.shopName)

        viewModel.nameShop("MK Rama 9")
        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        val confirmed = store.confirmed.single()
        assertEquals("MK Restaurant Rama 9", confirmed.counterparty)
        assertEquals("MK Rama 9", confirmed.displayName)
    }

    @Test
    fun `a name typed for a slip that read no shop becomes its shop`() = runTest {
        val store = InMemoryImportStore()
        val unread = slip(amount = Satang(1_000), merchant = "").copy(reason = ReviewReason.Unreadable)
        val viewModel = viewModel(unread, store = store)
        runCurrent()
        assertEquals("", viewModel.uiState.value.shopName)

        viewModel.nameShop("Aunty Khin")
        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        val confirmed = store.confirmed.single()
        assertEquals("Aunty Khin", confirmed.counterparty)
        assertNull(confirmed.displayName)
    }

    @Test
    fun `the toast names the amount with the category and the merchant now remembered`() = runTest {
        val toasts = ToastMessages()
        val viewModel = viewModel(slip(amount = Satang(48_500)), toasts = toasts)

        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        val toast = toasts.messages.first()
        assertEquals(
            "Saved ฿485 to today · Food & drink remembered for MK Restaurant Rama 9",
            toast.text,
        )
        assertEquals(ToastTone.Success, toast.tone)
    }

    /**
     * FR-19 files a slip under the day printed on it, and Today lists only today. Without the
     * day here, a slip from last week looks lost the moment this screen closes.
     */
    @Test
    fun `a slip from an earlier day says which day it went to`() = runTest {
        val toasts = ToastMessages()
        val twoDaysAgo = LocalDateTime(2026, 8, 12, 9, 25).toInstant(BangkokTime)
        val viewModel = viewModel(slip(amount = Satang(48_500), occurredAt = twoDaysAgo), toasts = toasts)

        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        assertEquals(
            "Saved ฿485 to Wed 12 Aug · Food & drink remembered for MK Restaurant Rama 9",
            toasts.messages.first().text,
        )
    }

    @Test
    fun `a retyped amount is what gets filed rather than the one the reader lifted off the slip`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(amount = Satang(48_500)), store = store)

        var entry = AmountEntry()
        "28.25".forEach { key -> entry = entry.press(key.toString()) }
        viewModel.type(entry)
        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        // FR-9: satang typed on the pad reach storage as satang, never through a float.
        assertEquals(Satang(2_825), store.confirmed.single().amount)
    }

    @Test
    fun `two taps in the same frame file one row rather than two`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(amount = Satang(48_500)), store = store)

        viewModel.choose(food)
        viewModel.save()
        viewModel.save()
        runCurrent()

        assertEquals(1, store.confirmed.size)
    }

    // NFR-5

    @Test
    fun `a masked account on the slip reaches no field on this screen`() = runTest {
        // Through the parser, not injected: the screen can only show what the read handed over,
        // so this asserts the whole path rather than the last step of it.
        val read = SlipParser.parse(
            listOf(
                "Transfer Completed", "14 Aug 26  7:41 PM",
                "MR. SOMCHAI J", "KBank", maskedAccount(SENDER_LAST_FOUR),
                "MR. SOMCHAI PRASERTKUL", "KBank", maskedAccount(RECIPIENT_LAST_FOUR),
                "Transaction ID:", "016200000018ATF00018",
                "Amount:", "25.00 Baht",
            ),
        )
        val complete = assertIs<SlipRead.Complete>(read)

        val viewModel = viewModel(
            slip(
                amount = complete.amount,
                merchant = complete.counterparty,
                reference = complete.reference,
            ),
        )

        val state = viewModel.uiState.value
        assertFalse(SENDER_LAST_FOUR in state.toString(), "a masked account survived into $state")
        assertFalse(RECIPIENT_LAST_FOUR in state.toString(), "a masked account survived into $state")
    }

    @Test
    fun `a slip for what I owe in a group asks whether it paid that debt`() = runTest {
        val viewModel = viewModel(slip(amount = Owed), debts = listOf(ayeDebt))

        val match = viewModel.uiState.value.debtMatch
        assertEquals(1, match?.choices?.size)
        assertTrue(match!!.question.contains("Aye") && match.question.contains("Flat 12"), match.question)
    }

    @Test
    fun `a slip with no amount read asks about no debt`() = runTest {
        assertNull(viewModel(slip(amount = null), debts = listOf(ayeDebt)).uiState.value.debtMatch)
    }

    @Test
    fun `group payment only records the payment at the slip's time and takes the slip off the list`() = runTest {
        val recorded = mutableListOf<PaymentDraft>()
        val viewModel = viewModel(slip(amount = Owed), debts = listOf(ayeDebt), recorded = recorded)

        viewModel.payDebt(keepInSpending = false)
        runCurrent()

        assertEquals(listOf(PaymentDraft("flat", "me", "aye", Owed, PaymentMethod.BankTransfer, now.toEpochMilliseconds())), recorded)
        assertTrue(viewModel.uiState.value.dismissed)
    }

    @Test
    fun `keeping it in my spending records the payment and leaves the slip to file`() = runTest {
        val recorded = mutableListOf<PaymentDraft>()
        val viewModel = viewModel(slip(amount = Owed), debts = listOf(ayeDebt), recorded = recorded)

        viewModel.payDebt(keepInSpending = true)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, recorded.size)
        assertFalse(state.dismissed)
        assertNull(state.debtMatch)
        assertTrue(state.debtPaidNote?.contains("Aye") == true)
    }

    @Test
    fun `not this hides the question and records nothing`() = runTest {
        val recorded = mutableListOf<PaymentDraft>()
        val viewModel = viewModel(slip(amount = Owed), debts = listOf(ayeDebt), recorded = recorded)

        viewModel.notThisDebt()
        runCurrent()

        assertNull(viewModel.uiState.value.debtMatch)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `with two debts of the amount the one picked is the one paid`() = runTest {
        val recorded = mutableListOf<PaymentDraft>()
        val koMin = SlipDebt("trip", "Trip", "me", "ko", "Ko Min", Owed)
        val viewModel = viewModel(slip(amount = Owed), debts = listOf(ayeDebt, koMin), recorded = recorded)

        viewModel.chooseDebt(1)
        viewModel.payDebt(keepInSpending = false)
        runCurrent()

        assertEquals("ko", recorded.single().toMemberId)
    }

    private val ayeDebt = SlipDebt("flat", "Flat 12", "me", "aye", "Aye", Owed)

    private fun viewModel(
        slip: QueuedSlip,
        source: FakeSlipSource = FakeSlipSource(),
        store: InMemoryImportStore = InMemoryImportStore(),
        toasts: ToastMessages = ToastMessages(),
        subCategories: List<SubCategory> = emptyList(),
        debts: List<SlipDebt> = emptyList(),
        recorded: MutableList<PaymentDraft> = mutableListOf(),
    ) = ConfirmSlipViewModel(
        slipId = slip.id,
        ledger = FakeLedger(listOf(slip)),
        importer = SlipImporter(source, FakeSlipTextReader(), store),
        toasts = toasts,
        discards = SlipDiscards(SlipImporter(source, FakeSlipTextReader(), store), toasts, CoroutineScope(Dispatchers.Main)) { Duration.ZERO },
        subCategories = subCategories,
        debtsFor = { amount, _ -> debts.filter { it.amount == amount } },
        recordPayment = { recorded += it },
        clock = object : Clock {
            override fun now(): Instant = this@ConfirmSlipViewModelTest.now
        },
        newId = { "generated" },
    )

    @Test
    fun `money in offers only income categories and is saved as income`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(Satang(50_000), merchant = "MS. ANONG W", direction = Direction.Income), store = store, debts = listOf(ayeDebt.copy(amount = Satang(50_000))))
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.moneyIn)
        assertTrue(state.categories.all { it.direction == Direction.Income })
        assertNull(state.debtMatch)

        viewModel.choose(state.categories.first())
        viewModel.save()
        runCurrent()

        assertEquals(Direction.Income, store.confirmed.single().direction)
    }

    @Test
    fun `a slip marked planned is saved planned`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = ConfirmSlipViewModel(
            slipId = "q1",
            ledger = FakeLedger(listOf(slip(Satang(48_500)))),
            importer = SlipImporter(FakeSlipSource(), FakeSlipTextReader(), store),
            toasts = ToastMessages(),
            discards = SlipDiscards(SlipImporter(FakeSlipSource(), FakeSlipTextReader(), store), ToastMessages(), CoroutineScope(Dispatchers.Main)) { Duration.ZERO },
            offersPlanned = true,
            newId = { "generated" },
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.offersPlanned)

        viewModel.markPlanned(true)
        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        assertTrue(store.confirmed.single().planned)
    }

    @Test
    fun `money out offers no income category`() = runTest {
        val viewModel = viewModel(slip(Satang(48_500)))
        runCurrent()

        assertFalse(viewModel.uiState.value.moneyIn)
        assertTrue(viewModel.uiState.value.categories.none { it.direction == Direction.Income })
    }

    @Test
    fun `a field the parser had to judge says so beside its label`() = runTest {
        val viewModel = viewModel(slip(Satang(2_825), lowConfidence = setOf(SlipField.Amount)))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("Amount · read, low confidence", state.amountLabel)
        // Exactly the marked field. Marking all of them is the same as marking none.
        assertEquals("MERCHANT", state.merchantLabel)
        assertEquals("REF", state.referenceLabel)
    }

    @Test
    fun `a clean read leaves every label plain`() = runTest {
        val viewModel = viewModel(slip(Satang(48_500)))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("Amount", state.amountLabel)
        assertEquals("MERCHANT", state.merchantLabel)
        assertEquals("REF", state.referenceLabel)
    }

    /** An empty pad is the stronger fact. Two clauses on one label is noise. */
    @Test
    fun `an amount that did not read says so rather than saying low confidence`() = runTest {
        val viewModel = viewModel(slip(amount = null, lowConfidence = setOf(SlipField.Amount)))
        runCurrent()

        assertEquals("Amount · not read", viewModel.uiState.value.amountLabel)
    }

    private fun slip(
        amount: Satang?,
        merchant: String = "MK Restaurant Rama 9",
        reference: String = "0142K9",
        lowConfidence: Set<SlipField> = emptySet(),
        occurredAt: Instant = now,
        direction: Direction = Direction.Expense,
    ) = QueuedSlip(
        id = "q1",
        merchant = merchant,
        occurredAtEpochMillis = occurredAt.toEpochMilliseconds(),
        note = reference,
        amount = amount,
        reason = if (amount == null) ReviewReason.AmountUnclear else ReviewReason.NewMerchant,
        capturedAtEpochMillis = now.toEpochMilliseconds(),
        lowConfidence = lowConfidence,
        direction = direction,
    )

    private class FakeLedger(queued: List<QueuedSlip>) : LedgerRepository {
        override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
        override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))

        override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
        override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(0L)
        override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

        override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(queued)

        /** Confirm slip never deletes, so a call here is a screen reaching past its job. */

        override suspend fun delete(transactionId: String): Unit = error("Confirm slip never deletes")

        override suspend fun restore(transaction: Transaction): Unit = error("Confirm slip never deletes")

        override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
            error("Confirm slip never edits")

        override suspend fun record(draft: TransactionDraft) = Unit
    }

    /** Assembled: verifyNoAccountNumbers scans this file too, and the guard is worth more. */
    private fun maskedAccount(lastFour: String) = "xxx-x-x" + lastFour + "-5"

    // #33: a kyat slip

    private fun kyat(amount: Satang = Satang(29_500)) =
        slip(amount = amount).copy(currency = "MMK", reason = ReviewReason.RateNeeded)

    @Test
    fun `a kyat slip shows its kyat and asks for a rate before it saves`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(kyat(), store = store)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("K", state.currencySymbol)
        assertEquals("29500", state.entry.display)
        assertTrue(state.asksRate)

        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        assertEquals(emptyList(), store.confirmed)
        assertEquals("Type the rate first", viewModel.uiState.value.categoryHint)
    }

    @Test
    fun `a kyat slip saves its baht at the typed rate and keeps the kyat and the rate`() = runTest {
        val store = InMemoryImportStore()
        val viewModel = viewModel(kyat(), store = store)
        runCurrent()

        viewModel.typeRate("8.2")
        assertEquals("Saved as ฿241.90", viewModel.uiState.value.homeLine)
        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        val confirmed = store.confirmed.single()
        assertEquals(Satang(24_190), confirmed.amount)
        assertEquals("MMK", confirmed.foreign?.currency)
        assertEquals(29_500L, confirmed.foreign?.originalMinor)
        assertEquals(Rate.parse("8.2", 1_000), store.rates["MMK"])
    }

    @Test
    fun `the next kyat slip opens on the rate saved last`() = runTest {
        val store = InMemoryImportStore().apply { rates["MMK"] = Rate.parse("8.2", 1_000)!! }
        val viewModel = viewModel(kyat(), store = store)
        runCurrent()

        assertEquals("8.2", viewModel.uiState.value.rateText)
        assertEquals("Saved as ฿241.90", viewModel.uiState.value.homeLine)
    }

    /** #44: foreign is anything but the home, so a baht slip in a kyat ledger asks for kyat per baht. */
    @Test
    fun `a baht slip in a kyat ledger is foreign and saves its kyat at the typed rate`() = runTest {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))
        val store = InMemoryImportStore()
        val viewModel = viewModel(slip(amount = Satang(29_500)).copy(reason = ReviewReason.RateNeeded), store = store)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.asksRate)
        assertEquals("฿", state.currencySymbol)
        assertEquals("฿", state.rateUnit)
        assertEquals("295.00", state.entry.display)

        viewModel.typeRate("125")
        assertEquals("Saved as K36,875", viewModel.uiState.value.homeLine)
        viewModel.choose(food)
        viewModel.save()
        runCurrent()

        val confirmed = store.confirmed.single()
        assertEquals(Satang.ofMinorUnits(36_875), confirmed.amount)
        assertEquals("THB", confirmed.foreign?.currency)
        assertEquals(29_500L, confirmed.foreign?.originalMinor)
    }

    private companion object {
        const val SENDER_LAST_FOUR = "12" + "34"
        const val RECIPIENT_LAST_FOUR = "67" + "89"
    }
}

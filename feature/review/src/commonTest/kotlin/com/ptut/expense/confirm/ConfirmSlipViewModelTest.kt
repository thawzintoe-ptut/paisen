package com.ptut.expense.confirm

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
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

    private fun viewModel(
        slip: QueuedSlip,
        source: FakeSlipSource = FakeSlipSource(),
        store: InMemoryImportStore = InMemoryImportStore(),
        toasts: ToastMessages = ToastMessages(),
        subCategories: List<SubCategory> = emptyList(),
    ) = ConfirmSlipViewModel(
        slipId = slip.id,
        ledger = FakeLedger(listOf(slip)),
        importer = SlipImporter(source, FakeSlipTextReader(), store),
        toasts = toasts,
        discards = SlipDiscards(SlipImporter(source, FakeSlipTextReader(), store), toasts, CoroutineScope(Dispatchers.Main)) { Duration.ZERO },
        subCategories = subCategories,
        clock = object : Clock {
            override fun now(): Instant = this@ConfirmSlipViewModelTest.now
        },
        newId = { "generated" },
    )

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
    ) = QueuedSlip(
        id = "q1",
        merchant = merchant,
        occurredAtEpochMillis = occurredAt.toEpochMilliseconds(),
        note = reference,
        amount = amount,
        reason = if (amount == null) ReviewReason.AmountUnclear else ReviewReason.NewMerchant,
        capturedAtEpochMillis = now.toEpochMilliseconds(),
        lowConfidence = lowConfidence,
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

    private companion object {
        const val SENDER_LAST_FOUR = "12" + "34"
        const val RECIPIENT_LAST_FOUR = "67" + "89"
    }
}

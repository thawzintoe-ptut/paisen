package com.ptut.expense.edit

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.ledger.EditTransactionUseCase
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.ledger.rows
import com.ptut.expense.domain.record.ObserveShareOptionsUseCase
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.TitleChange
import com.ptut.expense.domain.shared.TransactionSeed
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.BACKSPACE
import com.ptut.expense.ui.MetaField
import com.ptut.expense.ui.inAppLanguage
import com.ptut.expense.ui.press
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The row page 11c is captured on: ฿642.50 at Foodland, filed against its own slip line. */
private val filedSlip = Transaction(
    id = "t1",
    occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 18, 30)
        .toInstant(BangkokTime).toEpochMilliseconds(),
    amount = Satang(64_250),
    direction = Direction.Expense,
    categoryId = "groceries",
    counterparty = "Foodland Rama IV",
    source = TransactionSource.Scan,
    reference = "0138K4",
    slipAmount = Satang(64_250),
)

/** No slip, so no reading to quote and no reference to show. #53's second branch. */
private val handTyped = Transaction(
    id = "t2",
    occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 9, 0)
        .toInstant(BangkokTime).toEpochMilliseconds(),
    amount = Satang(12_000),
    direction = Direction.Expense,
    categoryId = "food",
    counterparty = null,
    source = TransactionSource.Manual,
)

/** A slip the parser read an amount off but no reference. Both are nullable at import. */
private val slipWithoutReference = filedSlip.copy(id = "t3", reference = null)

/** Groceries › Supermarket, the pair page 11c draws. */
private val filedUnderSupermarket = filedSlip.copy(id = "t4", subCategoryId = "supermarket")

private val supermarket = SubCategory("supermarket", "groceries", "Supermarket")
private val market = SubCategory("market", "groceries", "Market")
private val bts = SubCategory("bts", "transport", "BTS")

private val transport = Category.seed().first { it.id == "transport" }
private val groceries = Category.seed().first { it.id == "groceries" }

/** Builds the screen over this file's fakes, as Koin builds it over the app's graph. */
private fun EditTransactionViewModel(
    transactionId: String,
    ledger: LedgerRepository,
    known: List<Category> = Category.seed(),
    subCategories: List<SubCategory> = emptyList(),
    shared: SharedRepository? = null,
): EditTransactionViewModel = EditTransactionViewModel(
    transactionId = transactionId,
    observeTransactions = ObserveTransactionsUseCase(ledger),
    known = known,
    subCategories = subCategories,
    shareOptions = ObserveShareOptionsUseCase(shared),
    editTransaction = EditTransactionUseCase(ledger, shared),
)

class EditTransactionViewModelTest : ViewModelTest() {

    /**
     * An empty ledger is Room before it has answered, not a row that went — the reader reaches
     * this screen from the record, so there was one.
     */
    /** Hick's law: the row's category leads, the two most filed follow, the rest wait behind More. */
    @Test
    fun `the chips lead with the row's category and then what the ledger files most`() = runTest {
        val rows = listOf(filedSlip) +
            List(3) { filedSlip.copy(id = "bus$it", categoryId = "transport", slipAmount = null, reference = null) } +
            List(2) { filedSlip.copy(id = "bill$it", categoryId = "bills", slipAmount = null, reference = null) }

        EditTransactionViewModel("t1", FakeLedger(rows)).uiState.test {
            assertEquals(EditTransactionUiState.Loading, awaitItem())
            val chips = (awaitItem() as EditTransactionUiState.Loaded).chips
            assertEquals(listOf("groceries", "transport", "bills"), chips.shown.map { it.id })
            assertEquals(3, chips.moreCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a ledger that has not answered yet stays loading`() = runTest {
        EditTransactionViewModel("t1", FakeLedger()).uiState.test {
            assertEquals(EditTransactionUiState.Loading, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** #52 can delete the row from another screen while this one is open. */
    @Test
    fun `an id nothing answers to reads as missing`() = runTest {
        EditTransactionViewModel("gone", FakeLedger(listOf(filedSlip))).uiState.test {
            assertEquals(EditTransactionUiState.Loading, awaitItem())
            assertEquals(EditTransactionUiState.Missing, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the screen opens on the record with save inert`() = runTest {
        loaded { state ->
            assertEquals("642.50", state.entry.display)
            assertEquals("groceries", state.categoryId)
            assertNull(state.memo)
            assertFalse(state.canSave, "Save was live before anything had changed")
        }
    }

    @Test
    fun `a different amount lights save`() = runTest {
        afterChanging(change = { type(AmountEntry.of(Satang(70_000))) }) { state ->
            assertEquals("700.00", state.entry.display)
            assertTrue(state.canSave)
        }
    }

    @Test
    fun `a different category lights save`() = runTest {
        afterChanging(change = { choose(transport) }) { state ->
            assertEquals("transport", state.categoryId)
            assertTrue(state.canSave)
        }
    }

    @Test
    fun `a note lights save and is kept trimmed`() = runTest {
        afterChanging(change = { setMemo("  Weekly shop  ") }) { state ->
            assertTrue(state.canSave)
        }
    }

    /**
     * The screen opens on the row's own figure and the keypad under it then refused every key
     * but backspace: a seeded entry already carries its two satang, which is the pad's ceiling.
     */
    @Test
    fun `the amount the screen opens on can be typed over on the pad under it`() = runTest {
        afterChanging(
            change = {
                val onScreen = AmountEntry.of(Satang(64_250))
                type("700".fold(onScreen) { entry, key -> entry.press(key.toString()) })
            },
        ) { state ->
            assertEquals("700", state.entry.display)
            assertTrue(state.canSave)
        }
    }

    @Test
    fun `backspace corrects the amount in place rather than clearing it`() = runTest {
        afterChanging(
            change = { type(AmountEntry.of(Satang(64_250)).press(BACKSPACE).press("9")) },
        ) { state ->
            assertEquals("642.59", state.entry.display)
            assertTrue(state.canSave)
        }
    }

    /**
     * #53's watch-out: "inert until something actually changes" means comparing values, not
     * tracking touches. 642.5 and 642.50 are the same money, so the pad may not light Save.
     */
    @Test
    fun `retyping the amount that is already there is not a change`() = runTest {
        afterChanging(change = { type(AmountEntry().press("6").press("4").press("2").press(".").press("5")) }) { state ->
            assertFalse(state.canSave, "the same amount typed differently lit Save")
        }
    }

    /** #6: an emptied amount is not ฿0.00, so Save stays dark and a save that still arrives writes nothing. */
    @Test
    fun `an emptied amount cannot be saved`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        afterChanging(
            ledger = ledger,
            change = {
                type(AmountEntry())
                save()
            },
        ) { state ->
            assertFalse(state.canSave, "an empty amount lit Save")
        }
        runCurrent()

        val row = ledger.allTransactions.value.rows().single()
        assertEquals(Satang(64_250), row.amount)
        assertNull(row.editedAt, "an empty amount was written")
    }

    @Test
    fun `saving writes the three fields and leaves the evidence alone`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val viewModel = EditTransactionViewModel("t1", ledger)
        viewModel.uiState.test {
            skipItems(1)
            viewModel.type(AmountEntry.of(Satang(70_000)))
            viewModel.choose(transport)
            viewModel.setMemo("  Airport taxi  ")
            viewModel.save()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        val saved = ledger.allTransactions.value.rows().single { it.id == "t1" }
        assertEquals(Satang(70_000), saved.amount)
        assertEquals("transport", saved.categoryId)
        // Trimmed, because the reader typed spaces around it and nobody meant them.
        assertEquals("Airport taxi", saved.memo)

        assertEquals(filedSlip.counterparty, saved.counterparty)
        assertEquals(filedSlip.reference, saved.reference)
        assertEquals(filedSlip.occurredAtEpochMillis, saved.occurredAtEpochMillis)
        assertEquals(filedSlip.source, saved.source)
        assertEquals(filedSlip.slipAmount, saved.slipAmount)
    }

    /** #10's Edited filter reads this field, so it has to mean somebody changed something. */
    @Test
    fun `the edited stamp is set on save and only on save`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val viewModel = EditTransactionViewModel("t1", ledger)
        viewModel.uiState.test {
            skipItems(1)
            viewModel.choose(transport)
            runCurrent()
            assertNull(
                ledger.allTransactions.value.rows().single().editedAt,
                "the row was marked edited before anything was saved",
            )

            viewModel.save()
            runCurrent()
            assertNotNull(ledger.allTransactions.value.rows().single().editedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row filed under a sub-category opens with save inert`() = runTest {
        loaded(filedUnderSupermarket) { state ->
            assertFalse(state.canSave, "Save was live before anything had changed")
        }
    }

    @Test
    fun `saving a new note keeps the sub-category the row already has`() = runTest {
        val ledger = FakeLedger(listOf(filedUnderSupermarket))
        val viewModel = EditTransactionViewModel("t4", ledger)
        viewModel.uiState.test {
            skipItems(1)
            viewModel.setMemo("weekly shop")
            runCurrent()
            viewModel.save()
            runCurrent()
            assertEquals("supermarket", ledger.allTransactions.value.rows().single().subCategoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** p34: a sub-category belongs to one parent, so a new parent leaves it behind. */
    @Test
    fun `picking another category clears the sub-category`() = runTest {
        val ledger = FakeLedger(listOf(filedUnderSupermarket))
        val viewModel = EditTransactionViewModel("t4", ledger)
        viewModel.uiState.test {
            skipItems(1)
            viewModel.choose(transport)
            runCurrent()
            viewModel.save()
            runCurrent()
            assertNull(ledger.allTransactions.value.rows().single().subCategoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row opens with its category's sub-categories and its own one chosen`() = runTest {
        withSubCategories { viewModel, _ ->
            val state = awaitItem() as EditTransactionUiState.Loaded
            assertEquals(listOf(supermarket, market), state.subCategories)
            assertEquals("supermarket", state.subCategoryId)
            assertFalse(state.canSave)
        }
    }

    @Test
    fun `a different sub-category lights save and is saved`() = runTest {
        withSubCategories { viewModel, ledger ->
            skipItems(1)
            viewModel.chooseSubCategory(market)
            assertTrue((awaitItem() as EditTransactionUiState.Loaded).canSave)

            viewModel.save()
            runCurrent()
            assertEquals("market", ledger.allTransactions.value.rows().single().subCategoryId)
        }
    }

    @Test
    fun `tapping the chosen sub-category again clears it`() = runTest {
        withSubCategories { viewModel, _ ->
            skipItems(1)
            viewModel.chooseSubCategory(supermarket)

            val state = awaitItem() as EditTransactionUiState.Loaded
            assertNull(state.subCategoryId)
            assertTrue(state.canSave)
        }
    }

    @Test
    fun `a new category offers its own sub-categories`() = runTest {
        withSubCategories { viewModel, _ ->
            skipItems(1)
            viewModel.choose(transport)

            val state = awaitItem() as EditTransactionUiState.Loaded
            assertEquals(listOf(bts), state.subCategories)
            assertNull(state.subCategoryId)
        }
    }

    /** The second guard behind the button: a no-op save must not stamp the row edited. */
    @Test
    fun `saving nothing writes nothing`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val viewModel = EditTransactionViewModel("t1", ledger)
        viewModel.uiState.test {
            skipItems(1)
            viewModel.save()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(filedSlip, ledger.allTransactions.value.rows().single())
        assertFalse(viewModel.isSaved.value, "the screen closed on a save that never happened")
    }

    /** Sourced from the slip, never recomputed: after a correction the two figures differ. */
    @Test
    fun `the slip line quotes what the slip read and not what is typed`() = runTest {
        afterChanging(change = { type(AmountEntry.of(Satang(70_000))) }) { state ->
            assertEquals("Read from the slip as ฿642.50", state.slipRead)
        }
    }

    @Test
    fun `a hand typed row shows no slip line`() = runTest {
        loaded(handTyped) { state ->
            assertNull(state.slipRead)
        }
    }

    /** They are evidence, not preferences: drawn so the reader sees what they correct against. */
    @Test
    fun `the merchant and the reference render as evidence and the time is a field`() = runTest {
        loaded { state ->
            assertEquals(listOf(MetaField.Merchant, MetaField.Reference), state.evidence.map { it.field })
            assertEquals(listOf("Foodland Rama IV", "0138K4"), state.evidence.map { it.value })
            assertEquals(filedSlip.occurredAtEpochMillis, state.occurredAtEpochMillis)
        }
    }

    /** No shop and no reference on a hand-typed row, so both lines go rather than reading as blank fields. */
    @Test
    fun `a hand typed row carries no reference line`() = runTest {
        loaded(handTyped) { state ->
            assertEquals(emptyList(), state.evidence.map { it.field })
        }
    }

    @Test
    fun `the notice names what came from the slip`() = runTest {
        loaded { state ->
            assertTrue(state.notice.startsWith("Shop and reference came from the slip"))
            assertTrue(state.notice.contains("marked edited"))
        }
    }

    /** Claiming a slip a hand-typed row never had is a confident wrong answer about their data. */
    @Test
    fun `a hand typed row is not told about a slip it never had`() = runTest {
        loaded(handTyped) { state ->
            assertFalse(state.notice.contains("came from the slip"))
            assertTrue(state.notice.contains("marked edited"))
        }
    }

    /** The notice named a row the screen does not draw, which is the fault it warns about. */
    @Test
    fun `a slip with no reference is not told its reference stays as read`() = runTest {
        loaded(slipWithoutReference) { state ->
            assertFalse(state.evidence.any { it.field == MetaField.Reference })
            assertTrue(state.notice.startsWith("The shop came from the slip"))
            assertFalse(state.notice.contains("reference"))
        }
    }

    @Test
    fun `opening the note sheet is not a change`() = runTest {
        val viewModel = EditTransactionViewModel("t1", FakeLedger(listOf(filedSlip)))
        viewModel.uiState.test {
            skipItems(1)
            viewModel.openMemo()
            val opened = awaitItem() as EditTransactionUiState.Loaded
            assertTrue(opened.editingMemo)
            assertFalse(opened.canSave, "opening the note counted as a change")

            viewModel.closeMemo()
            assertFalse((awaitItem() as EditTransactionUiState.Loaded).editingMemo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * FR-13, and the answer #53 gives to #27: recategorising one row says nothing about the
     * merchant. The guard is structural — this holder is handed no rule seam at all — and the test
     * is here so that the day somebody wires one in, it fails rather than quietly rewriting a rule.
     */
    @Test
    fun `editing a category leaves the shop rule untouched`() = runTest {
        val rules = RecordingRules(
            listOf(RememberedMerchant("r1", "Foodland Rama IV", "groceries", 12)),
        )
        val before = rules.rules.value
        val viewModel = EditTransactionViewModel("t1", FakeLedger(listOf(filedSlip)))

        viewModel.uiState.test {
            skipItems(1)
            viewModel.choose(transport)
            viewModel.save()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(before, rules.rules.value)
        assertEquals(0, rules.writes, "the edit reached the shop rules")
    }

    @Test
    fun `a group copy that cannot follow still leaves the edit saved`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val viewModel = EditTransactionViewModel(
            transactionId = "t1",
            ledger = ledger,
            shared = TitleRecordingShared(failFollow = true),
        )

        viewModel.uiState.test {
            skipItems(1)
            viewModel.choose(transport)
            viewModel.save()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(viewModel.isSaved.value)
        assertEquals("transport", ledger.allTransactions.value.rows().single().categoryId)
    }

    /** Shared titles are stored in English, so a rename made in Burmese has to compare English names. */
    @Test
    fun `a category change made in Burmese renames the shared title by its English names`() = runTest {
        AppText.use(Language.Burmese)
        val shared = TitleRecordingShared()
        val viewModel = EditTransactionViewModel(
            transactionId = "t1",
            ledger = FakeLedger(listOf(filedSlip)),
            known = Category.seed().map(Category::inAppLanguage),
            shared = shared,
        )

        viewModel.uiState.test {
            skipItems(1)
            viewModel.choose(transport.inAppLanguage())
            viewModel.save()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(TitleChange(from = "Groceries", to = "Transport"), shared.titleChanges.single())
    }

    /**
     * Opens the screen on [row], applies [change], and hands back what the screen then draws.
     * The state flow only lives while something collects it, so every assertion runs inside one.
     */
    private suspend fun withSubCategories(
        test: suspend app.cash.turbine.TurbineTestContext<EditTransactionUiState>.(
            EditTransactionViewModel,
            FakeLedger,
        ) -> Unit,
    ) {
        val ledger = FakeLedger(listOf(filedUnderSupermarket))
        val viewModel = EditTransactionViewModel(
            transactionId = "t4",
            ledger = ledger,
            subCategories = listOf(supermarket, market, bts),
        )
        viewModel.uiState.test {
            skipItems(1)
            test(viewModel, ledger)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun afterChanging(
        row: Transaction = filedSlip,
        ledger: FakeLedger = FakeLedger(listOf(row)),
        change: EditTransactionViewModel.() -> Unit,
        assertions: (EditTransactionUiState.Loaded) -> Unit,
    ) {
        val viewModel = EditTransactionViewModel(row.id, ledger)
        viewModel.uiState.test {
            skipItems(1)
            viewModel.change()
            assertions(awaitItem() as EditTransactionUiState.Loaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Settles the ledger's first emission, then hands the loaded screen to the assertions. */
    private suspend fun loaded(
        row: Transaction = filedSlip,
        assertions: (EditTransactionUiState.Loaded) -> Unit,
    ) {
        EditTransactionViewModel(row.id, FakeLedger(listOf(row))).uiState.test {
            assertEquals(EditTransactionUiState.Loading, awaitItem())
            assertions(awaitItem() as EditTransactionUiState.Loaded)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Counts rather than throws, so a failure names what the edit did instead of where it died. */
private class RecordingRules(initial: List<RememberedMerchant>) : RuleRepository {
    var writes: Int = 0
        private set

    override val rules: StateFlow<List<RememberedMerchant>> = MutableStateFlow(initial)

    override suspend fun recategorise(id: String, categoryId: String) {
        writes++
    }

    override suspend fun forget(id: String) {
        writes++
    }

    override suspend fun rename(id: String, displayName: String?) {
        writes++
    }
}

/** Only the follow is answered; any other call is the edit screen reaching past its job. */
private class TitleRecordingShared(private val failFollow: Boolean = false) : SharedRepository {
    val titleChanges = mutableListOf<TitleChange?>()

    override val snapshot: StateFlow<SharedSnapshot> = MutableStateFlow(SharedSnapshot.Empty)

    override suspend fun followTransaction(transactionId: String, before: TransactionEdit, titleChange: TitleChange?) {
        if (failFollow) error("offline")
        titleChanges += titleChange
    }

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String = unused()
    override suspend fun addMembers(groupId: String, members: List<NewMember>) = unused()
    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = unused()
    override suspend fun setPromptPay(groupId: String, promptPayId: String?) = unused()
    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) = unused()
    override suspend fun deleteGroup(groupId: String) = unused()
    override suspend fun setPersonArchived(personId: String, archived: Boolean) = unused()
    override suspend fun removeMember(groupId: String, memberId: String) = unused()
    override suspend fun saveExpense(draft: ExpenseDraft): String = unused()
    override suspend fun removeExpense(expenseId: String) = unused()
    override suspend fun recordPayment(draft: PaymentDraft): String = unused()
    override suspend fun transactionSeed(transactionId: String): TransactionSeed? = unused()
    override suspend fun removePayment(paymentId: String) = unused()
    override suspend fun putBackPayment(paymentId: String) = unused()
    override suspend fun restoreExpense(expenseId: String) = unused()
    override suspend fun relink(expenseId: String, transactionId: String) = unused()
    override suspend fun saveRepeat(rule: RepeatRule): String = unused()
    override suspend fun stopRepeat(ruleId: String) = unused()
    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = unused()
    override suspend fun addDueRepeats(today: LocalDate): Int = unused()
    override suspend fun lastRate(currency: String, into: String): Rate? = unused()
    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = unused()

    private fun unused(): Nothing = error("Edit transaction only follows a transaction")
}

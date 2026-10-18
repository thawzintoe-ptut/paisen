package com.ptut.expense.detail

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.rows
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A Wednesday. The fixture below falls on Friday 14 August, which puts it inside this month and
 * outside this week — the case #52 names as the trap in the copy.
 */
private val today = LocalDateTime(2026, 8, 19, 9, 0).toInstant(BangkokTime)

private val fixedClock = object : Clock {
    override fun now(): Instant = today
}

class TransactionDetailViewModelTest : ViewModelTest() {

    /**
     * The bug the seam was hiding. A ledger that has answered and holds nothing is not a ledger
     * that has not answered — delete the only transaction (#52), come back through a restored
     * back stack, and the record is genuinely gone rather than still loading.
     */
    @Test
    fun `an answered ledger holding nothing reads as missing rather than loading`() = runTest {
        val answeredAndEmpty = FakeLedger(emptyList())

        TransactionDetailViewModel("t1", answeredAndEmpty, ToastMessages()).uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            assertEquals(TransactionDetailUiState.Missing, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A cold open, before Room has produced a first result. The pair with the test above is the
     * whole point of the seam: this one must not say "gone", and that one must.
     */
    @Test
    fun `a ledger that has not answered yet stays loading`() = runTest {
        val neverAnswered = FakeLedger.unanswered()

        TransactionDetailViewModel("t1", neverAnswered, ToastMessages()).uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A ledger with rows but not this one. Restoring a back stack after #52 deleted the row is
     * the real path here, and a blank record under a live Delete button would offer to delete
     * something that is already gone.
     */
    @Test
    fun `an id nothing answers to reads as missing`() = runTest {
        TransactionDetailViewModel("gone", FakeLedger(listOf(filedSlip)), ToastMessages()).uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            assertEquals(TransactionDetailUiState.Missing, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * FR-12's correction had nowhere to live once rev 5 took the shops screen away. The record
     * names the rule that filed it and leads to the one screen that may change it.
     */
    @Test
    fun `a row a rule filed offers the way to correct that rule`() = runTest {
        val rule = RememberedMerchant("r1", "Foodland Rama IV", "groceries", slipCount = 12)

        loaded(filedSlip.copy(ruleId = "r1"), rules = listOf(rule)) { state ->
            assertEquals("Foodland Rama IV", state.shopRule?.shop)
            assertEquals("Groceries", state.shopRule?.filesUnder)
        }
    }

    /** A shop the app was never taught has no rule, so there is nothing to correct. */
    @Test
    fun `a row no rule filed offers nothing to correct`() = runTest {
        loaded(filedSlip, rules = emptyList()) { state ->
            assertNull(state.shopRule)
        }
    }

    /** The engine matches on a trimmed lowercase key, and so does the record. */
    @Test
    fun `the rule is found however the slip printed the name`() = runTest {
        val rule = RememberedMerchant("r1", "  foodland rama iv ", "groceries", slipCount = 3)

        loaded(filedSlip, rules = listOf(rule)) { state ->
            assertEquals("Foodland Rama IV stays sorted into Groceries. Only this one purchase is removed.", state.ruleFooter)
        }
    }

    @Test
    fun `the record maps to every line the screen draws`() = runTest {
        loaded(filedSlip) { state ->
            assertEquals("Foodland Rama IV", state.merchant)
            // No sign: the amount's colour says whether the money went out or came in.
            assertEquals("฿", state.amount.symbol)
            assertEquals("642", state.amount.whole)
            assertEquals(".50", state.amount.fraction)
            assertEquals("local_grocery_store", state.categoryIcon)

            assertEquals(
                listOf("CATEGORY", "DATE", "SOURCE", "REFERENCE"),
                state.rows.map { it.label },
            )
            assertEquals(
                listOf("Groceries", "Fri 14 Aug, 18:30", "K PLUS slip", "0138K4"),
                state.rows.map { it.value },
            )
        }
    }

    @Test
    fun `the category row names the sub-category beside its parent`() = runTest {
        loaded(
            filedSlip.copy(subCategoryId = "supermarket"),
            subCategories = listOf(SubCategory("supermarket", "groceries", "Supermarket")),
        ) { state ->
            assertEquals("Groceries › Supermarket", state.rows.single { it.label == "CATEGORY" }.value)
        }
    }

    /** An auto-filed row somebody later corrected is still auto-filed — two facts, two clauses. */
    @Test
    fun `an edited row keeps where it came from and names the correction`() = runTest {
        val corrected = filedSlip.copy(
            editedAt = LocalDateTime(2026, 8, 15, 9, 5).toInstant(BangkokTime).toEpochMilliseconds(),
        )

        loaded(corrected) { state ->
            assertEquals(
                "K PLUS slip · edited 15 Aug",
                state.rows.single { it.label == "SOURCE" }.value,
            )
        }
    }

    /**
     * The table drops a deleted category's link and keeps the spending, so a row really can
     * arrive here with none. Naming the absence beats an empty line where a word should be.
     */
    @Test
    fun `a category that is gone leaves the row uncategorised rather than blank`() = runTest {
        loaded(filedSlip.copy(categoryId = null)) { state ->
            assertEquals("Uncategorised", state.rows.single { it.label == "CATEGORY" }.value)
            assertEquals("more_horiz", state.categoryIcon)
        }
    }

    /**
     * NFR-4 deletes the picture once the row is durable, so the reader has to be told before they
     * tap — nothing can be re-read afterwards.
     */
    @Test
    fun `the warning names the slip image and only the totals that really move`() = runTest {
        loaded(filedSlip) { state ->
            assertEquals(
                "The slip image for this transaction was already deleted. " +
                    "Deleting it here takes ฿642.50 off this month.",
                state.consequence,
            )
        }
    }

    /** The row's own Bangkok day decides this, so a row filed today moves all three. */
    @Test
    fun `a row from today names all three totals`() = runTest {
        loaded(filedSlip.on(2026, 8, 19)) { state ->
            assertTrue(
                state.consequence.endsWith("takes ฿642.50 off today, this week and this month."),
                state.consequence,
            )
        }
    }

    /**
     * A row old enough to sit outside all three still leaves the ledger. Printing the periods
     * regardless would tell the reader a total is about to move when it is not.
     */
    @Test
    fun `a row from last month says the three totals do not change`() = runTest {
        loaded(filedSlip.on(2026, 7, 30)) { state ->
            assertTrue(
                state.consequence.endsWith(
                    "takes ฿642.50 off your history. " +
                        "Today, this week and this month do not change.",
                ),
                state.consequence,
            )
        }
    }

    /**
     * The delete sheet writes its own sentence, and it is not the screen's. The note behind warns
     * about the slip image; the sheet names the money and the totals. Showing the note's words in
     * the sheet printed the same paragraph twice, one on top of the other.
     */
    @Test
    fun `the sheet names the money and the totals it moves`() = runTest {
        loaded(filedSlip) { state ->
            assertEquals("฿642.50 comes off this month.", state.deleteMoves)
        }
    }

    @Test
    fun `the sheet names all three totals for a row filed today`() = runTest {
        loaded(filedSlip.on(2026, 8, 19)) { state ->
            assertEquals("฿642.50 comes off today, this week and this month.", state.deleteMoves)
        }
    }

    @Test
    fun `the sheet says so when no total moves`() = runTest {
        loaded(filedSlip.on(2026, 7, 30)) { state ->
            assertEquals(
                "฿642.50 comes off your history. Today, this week and this month do not change.",
                state.deleteMoves,
            )
        }
    }

    /** The screen's note is the one that carries NFR-4's warning, and it keeps carrying it. */
    @Test
    fun `the note behind the dialog is not the dialog's sentence`() = runTest {
        loaded(filedSlip) { state ->
            assertTrue(state.consequence.startsWith("The slip image"), state.consequence)
            assertFalse(state.deleteMoves in state.consequence, "the sheet repeats the note")
        }
    }

    /** FR-12, and the reason the footer exists: people assume delete undoes the learning too. */
    @Test
    fun `the footer promises the shop rule outlives the row`() = runTest {
        val rule = RememberedMerchant("r1", "Foodland Rama IV", "groceries", slipCount = 12)

        loaded(filedSlip, rules = listOf(rule)) { state ->
            assertEquals("Foodland Rama IV stays sorted into Groceries. Only this one purchase is removed.", state.ruleFooter)
        }
    }

    /**
     * A row nobody imported has no slip and taught no rule, so both promises change. Claiming a
     * deleted picture or a surviving shop rule here would be a confident wrong answer about
     * the reader's own data — on the screen whose next action is destructive.
     */
    @Test
    fun `a row added by hand promises nothing about a slip or a rule`() = runTest {
        val byHand = filedSlip.copy(
            source = TransactionSource.Manual,
            counterparty = null,
            reference = null,
        )

        loaded(byHand) { state ->
            assertEquals("Groceries", state.merchant)
            assertEquals("Added by hand", state.rows.single { it.label == "SOURCE" }.value)
            assertEquals("—", state.rows.single { it.label == "REFERENCE" }.value)
            assertEquals(
                "Deleting it here takes ฿642.50 off this month.",
                state.consequence,
            )
            assertEquals(
                "Only this row goes. Nothing added by hand teaches a shop rule.",
                state.ruleFooter,
            )
        }
    }

    /**
     * The screen observes rather than reads once, so #52 deleting the row from the step after
     * this one reaches it. A holder that had taken a snapshot would keep drawing a live Delete
     * button over a record that no longer exists.
     */
    @Test
    fun `a row that goes while the screen is open turns it to missing`() = runTest {
        val other = filedSlip.copy(id = "t2", counterparty = "Café Amazon")
        val ledger = FakeLedger(listOf(filedSlip, other))

        TransactionDetailViewModel(filedSlip.id, ledger, ToastMessages()).uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            assertTrue(awaitItem() is TransactionDetailUiState.Loaded)

            ledger.rewrite(listOf(other))

            assertEquals(TransactionDetailUiState.Missing, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The card sets the amount in three sizes, so TalkBack would otherwise read it as three
     * separate figures — and "−" as "dash". One sentence carries the whole card instead.
     */
    @Test
    fun `the card is spoken as one sentence rather than three figures`() = runTest {
        loaded(filedSlip) { state ->
            assertEquals("Foodland Rama IV, 642.50 baht spent", state.spoken)
        }
    }

    private suspend fun loaded(
        transaction: Transaction,
        rules: List<RememberedMerchant> = emptyList(),
        subCategories: List<SubCategory> = emptyList(),
        assert: (TransactionDetailUiState.Loaded) -> Unit,
    ) = TransactionDetailViewModel(
        transactionId = transaction.id,
        ledger = FakeLedger(listOf(transaction)),
        toasts = ToastMessages(),
        subCategories = subCategories,
        clock = fixedClock,
        rules = StaticRules(rules),
    ).uiState.test {
        var state = awaitItem()
        while (state == TransactionDetailUiState.Loading) state = awaitItem()
        assertTrue(state is TransactionDetailUiState.Loaded, "still $state")
        assert(state)
        cancelAndIgnoreRemainingEvents()
    }

    /**
     * FR-21. Delete is one tap away from a total nobody can see is wrong, so the tap asks first
     * and the ledger is not touched until the answer comes back.
     */
    @Test
    fun `asking to delete raises the dialog and leaves the row alone`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val viewModel = viewModel(ledger)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.askToDelete()
            assertTrue(awaitItem().loaded().confirmingDelete)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(filedSlip), ledger.allTransactions.value.rows())
    }

    /** The calm option describes the outcome, and the outcome is that nothing happens. */
    @Test
    fun `keeping it closes the dialog and deletes nothing`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val viewModel = viewModel(ledger)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.askToDelete()
            awaitItem()
            viewModel.keepIt()
            assertFalse(awaitItem().loaded().confirmingDelete)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(filedSlip), ledger.allTransactions.value.rows())
    }

    @Test
    fun `confirming removes the row and offers it back`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val toasts = ToastMessages()
        val viewModel = viewModel(ledger, toasts)

        toasts.messages.test {
            viewModel.uiState.test {
                awaitLoaded()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.confirmDelete()
            runCurrent()

            assertEquals(emptyList<Transaction>(), ledger.allTransactions.value.rows())
            val toast = awaitItem()
            assertEquals("Deleted Foodland Rama IV", toast.text)
            assertEquals("Undo", toast.action?.label)
        }
    }

    /**
     * The shell pops on this, so it must not be true before the row is actually gone and the undo
     * is actually offered. Popping first would destroy this holder and cancel both mid-flight.
     */
    @Test
    fun `the screen is told to leave only once the row is gone and the undo is out`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val toasts = ToastMessages()
        val viewModel = viewModel(ledger, toasts)

        toasts.messages.test {
            viewModel.uiState.test {
                awaitLoaded()
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(viewModel.isDeleted.value, "the screen was told to leave before the tap")

            viewModel.confirmDelete()
            runCurrent()

            assertEquals(emptyList<Transaction>(), ledger.allTransactions.value.rows())
            assertEquals("Undo", awaitItem().action?.label)
            assertTrue(viewModel.isDeleted.value, "the screen was never told the row had gone")
        }
    }

    /** The whole point of the window: the record comes back, category and all, not a copy of it. */
    @Test
    fun `undo puts the same row back`() = runTest {
        val ledger = FakeLedger(listOf(filedSlip))
        val toasts = ToastMessages()
        val viewModel = viewModel(ledger, toasts)

        toasts.messages.test {
            viewModel.uiState.test {
                awaitLoaded()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.confirmDelete()
            runCurrent()

            awaitItem().action!!.onAction()
            runCurrent()

            assertEquals(listOf(filedSlip), ledger.allTransactions.value.rows())
        }
    }

    private fun viewModel(ledger: FakeLedger, toasts: ToastMessages = ToastMessages()) =
        TransactionDetailViewModel(
            transactionId = filedSlip.id,
            ledger = ledger,
            toasts = toasts,
            clock = fixedClock,
        )

    private suspend fun ReceiveTurbine<TransactionDetailUiState>.awaitLoaded(): TransactionDetailUiState.Loaded {
        var state = awaitItem()
        while (state == TransactionDetailUiState.Loading) state = awaitItem()
        return state.loaded()
    }

    private fun TransactionDetailUiState.loaded(): TransactionDetailUiState.Loaded {
        assertTrue(this is TransactionDetailUiState.Loaded, "still $this")
        return this
    }

    private fun Transaction.on(year: Int, month: Int, day: Int) = copy(
        occurredAtEpochMillis = LocalDateTime(year, month, day, 18, 30)
            .toInstant(BangkokTime)
            .toEpochMilliseconds(),
    )

    private val filedSlip = Transaction(
        id = "t1",
        occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 18, 30)
            .toInstant(BangkokTime)
            .toEpochMilliseconds(),
        amount = Satang(64_250),
        direction = Direction.Expense,
        categoryId = "groceries",
        counterparty = "Foodland Rama IV",
        source = TransactionSource.Scan,
        reference = "0138K4",
    )
}

/** The record only reads rules; a write from here would be the screen reaching past its job. */
private class StaticRules(held: List<RememberedMerchant>) : RuleRepository {
    override val rules: StateFlow<List<RememberedMerchant>> = MutableStateFlow(held)

    override suspend fun recategorise(id: String, categoryId: String): Unit =
        error("The record never changes a rule; the shop rule screen does")

    override suspend fun forget(id: String): Unit =
        error("The record never forgets a rule; the shop rule screen does")

    override suspend fun rename(id: String, displayName: String?): Unit =
        error("The record never names a rule; the shop rule screen does")
}

package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RecordingSharedRepository
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerWriteUseCasesTest {

    private val lunch = Transaction(
        id = "t1",
        occurredAtEpochMillis = 0,
        amount = Satang(15_500),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "LAWSON",
        source = TransactionSource.Scan,
    )

    private val categories = listOf(Category(id = "food", name = "Food & drink", icon = "restaurant"), Category(id = "travel", name = "Travel", icon = "flight"))

    // Rates and records

    @Test
    fun aForeignRowSavesItsRateAsTheCurrencysLast() = runTest {
        val ledger = RecordingLedger()
        val rates = MapRates()
        val usd = ForeignAmount("USD", 1_250, Rate(334, 1))

        RecordTransactionUseCase(ledger, rates)(TransactionDraft(amount = usd.inHome, direction = Direction.Expense, categoryId = "food", foreign = usd))

        assertEquals(1, ledger.recorded.size)
        assertEquals(Rate(334, 1), rates.saved["USD"])
    }

    @Test
    fun aHomeRowSavesNoRate() = runTest {
        val rates = MapRates()

        RecordTransactionUseCase(RecordingLedger(), rates)(TransactionDraft(amount = Satang(15_500), direction = Direction.Expense, categoryId = "food"))

        assertTrue(rates.saved.isEmpty())
    }

    @Test
    fun aRateThatFailsToSaveKeepsTheRow() = runTest {
        val ledger = RecordingLedger()
        val usd = ForeignAmount("USD", 1_250, Rate(334, 1))

        RecordTransactionUseCase(ledger, MapRates(failSave = true))(TransactionDraft(amount = usd.inHome, direction = Direction.Expense, categoryId = "food", foreign = usd))

        assertEquals(1, ledger.recorded.size)
    }

    @Test
    fun theLastRateComesFromTheBookOrIsNone() = runTest {
        assertEquals(Rate(334, 1), GetLastRateUseCase(MapRates(mutableMapOf("USD" to Rate(334, 1))))("USD"))
        assertNull(GetLastRateUseCase(null)("USD"))
    }

    // Edit

    @Test
    fun anEditLandsAndTheSharedCopyTakesTheNewCategorysName() = runTest {
        val ledger = RecordingLedger(listOf(lunch))
        val shared = RecordingSharedRepository()

        EditTransactionUseCase(ledger, shared)(lunch, TransactionEdit(Satang(15_500), categoryId = "travel", memo = null), categories)

        assertEquals("travel", ledger.rows().single().categoryId)
        assertEquals(listOf("followTransaction t1 TitleChange(from=Food & drink, to=Travel)"), shared.calls)
    }

    @Test
    fun anEditThatKeepsTheCategoryChangesNoTitle() = runTest {
        val shared = RecordingSharedRepository()

        EditTransactionUseCase(RecordingLedger(listOf(lunch)), shared)(lunch, TransactionEdit(Satang(9_900), categoryId = "food", memo = "lunch"), categories)

        assertEquals(listOf("followTransaction t1 null"), shared.calls)
    }

    @Test
    fun aSharedCopyThatCannotFollowDoesNotUndoTheEdit() = runTest {
        val ledger = RecordingLedger(listOf(lunch))
        val shared = RecordingSharedRepository().apply { failNext = IllegalStateException("offline") }

        EditTransactionUseCase(ledger, shared)(lunch, TransactionEdit(Satang(9_900), categoryId = "food", memo = null), categories)

        assertEquals(Satang(9_900), ledger.rows().single().amount)
    }

    // Delete and Undo

    private val group = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null)),
    )

    private val lunchShared = SharedExpense(
        id = "e1",
        versionId = "v1",
        version = 1,
        groupId = "g1",
        description = "Lunch",
        occurredAtEpochMillis = 0,
        categoryId = "food",
        subCategoryId = null,
        note = null,
        currency = "THB",
        amountMinor = 15_500,
        rate = null,
        satang = Satang(15_500),
        payerMemberId = "me",
        splitMode = SplitMode.Equally,
        shares = listOf(Share("me", 1, 15_500, Satang(15_500))),
        authorMemberId = "me",
        createdAtEpochMillis = 0,
        removed = false,
    )

    private fun sharedLunch() = RecordingSharedRepository(SharedSnapshot.Empty.copy(groups = listOf(group), expenseVersions = listOf(lunchShared), links = mapOf("e1" to "t1")))

    @Test
    fun aSharedRowDeletedWithItsGroupCopySaysWhichGroup() = runTest {
        val ledger = RecordingLedger(listOf(lunch))
        val shared = sharedLunch()

        val receipt = DeleteTransactionUseCase(ledger, shared)(lunch, alsoRemoveShared = true)

        assertEquals(DeleteReceipt(lunch, sharedExpenseId = "e1", removedShared = true, groupName = "Flat 12"), receipt)
        assertTrue(ledger.rows().isEmpty())
        assertEquals(listOf("removeExpense e1"), shared.calls)
    }

    @Test
    fun anUntickedGroupCopyStays() = runTest {
        val shared = sharedLunch()

        val receipt = DeleteTransactionUseCase(RecordingLedger(listOf(lunch)), shared)(lunch, alsoRemoveShared = false)

        assertFalse(receipt.removedShared)
        assertTrue(shared.calls.isEmpty())
    }

    @Test
    fun aGroupCopyThatWillNotGoStillDeletesTheRow() = runTest {
        val ledger = RecordingLedger(listOf(lunch))
        val shared = sharedLunch().apply { failNext = IllegalStateException("offline") }

        val receipt = DeleteTransactionUseCase(ledger, shared)(lunch, alsoRemoveShared = true)

        assertFalse(receipt.removedShared)
        assertTrue(ledger.rows().isEmpty())
    }

    @Test
    fun undoPutsBackTheRowTheGroupCopyAndTheLink() = runTest {
        val ledger = RecordingLedger()
        val shared = sharedLunch()

        UndoDeleteTransactionUseCase(ledger, shared)(DeleteReceipt(lunch, "e1", removedShared = true, groupName = "Flat 12"))

        assertEquals(listOf(lunch), ledger.rows())
        assertEquals(listOf("restoreExpense e1", "relink e1 t1"), shared.calls)
    }

    @Test
    fun undoOfAnUnsharedRowOnlyPutsTheRowBack() = runTest {
        val ledger = RecordingLedger()
        val shared = sharedLunch()

        UndoDeleteTransactionUseCase(ledger, shared)(DeleteReceipt(lunch, null, removedShared = false, groupName = null))

        assertEquals(listOf(lunch), ledger.rows())
        assertTrue(shared.calls.isEmpty())
    }

    private class MapRates(val saved: MutableMap<String, Rate> = mutableMapOf(), private val failSave: Boolean = false) : RateBook {
        override suspend fun lastRate(currency: String): Rate? = saved[currency]

        override suspend fun saveRate(currency: String, rate: Rate) {
            if (failSave) error("disk")
            saved[currency] = rate
        }
    }
}

/** [FakeLedger] that also records new rows, and reads its rows back plainly. */
private class RecordingLedger(rows: List<Transaction> = emptyList(), private val base: FakeLedger = FakeLedger(rows)) : LedgerRepository by base {
    val recorded = mutableListOf<TransactionDraft>()

    override suspend fun record(draft: TransactionDraft) {
        recorded += draft
    }

    fun rows(): List<Transaction> = (base.allTransactions.value as LedgerSnapshot.Rows).transactions
}

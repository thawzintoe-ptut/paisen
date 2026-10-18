package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.entity.toDomain
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The slice-5 claim against a real engine rather than a fake DAO: a row typed by hand reaches
 * SQLite, and it is still there after the process that wrote it has gone.
 *
 * It lives here because `commonTest` runs on the Android host, which has no `Context` to open a
 * database with. Two things only a real engine can show: `transactions.categoryId` is a foreign
 * key, so this fails the moment the category seed stops running, and the insert is Room's own SQL
 * rather than a fake's Kotlin.
 */
class LedgerWriteTest {

    @Test
    fun aRecordedRowSurvivesTheDatabaseBeingClosedAndOpenedAgain() = runTest {
        val file = "${NSTemporaryDirectory()}ledger-write-${Random.nextLong()}.db"

        val first = openAt(file)
        try {
            DefaultLedgerRepository(
                transactionDao = first.transactionDao(),
                queuedSlipDao = first.queuedSlipDao(),
                importRunDao = first.importRunDao(),
                // Dies with the test, so the eagerly-started reads do not outlive it.
                scope = backgroundScope,
            ).record(
                TransactionDraft(
                    amount = Satang(48_500),
                    direction = Direction.Expense,
                    categoryId = "food",
                ),
            )
        } finally {
            first.close()
        }

        // A second open of the same file is the app after it has been killed and started again.
        val reopened = openAt(file)
        try {
            val saved = reopened.transactionDao().observeSince(0L).first().single()

            assertEquals(48_500L, saved.amountMinor)
            assertEquals("food", saved.categoryId)
            assertEquals(Direction.Expense.name, saved.direction)
            assertEquals(TransactionSource.Manual.name, saved.source)
            assertTrue(saved.id.isNotEmpty(), "the row was stored without an id")
        } finally {
            reopened.close()
        }
    }

    @Test
    fun aSubCategoryPickedWithTheRowIsStoredWithIt() = withLedger { ledger, database ->
        ledger.record(coffee(subCategoryId = "coffee"))

        assertEquals("coffee", database.transactionDao().all().single().subCategoryId)
    }

    /** p34: the finer label belongs to one parent. Under another it would split the wrong total. */
    @Test
    fun aSubCategoryFromAnotherCategoryIsNotStored() = withLedger { ledger, database ->
        ledger.record(coffee(categoryId = "transport", subCategoryId = "coffee"))

        val saved = database.transactionDao().all().single()
        assertEquals("transport", saved.categoryId)
        assertNull(saved.subCategoryId)
    }

    @Test
    fun anUndoneDeleteKeepsTheSubCategory() = withLedger { ledger, database ->
        ledger.record(coffee(subCategoryId = "coffee"))
        val victim = database.transactionDao().all().single().toDomain()

        ledger.delete(victim.id)
        ledger.restore(victim)

        assertEquals("coffee", database.transactionDao().all().single().subCategoryId)
    }

    @Test
    fun anEditCanFileTheRowUnderASubCategory() = withLedger { ledger, database ->
        ledger.record(coffee())
        val row = database.transactionDao().all().single()

        ledger.applyEdit(row.id, edit(categoryId = "food", subCategoryId = "coffee"))

        assertEquals("coffee", database.transactionDao().all().single().subCategoryId)
    }

    @Test
    fun movingTheRowToAnotherCategoryDropsTheOldSubCategory() = withLedger { ledger, database ->
        ledger.record(coffee(subCategoryId = "coffee"))
        val row = database.transactionDao().all().single()

        ledger.applyEdit(row.id, edit(categoryId = "transport", subCategoryId = "coffee"))

        val saved = database.transactionDao().all().single()
        assertEquals("transport", saved.categoryId)
        assertNull(saved.subCategoryId)
    }

    private fun withLedger(
        test: suspend (DefaultLedgerRepository, PtutDatabase) -> Unit,
    ): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        database.subCategoryDao().upsert(SubCategoryEntity(id = "coffee", categoryId = "food", name = "Coffee"))
        // Its own scope, stopped before the close: the ledger's always-on reads must not query a
        // closed database, which on iOS is a segfault rather than an exception.
        val reads = CoroutineScope(backgroundScope.coroutineContext + Job())
        val ledger = DefaultLedgerRepository(
            transactionDao = database.transactionDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            scope = reads,
        )
        try {
            test(ledger, database)
        } finally {
            reads.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }

    private fun coffee(categoryId: String = "food", subCategoryId: String? = null) = TransactionDraft(
        amount = Satang(6_500),
        direction = Direction.Expense,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
    )

    private fun edit(categoryId: String, subCategoryId: String?) = TransactionEdit.of(
        amount = Satang(6_500),
        categoryId = categoryId,
        memo = null,
        subCategoryId = subCategoryId,
    )

    private fun openAt(file: String): PtutDatabase =
        Room.databaseBuilder<PtutDatabase>(name = file).openPtutDatabase()
}

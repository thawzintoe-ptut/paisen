package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #48 against a real engine: "you paid" writes your own transaction only when the group counts in
 * the ledger's home. The home stays baht here, so a kyat group writes none: K9,000 is not ฿9,000.
 */
class SharedExpenseWriteTest {

    @Test
    fun youPaidInAKyatGroupWithABahtHomeWritesNoOwnTransaction() = withShared { shared, database ->
        val groupId = shared.createGroup("Yangon trip", GroupType.Trip, "work", listOf(NewMember("Aye")), currency = "MMK")

        shared.saveExpense(shared.youPaidDinner(groupId))

        assertTrue(database.transactionDao().all().isEmpty(), "a kyat amount reached the baht ledger")
    }

    @Test
    fun youPaidInABahtGroupWritesYourOwnTransaction() = withShared { shared, database ->
        val groupId = shared.createGroup("Flat 12", GroupType.Flat, "home", listOf(NewMember("Aye")), currency = "THB")

        shared.saveExpense(shared.youPaidDinner(groupId))

        assertEquals(9_000L, database.transactionDao().all().single().amountMinor)
    }

    /** A dinner in the group's own money, paid by this phone's member and split equally. */
    private suspend fun RoomSharedRepository.youPaidDinner(groupId: String): ExpenseDraft {
        val group = snapshot.first { it.group(groupId)?.members?.size == 2 }.group(groupId)!!
        return ExpenseDraft(
            expenseId = null,
            groupId = groupId,
            description = "Dinner",
            occurredAtEpochMillis = 0,
            categoryId = "food",
            subCategoryId = null,
            note = null,
            currency = group.currency,
            amountMinor = 9_000,
            rate = null,
            payerMemberId = group.meMemberId!!,
            splitMode = SplitMode.Equally,
            weights = group.members.associate { it.id to 1L },
        )
    }

    private fun withShared(test: suspend (RoomSharedRepository, PtutDatabase) -> Unit): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        // Its own scope, stopped before the close: the always-on reads must not query a closed
        // database, which on iOS is a segfault rather than an exception.
        val reads = CoroutineScope(backgroundScope.coroutineContext + Job())
        val ledger = DefaultLedgerRepository(
            transactionDao = database.transactionDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            scope = reads,
        )
        val shared = RoomSharedRepository(dao = database.sharedDao(), transactionDao = database.transactionDao(), ledger = ledger, scope = reads)
        try {
            test(shared, database)
        } finally {
            reads.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }
}

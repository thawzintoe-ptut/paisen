package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.SharedGroup
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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The shared-expense criticals of 17 Nov against a real engine: the outbox, the order versions
 * and removals sort in, History following a shared expense, and an equal split being edited.
 */
class SharedExpenseOrderTest {

    private class SetClock(var at: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(at)
    }

    /** One phone: its group copy, its ledger, the two tables read back, and the clock it stamps with. */
    private class Phone(val shared: RoomSharedRepository, val ledger: DefaultLedgerRepository, val dao: SharedDao, val transactions: TransactionDao, val clock: SetClock)

    // C1

    @Test
    fun aChangeQueuedAgainDuringItsUploadStaysInTheOutbox() = withPhone {
        dao.queue(listOf(SyncOutboxEntity("group:g1", "g1", "group", "g1", createdAtEpochMillis = 1)))
        dao.queue(listOf(SyncOutboxEntity("group:g1", "g1", "group", "g1", createdAtEpochMillis = 2)))

        dao.dequeue("group:g1", queuedAt = 1)

        assertEquals(listOf(2L), dao.outbox().map { it.createdAtEpochMillis })
    }

    // C2

    @Test
    fun aRemovalFromAPhoneWhoseClockIsBehindStillRemoves() = withPhone {
        val group = shared.groupWith("Aye")
        val expenseId = shared.saveExpense(dinner(group, 30_000))

        clock.at -= FIVE_MINUTES
        shared.removeExpense(expenseId)

        assertTrue(dao.latestVersion(expenseId)!!.removed)
    }

    @Test
    fun aPaymentPutBackFromAPhoneWhoseClockIsBehindIsBack() = withPhone {
        val group = shared.groupWith("Aye")
        dao.upsertGroup(dao.group(group.id)!!.copy(remote = true))
        val paymentId = shared.recordPayment(PaymentDraft(group.id, group.meMemberId!!, group.idOf("Aye"), Satang(5_000), PaymentMethod.Cash, 0))
        shared.removePayment(paymentId)

        clock.at -= FIVE_MINUTES
        shared.putBackPayment(paymentId)

        val latest = dao.observePaymentRemovals().first()
            .filter { it.paymentId == paymentId }
            .maxWith(compareBy({ it.createdAtEpochMillis }, { it.id }))
        assertEquals(false, latest.removed)
    }

    // C3

    @Test
    fun aMemoEditInHistoryDoesNotUndoAnotherPhonesAmount() = withPhone {
        val group = shared.groupWith("Aye")
        val expenseId = shared.saveExpense(dinner(group, 30_000))
        val row = ownRow()
        receiveFromAnotherPhone(expenseId, amount = 32_000, at = clock.at + 1)

        editInHistory(row, before = 30_000, edit = TransactionEdit(Satang(30_000), "food", memo = "late"))

        assertEquals(32_000L, dao.latestVersion(expenseId)!!.amountMinor)
        assertEquals(2, dao.versionsOf(expenseId).size)
    }

    @Test
    fun anAmountChangedInHistoryStillFollows() = withPhone {
        val group = shared.groupWith("Aye")
        val expenseId = shared.saveExpense(dinner(group, 30_000))
        val row = ownRow()

        editInHistory(row, before = 30_000, edit = TransactionEdit(Satang(35_000), "food", memo = null))

        assertEquals(35_000L, dao.latestVersion(expenseId)!!.amountMinor)
    }

    @Test
    fun anExpenseSomeoneElseNowPaysIgnoresYourHistoryRow() = withPhone {
        val group = shared.groupWith("Aye")
        val expenseId = shared.saveExpense(dinner(group, 30_000))
        val row = ownRow()
        receiveFromAnotherPhone(expenseId, amount = 30_000, at = clock.at + 1, payer = group.idOf("Aye"))

        editInHistory(row, before = 30_000, edit = TransactionEdit(Satang(35_000), "food", memo = null))

        val latest = dao.latestVersion(expenseId)!!
        assertEquals(30_000L, latest.amountMinor)
        assertEquals(group.idOf("Aye"), latest.payerMemberId)
    }

    // C4

    @Test
    fun anExactSplitThatFellBelowThePayersPartKeepsThePayerOnTheNextEdit() = withPhone {
        val group = shared.groupWith("Aye", "Bo")
        val everyone = group.members.associate { it.id to 10_000L }
        val expenseId = shared.saveExpense(dinner(group, 30_000).copy(splitMode = SplitMode.Exact, weights = everyone))
        val row = ownRow()

        editInHistory(row, before = 30_000, edit = TransactionEdit(Satang(15_000), "food", memo = null))
        editInHistory(row, before = 15_000, edit = TransactionEdit(Satang(16_000), "food", memo = null))

        val latest = dao.latestVersion(expenseId)!!
        val parts = dao.sharesOf(latest.versionId).associate { it.memberId to it.amountMinor }
        assertEquals(mapOf(group.meMemberId!! to 5_334L, group.idOf("Aye") to 5_333L, group.idOf("Bo") to 5_333L), parts)
    }

    // C5

    @Test
    fun editingAnEqualSplitAfterSomeoneJoinedKeepsTheOriginalPeople() = withPhone {
        val group = shared.groupWith("Aye", "Bo")
        val expenseId = shared.saveExpense(dinner(group, 30_000))
        val first = dao.latestVersion(expenseId)!!
        shared.addMembers(group.id, listOf(NewMember("Dan")))
        val withDan = shared.snapshot.first { it.group(group.id)?.members?.size == 4 }.group(group.id)!!

        shared.saveExpense(
            dinner(withDan, 30_000).copy(expenseId = expenseId, description = "Dinner at Mali's", baseVersionId = first.versionId),
        )

        val latest = dao.latestVersion(expenseId)!!
        val parts = dao.sharesOf(latest.versionId).map { it.amountMinor }
        assertEquals(listOf(10_000L, 10_000L, 10_000L), parts)
    }

    private suspend fun RoomSharedRepository.groupWith(vararg names: String): SharedGroup {
        val id = createGroup("Trip", GroupType.Trip, "work", names.map { NewMember(it) }, currency = "THB")
        return snapshot.first { it.group(id)?.members?.size == names.size + 1 }.group(id)!!
    }

    private fun SharedGroup.idOf(name: String): String = members.first { it.name == name }.id

    /** Paid by this phone's member, split equally between everyone in [group]. */
    private fun dinner(group: SharedGroup, amount: Long) = ExpenseDraft(
        expenseId = null,
        groupId = group.id,
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = "food",
        subCategoryId = null,
        note = null,
        currency = group.currency,
        amountMinor = amount,
        rate = null,
        payerMemberId = group.meMemberId!!,
        splitMode = SplitMode.Equally,
        weights = group.members.associate { it.id to 1L },
    )

    /** The History row "you paid" wrote, once the snapshot links it. */
    private suspend fun Phone.ownRow(): String {
        shared.snapshot.first { it.links.isNotEmpty() }
        return transactions.all().single().id
    }

    /** What EditTransactionViewModel.save does: the row changes, then the shared copy follows. */
    private suspend fun Phone.editInHistory(rowId: String, before: Long, edit: TransactionEdit) {
        ledger.applyEdit(rowId, edit)
        shared.followTransaction(rowId, before = TransactionEdit(Satang(before), "food", memo = null, occurredAtEpochMillis = 0))
    }

    /** A version written on Aye's phone, as a pull stores it. */
    private suspend fun Phone.receiveFromAnotherPhone(expenseId: String, amount: Long, at: Long, payer: String? = null) {
        val last = dao.latestVersion(expenseId)!!
        val shares = dao.sharesOf(last.versionId)
        val each = amount / shares.size
        dao.receiveVersion(
            last.copy(versionId = "aye-v2", version = last.version + 1, amountMinor = amount, amountSatang = amount, createdAtEpochMillis = at, payerMemberId = payer ?: last.payerMemberId),
            shares.mapIndexed { index, share ->
                val part = if (index == 0) amount - each * (shares.size - 1) else each
                share.copy(versionId = "aye-v2", amountMinor = part, amountSatang = part)
            },
        )
    }

    private fun withPhone(test: suspend Phone.() -> Unit): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        // Its own scope, stopped before the close: the always-on reads must not query a closed
        // database, which on iOS is a segfault rather than an exception.
        val reads = CoroutineScope(backgroundScope.coroutineContext + Job())
        val clock = SetClock(at = 1_763_300_000_000)
        val ledger = DefaultLedgerRepository(
            transactionDao = database.transactionDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            scope = reads,
        )
        val shared = RoomSharedRepository(dao = database.sharedDao(), transactionDao = database.transactionDao(), ledger = ledger, scope = reads, clock = clock)
        try {
            Phone(shared, ledger, database.sharedDao(), database.transactionDao(), clock).test()
        } finally {
            reads.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }

    private companion object {
        const val FIVE_MINUTES = 5 * 60 * 1_000L
    }
}

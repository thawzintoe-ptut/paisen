package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** H6 and M4 against a real engine: a file never writes into a synced group, and one bad repeat stops none of the others. */
class RestoreAndRepeatTest {

    @Test
    fun aFileAimedAtASyncedGroupAddsNothingToIt() = withShared { _, database ->
        val dao = database.sharedDao()
        dao.upsertGroup(group("synced").copy(remote = true))
        dao.insertMembersIfNew(listOf(member("synced-me", "synced")))

        val added = dao.restoreShared(
            people = emptyList(),
            groups = listOf(group("synced")),
            members = listOf(member("intruder", "synced")),
            versions = listOf(version("v1", "synced")),
            shares = listOf(share("v1", "intruder")),
            payments = listOf(payment("pay1", "synced")),
            links = emptyList(),
            repeats = listOf(repeat("r1", "synced")),
        )

        val ids = listOf("synced")
        assertEquals(0, added)
        assertEquals(listOf("synced-me"), dao.membersOf("synced").map { it.id })
        assertTrue(dao.versionsIn(ids).isEmpty() && dao.sharesIn(ids).isEmpty())
        assertTrue(dao.paymentsIn(ids).isEmpty() && dao.repeatsIn(ids).isEmpty())
    }

    @Test
    fun aFileRowForAGroupItDoesNotHoldIsLeftOut() = withShared { _, database ->
        val dao = database.sharedDao()
        dao.upsertGroup(group("mine"))

        dao.restoreShared(
            people = emptyList(),
            groups = emptyList(),
            members = listOf(member("intruder", "mine")),
            versions = emptyList(),
            shares = emptyList(),
            payments = listOf(payment("pay1", "mine")),
            links = emptyList(),
        )

        assertTrue(dao.membersOf("mine").isEmpty())
        assertTrue(dao.paymentsIn(listOf("mine")).isEmpty())
    }

    @Test
    fun aFileWithANewLocalGroupStillRestoresWhole() = withShared { _, database ->
        val dao = database.sharedDao()

        val added = dao.restoreShared(
            people = emptyList(),
            groups = listOf(group("trip")),
            members = listOf(member("me", "trip"), member("aye", "trip")),
            versions = listOf(version("v1", "trip")),
            shares = listOf(share("v1", "me"), share("v1", "aye")),
            payments = listOf(payment("pay1", "trip")),
            links = emptyList(),
            repeats = listOf(repeat("r1", "trip")),
        )

        val ids = listOf("trip")
        assertEquals(1, added)
        assertEquals(setOf("me", "aye"), dao.membersOf("trip").map { it.id }.toSet())
        assertEquals(1, dao.versionsIn(ids).size)
        assertEquals(2, dao.sharesIn(ids).size)
        assertEquals(1, dao.paymentsIn(ids).size)
        assertEquals(1, dao.repeatsIn(ids).size)
    }

    @Test
    fun aRepeatThatCannotBeAddedLeavesTheOthersToRun() = withShared { shared, database ->
        val groupId = shared.createGroup("Flat 12", GroupType.Flat, "home", listOf(NewMember("Aye")), currency = "THB")
        val me = shared.snapshot.first { it.group(groupId)?.members?.size == 2 }.group(groupId)!!.meMemberId!!
        val dao = database.sharedDao()
        // Kyat with no rate cannot become baht, and comes first.
        dao.upsertRepeat(repeat("kyat", groupId, payer = me).copy(currency = "MMK"))
        dao.upsertRepeat(repeat("rent", groupId, payer = me))

        val added = shared.addDueRepeats(LocalDate(2026, 3, 1))

        // 1 Feb and 1 Mar.
        assertEquals(2, added)
        assertEquals(listOf("rent"), dao.versionsIn(listOf(groupId)).map { it.expenseId.split(':')[1] }.distinct())
    }

    @Test
    fun aStoredRepeatNoBuildCanReadIsLeftOutOfTheSnapshot() = withShared { shared, database ->
        val groupId = shared.createGroup("Flat 12", GroupType.Flat, "home", listOf(NewMember("Aye")), currency = "THB")
        val dao = database.sharedDao()
        dao.upsertRepeat(repeat("broken", groupId).copy(kind = "X"))
        dao.upsertRepeat(repeat("rent", groupId))

        val repeats = shared.snapshot.first { it.group(groupId) != null && it.repeats.isNotEmpty() }.repeats

        assertEquals(listOf("rent"), repeats.map { it.id })
        assertEquals(2, shared.addDueRepeats(LocalDate(2026, 3, 1)))
    }

    private fun group(id: String) = SharedGroupEntity(id, "Flat", GroupType.Flat.name, "home", simplifyDebts = true, createdAtEpochMillis = 1, meMemberId = "me")

    private fun member(id: String, groupId: String) = GroupMemberEntity(id, groupId, id, personId = null, archived = false, createdAtEpochMillis = 1)

    private fun version(id: String, groupId: String) = ExpenseVersionEntity(
        versionId = id, expenseId = "e-$id", groupId = groupId, version = 1, description = "Dinner", occurredAtEpochMillis = 1,
        categoryId = null, subCategoryId = null, note = null, currency = "THB", amountMinor = 9_000, rateUnscaled = null, rateScale = null,
        amountSatang = 9_000, payerMemberId = "me", splitMode = "Equally", authorMemberId = null, createdAtEpochMillis = 1, removed = false,
    )

    private fun share(versionId: String, memberId: String) = ExpenseShareEntity(versionId, memberId, 1, 4_500, 4_500)

    private fun payment(id: String, groupId: String) = SharedPaymentEntity(id, groupId, "aye", "me", 500, "Cash", 1, null, 1)

    private fun repeat(id: String, groupId: String, payer: String = "me") = RepeatRuleEntity(
        id = id, groupId = groupId, kind = "Rent", frequency = "Monthly", startDate = "2026-02-01", endKind = END_NEVER, endDate = null,
        endTimes = null, remind = true, active = true, lastAdded = null, description = "Rent", categoryId = null, currency = "THB",
        amountMinor = 1_200_000, rateUnscaled = null, rateScale = null, payerMemberId = payer, splitMode = "Equally", weights = "",
        createdAtEpochMillis = 1,
    )

    private fun withShared(test: suspend (RoomSharedRepository, PtutDatabase) -> Unit): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        // Its own scope, stopped before the close: a query on a closed database is a segfault on iOS.
        val reads = CoroutineScope(backgroundScope.coroutineContext + Job())
        val ledger = RoomLedgerRepository(
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

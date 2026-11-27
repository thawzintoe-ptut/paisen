package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.PaymentReactionEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.KIND_GROUP
import com.ptut.expense.data.repository.KIND_MEMBER
import com.ptut.expense.data.repository.KIND_REACTION
import com.ptut.expense.data.repository.KIND_TRIP_END
import com.ptut.expense.data.repository.REACTION_GONE
import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.domain.shared.due
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupPullerTest {

    private val firestore = FakeFirestore()
    private val dao = FakeSharedDao()
    private val clock = TestClock(nowMillis = 1_000_000L)
    private val puller = GroupPuller(firestore, dao, clock) { UID }

    @Test
    fun `an unchanged group and its names are not written again`() = runTest {
        holds("g1")
        puller.pull("g1")
        val written = Triple(dao.groupWrites, dao.memberWrites, dao.marks)

        puller.pull("g1")

        assertEquals(written, Triple(dao.groupWrites, dao.memberWrites, dao.marks))
        assertEquals(1, firestore.calls.count { it == "list groups/g1/members" })
    }

    @Test
    fun `names are listed again when the group document changed`() = runTest {
        holds("g1")
        puller.pull("g1")

        firestore.docs["groups/g1"] = groupDoc("g1", name = "Flat 2")
        puller.pull("g1")

        assertEquals(2, firestore.calls.count { it == "list groups/g1/members" })
    }

    @Test
    fun `names are listed again after ten minutes`() = runTest {
        holds("g1")
        puller.pull("g1")

        clock.nowMillis += TEN_MINUTES
        puller.pull("g1")

        assertEquals(2, firestore.calls.count { it == "list groups/g1/members" })
    }

    @Test
    fun `an entry naming someone new lists the names again`() = runTest {
        holds("g1")
        puller.pull("g1")

        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), memberDoc("bo"))
        firestore.collections["groups/g1/entries"] = listOf(paymentDoc("p1", from = "bo", to = ME))
        puller.pull("g1")

        assertEquals(2, firestore.calls.count { it == "list groups/g1/members" })
        assertTrue("bo" in dao.members)
    }

    @Test
    fun `a pull writes its entries in one transaction`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), memberDoc("bo"))
        firestore.collections["groups/g1/entries"] = listOf(paymentDoc("p1", from = "bo", to = ME), paymentDoc("p2", from = ME, to = "bo"))

        puller.pull("g1")

        assertEquals(1, dao.logWrites)
        assertEquals(setOf("p1", "p2"), dao.payments.keys)
        assertEquals(1, dao.marks)
    }

    @Test
    fun `an empty pull leaves the mark where it was`() = runTest {
        holds("g1")

        puller.pull("g1")

        assertEquals(0, dao.logWrites)
        assertEquals(0, dao.marks)
        assertEquals(MARK, dao.groups.getValue("g1").lastSyncedAtEpochMillis)
    }

    @Test
    fun `a split whose parts only add up by wrapping round is not taken`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), memberDoc("bo"))
        val wrapped = List(4) { 1L shl 62 }
        firestore.collections["groups/g1/entries"] = listOf(versionDoc("v1", total = 0, parts = wrapped), versionDoc("v2", total = 9_000, parts = listOf(4_500, 4_500)))

        puller.pull("g1")

        assertEquals(setOf("v2"), dao.versions.keys)
    }

    @Test
    fun `an amount past the ceiling is not taken`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), memberDoc("bo"))
        val huge = 10_000_000_000_001L
        firestore.collections["groups/g1/entries"] = listOf(
            versionDoc("v1", total = huge, parts = listOf(huge - 1, 1)),
            paymentDoc("p1", from = "bo", to = ME).let { it.copy(fields = it.fields + ("amountSatang" to huge)) },
            paymentDoc("p2", from = "bo", to = ME),
        )

        puller.pull("g1")

        assertTrue(dao.versions.isEmpty())
        assertEquals(setOf("p2"), dao.payments.keys)
    }

    @Test
    fun `a repeat brought back adds the date the phone that left missed exactly once`() = runTest {
        holds("g1")
        clock.nowMillis = NOV_22
        firestore.collections["groups/g1/repeats"] = listOf(repeatDoc("r1"))

        puller.pull("g1")

        val rule = assertNotNull(dao.repeats.getValue("r1").toDomain())
        assertEquals(listOf(LocalDate(2026, 11, 1)), rule.due(LocalDate(2026, 11, 22)))
    }

    @Test
    fun `a repeat brought back catches up after the newest date already in the log`() = runTest {
        holds("g1")
        clock.nowMillis = NOV_22
        val added = versionDoc("v1", 100, listOf(100))
        firestore.collections["groups/g1/entries"] = listOf(added.copy(fields = added.fields + ("expenseId" to "repeat:r1:2026-09-01")))
        firestore.collections["groups/g1/repeats"] = listOf(repeatDoc("r1"))

        puller.pull("g1")

        val rule = assertNotNull(dao.repeats.getValue("r1").toDomain())
        assertEquals(listOf(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1)), rule.due(LocalDate(2026, 11, 22)))
    }

    @Test
    fun `a repeat in other money with no rate is not taken and one with its rate is`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/repeats"] = listOf(
            repeatDoc("r1", "currency" to "MMK"),
            repeatDoc("r2", "currency" to "MMK", "rateUnscaled" to 82L, "rateScale" to 4L),
            repeatDoc("r3"),
        )

        puller.pull("g1")

        assertEquals(setOf("r2", "r3"), dao.repeats.keys)
    }

    @Test
    fun `a repeat this build cannot read is not taken`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/repeats"] = listOf(
            repeatDoc("r1", "kind" to "Lottery"),
            repeatDoc("r2", "start" to "2026-02-31"),
            repeatDoc("r3", "amountMinor" to -1L),
            repeatDoc("r4"),
        )

        puller.pull("g1")

        assertEquals(setOf("r4"), dao.repeats.keys)
    }

    @Test
    fun `a group deleted for everyone leaves this phone`() = runTest {
        holds("g1")
        firestore.docs["groups/g1"] = groupDoc("g1", deleted = true)

        assertEquals(PullResult.Removed, puller.pull("g1"))
        assertTrue("g1" !in dao.groups)
    }

    @Test
    fun `a trip's end comes down with the group unless this phone's own change waits to go up`() = runTest {
        holds("g1")
        firestore.docs["groups/g1"] = groupDoc("g1").let { it.copy(fields = it.fields + ("endedOn" to "2026-11-24")) }

        puller.pull("g1")
        assertEquals("2026-11-24", dao.groups.getValue("g1").endedOn)

        dao.groups["g1"] = dao.groups.getValue("g1").copy(endedOn = null)
        dao.outboxRows.value += SyncOutboxEntity("$KIND_TRIP_END:g1", "g1", KIND_TRIP_END, "g1", 1)
        clock.nowMillis += TEN_MINUTES
        puller.pull("g1")
        assertNull(dao.groups.getValue("g1").endedOn)
    }

    @Test
    fun `an awards opt-out counts only from the name's own holder`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), heldDoc("bo", "uid-2"), heldDoc("cy", "uid-3"))
        firestore.collections["groups/g1/play"] = listOf(
            FirestoreDoc("bo", "groups/g1/play/bo", mapOf("awardsOff" to true, "claimedByUid" to "uid-2")),
            FirestoreDoc("cy", "groups/g1/play/cy", mapOf("awardsOff" to true, "claimedByUid" to "uid-9")),
        )

        puller.pull("g1")

        assertTrue(dao.members.getValue("bo").playOff)
        assertFalse(dao.members.getValue("cy").playOff)
    }

    @Test
    fun `the first pull takes the whole list of reactions and this phone's own unsent one stays`() = runTest {
        holds("g1")
        dao.groups["g1"] = dao.groups.getValue("g1").copy(lastSyncedAtEpochMillis = null)
        dao.reactions["p2_$UID"] = PaymentReactionEntity("p2_$UID", "g1", "p2", UID, "Cheers", 1)
        dao.reactions["p9_uid-2"] = PaymentReactionEntity("p9_uid-2", "g1", "p9", "uid-2", "Thanks", 1)
        dao.outboxRows.value += SyncOutboxEntity("$KIND_REACTION:p2_$UID", "g1", KIND_REACTION, "p2_$UID", 1)
        firestore.collections["groups/g1/reactions"] = listOf(
            reactionDoc("p1", "uid-2", "WellPlayed"),
            reactionDoc("p3", "uid-2", "Shame"),
            reactionDoc("p4", "uid-2", REACTION_GONE),
            reactionDoc("p5", "uid-2").copy(id = "p5_uid-3"),
        )

        puller.pull("g1")

        assertTrue("list groups/g1/reactions" in firestore.calls)
        assertEquals(setOf("p1_uid-2", "p2_$UID"), dao.reactions.keys)
    }

    @Test
    fun `a new reaction arrives on the next pull even when the names are not listed again and leaves the log mark`() = runTest {
        holds("g1")
        puller.pull("g1")
        val marks = dao.marks
        firestore.collections["groups/g1/reactions"] = listOf(reactionDoc("p1", "uid-2"))

        puller.pull("g1")

        assertEquals(1, firestore.calls.count { it == "list groups/g1/members" })
        // Listed whole once by a new process, then asked only for what is newer.
        assertEquals(1, firestore.calls.count { it == "list groups/g1/reactions" })
        assertEquals(setOf("p1_uid-2"), dao.reactions.keys)
        assertEquals(marks, dao.marks)
    }

    @Test
    fun `a pull that skips reactions still moves the log mark past new entries`() = runTest {
        holds("g1")
        puller.pull("g1")
        val marks = dao.marks
        firestore.collections["groups/g1/entries"] = listOf(paymentDoc("p1", from = "bo", to = ME))

        puller.pull("g1", withReactions = false)

        assertEquals(marks + 1, dao.marks)
    }

    @Test
    fun `a payment that never went up is kept as refused when its group leaves this phone`() = runTest {
        holds("g1")
        queuePayment(dao, "p1", "g1")
        firestore.docs["groups/g1"] = groupDoc("g1", deleted = true)

        assertEquals(PullResult.Removed, puller.pull("g1"))

        val kept = dao.refusedRows.value.single()
        assertEquals("payment:p1", kept.id)
        assertNotNull(kept.amountMinor)
        assertNotNull(kept.groupName)
    }

    @Test
    fun `a group whose document is gone leaves this phone unless a change still waits`() = runTest {
        holds("g1")
        firestore.docs.remove("groups/g1")
        dao.outboxRows.value += SyncOutboxEntity("$KIND_GROUP:g1", "g1", KIND_GROUP, "g1", 1)

        assertEquals(PullResult.Kept, puller.pull("g1"))

        dao.outboxRows.value = emptyList()
        assertEquals(PullResult.Removed, puller.pull("g1"))
    }

    @Test
    fun `a reaction taken back comes as Gone and leaves unless it is this phone's own unsent one`() = runTest {
        holds("g1")
        dao.reactions["p1_uid-2"] = PaymentReactionEntity("p1_uid-2", "g1", "p1", "uid-2", "Thanks", 1)
        dao.reactions["p2_$UID"] = PaymentReactionEntity("p2_$UID", "g1", "p2", UID, "Cheers", 1)
        dao.outboxRows.value += SyncOutboxEntity("$KIND_REACTION:p2_$UID", "g1", KIND_REACTION, "p2_$UID", 1)
        firestore.collections["groups/g1/reactions"] = listOf(reactionDoc("p1", "uid-2", REACTION_GONE), reactionDoc("p2", UID, REACTION_GONE))

        puller.pull("g1")

        assertEquals(setOf("p2_$UID"), dao.reactions.keys)
    }

    @Test
    fun `a reaction pulled again unchanged is not written again`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/reactions"] = listOf(reactionDoc("p1", "uid-2"))
        puller.pull("g1")
        val written = dao.reactionWrites

        puller.pull("g1")

        assertEquals(written, dao.reactionWrites)
    }

    @Test
    fun `a later pull without reactions skips their query and the next pull with them still gets the new one`() = runTest {
        holds("g1")
        puller.pull("g1")
        firestore.collections["groups/g1/reactions"] = listOf(reactionDoc("p1", "uid-2"))
        val queried = firestore.calls.count { it == "since groups/g1/reactions" }

        puller.pull("g1", withReactions = false)

        assertEquals(queried, firestore.calls.count { it == "since groups/g1/reactions" })
        assertTrue(dao.reactions.isEmpty())

        puller.pull("g1")

        assertEquals(setOf("p1_uid-2"), dao.reactions.keys)
    }

    @Test
    fun `a PromptPay ID stored by the name's holder is read from the members-only place`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), heldDoc("bo", "uid-2"))
        firestore.collections["groups/g1/pay"] = listOf(payDoc("bo", "uid-2"))

        puller.pull("g1")

        assertEquals(PROMPT_PAY, dao.members.getValue("bo").promptPayId)
    }

    @Test
    fun `an ID stored for an earlier holder is ignored and the name's own field is the fallback`() = runTest {
        holds("g1")
        val older = "08" + "99999999"
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), heldDoc("bo", "uid-3", older), heldDoc("cy", "uid-4"))
        firestore.collections["groups/g1/pay"] = listOf(payDoc("bo", "uid-2"), payDoc("cy", "uid-2"))

        puller.pull("g1")

        assertEquals(older, dao.members.getValue("bo").promptPayId)
        assertNull(dao.members.getValue("cy").promptPayId)
    }

    @Test
    fun `no members-only place yet reads as no IDs there`() = runTest {
        holds("g1")
        firestore.hidden += "groups/g1/pay"
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), heldDoc("bo", "uid-2", PROMPT_PAY))

        puller.pull("g1")

        assertEquals(PROMPT_PAY, dao.members.getValue("bo").promptPayId)
    }

    @Test
    fun `this phone's own ID left on its name by an older build is queued to move`() = runTest {
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(heldDoc(ME, UID, PROMPT_PAY), heldDoc("bo", "uid-2", PROMPT_PAY))

        puller.pull("g1")

        assertEquals(listOf("$KIND_MEMBER:$ME"), dao.outboxRows.value.map { it.id })
        assertEquals(PROMPT_PAY, dao.members.getValue(ME).promptPayId)
    }

    @Test
    fun `the invite's names are put right only when the names nobody holds changed`() = runTest {
        val published = mutableListOf<String>()
        val puller = GroupPuller(firestore, dao, clock, { published += it }) { UID }
        holds("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), named(BO, "Bo"))
        puller.pull("g1")

        clock.nowMillis += TEN_MINUTES
        puller.pull("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), named(BO, "Bo", holder = "uid-2"))
        clock.nowMillis += TEN_MINUTES
        puller.pull("g1")

        assertEquals(listOf("g1", "g1"), published)
    }

    private fun holds(vararg ids: String) = holdGroups(dao, firestore, *ids)
}

/** 2026-11-22, midday in Bangkok. */
private const val NOV_22 = 1_795_323_600_000L

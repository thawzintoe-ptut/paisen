package com.ptut.expense.sync

import com.ptut.expense.data.repository.KIND_MEMBER
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

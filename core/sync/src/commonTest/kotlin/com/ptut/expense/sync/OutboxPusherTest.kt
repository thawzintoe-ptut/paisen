package com.ptut.expense.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxPusherTest {

    private val firestore = FakeFirestore()
    private val dao = FakeSharedDao()
    private val auth = FakeAuth(UID)
    private val failed = MutableStateFlow<Set<String>>(emptySet())
    private val pusher = OutboxPusher(
        firestore = firestore,
        dao = dao,
        repository = FakeSharedRepository(dao),
        clock = TestClock(nowMillis = 1_000_000L),
        pushes = null,
        failed = failed,
        account = { auth.signedIn },
        newInvite = { error("unused") },
    )

    @Test
    fun `a rate limit holds the rest of its group and the next group still uploads`() = runTest {
        holdGroups(dao, firestore, "g1", "g2")
        queuePayment(dao, "p1", "g1")
        queuePayment(dao, "p2", "g1")
        queuePayment(dao, "p3", "g2")
        firestore.refusing["groups/g1/entries/p1"] = 429

        pusher.push()

        assertEquals(listOf("payment:p1", "payment:p2"), dao.outboxRows.value.map { it.id })
        assertTrue(firestore.writes.none { "p2" in it })
        assertTrue("stamped groups/g2/entries/p3" in firestore.writes)
        assertNull(pusher.refused.value["g1"])
        assertTrue("p1" in dao.payments)
        assertEquals(setOf("g1"), failed.value)
    }

    @Test
    fun `a refusal for good is counted once per change and can be acknowledged`() = runTest {
        holdGroups(dao, firestore, "g1")
        queuePayment(dao, "p1", "g1")
        queuePayment(dao, "p2", "g1")
        firestore.refusing["groups/g1/entries/p1"] = 403
        firestore.refusing["groups/g1/entries/p2"] = 403

        pusher.push()
        assertEquals(2, pusher.refused.value["g1"])
        assertFalse("p1" in dao.payments)

        pusher.acknowledgeRefused("g1")
        assertNull(pusher.refused.value["g1"])
    }
}

package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.KIND_MEMBER
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
    private val published = mutableListOf<String>()
    private val pusher = OutboxPusher(
        firestore = firestore,
        dao = dao,
        repository = FakeSharedRepository(dao),
        clock = TestClock(nowMillis = 1_000_000L),
        pushes = null,
        failed = failed,
        account = { auth.signedIn },
        newInvite = { error("unused") },
        publishNames = { published += it },
    )

    @Test
    fun `this phone's own PromptPay ID goes where only members read and leaves its name`() = runTest {
        holdGroups(dao, firestore, "g1")
        dao.members[ME] = dao.members.getValue(ME).copy(promptPayId = PROMPT_PAY)
        firestore.docs["groups/g1/members/$ME"] = memberDoc(ME)
        queueMember(ME)

        pusher.push()

        assertEquals(mapOf("promptPay" to PROMPT_PAY, "claimedByUid" to UID), firestore.sent["groups/g1/pay/$ME"])
        assertEquals(mapOf("promptPay" to null), firestore.sent["groups/g1/members/$ME"])
        assertTrue(published.isEmpty())
    }

    @Test
    fun `a PromptPay ID taken off this phone's own name is taken away there too`() = runTest {
        holdGroups(dao, firestore, "g1")
        firestore.docs["groups/g1/members/$ME"] = memberDoc(ME)
        queueMember(ME)

        pusher.push()

        assertTrue("delete groups/g1/pay/$ME" in firestore.writes)
    }

    @Test
    fun `a new name of this phone's own carries no PromptPay ID on the name`() = runTest {
        holdGroups(dao, firestore, "g1")
        dao.members[ME] = dao.members.getValue(ME).copy(promptPayId = PROMPT_PAY)
        queueMember(ME)

        pusher.push()

        assertFalse("promptPay" in firestore.created.getValue("groups/g1/members/$ME"))
        assertEquals(mapOf("promptPay" to PROMPT_PAY, "claimedByUid" to UID), firestore.sent["groups/g1/pay/$ME"])
    }

    @Test
    fun `names nobody holds put the invite's names right once per push`() = runTest {
        holdGroups(dao, firestore, "g1")
        listOf("bo", "cy").forEach { id ->
            dao.members[id] = GroupMemberEntity(id, "g1", id, personId = null, archived = false, createdAtEpochMillis = 2)
            queueMember(id)
        }

        pusher.push()

        assertEquals(listOf("g1"), published)
    }

    private fun queueMember(id: String) {
        dao.outboxRows.value += SyncOutboxEntity("$KIND_MEMBER:$id", "g1", KIND_MEMBER, id, 1)
    }

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

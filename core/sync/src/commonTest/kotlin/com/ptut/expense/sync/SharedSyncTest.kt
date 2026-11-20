package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.KIND_GROUP
import com.ptut.expense.data.repository.KIND_MEMBER
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.auth.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SharedSyncTest {

    private val firestore = FakeFirestore()
    private val dao = FakeSharedDao()
    private val clock = TestClock(nowMillis = 1_000_000L)
    private val auth = FakeAuth(UID)
    private val sync = SharedSync(auth, firestore, FakeSharedRepository(dao), dao, clock)

    @Test
    fun `syncGroup pulls that group alone`() = runTest {
        holds("g1", "g2")

        val removed = sync.syncGroup("g1")

        assertFalse(removed)
        assertTrue("get groups/g1" in firestore.calls)
        assertTrue(firestore.calls.none { "g2" in it })
        assertTrue(firestore.calls.none { it.startsWith("query") })
    }

    @Test
    fun `syncGroup says so when the group was deleted for everyone`() = runTest {
        holds("g1")
        firestore.docs["groups/g1"] = groupDoc("g1", deleted = true)

        assertTrue(sync.syncGroup("g1"))
        assertNull(dao.groups["g1"])
    }

    @Test
    fun `syncGroup leaves a group this phone does not hold`() = runTest {
        assertFalse(sync.syncGroup("elsewhere"))
        assertTrue(firestore.calls.isEmpty())
    }

    @Test
    fun `syncGroup says it failed when its group could not be pulled`() = runTest {
        holds("g1")
        firestore.failing += "groups/g1"

        assertEquals(SyncOutcome(groupsChanged = false, failed = true), sync.syncGroupOutcome("g1"))
        firestore.failing.clear()
        assertEquals(SyncOutcome(groupsChanged = false, failed = false), sync.syncGroupOutcome("g1"))
    }

    @Test
    fun `syncAll says it failed when one group could not be pulled`() = runTest {
        holds("g1", "g2")
        firestore.failing += "groups/g1"

        assertEquals(SyncOutcome(groupsChanged = false, failed = true), sync.syncAllOutcome())
    }

    @Test
    fun `a lost sign-in fails the whole sync`() = runTest {
        holds("g1")
        queuePayment(dao, "p1", "g1")
        queuePayment(dao, "p2", "g1")
        firestore.beforeWrite = { if ("p1" in it) auth.account.value = Account.SignedOut }

        assertEquals(SyncOutcome(groupsChanged = false, failed = true), sync.syncAllOutcome())
    }

    @Test
    fun `a sync while signed out is quiet and not a failure`() = runTest {
        auth.account.value = Account.SignedOut

        assertEquals(SyncOutcome(groupsChanged = false, failed = false), sync.syncAllOutcome())
    }

    @Test
    fun `the restore query runs once in ten minutes`() = runTest {
        holds("g1")

        sync.syncAll()
        sync.syncAll()
        assertEquals(1, firestore.calls.count { it == "query groups" })

        clock.nowMillis += TEN_MINUTES
        sync.syncAll()
        assertEquals(2, firestore.calls.count { it == "query groups" })
    }

    @Test
    fun `a failed restore query is asked again at the next sync`() = runTest {
        holds("g1")
        firestore.failing += "query"
        sync.syncAll()

        firestore.failing.clear()
        sync.syncAll()

        assertEquals(2, firestore.calls.count { it == "query groups" })
    }

    @Test
    fun `a group the restore query read is not read again`() = runTest {
        holds("g1")
        firestore.memberships = listOf(groupDoc("g1"), groupDoc("g2"))
        firestore.docs["groups/g2"] = groupDoc("g2")

        assertTrue(sync.syncAll())

        assertTrue(firestore.calls.none { it.startsWith("get groups/") })
        assertTrue(dao.groups.getValue("g2").remote)
    }

    @Test
    fun `an html answer marks its group offline and the next group still syncs`() = runTest {
        holds("g1", "g2")
        firestore.failing += "groups/g1"

        sync.syncAll()

        assertTrue("get groups/g2" in firestore.calls)
        assertEquals(SyncHealth.Offline, sync.status("g1").first().health)
        assertEquals(SyncHealth.UpToDate, sync.status("g2").first().health)
    }

    @Test
    fun `an html page with a success status is a failed call`() {
        val failure = assertFailsWith<FirestoreException> { answerOf(200, "<html><body>Sign in to Wi-Fi</body></html>") }

        assertEquals(FirestoreStatus.NO_ANSWER, failure.status)
    }

    @Test
    fun `a refused call keeps its body for the message`() {
        val answer = answerOf(403, "<html>Forbidden</html>")

        assertEquals(403, answer.status)
        assertNull(answer.json)
    }

    @Test
    fun `json of the wrong shape is a failed call`() {
        val failure = assertFailsWith<FirestoreException> { answerOf(200, "[1]").read { it.jsonObject } }

        assertEquals(FirestoreStatus.NO_ANSWER, failure.status)
    }

    @Test
    fun `a change the group refuses for good leaves the outbox and this phone and is counted`() = runTest {
        holds("g1")
        queuePayment(dao, "p1", "g1")
        firestore.refusing["groups/g1/entries/p1"] = 403

        sync.syncGroup("g1")

        assertTrue(dao.outboxRows.value.isEmpty())
        assertFalse("p1" in dao.payments)
        assertEquals(1, sync.status("g1").first().refused)
    }

    @Test
    fun `a group change still waiting keeps its name through a pull`() = runTest {
        holds("g1")
        dao.groups["g1"] = dao.groups.getValue("g1").copy(name = "Here")
        dao.outboxRows.value = listOf(SyncOutboxEntity("$KIND_GROUP:g1", "g1", KIND_GROUP, "g1", 1))
        firestore.refusing["groups/g1"] = 429
        firestore.docs["groups/g1"] = groupDoc("g1", name = "There")

        sync.syncGroup("g1")

        assertEquals("Here", dao.groups.getValue("g1").name)
    }

    @Test
    fun `a name still waiting is not overwritten by a pull`() = runTest {
        holds("g1")
        dao.members["bo"] = GroupMemberEntity("bo", "g1", "Bo here", personId = null, archived = false, createdAtEpochMillis = 1, claimedByUid = null)
        dao.outboxRows.value = listOf(SyncOutboxEntity("$KIND_MEMBER:bo", "g1", KIND_MEMBER, "bo", 1))
        firestore.refusing["groups/g1/members/bo"] = 429
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), memberDoc("bo"))

        sync.syncGroup("g1")

        assertEquals("Bo here", dao.members.getValue("bo").name)
    }

    @Test
    fun `a refused join says the name was taken only when someone claimed it`() = runTest {
        val choice = JoinChoice("g1", "Flat", listOf("bo" to "Bo"))
        firestore.refusing["groups/g1/members/bo"] = 403
        firestore.docs["groups/g1/members/bo"] = memberDoc("bo").let { it.copy(fields = it.fields + ("claimedByUid" to "uid-2")) }

        val taken = assertFailsWith<JoinException> { sync.join(choice, "bo") }
        firestore.docs.remove("groups/g1/members/bo")
        val expired = assertFailsWith<JoinException> { sync.join(choice, "bo") }

        assertEquals(AppText.copy.shared.sync.nameTaken, taken.message)
        assertEquals(AppText.copy.shared.sync.inviteExpired, expired.message)
    }

    @Test
    fun `sign-out stops while a change cannot go yet`() = runTest {
        holds("g1")
        queuePayment(dao, "p1", "g1")
        firestore.refusing["groups/g1/entries/p1"] = 429

        val waiting = assertFailsWith<ChangesWaiting> { sync.signOut() }

        assertEquals(1, waiting.count)
        assertTrue(auth.account.value is Account.SignedIn)
        assertTrue("g1" in dao.groups)
    }

    @Test
    fun `a sign-out in the middle of an upload fails the sync quietly and keeps the rest`() = runTest {
        holds("g1")
        queuePayment(dao, "p1", "g1")
        queuePayment(dao, "p2", "g1")
        firestore.beforeWrite = { if ("p1" in it) auth.account.value = Account.SignedOut }

        assertFalse(sync.syncAll())

        assertEquals(listOf("payment:p2"), dao.outboxRows.value.map { it.id })
        assertEquals(SyncHealth.SignedOut, sync.status("g1").first().health)
    }

    @Test
    fun `leaving lets go of the name and the group in one commit`() = runTest {
        holds("g1")

        sync.leaveGroup("g1")

        assertEquals(listOf("commit update groups/g1/members/me-g1 [claimedByUid, promptPay], delete groups/g1/pay/me-g1, remove groups/g1 memberUids [$UID]"), firestore.writes)
        assertNull(dao.groups["g1"])
    }

    @Test
    fun `signing out forgets which groups were read so the next account lists their names again`() = runTest {
        holds("g1")
        sync.syncGroup("g1")
        sync.signOut()

        auth.account.value = auth.signedIn
        holds("g1")
        sync.syncGroup("g1")

        assertEquals(2, firestore.calls.count { it == "list groups/g1/members" })
    }

    @Test
    fun `a new invite stops letting people in after ninety days`() = runTest {
        holds("g1")
        createdHere("g1")

        val code = sync.newInvite("g1")

        assertEquals(Instant.fromEpochMilliseconds(clock.nowMillis + NINETY_DAYS), firestore.created.getValue("invites/$code")["expiresAt"])
    }

    @Test
    fun `an invite past its end date says it has expired and writes nothing`() = runTest {
        firestore.docs["invites/C1"] = invite(expiresAt = "1970-01-01T00:16:00Z")

        val expired = assertFailsWith<JoinException> { sync.openJoin("C1") }

        assertEquals(AppText.copy.shared.sync.inviteExpired, expired.message)
        assertTrue(firestore.writes.isEmpty())
    }

    @Test
    fun `an invite before its end date and one with no end date still open`() = runTest {
        firestore.docs["invites/C1"] = invite(expiresAt = "2100-01-01T00:00:00Z")
        firestore.docs["invites/C2"] = invite(expiresAt = null)

        assertEquals("g1", sync.openJoin("C1").groupId)
        assertEquals("g1", sync.openJoin("C2").groupId)
    }

    @Test
    fun `deleting for everyone clears every PromptPay ID and stamps the time in one commit`() = runTest {
        holds("g1")
        createdHere("g1")
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), heldDoc("bo", "uid-2", PROMPT_PAY))
        firestore.collections["groups/g1/pay"] = listOf(payDoc(ME, UID))

        sync.deleteForEveryone("g1")

        assertEquals(
            listOf(
                "commit update invites/C1 [active, names], update groups/g1 [deleted, inviteActive] stamp deletedAt, " +
                    "update groups/g1/members/bo [promptPay], delete groups/g1/pay/$ME",
            ),
            firestore.writes,
        )
        assertNull(dao.groups["g1"])
    }

    @Test
    fun `a creator deleting their account switches the invite off as they leave`() = runTest {
        holds("g1")
        createdHere("g1")

        sync.deleteAccount()

        assertEquals(
            listOf(
                "commit update groups/g1/members/me-g1 [claimedByUid, promptPay], delete groups/g1/pay/me-g1, remove groups/g1 memberUids [$UID] set {inviteActive=false}",
                "patch invites/C1",
                "delete sessions/$UID",
            ),
            firestore.writes,
        )
    }

    @Test
    fun `a member who did not create the group leaves its invite alone when deleting their account`() = runTest {
        holds("g1")
        dao.groups["g1"] = dao.groups.getValue("g1").copy(creatorUid = "uid-2", inviteCode = "C1", inviteActive = true)

        sync.deleteAccount()

        assertEquals(
            listOf("commit update groups/g1/members/me-g1 [claimedByUid, promptPay], delete groups/g1/pay/me-g1, remove groups/g1 memberUids [$UID]", "delete sessions/$UID"),
            firestore.writes,
        )
    }

    @Test
    fun `opening an invite that lists its names writes nothing and offers them`() = runTest {
        firestore.docs["invites/C1"] = invite(expiresAt = null, names = listOf(mapOf("id" to "bo", "name" to "Bo")))
        firestore.hidden += "groups/g1"

        val choice = sync.openJoin("C1")

        assertEquals(listOf("bo" to "Bo"), choice.names)
        assertEquals("C1", choice.code)
        assertFalse(choice.member)
        assertTrue(firestore.writes.isEmpty())
        assertTrue(firestore.calls.none { it.startsWith("list") })
    }

    @Test
    fun `an invite from an older build registers the join first and lists the names`() = runTest {
        firestore.docs["invites/C1"] = invite(expiresAt = null)
        firestore.hidden += "groups/g1"
        firestore.collections["groups/g1/members"] = listOf(memberDoc("bo"))

        val choice = sync.openJoin("C1")

        assertEquals(listOf("create groups/g1/joins/$UID"), firestore.writes)
        assertEquals(listOf("bo" to "bo"), choice.names)
    }

    @Test
    fun `a member opening the link again registers nothing and is offered their own name`() = runTest {
        holds("g1")
        firestore.docs["invites/C1"] = invite(expiresAt = null, names = emptyList())

        val choice = sync.openJoin("C1")

        assertEquals(ME, choice.mine?.first)
        assertTrue(choice.member)
        assertTrue(firestore.writes.isEmpty())
    }

    @Test
    fun `picking a name registers the join in the same commit`() = runTest {
        holds("g1")

        sync.join(JoinChoice("g1", "Flat", listOf("bo" to "Bo"), code = "C1"), "bo")

        assertEquals(
            "commit set groups/g1/joins/$UID [code], update groups/g1/members/bo [claimedByUid], append groups/g1 memberUids [$UID]",
            firestore.writes.first(),
        )
    }

    @Test
    fun `a member picking their own name again registers nothing`() = runTest {
        holds("g1")

        sync.join(JoinChoice("g1", "Flat", emptyList(), mine = ME to "Mali", code = "C1", member = true), ME)

        assertEquals("commit update groups/g1/members/$ME [claimedByUid], append groups/g1 memberUids [$UID]", firestore.writes.first())
    }

    @Test
    fun `a refused pick from the invite's names registers the join to tell a taken name`() = runTest {
        firestore.refusing["groups/g1/members/bo"] = 403
        firestore.docs["groups/g1/members/bo"] = heldDoc("bo", "uid-2")

        val taken = assertFailsWith<JoinException> { sync.join(JoinChoice("g1", "Flat", listOf("bo" to "Bo"), code = "C1"), "bo") }

        assertEquals(AppText.copy.shared.sync.nameTaken, taken.message)
        assertEquals(listOf("create groups/g1/joins/$UID"), firestore.writes)
    }

    @Test
    fun `a refused pick whose code has stopped says the invite expired`() = runTest {
        firestore.refusing["groups/g1/joins/$UID"] = 403
        firestore.docs["groups/g1/members/bo"] = heldDoc("bo", "uid-2")

        val expired = assertFailsWith<JoinException> { sync.join(JoinChoice("g1", "Flat", listOf("bo" to "Bo"), code = "C1"), "bo") }

        assertEquals(AppText.copy.shared.sync.inviteExpired, expired.message)
    }

    @Test
    fun `a new invite lists the names nobody holds and the old one is emptied`() = runTest {
        holds("g1")
        createdHere("g1")
        dao.members["bo"] = GroupMemberEntity("bo", "g1", "Bo", personId = null, archived = false, createdAtEpochMillis = 2)
        dao.members["cy"] = GroupMemberEntity("cy", "g1", "Cy", personId = null, archived = true, createdAtEpochMillis = 3)

        val code = sync.newInvite("g1")

        assertEquals(listOf(mapOf("id" to "bo", "name" to "Bo")), firestore.created.getValue("invites/$code")[INVITE_NAMES])
        assertEquals(mapOf("active" to false, INVITE_NAMES to emptyList<String>()), firestore.sent["invites/C1"])
    }

    @Test
    fun `a pull that sees a new name nobody holds puts it on the live invite`() = runTest {
        holds("g1")
        firestore.docs["groups/g1"] = groupDoc("g1").let { it.copy(fields = it.fields + ("inviteCode" to "C1") + ("inviteActive" to true)) }
        firestore.collections["groups/g1/members"] = listOf(memberDoc(ME), memberDoc("bo"))

        sync.syncGroup("g1")

        assertEquals(mapOf(INVITE_NAMES to listOf(mapOf("id" to "bo", "name" to "bo"))), firestore.sent["invites/C1"])
    }

    private fun holds(vararg ids: String) = holdGroups(dao, firestore, *ids)

    /** This account made [groupId] and its live code is C1. */
    private fun createdHere(groupId: String) {
        dao.groups[groupId] = dao.groups.getValue(groupId).copy(creatorUid = UID, inviteCode = "C1", inviteActive = true)
    }

    /** An invite to g1; [names] null is one made by a build before L4. */
    private fun invite(expiresAt: String?, names: List<Map<String, String>>? = null) = FirestoreDoc(
        id = "C1",
        name = "invites/C1",
        fields = mapOf("groupId" to "g1", "groupName" to "Flat", "active" to true, "creatorUid" to "uid-2", "expiresAt" to expiresAt) +
            (names?.let { mapOf(INVITE_NAMES to it) } ?: emptyMap()),
    )

    private companion object {
        const val NINETY_DAYS = 90L * 24 * 60 * 60 * 1000
    }
}

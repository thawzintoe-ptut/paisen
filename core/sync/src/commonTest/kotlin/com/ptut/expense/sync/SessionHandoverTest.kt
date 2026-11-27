package com.ptut.expense.sync

import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.push.MemoryPushLeftovers
import com.ptut.expense.sync.push.PushRegistrar
import com.ptut.expense.sync.push.PushTokenHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHandoverTest {

    private val firestore = FakeFirestore()
    private val dao = FakeSharedDao()
    private val clock = TestClock(nowMillis = 1_000_000L)
    private val auth = FakeAuth(UID)
    private val notice = MemoryEndedHereNotice()
    private val tokens = PushTokenHolder().apply { set("fid-here") }
    private val registrar = PushRegistrar(auth, tokens, firestore, enabled = true, clock = clock, leftovers = MemoryPushLeftovers())
    private val sync = SharedSync(auth, firestore, FakeSharedRepository(dao), dao, clock, registrar = registrar, notice = notice)

    @Test
    fun `the newest sign-in stores its session and deletes the other phones of the account`() = runTest {
        firestore.collections["devices"] = listOf(device("fid-here", UID), device("fid-old", UID), device("fid-theirs", "uid-2"))

        sync.syncAll()

        assertEquals(mapOf<String, Any?>("signedInAt" to SIGNED_IN_AT), firestore.sent["sessions/$UID"])
        assertTrue("delete devices/fid-old" in firestore.writes)
        assertFalse(firestore.writes.any { it == "delete devices/fid-here" || it == "delete devices/fid-theirs" })
    }

    @Test
    fun `a failed device clean-up does not fail the sync`() = runTest {
        holdGroups(dao, firestore, "g1")
        firestore.failing += "query devices"

        assertEquals(SyncOutcome(groupsChanged = false, failed = false), sync.syncAllOutcome())
        assertTrue(auth.account.value is Account.SignedIn)
    }

    @Test
    fun `the same sign-in keeps the session and writes nothing`() = runTest {
        session(SIGNED_IN_AT)

        sync.syncAll()

        assertFalse(firestore.writes.any { "sessions" in it || "devices" in it })
        assertTrue(auth.account.value is Account.SignedIn)
    }

    @Test
    fun `a newer sign-in elsewhere with nothing waiting signs out and forgets the groups and keeps the notice`() = runTest {
        holdGroups(dao, firestore, "g1")
        session(SIGNED_IN_AT + 60)

        assertTrue(sync.syncAll())

        assertTrue(auth.account.value is Account.SignedOut)
        assertTrue(dao.groups.isEmpty())
        assertTrue(notice.read())
        assertTrue(sync.signedOutElsewhere.value)
        val nextLaunch = SharedSync(auth, firestore, FakeSharedRepository(dao), dao, clock, notice = notice)
        assertTrue(nextLaunch.signedOutElsewhere.value)
        nextLaunch.acknowledgeSignedOutElsewhere()
        assertFalse(notice.read())
    }

    @Test
    fun `a newer sign-in elsewhere waits for a change that cannot go and says how many wait`() = runTest {
        holdGroups(dao, firestore, "g1")
        queuePayment(dao, "p1", "g1")
        firestore.refusing["groups/g1/entries/p1"] = 429
        session(SIGNED_IN_AT + 60)

        assertFalse(sync.syncAll())

        assertTrue(auth.account.value is Account.SignedIn)
        assertTrue("g1" in dao.groups)
        assertFalse(notice.read())
        assertEquals(GroupSyncStatus(SyncHealth.EndingElsewhere, 1), sync.overallStatus().first())
    }

    @Test
    fun `while another phone holds the account a sync only sends what waits and then signs out`() = runTest {
        holdGroups(dao, firestore, "g1")
        queuePayment(dao, "p1", "g1")
        firestore.refusing["groups/g1/entries/p1"] = 429
        session(SIGNED_IN_AT + 60)
        sync.syncAll()
        firestore.calls.clear()

        firestore.refusing.clear()
        assertTrue(sync.syncGroup("g1"))

        assertFalse(firestore.calls.any { "groups/g1" in it })
        assertTrue(auth.account.value is Account.SignedOut)
        assertTrue(notice.read())
    }

    @Test
    fun `the group screen poll checks the session too but not on every poll`() = runTest {
        holdGroups(dao, firestore, "g1")

        sync.syncGroup("g1")
        sync.syncGroup("g1")

        assertEquals(1, firestore.calls.count { it == "get sessions/$UID" })
    }

    @Test
    fun `a session check that could not answer is asked again at the next sync`() = runTest {
        holdGroups(dao, firestore, "g1")
        firestore.failing += "get sessions"
        sync.syncAll()

        firestore.failing.clear()
        sync.syncAll()
        sync.syncAll()

        assertEquals(2, firestore.calls.count { it == "get sessions/$UID" })
    }

    @Test
    fun `a token with no sign-in time is checked again at the next sync`() = runTest {
        auth.authTime = null
        sync.syncAll()

        auth.authTime = SIGNED_IN_AT
        sync.syncAll()

        assertEquals(1, firestore.calls.count { it == "get sessions/$UID" })
        assertEquals(mapOf<String, Any?>("signedInAt" to SIGNED_IN_AT), firestore.sent["sessions/$UID"])
    }

    @Test
    fun `a group that could not be brought back is looked for again at the next sync`() = runTest {
        firestore.memberships = listOf(groupDoc("g2"))
        firestore.docs["groups/g2"] = groupDoc("g2")
        firestore.failing += "groups/g2"
        sync.syncAll()
        sync.syncAll()
        assertEquals(2, firestore.calls.count { it == "query groups" })

        firestore.failing.clear()
        sync.syncAll()
        sync.syncAll()

        assertEquals(3, firestore.calls.count { it == "query groups" })
        assertTrue(dao.groups.getValue("g2").remote)
    }

    private fun session(signedInAt: Long) {
        firestore.docs["sessions/$UID"] = FirestoreDoc(UID, "sessions/$UID", mapOf("signedInAt" to signedInAt))
    }

    private fun device(fid: String, uid: String) = FirestoreDoc(fid, "devices/$fid", mapOf("uid" to uid, "platform" to "android"))
}

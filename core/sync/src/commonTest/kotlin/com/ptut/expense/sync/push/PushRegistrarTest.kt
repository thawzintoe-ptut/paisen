package com.ptut.expense.sync.push

import com.ptut.expense.sync.FakeAuth
import com.ptut.expense.sync.FakeFirestore
import com.ptut.expense.sync.FirestoreStatus
import com.ptut.expense.sync.TestClock
import com.ptut.expense.sync.auth.Account
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PushRegistrarTest {

    private val firestore = FakeFirestore()
    private val tokens = PushTokenHolder().apply { set("fid-1") }

    private fun registrar(auth: FakeAuth, leftovers: PushLeftovers) =
        PushRegistrar(auth, tokens, firestore, enabled = true, clock = TestClock(5), leftovers = leftovers)

    @Test
    fun `sign-out removes the device document this phone wrote`() = runTest {
        val leftovers = MemoryPushLeftovers()
        val registrar = registrar(FakeAuth("uid-1"), leftovers)

        registrar.start(backgroundScope)
        runCurrent()
        registrar.forgetDevice()

        assertEquals(listOf("set devices/fid-1", "delete devices/fid-1"), firestore.writes)
        assertNull(leftovers.read())
    }

    @Test
    fun `a device write that fails at launch is tried again so the phone still hears pushes`() = runTest {
        val registrar = registrar(FakeAuth("uid-1"), MemoryPushLeftovers())
        firestore.failing += "devices/fid-1"

        registrar.start(backgroundScope)
        runCurrent()
        firestore.failing.clear()
        advanceTimeBy(6.seconds)
        registrar.forgetDevice()

        assertTrue("delete devices/fid-1" in firestore.writes)
    }

    @Test
    fun `a delete that fails at sign-out is kept for a later start`() = runTest {
        val leftovers = MemoryPushLeftovers()
        val registrar = registrar(FakeAuth("uid-1"), leftovers)
        firestore.refusing["devices/fid-1"] = FirestoreStatus.NO_ANSWER

        registrar.forgetDevice()

        assertEquals(PushLeftover("fid-1", "uid-1"), leftovers.read())
    }

    @Test
    fun `the same account deletes its old document at a later start`() = runTest {
        val leftovers = MemoryPushLeftovers().apply { write(PushLeftover("fid-old", "uid-1")) }

        registrar(FakeAuth("uid-1"), leftovers).start(backgroundScope)
        runCurrent()

        assertEquals(listOf("delete devices/fid-old", "set devices/fid-1"), firestore.writes)
        assertNull(leftovers.read())
    }

    @Test
    fun `the same account on the same installation writes over it`() = runTest {
        val leftovers = MemoryPushLeftovers().apply { write(PushLeftover("fid-1", "uid-1")) }

        registrar(FakeAuth("uid-1"), leftovers).start(backgroundScope)
        runCurrent()

        assertEquals(listOf("set devices/fid-1"), firestore.writes)
        assertNull(leftovers.read())
    }

    @Test
    fun `a delete refused for good needs nothing more`() = runTest {
        val leftovers = MemoryPushLeftovers().apply { write(PushLeftover("fid-old", "uid-1")) }
        firestore.refusing["devices/fid-old"] = FirestoreStatus.FORBIDDEN

        registrar(FakeAuth("uid-1"), leftovers).start(backgroundScope)
        runCurrent()

        assertNull(leftovers.read())
    }

    @Test
    fun `a delete that fails again waits for the next start`() = runTest {
        val leftovers = MemoryPushLeftovers().apply { write(PushLeftover("fid-old", "uid-1")) }
        firestore.refusing["devices/fid-old"] = FirestoreStatus.NO_ANSWER

        registrar(FakeAuth("uid-1"), leftovers).start(backgroundScope)
        runCurrent()

        assertEquals(PushLeftover("fid-old", "uid-1"), leftovers.read())
    }

    @Test
    fun `signed out the platform retires the installation since no delete is allowed`() = runTest {
        val leftovers = MemoryPushLeftovers(canRetire = true).apply { write(PushLeftover("fid-1", "uid-1")) }
        val auth = FakeAuth("uid-1").apply { account.value = Account.SignedOut }

        registrar(auth, leftovers).start(backgroundScope)
        runCurrent()

        assertEquals(listOf("fid-1"), leftovers.retired)
        assertEquals(emptyList(), firestore.writes)
        assertNull(leftovers.read())
    }

    @Test
    fun `another account keeps the leftover while the platform cannot retire it`() = runTest {
        val leftovers = MemoryPushLeftovers(canRetire = false).apply { write(PushLeftover("fid-1", "uid-old")) }

        registrar(FakeAuth("uid-1"), leftovers).start(backgroundScope)
        runCurrent()

        assertEquals(PushLeftover("fid-1", "uid-old"), leftovers.read())
    }
}

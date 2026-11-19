package com.ptut.expense.sync.push

import com.ptut.expense.sync.FakeAuth
import com.ptut.expense.sync.FakeFirestore
import com.ptut.expense.sync.TestClock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PushRegistrarTest {

    @Test
    fun `sign-out removes the device document this phone wrote`() = runTest {
        val firestore = FakeFirestore()
        val tokens = PushTokenHolder().apply { set("fid-1") }
        val registrar = PushRegistrar(FakeAuth("uid-1"), tokens, firestore, enabled = true, clock = TestClock(5))

        registrar.start(backgroundScope)
        runCurrent()
        registrar.forgetDevice()

        assertEquals(listOf("set devices/fid-1", "delete devices/fid-1"), firestore.writes)
    }
}

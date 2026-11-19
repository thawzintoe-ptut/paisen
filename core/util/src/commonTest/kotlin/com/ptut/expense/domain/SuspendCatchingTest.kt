package com.ptut.expense.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SuspendCatchingTest {

    @Test
    fun aValueComesBackAsSuccess() {
        assertEquals(Result.success(3), runSuspendCatching { 1 + 2 })
    }

    @Test
    fun aFailureComesBackAsFailure() {
        val result = runSuspendCatching { error("write failed") }
        assertTrue(result.isFailure)
        assertEquals("write failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun aCancellationIsThrownOn() {
        assertFailsWith<CancellationException> { runSuspendCatching { throw CancellationException("left the screen") } }
    }
}

package com.ptut.expense.platform.lock

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {

    private var now = 0L
    private var on = true

    private fun lock() = AppLock(enabled = { on }, nowMillis = { now })

    @Test
    fun `the app opens locked when the switch is on`() {
        assertTrue(lock().locked.value)
    }

    @Test
    fun `the app opens unlocked when the switch is off`() {
        on = false

        assertFalse(lock().locked.value)
    }

    @Test
    fun `a short trip to the photo picker does not lock again`() {
        val lock = lock().apply { unlock() }

        lock.movedToBackground()
        now += 29_000
        lock.movedToForeground()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `thirty seconds away locks the app again`() {
        val lock = lock().apply { unlock() }

        lock.movedToBackground()
        now += 30_000
        lock.movedToForeground()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `with the switch off a long time away never locks`() {
        on = false
        val lock = lock()

        lock.movedToBackground()
        now += 600_000
        lock.movedToForeground()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `coming back without having left changes nothing`() {
        val lock = lock().apply { unlock() }

        now += 600_000
        lock.movedToForeground()

        assertFalse(lock.locked.value)
    }
}

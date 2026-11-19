package com.ptut.expense.platform.files

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadUpToTest {

    @Test
    fun `a file within the cap is read whole`() {
        assertEquals(50, ByteArrayInputStream(ByteArray(50)).readUpTo(50)?.size)
    }

    @Test
    fun `a file past the cap is not read`() {
        assertNull(ByteArrayInputStream(ByteArray(51)).readUpTo(50))
    }
}

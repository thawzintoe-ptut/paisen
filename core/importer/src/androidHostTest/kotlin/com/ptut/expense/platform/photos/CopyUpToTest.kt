package com.ptut.expense.platform.photos

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyUpToTest {

    @Test
    fun `a picture within the cap is copied whole`() {
        val out = ByteArrayOutputStream()

        assertTrue(ByteArrayInputStream(ByteArray(100) { it.toByte() }).copyUpTo(100, out))
        assertContentEquals(ByteArray(100) { it.toByte() }, out.toByteArray())
    }

    @Test
    fun `a picture past the cap stops the copy`() {
        val out = ByteArrayOutputStream()

        assertFalse(ByteArrayInputStream(ByteArray(101)).copyUpTo(100, out))
        assertTrue(out.size() <= 100)
    }
}

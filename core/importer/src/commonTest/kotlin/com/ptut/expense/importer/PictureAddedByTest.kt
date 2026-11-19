package com.ptut.expense.importer

import com.ptut.expense.platform.photos.SlipCandidate
import com.ptut.expense.platform.photos.SlipSource
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PictureAddedByTest {

    @Test
    fun `the one new picture is the one the share added`() = runTest {
        val source = Listing(failure = null)

        assertEquals("new", source.pictureAddedBy { source.ids += "new" })
    }

    @Test
    fun `a listing that fails cannot tell the picture apart`() = runTest {
        val source = Listing(failure = IllegalStateException("no answer"))

        assertNull(source.pictureAddedBy {})
    }

    @Test
    fun `a cancelled listing stops the share instead of answering null`() = runTest {
        val source = Listing(failure = CancellationException("left"))

        assertFailsWith<CancellationException> { source.pictureAddedBy {} }
    }

    private class Listing(private val failure: Throwable?) : SlipSource {
        val ids = mutableListOf("old")

        override suspend fun candidates(): List<SlipCandidate> {
            failure?.let { throw it }
            return ids.map { SlipCandidate(it, capturedAtEpochMillis = 0, folderId = "shared", folderName = "Shared") }
        }

        override suspend fun bytesOf(id: String): ByteArray? = null

        override suspend fun delete(id: String) = Unit
    }
}

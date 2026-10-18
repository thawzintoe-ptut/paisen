package com.ptut.expense.platform.photos

import com.ptut.expense.importer.FakeSlipSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #17 puts two folders behind one seam on Android, and an id from either has to survive a round
 * trip through Room and come back to the part that owns the picture. Nothing else checks that: a
 * misrouted id looks like a picture that has gone, which the importer is built to shrug off.
 */
class CompositeSlipSourceTest {

    private val folder = FakeSlipSource(mapOf("1" to ("from the folder" to 200L)))
    private val shared = FakeSlipSource(mapOf("a" to ("from the share sheet" to 100L)))
    private val source = CompositeSlipSource(mapOf("folder" to folder, "shared" to shared))

    @Test
    fun `every candidate says which part it came from`() = runTest {
        assertEquals(listOf("shared:a", "folder:1"), source.candidates().map { it.id })
    }

    @Test
    fun `candidates come back oldest first across both parts`() = runTest {
        folder.add("2", "older than either", capturedAtEpochMillis = 50L)

        assertEquals(
            listOf(50L, 100L, 200L),
            source.candidates().map { it.capturedAtEpochMillis },
        )
    }

    @Test
    fun `bytes come from the part that owns the id`() = runTest {
        assertEquals("from the folder", source.bytesOf("folder:1")?.decodeToString())
        assertEquals("from the share sheet", source.bytesOf("shared:a")?.decodeToString())
    }

    @Test
    fun `deleting reaches only the part that owns the id`() = runTest {
        source.delete("folder:1")

        assertTrue(folder.remaining.isEmpty())
        assertEquals(setOf("a"), shared.remaining)
    }

    /** An id left by a build that bound a different set of parts. NFR-4 sweeps those too. */
    @Test
    fun `an id no part claims is not an error`() = runTest {
        assertNull(source.bytesOf("camera:9"))
        source.delete("camera:9")
        source.delete("no separator at all")
    }

    /**
     * US-8 counts a scan that found nothing as a success. If the folder's permission failure were
     * swallowed because the share inbox happened to be empty the run would look healthy.
     */
    @Test
    fun `a part that cannot scan fails the whole scan`() = runTest {
        folder.scanFails = true

        assertFailsWith<IllegalStateException> { source.candidates() }
    }

    @Test
    fun `a part name holding the separator is refused`() {
        assertFailsWith<IllegalArgumentException> {
            CompositeSlipSource(mapOf("folder:shared" to folder))
        }
    }
}

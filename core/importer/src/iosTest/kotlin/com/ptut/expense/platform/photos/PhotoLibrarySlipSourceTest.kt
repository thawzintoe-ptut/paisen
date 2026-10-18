package com.ptut.expense.platform.photos

import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.WatchedFolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one distinction US-8 rests on: a scan that could not look is a failure, and a scan that
 * looked and found nothing is a success. Collapsing the two is how an import breaks while the
 * Today banner stays green.
 */
class PhotoLibrarySlipSourceTest {

    @Test
    fun `a scan without photo access fails with the fix in the message`() = runTest {
        val source = PhotoLibrarySlipSource(isAllowed = { false }, folders = FakeFolders())

        val denied = assertFailsWith<PhotoAccessDenied> { source.candidates() }
        val reason = denied.message.orEmpty()

        assertTrue(reason.contains("iOS Settings"), reason)
    }

    /** Nothing is watched, so there is nothing to look in — empty and healthy, not an error. */
    @Test
    fun `a library with no watched album scans clean`() = runTest {
        val source = PhotoLibrarySlipSource(isAllowed = { true }, folders = FakeFolders())

        assertEquals(emptyList(), source.candidates())
    }

    /** No such album on this simulator, which is still the empty-but-healthy case. */
    @Test
    fun `an album the library does not hold scans clean`() = runTest {
        val source = PhotoLibrarySlipSource(
            isAllowed = { true },
            folders = FakeFolders(listOf(album("not-a-real-album-identifier"))),
        )

        assertEquals(emptyList(), source.candidates())
    }

    @Test
    fun `an asset that is no longer there reads as gone rather than as a failure`() = runTest {
        val source = PhotoLibrarySlipSource(isAllowed = { true }, folders = FakeFolders())

        assertNull(source.bytesOf("not-a-local-identifier"))
    }

    /** iOS will not let this app remove a picture it did not write, and that is not an error. */
    @Test
    fun `deleting a picture this app does not own is quietly accepted`() = runTest {
        val source = PhotoLibrarySlipSource(isAllowed = { true }, folders = FakeFolders())

        source.delete("not-a-local-identifier")
    }

    private fun album(id: String) =
        WatchedFolder(id = id, name = id, path = "Album you made", slipsRead = 0, checked = false)
}

private class FakeFolders(seed: List<WatchedFolder> = emptyList()) : WatchedFolderRepository {

    override val folders: StateFlow<List<WatchedFolder>> = MutableStateFlow(seed)

    override suspend fun watch(id: String, name: String, path: String) = Unit

    override suspend fun stopWatching(id: String) = Unit

    override suspend fun known(): List<WatchedFolder> = folders.value

    override suspend fun scannedThroughSeconds(id: String): Long = 0L

    override suspend fun recordScan(id: String, throughSeconds: Long, checkedAtEpochMillis: Long) = Unit

    override suspend fun addSlipsRead(id: String, read: Int) = Unit
}

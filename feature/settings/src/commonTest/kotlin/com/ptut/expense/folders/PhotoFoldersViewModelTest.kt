package com.ptut.expense.folders

import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.WatchedFolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoFoldersViewModelTest {

    @BeforeTest
    fun pinTheDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun releaseTheDispatcher() = Dispatchers.resetMain()

    @Test
    fun `every folder on the phone is offered with the watched ones first`() = runTest {
        val model = viewModel(
            offers = listOf(device("camera", "Camera", "DCIM/Camera"), device("kplus", "K PLUS", "Pictures/K PLUS")),
            watched = listOf(watched("kplus", "K PLUS", "Pictures/K PLUS", slipsRead = 312, checked = true)),
        )
        runCurrent()

        val rows = model.uiState.value.rows
        assertEquals(listOf("K PLUS", "Camera"), rows.map { it.name })
        assertTrue(rows.first().watched)
        assertFalse(rows.last().watched)
    }

    @Test
    fun `the chip counts the folders watched and the slips they read`() = runTest {
        val model = viewModel(
            offers = listOf(device("kplus"), device("scb"), device("shots")),
            watched = listOf(
                watched("kplus", slipsRead = 312, checked = true),
                watched("scb", slipsRead = 48, checked = true),
                watched("shots", slipsRead = 9, checked = true),
            ),
        )
        runCurrent()

        assertEquals("3 folders watched · 369 slips read so far", model.uiState.value.summary)
    }

    @Test
    fun `the badged rows are exactly the chip's total`() = runTest {
        val model = viewModel(
            offers = listOf(device("kplus"), device("camera"), device("line")),
            watched = listOf(
                watched("kplus", slipsRead = 312, checked = true),
                watched("camera", slipsRead = 0, checked = true),
                watched("line", slipsRead = 0, checked = false),
            ),
        )
        runCurrent()

        val state = model.uiState.value
        assertEquals(listOf("kplus"), state.rows.filter { it.badged }.map { it.id })
        assertTrue(state.summary.contains("312 slips read so far"))
    }

    @Test
    fun `a folder looked at and empty reads differently from one never looked at`() = runTest {
        val model = viewModel(
            offers = listOf(device("camera"), device("line")),
            watched = listOf(
                watched("camera", slipsRead = 0, checked = true),
                watched("line", slipsRead = 0, checked = false),
            ),
        )
        runCurrent()

        val byId = model.uiState.value.rows.associateBy { it.id }
        assertEquals("No slips found yet", byId.getValue("camera").reading)
        assertEquals("Not checked", byId.getValue("line").reading)
    }

    @Test
    fun `an unwatched folder states it has not been checked`() = runTest {
        val model = viewModel(offers = listOf(device("download")), watched = emptyList())
        runCurrent()

        val row = model.uiState.value.rows.single()
        assertEquals("Not checked", row.reading)
        assertFalse(row.badged)
    }

    @Test
    fun `a watched folder the phone stopped offering keeps its row and its count`() = runTest {
        val model = viewModel(
            offers = listOf(device("camera")),
            watched = listOf(watched("sdcard", "Old card", "SD/Pictures", slipsRead = 40, checked = true)),
        )
        runCurrent()

        val row = model.uiState.value.rows.first { it.id == "sdcard" }
        assertEquals("40 slips read", row.reading)
        assertTrue(row.watched)
    }

    @Test
    fun `one slip read is singular`() = runTest {
        val model = viewModel(
            offers = listOf(device("kplus")),
            watched = listOf(watched("kplus", slipsRead = 1, checked = true)),
        )
        runCurrent()

        assertEquals("1 slip read", model.uiState.value.rows.single().reading)
        assertEquals("1 folder watched · 1 slip read so far", model.uiState.value.summary)
    }

    @Test
    fun `a tick changes nothing until save and save starts or stops watching`() = runTest {
        val repository = FakeFolders()
        val model = watching(PhotoFoldersViewModel(FakeCatalog(listOf(device("kplus", "K PLUS", "Pictures/K PLUS"))), repository))
        runCurrent()

        model.onToggle("kplus")
        runCurrent()
        assertTrue(repository.folders.value.none { it.watched })
        assertTrue(model.uiState.value.canSave)

        model.save()
        runCurrent()
        assertEquals(listOf("kplus"), repository.folders.value.filter { it.watched }.map { it.id })
        assertFalse(model.uiState.value.canSave)

        model.onToggle("kplus")
        model.save()
        runCurrent()
        assertTrue(repository.folders.value.none { it.watched })
    }

    @Test
    fun `a folder switched off keeps the slips it already read`() = runTest {
        val repository = FakeFolders(
            listOf(watched("kplus", "K PLUS", "Pictures/K PLUS", slipsRead = 312, checked = true)),
        )
        val model = watching(
            PhotoFoldersViewModel(FakeCatalog(listOf(device("kplus", "K PLUS", "Pictures/K PLUS"))), repository),
        )
        runCurrent()

        model.onToggle("kplus")
        model.save()
        runCurrent()

        val row = model.uiState.value.rows.single()
        assertFalse(row.watched)
        // Still badged and still counted: the reader did read 312 slips there, and turning the
        // folder off does not make that untrue.
        assertEquals("312 slips read", row.reading)
        assertEquals("0 folders watched · 0 slips read so far", model.uiState.value.summary)
    }

    @Test
    fun `a catalogue answering with nothing is a permission problem`() = runTest {
        val empty = viewModel(offers = emptyList(), watched = emptyList())
        runCurrent()

        assertFalse(empty.uiState.value.accessGranted)
    }

    /** [PhotoFoldersUiState] is shared while subscribed, so a test with no collector reads the seed. */
    private fun TestScope.viewModel(offers: List<DeviceFolder>, watched: List<WatchedFolder>) =
        watching(PhotoFoldersViewModel(FakeCatalog(offers), FakeFolders(watched)))

    private fun TestScope.watching(model: PhotoFoldersViewModel): PhotoFoldersViewModel {
        backgroundScope.launch { model.uiState.collect {} }
        return model
    }

    private fun device(id: String, name: String = id, path: String = "Pictures/$id") =
        DeviceFolder(id = id, name = name, path = path)

    private fun watched(
        id: String,
        name: String = id,
        path: String = "Pictures/$id",
        slipsRead: Int = 0,
        checked: Boolean = false,
    ) = WatchedFolder(id = id, name = name, path = path, slipsRead = slipsRead, checked = checked)

    private class FakeCatalog(private val offers: List<DeviceFolder>) : FolderCatalog {
        override suspend fun available(): List<DeviceFolder> = offers
    }

    private class FakeFolders(seed: List<WatchedFolder> = emptyList()) : WatchedFolderRepository {
        private val state = MutableStateFlow(seed)
        override val folders: StateFlow<List<WatchedFolder>> = state

        override suspend fun watch(id: String, name: String, path: String) {
            val held = state.value.firstOrNull { it.id == id }
            state.value = state.value.filterNot { it.id == id } +
                (held?.copy(watched = true) ?: WatchedFolder(id, name, path, slipsRead = 0, checked = false))
        }

        /** Off, not gone — the row keeps its count, which is what the real repository does. */
        override suspend fun stopWatching(id: String) {
            state.value = state.value.map { if (it.id == id) it.copy(watched = false) else it }
        }

        override suspend fun known(): List<WatchedFolder> = state.value

        override suspend fun scannedThroughSeconds(id: String): Long = 0L

        override suspend fun recordScan(id: String, throughSeconds: Long, checkedAtEpochMillis: Long) = Unit

        override suspend fun addSlipsRead(id: String, read: Int) = Unit
    }
}

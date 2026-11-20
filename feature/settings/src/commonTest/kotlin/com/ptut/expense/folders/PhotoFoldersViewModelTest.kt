package com.ptut.expense.folders

import com.ptut.expense.domain.folders.ApplyWatchedFoldersUseCase
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FakeFolders
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.domain.folders.ObserveWatchedFoldersUseCase
import com.ptut.expense.domain.folders.RefreshFolderCatalogUseCase
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        val model = watching(photoFolders(FakeCatalog(listOf(device("kplus", "K PLUS", "Pictures/K PLUS"))), repository))
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
            photoFolders(FakeCatalog(listOf(device("kplus", "K PLUS", "Pictures/K PLUS"))), repository),
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
    fun `one folder failing does not stop the rest and the ticks stay for another save`() = runTest {
        val toasts = ToastMessages()
        val repository = RefusingFolders(refuses = "camera")
        val catalog = FakeCatalog(listOf(device("camera"), device("kplus")))
        val model = watching(photoFolders(catalog, repository, toasts = toasts))
        runCurrent()

        model.onToggle("camera")
        model.onToggle("kplus")
        model.save()
        runCurrent()

        assertEquals(ToastMessage(AppText.copy.folders.notSaved, tone = ToastTone.Error), toasts.messages.first())
        assertEquals(listOf("kplus"), repository.folders.value.filter { it.watched }.map { it.id })
        assertTrue(model.uiState.value.canSave)
    }

    @Test
    fun `a catalogue that throws says so and leaves the screen usable`() = runTest {
        val toasts = ToastMessages()
        val model = watching(photoFolders(BrokenCatalog(), FakeFolders(), toasts = toasts))
        runCurrent()

        assertEquals(ToastMessage(AppText.copy.folders.notRead, tone = ToastTone.Error), toasts.messages.first())
        assertTrue(model.uiState.value.rows.isEmpty())
    }

    @Test
    fun `a chooser that throws says so and still reads the folders again`() = runTest {
        val toasts = ToastMessages()
        val model = watching(
            photoFolders(FakeCatalog(listOf(device("kplus"))), FakeFolders(), chooser = { error("no screen") }, toasts = toasts),
        )
        runCurrent()

        model.chooseMore()
        runCurrent()

        assertEquals(ToastMessage(AppText.copy.settings.didNotWork, tone = ToastTone.Error), toasts.messages.first())
        assertEquals(listOf("kplus"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `a catalogue answering with nothing is a permission problem`() = runTest {
        val empty = viewModel(offers = emptyList(), watched = emptyList())
        runCurrent()

        assertFalse(empty.uiState.value.accessGranted)
    }

    /** [PhotoFoldersUiState] is shared while subscribed, so a test with no collector reads the seed. */
    private fun TestScope.viewModel(offers: List<DeviceFolder>, watched: List<WatchedFolder>) =
        watching(photoFolders(FakeCatalog(offers), FakeFolders(watched)))

    private fun photoFolders(
        catalog: FolderCatalog,
        folders: WatchedFolderRepository,
        chooser: FolderChooser = FolderChooser {},
        toasts: ToastMessages? = null,
    ) = PhotoFoldersViewModel(
        refreshCatalog = RefreshFolderCatalogUseCase(catalog),
        observeFolders = ObserveWatchedFoldersUseCase(folders),
        applyWatchedFolders = ApplyWatchedFoldersUseCase(folders),
        chooser = chooser,
        toasts = toasts,
    )

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

    private class BrokenCatalog : FolderCatalog {
        override suspend fun available(): List<DeviceFolder> = error("MediaStore is gone")
    }

    private class RefusingFolders(private val refuses: String, private val held: FakeFolders = FakeFolders()) :
        WatchedFolderRepository by held {
        override suspend fun watch(id: String, name: String, path: String) {
            check(id != refuses) { "disk full" }
            held.watch(id, name, path)
        }
    }
}

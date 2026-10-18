package com.ptut.expense.domain.folders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-1 on a fresh install, and the rule that keeps it from becoming a nuisance: it seeds once.
 */
class FirstRunFoldersTest {

    @Test
    fun `a fresh install starts watching the bank's own folder`() = runTest {
        val folders = FakeFolders()
        seeder(folders, "Camera", "K PLUS", "Download").seed()

        assertEquals(listOf("K PLUS"), folders.state.value.map { it.name })
    }

    @Test
    fun `the folder is matched however the phone spells it`() = runTest {
        val folders = FakeFolders()
        seeder(folders, "kplus").seed()

        assertEquals(listOf("kplus"), folders.state.value.map { it.name })
    }

    @Test
    fun `a phone with no such folder is left alone rather than given a guess`() = runTest {
        val folders = FakeFolders()
        seeder(folders, "Camera", "Download", "LINE").seed()

        assertTrue(folders.state.value.isEmpty())
    }

    @Test
    fun `a reader who switched everything off is not handed it back`() = runTest {
        val folders = FakeFolders(
            listOf(folder("kplus", "K PLUS", watched = false)),
        )

        seeder(folders, "K PLUS").seed()

        assertTrue(folders.state.value.none { it.watched })
    }

    @Test
    fun `seeding twice adds nothing the second time`() = runTest {
        val folders = FakeFolders()
        val seeder = seeder(folders, "K PLUS")

        seeder.seed()
        seeder.seed()

        assertEquals(1, folders.state.value.size)
    }

    private fun seeder(folders: FakeFolders, vararg offered: String) = FirstRunFolders(
        catalog = { offered.map { DeviceFolder(id = it.lowercase(), name = it, path = "Pictures/$it") } },
        folders = folders,
    )

    private fun folder(id: String, name: String, watched: Boolean) = WatchedFolder(
        id = id,
        name = name,
        path = "Pictures/$name",
        slipsRead = 0,
        checked = false,
        watched = watched,
    )

    private class FakeFolders(seed: List<WatchedFolder> = emptyList()) : WatchedFolderRepository {
        val state = MutableStateFlow(seed)
        override val folders: StateFlow<List<WatchedFolder>> = state

        override suspend fun watch(id: String, name: String, path: String) {
            state.value = state.value + WatchedFolder(id, name, path, slipsRead = 0, checked = false)
        }

        override suspend fun stopWatching(id: String) {
            state.value = state.value.map { if (it.id == id) it.copy(watched = false) else it }
        }

        override suspend fun known(): List<WatchedFolder> = state.value

        override suspend fun scannedThroughSeconds(id: String): Long = 0L

        override suspend fun recordScan(id: String, throughSeconds: Long, checkedAtEpochMillis: Long) = Unit

        override suspend fun addSlipsRead(id: String, read: Int) = Unit
    }
}

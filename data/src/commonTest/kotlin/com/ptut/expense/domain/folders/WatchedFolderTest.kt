package com.ptut.expense.domain.folders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchedFolderTest {

    @Test
    fun `a folder that has read a slip is badged`() {
        assertTrue(folder(slipsRead = 1, checked = true).foundSlips)
        assertEquals(FolderReading.Found, folder(slipsRead = 1, checked = true).readingState)
    }

    @Test
    fun `a folder looked at and empty is not badged`() {
        val looked = folder(slipsRead = 0, checked = true)

        assertFalse(looked.foundSlips)
        assertEquals(FolderReading.NothingFound, looked.readingState)
    }

    @Test
    fun `a folder never looked at is told apart from one that held nothing`() {
        val never = folder(slipsRead = 0, checked = false)

        assertEquals(FolderReading.NeverChecked, never.readingState)
        assertFalse(never.foundSlips)
    }

    @Test
    fun `the badged rows are the whole of the summary total`() {
        val folders = listOf(
            folder(id = "kplus", slipsRead = 312, checked = true),
            folder(id = "scb", slipsRead = 48, checked = true),
            folder(id = "shots", slipsRead = 9, checked = true),
            folder(id = "camera", slipsRead = 0, checked = true),
            folder(id = "line", slipsRead = 0, checked = false),
        )

        assertEquals(369, folders.slipsRead())
        assertEquals(369, folders.filter { it.foundSlips }.sumOf { it.slipsRead })
    }

    private fun folder(
        id: String = "kplus",
        slipsRead: Int = 0,
        checked: Boolean = false,
    ) = WatchedFolder(id = id, name = id, path = "Pictures/$id", slipsRead = slipsRead, checked = checked)
}

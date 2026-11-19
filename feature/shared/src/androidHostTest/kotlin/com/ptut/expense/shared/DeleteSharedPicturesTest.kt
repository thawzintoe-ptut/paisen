package com.ptut.expense.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteSharedPicturesTest {

    private val folder: File = Files.createTempDirectory("share").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun picture(name: String, writtenAt: Long): File =
        File(folder, name).apply {
            writeBytes(byteArrayOf(1))
            setLastModified(writtenAt)
        }

    @Test
    fun `the next share deletes every earlier picture`() {
        picture("paisen-card.png", writtenAt = 1_000)
        picture("older-card.png", writtenAt = 2_000)

        deleteSharedPictures(folder, sharedBefore = Long.MAX_VALUE)

        assertEquals(0, folder.listFiles()?.size)
    }

    @Test
    fun `opening a screen keeps a picture shared moments ago`() {
        val old = picture("old-card.png", writtenAt = 1_000)
        val recent = picture("paisen-card.png", writtenAt = 5_000)

        deleteSharedPictures(folder, sharedBefore = 4_000)

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun `a folder that was never made is left alone`() {
        deleteSharedPictures(File(folder, "missing"), sharedBefore = Long.MAX_VALUE)

        assertTrue(folder.exists())
    }
}

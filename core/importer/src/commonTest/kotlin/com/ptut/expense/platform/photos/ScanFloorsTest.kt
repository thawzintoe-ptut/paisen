package com.ptut.expense.platform.photos

import com.ptut.expense.domain.folders.FakeFolders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class ScanFloorsTest {

    private val folders = FakeFolders()
    private val floors = ScanFloors(
        folders,
        clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_000)
        },
    )

    @Test
    fun `a listed folder keeps its floor until the run settles it`() = runTest {
        listCamera()

        assertEquals(0L, folders.scannedThroughSeconds("camera"))

        floors.settle()
        assertEquals(30L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a failed read holds the floor below that picture`() = runTest {
        listCamera()

        floors.holdBelow("b")
        floors.settle()

        assertEquals(19L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `two failed reads hold the floor below the older one`() = runTest {
        listCamera()

        floors.holdBelow("c")
        floors.holdBelow("a")
        floors.settle()

        assertEquals(9L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a failed read of a picture this run never handed out changes nothing`() = runTest {
        listCamera()

        floors.holdBelow("unknown")
        floors.settle()

        assertEquals(30L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a settled floor is written once`() = runTest {
        listCamera()
        floors.settle()
        folders.recordScan("camera", throughSeconds = 5, checkedAtEpochMillis = 0)

        floors.settle()

        assertEquals(5L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a run that never settled leaves the floor where it was`() = runTest {
        listCamera()

        floors.startListing()
        floors.settle()

        assertEquals(0L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a full share keeps the floor one second below its newest picture`() = runTest {
        floors.listedThrough("camera", floor = 0, newest = 30, filledShare = true)
        floors.listedThrough("screens", floor = 29, newest = 30, filledShare = true)
        floors.settle()

        assertEquals(29L, folders.scannedThroughSeconds("camera"))
        assertEquals(30L, folders.scannedThroughSeconds("screens"), "a floor that would not move moves anyway")
    }

    @Test
    fun `every folder gets an even share of the run and never less than one`() {
        assertEquals(BATCH_CAP, floors.shareFor(1))
        assertEquals(BATCH_CAP / 4, floors.shareFor(4))
        assertEquals(1, floors.shareFor(BATCH_CAP * 2))
    }

    private fun listCamera() {
        floors.listedThrough("camera", floor = 0, newest = 30, filledShare = false)
        floors.handOut(
            secondsOf = mapOf("a" to 10L, "b" to 20L, "c" to 30L),
            folderOf = mapOf("a" to "camera", "b" to "camera", "c" to "camera"),
        )
    }
}

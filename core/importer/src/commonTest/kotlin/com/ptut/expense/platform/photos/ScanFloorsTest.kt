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
    fun `a listed folder keeps its floor until the next scan settles it`() = runTest {
        listCamera()

        assertEquals(0L, folders.scannedThroughSeconds("camera"))

        floors.settlePrevious()
        assertEquals(30L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a failed read holds the floor below that picture`() = runTest {
        listCamera()

        floors.holdBelow("b")
        floors.settlePrevious()

        assertEquals(19L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `two failed reads hold the floor below the older one`() = runTest {
        listCamera()

        floors.holdBelow("c")
        floors.holdBelow("a")
        floors.settlePrevious()

        assertEquals(9L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a failed read of a picture this run never handed out changes nothing`() = runTest {
        listCamera()

        floors.holdBelow("unknown")
        floors.settlePrevious()

        assertEquals(30L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `a settled floor is written once`() = runTest {
        listCamera()
        floors.settlePrevious()
        folders.recordScan("camera", throughSeconds = 5, checkedAtEpochMillis = 0)

        floors.settlePrevious()

        assertEquals(5L, folders.scannedThroughSeconds("camera"))
    }

    @Test
    fun `every folder gets an even share of the run and never less than one`() {
        assertEquals(BATCH_CAP, floors.shareFor(1))
        assertEquals(BATCH_CAP / 4, floors.shareFor(4))
        assertEquals(1, floors.shareFor(BATCH_CAP * 2))
    }

    private fun listCamera() {
        floors.listedThrough("camera", 30)
        floors.handOut(
            secondsOf = mapOf("a" to 10L, "b" to 20L, "c" to 30L),
            folderOf = mapOf("a" to "camera", "b" to "camera", "c" to "camera"),
        )
    }
}

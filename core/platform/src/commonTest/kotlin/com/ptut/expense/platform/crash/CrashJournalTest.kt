package com.ptut.expense.platform.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashJournalTest {

    private class MemoryFiles : CrashFiles {
        val disk = linkedMapOf<String, String>()
        override fun list() = disk.keys.toList()
        override fun write(name: String, text: String) {
            disk[name] = text
        }
        override fun read(name: String) = disk.getValue(name)
        override fun delete(name: String) {
            disk.remove(name)
        }
        override fun rename(from: String, to: String) {
            disk[to] = disk.remove(from)!!
        }
    }

    private val files = MemoryFiles()
    private var now = 1_000L
    private val journal = CrashJournal(files, CrashHeader("1.2.0 (12)", 25, "Pixel 7, Android 16"), now = { now })

    @Test
    fun aReportKeepsTheBuildAndFramesButNoMessage() {
        journal.record("main", IllegalStateException("Cafe 12345 paid to Aung", RuntimeException("note: Thandar")))

        val report = journal.unseen()!!
        assertTrue(report.startsWith("App: 1.2.0 (12)\nSchema: 25\nDevice: Pixel 7, Android 16\nThread: main\n"))
        assertTrue("IllegalStateException" in report)
        assertFalse("Cafe" in report)
        assertFalse("12345" in report)
        assertFalse("Aung" in report)
        assertFalse("Thandar" in report)
    }

    @Test
    fun onlyTheNewestTenReportsAreKept() {
        repeat(12) {
            now += 1
            journal.record("main", RuntimeException())
        }

        assertEquals((1_003L..1_012L).map { "$it.txt" }, files.disk.keys.sorted())
    }

    @Test
    fun answeringThePromptMarksEveryWaitingReportSeen() {
        journal.record("main", RuntimeException())

        journal.markSeen()

        assertNull(journal.unseen())
        assertEquals(listOf("1000.seen.txt"), files.disk.keys.toList())
    }

    @Test
    fun redactionDropsTheMessageOfACauseAndKeepsItsFrames() {
        val trace = "java.lang.IllegalStateException: 500 THB to Mya\n\tat a.B.c(B.kt:1)\nCaused by: java.io.IOException: slip text\n\t... 3 more"

        assertEquals(
            "java.lang.IllegalStateException\n\tat a.B.c(B.kt:1)\nCaused by: java.io.IOException\n\t... 3 more",
            CrashJournal.redact(trace),
        )
    }

    @Test
    fun aMessageThatRunsOverSeveralLinesLeavesNoneOfThemBehind() {
        val trace = "java.lang.IllegalStateException: Tops Market\n500 THB to Mya Mya\nnote: rent\n\tat a.B.c(B.kt:1)"

        assertEquals("java.lang.IllegalStateException\n\tat a.B.c(B.kt:1)", CrashJournal.redact(trace))
    }
}

package com.ptut.expense

import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Saved = ToastMessage("Saved ฿485 · Food & drink")
private val Later = ToastMessage("Saved ฿120 · Transport")

class AppViewModelTest : ViewModelTest() {

    /** The shell sweeps on start, so every test here needs one. Nothing queued means nothing swept. */
    private fun importer() =
        SlipImporter(FakeSlipSource(), FakeSlipTextReader(), InMemoryImportStore())

    @Test
    fun aMessageShowsAndClearsItselfWithoutAnyoneAskingItTo() = runTest {
        val toasts = ToastMessages()
        val viewModel = AppViewModel(toasts, importer())
        runCurrent()

        assertEquals(null, viewModel.toast.value, "the shell starts saying nothing")

        toasts.show(Saved)
        runCurrent()
        assertEquals(Saved, viewModel.toast.value)

        advanceTimeBy(2_600)
        runCurrent()
        assertEquals(null, viewModel.toast.value)
    }

    /** Two saves in a row leave the second one on screen for its full time, not the first's rest. */
    @Test
    fun aSecondMessageReplacesTheFirstAndStartsTheClockAgain() = runTest {
        val toasts = ToastMessages()
        val viewModel = AppViewModel(toasts, importer())
        runCurrent()

        toasts.show(Saved)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        toasts.show(Later)
        runCurrent()
        assertEquals(Later, viewModel.toast.value)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(Later, viewModel.toast.value, "the second toast inherited the first's clock")

        advanceTimeBy(600)
        runCurrent()
        assertEquals(null, viewModel.toast.value)
    }

    @Test
    fun anUndoOutlastsASaveToastThenLeavesAndSaysSo() = runTest {
        val toasts = ToastMessages()
        val viewModel = AppViewModel(toasts, importer())
        runCurrent()

        var gone = false
        toasts.show(ToastMessage("Deleted Foodland Rama IV", action = ToastAction("Undo") {}, onGone = { gone = true }))
        runCurrent()

        advanceTimeBy(3_000)
        runCurrent()
        assertNotNull(viewModel.toast.value, "the undo left with a save toast's clock")

        advanceTimeBy(1_000)
        runCurrent()
        assertNull(viewModel.toast.value, "the undo outstayed its four seconds")
        assertTrue(gone, "the undo left without saying so")
    }

    @Test
    fun tappingTheUndoRunsItAndTakesTheLineAway() = runTest {
        val toasts = ToastMessages()
        val viewModel = AppViewModel(toasts, importer())
        runCurrent()

        var restored = false
        toasts.show(ToastMessage("Deleted", action = ToastAction("Undo") { restored = true }))
        runCurrent()

        viewModel.runToastAction()
        runCurrent()

        assertTrue(restored, "the undo never ran")
        assertEquals(null, viewModel.toast.value, "the line stayed after it was acted on")
    }

    /** Once the next line has replaced it, the undo is gone with it, and a late tap restores nothing. */
    @Test
    fun anUndoTappedAfterItWasReplacedDoesNothing() = runTest {
        val toasts = ToastMessages()
        val viewModel = AppViewModel(toasts, importer())
        runCurrent()

        var restored = false
        toasts.show(ToastMessage("Deleted", action = ToastAction("Undo") { restored = true }))
        runCurrent()
        toasts.show(ToastMessage("Saved Lawson"))
        runCurrent()

        viewModel.runToastAction()
        runCurrent()

        assertFalse(restored, "the undo ran after the next line had replaced it")
    }
}

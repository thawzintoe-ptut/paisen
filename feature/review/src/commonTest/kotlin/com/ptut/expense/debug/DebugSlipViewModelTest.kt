package com.ptut.expense.debug

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.platform.photos.InMemorySlipSource
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Drives the shipped pipeline end to end. Only the two platform seams are doubles, so what runs
 * here is the same hashing, dedupe, parsing, classification and journalling a real slip goes
 * through — which is the whole reason the debug menu exists.
 */
class DebugSlipViewModelTest : ViewModelTest() {

    private val source = InMemorySlipSource()
    private val store = InMemoryImportStore()

    /**
     * One clock for both, and it has to be: the panel stamps a capture with it and the importer
     * sweeps against it. Two clocks that disagree by more than seven days make every injected
     * slip look like expired evidence, and the picture is gone before it can be looked at.
     */
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_755_000_000_000)
    }

    private val importer = SlipImporter(source, FakeSlipTextReader(), store, clock)

    private val viewModel = DebugSlipViewModel(source, importer, clock)

    @Test
    fun `a readable slip from an unknown shop is queued as a question rather than filed`() = runTest {
        viewModel.injectReadable()
        runCurrent()

        assertEquals(emptyList(), store.filed)
        val queued = store.queued.single()
        assertEquals("LAWSON108_P3793 THE LIVIN", queued.counterparty)
        assertEquals(ReviewReason.NewMerchant, queued.reason)
    }

    @Test
    fun `the same slip injected twice is queued once`() = runTest {
        viewModel.injectReadable()
        runCurrent()
        viewModel.injectReadable()
        runCurrent()

        // Two images, two ids, one payment. F3 hashes the bytes, so the second is recognised even
        // though nothing about the filename repeated — and the second run queues nothing new.
        assertEquals(1, store.queued.size)
        assertTrue("queued 0" in viewModel.summary.value, viewModel.summary.value)

        // Both pictures are still there on purpose: a queued slip keeps its evidence until the
        // reader has answered or the seven days run out, so every run re-reads and re-recognises
        // them. Reporting that as work done would be the journal disagreeing with the screen.
        assertTrue("already seen 2" in viewModel.summary.value, viewModel.summary.value)
    }

    @Test
    fun `a Thai-only slip is queued to be filled in by hand and keeps its picture`() =
        runTest {
            viewModel.injectUnreadable()
            runCurrent()

            assertEquals(emptyList(), store.filed)
            assertEquals(ReviewReason.Unreadable, store.queued.single().reason)
            assertTrue("queued 1" in viewModel.summary.value, viewModel.summary.value)
            assertEquals(1, source.candidates().size, "the picture went before it was checked")
        }

    // FR-11 — the payoff

    @Test
    fun `once the merchant is confirmed the next slip from that shop files itself`() = runTest {
        viewModel.injectReadable()
        runCurrent()
        confirmTheQueuedSlipAs("groceries")

        viewModel.injectSameMerchant()
        runCurrent()

        val filed = store.filed.single()
        assertEquals("LAWSON108_P3793 THE LIVIN", filed.read.counterparty)
        assertEquals("groceries", filed.categoryId)
        assertEquals(Satang.ofMinorUnits(15_100), filed.read.amount)
        assertEquals(emptyList(), store.queued, "the reader was asked twice about one shop")
    }

    @Test
    fun `a payment already confirmed is not filed again when its slip comes back`() = runTest {
        viewModel.injectReadable()
        runCurrent()
        confirmTheQueuedSlipAs("groceries")

        viewModel.injectReadable()
        runCurrent()

        // The rule now matches, so this reaches the ledger — and F3 tier two turns it away,
        // because it is the same reference, amount and instant already recorded.
        assertEquals(emptyList(), store.filed)
        assertTrue("already seen 1" in viewModel.summary.value, viewModel.summary.value)
    }

    private suspend fun confirmTheQueuedSlipAs(categoryId: String) {
        val queued = store.queued.single()
        importer.confirm(
            ConfirmedSlip(
                transactionId = "t1",
                ruleId = "r1",
                queuedSlipId = queued.id,
                counterparty = queued.counterparty,
                categoryId = categoryId,
                amount = Satang.ofMinorUnits(7_200),
                occurredAtEpochMillis = queued.occurredAtEpochMillis,
                reference = queued.reference,
                confirmedAtEpochMillis = 0L,
            ),
        )
    }
}

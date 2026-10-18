package com.ptut.expense.importer

import app.cash.turbine.test
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.QueuedSlipEntry
import com.ptut.expense.platform.photos.SlipCandidate
import com.ptut.expense.platform.photos.SlipSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SlipImporterTest {

    private val lawson = MerchantRule("LAWSON108_P3793 THE LIVIN", "groceries")
    private val now = Instant.fromEpochMilliseconds(1_755_000_000_000)

    private fun slip(
        merchant: String = "LAWSON108_P3793 THE LIVIN",
        reference: String = "016200000007AQR00007",
        amount: String = "72.00",
        at: String = "15 Aug 26  9:25 AM",
    ) = """
        Payment Completed
        $at
        MR. SOMCHAI J
        KBank
        xxx-x-xxxxx-x
        $merchant
        Transaction ID:
        $reference
        Amount:
        $amount Baht
        Fee:
        0.00 Baht
    """.trimIndent()

    private fun importer(
        source: FakeSlipSource,
        store: InMemoryImportStore,
        clock: Clock = FixedClock(now),
    ) = SlipImporter(
        source = source,
        reader = FakeSlipTextReader(),
        store = store,
        clock = clock,
        newId = { "id-${store.filed.size}-${store.runs.size}" },
    )

    private class FixedClock(private var at: Instant) : Clock {
        override fun now(): Instant = at

        fun advanceBy(millis: Long) {
            at = Instant.fromEpochMilliseconds(at.toEpochMilliseconds() + millis)
        }
    }

    // F3 tier one — file identity

    @Test
    fun `the same bytes seen twice give one row`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("a.jpg", slip(), capturedAt())

        importer(source, store).run()
        source.add("a.jpg", slip(), capturedAt())
        val second = importer(source, store).run()

        assertEquals(1, store.filed.size)
        assertEquals(1, second.alreadySeen)
        assertEquals(0, second.filed)
    }

    // F3 tier two — content identity

    @Test
    fun `a re-photograph gives one row and is never reported as imported`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("original.jpg", slip(), capturedAt())
        // New bytes, same payment: a trailing space is enough to change the hash.
        source.add("rephotographed.jpg", slip() + " ", capturedAt())

        val summary = importer(source, store).run()

        assertEquals(1, store.filed.size)
        assertEquals(1, summary.filed)
        assertEquals(1, summary.alreadySeen)
    }

    @Test
    fun `two payments sharing an amount and an hour still give two rows`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("one.jpg", slip(reference = "016200000007AQR00007"), capturedAt())
        source.add("two.jpg", slip(reference = "016200000011BQR00011"), capturedAt())

        val summary = importer(source, store).run()

        assertEquals(2, store.filed.size)
        assertEquals(2, summary.filed)
    }

    // Classification — FR-11, FR-12

    @Test
    fun `a merchant with no rule is asked about rather than filed`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore()
        source.add("a.jpg", slip(), capturedAt())

        val summary = importer(source, store).run()

        assertEquals(0, store.filed.size)
        assertEquals(1, summary.queued)
        assertEquals(ReviewReason.NewMerchant, store.queued.single().reason)
    }

    /** Home's Saved today block reads this. The date printed on the slip says nothing about it. */
    @Test
    fun `a filed slip carries the time of the run that filed it`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val clock = FixedClock(now).apply { advanceBy(1.minutes.inWholeMilliseconds) }
        source.add("a.jpg", slip(), capturedAt())

        importer(source, store, clock).run()

        assertEquals(
            now.toEpochMilliseconds() + 1.minutes.inWholeMilliseconds,
            store.filed.single().filedAtEpochMillis,
        )
    }

    @Test
    fun `a Thai-only merchant is queued as an unread slip at the photo time and keeps its picture`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("thai.jpg", SlipTranscripts.unreadable, capturedAt())

        val summary = importer(source, store).run()

        assertEquals(0, store.filed.size)
        val queued = store.queued.single()
        assertEquals(ReviewReason.Unreadable, queued.reason)
        assertEquals("", queued.counterparty)
        assertEquals(null, queued.amount)
        assertEquals(capturedAt(), queued.occurredAtEpochMillis)
        assertEquals(1, summary.queued)
        assertEquals(0, summary.unreadable)
        assertContentEquals(listOf("thai.jpg"), source.remaining.toList())
    }

    // NFR-4 — the picture

    @Test
    fun `a filed slip loses its picture`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("a.jpg", slip(), capturedAt())

        importer(source, store).run()

        assertTrue(source.remaining.isEmpty(), "the picture outlived the row it became")
    }

    @Test
    fun `a queued slip keeps its picture so the reader can still see it`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore()
        source.add("a.jpg", slip(), capturedAt())

        importer(source, store).run()

        assertContentEquals(listOf("a.jpg"), source.remaining.toList())
    }

    @Test
    fun `evidence older than seven days is swept and the entry stays`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore()
        val clock = FixedClock(now)
        source.add("old.jpg", slip(), capturedAt())

        importer(source, store, clock).run()
        assertContentEquals(listOf("old.jpg"), source.remaining.toList())

        clock.advanceBy(8.days.inWholeMilliseconds)
        importer(source, store, clock).sweep()

        assertTrue(source.remaining.isEmpty(), "the picture outlived its seven days")
        assertEquals(1, store.queued.size, "the entry went with the picture")
    }

    // FR-4 — neither platform may delete a picture K PLUS wrote, so a cold start lists a
    // confirmed slip again, and only its hash says the reader already answered it.

    @Test
    fun `a confirmed slip is not queued again when its picture survives`() = runTest {
        val source = FakeSlipSource().apply { refusesDeletes = true }
        val store = InMemoryImportStore()
        source.add("a.jpg", slip(amount = "1S5.00"), capturedAt())
        importer(source, store).run()

        importer(source, store).confirm(confirmation(of = store.queued.single(), amount = Satang(15_500)))
        val rescan = importer(source, store).run()

        assertTrue(store.queued.isEmpty(), "the slip the reader answered came back to the queue")
        assertEquals(1, rescan.alreadySeen)
    }

    @Test
    fun `a confirmed slip whose amount the reader corrected is not filed a second time`() = runTest {
        val source = FakeSlipSource().apply { refusesDeletes = true }
        val store = InMemoryImportStore()
        source.add("a.jpg", slip(amount = "15.50"), capturedAt())
        importer(source, store).run()

        importer(source, store).confirm(confirmation(of = store.queued.single(), amount = Satang(15_500)))
        importer(source, store).run()

        assertTrue(store.filed.isEmpty(), "the rule the reader made filed the same slip a second time")
    }

    // F5 / US-8 — the journal

    @Test
    fun `a run that finds nothing is a success because the pipe still works`() = runTest {
        val store = InMemoryImportStore()

        val summary = importer(FakeSlipSource(), store).run()

        assertEquals(ImportOutcome.Success, summary.outcome)
        assertEquals(0, summary.found)
        assertEquals(ImportOutcome.Success, store.runs.single().outcome)
    }

    @Test
    fun `a run that cannot scan is a failure and says why`() = runTest {
        val source = FakeSlipSource().apply { scanFails = true }
        val store = InMemoryImportStore()

        val summary = importer(source, store).run()

        assertEquals(ImportOutcome.Failure, summary.outcome)
        val run = store.runs.single()
        assertEquals(ImportOutcome.Failure, run.outcome)
        assertEquals("the folder could not be read", run.failureReason)
    }

    @Test
    fun `a finished run records when it ended and how much it saw`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("a.jpg", slip(), capturedAt())

        importer(source, store).run()

        val run = store.runs.single()
        assertEquals(now.toEpochMilliseconds(), run.finishedAtEpochMillis)
        assertEquals(1, run.slipsFound)
    }

    /**
     * Found on a device: sharing a slip that the folder scan had already queued left the shared
     * copy sitting in the inbox, to be OCR'd and rejected again on every run from then on.
     */
    @Test
    fun `a second picture of a slip already queued is not kept`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore()
        source.add("from-the-folder.png", slip(), capturedAt())
        importer(source, store).run()

        source.add("shared-by-hand.png", slip(), capturedAt())
        importer(source, store).run()

        assertEquals(1, store.queued.size)
        assertEquals(
            setOf("from-the-folder.png"),
            source.remaining,
            "the queue points at the first picture, so that one is evidence and the copy is not",
        )
    }

    /**
     * #17 gave Android a periodic job and a scan on open, and WorkManager runs them concurrently.
     * Two runs over one folder journal one piece of work twice and let the MediaStore scan marker
     * settle for a batch the other run has not finished — which loses the slips it never reached.
     */
    @Test
    fun `two runs cannot scan one folder at the same time`() = runTest {
        val source = ScanWatchingSource()
        val store = InMemoryImportStore()
        val importer = SlipImporter(source, FakeSlipTextReader(), store, FixedClock(now)) { "one" }

        listOf(async { importer.run() }, async { importer.run() }).awaitAll()

        assertEquals(1, source.mostScansAtOnce)
        assertEquals(2, store.runs.size, "each caller still gets its own journal entry")
    }

    private fun capturedAt(): Long = now.toEpochMilliseconds()

    /** What the confirm screen hands over for a queued slip, with the amount the reader typed. */
    private fun confirmation(of: QueuedSlipEntry, amount: Satang) = ConfirmedSlip(
        transactionId = "confirmed",
        ruleId = "rule",
        queuedSlipId = of.id,
        counterparty = of.counterparty,
        categoryId = "food",
        amount = amount,
        occurredAtEpochMillis = of.occurredAtEpochMillis,
        reference = of.reference,
        confirmedAtEpochMillis = capturedAt(),
        readAmount = of.amount,
    )

    /** Counts how many scans were inside the folder together, which is the only way to see a missing lock. */
    private class ScanWatchingSource : SlipSource {

        var mostScansAtOnce = 0
            private set

        private var scanning = 0

        override suspend fun candidates(): List<SlipCandidate> {
            scanning++
            mostScansAtOnce = maxOf(mostScansAtOnce, scanning)
            // Hands the dispatcher to the other run, which is what a missing lock would let in.
            yield()
            scanning--
            return emptyList()
        }

        override suspend fun bytesOf(id: String): ByteArray? = null

        override suspend fun delete(id: String) = Unit
    }

    /**
     * Page 05: the pulse "sits in the freshness chip while a scan runs", and "a still dot and a
     * scanning dot must not look the same — that ambiguity is the whole failure the spec is
     * written against". The chip can only tell them apart if the importer says which it is, so
     * the running state is published rather than left inside the mutex that already knows it.
     */
    @Test
    fun `the importer says out loud while it is scanning`() = runTest {
        val source = FakeSlipSource().apply { add("a", slip(), now.toEpochMilliseconds()) }
        val importer = importer(source, InMemoryImportStore())

        importer.isScanning.test {
            assertEquals(false, awaitItem(), "the importer claims to be scanning at rest")

            val run = async { importer.run() }
            assertEquals(true, awaitItem(), "the importer went quiet while it was working")

            run.await()
            assertEquals(false, awaitItem(), "the importer never stopped scanning")
        }
    }
}

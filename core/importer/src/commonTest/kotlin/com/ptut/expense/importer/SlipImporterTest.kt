package com.ptut.expense.importer

import app.cash.turbine.test
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportFailureKind
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.play.DepositSource
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.Goal
import com.ptut.expense.domain.play.JarInbox
import com.ptut.expense.domain.play.PlayBookJarInbox
import com.ptut.expense.domain.play.SeenAccount
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.QueuedSlipEntry
import com.ptut.expense.domain.slip.SlipOwner
import com.ptut.expense.domain.slip.SlipParser
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.SlipCandidate
import com.ptut.expense.platform.photos.SlipSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SlipImporterTest {

    private companion object {
        /** Split so no fixture line reads as an account number (NFR-5). */
        const val SAVINGS_LAST_FOUR = "48" + "21"
        const val OTHER_LAST_FOUR = "09" + "17"

        fun maskedAccount(lastFour: String) = "xxx-x-x" + lastFour + "-x"
    }

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
        source: SlipSource,
        store: InMemoryImportStore,
        clock: Clock = FixedClock(now),
        owner: SlipOwnerBook = SlipOwnerBook.Unknown,
        jar: JarInbox = JarInbox.None,
    ) = SlipImporter(
        source = source,
        reader = FakeSlipTextReader(),
        store = store,
        clock = clock,
        owner = owner,
        jar = jar,
        // The test's own dispatcher, so the scanning flag and the journal are read in step.
        work = EmptyCoroutineContext,
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
    fun `a slip with no readable shop is queued as an unread slip at the photo time and keeps its picture`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        source.add("glare.jpg", SlipTranscripts.unreadable, capturedAt())

        val summary = importer(source, store).run()

        assertEquals(0, store.filed.size)
        val queued = store.queued.single()
        assertEquals(ReviewReason.Unreadable, queued.reason)
        assertEquals("", queued.counterparty)
        assertEquals(null, queued.amount)
        assertEquals(capturedAt(), queued.occurredAtEpochMillis)
        assertEquals(1, summary.queued)
        assertEquals(0, summary.unreadable)
        assertContentEquals(listOf("glare.jpg"), source.remaining.toList())
    }

    // #26 — money in

    @Test
    fun `your name is learned only from slips in the bank app's own folder`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val owner = OwnerInMemory()
        source.add("shot.jpg", slip(reference = "016200000001AQR00001"), capturedAt(), folderName = "Screenshots")
        source.add("a.jpg", slip(reference = "016200000002AQR00002"), capturedAt(), folderName = "K PLUS")
        importer(source, store, owner = owner).run()
        assertNull(owner.owner.name)

        source.add("b.jpg", slip(reference = "016200000003AQR00003"), capturedAt(), folderName = "K PLUS")
        importer(source, store, owner = owner).run()

        assertEquals("MR. SOMCHAI J", owner.owner.name)
    }

    @Test
    fun `a slip that names you as recipient waits in To check as money in and then files itself`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val owner = OwnerInMemory(SlipOwner(name = "MR. SOMCHAI J"))
        source.add("in1.jpg", received("MS. ANONG W", "MR. SOMCHAI JAIDEE", "016200000010APP00010"), capturedAt())
        importer(source, store, owner = owner).run()

        val asked = store.queued.single()
        assertEquals(Direction.Income, asked.direction)
        assertEquals("MS. ANONG W", asked.counterparty)
        assertEquals(ReviewReason.NewMerchant, asked.reason)

        importer(source, store, owner = owner).confirm(
            confirmation(asked, Satang.ofMinorUnits(50_000)).copy(counterparty = "MS. ANONG W", categoryId = "income", direction = Direction.Income),
        )
        source.add("in2.jpg", received("MS. ANONG W", "MR. SOMCHAI JAIDEE", "016200000011APP00011"), capturedAt())
        importer(source, store, owner = owner).run()

        val filed = store.filed.single()
        assertEquals(Direction.Income, filed.direction)
        assertEquals("income", filed.categoryId)
    }

    @Test
    fun `a transfer to the savings account of the jar fills the jar and never reaches the ledger`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val owner = OwnerInMemory(SlipOwner(name = "MR. SOMCHAI J"))
        val goal = Goal("jar", "Trip", Satang.ofMinorUnits(1_200_000), "THB", LocalDate(2026, 8, 1), createdAtEpochMillis = 0, savingsAccount = "••4821")
        val book = FakePlayBook(goals = listOf(goal))
        source.add("save.jpg", received("MR. SOMCHAI J", "MR. SOMCHAI JAIDEE", "016200000012TRF00012", toAccount = maskedAccount(SAVINGS_LAST_FOUR)), capturedAt())

        val summary = importer(source, store, owner = owner, jar = PlayBookJarInbox(book)).run()

        val deposit = book.deposits.value.single()
        assertEquals(Satang.ofMinorUnits(50_000), deposit.amount)
        assertEquals(DepositSource.Slip, deposit.source)
        assertEquals("••4821", deposit.account)
        assertEquals(1, summary.filed)
        assertTrue(store.filed.isEmpty())
        assertTrue(store.queued.isEmpty())
        assertEquals("slip:016200000012TRF00012", deposit.id)
        assertEquals(SeenAccount("••4821", "MR. SOMCHAI JAIDEE", 1, now.toEpochMilliseconds()), book.seenAccounts.value.single())
        assertNull(source.bytesOf("save.jpg"))
    }

    @Test
    fun `before your name is learned a transfer from you to you still fills the jar`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val goal = Goal("jar", "Trip", Satang.ofMinorUnits(1_200_000), "THB", LocalDate(2026, 8, 1), createdAtEpochMillis = 0, savingsAccount = "••4821")
        val book = FakePlayBook(goals = listOf(goal))
        source.add("save.jpg", received("MR. SOMCHAI J", "MR. SOMCHAI JAIDEE", "016200000016TRF00016", toAccount = maskedAccount(SAVINGS_LAST_FOUR)), capturedAt())

        importer(source, store, jar = PlayBookJarInbox(book)).run()

        assertEquals(1, book.deposits.value.size)
        assertEquals("••4821", book.seenAccounts.value.single().mask)
    }

    @Test
    fun `a second picture of one transfer fills the jar once`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val owner = OwnerInMemory(SlipOwner(name = "MR. SOMCHAI J"))
        val goal = Goal("jar", "Trip", Satang.ofMinorUnits(1_200_000), "THB", LocalDate(2026, 8, 1), createdAtEpochMillis = 0, savingsAccount = "••4821")
        val book = FakePlayBook(goals = listOf(goal))
        val transfer = received("MR. SOMCHAI J", "MR. SOMCHAI JAIDEE", "016200000014TRF00014", toAccount = maskedAccount(SAVINGS_LAST_FOUR))
        source.add("save.jpg", transfer, capturedAt())
        source.add("save-again.jpg", transfer + "\nVerify Slip", capturedAt())

        importer(source, store, owner = owner, jar = PlayBookJarInbox(book)).run()

        assertEquals(1, book.deposits.value.size)
        assertTrue(store.filed.isEmpty())
    }

    @Test
    fun `paying a shop whose account ends in the same four digits stays spending`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson, MerchantRule("FOODLAND RAMA IV", "groceries")))
        val owner = OwnerInMemory(SlipOwner(name = "MR. SOMCHAI J"))
        val goal = Goal("jar", "Trip", Satang.ofMinorUnits(1_200_000), "THB", LocalDate(2026, 8, 1), createdAtEpochMillis = 0, savingsAccount = "••4821")
        val book = FakePlayBook(goals = listOf(goal))
        source.add("shop.jpg", received("MR. SOMCHAI J", "FOODLAND RAMA IV", "016200000015TRF00015", toAccount = maskedAccount(SAVINGS_LAST_FOUR)), capturedAt())

        importer(source, store, owner = owner, jar = PlayBookJarInbox(book)).run()

        assertTrue(book.deposits.value.isEmpty())
        assertTrue(book.seenAccounts.value.isEmpty())
        assertEquals("groceries", store.filed.single().categoryId)
    }

    @Test
    fun `a transfer to your own account that is not the jar one is noted and still asked about`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson))
        val owner = OwnerInMemory(SlipOwner(name = "MR. SOMCHAI J"))
        val book = FakePlayBook()
        source.add("save.jpg", received("MR. SOMCHAI J", "MR. SOMCHAI JAIDEE", "016200000013TRF00013", toAccount = maskedAccount(OTHER_LAST_FOUR)), capturedAt())

        importer(source, store, owner = owner, jar = PlayBookJarInbox(book)).run()

        assertEquals("••0917", book.seenAccounts.value.single().mask)
        assertTrue(book.deposits.value.isEmpty())
        assertEquals(1, store.queued.size)
    }

    @Test
    fun `paying someone who has paid you still asks under money out`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore(listOf(lawson, MerchantRule("MS. ANONG W", "income", direction = Direction.Income)))
        val owner = OwnerInMemory(SlipOwner(name = "MR. SOMCHAI J"))
        source.add("out.jpg", slip(merchant = "MS. ANONG W", reference = "016200000012AQR00012"), capturedAt())

        importer(source, store, owner = owner).run()

        assertEquals(0, store.filed.size)
        assertEquals(Direction.Expense, store.queued.single().direction)
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
        assertEquals(ImportFailureKind.Other, run.failureKind)
    }

    @Test
    fun `a scan the photo source refuses journals its kind beside the sentence`() = runTest {
        ImportFailureKind.entries.forEach { kind ->
            val store = InMemoryImportStore()
            val refusing = object : SlipSource by FakeSlipSource() {
                override suspend fun candidates(): List<SlipCandidate> = throw ScanStopped(kind, "stopped")
            }

            importer(refusing, store).run()

            val run = store.runs.single()
            assertEquals(kind, run.failureKind)
            assertEquals("stopped", run.failureReason)
        }
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
        val importer = SlipImporter(source, FakeSlipTextReader(), store, FixedClock(now), work = EmptyCoroutineContext) { "one" }

        listOf(async { importer.run() }, async { importer.run() }).awaitAll()

        assertEquals(1, source.mostScansAtOnce)
        assertEquals(2, store.runs.size, "each caller still gets its own journal entry")
    }

    /** Check now starts a run from a screen, so hashing and parsing 200 slips must not hold the main thread. */
    @Test
    fun `a run does its work off the caller dispatcher`() = runTest {
        val source = FakeSlipSource().apply { add("a.jpg", slip(), capturedAt()) }
        val reader = DispatcherWatchingReader()

        SlipImporter(source, reader, InMemoryImportStore(listOf(lawson))).run()

        assertEquals(Dispatchers.Default, reader.readOn)
    }

    @Test
    fun `a slip the parser throws on is set aside and the run goes on`() = runTest {
        val source = FakeSlipSource().apply {
            add("bad.jpg", slip(reference = "BAD1"), capturedAt())
            add("good.jpg", slip(), capturedAt() + 1)
        }
        val store = InMemoryImportStore(listOf(lawson))
        val throwing = SlipImporter(
            source,
            FakeSlipTextReader(),
            store,
            FixedClock(now),
            work = EmptyCoroutineContext,
            parse = { lines, wallets -> if ("BAD1" in lines) error("a parser bug") else SlipParser.parse(lines, wallets) },
        ) { "one" }

        val summary = throwing.run()

        assertEquals(1, summary.filed)
        assertEquals(1, summary.unreadable)
        assertTrue("bad.jpg" in source.remaining, "the picture waits for the next run")
    }

    /** A stopped worker is not an unreadable slip: the picture must not be marked to wait for a retry. */
    @Test
    fun `a reader stopped mid slip stops the run instead of setting the slip aside`() = runTest {
        val source = FakeSlipSource().apply { add("a.jpg", slip(), capturedAt()) }
        val store = InMemoryImportStore(listOf(lawson))
        val stopped = SlipImporter(source, StoppedReader(), store, FixedClock(now), work = EmptyCoroutineContext) { "one" }

        assertFailsWith<CancellationException> { stopped.run() }

        assertEquals(ImportOutcome.Running, store.runs.single().outcome, "a stop is not journaled as a failed run")
        assertFalse(stopped.isScanning.value)
    }

    @Test
    fun `a scan stopped before it lists is not journaled as a failure`() = runTest {
        val store = InMemoryImportStore()
        val stopped = SlipImporter(StoppedSource(), FakeSlipTextReader(), store, FixedClock(now), work = EmptyCoroutineContext) { "one" }

        assertFailsWith<CancellationException> { stopped.run() }

        assertEquals(ImportOutcome.Running, store.runs.single().outcome)
    }

    private class DispatcherWatchingReader : SlipTextReader {
        var readOn: ContinuationInterceptor? = null
            private set

        override suspend fun read(bytes: ByteArray): List<String> {
            readOn = currentCoroutineContext()[ContinuationInterceptor]
            return FakeSlipTextReader().read(bytes)
        }
    }

    private class StoppedReader : SlipTextReader {
        override suspend fun read(bytes: ByteArray): List<String> = throw CancellationException("worker stopped")
    }

    private class StoppedSource : SlipSource {
        override suspend fun candidates(): List<SlipCandidate> = throw CancellationException("worker stopped")

        override suspend fun bytesOf(id: String): ByteArray? = null

        override suspend fun delete(id: String) = Unit
    }

    /** #26: a slip someone else's K PLUS saved, sent to you as a picture. */
    private fun received(from: String, to: String, reference: String, amount: String = "500.00", toAccount: String = "xxx-x-xxxxx-x") = """
        Transfer Completed
        15 Aug 26  9:25 AM
        $from
        KBank
        xxx-x-xxxxx-x
        $to
        KBank
        $toAccount
        Transaction ID:
        $reference
        Amount:
        $amount Baht
        Fee:
        0.00 Baht
    """.trimIndent()

    /** #26: the learned name, held the way the app's settings hold it. */
    private class OwnerInMemory(var owner: SlipOwner = SlipOwner()) : SlipOwnerBook {
        override fun name(): String? = owner.name

        override fun learnFrom(sender: String) {
            owner = owner.learnFrom(sender)
        }
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

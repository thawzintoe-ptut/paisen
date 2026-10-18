package com.ptut.expense.importer

import com.ptut.expense.domain.EvidenceRetention
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.folders.FolderReadTally
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.rules.RuleEngine
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.FiledSlip
import com.ptut.expense.domain.slip.ImportStore
import com.ptut.expense.domain.slip.QueuedSlipEntry
import com.ptut.expense.domain.slip.SlipParser
import com.ptut.expense.domain.slip.SlipRead
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.SlipCandidate
import com.ptut.expense.platform.photos.SlipSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Enough for one run's pictures to wait for a slow listener. */
private const val LANDINGS_BUFFER = 64

/** What one run did. Every count is a different story the banner and Activity can tell. */
data class ImportSummary(
    val outcome: ImportOutcome,
    val found: Int,
    val filed: Int,
    val queued: Int,
    val alreadySeen: Int,
    /** Images that read as no slip at all, and were skipped. An unread slip is in [queued] (#113). */
    val unreadable: Int,
)

/** Where one picture went on a run (#219), so a slip shared in by hand can say so. */
enum class SlipPlace { Filed, ToCheck, AlreadyRecorded, NotASlip }

data class SlipLanding(
    val candidateId: String,
    val place: SlipPlace,
    /** The slip's own time, for [SlipPlace.Filed] only. */
    val occurredAtEpochMillis: Long? = null,
)

/**
 * The whole slip pipeline: enumerate, hash, dedupe, read, parse, classify, commit or queue,
 * journal, sweep. No screen touches it, and both platform seams are interfaces, so every rule
 * below runs in the host suite.
 *
 * Dedupe is two tiers, and they answer different questions. The hash asks "have I seen this
 * file?" and catches a rescan of the same folder. The content key — reference, amount, instant —
 * asks "have I seen this payment?" and catches a re-photograph, where the bytes are new but the
 * slip is not. Neither alone is enough: two slips can share a merchant, an amount and an hour and
 * still be two payments, which is why the reference is in the key and the merchant is not.
 */
@OptIn(ExperimentalUuidApi::class)
class SlipImporter(
    private val source: SlipSource,
    private val reader: SlipTextReader,
    private val store: ImportStore,
    private val clock: Clock = Clock.System,
    /**
     * FR-5's counter. Defaulted away because most of this class has nothing to do with folders,
     * and a run over the share inbox alone has nothing to report to one.
     */
    private val folders: FolderReadTally = FolderReadTally { _, _ -> },
    /**
     * NFR-4's window, asked for rather than held. The reader can change it, this class is a
     * `single`, and a value read once at construction would keep sweeping on the old schedule
     * until the process restarted. Defaulted so a test that is not about retention says nothing.
     */
    private val retention: () -> Duration = { EvidenceRetention },
    /**
     * Injected so a test can name the row it wrote. Random everywhere else, and last so the tests
     * that name one keep reading it as a trailing lambda.
     */
    private val newId: () -> String = { Uuid.random().toString() },
) {

    /**
     * #17 gave Android two triggers — a periodic job, and a scan when the app opens or a slip is
     * shared — and WorkManager will run them at the same time. Two runs over one folder journal
     * two runs for one piece of work, and the folder scan's own marker would settle for a batch
     * the other run had not finished, which loses whatever it had not reached. The second caller
     * waits instead, and then finds a folder the first one has already emptied.
     */
    private val scanning = Mutex()

    private val running = MutableStateFlow(false)

    /**
     * Whether a scan is happening right now, so the freshness chip can say so.
     *
     * Page 05 makes this a correctness problem rather than a flourish: "A still dot and a scanning
     * dot must not look the same — that ambiguity is the whole failure the spec is written
     * against." The mutex above already knows, and knowing it privately is what left the chip
     * unable to tell a working importer from a stopped one.
     *
     * It stays true across a queued second caller, because from the reader's side one scan waiting
     * on another is still a scan running.
     */
    val isScanning: StateFlow<Boolean> = running.asStateFlow()

    // Dropped when nobody listens: only a share that is waiting for its own slip reads this.
    private val landed = MutableSharedFlow<SlipLanding>(
        extraBufferCapacity = LANDINGS_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Each picture's result as the run reaches it. A picture that vanished mid-run has none. */
    val landings: SharedFlow<SlipLanding> = landed.asSharedFlow()

    suspend fun run(): ImportSummary = scanning.withLock {
        running.value = true
        try {
            scanAndImport()
        } finally {
            running.value = false
        }
    }

    private suspend fun scanAndImport(): ImportSummary {
        val run = ImportRun(
            id = newId(),
            startedAtEpochMillis = now(),
            finishedAtEpochMillis = null,
            outcome = ImportOutcome.Running,
            slipsFound = 0,
            failureReason = null,
        )
        store.startRun(run)

        val candidates = try {
            source.candidates()
        } catch (failure: Exception) {
            // US-8: a run that could not scan is a failure. A run that scanned and found nothing
            // is not — collapsing the two is how an import breaks while the banner stays green.
            return run.failed(failure.message ?: failure::class.simpleName.orEmpty())
        }

        val rules = store.rules()
        var tally = Tally()
        val readPerFolder = mutableMapOf<String, Int>()
        // The folders this run's rows came from, for Activity's line (#170).
        val folderNames = LinkedHashSet<String>()
        for (candidate in candidates) {
            val outcome = importOne(candidate, rules)
            tally = tally.plus(outcome)
            if (outcome.isAReading && candidate.folderId != null) {
                readPerFolder[candidate.folderId] = (readPerFolder[candidate.folderId] ?: 0) + 1
            }
            if (outcome.wroteARow) candidate.folderName?.let(folderNames::add)
        }
        // Once per folder rather than once per slip: a run over a folder of three hundred is one
        // write, and the counter the badge reads never sees a half-finished run.
        readPerFolder.forEach { (id, read) -> folders.addSlipsRead(id, read) }

        store.finishRun(
            run.copy(
                finishedAtEpochMillis = now(),
                outcome = ImportOutcome.Success,
                slipsFound = candidates.size,
                filed = tally.filed,
                queued = tally.queued,
                folders = folderNames.toList(),
            ),
        )
        sweep()
        return tally.summarise(ImportOutcome.Success, candidates.size)
    }

    /**
     * FR-11: the reader answered the question, so the slip is filed and the merchant remembered.
     * The picture goes with it — a confirmed slip is evidence for a row that now exists.
     *
     * The writes commit before the delete, for the same reason they do when the importer files a
     * slip on its own: a delete that succeeds ahead of a commit that fails loses the evidence.
     */
    suspend fun confirm(slip: ConfirmedSlip) {
        store.confirm(slip)
        source.delete(slip.queuedSlipId)
    }

    /**
     * FR-8: the reader looked at a queued slip and said no. The row goes and so does the picture.
     *
     * It lives here rather than on the ledger because this class owns a slip picture's whole life:
     * it is the only thing that deletes one, whether the slip was filed, swept or refused. Two
     * owners of one delete is how an orphaned image outlives the row that explained it.
     */
    suspend fun discard(id: String) {
        store.discard(id)
        source.delete(id)
    }

    /**
     * NFR-4: a failed slip keeps its picture for as long as the reader chose, and loses it after.
     * Runs on its own as well as after a run, because an app that has not imported for a month
     * must still let go of the evidence.
     *
     * A shortened window takes effect here and nowhere else. This is the only thing in the app
     * that deletes a slip picture, so a screen that shortened the window by deleting as well is
     * how an orphaned image outlives the row that explained it.
     */
    suspend fun sweep() {
        val cutoff = now() - retention().inWholeMilliseconds
        store.expireEvidenceOlderThan(cutoff).forEach { source.delete(it) }
    }

    private suspend fun importOne(candidate: SlipCandidate, rules: List<MerchantRule>): Outcome {
        val bytes = source.bytesOf(candidate.id) ?: return Outcome.Gone
        val hash = bytes.toByteString().sha256().hex()

        if (store.isImported(hash)) {
            // Seen before, so the picture has no job left. Deleting one already gone is not an error.
            source.delete(candidate.id)
            land(candidate, SlipPlace.AlreadyRecorded)
            return Outcome.AlreadySeen
        }

        val lines = try {
            reader.read(bytes)
        } catch (failure: Exception) {
            // One unreadable slip does not fail the run. The picture stays for the retry.
            return Outcome.Unreadable
        }

        return when (val read = SlipParser.parse(lines)) {
            is SlipRead.Complete -> file(candidate, hash, read, rules)

            // FR-7: the amount did not read. It is queued with that field still empty.
            is SlipRead.Partial -> queue(candidate, hash, read, ReviewReason.AmountUnclear)

            // #113: a slip that read no shop becomes a hand entry. An image with no slip labels is not a slip.
            SlipRead.Unreadable -> if (SlipParser.hasSlipLabels(lines)) {
                queueUnread(candidate, hash)
            } else {
                land(candidate, SlipPlace.NotASlip)
                Outcome.Unreadable
            }
        }
    }

    private fun land(candidate: SlipCandidate, place: SlipPlace, occurredAtEpochMillis: Long? = null) {
        landed.tryEmit(SlipLanding(candidate.id, place, occurredAtEpochMillis))
    }

    private suspend fun file(
        candidate: SlipCandidate,
        hash: String,
        read: SlipRead.Complete,
        rules: List<MerchantRule>,
    ): Outcome {
        val rule = RuleEngine.ruleFor(read.counterparty, rules)
            // FR-11: the first slip from a merchant is a question, not a guess. Every one after
            // it is filed without asking, because by then the reader has answered.
            ?: return queue(candidate, hash, read, ReviewReason.NewMerchant)

        val written = store.file(
            FiledSlip(
                newId(),
                hash,
                read,
                rule.categoryId,
                filedAtEpochMillis = now(),
                folder = candidate.folderName,
                ruleId = rule.id.ifEmpty { null },
            ),
        )

        // After the write, never before: if this delete fails the picture lingers and the next
        // run recognises it by hash. Deleting first and then failing to commit would lose it.
        source.delete(candidate.id)

        land(candidate, if (written) SlipPlace.Filed else SlipPlace.AlreadyRecorded, read.occurredAtEpochMillis)
        return if (written) Outcome.Filed else Outcome.AlreadySeen
    }

    private suspend fun queue(
        candidate: SlipCandidate,
        hash: String,
        read: SlipRead.Readable,
        reason: ReviewReason,
    ): Outcome = enqueue(
        candidate,
        QueuedSlipEntry(
            id = candidate.id,
            hash = hash,
            counterparty = read.counterparty,
            amount = read.amount,
            // A slip whose date did not read still happened. The photo's own timestamp is
            // the closest honest stand-in, and it keeps the row in the right day bucket.
            occurredAtEpochMillis = read.occurredAtEpochMillis ?: candidate.capturedAtEpochMillis,
            reference = read.reference,
            capturedAtEpochMillis = candidate.capturedAtEpochMillis,
            reason = reason,
            lowConfidence = read.lowConfidence,
            folder = candidate.folderName,
        ),
    )

    /** No shop, no amount, no reference, and the photo's own time: Check slip fills in the rest. */
    private suspend fun queueUnread(candidate: SlipCandidate, hash: String): Outcome = enqueue(
        candidate,
        QueuedSlipEntry(
            id = candidate.id,
            hash = hash,
            counterparty = "",
            amount = null,
            occurredAtEpochMillis = candidate.capturedAtEpochMillis,
            reference = null,
            capturedAtEpochMillis = candidate.capturedAtEpochMillis,
            reason = ReviewReason.Unreadable,
            folder = candidate.folderName,
        ),
    )

    private suspend fun enqueue(candidate: SlipCandidate, entry: QueuedSlipEntry): Outcome {
        val added = store.queue(entry)
        // New to the queue or already in it, the slip is waiting in To check either way.
        land(candidate, SlipPlace.ToCheck)
        if (added) {
            return if (entry.reason == ReviewReason.Unreadable) Outcome.QueuedUnread else Outcome.Queued
        }

        // The queue already holds this slip. Under this same picture, it is the evidence the
        // review screen shows and it stays until the reader answers or the seven days run out.
        // Under a different one — shared by hand after the scan had found it — this copy has no
        // job left, and the share inbox has no marker to step over it, so leaving it would cost
        // an OCR pass every run for as long as the app is installed.
        if (store.queuedPictureFor(entry.hash) != candidate.id) source.delete(candidate.id)

        // Either way it is not work this run did, and counting it would say otherwise.
        return Outcome.AlreadySeen
    }

    private suspend fun ImportRun.failed(reason: String): ImportSummary {
        store.finishRun(
            copy(
                finishedAtEpochMillis = now(),
                outcome = ImportOutcome.Failure,
                failureReason = reason,
            ),
        )
        return ImportSummary(ImportOutcome.Failure, 0, 0, 0, 0, 0)
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private enum class Outcome {
        Filed,
        Queued,
        QueuedUnread,
        AlreadySeen,
        Unreadable,
        Gone,
        ;

        /**
         * What the "Slips found" badge promises: the reader got a reading out of this picture, on
         * this run. [AlreadySeen] was read on an earlier one and is already in the count, so
         * counting it again would inflate every folder on every rescan.
         */
        val isAReading: Boolean get() = this == Filed || this == Queued

        /** Activity names the folder of every row the run wrote, read or not (#170, #113). */
        val wroteARow: Boolean get() = isAReading || this == QueuedUnread
    }

    private data class Tally(
        val filed: Int = 0,
        val queued: Int = 0,
        val alreadySeen: Int = 0,
        val unreadable: Int = 0,
    ) {
        fun plus(outcome: Outcome): Tally = when (outcome) {
            Outcome.Filed -> copy(filed = filed + 1)
            Outcome.Queued, Outcome.QueuedUnread -> copy(queued = queued + 1)
            Outcome.AlreadySeen -> copy(alreadySeen = alreadySeen + 1)
            Outcome.Unreadable -> copy(unreadable = unreadable + 1)
            Outcome.Gone -> this
        }

        fun summarise(outcome: ImportOutcome, found: Int) =
            ImportSummary(outcome, found, filed, queued, alreadySeen, unreadable)
    }
}

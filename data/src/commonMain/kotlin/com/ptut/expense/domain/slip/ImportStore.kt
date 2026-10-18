package com.ptut.expense.domain.slip

import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.rules.MerchantRule

/** A slip good enough to file without asking: read in full, and its merchant already remembered. */
data class FiledSlip(
    val id: String,
    val hash: String,
    val read: SlipRead.Complete,
    val categoryId: String,
    /** When the run filed it. Home's Saved today block reads this, not the slip's own date. */
    val filedAtEpochMillis: Long,
    /** The watched folder it came from (#170). */
    val folder: String? = null,
    /** The rule that filed it, for the record's Why card (#117). */
    val ruleId: String? = null,
)

/**
 * A slip that needs a human. [amount] stays null when the reader could not make it out — FR-7,
 * a pre-filled figure invites a careless confirm.
 */
data class QueuedSlipEntry(
    val id: String,
    val hash: String,
    val counterparty: String,
    val amount: Satang?,
    val occurredAtEpochMillis: Long,
    val reference: String?,
    val capturedAtEpochMillis: Long,
    val reason: ReviewReason,
    /** Carried through so the confirm screen can mark them; see [SlipRead.Readable.lowConfidence]. */
    val lowConfidence: Set<SlipField> = emptySet(),
    /** The watched folder it came from (#170). */
    val folder: String? = null,
)

/**
 * FR-11 and FR-13 together: the row the reader confirmed, the rule that will file the next slip
 * from this merchant, and the queue entry that goes away because the question was answered.
 */
data class ConfirmedSlip(
    val transactionId: String,
    val ruleId: String,
    val queuedSlipId: String,
    /** Null when the slip read no shop (#113): the row is a hand entry and no rule is written. */
    val counterparty: String?,
    val categoryId: String,
    val amount: Satang,
    val occurredAtEpochMillis: Long,
    val reference: String?,
    val confirmedAtEpochMillis: Long,
    /** Optional, and not part of the rule: a rule files the parent only (R5 in #147). */
    val subCategoryId: String? = null,
    /**
     * What the parser made of the amount, which is not always [amount] — the confirm screen exists
     * so a reader can correct a misread figure. Null when nothing read, and kept apart from
     * [amount] so screen 11c can still show what the slip said after a second correction.
     */
    val readAmount: Satang? = null,
    /** The watched folder the queued slip came from (#170). */
    val folder: String? = null,
    /** The reader's name for a shop read as noise, kept on the rule; [counterparty] stays the key. */
    val displayName: String? = null,
)

/**
 * Everything the importer persists. Room sits behind this, so the importer never sees a DAO and
 * every dedupe and retention rule can be driven from the host suite.
 */
interface ImportStore {

    /** Read once per run rather than held open: a rule added mid-run belongs to the next one. */
    suspend fun rules(): List<MerchantRule>

    /** F3 tier one, file identity. */
    suspend fun isImported(hash: String): Boolean

    /**
     * Writes the transaction and records the hash, together.
     *
     * Returns false when the F3 content key already held this reference, amount and instant — a
     * re-photograph of a slip already filed. The hash is recorded either way, so the second
     * picture is recognised by its bytes next time and never read again.
     */
    suspend fun file(slip: FiledSlip): Boolean

    /**
     * Returns false when this hash was already queued, so a rescan cannot queue one picture twice
     * — and cannot report having done so either.
     */
    suspend fun queue(slip: QueuedSlipEntry): Boolean

    /**
     * The picture the queue holds this slip under, or null when it holds no such slip.
     *
     * Asked only after [queue] has refused, to tell two things apart that both mean "already
     * queued": a rescan of the picture the queue points at, which is the evidence the review
     * screen shows and has to stay, and a second copy of the same slip — shared by hand after the
     * folder scan found it — which has no evidence to be.
     */
    suspend fun queuedPictureFor(hash: String): String?

    /**
     * FR-11, FR-13. All three writes land together or none does: a rule for a slip that was never
     * filed, or a queue entry for money already in the ledger, are both states the reader has no
     * way to tell apart from the outside.
     *
     * FR-4: the picture's hash is recorded with them. The picture usually survives a confirm, so
     * without the hash the next scan would queue or file the same slip again.
     */
    suspend fun confirm(slip: ConfirmedSlip)

    /**
     * FR-8. The file hash is not kept: discarding says this picture is not worth filing, and a
     * reader who photographs the same slip again is asking for a second opinion, not a repeat of
     * the first one.
     */
    suspend fun discard(id: String)

    suspend fun startRun(run: ImportRun)

    suspend fun finishRun(run: ImportRun)

    /**
     * NFR-4. Marks every queued slip captured before [cutoffEpochMillis] as having lost its
     * evidence and returns their ids, so the caller can delete the pictures. The rows stay: a
     * known spend that lost its evidence still belongs on the review queue.
     */
    suspend fun expireEvidenceOlderThan(cutoffEpochMillis: Long): List<String>
}

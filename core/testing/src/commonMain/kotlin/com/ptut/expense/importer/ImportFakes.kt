package com.ptut.expense.importer

import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.FiledSlip
import com.ptut.expense.domain.slip.ImportStore
import com.ptut.expense.domain.slip.QueuedSlipEntry
import com.ptut.expense.platform.photos.SlipCandidate
import com.ptut.expense.platform.photos.SlipSource

/** A folder of slip images, where an image is its transcript. */
class FakeSlipSource(
    images: Map<String, Pair<String, Long>> = emptyMap(),
) : SlipSource {

    private val images = images.toMutableMap()

    /** Which watched folder listed a picture, for the ids a test says came from one. */
    private val folders = mutableMapOf<String, String>()

    /** So a test can say the picture went, which is the whole of NFR-4's first half. */
    val remaining: Set<String> get() = images.keys

    var scanFails: Boolean = false

    /** A K PLUS folder as both platforms see it: another app wrote the pictures, so none can go. */
    var refusesDeletes: Boolean = false

    fun add(id: String, transcript: String, capturedAtEpochMillis: Long, folderId: String? = null) {
        images[id] = transcript to capturedAtEpochMillis
        folderId?.let { folders[id] = it }
    }

    override suspend fun candidates(): List<SlipCandidate> {
        if (scanFails) throw IllegalStateException("the folder could not be read")
        return images.entries
            .map { (id, image) -> SlipCandidate(id, image.second, folders[id]) }
            .sortedBy { it.capturedAtEpochMillis }
    }

    override suspend fun bytesOf(id: String): ByteArray? = images[id]?.first?.encodeToByteArray()

    override suspend fun delete(id: String) {
        if (!refusesDeletes) images.remove(id)
    }
}

/**
 * The store, in memory, enforcing the same two keys SQLite does: the unique hash on
 * `imported_slip` and `queued_slip`, and the F3 content index on `transactions`.
 */
class InMemoryImportStore(rules: List<MerchantRule> = emptyList()) : ImportStore {

    private val rules = rules.toMutableList()

    val filed = mutableListOf<FiledSlip>()
    val queued = mutableListOf<QueuedSlipEntry>()
    val runs = mutableListOf<ImportRun>()

    private val importedHashes = mutableSetOf<String>()
    private val contentKeys = mutableSetOf<Triple<String, Long, Long>>()

    override suspend fun rules(): List<MerchantRule> = rules

    override suspend fun isImported(hash: String): Boolean = hash in importedHashes

    override suspend fun file(slip: FiledSlip): Boolean {
        // Recorded either way: the second picture of a filed slip is recognised by its bytes
        // next time, so it is never read again.
        importedHashes += slip.hash

        val key = Triple(
            slip.read.reference,
            slip.read.amount.toMinorUnits(),
            slip.read.occurredAtEpochMillis,
        )
        if (!contentKeys.add(key)) return false

        filed += slip
        return true
    }

    override suspend fun queue(slip: QueuedSlipEntry): Boolean {
        if (queued.any { it.hash == slip.hash }) return false
        queued += slip
        return true
    }

    override suspend fun queuedPictureFor(hash: String): String? =
        queued.firstOrNull { it.hash == hash }?.id

    val confirmed = mutableListOf<ConfirmedSlip>()

    /**
     * Mirrors [com.ptut.expense.data.local.dao.SlipConfirmationDao]: the row lands in the ledger,
     * the merchant replaces any rule it had, the picture's hash is recorded, and the queue entry
     * goes. The content key is registered too, or this double would let a payment already filed
     * be filed a second time — which the real unique index would refuse.
     */
    override suspend fun confirm(slip: ConfirmedSlip) {
        confirmed += slip
        queued.firstOrNull { it.id == slip.queuedSlipId }?.let { importedHashes += it.hash }
        queued.removeAll { it.id == slip.queuedSlipId }
        slip.counterparty?.let { counterparty ->
            rules.removeAll { it.counterparty == counterparty }
            rules += MerchantRule(counterparty, slip.categoryId)
        }
        slip.reference?.let {
            contentKeys += Triple(it, slip.amount.toMinorUnits(), slip.occurredAtEpochMillis)
        }
    }

    override suspend fun discard(id: String) {
        queued.removeAll { it.id == id }
    }

    override suspend fun startRun(run: ImportRun) {
        runs += run
    }

    override suspend fun finishRun(run: ImportRun) {
        runs[runs.indexOfFirst { it.id == run.id }] = run
    }

    override suspend fun expireEvidenceOlderThan(cutoffEpochMillis: Long): List<String> =
        queued.filter { it.capturedAtEpochMillis < cutoffEpochMillis }.map { it.id }
}

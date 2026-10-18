package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.ImportRunDao
import com.ptut.expense.data.local.dao.ImportedSlipDao
import com.ptut.expense.data.local.dao.QueuedSlipDao
import com.ptut.expense.data.local.dao.RuleDao
import com.ptut.expense.data.local.dao.SlipConfirmationDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.withCheckedSubCategory
import com.ptut.expense.data.local.entity.FOLDER_SEPARATOR
import com.ptut.expense.data.local.entity.ImportRunEntity
import com.ptut.expense.data.local.entity.ImportedSlipEntity
import com.ptut.expense.data.local.entity.QueuedSlipEntity
import com.ptut.expense.data.local.entity.RuleEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.local.entity.asColumn
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.FiledSlip
import com.ptut.expense.domain.slip.ImportStore
import com.ptut.expense.domain.slip.QueuedSlipEntry

/**
 * [ImportStore] over Room. It owns the mapping and nothing else — every rule about what to write
 * lives in [com.ptut.expense.importer.SlipImporter], where a fake can drive it.
 */
class RoomImportStore(
    private val transactionDao: TransactionDao,
    private val importedSlipDao: ImportedSlipDao,
    private val queuedSlipDao: QueuedSlipDao,
    private val importRunDao: ImportRunDao,
    private val ruleDao: RuleDao,
    private val slipConfirmationDao: SlipConfirmationDao,
) : ImportStore {

    override suspend fun rules(): List<MerchantRule> =
        ruleDao.all().map { MerchantRule(counterparty = it.counterparty, categoryId = it.categoryId, id = it.id) }

    override suspend fun isImported(hash: String): Boolean = importedSlipDao.isImported(hash)

    /**
     * The row first, the hash second, and the order is the safety.
     *
     * Recording the hash first and then failing to write would skip the slip forever — money the
     * reader photographed and never sees again. This way round, a failure between the two leaves
     * the row written and the hash missing, so the next run reads the picture again and the F3
     * content key recognises it. A wasted read is the cheaper mistake.
     */
    override suspend fun file(slip: FiledSlip): Boolean {
        val written = transactionDao.insertIfNew(slip.toEntity()) != IGNORED
        importedSlipDao.record(
            ImportedSlipEntity(
                hash = slip.hash,
                importedAtEpochMillis = slip.read.occurredAtEpochMillis,
            ),
        )
        return written
    }

    override suspend fun queue(slip: QueuedSlipEntry): Boolean =
        queuedSlipDao.queue(slip.toEntity()) != IGNORED

    override suspend fun queuedPictureFor(hash: String): String? =
        queuedSlipDao.idHoldingSlip(hash)

    override suspend fun confirm(slip: ConfirmedSlip) = slipConfirmationDao.confirm(
        transaction = transactionDao.withCheckedSubCategory(slip.toEntity()),
        rule = slip.counterparty?.let { counterparty ->
            RuleEntity(
                id = slip.ruleId,
                counterparty = counterparty,
                categoryId = slip.categoryId,
                createdAtEpochMillis = slip.confirmedAtEpochMillis,
                displayName = slip.displayName?.trim()?.takeIf { it.isNotEmpty() },
            )
        },
        queuedSlipId = slip.queuedSlipId,
    )

    private fun ConfirmedSlip.toEntity() = TransactionEntity(
        id = transactionId,
        occurredAtEpochMillis = occurredAtEpochMillis,
        amountMinor = amount.toMinorUnits(),
        direction = Direction.Expense.name,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        counterparty = counterparty,
        // A slip that read no shop was typed in by hand, amount and all (#113).
        source = (if (counterparty == null) TransactionSource.Manual else TransactionSource.Scan).name,
        reference = reference,
        slipAmountMinor = readAmount?.toMinorUnits(),
        filedAt = confirmedAtEpochMillis,
        sourceFolder = folder,
    )

    override suspend fun discard(id: String) = queuedSlipDao.discard(id)

    override suspend fun startRun(run: ImportRun) = importRunDao.start(run.toEntity())

    override suspend fun finishRun(run: ImportRun) = importRunDao.finish(run.toEntity())

    /** Read before the update: after it, nothing is left older than the cutoff to find. */
    override suspend fun expireEvidenceOlderThan(cutoffEpochMillis: Long): List<String> {
        val ids = queuedSlipDao.idsWithEvidenceOlderThan(cutoffEpochMillis)
        queuedSlipDao.markEvidenceExpiredOlderThan(cutoffEpochMillis)
        queuedSlipDao.dropExpired(ReviewReason.Unreadable.name)
        return ids
    }

    /**
     * Every slip in the set is money out. The header names the rail — `Payment Completed` or
     * `Transfer Completed` — and never the direction, so it is not read as one.
     */
    private fun FiledSlip.toEntity() = TransactionEntity(
        id = id,
        occurredAtEpochMillis = read.occurredAtEpochMillis,
        amountMinor = read.amount.toMinorUnits(),
        direction = Direction.Expense.name,
        categoryId = categoryId,
        counterparty = read.counterparty,
        source = TransactionSource.Scan.name,
        reference = read.reference,
        // A slip filed without asking was filed at exactly what the parser read, so the two agree
        // today. They stop agreeing the moment screen 11c corrects one, which is the point.
        slipAmountMinor = read.amount.toMinorUnits(),
        filedAt = filedAtEpochMillis,
        sourceFolder = folder,
        ruleId = ruleId,
    )

    private fun QueuedSlipEntry.toEntity() = QueuedSlipEntity(
        id = id,
        slipHash = hash,
        merchant = counterparty,
        occurredAtEpochMillis = occurredAtEpochMillis,
        note = reference.orEmpty(),
        amountMinor = amount?.toMinorUnits(),
        reason = reason.name,
        capturedAtEpochMillis = capturedAtEpochMillis,
        evidenceExpired = false,
        lowConfidence = lowConfidence.asColumn(),
        sourceFolder = folder,
    )

    private fun ImportRun.toEntity() = ImportRunEntity(
        id = id,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        outcome = outcome.toColumn(),
        slipsFound = slipsFound,
        failureReason = failureReason,
        filed = filed,
        queued = queued,
        folders = folders.joinToString(FOLDER_SEPARATOR),
    )

    /**
     * The enum's own name would do until somebody renames it, and the freshness query matches the
     * column against [ImportRunEntity.OUTCOME_SUCCESS]. Going through the constants keeps the
     * writer and the query from drifting apart silently.
     */
    private fun ImportOutcome.toColumn(): String = when (this) {
        ImportOutcome.Success -> ImportRunEntity.OUTCOME_SUCCESS
        ImportOutcome.Failure -> ImportRunEntity.OUTCOME_FAILURE
        ImportOutcome.Running -> ImportRunEntity.OUTCOME_RUNNING
    }

    private companion object {
        /** What Room returns from an IGNORE insert that hit the index. */
        const val IGNORED = -1L
    }
}

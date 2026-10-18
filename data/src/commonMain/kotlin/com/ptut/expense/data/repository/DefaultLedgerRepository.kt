package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.ImportRunDao
import com.ptut.expense.data.local.dao.QueuedSlipDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.withCheckedSubCategory
import com.ptut.expense.data.local.entity.ImportRunEntity
import com.ptut.expense.data.local.entity.QueuedSlipEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.local.entity.toDomain
import com.ptut.expense.data.local.entity.toEntity
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.startOfDayEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The ledger, read straight out of SQLite. Every window is computed here in Kotlin from a
 * Bangkok date and handed to the DAO as epoch millis — F2 keeps SQLite's own date functions out
 * of the query, because `localtime` reads the device zone and would re-bucket the ledger the
 * moment the user travels.
 *
 * Known limit: the day window is fixed when this is built, so an app left open across midnight
 * keeps yesterday's "today" until it is rebuilt. Slice 4 owns that, where a lifecycle-aware
 * holder can notice the date change.
 */
@OptIn(ExperimentalUuidApi::class)
class DefaultLedgerRepository(
    private val transactionDao: TransactionDao,
    queuedSlipDao: QueuedSlipDao,
    importRunDao: ImportRunDao,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    /** Injected so a test can name the row it wrote. Random everywhere else. */
    private val newId: () -> String = { Uuid.random().toString() },
) : LedgerRepository {

    private val today: LocalDate = clock.now().toLocalDateTime(BangkokTime).date

    override val todaysTransactions: StateFlow<LedgerSnapshot> =
        transactionDao.observeBetween(
            fromEpochMillis = today.startOfDayEpochMillis(),
            untilEpochMillis = today.plus(1, DateTimeUnit.DAY).startOfDayEpochMillis(),
        ).asTransactions(scope)

    override val recentTransactions: StateFlow<LedgerSnapshot> =
        transactionDao.observeSince(startOfPreviousMonth()).asTransactions(scope)

    override val allTransactions: StateFlow<LedgerSnapshot> =
        transactionDao.observeAll().asTransactions(scope)

    override val sinceLastImportMillis: StateFlow<Long?> =
        importRunDao.observeLastSuccessfulFinishedAt(ImportRunEntity.OUTCOME_SUCCESS)
            .map { lastSuccess ->
                // Never negative: a clock that has moved backwards must not read as a future
                // import, which would render as a healthy ledger.
                lastSuccess?.let { (clock.now().toEpochMilliseconds() - it).coerceAtLeast(0L) }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val importRuns: StateFlow<List<ImportRun>> =
        importRunDao.observeRecent(JOURNAL_DEPTH)
            .map { rows -> rows.map(ImportRunEntity::toDomain) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val queuedSlips: StateFlow<List<QueuedSlip>> =
        queuedSlipDao.observeAll()
            .map { rows -> rows.map(QueuedSlipEntity::toDomain) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Stamped here rather than by the caller: an id is storage's business, and "now" for a row a
     * person just typed is this repository's clock — the same one the day window was built from,
     * so a save at 23:59 cannot land outside the day it is shown in.
     */
    override suspend fun delete(transactionId: String) {
        transactionDao.deleteById(transactionId)
    }

    override suspend fun restore(transaction: Transaction) {
        transactionDao.insert(transactionDao.withCheckedSubCategory(transaction.toEntity()))
    }

    /**
     * The edit stamp comes from this repository's clock, the same one the day window was built
     * from — so a correction made at 23:59 cannot claim a day the screen is not showing.
     */
    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit) {
        transactionDao.applyEdit(
            id = transactionId,
            amountMinor = edit.amount.toMinorUnits(),
            categoryId = edit.categoryId,
            subCategoryId = edit.subCategoryId,
            memo = edit.memo,
            occurredAtEpochMillis = edit.occurredAtEpochMillis,
            editedAtEpochMillis = clock.now().toEpochMilliseconds(),
        )
    }

    override suspend fun record(draft: TransactionDraft) {
        val now = clock.now().toEpochMilliseconds()
        val row = draft.toEntity(id = newId(), occurredAtEpochMillis = draft.occurredAtEpochMillis ?: now, filedAt = now)
        transactionDao.insert(transactionDao.withCheckedSubCategory(row))
    }

    /**
     * The reports measure a period against the one before it, and the longest of those is a
     * month, so history reaches back to the first of the previous month and no further.
     */
    private fun startOfPreviousMonth(): Long {
        val previousMonth = today.minus(1, DateTimeUnit.MONTH)
        return LocalDate(previousMonth.year, previousMonth.month, 1).startOfDayEpochMillis()
    }

    /**
     * [LedgerSnapshot.Unanswered] is the initial value because it is the truth: Room has not run
     * the query yet, and an empty list here would be indistinguishable from an empty ledger.
     *
     * `Eagerly` is kept, but it is now warmth rather than correctness. It used to be load-bearing
     * — a late subscriber meeting `emptyList()` would flash the FR-20 "nothing recorded" copy —
     * and with the two cases separated a late subscriber meets `Unanswered` instead and renders
     * as loading. Moving to `WhileSubscribed` is now safe, and is its own decision to make.
     */
    private fun Flow<List<TransactionEntity>>.asTransactions(
        scope: CoroutineScope,
    ): StateFlow<LedgerSnapshot> =
        map { rows -> LedgerSnapshot.Rows(rows.map(TransactionEntity::toDomain)) }
            .stateIn(scope, SharingStarted.Eagerly, LedgerSnapshot.Unanswered)

    private companion object {
        /**
         * How far the Activity screen looks back. A log, not a ledger — nobody audits a scan
         * from six months ago, and the importer appends to this table every fifteen minutes.
         */
        const val JOURNAL_DEPTH = 50
    }
}

package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.ImportRunDao
import com.ptut.expense.data.local.dao.QueuedSlipDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.entity.ImportRunEntity
import com.ptut.expense.data.local.entity.QueuedSlipEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerRepositoryContract
import com.ptut.expense.domain.ledger.rows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
private const val TWO_MINUTES_MILLIS = 2L * 60 * 1000

class DefaultLedgerRepositoryTest : LedgerRepositoryContract() {

    override fun TestScope.newRepository(): LedgerRepository = repository()

    @Test
    fun aLedgerThatHasNeverImportedSaysSoRatherThanReadingAsFresh() = runTest {
        val repository = repository(lastSuccessfulImportAt = null)
        runCurrent()

        assertEquals(null, repository.sinceLastImportMillis.value)
    }

    /**
     * The independent check on FR-19: Bangkok is UTC+7, so a Bangkok day starts at 17:00 UTC the
     * day before. A window built from the device zone, or from UTC, lands somewhere else.
     */
    @Test
    fun theDayWindowIsABangkokDayNotAUtcOne() = runTest {
        val transactions = FakeTransactionDao(transactionRows())
        repository(transactions = transactions)

        val window = assertNotNull(transactions.lastWindow, "no day window was ever asked for")
        assertEquals(ONE_DAY_MILLIS, window.second - window.first)
        assertEquals(
            LocalTime(17, 0),
            Instant.fromEpochMilliseconds(window.first).toLocalDateTime(TimeZone.UTC).time,
        )
    }

    @Test
    fun historyReachesTheFirstOfThePreviousMonthAndNoFurther() = runTest {
        val repository = repository()
        runCurrent()

        val ids = repository.recentTransactions.value.rows().map { it.id }
        assertTrue("first-of-previous-month" in ids, "the boundary row itself was dropped")
        assertTrue("day-before-the-window" !in ids, "history reached past the boundary")
    }

    @Test
    fun rowsMapToExactAmountsAndKeepTheirOwnDirectionAndSource() = runTest {
        val repository = repository()
        runCurrent()

        val income = repository.recentTransactions.value.rows().single { it.direction == Direction.Income }
        assertEquals(Satang(450_000), income.amount)
        assertEquals(TransactionSource.Manual, income.source)
        assertEquals("Payroll", income.counterparty)
    }

    /**
     * The storage shape of a hand-typed row. Money is an INTEGER count of satang (FR-9), the
     * instant is this repository's clock rather than the caller's, and `reference` stays empty
     * because there is no slip for a row somebody typed — half of the F3 content key would
     * otherwise be invented.
     */
    @Test
    fun aRecordedRowIsStoredAsMinorUnitsStampedByTheLedgersOwnClock() = runTest {
        val transactions = FakeTransactionDao(rows = emptyList())
        val repository = repository(transactions = transactions)

        repository.record(
            TransactionDraft(
                amount = Satang(48_500),
                direction = Direction.Expense,
                categoryId = "food",
            ),
        )

        val written = transactions.rows.single()
        assertEquals("written-row", written.id)
        assertEquals(48_500L, written.amountMinor)
        assertEquals(now.toEpochMilliseconds(), written.occurredAtEpochMillis)
        assertEquals(Direction.Expense.name, written.direction)
        assertEquals(TransactionSource.Manual.name, written.source)
        assertEquals("food", written.categoryId)
        assertNull(written.reference)
    }

    /** Home's Saved today block reads this. A row typed by hand enters the ledger as it happens. */
    @Test
    fun aRecordedRowIsFiledAtTheInstantItWasWritten() = runTest {
        val transactions = FakeTransactionDao(rows = emptyList())
        val repository = repository(transactions = transactions)

        repository.record(
            TransactionDraft(amount = Satang(5_000), direction = Direction.Expense, categoryId = "food"),
        )

        assertEquals(now.toEpochMilliseconds(), transactions.rows.single().filedAt)
    }

    /**
     * FR-21's undo puts the record back as it was. A slip confirmed today, deleted and restored is
     * still one saved today, so its filing time has to survive both the read and the write.
     */
    @Test
    fun anUndoneDeleteKeepsWhenTheRowFirstEnteredTheLedger() = runTest {
        val filedAt = at(2026, 8, 17, 11, 0)
        val confirmed = row("confirmed", 2026, 8, 2, 19, 20, 15_500).copy(filedAt = filedAt)
        val transactions = FakeTransactionDao(rows = listOf(confirmed))
        val repository = repository(transactions = transactions)
        runCurrent()

        val victim = repository.allTransactions.value.rows().single()
        assertEquals(filedAt, victim.filedAt, "the ledger dropped the filing time on the way out")

        repository.delete(victim.id)
        repository.restore(victim)

        assertEquals(filedAt, transactions.rows.single().filedAt, "the undo dropped the filing time")
    }

    @Test
    fun aRecordedRowKeepsTheSubCategoryPickedForIt() = runTest {
        val transactions = FakeTransactionDao(rows = emptyList())
        val repository = repository(transactions = transactions)

        repository.record(
            TransactionDraft(
                amount = Satang(6_500),
                direction = Direction.Expense,
                categoryId = "groceries",
                subCategoryId = "supermarket",
            ),
        )

        assertEquals("supermarket", transactions.rows.single().subCategoryId)
    }

    @Test
    fun anUndoneDeleteKeepsTheSubCategory() = runTest {
        val filed = row("filed", 2026, 8, 17, 11, 0, 64_250).copy(subCategoryId = "supermarket")
        val transactions = FakeTransactionDao(rows = listOf(filed))
        val repository = repository(transactions = transactions)
        runCurrent()

        val victim = repository.allTransactions.value.rows().single()
        repository.delete(victim.id)
        repository.restore(victim)

        assertEquals("supermarket", transactions.rows.single().subCategoryId, "the undo dropped the sub-category")
    }

    private fun TestScope.repository(
        transactions: FakeTransactionDao = FakeTransactionDao(transactionRows()),
        lastSuccessfulImportAt: Long? = now.toEpochMilliseconds() - TWO_MINUTES_MILLIS,
    ): LedgerRepository = DefaultLedgerRepository(
        transactionDao = transactions,
        queuedSlipDao = FakeQueuedSlipDao(queuedSlipRows()),
        importRunDao = FakeImportRunDao(lastSuccessfulImportAt),
        scope = backgroundScope,
        clock = clock,
        newId = { "written-row" },
    )
}

/** `now` in the contract is 2026-08-17 12:00 Bangkok, so the previous month starts 1 July. */
private fun transactionRows() = listOf(
    row("today-groceries", 2026, 8, 17, 9, 30, 12_800),
    row(
        id = "today-salary",
        year = 2026, month = 8, day = 17, hour = 8, minute = 0,
        amountMinor = 450_000,
        direction = Direction.Income,
        source = TransactionSource.Manual,
        counterparty = "Payroll",
    ),
    row("yesterday-supermarket", 2026, 8, 16, 19, 22, 84_500),
    row("first-of-previous-month", 2026, 7, 1, 0, 0, 10_000),
    row("day-before-the-window", 2026, 6, 30, 23, 59, 10_000),
)

private fun queuedSlipRows() = listOf(
    slip("queued-readable", 2026, 8, 17, 19, 41, amountMinor = 48_500, reason = ReviewReason.NewMerchant),
    slip("queued-unreadable", 2026, 8, 16, 21, 7, amountMinor = null, reason = ReviewReason.AmountUnclear),
)

private fun row(
    id: String,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    amountMinor: Long,
    direction: Direction = Direction.Expense,
    source: TransactionSource = TransactionSource.Scan,
    counterparty: String? = "Foodland Rama IV",
    // A ledger where nothing is categorised is not one this app ever holds, and FR-21's undo
    // has to put the category back — an all-null fixture would assert that vacuously.
    categoryId: String? = "groceries",
) = TransactionEntity(
    id = id,
    occurredAtEpochMillis = at(year, month, day, hour, minute),
    amountMinor = amountMinor,
    direction = direction.name,
    categoryId = categoryId,
    counterparty = counterparty,
    source = source.name,
    reference = null,
)

private fun slip(
    id: String,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    amountMinor: Long?,
    reason: ReviewReason,
) = QueuedSlipEntity(
    id = id,
    slipHash = "hash-$id",
    merchant = "MK Restaurant Rama 9",
    occurredAtEpochMillis = at(year, month, day, hour, minute),
    note = "ref 0142K9",
    amountMinor = amountMinor,
    reason = reason.name,
    capturedAtEpochMillis = at(year, month, day, hour, minute),
    evidenceExpired = false,
)

private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDateTime(year, month, day, hour, minute).toInstant(BangkokTime).toEpochMilliseconds()

/**
 * The filtering mirrors the DAO's SQL in Kotlin. It cannot prove the SQL itself is right — no
 * gate runs a real database yet — but it does hold the repository to the window it asks for.
 */
private class FakeTransactionDao(
    rows: List<TransactionEntity>,
    /** Sub-category to its parent, the pairs the real `sub_category` table would hold. */
    private val subCategories: Map<String, String> = mapOf("supermarket" to "groceries"),
) : TransactionDao {

    private val all = MutableStateFlow(rows)

    val rows: List<TransactionEntity> get() = all.value

    var lastWindow: Pair<Long, Long>? = null
        private set

    override fun observeBetween(
        fromEpochMillis: Long,
        untilEpochMillis: Long,
    ): Flow<List<TransactionEntity>> {
        lastWindow = fromEpochMillis to untilEpochMillis
        return all.map { rows ->
            rows.filter {
                it.occurredAtEpochMillis >= fromEpochMillis && it.occurredAtEpochMillis < untilEpochMillis
            }.sortedByDescending { it.occurredAtEpochMillis }
        }
    }

    override fun observeAll(): Flow<List<TransactionEntity>> =
        all.map { rows -> rows.sortedByDescending { it.occurredAtEpochMillis } }

    override suspend fun all(): List<TransactionEntity> =
        rows.sortedByDescending { it.occurredAtEpochMillis }

    override fun observeSince(fromEpochMillis: Long): Flow<List<TransactionEntity>> =
        all.map { rows ->
            rows.filter { it.occurredAtEpochMillis >= fromEpochMillis }
                .sortedByDescending { it.occurredAtEpochMillis }
        }

    override suspend fun insert(row: TransactionEntity) = all.update { it + row }

    override suspend fun deleteById(id: String) = all.update { rows -> rows.filterNot { it.id == id } }

    override suspend fun subCategoryUnder(subCategoryId: String, categoryId: String): String? =
        subCategoryId.takeIf { subCategories[it] == categoryId }

    /** Names the six columns the real UPDATE names, so a widened write fails here first. */
    override suspend fun applyEdit(
        id: String,
        amountMinor: Long,
        categoryId: String?,
        subCategoryId: String?,
        memo: String?,
        occurredAtEpochMillis: Long?,
        editedAtEpochMillis: Long,
    ) = all.update { rows ->
        rows.map { row ->
            if (row.id != id) {
                row
            } else {
                row.copy(
                    amountMinor = amountMinor,
                    categoryId = categoryId,
                    subCategoryId = subCategoryId?.takeIf { subCategories[it] == categoryId },
                    memo = memo,
                    occurredAtEpochMillis = occurredAtEpochMillis ?: row.occurredAtEpochMillis,
                    editedAt = editedAtEpochMillis,
                )
            }
        }
    }

    /** Mirrors the F3 content index: same reference, amount and instant writes nothing. */
    override suspend fun insertIfNew(row: TransactionEntity): Long {
        val clash = all.value.any {
            it.reference != null &&
                it.reference == row.reference &&
                it.amountMinor == row.amountMinor &&
                it.occurredAtEpochMillis == row.occurredAtEpochMillis
        }
        if (clash) return -1
        all.update { it + row }
        return all.value.size.toLong()
    }
}

private class FakeQueuedSlipDao(rows: List<QueuedSlipEntity>) : QueuedSlipDao {
    private val all = MutableStateFlow(rows)

    override fun observeAll(): Flow<List<QueuedSlipEntity>> =
        all.map { rows -> rows.sortedByDescending { it.occurredAtEpochMillis } }

    override suspend fun discard(id: String) = all.update { rows -> rows.filterNot { it.id == id } }

    /** Mirrors the unique slipHash: -1 when the picture is already on the queue. */
    override suspend fun queue(row: QueuedSlipEntity): Long {
        if (all.value.any { it.slipHash == row.slipHash }) return -1
        all.update { it + row }
        return all.value.size.toLong()
    }

    override suspend fun idHoldingSlip(hash: String): String? =
        all.value.firstOrNull { it.slipHash == hash }?.id

    override suspend fun idsWithEvidenceOlderThan(cutoff: Long): List<String> =
        all.value.filter { it.capturedAtEpochMillis < cutoff && !it.evidenceExpired }.map { it.id }

    override suspend fun markEvidenceExpiredOlderThan(cutoff: Long) = all.update { rows ->
        rows.map {
            if (it.capturedAtEpochMillis < cutoff) it.copy(evidenceExpired = true) else it
        }
    }

    override suspend fun dropExpired(reason: String) = all.update { rows ->
        rows.filterNot { it.evidenceExpired && it.reason == reason }
    }
}

private class FakeImportRunDao(private val lastSuccessfulFinishedAt: Long?) : ImportRunDao {

    val runs = mutableListOf<ImportRunEntity>()

    override fun observeLastSuccessfulFinishedAt(successOutcome: String): Flow<Long?> =
        MutableStateFlow(lastSuccessfulFinishedAt)

    override fun observeRecent(limit: Int): Flow<List<ImportRunEntity>> =
        MutableStateFlow(runs.sortedByDescending { it.startedAtEpochMillis }.take(limit))

    override suspend fun start(row: ImportRunEntity) {
        runs += row
    }

    override suspend fun finish(row: ImportRunEntity) {
        runs[runs.indexOfFirst { it.id == row.id }] = row
    }
}

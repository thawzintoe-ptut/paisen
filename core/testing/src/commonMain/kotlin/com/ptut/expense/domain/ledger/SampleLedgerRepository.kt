package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.ledger.LedgerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * A populated ledger for tests that need one. It is a fixture, not a fallback: the app builds
 * [com.ptut.expense.data.repository.DefaultLedgerRepository] and nothing in `commonMain` can
 * reach this, so an empty screen in the running app is the truth rather than a wiring mistake.
 *
 * Dates are relative to now rather than pinned to a date in 2026, so the week chart and the
 * "spent today" total stay populated whenever a test runs.
 */
class SampleLedgerRepository(
    private val clock: Clock = Clock.System,
    /** Two minutes, so the fixture reads as a healthy importer unless a test asks otherwise. */
    sinceLastImport: Long? = 2 * 60 * 1000L,
) : LedgerRepository {

    private val today = clock.now().toLocalDateTime(BangkokTime).date

    private val history = listOf(
        spend(0, 18, 30, "Foodland Rama IV", 64_250, "groceries"),
        spend(0, 10, 15, "Café Amazon", 7_000, "food"),
        spend(0, 9, 4, "Grab", 9_600, "transport"),
        spend(0, 8, 12, "7-Eleven Sathon", 12_800, "groceries"),

        spend(1, 19, 22, "Big C Extra", 84_500, "groceries"),
        spend(1, 8, 44, "BTS Rabbit top-up", 39_500, "transport"),

        spend(2, 12, 40, "Café Amazon", 30_500, "food"),

        spend(4, 20, 10, "MEA Electricity", 62_000, "bills"),
        spend(4, 13, 5, "Grab", 27_000, "transport"),

        spend(5, 19, 0, "After You", 56_000, "food"),

        spend(6, 17, 45, "Lazada", 25_000, "shopping"),

        // The week before, so "this week" has a baseline to be measured against.
        spend(8, 12, 0, "Foodland Rama IV", 71_000, "groceries"),
        spend(9, 19, 30, "MK Restaurant", 92_000, "food"),
        spend(11, 8, 20, "Grab", 18_000, "transport"),
        spend(12, 14, 0, "Uniqlo", 149_000, "shopping"),

        // Far enough back to fill out "this month" and last month's comparison.
        spend(20, 11, 0, "True Move", 59_900, "bills"),
        spend(26, 16, 30, "Villa Market", 88_000, "groceries"),
        spend(34, 19, 15, "Sizzler", 78_000, "food"),
        spend(40, 9, 0, "Grab", 21_000, "transport"),
    )

    private val today_ = snapshotOf(history.filter { it.id.startsWith("d0-") })
    private val recent = snapshotOf(history)
    private val all = snapshotOf(history)

    override val todaysTransactions: StateFlow<LedgerSnapshot> = today_

    override val recentTransactions: StateFlow<LedgerSnapshot> = recent

    override val allTransactions: StateFlow<LedgerSnapshot> = all

    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(sinceLastImport)

    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(
        listOf(
            QueuedSlip(
                id = "q1",
                merchant = "MK Restaurant Rama 9",
                occurredAtEpochMillis = at(daysAgo = 0, hour = 19, minute = 41),
                note = "ref 0142K9",
                amount = Satang(48_500),
                reason = ReviewReason.NewMerchant,
                capturedAtEpochMillis = at(daysAgo = 0, hour = 19, minute = 41),
            ),
            QueuedSlip(
                id = "q2",
                merchant = "PTT Station Phra Ram 2",
                occurredAtEpochMillis = at(daysAgo = 1, hour = 21, minute = 7),
                note = "glare on slip",
                amount = null,
                reason = ReviewReason.AmountUnclear,
                capturedAtEpochMillis = at(daysAgo = 1, hour = 21, minute = 7),
            ),
        ),
    )

    private var written = 0

    /**
     * Stamped the way the Room-backed ledger stamps one, so a screen written against this fixture
     * meets the same row it will meet in the app: an id of its own, this clock's instant, and a
     * list that still reads newest first.
     */
    override suspend fun delete(transactionId: String) = everyList { rows ->
        rows.filterNot { it.id == transactionId }
    }

    /** Back into the lists it was taken from, and only into today's if it belongs there. */
    override suspend fun restore(transaction: Transaction) {
        all.add(transaction)
        recent.add(transaction)
        if (transaction.occurredAtEpochMillis.dateIn(BangkokTime) == today) today_.add(transaction)
    }

    /** The three fields screen 11c owns, and the stamp. Everything else stays where it was. */
    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit) = everyList { rows ->
        rows.map { row ->
            if (row.id != transactionId) {
                row
            } else {
                row.copy(
                    amount = edit.amount,
                    categoryId = edit.categoryId,
                    subCategoryId = edit.subCategoryId,
                    memo = edit.memo,
                    occurredAtEpochMillis = edit.occurredAtEpochMillis ?: row.occurredAtEpochMillis,
                    editedAt = clock.now().toEpochMilliseconds(),
                )
            }
        }
    }

    override suspend fun record(draft: TransactionDraft) {
        val row = Transaction(
            id = "manual-${++written}",
            occurredAtEpochMillis = clock.now().toEpochMilliseconds(),
            amount = draft.amount,
            direction = draft.direction,
            categoryId = draft.categoryId,
            counterparty = draft.counterparty,
            source = draft.source,
            memo = draft.memo,
        )
        today_.add(row)
        recent.add(row)
        all.add(row)
    }

    private fun snapshotOf(rows: List<Transaction>): MutableStateFlow<LedgerSnapshot> =
        MutableStateFlow(LedgerSnapshot.Rows(rows))

    /** Today, recent and all hold overlapping views of one table, so a write reaches every one. */
    private fun everyList(change: (List<Transaction>) -> List<Transaction>) =
        listOf(today_, recent, all).forEach { flow -> flow.change(change) }

    private fun MutableStateFlow<LedgerSnapshot>.change(
        change: (List<Transaction>) -> List<Transaction>,
    ) = update { snapshot -> LedgerSnapshot.Rows(change(snapshot.rows())) }

    private fun MutableStateFlow<LedgerSnapshot>.add(row: Transaction) =
        change { rows -> (rows + row).sortedByDescending(Transaction::occurredAtEpochMillis) }

    private fun spend(
        daysAgo: Int,
        hour: Int,
        minute: Int,
        counterparty: String,
        satang: Long,
        categoryId: String,
    ) = Transaction(
        id = "d$daysAgo-$counterparty-$satang",
        occurredAtEpochMillis = at(daysAgo, hour, minute),
        amount = Satang(satang),
        direction = Direction.Expense,
        categoryId = categoryId,
        counterparty = counterparty,
        source = TransactionSource.Scan,
    )

    private fun at(daysAgo: Int, hour: Int, minute: Int): Long =
        LocalDateTime(today.minus(daysAgo, DateTimeUnit.DAY), LocalTime(hour, minute))
            .toInstant(BangkokTime)
            .toEpochMilliseconds()
}

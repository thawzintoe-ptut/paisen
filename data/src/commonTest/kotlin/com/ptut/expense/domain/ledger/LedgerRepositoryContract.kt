package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.asEdit
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The promises every ledger makes, whatever is behind it. Both the sample fixture and the Room
 * implementation run this, so a screen written against one cannot be surprised by the other.
 *
 * The assertions are properties rather than fixed values, because the two implementations hold
 * deliberately different data — the point is that the shape of the answer agrees.
 */
abstract class LedgerRepositoryContract {

    /** One clock for every implementation, so "today" names the same Bangkok day in each. */
    protected val now: Instant = LocalDateTime(2026, 8, 17, 12, 0).toInstant(BangkokTime)

    protected val clock: Clock = object : Clock {
        override fun now(): Instant = now
    }

    protected val today: LocalDate get() = now.toLocalDateTime(BangkokTime).date

    /** Built inside the test's own scope, so a hot flow dies with the test. */
    protected abstract fun TestScope.newRepository(): LedgerRepository

    @Test
    fun theFixtureSpansMoreThanOneDaySoTheRestOfThisIsNotVacuous() = runTest {
        val recent = settled().recentTransactions.value.rows()
        assertTrue(recent.any { it.bangkokDate == today }, "nothing falls on today")
        assertTrue(recent.any { it.bangkokDate != today }, "everything falls on today")
    }

    @Test
    fun todaysTransactionsAreExactlyTheRecentOnesOnTodaysBangkokDate() = runTest {
        val repository = settled()
        assertEquals(
            repository.recentTransactions.value.rows().filter { it.bangkokDate == today }.ids(),
            repository.todaysTransactions.value.rows().ids(),
        )
    }

    @Test
    fun recentTransactionsReadNewestFirst() = runTest {
        val occurredAt = settled().recentTransactions.value.rows().map { it.occurredAtEpochMillis }
        assertEquals(occurredAt.sortedDescending(), occurredAt)
    }

    /**
     * The Ledger audits against this, so it may never be the narrower window. A list that
     * quietly stopped at the reports horizon is how a month stops adding up unseen.
     */
    @Test
    fun everyRecentTransactionAlsoAppearsInTheWholeLedger() = runTest {
        val repository = settled()
        val everything = repository.allTransactions.value.rows().ids().toSet()
        assertTrue(
            everything.containsAll(repository.recentTransactions.value.rows().ids()),
            "the whole ledger is missing rows the reports window holds",
        )
    }

    @Test
    fun theWholeLedgerReadsNewestFirst() = runTest {
        val occurredAt = settled().allTransactions.value.rows().map { it.occurredAtEpochMillis }
        assertEquals(occurredAt.sortedDescending(), occurredAt)
    }

    @Test
    fun everyTransactionHasItsOwnId() = runTest {
        val ids = settled().recentTransactions.value.rows().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * A negative span would render as a future import, which reads as a healthy ledger. Absent
     * is allowed and means no import has ever succeeded; it is the screen's job to say so.
     */
    @Test
    fun timeSinceTheLastImportIsAbsentOrNeverNegative() = runTest {
        val since = settled().sinceLastImportMillis.value
        assertTrue(since == null || since >= 0L, "a negative span reads as a future import")
    }

    /** FR-7: an unread amount stays absent. A zero would be a guess that totals believe. */
    @Test
    fun anUnreadableSlipCarriesNoAmountRatherThanAZero() = runTest {
        val amounts = settled().queuedSlips.value.map { it.amount }
        assertTrue(amounts.any { it == null }, "fixture holds no unreadable slip to check")
        assertTrue(amounts.none { it == Satang.Zero }, "a zero stands in for an unread amount")
    }

    /**
     * The write, seen from the only side a screen has: nobody is told, the day's list simply
     * holds it. FR-16 — a row typed by hand is a row Today counts.
     */
    @Test
    fun aRecordedRowJoinsTodaysListWithoutAnyoneRefreshingAnything() = runTest {
        val repository = settled()
        val before = repository.todaysTransactions.value.rows().size

        repository.record(
            TransactionDraft(
                amount = Satang(48_500),
                direction = Direction.Expense,
                categoryId = "food",
            ),
        )
        runCurrent()

        val today = repository.todaysTransactions.value.rows()
        assertEquals(before + 1, today.size, "the row never reached the day's list")

        val saved = today.single { it.amount == Satang(48_500) }
        assertEquals("food", saved.categoryId)
        assertEquals(TransactionSource.Manual, saved.source)
        assertTrue(saved.id.isNotEmpty(), "the row was stored without an id")
    }

    @Test
    fun aDeletedRowLeavesTheLedgerItWasIn() = runTest {
        val repository = settled()
        val victim = repository.allTransactions.value.rows().first()

        repository.delete(victim.id)
        runCurrent()

        assertTrue(
            repository.allTransactions.value.rows().none { it.id == victim.id },
            "the row is still in the ledger after a delete",
        )
    }

    /**
     * FR-21's undo restores the record, not a copy of it. The id and the category are the two
     * that matter: a new id would break the F3 content key, and a lost category would file the
     * row under nothing the next time a report ran.
     */
    @Test
    fun restoringPutsBackTheSameRowWithItsCategory() = runTest {
        val repository = settled()
        val victim = repository.allTransactions.value.rows().first { it.categoryId != null }

        repository.delete(victim.id)
        runCurrent()
        repository.restore(victim)
        runCurrent()

        assertEquals(victim, repository.allTransactions.value.rows().singleOrNull { it.id == victim.id })
    }

    /**
     * FR-21's correction. The three fields move and the stamp appears; the merchant, the
     * reference and the source are what the correction is measured against, so a ledger that let
     * any of them drift would erase the evidence with the edit. An edit with no time keeps the time.
     */
    @Test
    fun anEditMovesTheThreeFieldsAndLeavesTheEvidenceAlone() = runTest {
        val repository = settled()
        val before = repository.allTransactions.value.rows().first { it.categoryId != null }
        val edit = TransactionEdit(
            amount = before.amount + Satang(1_000),
            categoryId = "bills",
            memo = "checked against the paper slip",
        )

        repository.applyEdit(before.id, edit)
        runCurrent()

        val after = repository.allTransactions.value.rows().single { it.id == before.id }
        assertEquals(edit.copy(occurredAtEpochMillis = before.occurredAtEpochMillis), after.asEdit())
        assertNotNull(after.editedAt, "the row was corrected without being marked edited")

        assertEquals(before.counterparty, after.counterparty)
        assertEquals(before.reference, after.reference)
        assertEquals(before.occurredAtEpochMillis, after.occurredAtEpochMillis)
        assertEquals(before.source, after.source)
        assertEquals(before.slipAmount, after.slipAmount)
    }

    /** Lets the eagerly-started collectors take their first value before anything is read. */
    private fun TestScope.settled(): LedgerRepository = newRepository().also { runCurrent() }

    private val Transaction.bangkokDate: LocalDate
        get() = Instant.fromEpochMilliseconds(occurredAtEpochMillis).toLocalDateTime(BangkokTime).date

    private fun List<Transaction>.ids(): Set<String> = map { it.id }.toSet()
}

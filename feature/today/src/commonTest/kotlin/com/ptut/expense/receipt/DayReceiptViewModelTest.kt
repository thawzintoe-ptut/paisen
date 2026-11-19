package com.ptut.expense.receipt

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class DayReceiptViewModelTest : ViewModelTest() {

    @Test
    fun `today and yesterday are named and an older day shows its date`() = runTest {
        assertEquals(AppText.copy.common.today, loaded("2026-08-14", emptyList()).title)
        assertEquals(AppText.copy.common.yesterday, loaded("2026-08-13", emptyList()).title)
        assertEquals("Wed 12 Aug", loaded("2026-08-12", emptyList()).title)
    }

    @Test
    fun `subtotals run from the biggest category down and the total is spending only`() = runTest {
        val rows = listOf(
            spend("a", hour = 9, satang = 5_000, categoryId = "transport"),
            spend("b", hour = 12, satang = 20_000, categoryId = "food"),
            spend("c", hour = 18, satang = 10_000, categoryId = "food"),
            spend("d", hour = 13, satang = 900_000, categoryId = "income").copy(direction = Direction.Income),
        )

        val receipt = loaded("2026-08-14", rows)

        assertEquals(listOf("Food & drink", "Transport"), receipt.subtotals.map { it.category })
        assertEquals(listOf("09:00", "12:00", "18:00"), receipt.lines.map { it.time })
        assertEquals("฿350.00", receipt.total)
        assertNull(receipt.empty)
    }

    @Test
    fun `a day with no spending says so in words`() = runTest {
        val receipt = loaded("2026-08-14", listOf(spend("y", hour = 12, satang = 5_000, categoryId = "food", day = 13)))

        assertEquals(AppText.copy.receipt.nothingSpent, receipt.empty)
        assertEquals(emptyList(), receipt.lines)
        assertNull(receipt.closing)
    }

    private suspend fun loaded(date: String, rows: List<Transaction>): DayReceiptUiState.Loaded {
        var receipt: DayReceiptUiState.Loaded? = null
        DayReceiptViewModel(date, FakeLedger(rows), clock = friday).uiState.test {
            receipt = awaitLoaded()
            cancelAndIgnoreRemainingEvents()
        }
        return requireNotNull(receipt)
    }

    private suspend fun TurbineTestContext<DayReceiptUiState>.awaitLoaded(): DayReceiptUiState.Loaded {
        var state = awaitItem()
        while (state !is DayReceiptUiState.Loaded) state = awaitItem()
        return state
    }

    private fun spend(id: String, hour: Int, satang: Long, categoryId: String, day: Int = 14) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(2026, 8, day, hour, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(satang),
        direction = Direction.Expense,
        categoryId = categoryId,
        counterparty = "Somewhere",
    )

    private val friday = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 8, 14, 20, 0).toInstant(BangkokTime)
    }
}

package com.ptut.expense.ledger

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.LedgerFilter
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.Provenance
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerViewModelTest : ViewModelTest() {

    private val autoGroceries = row("t1", day = 14, hour = 18, merchant = "Foodland", amount = 64_250)
    private val byHandCoffee = row(
        "t2",
        day = 14,
        hour = 10,
        merchant = "Café Amazon",
        amount = 7_000,
        source = TransactionSource.Manual,
    )
    private val correctedTaxi = row(
        "t3",
        day = 13,
        hour = 9,
        merchant = "Grab",
        amount = 9_600,
        editedAt = 1L,
    )
    private val salary = row(
        "t4",
        day = 13,
        hour = 8,
        merchant = "Payroll",
        amount = 3_200_000,
        direction = Direction.Income,
        source = TransactionSource.Manual,
    )

    private val everything = listOf(autoGroceries, byHandCoffee, correctedTaxi, salary)

    private val julyRent = row("t5", day = 2, hour = 9, merchant = "Rent", amount = 900_000, month = 7)
    private val newYearsEve = row("t6", day = 31, hour = 20, merchant = "Party", amount = 50_000, month = 12, year = 2025)
    private val threeMonths = everything + listOf(julyRent, newYearsEve)

    @AfterTest
    fun englishAgain() {
        AppText.use(Language.English)
    }

    @Test
    fun `days read newest first and each header carries a signed net`() = runTest {
        loaded(everything) { state ->
            assertEquals(listOf("Fri 14 Aug", "Thu 13 Aug"), state.days.map { it.date })
            // 64,250 + 7,000 out and nothing in, so the day is negative.
            assertEquals("−฿712.50", state.days[0].total)
            // 3,200,000 in against 9,600 out leaves the day positive.
            assertEquals("+฿31,904.00", state.days[1].total)
        }
    }

    @Test
    fun `each filter narrows the list and All restores it`() = runTest {
        val viewModel = viewModel(everything)

        viewModel.uiState.test {
            assertEquals(
                listOf("Foodland", "Café Amazon", "Grab", "Payroll"),
                awaitSuccess().merchants(),
            )

            viewModel.filter(LedgerFilter.Auto)
            assertEquals(listOf("Foodland", "Grab"), awaitSuccess().merchants())

            viewModel.filter(LedgerFilter.ByHand)
            assertEquals(listOf("Café Amazon", "Payroll"), awaitSuccess().merchants())

            viewModel.filter(LedgerFilter.Edited)
            assertEquals(listOf("Grab"), awaitSuccess().merchants())

            viewModel.filter(LedgerFilter.Income)
            assertEquals(listOf("Payroll"), awaitSuccess().merchants())

            viewModel.filter(LedgerFilter.All)
            assertEquals(
                listOf("Foodland", "Café Amazon", "Grab", "Payroll"),
                awaitSuccess().merchants(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** An auto-filed row someone later corrected is both. One enum could not carry that. */
    @Test
    fun `a corrected row stays auto as well as edited`() = runTest {
        val viewModel = viewModel(everything)

        viewModel.uiState.test {
            awaitSuccess()

            viewModel.filter(LedgerFilter.Auto)
            assertTrue("Grab" in awaitSuccess().merchants())

            viewModel.filter(LedgerFilter.Edited)
            assertTrue("Grab" in awaitSuccess().merchants())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search matches a merchant and a typed amount`() = runTest {
        val viewModel = viewModel(everything)

        viewModel.uiState.test {
            awaitSuccess()

            viewModel.search("amazon")
            assertEquals(listOf("Café Amazon"), awaitSuccess().merchants())

            viewModel.search("642")
            assertEquals(listOf("Foodland"), awaitSuccess().merchants())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** #156: a Sky row draws the time under the amount, so History hands the parts over apart. */
    @Test
    fun `a row hands over its category and time and who filed it apart`() = runTest {
        loaded(everything) { state ->
            val rows = state.days.flatMap { it.rows }
            assertEquals(
                MetaParts(category = "Groceries", time = "18:00", provenance = Provenance.Auto),
                rows.first { it.merchant == "Foodland" }.parts,
            )
            assertEquals(
                MetaParts(category = "Groceries", time = null, provenance = Provenance.ByHand),
                rows.first { it.merchant == "Café Amazon" }.parts,
            )
        }
    }

    @Test
    fun `income shows no sign and takes the payments glyph`() = runTest {
        loaded(everything) { state ->
            val row = state.days[1].rows.first { it.merchant == "Payroll" }
            assertEquals("฿32,000", row.amount)
            assertEquals("payments", row.icon)
        }
    }

    /**
     * FR-20. A ledger with nothing in it and a ledger hidden behind a chip are different facts,
     * and only the second one has a way out to offer.
     */
    @Test
    fun `an empty result says whether anything is hidden behind the filter`() = runTest {
        val viewModel = viewModel(everything)

        viewModel.uiState.test {
            awaitSuccess()

            viewModel.search("nothing matches this")
            val filtered = awaitSuccess()
            assertTrue(filtered.isEmpty)
            assertTrue(!filtered.ledgerIsEmpty, "the ledger holds rows this search hides")
            assertEquals(everything.size, filtered.hiddenByFilter)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel(emptyList()).uiState.test {
            val bare = awaitSuccess()
            assertTrue(bare.ledgerIsEmpty)
            assertEquals(0, bare.hiddenByFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row filed under a sub-category names the pair`() = runTest {
        val row = autoGroceries.copy(subCategoryId = "supermarket")
        val viewModel = LedgerViewModel(
            FakeLedger(listOf(row)),
            subCategoryUpdates = flowOf(listOf(SubCategory("supermarket", "groceries", "Supermarket"))),
        )

        viewModel.uiState.test {
            val state = awaitSuccess()
            assertEquals("Groceries › Supermarket", state.days.single().rows.single().parts.category)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the month index lists each month newest first and marks where the year changes`() = runTest {
        loaded(threeMonths) { state ->
            assertTrue(state.hasMonthIndex)
            assertEquals(listOf("Aug", "Jul", "Dec"), state.months.map { it.label })
            assertEquals(listOf("2026", null, "2025"), state.months.map { it.year })
            assertEquals("Go to August 2026", state.months[0].spoken)
            assertEquals("Go to December 2025", state.months[2].spoken)
        }
    }

    /** Two August days of two rows each sit above July: two headers and four rows. */
    @Test
    fun `a month points at its first day header past every header and row above it`() = runTest {
        loaded(threeMonths) { state ->
            assertEquals(listOf(0, 6, 8), state.months.map { it.firstItem })
            assertEquals("Aug", state.monthAt(5)?.label)
            assertEquals("Jul", state.monthAt(6)?.label)
            assertEquals("Dec", state.monthAt(100)?.label)
            assertEquals("Aug", state.monthAt(-3)?.label)
        }
    }

    @Test
    fun `the top line counts the whole span then what sits above the month in view`() = runTest {
        loaded(threeMonths) { state ->
            assertEquals(
                listOf(
                    "6 transactions over 3 months",
                    "4 newer transactions above, from 1 month",
                    "5 newer transactions above, from 2 months",
                ),
                state.months.map { it.behind },
            )
        }
    }

    @Test
    fun `one month of rows shows no month index`() = runTest {
        loaded(everything) { state ->
            assertEquals(1, state.months.size)
            assertFalse(state.hasMonthIndex)
        }
    }

    @Test
    fun `a filter that leaves one month hides the index`() = runTest {
        val viewModel = viewModel(threeMonths)

        viewModel.uiState.test {
            assertTrue(awaitSuccess().hasMonthIndex)

            viewModel.search("rent")
            assertFalse(awaitSuccess().hasMonthIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Thai months carry the Buddhist Era year`() = runTest {
        AppText.use(Language.Thai)

        loaded(threeMonths) { state ->
            assertEquals(listOf("ส.ค.", "ก.ค.", "ธ.ค."), state.months.map { it.label })
            assertEquals(listOf("2569", null, "2568"), state.months.map { it.year })
            assertEquals("ไปที่ สิงหาคม 2569", state.months[0].spoken)
        }
    }

    @Test
    fun `Burmese months count in Burmese digits`() = runTest {
        AppText.use(Language.Burmese)

        loaded(threeMonths) { state ->
            assertEquals("သြဂုတ်", state.months[0].label)
            assertEquals("၂၀၂၆", state.months[0].year)
            assertEquals("၃ လအတွင်း ၆ ခု", state.months[0].behind)
        }
    }

    private fun viewModel(rows: List<Transaction>) = LedgerViewModel(FakeLedger(rows))

    /**
     * Every assertion runs inside one subscription. Re-entering `test { }` per filter would read
     * the StateFlow's retained value before the change landed, and the assertion would pass or
     * fail on the previous filter's answer.
     */
    private suspend fun TurbineTestContext<LedgerUiState>.awaitSuccess(): LedgerUiState.Success {
        var state = awaitItem()
        while (state !is LedgerUiState.Success) state = awaitItem()
        return state
    }

    private suspend fun loaded(rows: List<Transaction>, assert: (LedgerUiState.Success) -> Unit) =
        viewModel(rows).uiState.test {
            assert(awaitSuccess())
            cancelAndIgnoreRemainingEvents()
        }

    private fun LedgerUiState.Success.merchants(): List<String> =
        days.flatMap { day -> day.rows.map { it.merchant } }

    private fun row(
        id: String,
        day: Int,
        hour: Int,
        merchant: String,
        amount: Long,
        direction: Direction = Direction.Expense,
        source: TransactionSource = TransactionSource.Scan,
        editedAt: Long? = null,
        month: Int = 8,
        year: Int = 2026,
    ) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(year, month, day, hour, 0)
            .toInstant(BangkokTime)
            .toEpochMilliseconds(),
        amount = Satang(amount),
        direction = direction,
        categoryId = "groceries",
        counterparty = merchant,
        source = source,
        editedAt = editedAt,
    )
}

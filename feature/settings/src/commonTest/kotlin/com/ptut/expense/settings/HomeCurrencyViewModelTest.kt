package com.ptut.expense.settings

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.home.FakeHomeBook
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeCurrencyViewModelTest : ViewModelTest() {

    private val yangon = HomeChoice(HomeMoney.Baht, "Asia/Yangon")

    @Test
    fun `an empty ledger takes the new home and keeps its zone and reopens Settings after the rebuild`() = runTest {
        Home.use(yangon)
        val viewModel = HomeCurrencyViewModel(FakeHomeBook())
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()

        assertEquals("MMK", Home.money.code)
        assertEquals("Asia/Yangon", Home.choice.value.zoneId)
        assertTrue(Home.reopenSettings)
        assertNull(viewModel.uiState.value.note)
    }

    @Test
    fun `a ledger with rows says so and opens the rate sheet instead of changing the home`() = runTest {
        val viewModel = HomeCurrencyViewModel(FakeHomeBook(rows = 3))
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()

        assertEquals("THB", Home.money.code)
        assertFalse(Home.reopenSettings)
        assertEquals("Your ledger already has entries, so changing this converts every amount.", viewModel.uiState.value.note)
        assertEquals("MMK", viewModel.uiState.value.converting?.code)
    }

    @Test
    fun `a slip filed between the check and the choice leaves the home and says why`() = runTest {
        val viewModel = HomeCurrencyViewModel(RacingHomeBook())
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()

        assertEquals("THB", Home.money.code)
        assertFalse(Home.reopenSettings)
        assertEquals("Your ledger already has entries, so changing this converts every amount.", viewModel.uiState.value.note)
    }

    // #47

    @Test
    fun `a ledger with rows asks for a rate and converts at it in the same zone`() = runTest {
        Home.use(yangon)
        val book = FakeHomeBook(rows = 3)
        val viewModel = HomeCurrencyViewModel(book)
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()
        viewModel.typeRate("125")

        val converting = viewModel.uiState.value.converting
        assertEquals("Change to MMK?", converting?.title)
        assertEquals("฿100.00 becomes K12,500", converting?.example)

        viewModel.confirmConvert()
        runCurrent()

        assertEquals("MMK", book.converted.single().first.money.code)
        assertEquals("Asia/Yangon", book.converted.single().first.zoneId)
        assertEquals(Rate.parse("125"), book.converted.single().second)
        assertEquals("Asia/Yangon", Home.choice.value.zoneId)
        assertTrue(Home.reopenSettings)
    }

    @Test
    fun `no rate typed says so and converts nothing`() = runTest {
        val book = FakeHomeBook(rows = 3)
        val viewModel = HomeCurrencyViewModel(book)
        runCurrent()
        viewModel.choose("MMK")
        runCurrent()

        viewModel.confirmConvert()

        assertEquals("Type the rate first", viewModel.uiState.value.converting?.problem)
        assertTrue(book.converted.isEmpty())
    }

    @Test
    fun `a convert the book refuses says so and changes nothing`() = runTest {
        val viewModel = HomeCurrencyViewModel(FakeHomeBook(rows = 3, convertFails = true))
        runCurrent()
        viewModel.choose("MMK")
        runCurrent()
        viewModel.typeRate("125")

        viewModel.confirmConvert()
        runCurrent()

        val converting = viewModel.uiState.value.converting
        assertEquals("Nothing changed. The entries could not be converted.", converting?.problem)
        assertFalse(converting?.working ?: true)
        assertFalse(Home.reopenSettings)
        assertEquals("THB", Home.money.code)
    }

    @Test
    fun `a sheet cancelled before a late failure stays closed`() = runTest {
        val book = GatedHomeBook(FakeHomeBook(rows = 3, convertFails = true))
        val viewModel = HomeCurrencyViewModel(book)
        runCurrent()
        viewModel.choose("MMK")
        runCurrent()
        viewModel.typeRate("125")
        viewModel.confirmConvert()
        runCurrent()

        viewModel.cancelConvert()
        book.gate.complete(Unit)
        runCurrent()

        assertNull(viewModel.uiState.value.converting)
        assertFalse(Home.reopenSettings)
    }

    @Test
    fun `a double tap on Change converts once`() = runTest {
        val book = FakeHomeBook(rows = 3)
        val viewModel = HomeCurrencyViewModel(book)
        runCurrent()
        viewModel.choose("MMK")
        runCurrent()
        viewModel.typeRate("125")

        viewModel.confirmConvert()
        viewModel.confirmConvert()
        runCurrent()

        assertEquals(1, book.converted.size)
    }

    @Test
    fun `a book that cannot be read on open says so`() = runTest {
        val viewModel = HomeCurrencyViewModel(BrokenHomeBook())
        runCurrent()

        assertEquals(AppText.copy.settings.didNotWork, viewModel.uiState.value.note)
    }

    @Test
    fun `a choice the book throws on says so and keeps the home`() = runTest {
        val viewModel = HomeCurrencyViewModel(BrokenHomeBook(failsOnOpen = false, failsAfter = 1))
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()

        assertEquals(AppText.copy.settings.didNotWork, viewModel.uiState.value.note)
        assertEquals("THB", Home.money.code)
        assertFalse(Home.reopenSettings)
    }

    @Test
    fun `a convert that throws frees the sheet and says nothing changed`() = runTest {
        val viewModel = HomeCurrencyViewModel(ThrowingConvertBook())
        runCurrent()
        viewModel.choose("MMK")
        runCurrent()
        viewModel.typeRate("125")

        viewModel.confirmConvert()
        runCurrent()

        val converting = viewModel.uiState.value.converting
        assertEquals("Nothing changed. The entries could not be converted.", converting?.problem)
        assertFalse(converting?.working ?: true)
    }

    /** A book whose reads throw, as a closed database would; [failsAfter] lets the first reads through. */
    private class BrokenHomeBook(
        private val failsOnOpen: Boolean = true,
        private val failsAfter: Int = 0,
        private val inner: FakeHomeBook = FakeHomeBook(),
    ) : HomeBook by inner {
        private var reads = 0

        override suspend fun isEmpty(): Boolean {
            reads++
            check(!failsOnOpen && reads <= failsAfter) { "database closed" }
            return inner.isEmpty()
        }
    }

    private class ThrowingConvertBook(private val inner: FakeHomeBook = FakeHomeBook(rows = 3)) : HomeBook by inner {
        override suspend fun convert(choice: HomeChoice, rate: Rate): Boolean = error("database closed")
    }

    /** A book seen empty whose ledger gains a row before the choice lands, so the choice is refused. */
    private class RacingHomeBook(private val inner: FakeHomeBook = FakeHomeBook()) : HomeBook by inner {
        override suspend fun isEmpty(): Boolean = true

        override suspend fun choose(choice: HomeChoice): Boolean {
            inner.rows = 1
            return inner.choose(choice)
        }
    }

    /** A book whose convert waits on [gate], so the sheet can be cancelled while it runs. */
    private class GatedHomeBook(private val inner: FakeHomeBook) : HomeBook by inner {
        val gate = CompletableDeferred<Unit>()

        override suspend fun convert(choice: HomeChoice, rate: Rate): Boolean {
            gate.await()
            return inner.convert(choice, rate)
        }
    }
}

package com.ptut.expense.settings

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.home.FakeHomeBook
import com.ptut.expense.domain.shared.Rate
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeCurrencyViewModelTest : ViewModelTest() {

    @AfterTest
    fun backToBaht() {
        Home.use(HomeChoice.Default)
        Home.settingsReopened()
    }

    @Test
    fun `an empty ledger takes the new home and reopens Settings after the rebuild`() = runTest {
        val book = FakeHomeBook()
        val viewModel = HomeCurrencyViewModel(book, phoneZone = { "Asia/Yangon" })
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()

        assertEquals("MMK", Home.money.code)
        assertEquals("Asia/Yangon", Home.choice.value.zoneId)
        assertTrue(Home.reopenSettings)
        assertNull(viewModel.uiState.value.note)
    }

    @Test
    fun `a ledger with rows says so and keeps its home`() = runTest {
        val viewModel = HomeCurrencyViewModel(FakeHomeBook(rows = 3))
        runCurrent()

        viewModel.choose("MMK")
        runCurrent()

        assertEquals("THB", Home.money.code)
        assertFalse(Home.reopenSettings)
        assertEquals("Your ledger already has entries, so changing this converts every amount.", viewModel.uiState.value.note)
    }

    // #47

    @Test
    fun `a ledger with rows asks for a rate and converts at it`() = runTest {
        val book = FakeHomeBook(rows = 3)
        val viewModel = HomeCurrencyViewModel(book, phoneZone = { "Asia/Yangon" })
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
        assertEquals(Rate.parse("125"), book.converted.single().second)
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
}

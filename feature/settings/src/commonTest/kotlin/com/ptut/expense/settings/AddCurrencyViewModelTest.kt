package com.ptut.expense.settings

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.shared.Currencies
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddCurrencyViewModelTest : ViewModelTest() {

    @Test
    fun `nothing pinned follows the phone and says which currency that is`() = runTest {
        val viewModel = AddCurrencyViewModel(appSettings(), phoneCurrency = { Currencies.of("MMK") })

        val chosen = viewModel.uiState.value.options.single { it.chosen }
        assertNull(chosen.code)
        assertEquals("Follow this phone (MMK)", chosen.label)
    }

    @Test
    fun `a pinned currency is kept and following the phone again clears it`() = runTest {
        val settings = appSettings()
        val viewModel = AddCurrencyViewModel(settings)

        viewModel.choose("USD")
        assertEquals("USD", settings.choices.value.addCurrency)

        viewModel.choose(null)
        assertNull(settings.choices.value.addCurrency)
    }
}

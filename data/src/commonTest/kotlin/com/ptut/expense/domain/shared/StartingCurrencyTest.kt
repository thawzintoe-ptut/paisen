package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class StartingCurrencyTest {

    @Test
    fun `a phone in Myanmar starts in kyat and one in Thailand in baht`() {
        assertEquals("MMK", StartingCurrency.forCountry("mm").code)
        assertEquals("THB", StartingCurrency.forCountry("TH").code)
    }

    @Test
    fun `a euro country starts in euro`() {
        assertEquals("EUR", StartingCurrency.forCountry("de").code)
    }

    @Test
    fun `an unknown or missing country starts in baht`() {
        assertEquals("THB", StartingCurrency.forCountry("BR").code)
        assertEquals("THB", StartingCurrency.forCountry(null).code)
    }

    @Test
    fun `a rate unit reads as typed`() {
        assertEquals("1,000 K", Currencies.of("MMK").rateUnitLabel())
        assertEquals("$", Currencies.of("USD").rateUnitLabel())
    }
}

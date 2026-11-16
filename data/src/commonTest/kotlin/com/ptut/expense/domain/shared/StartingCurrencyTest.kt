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

    @Test
    fun `baht becomes kyat at kyat per baht with no fraction left over`() {
        assertEquals(1_250L, convert(1_000, Currencies.Thb, Rate.parse("125"), Currencies.of("MMK")).toMinorUnits())
        assertEquals(1_000L, convert(1_000, Currencies.of("MMK"), null, Currencies.of("MMK")).toMinorUnits())
    }

    @Test
    fun `two rates combine into one and a long product keeps nine decimals`() {
        assertEquals(Rate(4_175, 0), Rate.parse("33.4")!!.then(Rate.parse("125")!!))
        assertEquals(Rate(61_728, 9), Rate(123_456, 9).then(Rate(5, 1)))
    }
}

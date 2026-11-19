package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CurrencyTest {

    private val usd = Currencies.of("USD")
    private val jpy = Currencies.of("JPY")
    private val vnd = Currencies.of("VND")
    private val thb = Currencies.Thb

    @Test
    fun mulDivRemKeepsTheRemainder() {
        assertEquals(10L to 1L, mulDivRem(7, 3, 2))
    }

    @Test
    fun mulDivRemHoldsAProductPastALong() {
        assertEquals(Long.MAX_VALUE to 0L, mulDivRem(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE to 0L, mulDivRem(Long.MAX_VALUE, 2, 2))
        assertEquals(Long.MAX_VALUE / 3 to 4L, mulDivRem(Long.MAX_VALUE, 4, 12))
    }

    @Test
    fun mulDivRemRefusesAQuotientPastALong() {
        assertFailsWith<IllegalStateException> { mulDivRem(Long.MAX_VALUE, 2, 1) }
    }

    @Test
    fun mulDivRemRefusesANegative() {
        assertFailsWith<IllegalArgumentException> { mulDivRem(-1, 2, 1) }
    }

    @Test
    fun mulDivRoundTakesAHalfUp() {
        assertEquals(3L, mulDivRound(5, 1, 2))
        assertEquals(2L, mulDivRound(3, 1, 2))
        assertEquals(0L, mulDivRound(1, 1, 3))
    }

    @Test
    fun convertMultipliesByTheRate() {
        assertEquals(Satang(3_550), convert(100, usd, Rate(355, 1), thb))
    }

    @Test
    fun convertRoundsAHalfAwayFromZeroBothWays() {
        val half = Rate(500_000_000, Rate.MAX_SCALE)

        assertEquals(Satang(1), convert(1, usd, half, thb))
        assertEquals(Satang(-1), convert(-1, usd, half, thb))
        assertEquals(Satang(1), convert(1, jpy, Rate(5, 3), thb))
        assertEquals(Satang(-1), convert(-1, jpy, Rate(5, 3), thb))
    }

    @Test
    fun convertIntoMoreDigitsMultipliesTheRateUp() {
        assertEquals(Satang(10_500), convert(3, jpy, Rate(35, 0), thb))
    }

    @Test
    fun convertAtANineDecimalRateCanRoundToNothing() {
        assertEquals(Satang.Zero, convert(100, usd, Rate(1, Rate.MAX_SCALE), thb))
    }

    @Test
    fun convertInTheSameMoneyNeedsNoRate() {
        assertEquals(Satang(Long.MAX_VALUE), convert(Long.MAX_VALUE, thb, rate = null, to = thb))
    }

    @Test
    fun convertWithoutARateFails() {
        assertFailsWith<IllegalArgumentException> { convert(100, usd, rate = null, to = thb) }
    }

    @Test
    fun convertOfTheLargestAmountFitsOnlyWhenTheRateKeepsIt() {
        assertEquals(Satang(Long.MAX_VALUE), convert(Long.MAX_VALUE, usd, Rate(1, 0), thb))
        assertNull(convertOrNull(Long.MAX_VALUE, usd, Rate(2, 0), thb))
    }

    @Test
    fun thenMultipliesTwoRatesAndDropsTrailingZeros() {
        assertEquals(Rate(69, 5), Rate(138, 5).then(Rate(5, 1)))
        assertEquals(Rate(1, 1), Rate(2, 1).then(Rate(5, 1)))
    }

    @Test
    fun thenRoundsPastNineDecimals() {
        assertEquals(Rate(8, Rate.MAX_SCALE), Rate(15, Rate.MAX_SCALE).then(Rate(5, 1)))
    }

    @Test
    fun thenClampsAProductTooSmallToTheSmallestRate() {
        assertEquals(Rate(1, Rate.MAX_SCALE), Rate(1, Rate.MAX_SCALE).then(Rate(1, Rate.MAX_SCALE)))
    }

    @Test
    fun typedForWritesTheRatePerItsUnit() {
        assertEquals("1.38", Rate(138, 5).typedFor(1_000))
        assertEquals("35", Rate(35, 0).typedFor(1))
        assertEquals("5000", Rate(5, 0).typedFor(1_000))
        assertEquals("1.5", Rate(150, 2).typedFor(1))
        assertEquals("0.000000001", Rate(1, Rate.MAX_SCALE).typedFor(1))
    }

    @Test
    fun parseReadsARateTypedPerItsUnit() {
        assertEquals(Rate(138, 5), Rate.parse("1.38", unit = 1_000))
        assertEquals(Rate(35, 0), Rate.parse(" 35.00 "))
    }

    @Test
    fun parseRefusesWhatIsNotAPositiveRate() {
        assertNull(Rate.parse("0"))
        assertNull(Rate.parse("1.2.3"))
        assertNull(Rate.parse("-1"))
        assertNull(Rate.parse("1.0000001"))
        assertNull(Rate.parse("1".repeat(13)))
    }

    @Test
    fun parseMinorReadsTypedDigits() {
        assertEquals(1_850_000L, parseMinor("1,850,000", vnd))
        assertEquals(24_050L, parseMinor("240.5", usd))
        assertEquals(50L, parseMinor(".5", usd))
        assertEquals(9_999_999_999_999_00L, parseMinor("9".repeat(MAX_AMOUNT_WHOLE_DIGITS), usd))
    }

    @Test
    fun parseMinorRefusesWhatIsNotAnAmount() {
        assertNull(parseMinor("", usd))
        assertNull(parseMinor("240.555", usd))
        assertNull(parseMinor("12.5", jpy))
        assertNull(parseMinor("1.2.3", usd))
        assertNull(parseMinor("-5", usd))
        assertNull(parseMinor("9".repeat(MAX_AMOUNT_WHOLE_DIGITS + 1), usd))
    }

    @Test
    fun formatMinorGroupsAndSignsTheAmount() {
        assertEquals("−$240.50", formatMinor(-24_050, usd))
        assertEquals("₫1,850,000", formatMinor(1_850_000, vnd))
        assertEquals("1,000 ₫", vnd.rateUnitLabel())
    }

    @Test
    fun typedTextKeepsOnlyDigitsAndThePoint() {
        assertEquals("1240.50", usd.typedText(124_050))
        assertEquals("1850000", vnd.typedText(1_850_000))
        assertEquals("1.00", usd.typedText(-100))
    }
}

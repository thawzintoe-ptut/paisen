package com.ptut.expense.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun formatsWholeBahtWithThousandsSeparatorAndTwoDecimals() {
        assertEquals("฿4,500.00", Satang(450_000).format())
    }

    /**
     * The save toast reads `Saved ฿485 · Food & drink`. Padded satang there turn a receipt into
     * machine output; keeping them when they exist stops the line from rounding away real money.
     */
    @Test
    fun dropsTheSatangInProseOnlyWhenThereAreNone() {
        assertEquals("฿485", Satang(48_500).formatCompact())
        assertEquals("฿485.50", Satang(48_550).formatCompact())
        assertEquals("฿4,500", Satang(450_000).formatCompact())
        assertEquals("฿0", Satang.Zero.formatCompact())
    }

    @Test
    fun signsAnExpenseSoColourIsNeverTheOnlyCue() {
        assertEquals("−฿128.75", Satang(12_875).formatSigned(Direction.Expense))
    }

    @Test
    fun aNegativeNetReadsAsALossNotAGain() {
        assertEquals("−฿936.50", Satang(-93_650).formatNet())
    }

    @Test
    fun aPositiveNetIsExplicitlyPositive() {
        assertEquals("+฿4,500.00", Satang(450_000).formatNet())
    }

    @Test
    fun aZeroNetCarriesNoSign() {
        assertEquals("฿0.00", Satang(0).formatNet())
    }

    @Test
    fun addsAndSubtractsAmounts() {
        assertEquals(Satang(30_000), Satang(10_000) + Satang(20_000))
        assertEquals(Satang(-10_000), Satang(10_000) - Satang(20_000))
    }

    @Test
    fun negatesAnAmountSoASpendCanBeFlippedIntoANet() {
        assertEquals(Satang(-12_875), -Satang(12_875))
    }

    @Test
    fun ordersAmountsSoTheLargestSortsFirst() {
        assertTrue(Satang(20_000) > Satang(10_000))
        assertEquals(
            listOf(Satang(30_000), Satang(20_000), Satang(10_000)),
            listOf(Satang(10_000), Satang(30_000), Satang(20_000)).sortedDescending(),
        )
    }

    @Test
    fun sumsAnEmptyLedgerToZero() {
        assertEquals(Satang.Zero, emptyList<Satang>().sum())
    }

    @Test
    fun sumsAmountsWithoutUnwrappingThem() {
        assertEquals(Satang(60_000), listOf(Satang(10_000), Satang(20_000), Satang(30_000)).sum())
    }

    @Test
    fun aRatioIsAShareOfTheOtherAmount() {
        assertEquals(0.25f, Satang(2_500).ratioTo(Satang(10_000)))
    }

    @Test
    fun aRatioAgainstZeroIsZeroNotAnInfinity() {
        assertEquals(0f, Satang(5_000).ratioTo(Satang.Zero))
        assertEquals(0f, Satang.Zero.ratioTo(Satang.Zero))
    }

    /**
     * FR-9. The fold through `Long` is the independent answer: it stops compiling the day
     * `raw` is not an integer, so this cannot pass vacuously on a swapped type.
     */
    @Test
    fun tenThousandAmountsSumExactlyWhereADoubleDrifts() {
        val random = Random(seed = 20260817)
        val amounts = List(10_000) { Satang(random.nextLong(1L, 1_000_000L)) }

        val exact = amounts.sum()
        val throughDouble = amounts.fold(0.0) { running, amount -> running + amount.raw / 100.0 }

        assertEquals(amounts.fold(0L) { running, amount -> running + amount.raw }, exact.raw)
        assertNotEquals(exact.raw / 100.0, throughDouble)
    }

    /** A four-digit baht figure is wider than the bar it labels; ฿642 is not "0.6k". */
    @Test
    fun abbreviatesOnlyOnceThereAreThousandsToLose() {
        assertEquals("0", Satang(0).abbreviated())
        assertEquals("642", Satang(64_250).abbreviated())
        assertEquals("999", Satang(99_999).abbreviated())
        assertEquals("1k", Satang(100_000).abbreviated())
        assertEquals("1.2k", Satang(125_000).abbreviated())
        assertEquals("12.5k", Satang(1_250_000).abbreviated())
    }
}

package com.ptut.expense.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountEntryTest {

    @Test
    fun startsEmptyAndShowsAZero() {
        assertEquals("0", AmountEntry().display)
    }

    @Test
    fun typingReplacesTheLeadingZeroRatherThanAppendingToIt() {
        assertEquals("52", AmountEntry().digit('5').digit('2').display)
    }

    @Test
    fun acceptsOnlyOneDecimalPointAndStopsAtSatang() {
        val entry = AmountEntry().digit('9').decimal().digit('5').decimal().digit('0').digit('7')

        assertEquals("9.50", entry.display)
    }

    @Test
    fun convertsToSatangWithoutFloatingPointDrift() {
        assertEquals(
            com.ptut.expense.domain.Satang(93_650),
            AmountEntry().digit('9').digit('3').digit('6').decimal().digit('5').toSatang(),
        )
    }

    @Test
    fun aSuppliedAmountIsShownInFull() {
        assertEquals("642.50", AmountEntry.of(com.ptut.expense.domain.Satang(64_250)).display)
    }

    @Test
    fun theFirstDigitOnASuppliedAmountReplacesItRatherThanBeingIgnored() {
        val seeded = AmountEntry.of(com.ptut.expense.domain.Satang(64_250))

        assertEquals("7", seeded.digit('7').display)
        assertEquals("70.25", seeded.digit('7').digit('0').decimal().digit('2').digit('5').display)
    }

    @Test
    fun theDecimalKeyOnASuppliedAmountStartsAFreshEntry() {
        val seeded = AmountEntry.of(com.ptut.expense.domain.Satang(64_250))

        assertEquals("0.", seeded.decimal().display)
    }

    @Test
    fun backspaceCorrectsASuppliedAmountInPlaceRatherThanClearingIt() {
        val seeded = AmountEntry.of(com.ptut.expense.domain.Satang(64_250))

        assertEquals("642.5", seeded.backspace().display)
        assertEquals("642.56", seeded.backspace().digit('6').display)
    }

    @Test
    fun aSuppliedAmountNobodyTouchedStillReadsAsTheSameMoney() {
        assertEquals(
            com.ptut.expense.domain.Satang(64_250),
            AmountEntry.of(com.ptut.expense.domain.Satang(64_250)).toSatang(),
        )
    }
}

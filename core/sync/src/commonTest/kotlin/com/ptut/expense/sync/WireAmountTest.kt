package com.ptut.expense.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class WireAmountTest {

    @Test
    fun aBahtGroupKeepsTheKeyEveryBuildReads() {
        assertEquals("amountSatang", WireAmount.key("THB"))
    }

    @Test
    fun anyOtherMoneyWritesTheKeyOlderBuildsSkip() {
        assertEquals("amountGroup", WireAmount.key("MMK"))
        assertEquals("amountGroup", WireAmount.key("USD"))
    }
}

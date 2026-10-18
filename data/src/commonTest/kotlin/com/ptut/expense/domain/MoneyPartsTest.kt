package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyPartsTest {

    @Test
    fun splitsAnAmountSoTheDigitsCanBeSetLargerThanTheSymbolAndTheSatang() {
        assertEquals(MoneyParts("฿", "936", ".50"), Satang(93_650).formatParts())
    }

    @Test
    fun spellsAnAmountOutForAScreenReaderRatherThanLeavingItSymbolic() {
        assertEquals("936.50 baht", Satang(93_650).spoken())
    }
}

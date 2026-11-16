package com.ptut.expense.domain

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** #42: every amount is written in the home currency's symbol and digits. */
class HomeMoneyFormatTest {

    @AfterTest
    fun backToBaht() = Home.use(HomeChoice.Default)

    @Test
    fun kyatHasNoFractionAnywhere() {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))
        val amount = Satang.ofMinorUnits(29_500)

        assertEquals("K29,500", amount.format())
        assertEquals("K29,500", amount.formatCompact())
        assertEquals(MoneyParts("K", "29,500", ""), amount.formatParts())
        assertEquals(MoneyParts("K", "29,500", ""), amount.formatCompactParts())
        assertEquals("29,500", amount.formatDigits())
        assertEquals("29,500 MMK", amount.spoken())
        assertEquals("29.5k", amount.abbreviated())
    }

    @Test
    fun aSymbolOfSeveralLettersStaysWhole() {
        Home.use(HomeChoice(HomeMoney("HKD", "HK$", 2), "Asia/Hong_Kong"))

        assertEquals(MoneyParts("HK$", "123", ".45"), Satang.ofMinorUnits(12_345).formatParts())
        assertEquals("HK$123", Satang.ofMinorUnits(12_300).formatCompact())
    }

    @Test
    fun bahtIsWrittenAsBefore() {
        assertEquals("฿1,250.00", Satang.ofMinorUnits(125_000).format())
        assertEquals("1,250 baht", Satang.ofMinorUnits(125_000).spoken().replace(".00", ""))
    }
}

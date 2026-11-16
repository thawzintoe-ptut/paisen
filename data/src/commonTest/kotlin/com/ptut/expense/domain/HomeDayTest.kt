package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** #43: the ledger's days start at the home's midnight. */
class HomeDayTest {

    private val yangon = TimeZone.of("Asia/Yangon")

    /** 23:45 in Yangon is already 00:15 the next day in Bangkok. */
    private val lateInYangon = LocalDateTime(2026, 11, 16, 23, 45).toInstant(yangon).toEpochMilliseconds()

    @AfterTest
    fun backToBaht() = Home.use(HomeChoice.Default)

    @Test
    fun aKyatLedgerKeepsALateYangonPaymentOnItsOwnDay() {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))

        assertEquals(LocalDate(2026, 11, 16), lateInYangon.dateIn(HomeTime))
        assertEquals(LocalDate(2026, 11, 16).startOfDayEpochMillis(yangon), LocalDate(2026, 11, 16).startOfDayEpochMillis())
    }

    @Test
    fun aBahtLedgerStillCountsBangkokDays() {
        assertEquals(LocalDate(2026, 11, 17), lateInYangon.dateIn(HomeTime))
    }
}

package com.ptut.expense.domain

import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DayLabelTest {

    @AfterTest
    fun backToEnglish() {
        AppText.use(Language.English)
    }

    @Test
    fun readsAsAShortWeekdayDayAndMonth() {
        assertEquals("Fri 14 Aug", LocalDate(2026, 8, 14).dayLabel())
    }

    @Test
    fun aThaiDayLabelUsesTheShortFormsThaiBanksPrint() {
        AppText.use(Language.Thai)

        assertEquals("จ. 16 พ.ย.", LocalDate(2026, 11, 16).dayLabel())
    }

    @Test
    fun aYearInThaiIsTheBuddhistEraYear() {
        AppText.use(Language.Thai)

        assertEquals("2569", LocalDate(2026, 11, 16).yearLabel())
    }

    @Test
    fun aYearInEnglishOrBurmeseStaysTheCommonEraYear() {
        assertEquals("2026", LocalDate(2026, 11, 16).yearLabel())
        AppText.use(Language.Burmese)
        assertEquals("၂၀၂၆", LocalDate(2026, 11, 16).yearLabel())
    }
}

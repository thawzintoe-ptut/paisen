package com.ptut.expense.ui

import com.ptut.expense.domain.backup.BackupPreview
import com.ptut.expense.domain.backup.BackupProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupWordsTest {

    @Test
    fun anUnknownValueIsNamedWithItsField() {
        assertEquals("The file holds an unknown kind: Lottery", BackupProblem.UnknownValue("kind", "Lottery").phrase())
        assertEquals("The file holds an unknown startDate: 2026-02-31", BackupProblem.UnknownValue("startDate", "2026-02-31").phrase())
        assertEquals("The file holds an unknown currency: baht", BackupProblem.UnknownValue("currency", "baht").phrase())
    }

    @Test
    fun aFileFromAnotherHomeNamesBothCurrencies() {
        assertEquals("This file counts in MMK. This phone counts in THB.", BackupProblem.OtherHome(file = "MMK", here = "THB").phrase())
    }

    @Test
    fun aNewerFormatIsNamedByItsNumber() {
        val phrase = BackupProblem.OtherVersion(99).phrase()

        assertTrue(phrase.contains("format 99"), phrase)
    }

    @Test
    fun aPreviewOfAKyatFileNamesItsHome() {
        val preview = BackupPreview(
            transactions = 1,
            oldestEpochMillis = 0L,
            groups = 0,
            categories = 0,
            subCategories = 0,
            shopRules = 0,
            homeCurrency = "MMK",
            homeZone = "Asia/Yangon",
        )

        val phrase = preview.phrase()

        assertTrue(phrase.contains("counts in MMK, Yangon days"), phrase)
    }
}

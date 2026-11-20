package com.ptut.expense.domain.backup

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.home.ChooseHomeUseCase
import com.ptut.expense.domain.home.FakeHomeBook
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ExportAndHomeUseCasesTest {

    @AfterTest
    fun backToBaht() = Home.use(HomeChoice.Default)

    @Test
    fun theExportIsNamedForTheHomeDayAndHoldsWhatTheArchiveWrites() = runTest {
        // 00:15 on 21 Nov in Bangkok, still 20 Nov in UTC.
        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-20T17:15:00Z")
        }

        val export = BuildLedgerExportUseCase(FixedArchive("{\"rows\":[]}"), GetHomeTodayUseCase(clock))()

        assertEquals(LedgerExport("ptut-ledger-2026-11-21.json", "{\"rows\":[]}"), export)
    }

    @Test
    fun aChosenHomeIsWrittenToTheBook() = runTest {
        val book = FakeHomeBook()
        val kyat = HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon")

        ChooseHomeUseCase(book)(kyat)

        assertEquals(listOf(kyat), book.chosen)
    }

    @Test
    fun noHomeWritesNothing() = runTest {
        val book = FakeHomeBook()

        ChooseHomeUseCase(book)(null)

        assertTrue(book.chosen.isEmpty())
    }

    @Test
    fun noBookLeavesTheHomeAsItWas() = runTest {
        Home.use(HomeChoice.Default)

        ChooseHomeUseCase(null)(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))

        assertEquals(HomeChoice.Default, Home.choice.value)
    }

    private class FixedArchive(private val json: String) : LedgerArchive {
        override suspend fun export(): String = json

        override suspend fun restore(json: String): MergeReport = error("Not expected in this test")
    }
}

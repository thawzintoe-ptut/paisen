package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.entity.LedgerMetaEntity
import com.ptut.expense.data.local.entity.toEntity
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.FakeImportSettingsStore
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Rate
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomHomeBookTest {

    private val kyat = HomeChoice(Currencies.of("MMK").toHomeMoney(), "Asia/Yangon")

    @Test
    fun anEmptyLedgerTakesANewHomeAndTheCopyAndTheAppFollow() = withBook { book, database, settings ->
        assertTrue(book.choose(kyat))

        assertEquals(LedgerMetaEntity(currency = "MMK", zone = "Asia/Yangon"), database.ledgerMetaDao().read())
        assertEquals("MMK", settings.choices.value.homeCurrency)
        assertEquals("MMK", Home.money.code)
    }

    @Test
    fun aLedgerWithRowsKeepsItsHome() = withBook { book, database, _ ->
        database.transactionDao().insert(TransactionDraft(Satang(48_500), Direction.Expense, "food").toEntity("t1", 0L, 0L))

        assertFalse(book.choose(kyat))
        assertEquals("THB", Home.money.code)
    }

    @Test
    fun aLedgerMadeBeforeHomeGetsItsRowFromTheCopy() = withBook(ImportChoices(homeCurrency = "MMK", homeZone = "Asia/Yangon")) { book, database, _ ->
        book.reconcile()

        assertEquals(LedgerMetaEntity(currency = "MMK", zone = "Asia/Yangon"), database.ledgerMetaDao().read())
    }

    @Test
    fun theLedgerRowWinsOverACopyThatDisagrees() = withBook { book, database, settings ->
        database.ledgerMetaDao().write(LedgerMetaEntity(currency = "MMK", zone = "Asia/Yangon"))

        book.reconcile()

        assertEquals("MMK", settings.choices.value.homeCurrency)
        assertEquals(kyat, Home.choice.value)
    }

    // #47

    @Test
    fun aBahtLedgerWithRowsMovesToKyatAndEveryRowKeepsWhatItWas() = withBook(ImportChoices(weeklyTarget = WeeklyTarget("food", Satang(200_000)))) { book, database, settings ->
        val dao = database.transactionDao()
        dao.insert(TransactionDraft(Satang(10_000), Direction.Expense, "food").toEntity("baht", 1L, 1L))
        dao.insert(TransactionDraft(Satang(24_190), Direction.Expense, "food", foreign = ForeignAmount("MMK", 29_500, Rate.parse("8.2", 1_000)!!)).toEntity("kyat", 2L, 2L))
        dao.insert(TransactionDraft(Satang(41_750), Direction.Expense, "food", foreign = ForeignAmount("USD", 1_250, Rate.parse("33.4")!!)).toEntity("dollars", 3L, 3L))

        assertTrue(book.convert(kyat, Rate.parse("125")!!))

        val rows = dao.all().associateBy { it.id }
        val baht = rows.getValue("baht")
        assertEquals(12_500L, baht.amountMinor)
        assertEquals("THB", baht.currency)
        assertEquals(10_000L, baht.originalMinor)
        val kyatRow = rows.getValue("kyat")
        assertEquals(29_500L, kyatRow.amountMinor)
        assertEquals(null, kyatRow.originalMinor)
        val dollars = rows.getValue("dollars")
        assertEquals(52_188L, dollars.amountMinor)
        assertEquals(Rate(4_175, 0), Rate(dollars.rateUnscaled!!, dollars.rateScale!!))
        assertEquals(LedgerMetaEntity(currency = "MMK", zone = "Asia/Yangon"), database.ledgerMetaDao().read())
        assertEquals(Satang(250_000), settings.choices.value.weeklyTarget?.amount)
        assertEquals(kyat, Home.choice.value)
    }

    private fun withBook(choices: ImportChoices = ImportChoices(), test: suspend (RoomHomeBook, PtutDatabase, ImportSettings) -> Unit): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        val settings = ImportSettings(FakeImportSettingsStore(choices))
        try {
            test(RoomHomeBook(database.ledgerMetaDao(), database.transactionDao(), settings), database, settings)
        } finally {
            Home.use(HomeChoice.Default)
            database.close()
        }
    }
}

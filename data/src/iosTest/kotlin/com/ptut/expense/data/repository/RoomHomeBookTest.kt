package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.entity.LedgerMetaEntity
import com.ptut.expense.data.local.entity.PlayGoalDepositEntity
import com.ptut.expense.data.local.entity.PlayGoalEntity
import com.ptut.expense.data.local.entity.toEntity
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.FakeAppSettingsStore
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Rate
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private const val NOW = 1_763_300_000_000L

class RoomHomeBookTest {

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
    }

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
    fun aLedgerMadeBeforeHomeGetsItsRowFromTheCopy() = withBook(AppChoices(homeCurrency = "MMK", homeZone = "Asia/Yangon")) { book, database, _ ->
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
    fun aBahtLedgerWithRowsMovesToKyatAndEveryRowKeepsWhatItWas() = withBook(AppChoices(weeklyTarget = WeeklyTarget("food", Satang(200_000)))) { book, database, settings ->
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

    @Test
    fun aJarsTargetAndItsDepositsMoveToTheNewMoneyWithTheRows() = withBook { book, database, _ ->
        val play = database.playDao()
        database.transactionDao().insert(TransactionDraft(Satang(10_000), Direction.Expense, "food").toEntity("baht", 1L, 1L))
        play.writeGoal(PlayGoalEntity("j1", "Trip", targetMinor = 1_000_000, currency = "THB", startDate = "2026-11-01", createdAtEpochMillis = 1))
        play.deposit(PlayGoalDepositEntity("d1", "j1", amountMinor = 50_000, date = "2026-11-02", atEpochMillis = 1, source = "Said"))

        assertTrue(book.convert(kyat, Rate.parse("125")!!))

        val goal = play.allGoals().single()
        assertEquals(1_250_000L, goal.targetMinor)
        assertEquals("MMK", goal.currency)
        assertEquals(62_500L, play.allDeposits().single().amountMinor)
    }

    @Test
    fun aConvertToTheSameMoneyIsRefusedBecauseDaysNeverMove() = withBook { book, _, _ ->
        assertFalse(book.convert(HomeChoice(HomeChoice.Default.money, "Asia/Yangon"), Rate.parse("1")!!))
        assertEquals(HomeChoice.Default, Home.choice.value)
    }

    @Test
    fun twoSlipsThatRoundToOneAmountLeaveTheLedgerAsItWas() = withBook { book, database, _ ->
        val dao = database.transactionDao()
        dao.insert(TransactionDraft(Satang(10_000), Direction.Expense, "food").toEntity("a", 1L, 1L).copy(reference = "R1"))
        dao.insert(TransactionDraft(Satang(10_001), Direction.Expense, "food").toEntity("b", 1L, 1L).copy(reference = "R1"))

        assertFalse(book.convert(HomeChoice(Currencies.of("USD").toHomeMoney(), "Asia/Bangkok"), Rate.parse("0.03")!!))

        assertEquals(setOf(10_000L, 10_001L), dao.all().map { it.amountMinor }.toSet())
        assertEquals(null, database.ledgerMetaDao().read())
        assertEquals("THB", Home.money.code)
    }

    @Test
    fun aKyatSlipRowKeepsItsSlipFigureNextToTheAmountItWasReadAt() = withBook { book, database, _ ->
        val dao = database.transactionDao()
        dao.insert(
            TransactionDraft(Satang(24_190), Direction.Expense, "food", foreign = ForeignAmount("MMK", 29_500, Rate.parse("8.2", 1_000)!!))
                .toEntity("kyat", 2L, 2L).copy(slipAmountMinor = 24_190),
        )

        assertTrue(book.convert(kyat, Rate.parse("125")!!))

        val row = dao.all().single()
        assertEquals(29_500L, row.amountMinor)
        assertEquals(29_500L, row.slipAmountMinor)
    }

    @Test
    fun aDollarRowComesBackToTheSameBahtAfterARoundTripThroughKyat() = withBook { book, database, _ ->
        val dao = database.transactionDao()
        dao.insert(TransactionDraft(Satang(41_750), Direction.Expense, "food", foreign = ForeignAmount("USD", 1_250, Rate.parse("33.4")!!)).toEntity("dollars", 3L, 3L))

        assertTrue(book.convert(kyat, Rate.parse("125")!!))
        assertTrue(book.convert(HomeChoice.Default, Rate.parse("0.008")!!))

        val row = dao.all().single()
        assertEquals(41_750L, row.amountMinor)
        assertEquals(1_250L, row.originalMinor)
        assertEquals("USD", row.currency)
    }

    @Test
    fun theTypedRateIsSavedForTheNextSlipInTheOldMoney() = withBook { book, database, _ ->
        database.transactionDao().insert(TransactionDraft(Satang(10_000), Direction.Expense, "food").toEntity("baht", 1L, 1L))

        assertTrue(book.convert(kyat, Rate.parse("125")!!))

        val saved = database.sharedDao().rate("THB", "MMK")
        assertEquals(Rate.parse("125"), saved?.let { Rate(it.rateUnscaled, it.rateScale) })
        assertEquals(NOW, saved?.updatedAtEpochMillis)
    }

    @Test
    fun aCopyLeftBehindByADeathAfterTheRowsMovedIsCorrectedAtLaunch() = withBook(AppChoices(homeCurrency = "THB", weeklyTarget = WeeklyTarget("food", Satang(200_000)))) { book, database, settings ->
        database.ledgerMetaDao().write(LedgerMetaEntity(currency = "MMK", zone = "Asia/Yangon"))

        book.reconcile()

        assertEquals(kyat, Home.choice.value)
        assertEquals("MMK", settings.choices.value.homeCurrency)
        assertEquals(null, settings.choices.value.weeklyTarget)
    }

    @Test
    fun aRestoredFilesHomeIsAdoptedRowCopyAndApp() = withBook { book, database, settings ->
        database.transactionDao().insert(TransactionDraft(Satang(29_500), Direction.Expense, "food").toEntity("kyat", 1L, 1L))

        book.adopt(kyat)

        assertEquals(LedgerMetaEntity(currency = "MMK", zone = "Asia/Yangon"), database.ledgerMetaDao().read())
        assertEquals("MMK", settings.choices.value.homeCurrency)
        assertEquals(kyat, Home.choice.value)
    }

    private fun withBook(choices: AppChoices = AppChoices(), test: suspend (RoomHomeBook, PtutDatabase, AppSettings) -> Unit): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        val settings = AppSettings(FakeAppSettingsStore(choices))
        try {
            test(RoomHomeBook(database.ledgerMetaDao(), database.transactionDao(), settings, rates = database.sharedDao(), clock = FixedClock), database, settings)
        } finally {
            Home.use(HomeChoice.Default)
            database.close()
        }
    }
}

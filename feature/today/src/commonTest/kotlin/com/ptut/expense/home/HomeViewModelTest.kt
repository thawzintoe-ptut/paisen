package com.ptut.expense.home

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportFailureKind
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.ImportStopReason
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.Provenance
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.format
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.ObserveImportRunsUseCase
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.DayMark
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.Goal
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.ObserveTodayPlayUseCase
import com.ptut.expense.domain.play.PlayDay
import com.ptut.expense.domain.play.PlayGate
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.today.GetImportHealthUseCase
import com.ptut.expense.domain.today.HomePeriod
import com.ptut.expense.domain.today.ObserveScanningUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.BurmeseCopy
import com.ptut.expense.locale.EnglishCopy
import com.ptut.expense.locale.NoteTone
import com.ptut.expense.locale.ThaiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class HomeViewModelTest : ViewModelTest() {

    @Test
    fun showsTodaysNetAndTheDaysTransactions() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                transaction(Satang(450_000), Direction.Income),
                transaction(Satang(12_875), Direction.Expense),
            ),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(Satang(437_125), state.summary.net)
            assertEquals(2, state.transactions.size)
        }
    }

    /**
     * A slip confirmed today from 12 Aug belongs to 12 Aug's totals, so it is shown and not
     * counted. Before this, Home said nothing at all about a save the reader had just made.
     */
    @Test
    fun aSlipSavedTodayFromAnEarlierDayIsShownWithItsDayButNotCounted() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = listOf(saved("lawson", occurred = at(2026, 8, 12, 19, 20), filed = at(2026, 8, 14, 11, 0))),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(listOf("lawson"), state.savedToday.map { it.id })
            assertEquals("Wed 12 Aug · Food & drink", state.savedToday.single().meta)
            assertEquals(Satang.Zero, state.summary.expense)
        }
    }

    @Test
    fun savedTodayShowsTheNewestThreeAndCountsTheRest() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = (1..5).map { saved("s$it", occurred = at(2026, 8, 1, 9, 0), filed = at(2026, 8, 14, 9, it)) },
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(listOf("s5", "s4", "s3"), state.savedToday.map { it.id })
            assertEquals(2, state.savedTodayMore)
        }
    }

    @Test
    fun flagsTheTotalWhenTheImporterHasNotRunForDays() = runTest {
        val fourDays = 4L * 24 * 60 * 60 * 1000
        val viewModel = viewModel(transactions = emptyList(), sinceLastImportMillis = fourDays)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Broken("4 days"), state.importStatus)
        }
    }

    @Test
    fun aFreshInstallSaysNoImportHasRunRatherThanNamingASpan() = runTest {
        val viewModel = viewModel(transactions = emptyList(), sinceLastImportMillis = null)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.NeverRun, state.importStatus)
        }
    }

    @Test
    fun offersTheFixThatMatchesWhichWayTheImporterFailed() = runTest {
        val fourDays = 4L * 24 * 60 * 60 * 1000

        assertEquals(ImportFix.GrantPhotoAccess, importFixFor(sinceLastImportMillis = null))
        assertEquals(ImportFix.ResumeWatching, importFixFor(sinceLastImportMillis = fourDays))
    }

    @Test
    fun offersNothingWhileAQuietStretchIsStillPlausiblyHealthy() = runTest {
        val twoDays = 2L * 24 * 60 * 60 * 1000

        assertNull(importFixFor(sinceLastImportMillis = 0L))
        assertNull(importFixFor(sinceLastImportMillis = twoDays))
    }

    @Test
    fun aScanThatFailedWarnsAndOffersTheFixWhileTheLastImportIsStillRecent() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Running), scan(ImportOutcome.Failure), scan(ImportOutcome.Success)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.Other(EnglishCopy.slips.importerStopped)), state.importStatus)
            assertEquals(ImportFix.AllowAllPhotos, state.importFix)
        }
    }

    @Test
    fun photoAccessTurnedOffIsNamedAndOffersTheSystemPage() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure, reason = EnglishCopy.folders.androidAccessDenied)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.PhotoAccessOff), state.importStatus)
            assertEquals("${EnglishCopy.home.stopAccessOff}\n${EnglishCopy.home.stillCounts}", (state.importStatus as ImportStatusUiModel.Failing).body)
            assertEquals(ImportFix.AllowAllPhotos, state.importFix)
        }
    }

    @Test
    fun aStopWrittenInAnotherLanguageIsStillRecognised() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure, reason = BurmeseCopy.folders.iosAccessDenied)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.PhotoAccessOff), (awaitItem() as HomeUiState.Success).importStatus)
        }
    }

    @Test
    fun aJournalledKindIsReadWithoutMatchingTheSentence() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure, reason = "worded in a later release", kind = ImportFailureKind.LibraryNoAnswer)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.LibraryNoAnswer), (awaitItem() as HomeUiState.Success).importStatus)
        }
    }

    @Test
    fun anOtherKindKeepsItsSentenceEvenWhenItReadsLikeAccessOff() = runTest {
        val said = EnglishCopy.folders.androidAccessDenied
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure, reason = said, kind = ImportFailureKind.Other)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.Other(said)), (awaitItem() as HomeUiState.Success).importStatus)
        }
    }

    @Test
    fun aLibraryThatDidNotAnswerOffersNoFixSinceTheNextScanAsksAgain() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure, reason = ThaiCopy.folders.libraryNoAnswer)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.LibraryNoAnswer), state.importStatus)
            assertNull(state.importFix)
        }
    }

    @Test
    fun anUnknownStopKeepsTheJournalsWords() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure, reason = "the folder could not be read")),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.Other("the folder could not be read")), state.importStatus)
            assertEquals("The folder could not be read\n${EnglishCopy.home.stillCounts}", (state.importStatus as ImportStatusUiModel.Failing).body)
            assertEquals(ImportFix.AllowAllPhotos, state.importFix)
        }
    }

    @Test
    fun aRunningScanShowsPlaceholderRowsAboveTheDaysRows() = runTest {
        val viewModel = viewModel(
            transactions = listOf(transaction(Satang(12_875), Direction.Expense)),
            scanning = flowOf(true),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(2, state.placeholderRows)
            assertEquals(Satang(12_875), state.summary.expense)
        }
    }

    @Test
    fun placeholdersGoWhenTheScanEnds() = runTest {
        val scanning = MutableStateFlow(true)
        val viewModel = viewModel(transactions = listOf(transaction(Satang(12_875), Direction.Expense)), scanning = scanning)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            assertEquals(2, (awaitItem() as HomeUiState.Success).placeholderRows)

            scanning.value = false

            assertEquals(0, (awaitItem() as HomeUiState.Success).placeholderRows)
        }
    }

    @Test
    fun anEmptyDayKeepsItsEmptyStateWhileAScanRuns() = runTest {
        val viewModel = viewModel(transactions = emptyList(), scanning = flowOf(true))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertTrue(state.isScanning)
            assertEquals(0, state.placeholderRows)
        }
    }

    @Test
    fun aSuccessAfterAFailedScanClearsTheWarning() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Success), scan(ImportOutcome.Failure)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Fresh("5 min"), state.importStatus)
            assertNull(state.importFix)
        }
    }

    @Test
    fun onlyPickedPhotosWarnsThatNewSlipsStayHiddenAndOffersTheFix() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Success)),
            onlyPickedPhotos = { true },
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.OnlyPicked, state.importStatus)
            assertEquals(ImportFix.AllowAllPhotos, state.importFix)
        }
    }

    @Test
    fun mondayShowsLastWeekBesideAUsualWeekAndOpensTheCategoryThatRose() = runTest {
        val viewModel = viewModel(transactions = emptyList(), saved = weekOfSpending(), now = MONDAY_NOON, weeklyCheckIn = true)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val card = assertNotNull((awaitItem() as HomeUiState.Success).weeklyCard)
            assertTrue(card.line.startsWith("Last week came to ฿500, ฿300 more than a usual week."), card.line)
            assertTrue(card.line.contains("mostly at LAWSON"), card.line)
            assertEquals("2026-11-09", card.from)
            assertEquals("2026-11-15", card.to)
            assertEquals("food", card.categoryId)
            assertEquals("trending_up", card.glyph)
        }
    }

    @Test
    fun theWeekTabShowsTheTargetLineInPlainWords() = runTest {
        val wednesday = LocalDateTime(2026, 11, 18, 12, 0).toInstant(BangkokTime)
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = listOf(saved("t1", at(2026, 11, 16, 12, 0), filed = 0L).copy(amount = Satang(190_000))),
            now = wednesday,
            target = WeeklyTarget("food", Satang(250_000)),
        )
        viewModel.show(HomePeriod.Week)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state !is HomeUiState.Success || state.period != HomePeriod.Week) state = awaitItem()
            val target = state.target
            assertEquals("Food & drink: ฿1,900 of ฿2,500 this week", target?.line)
            assertEquals(0.76f, target?.fraction)
        }
    }

    @Test
    fun mondaysCardSaysWhetherLastWeekStayedUnderTheTarget() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = weekOfSpending(),
            now = MONDAY_NOON,
            weeklyCheckIn = true,
            target = WeeklyTarget("food", Satang(40_000)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val line = assertNotNull((awaitItem() as HomeUiState.Success).weeklyCard).line
            assertTrue(line.endsWith("Food & drink went ฿100 past its ฿400 target."), line)
        }
    }

    @Test
    fun thePlainNoteNamesTheRiseAndOffersThatsNotRight() = runTest {
        val viewModel = viewModel(transactions = emptyList(), saved = weekOfSpending(), now = MONDAY_NOON, weeklyCheckIn = true, note = WeeklyNoteSettings(on = true))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val card = assertNotNull((awaitItem() as HomeUiState.Success).weeklyCard)
            assertTrue(card.line.endsWith("Food & drink rose ฿300, mostly at LAWSON."), card.line)
            assertTrue(card.showsNotRight)
        }
    }

    @Test
    fun thePlayfulNoteSaysTheSameNumbersInItsOwnWords() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = weekOfSpending(),
            now = MONDAY_NOON,
            weeklyCheckIn = true,
            note = WeeklyNoteSettings(on = true, tone = NoteTone.Playful),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val line = assertNotNull((awaitItem() as HomeUiState.Success).weeklyCard).line
            assertTrue(line.endsWith("LAWSON had a good week: Food & drink ran ฿300 above usual."), line)
        }
    }

    @Test
    fun aHiddenWeekKeepsTheCardButDropsTheNote() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = weekOfSpending(),
            now = MONDAY_NOON,
            weeklyCheckIn = true,
            note = WeeklyNoteSettings(on = true, hiddenWeek = "2026-11-16"),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val card = assertNotNull((awaitItem() as HomeUiState.Success).weeklyCard)
            assertFalse(card.showsNotRight)
        }
    }

    @Test
    fun tuesdayShowsNoWeeklyCard() = runTest {
        val tuesday = LocalDateTime(2026, 11, 17, 12, 0).toInstant(BangkokTime)
        val viewModel = viewModel(transactions = emptyList(), saved = weekOfSpending(), now = tuesday, weeklyCheckIn = true)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            assertNull((awaitItem() as HomeUiState.Success).weeklyCard)
        }
    }

    @Test
    fun theWeeklyCardStaysAwayWithItsSwitchOff() = runTest {
        val viewModel = viewModel(transactions = emptyList(), saved = weekOfSpending(), now = MONDAY_NOON, weeklyCheckIn = false)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            assertNull((awaitItem() as HomeUiState.Success).weeklyCard)
        }
    }

    @Test
    fun aFailedScanOutranksOnlyPickedPhotos() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Failure)),
            onlyPickedPhotos = { true },
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            assertEquals(ImportStatusUiModel.Failing(ImportStopReason.Other(EnglishCopy.slips.importerStopped)), (awaitItem() as HomeUiState.Success).importStatus)
        }
    }

    @Test
    fun allowingAllPhotosClearsThePickedWarningWhenTodayComesBack() = runTest {
        var picked = true
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Success)),
            onlyPickedPhotos = { picked },
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            assertEquals(ImportStatusUiModel.OnlyPicked, (awaitItem() as HomeUiState.Success).importStatus)

            picked = false
            viewModel.refreshClock()

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Fresh("5 min"), state.importStatus)
            assertNull(state.importFix)
        }
    }

    private fun scan(outcome: ImportOutcome, reason: String? = null, kind: ImportFailureKind? = null) = ImportRun(
        id = outcome.name,
        startedAtEpochMillis = 0L,
        finishedAtEpochMillis = null,
        outcome = outcome,
        slipsFound = 0,
        failureReason = reason,
        failureKind = kind,
    )

    private suspend fun importFixFor(sinceLastImportMillis: Long?): ImportFix? {
        var fix: ImportFix? = null
        viewModel(transactions = emptyList(), sinceLastImportMillis = sinceLastImportMillis)
            .uiState.test {
                awaitItem()
                fix = (awaitItem() as HomeUiState.Success).importFix
            }
        return fix
    }

    @Test
    fun aSlipNameTheReaderCouldOnlyMakeNoiseOfShowsAsUnknown() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                Transaction(
                    id = "1",
                    occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 12, 41)
                        .toInstant(BangkokTime).toEpochMilliseconds(),
                    amount = Satang(1_000),
                    direction = Direction.Expense,
                    categoryId = "groceries",
                    counterparty = "msus:quus:óiÜangönms",
                    source = TransactionSource.Scan,
                ),
            ),
        )

        viewModel.uiState.test {
            awaitItem()
            assertEquals("Unknown shop", (awaitItem() as HomeUiState.Success).transactions.single().merchant)
        }
    }

    @Test
    fun describesEachRowWellEnoughForTheListToDrawItWithoutTheDomain() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                Transaction(
                    id = "1",
                    occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 18, 30)
                        .toInstant(BangkokTime).toEpochMilliseconds(),
                    amount = Satang(64_250),
                    direction = Direction.Expense,
                    categoryId = "groceries",
                    counterparty = "Foodland Rama IV",
                    source = TransactionSource.Scan,
                ),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val row = (awaitItem() as HomeUiState.Success).transactions.single()
            assertEquals("Foodland Rama IV", row.merchant)
            assertEquals("Groceries · 18:30 · auto", row.meta)
            assertEquals(MetaParts(category = "Groceries", time = "18:30", provenance = Provenance.Auto), row.parts)
            assertEquals("฿642.50", row.amount)
            assertEquals("local_grocery_store", row.icon)
            assertEquals(Direction.Expense, row.direction)
        }
    }

    @Test
    fun aRowFiledUnderASubCategoryNamesThePairOnTheListAndInSavedToday() = runTest {
        val grocery = saved("bigc", occurred = at(2026, 8, 14, 18, 30), filed = at(2026, 8, 14, 18, 31))
            .copy(categoryId = "groceries", subCategoryId = "supermarket")
        val earlier = saved("lawson", occurred = at(2026, 8, 12, 19, 20), filed = at(2026, 8, 14, 11, 0))
            .copy(categoryId = "groceries", subCategoryId = "supermarket")
        val viewModel = viewModel(
            transactions = listOf(grocery),
            saved = listOf(earlier),
            subCategories = flowOf(listOf(Supermarket)),
        )

        viewModel.uiState.test {
            awaitItem()

            val state = awaitItem() as HomeUiState.Success
            assertEquals("Groceries › Supermarket", state.transactions.single().parts.category)
            assertEquals("Wed 12 Aug · Groceries › Supermarket", state.savedToday.single().meta)
        }
    }

    /** #115's rule for sub-categories: Home keeps its holder, so a rename has to arrive live. */
    @Test
    fun aRenamedSubCategoryReachesTheOpenScreen() = runTest {
        val grocery = saved("bigc", occurred = at(2026, 8, 14, 18, 30), filed = at(2026, 8, 14, 18, 31))
            .copy(categoryId = "groceries", subCategoryId = "supermarket")
        val subCategories = MutableStateFlow(listOf(Supermarket))
        val viewModel = viewModel(transactions = listOf(grocery), subCategories = subCategories)

        viewModel.uiState.test {
            awaitItem()
            assertEquals("Groceries › Supermarket", (awaitItem() as HomeUiState.Success).transactions.single().parts.category)

            subCategories.value = listOf(Supermarket.copy(name = "Big C"))

            assertEquals("Groceries › Big C", (awaitItem() as HomeUiState.Success).transactions.single().parts.category)
        }
    }

    @Test
    fun namesTheBangkokDayThatTheHeroTotalBelongsTo() = runTest {
        val lateOnTheFourteenth = LocalDateTime(2026, 8, 14, 23, 30).toInstant(BangkokTime)
        val viewModel = viewModel(transactions = emptyList(), now = lateOnTheFourteenth)

        viewModel.uiState.test {
            awaitItem()

            val state = awaitItem() as HomeUiState.Success
            assertEquals("Spent today · Fri 14 Aug", state.heroLabel)
        }
    }

    @Test
    fun offersAWayIntoTheQueueThatSaysHowManySlipsAndWhy() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            queued = listOf(
                slip("q1", "MK Restaurant Rama 9", ReviewReason.NewMerchant),
                slip("q2", "PTT Station Phra Ram 2", ReviewReason.AmountUnclear),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val review = (awaitItem() as HomeUiState.Success).review
            assertEquals("2 slips to check", review?.title)
            assertEquals("One new shop, one unreadable amount", review?.detail)
            assertEquals("Check 2", review?.action)
        }
    }

    /**
     * Three rows with nothing in the merchant column, meaning different things. A slip whose name
     * the reader missed really is unknown; a row somebody typed never had one, so its category names it (#210).
     */
    @Test
    fun aRowWithNoMerchantSaysWhichKindOfNothingItIs() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                nameless("scanned", Direction.Expense, TransactionSource.Scan),
                nameless("cash", Direction.Expense, TransactionSource.Manual).copy(categoryId = "food"),
                nameless("salary", Direction.Income, TransactionSource.Manual).copy(categoryId = "income"),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val rows = (awaitItem() as HomeUiState.Success).transactions
                .associate { it.id to it.merchant }

            assertEquals("Unknown shop", rows["scanned"])
            assertEquals("Food & drink", rows["cash"])
            assertEquals("Income", rows["salary"])
        }
    }

    /**
     * Today read the category off the row while the Ledger special-cased income, so one restored
     * income row drew two different glyphs depending on which screen you opened it from.
     */
    @Test
    fun anIncomeRowKeepsThePaymentsGlyphWhereSpendingFallsBackToUncategorised() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                nameless("salary", Direction.Income, TransactionSource.Manual),
                nameless("cash", Direction.Expense, TransactionSource.Manual),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val icons = (awaitItem() as HomeUiState.Success).transactions
                .associate { it.id to it.icon }

            assertEquals("payments", icons["salary"])
            assertEquals("more_horiz", icons["cash"])
        }
    }

    private fun nameless(id: String, direction: Direction, source: TransactionSource) = Transaction(
        id = id,
        occurredAtEpochMillis = 0L,
        amount = Satang(48_500),
        direction = direction,
        categoryId = null,
        counterparty = null,
        source = source,
    )

    @Test
    fun aDayWhoseOnlyEntriesAreQueuedSlipsShowsTheEmptyStateNotZero() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            queued = listOf(
                slip("q1", "LAWSON108_P3793 THE LIVIN", ReviewReason.AmountUnclear),
                slip("q2", "SHOPEEPAY", ReviewReason.NewMerchant),
            ),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            val state = awaitItem() as HomeUiState.Success

            // FR-20: the day has no rows, so the screen says so. A queued slip is money the app
            // refused to count, and rendering it as a zero would report a measurement it never took.
            assertEquals(emptyList(), state.transactions)
            assertEquals(0, state.summary.count)
            // The queue is still offered — the reader has something to do, just not a total to read.
            assertEquals("2 slips to check", state.review?.title)
        }
    }

    private fun slip(id: String, merchant: String, reason: ReviewReason) = QueuedSlip(
        id = id,
        merchant = merchant,
        occurredAtEpochMillis = 0L,
        note = "ref 0142K9",
        amount = if (reason == ReviewReason.AmountUnclear) null else Satang(48_500),
        reason = reason,
        capturedAtEpochMillis = 0L,
    )

    @Test
    fun theMorningCardRecapsYesterdaysSpendingAndOpensItsReceipt() = runTest {
        val yesterday = listOf(
            spent("y1", at(2026, 8, 13, 9, 0), Satang(15_500)),
            spent("y2", at(2026, 8, 13, 20, 0), Satang(15_500)),
            spent("y3", at(2026, 8, 13, 12, 0), Satang(900_000)).copy(direction = Direction.Income),
        )
        val viewModel = viewModel(transactions = emptyList(), saved = yesterday, now = bangkok(2026, 8, 14, 8), mood = true)

        viewModel.uiState.test {
            val card = assertNotNull(awaitSuccess().moodCard)
            assertEquals(EnglishCopy.home.goodMorning, card.lead)
            assertEquals(EnglishCopy.home.yesterdayCameTo("฿310", ""), card.line)
            assertEquals("wb_twilight", card.glyph)
            assertEquals("2026-08-13", card.receiptDate)
        }
    }

    @Test
    fun theEveningCardWrapsTodaysPurchases() = runTest {
        val today = listOf(spent("e1", at(2026, 8, 14, 9, 0), Satang(15_500)), spent("e2", at(2026, 8, 14, 13, 0), Satang(15_500)))
        val viewModel = viewModel(transactions = today, now = bangkok(2026, 8, 14, 19), mood = true)

        viewModel.uiState.test {
            val card = assertNotNull(awaitSuccess().moodCard)
            assertEquals(EnglishCopy.home.dayWrapped, card.lead)
            assertEquals(EnglishCopy.home.wrapLine(2, Satang(31_000).format()), card.line)
            assertEquals("2026-08-14", card.receiptDate)
        }
    }

    @Test
    fun aMorningAfterADayWithNoSpendingHasNoMoodCard() = runTest {
        val viewModel = viewModel(transactions = emptyList(), now = bangkok(2026, 8, 14, 8), mood = true)

        viewModel.uiState.test {
            assertNull(awaitSuccess().moodCard)
        }
    }

    @Test
    fun theComparedCardRisesOverAUsualFriday() = runTest {
        val fridays = listOf(
            spent("f1", at(2026, 7, 24, 12, 0), Satang(10_000)),
            spent("f2", at(2026, 7, 31, 12, 0), Satang(10_000)),
            spent("f3", at(2026, 8, 7, 12, 0), Satang(10_000)),
        )
        val today = listOf(spent("f4", at(2026, 8, 14, 10, 0), Satang(15_500)))
        val viewModel = viewModel(transactions = today, saved = fridays)

        viewModel.uiState.test {
            val compared = assertNotNull(awaitSuccess().compared)
            assertEquals("trending_up", compared.glyph)
        }
    }

    @Test
    fun topCategoriesAreTheBiggestSpendFirst() = runTest {
        val today = listOf(
            spent("c1", at(2026, 8, 14, 9, 0), Satang(5_000)).copy(categoryId = "transport"),
            spent("c2", at(2026, 8, 14, 10, 0), Satang(50_000)).copy(categoryId = "food"),
            spent("c3", at(2026, 8, 14, 11, 0), Satang(20_000)).copy(categoryId = "groceries"),
        )
        val viewModel = viewModel(transactions = today)

        viewModel.uiState.test {
            assertEquals(listOf("Food & drink", "Groceries", "Transport"), awaitSuccess().topCategories.map { it.name })
        }
    }

    @Test
    fun theMonthTabCountsTheBangkokMonthToTodayOnly() = runTest {
        val rows = listOf(
            spent("m1", at(2026, 8, 1, 0, 30), Satang(10_000)),
            spent("m2", at(2026, 8, 14, 9, 0), Satang(20_000)),
            spent("m3", at(2026, 7, 31, 23, 30), Satang(40_000)),
            spent("m4", at(2026, 8, 20, 9, 0), Satang(80_000)),
        )
        val viewModel = viewModel(transactions = emptyList(), saved = rows)
        viewModel.show(HomePeriod.Month)

        viewModel.uiState.test {
            var state = awaitSuccess()
            while (state.period != HomePeriod.Month) state = awaitSuccess()
            assertEquals(Satang(30_000), state.summary.expense)
            assertTrue(state.heroLabel.startsWith(EnglishCopy.home.spentThisMonth), state.heroLabel)
        }
    }

    private suspend fun TurbineTestContext<HomeUiState>.awaitSuccess(): HomeUiState.Success {
        var state = awaitItem()
        while (state !is HomeUiState.Success) state = awaitItem()
        return state
    }

    private fun spent(id: String, occurred: Long, amount: Satang) =
        saved(id, occurred, filed = 0L).copy(amount = amount)

    private fun bangkok(year: Int, month: Int, day: Int, hour: Int): Instant =
        LocalDateTime(year, month, day, hour, 0).toInstant(BangkokTime)

    private fun viewModel(
        transactions: List<Transaction>,
        sinceLastImportMillis: Long? = 0L,
        queued: List<QueuedSlip> = emptyList(),
        saved: List<Transaction> = emptyList(),
        now: Instant = LocalDateTime(2026, 8, 14, 12, 0).toInstant(BangkokTime),
        subCategories: Flow<List<SubCategory>> = flowOf(emptyList()),
        runs: List<ImportRun> = emptyList(),
        onlyPickedPhotos: () -> Boolean = { false },
        weeklyCheckIn: Boolean = false,
        target: WeeklyTarget? = null,
        note: WeeklyNoteSettings = WeeklyNoteSettings(),
        scanning: Flow<Boolean> = flowOf(false),
        mood: Boolean = false,
        play: PlayFixture? = null,
    ) = FakeLedger(transactions, sinceLastImportMillis, queued, all = transactions + saved, runs = runs).let { ledger ->
        HomeViewModel(
            observeTransactions = ObserveTransactionsUseCase(ledger),
            observeLastImport = ObserveLastImportUseCase(ledger),
            observeImportRuns = ObserveImportRunsUseCase(ledger),
            observeQueuedSlips = ObserveQueuedSlipsUseCase(ledger, flowOf(emptySet())),
            observeScanning = ObserveScanningUseCase(scanning),
            subCategoryUpdates = subCategories,
            getImportHealth = GetImportHealthUseCase(onlyPickedPhotos),
            weeklyCheckIn = flowOf(weeklyCheckIn),
            moodByTimeOfDay = flowOf(mood),
            weeklyTarget = flowOf(target),
            weeklyNote = flowOf(note),
            clock = object : Clock {
                override fun now(): Instant = now
            },
            observeTodayPlay = play?.let { fixture ->
                ObserveTodayPlayUseCase(
                    playOn = ObservePlayOnUseCase(ObserveAppChoicesUseCase(appSettings(AppChoices(play = fixture.choices))), PlayGate(flagOn = true, debug = true)),
                    book = fixture.book,
                    homeToday = GetHomeTodayUseCase(
                        object : Clock {
                            override fun now(): Instant = now
                        },
                    ),
                    transactions = ObserveTransactionsUseCase(ledger),
                    work = Dispatchers.Unconfined,
                )
            },
        )
    }

    /** #85: what play holds for a test, and the reader's switches. */
    private class PlayFixture(val book: FakePlayBook = FakePlayBook(), val choices: PlayChoices = PlayChoices())

    private fun cleared(year: Int, month: Int, day: Int) = PlayDay(LocalDate(year, month, day), clearedAtEpochMillis = 1L)

    // #102: first run ended Mon 10 Aug, so Fri 14 Aug is day 5 and Fri 7 Aug's start makes it day 8.

    private val jar = Goal(id = "g1", name = "A trip", target = Satang(500_000), currency = "THB", start = LocalDate(2026, 8, 10), createdAtEpochMillis = 1L)

    @Test
    fun theFirstWeekHidesGamesAndStampsAndSaysWhenTheyJoin() = runTest {
        val book = FakePlayBook(days = listOf(cleared(2026, 8, 12), cleared(2026, 8, 13)), goals = listOf(jar))
        val viewModel = viewModel(transactions = emptyList(), play = PlayFixture(book, PlayChoices(welcomedOn = "2026-08-10")))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            val state = awaitItem() as HomeUiState.Success
            assertNull(state.play)
            assertNull(state.question)
            assertNull(state.challenge)
            assertEquals(AppText.copy.play.extras.firstWeek, state.firstWeekLine)
            assertEquals(listOf(ShelfEntry.Jar), state.shelf.map { it.entry })
        }
    }

    @Test
    fun dayEightBringsGamesAndStampsBack() = runTest {
        val book = FakePlayBook(days = listOf(cleared(2026, 8, 12), cleared(2026, 8, 13)))
        val viewModel = viewModel(transactions = emptyList(), play = PlayFixture(book, PlayChoices(welcomedOn = "2026-08-07")))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            val state = awaitItem() as HomeUiState.Success
            assertNotNull(state.play)
            assertNull(state.firstWeekLine)
        }
    }

    @Test
    fun theRunCardShowsTheRunTheWeekAndTheNextStep() = runTest {
        // Friday 14 Aug: Wednesday and Thursday cleared, two slips waiting today.
        val book = FakePlayBook(days = listOf(cleared(2026, 8, 12), cleared(2026, 8, 13)))
        val viewModel = viewModel(
            transactions = emptyList(),
            queued = listOf(slip("q1", "Cafe", ReviewReason.NewMerchant), slip("q2", "Shop", ReviewReason.NewMerchant)),
            play = PlayFixture(book),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            val strip = assertNotNull((awaitItem() as HomeUiState.Success).play)
            assertEquals("2 days", strip.runTitle)
            assertEquals("Check 2 slips to make it 3 and turn over a stamp.", strip.nextLine)
            assertEquals(listOf(DayMark.Missed, DayMark.Missed, DayMark.Cleared, DayMark.Cleared, DayMark.Today, DayMark.Ahead, DayMark.Ahead), strip.dots.map { it.mark })
            assertEquals(2, strip.dots[4].waiting)
            assertNull(strip.freezes)
        }
    }

    @Test
    fun aClearedTodayOffersItsFaceDownStamp() = runTest {
        val book = FakePlayBook()
        book.markCleared(LocalDate(2026, 8, 14), atEpochMillis = 1L, stamp = StampKind.Friday)
        val viewModel = viewModel(transactions = emptyList(), play = PlayFixture(book))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            val strip = assertNotNull((awaitItem() as HomeUiState.Success).play)
            assertEquals("Friday:2026-08-14", strip.stampId)
            assertEquals("Tray clear today. A stamp is waiting.", strip.nextLine)
        }
    }

    @Test
    fun theCoachLineTakesComparedsPlaceAndPlayOffGivesComparedBack() = runTest {
        val fridays = listOf(
            spent("f1", at(2026, 7, 24, 12, 0), Satang(10_000)),
            spent("f2", at(2026, 7, 31, 12, 0), Satang(10_000)),
            spent("f3", at(2026, 8, 7, 12, 0), Satang(10_000)),
        )
        val today = listOf(spent("f4", at(2026, 8, 14, 10, 0), Satang(50_000)))

        viewModel(transactions = today, saved = fridays, play = PlayFixture()).uiState.test {
            val state = awaitSuccess()
            assertEquals("Food & drink: ฿400 more than a usual Friday", state.coach?.text)
            assertEquals("food", state.coach?.categoryId)
            assertNull(state.compared)
        }
        viewModel(transactions = today, saved = fridays, play = PlayFixture(choices = PlayChoices(on = false))).uiState.test {
            val state = awaitSuccess()
            assertNull(state.coach)
            assertNotNull(state.compared)
        }
    }

    @Test
    fun theWeeklyCardOpensLastWeeksGuessFirst() = runTest {
        // Monday 10 Aug: the card is about the week of 3 Aug, with the weeks of 20 and 27 Jul behind it.
        val earlier = listOf(
            spent("w1", at(2026, 7, 21, 12, 0), Satang(100_000)),
            spent("w2", at(2026, 7, 28, 12, 0), Satang(120_000)),
            spent("w3", at(2026, 8, 4, 12, 0), Satang(110_000)),
        )

        viewModel(transactions = emptyList(), saved = earlier, weeklyCheckIn = true, now = bangkok(2026, 8, 10, 12), play = PlayFixture()).uiState.test {
            assertEquals("2026-08-03", assertNotNull(awaitSuccess().weeklyCard).guessMonday)
        }
    }

    @Test
    fun aLedgerWithNoIncomeShowsNoIncomeOrNet() = runTest {
        viewModel(transactions = listOf(transaction(Satang(12_875), Direction.Expense))).uiState.test {
            assertNull(awaitSuccess().incomeLine)
        }
    }

    @Test
    fun incomeAnywhereInTheLedgerShowsTheNetWithItsSign() = runTest {
        viewModel(
            transactions = listOf(transaction(Satang(450_000), Direction.Income), transaction(Satang(12_875), Direction.Expense)),
        ).uiState.test {
            assertEquals(IncomeLineUiModel(income = "฿4,500.00", net = "+฿4,371.25", netBelowZero = false), awaitSuccess().incomeLine)
        }
        // An older income still counts; today's net is then below zero, the one case drawn in ruby.
        viewModel(
            transactions = listOf(transaction(Satang(12_875), Direction.Expense)),
            saved = listOf(spent("salary", at(2026, 8, 1, 9, 0), Satang(900_000)).copy(direction = Direction.Income)),
        ).uiState.test {
            assertEquals(IncomeLineUiModel(income = "฿0.00", net = "−฿128.75", netBelowZero = true), awaitSuccess().incomeLine)
        }
    }

    @Test
    fun todayShowsOneInsightWhenTheWeeklyNoteCoachAndComparedAllHaveOne() = runTest {
        val mondays = listOf(
            spent("m1", at(2026, 10, 26, 12, 0), Satang(10_000)),
            spent("m2", at(2026, 11, 2, 12, 0), Satang(10_000)),
            spent("m3", at(2026, 11, 9, 12, 0), Satang(10_000)),
        )
        val today = listOf(spent("m4", at(2026, 11, 16, 10, 0), Satang(50_000)))

        viewModel(transactions = today, saved = weekOfSpending() + mondays, now = MONDAY_NOON, weeklyCheckIn = true, play = PlayFixture()).uiState.test {
            val state = awaitSuccess()
            assertNotNull(state.weeklyCard)
            assertNull(state.coach)
            assertNull(state.compared)
        }
        viewModel(transactions = today, saved = weekOfSpending() + mondays, now = MONDAY_NOON, play = PlayFixture()).uiState.test {
            assertNotNull(awaitSuccess().coach)
        }
    }

    @Test
    fun theRunSwitchedOffDrawsNoRunCard() = runTest {
        val viewModel = viewModel(transactions = emptyList(), play = PlayFixture(choices = PlayChoices(run = false)))

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            assertNull((awaitItem() as HomeUiState.Success).play)
        }
    }

    private fun transaction(amount: Satang, direction: Direction) = Transaction(
        id = "t${amount.raw}$direction",
        occurredAtEpochMillis = 0L,
        amount = amount,
        direction = direction,
        categoryId = null,
        counterparty = null,
    )

    private fun saved(id: String, occurred: Long, filed: Long) = Transaction(
        id = id,
        occurredAtEpochMillis = occurred,
        amount = Satang(15_500),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "LAWSON108_P3793 THE LIVIN",
        source = TransactionSource.Scan,
        filedAt = filed,
    )

    /** ฿200 in each of the two weeks before last, then ฿500 last week, most of it at one shop. */
    private fun weekOfSpending() = listOf(
        saved("w1", at(2026, 10, 27, 12, 0), filed = 0L).copy(amount = Satang(20_000)),
        saved("w2", at(2026, 11, 4, 12, 0), filed = 0L).copy(amount = Satang(20_000)),
        saved("w3", at(2026, 11, 10, 12, 0), filed = 0L).copy(amount = Satang(50_000), counterparty = "LAWSON"),
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(BangkokTime).toEpochMilliseconds()

    private class FakeLedger(
        transactions: List<Transaction>,
        sinceLastImportMillis: Long?,
        queued: List<QueuedSlip>,
        all: List<Transaction>,
        runs: List<ImportRun>,
    ) : LedgerRepository {
        override val todaysTransactions: StateFlow<LedgerSnapshot> =
            MutableStateFlow(LedgerSnapshot.Rows(transactions))
        override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(all))

        override val recentTransactions: StateFlow<LedgerSnapshot> =
            MutableStateFlow(LedgerSnapshot.Rows(transactions))
        override val sinceLastImportMillis: StateFlow<Long?> =
            MutableStateFlow(sinceLastImportMillis)
        override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(runs)

        override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(queued)

        /** Today is a reader. A write reaching here would mean the screen has grown a writer. */

        /** Today never deletes, so a call here is a screen reaching past its job. */
        override suspend fun delete(transactionId: String): Unit = error("Today never deletes")

        override suspend fun restore(transaction: Transaction): Unit = error("Today never deletes")

        override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
            error("Today never edits")

        override suspend fun record(draft: TransactionDraft) = error("Today never writes")
    }
}

private val Supermarket = SubCategory("supermarket", "groceries", "Supermarket")

private const val FIVE_MINUTES = 5L * 60 * 1000

private val MONDAY_NOON = LocalDateTime(2026, 11, 16, 12, 0).toInstant(BangkokTime)

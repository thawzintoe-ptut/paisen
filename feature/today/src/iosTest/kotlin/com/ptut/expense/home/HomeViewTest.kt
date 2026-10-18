package com.ptut.expense.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.domain.DEFAULT_COLOR
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.PeriodSummary
import com.ptut.expense.domain.Provenance
import com.ptut.expense.domain.Satang
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeViewTest {

    @Test
    fun anEmptyDaySaysSoRatherThanShowingAZeroThatLooksLikeAReading() = runComposeUiTest {
        setContent { Home(success(spent = Satang(0), transactions = emptyList())) }

        // p24 draws the headline under the illustration slot, past the fold of the test window.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("A quiet day so far."))
        onNodeWithText("A quiet day so far.").assertIsDisplayed()
    }

    @Test
    fun theHeroIsSpokenAsAnAmountAndTheDayItCovers() = runComposeUiTest {
        setContent { Home(success(spent = Satang(93_650))) }

        onNodeWithContentDescription("Spent today · Fri 14 Aug, 936.50 baht").assertIsDisplayed()
    }

    @Test
    fun aStaleImportIsFlaggedAboveTheTotalItMakesUnreliable() = runComposeUiTest {
        setContent {
            Home(success(spent = Satang(93_650), status = ImportStatusUiModel.Stale("2 days")))
        }

        onNodeWithText("Totals below may be incomplete").assertIsDisplayed()
    }

    /** A fresh install has no span to name, so the banner must not try to print one. */
    @Test
    fun anInstallThatHasNeverImportedSaysSoWithoutNamingASpan() = runComposeUiTest {
        setContent {
            Home(success(spent = Satang.Zero, status = ImportStatusUiModel.NeverRun))
        }

        onNodeWithText("No slips imported yet").assertIsDisplayed()
    }

    @Test
    fun anInstallThatHasNeverImportedOffersTheStepThatStartsIt() = runComposeUiTest {
        var reached: ImportFix? = null
        setContent {
            Home(
                uiState = success(
                    spent = Satang.Zero,
                    status = ImportStatusUiModel.NeverRun,
                    fix = ImportFix.GrantPhotoAccess,
                ),
                onFixImport = { reached = it },
            )
        }

        onNodeWithText("Fix").performClick()
        assertEquals(ImportFix.GrantPhotoAccess, reached)
    }

    @Test
    fun anImporterThatStoppedOffersTheFixThatResumesIt() = runComposeUiTest {
        var reached: ImportFix? = null
        setContent {
            Home(
                uiState = success(
                    spent = Satang(93_650),
                    status = ImportStatusUiModel.Broken("4 days"),
                    fix = ImportFix.ResumeWatching,
                ),
                onFixImport = { reached = it },
            )
        }

        onNodeWithText("Fix").performClick()
        assertEquals(ImportFix.ResumeWatching, reached)
    }

    /** C-5. Under three days the notice states the fact and asks for nothing. */
    @Test
    fun aQuietStretchStatesTheFactWithoutOfferingAnAction() = runComposeUiTest {
        setContent {
            Home(success(spent = Satang(93_650), status = ImportStatusUiModel.Stale("2 days")))
        }

        onAllNodesWithText("Fix").assertCountEquals(0)
    }

    /** A slip confirmed today from another day is counted on its own day, so Home shows it apart. */
    @Test
    fun aRowSavedTodayShowsUnderSavedTodayWithItsOwnDay() = runComposeUiTest {
        setContent { Home(success(spent = Satang(64_250), saved = listOf(savedRow()))) }

        onNodeWithText("Saved today").assertIsDisplayed()
        onNodeWithText("Wed 12 Aug · Food & drink").assertIsDisplayed()
    }

    @Test
    fun anEmptyDayStillShowsWhatWasSavedTodayAboveTheReviewCard() = runComposeUiTest {
        setContent {
            Home(
                success(
                    spent = Satang.Zero,
                    transactions = emptyList(),
                    review = ReviewCardUiModel(title = "17 slips need you", detail = "Six new shops"),
                    saved = listOf(savedRow()),
                ),
            )
        }

        listOf("A quiet day so far.", "17 slips need you", "Wed 12 Aug · Food & drink").forEach { text ->
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun theRowsSavedTodayBeyondThreeAreCountedAndOpenHistory() = runComposeUiTest {
        var openedLedger = false
        setContent {
            Home(
                uiState = success(spent = Satang(64_250), saved = listOf(savedRow()), savedMore = 2),
                onOpenLedger = { openedLedger = true },
            )
        }

        onNodeWithText("2 more in History").performClick()
        assertEquals(true, openedLedger)
    }

    @Composable
    private fun Home(
        uiState: HomeUiState,
        onFixImport: (ImportFix) -> Unit = {},
        onOpenTransaction: (String) -> Unit = {},
        onOpenLedger: () -> Unit = {},
    ) = HomeView(
        uiState = uiState,
        onChoosePeriod = {},
        onOpenReview = {},
        onOpenLedger = onOpenLedger,
        onAddByHand = {},
        onFixImport = onFixImport,
        onOpenTransaction = onOpenTransaction,
    )

    private fun success(
        spent: Satang,
        transactions: List<TransactionRowUiModel> = listOf(row()),
        status: ImportStatusUiModel = ImportStatusUiModel.Fresh("2 min"),
        fix: ImportFix? = null,
        review: ReviewCardUiModel? = null,
        saved: List<TransactionRowUiModel> = emptyList(),
        savedMore: Int = 0,
    ) = HomeUiState.Success(
        heroLabel = "Spent today · Fri 14 Aug",
        summary = PeriodSummary(
            income = Satang.Zero,
            expense = spent,
            net = -spent,
            count = transactions.size,
        ),
        transactions = transactions,
        importStatus = status,
        isScanning = false,
        importFix = fix,
        review = review,
        savedToday = saved,
        savedTodayMore = savedMore,
    )

    private fun row() = TransactionRowUiModel(
        id = "1",
        merchant = "Foodland Rama IV",
        meta = "Groceries · 18:30 · auto",
        parts = MetaParts(category = "Groceries", time = "18:30", provenance = Provenance.Auto),
        amount = "−฿642.50",
        direction = Direction.Expense,
        icon = "local_grocery_store",
        color = DEFAULT_COLOR,
    )

    private fun savedRow() = TransactionRowUiModel(
        id = "2",
        merchant = "LAWSON108_P3793 THE LIVIN",
        meta = "Wed 12 Aug · Food & drink",
        parts = MetaParts(category = "Food & drink", time = "19:40", provenance = Provenance.Auto),
        amount = "−฿155.00",
        direction = Direction.Expense,
        icon = "restaurant",
        color = DEFAULT_COLOR,
    )
}

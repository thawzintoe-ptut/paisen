package com.ptut.expense.ui.sky

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.domain.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The containers every Sky screen lays its content in (#152). Each renders what it is handed and,
 * where it can be tapped, reports back. Size and colour belong to the floor and contrast tests.
 */
@OptIn(ExperimentalTestApi::class)
class SkySurfacesTest {

    @Test
    fun aTransactionRowThatOpensItsRecordReportsATap() = runComposeUiTest {
        var taps = 0
        setContent { Sky { Foodland(onClick = { taps++ }) } }

        onNodeWithText("Foodland Rama IV").assertHasClickAction().performClick()

        assertEquals(1, taps)
    }

    /** FR-21: a row with no record behind it must not look or act like one that has. */
    @Test
    fun aTransactionRowWithNothingBehindItTakesNoTap() = runComposeUiTest {
        setContent { Sky { Foodland(onClick = null) } }

        onNodeWithText("Foodland Rama IV").assertHasNoClickAction()
    }

    @Test
    fun aTransactionRowShowsItsTimeUnderTheAmount() = runComposeUiTest {
        setContent { Sky { Foodland(onClick = null) } }

        onNodeWithContentDescription("−฿642.50, spent").assertIsDisplayed()
        onNodeWithText("18:30").assertIsDisplayed()
    }

    @Test
    fun aKeyValueRowShowsItsLabelAndItsValue() = runComposeUiTest {
        setContent { Sky { SkyKeyValueRow(label = "Category", value = "Groceries · Supermarket") } }

        onNodeWithText("Category").assertIsDisplayed()
        onNodeWithText("Groceries · Supermarket").assertIsDisplayed()
    }

    @Test
    fun aSectionHeaderWithSomewhereToGoReportsATap() = runComposeUiTest {
        var taps = 0
        setContent { Sky { SkySectionHeader(text = "Purchases", onClick = { taps++ }) } }

        onNodeWithText("Purchases").assertHasClickAction().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aSectionHeaderWithNowhereToGoIsOnlyAHeading() = runComposeUiTest {
        setContent { Sky { SkySectionHeader(text = "Top categories") } }

        onNodeWithText("Top categories").assertHasNoClickAction()
    }

    @Test
    fun theWarningBannerOffersItsFixAndReportsATap() = runComposeUiTest {
        var fixes = 0
        setContent {
            Sky {
                SkyWarningBanner(
                    title = "No slips seen for 3 days",
                    body = "Totals below may be incomplete",
                    actionLabel = "Fix",
                    onAction = { fixes++ },
                )
            }
        }

        onNodeWithText("Fix").performClick()

        assertEquals(1, fixes)
    }

    /** C-5: one to three days stale says so quietly, with nothing to tap. */
    @Test
    fun aQuietWarningBannerOffersNothingToTap() = runComposeUiTest {
        setContent { Sky { SkyWarningBanner(title = "No slips seen for 2 days", body = "Totals below may be incomplete") } }

        onNodeWithText("No slips seen for 2 days").assertHasNoClickAction()
        onAllNodesWithText("Fix").assertCountEquals(0)
    }

    @Test
    fun aStatCardThatLeadsSomewhereReportsATap() = runComposeUiTest {
        var taps = 0
        setContent {
            Sky { SkyStatCard(title = "To check", line = "2 slips to check", icon = PtutIcon.Inbox.image, onClick = { taps++ }) }
        }

        onNodeWithText("To check").assertHasClickAction().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aNoticeCardReadsItsTitleAndBodyAsOneSentence() = runComposeUiTest {
        setContent {
            Sky {
                SkyNoticeCard(
                    title = "Why Groceries?",
                    text = "You picked it for Foodland on 2 Aug.",
                    icon = PtutIcon.AutoAwesome.image,
                )
            }
        }

        onNodeWithText("Why Groceries? You picked it for Foodland on 2 Aug.").assertIsDisplayed()
    }

    @Test
    fun aGroupedCardDrawsEveryRowItIsHanded() = runComposeUiTest {
        setContent { Sky { SkyGroupedCard(rows = listOf({ Text("Photo folders") }, { Text("Background import") })) } }

        onNodeWithText("Photo folders").assertIsDisplayed()
        onNodeWithText("Background import").assertIsDisplayed()
    }

    /** Drawn in capitals, read in sentence case, like the pill buttons. */
    @Test
    fun aStatusPillIsReadByItsSentenceCaseLabel() = runComposeUiTest {
        setContent { Sky { SkyStatusPill(label = "Not counted") } }

        onNodeWithContentDescription("Not counted").assertIsDisplayed()
    }

    @Test
    fun aConfidenceBadgeShowsWhatWasReadClearly() = runComposeUiTest {
        setContent { Sky { SkyConfidenceBadge(label = "Amount read clearly") } }

        onNodeWithText("Amount read clearly").assertIsDisplayed()
    }

    @Test
    fun theBottomSheetShowsWhatItIsHanded() = runComposeUiTest {
        setContent { Sky { SkyBottomSheet(onDismissRequest = {}) { Text("Delete this purchase?") } } }

        onNodeWithText("Delete this purchase?").assertIsDisplayed()
    }
}

@Composable
private fun Foodland(onClick: (() -> Unit)?) = SkyTransactionRow(
    merchant = "Foodland Rama IV",
    meta = "Groceries",
    amount = "−฿642.50",
    icon = "local_grocery_store",
    direction = Direction.Expense,
    time = "18:30",
    onClick = onClick,
)

@Composable
private fun Sky(content: @Composable () -> Unit) = PtutTheme(content = content)

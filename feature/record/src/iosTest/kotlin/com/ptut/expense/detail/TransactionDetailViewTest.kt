package com.ptut.expense.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.domain.DEFAULT_COLOR
import com.ptut.expense.domain.MoneyParts
import com.ptut.expense.ui.TransactionMetaRow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TransactionDetailViewTest {

    /**
     * FR-12's promise, and the one line the spec calls required: without it people assume delete
     * undoes the learning too. It has to be on screen before the button, not after the tap.
     */
    @Test
    fun theFooterPromisesTheMerchantRuleBeforeTheDeleteButtonIsReachable() = runComposeUiTest {
        setContent {
            TransactionDetailView(
                uiState = loaded,
                onBack = {},
                onEdit = {},
                onAskToDelete = {},
                onKeepIt = {},
                onConfirmDelete = {},
                onOpenShopRule = {},
            )
        }

        onNodeWithText(loaded.ruleFooter).assertIsDisplayed()
    }

    /**
     * The whole record on one screen, and the destructive action reachable from it. Every field
     * the spec lists has to be here — the screen exists so the reader looks at what they picked
     * before the step that cannot be undone.
     */
    @Test
    fun theWholeRecordIsOnScreenAndDeleteAsksBeforeItActs() = runComposeUiTest {
        var deleted = false
        setContent {
            TransactionDetailView(
                uiState = loaded,
                onBack = {},
                onEdit = {},
                onAskToDelete = { deleted = true },
                onKeepIt = {},
                onConfirmDelete = {},
                onOpenShopRule = {},
            )
        }

        onNodeWithText("Purchase").assertIsDisplayed()
        onNodeWithText("Foodland Rama IV").assertIsDisplayed()
        onNodeWithContentDescription(loaded.spoken).assertIsDisplayed()
        onNodeWithText("Category").assertIsDisplayed()
        onNodeWithText("Groceries").assertIsDisplayed()
        onNodeWithText(loaded.consequence).assertIsDisplayed()

        onNodeWithContentDescription("Delete").performClick()

        assertTrue(deleted, "the delete button reported nothing up")
    }

    /**
     * #52 deletes the row from the step after this one, and a back stack restored afterwards
     * still holds its id. Saying which emptiness this is — deleted, not failed to load — is the
     * difference between the reader worrying and not.
     */
    @Test
    fun aRecordThatWentSaysSoRatherThanDrawingABlankOneWithALiveDeleteButton() = runComposeUiTest {
        setContent {
            TransactionDetailView(
                uiState = TransactionDetailUiState.Missing,
                onBack = {},
                onEdit = {},
                onAskToDelete = {},
                onKeepIt = {},
                onConfirmDelete = {},
                onOpenShopRule = {},
            )
        }

        onNodeWithText("This transaction is gone").assertIsDisplayed()
        onNodeWithText("Delete transaction").assertDoesNotExist()
    }

    private val loaded = TransactionDetailUiState.Loaded(
        merchant = "Foodland Rama IV",
        amount = MoneyParts(symbol = "−฿", whole = "642", fraction = ".50"),
        spoken = "Foodland Rama IV, 642.50 baht spent",
        categoryIcon = "local_grocery_store",
        categoryColor = DEFAULT_COLOR,
        rows = listOf(TransactionMetaRow("CATEGORY", "Groceries", icon = "sell")),
        consequence = "The slip image for this transaction was already deleted.",
        deleteMoves = "฿642.50 comes off today, this week and this month.",
        ruleFooter = "The shop rule is kept. " +
            "Later slips from Foodland Rama IV will still file themselves.",
    )

    /**
     * FR-21. The dialog names the damage in baht and only the totals that move, and the calm
     * option describes its outcome — a reader scanning two buttons under a warning reads the
     * verbs, not the positions.
     */
    @Test
    fun theDialogNamesTheDamageAndOffersToKeepTheRow() = runComposeUiTest {
        var kept = false
        var confirmed = false
        setContent {
            TransactionDetailView(
                uiState = loaded.copy(confirmingDelete = true),
                onBack = {},
                onEdit = {},
                onAskToDelete = {},
                onKeepIt = { kept = true },
                onConfirmDelete = { confirmed = true },
                onOpenShopRule = {},
            )
        }

        onNodeWithText("Delete this purchase?").assertIsDisplayed()
        // The sheet's own sentence, not the note's. Asserting the note's text here would pass
        // just as well when the sheet is repeating the paragraph behind it.
        onNodeWithText("${loaded.deleteMoves} You can undo it until you do something else.").assertIsDisplayed()

        onNodeWithContentDescription("Keep it").performClick()
        assertTrue(kept, "the calm option reported nothing")
        assertFalse(confirmed, "keeping it went on to delete")
    }

    /** No dialog until the reader asks for one. */
    @Test
    fun theRecordDrawsNoDialogUntilDeleteIsTapped() = runComposeUiTest {
        setContent {
            TransactionDetailView(
                uiState = loaded,
                onBack = {},
                onEdit = {},
                onAskToDelete = {},
                onKeepIt = {},
                onConfirmDelete = {},
                onOpenShopRule = {},
            )
        }

        onNodeWithText("Delete this transaction?").assertDoesNotExist()
    }
}

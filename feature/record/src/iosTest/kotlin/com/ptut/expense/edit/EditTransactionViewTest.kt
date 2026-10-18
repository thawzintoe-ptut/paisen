package com.ptut.expense.edit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.domain.Category
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.TransactionMetaRow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EditTransactionViewTest {

    /**
     * Page 11c drawn: the amount being typed, the slip's own reading under it, the chips, the
     * fields that stay as read, and the note. All of it in one place, because the screen's whole
     * argument is that a correction is made in sight of what it corrects.
     */
    @Test
    fun theScreenDrawsTheAmountItsSlipReadingAndTheFieldsThatStayAsRead() = runComposeUiTest {
        setContent {
            EditTransactionView(
                uiState = loaded,
                onClose = {},
                onType = {},
                onCategory = {},
                onSubCategory = {},
                onTime = {},
                onOpenMemo = {},
                onMemo = {},
                onCloseMemo = {},
                onSave = {},
            )
        }

        onNodeWithText("Edit purchase").assertIsDisplayed()
        // #53: the bar action is Done here and Save on Android, under the same rule about when
        // it lights. This target is the only one that can prove which word was drawn.
        onNodeWithText("Done").assertIsDisplayed()
        onNodeWithText("642.50").assertIsDisplayed()
        onNodeWithText("Read from the slip as ฿642.50").assertIsDisplayed()
        onNodeWithText("Category").assertIsDisplayed()
        onNodeWithText("Groceries").assertIsDisplayed()
        onNodeWithText("Shop").assertIsDisplayed()
        onNodeWithText("Foodland Rama IV").assertIsDisplayed()
        onNodeWithText("Note").assertIsDisplayed()
        onNodeWithText(loaded.notice).assertIsDisplayed()
    }
}

private val loaded = EditTransactionUiState.Loaded(
    entry = AmountEntry.of(com.ptut.expense.domain.Satang(64_250)),
    slipRead = "Read from the slip as ฿642.50",
    categories = Category.spending,
    categoryId = "groceries",
    memo = null,
    evidence = listOf(
        TransactionMetaRow("MERCHANT", "Foodland Rama IV", icon = "store"),
        TransactionMetaRow("DATE & TIME", "Fri 14 Aug, 18:30", icon = "schedule"),
        TransactionMetaRow("REFERENCE", "0138K4", icon = "sell"),
    ),
    notice = "Shop, reference and time came from the slip and stay as read.",
    canSave = false,
)

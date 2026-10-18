package com.ptut.expense.add

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.ptut.expense.design.AtFontScale
import com.ptut.expense.design.PtutTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sheet's header at the largest text size. On Android 15 at font scale 2.0 the expense and
 * income selector, held to its width cap, cut its labels to "Expe" and "Inco" (#20).
 */
@OptIn(ExperimentalTestApi::class)
class AddSheetTest {

    @Test
    fun theExpenseAndIncomeLabelsStayWholeAtTwiceTheTextSize() = runComposeUiTest {
        setContent { AtFontScale(2f) { PtutTheme { Sheet() } } }

        listOf("Expense", "Income").forEach { label ->
            val layout = onNodeWithText(label, useUnmergedTree = true).textLayout()
            assertEquals(label.length, layout.getLineEnd(0), "$label was cut short")
        }
    }

    /** The default text size keeps the layout p10 draws: the selector beside the amount. */
    @Test
    fun atTheDefaultTextSizeTheSelectorSitsBesideTheAmount() = runComposeUiTest {
        setContent { PtutTheme { Sheet() } }

        val amount = onNodeWithText("฿").getUnclippedBoundsInRoot()
        val selector = onNodeWithText("Expense", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(selector.top < amount.bottom, "the selector dropped under the amount")
    }

    @Composable
    private fun Sheet() = AddSheet(
        uiState = AddUiState(),
        onType = {},
        onKind = {},
        onCategory = {},
        onSubCategory = {},
        onTime = {},
        onOpenMemo = {},
        onMemo = {},
        onCloseMemo = {},
        onSave = {},
    )
}

private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}

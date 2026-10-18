package com.ptut.expense.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.domain.Direction
import com.ptut.expense.ui.sky.SkyNumpad
import com.ptut.expense.ui.sky.SkyTransactionRow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The primitives every remaining screen draws. They were private to `home/`, `reports/` and
 * `add/`, and two of them had been copied rather than shared.
 *
 * These cover the contract the screens rely on: each one renders exactly what it is handed, and
 * the interactive ones report back. Size and colour are the theme's business, asserted there.
 */
@OptIn(ExperimentalTestApi::class)
class SharedPrimitivesTest {

    @Test
    fun aTransactionRowDrawsTheMerchantItsMetaAndItsAmount() = runComposeUiTest {
        setContent {
            PtutTheme {
                SkyTransactionRow(
                    merchant = "Foodland Rama IV",
                    meta = "Groceries · 18:30 · auto",
                    amount = "−฿642.50",
                    icon = "local_grocery_store",
                    direction = Direction.Expense,
                )
            }
        }

        onNodeWithText("Foodland Rama IV").assertIsDisplayed()
        onNodeWithText("Groceries · 18:30 · auto").assertIsDisplayed()
        // The amount is spoken with its direction, so a reader hears "spent" and not only a figure.
        onNodeWithContentDescription("−฿642.50, spent").assertIsDisplayed()
    }

    /** The keypad is the only way an amount is typed, so a key that reports nothing is a dead app. */
    @Test
    fun aNumpadKeyReportsTheGlyphItCarries() = runComposeUiTest {
        var pressed: String? = null
        setContent { PtutTheme { SkyNumpad(onKey = { pressed = it }) } }

        onNodeWithText("7").performClick()

        assertEquals("7", pressed)
    }
}

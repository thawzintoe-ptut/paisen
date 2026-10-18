package com.ptut.expense.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.design.PtutTheme
import kotlin.test.Test

/**
 * The drawing half of the toast. The timing half is [com.ptut.expense.AppViewModelTest]: this
 * harness runs `delay` on virtual time, so a host that owned its own clock could not be seen here.
 */
@OptIn(ExperimentalTestApi::class)
class ToastHostTest {

    @Test
    fun aMessageIsDrawnAsGivenWithNoAmountReformatted() = runComposeUiTest {
        setContent { PtutTheme { ToastHost(ToastMessage("Saved ฿485 · Food & drink")) } }

        onNodeWithText("Saved ฿485 · Food & drink").assertIsDisplayed()
    }

    @Test
    fun nothingIsDrawnWhileTheAppHasNothingToSay() = runComposeUiTest {
        setContent { PtutTheme { ToastHost(message = null) } }

        onNodeWithText("Saved ฿485 · Food & drink").assertDoesNotExist()
    }
}

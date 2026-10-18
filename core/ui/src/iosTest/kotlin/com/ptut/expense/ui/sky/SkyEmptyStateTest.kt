package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** #153. FR-20: an empty screen says what it waits for and proves the importer is alive. */
@OptIn(ExperimentalTestApi::class)
class SkyEmptyStateTest {

    @Test
    fun anEmptyScreenSaysWhatItWaitsForAndThatTheImporterIsAlive() = runComposeUiTest {
        setContent { Sky { QuietDay(cta = null) } }

        onNodeWithText("A quiet day so far.").assertIsDisplayed()
        onNodeWithText("New slips in your chosen folders show up here on their own.").assertIsDisplayed()
        onNodeWithText("Watching the K PLUS folder · checked 4 min ago").assertIsDisplayed()
    }

    @Test
    fun anEmptyScreenWithARealNextStepOffersItAsAButton() = runComposeUiTest {
        var taps = 0
        setContent { Sky { QuietDay(cta = "Add by hand", onCta = { taps++ }) } }

        onNodeWithContentDescription("Add by hand").performClick()

        assertEquals(1, taps)
    }

    /** Reports, Activity and a clear queue wait: a button there would say the reader is at fault. */
    @Test
    fun anEmptyScreenWhereWaitingIsRightOffersNothingToTap() = runComposeUiTest {
        setContent { Sky { QuietDay(cta = null) } }

        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}

/** At a phone's width: the slot keeps its ratio, and at the test window's 1024 it pushes the button off screen. */
@Composable
private fun QuietDay(cta: String?, onCta: (() -> Unit)? = null) = SkyEmptyState(
    modifier = Modifier.width(390.dp),
    headline = "A quiet day so far.",
    body = "New slips in your chosen folders show up here on their own.",
    status = "Watching the K PLUS folder · checked 4 min ago",
    statusIcon = PtutIcon.Sync.image,
    waiting = true,
    cta = cta,
    onCta = onCta,
)

@Composable
private fun Sky(content: @Composable () -> Unit) = PtutTheme(content = content)

package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.ui.confirmActionLabel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #154: one bar for every Sky screen, its title centred whatever sits either side of it. */
@OptIn(ExperimentalTestApi::class)
class SkyTopBarTest {

    @Test
    fun aPushedScreenGoesBackFromTheLeft() = runComposeUiTest {
        var backs = 0
        setContent { Sky { SkyTopBar(title = "Photo folders", onBack = { backs++ }) } }

        onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    /** A form closes and discards, so it offers no arrow promising a step back. */
    @Test
    fun aFormClosesRatherThanGoingBack() = runComposeUiTest {
        var closes = 0
        setContent {
            Sky {
                SkyTopBar(title = "Edit purchase", onBack = { closes++ }, backIcon = PtutIcon.Close, backLabel = "Close")
            }
        }

        onNodeWithContentDescription("Close").performClick()

        assertEquals(1, closes)
    }

    @Test
    fun aTabRootHasNothingToTapInItsBar() = runComposeUiTest {
        setContent { Sky { SkyTopBar(title = "Reports", onBack = null) } }

        onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun theTitleIsReadAsAHeading() = runComposeUiTest {
        setContent { Sky { SkyTopBar(title = "Categories", onBack = {}) } }

        onNodeWithText("Categories").assert(isHeading())
    }

    @Test
    fun theTitleStaysCentredWhenTheSidesDifferInWidth() = runComposeUiTest {
        setContent {
            Sky {
                SkyTopBar(
                    title = "Edit purchase",
                    onBack = {},
                    modifier = Modifier.width(390.dp).testTag("bar"),
                    trailing = { SkyTextAction(label = "Save changes", onClick = {}, enabled = true) },
                )
            }
        }

        val bar = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val title = onNodeWithText("Edit purchase").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(title.center.x - bar.center.x) < 1f, "title centre ${title.center.x}, bar centre ${bar.center.x}")
    }

    @Test
    fun anIconActionIsNamedForAScreenReaderAndReportsATap() = runComposeUiTest {
        var edits = 0
        setContent { Sky { SkyBarIconAction(icon = PtutIcon.Edit, label = "Edit", onClick = { edits++ }) } }

        onNodeWithContentDescription("Edit").performClick()

        assertEquals(1, edits)
    }

    /** R11 (#147): the platform names the confirm action, Done on iOS and Save on Android. */
    @Test
    fun theConfirmActionIsNamedTheWayThisPlatformNamesIt() = runComposeUiTest {
        setContent { Sky { SkyConfirmAction(enabled = true, onClick = {}) } }

        onNodeWithText(confirmActionLabel).assert(hasClickAction())
    }

    @Test
    fun theLargeTitleIsReadAsAHeading() = runComposeUiTest {
        setContent { Sky { SkyLargeTitle(text = "Settings") } }

        onNodeWithText("Settings").assert(isHeading())
    }
}

@Composable
private fun Sky(content: @Composable () -> Unit) = PtutTheme(content = content)

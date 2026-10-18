package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.design.PtutTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The controls every Sky screen draws (#151). Each one renders what it is handed, reports back, and
 * names itself to a screen reader. Size is `TouchTargetFloorTest`'s business, colour the palette's.
 */
@OptIn(ExperimentalTestApi::class)
class SkyControlsTest {

    @Test
    fun aPillButtonIsReadByItsSentenceCaseLabelAndReportsATap() = runComposeUiTest {
        var taps = 0
        setContent { Sky { SkyButton(label = "Not mine", onClick = { taps++ }, kind = SkyButtonKind.Outline) } }

        onNodeWithContentDescription("Not mine").assertHasClickAction().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aDisabledPillButtonTakesNoTap() = runComposeUiTest {
        var taps = 0
        setContent { Sky { SkyButton(label = "Save", onClick = { taps++ }, enabled = false) } }

        onNodeWithContentDescription("Save").assertIsNotEnabled().performClick()

        assertEquals(0, taps)
    }

    @Test
    fun aTopBarActionIsInertUntilTheScreenHasAChange() = runComposeUiTest {
        var taps = 0
        setContent { Sky { SkyTextAction(label = "Save", onClick = { taps++ }, enabled = false) } }

        onNodeWithText("Save").assertIsNotEnabled().performClick()

        assertEquals(0, taps)
    }

    @Test
    fun aTopBarActionWithAChangeToKeepReportsATap() = runComposeUiTest {
        var taps = 0
        setContent { Sky { SkyTextAction(label = "Save", onClick = { taps++ }, enabled = true) } }

        onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aChipSaysWhetherItIsChosenAndReportsATap() = runComposeUiTest {
        var taps = 0
        setContent {
            Sky {
                Column {
                    SkyChip(label = "Groceries", selected = true, onClick = {})
                    SkyChip(label = "Transport", selected = false, onClick = { taps++ })
                }
            }
        }

        onNodeWithText("Groceries").assertIsSelected()
        onNodeWithText("Transport").assertIsNotSelected().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aSubCategoryChipSaysWhetherItIsChosenAndReportsATap() = runComposeUiTest {
        var taps = 0
        setContent {
            Sky {
                Column {
                    SkySubChip(label = "Supermarket", selected = true, onClick = {})
                    SkySubChip(label = "Market", selected = false, onClick = { taps++ })
                }
            }
        }

        onNodeWithText("Supermarket").assertIsSelected()
        onNodeWithText("Market").assertIsNotSelected().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun theSegmentedToggleReportsWhichSegmentWasTapped() = runComposeUiTest {
        var chosen = -1
        setContent {
            Sky { SkySegmentedToggle(options = listOf("Expense", "Income"), selected = 0, onSelect = { chosen = it }) }
        }

        onNodeWithText("Expense").assertIsSelected()
        onNodeWithText("Income").assertIsNotSelected().performClick()

        assertEquals(1, chosen)
    }

    @Test
    fun theTabsReportWhichTabWasTapped() = runComposeUiTest {
        var chosen = -1
        setContent {
            Sky { SkyUnderlineTabs(options = listOf("Today", "Week", "Month"), selected = 0, onSelect = { chosen = it }) }
        }

        onNodeWithText("Today").assertIsSelected()
        onNodeWithText("Month").assertIsNotSelected().performClick()

        assertEquals(2, chosen)
    }

    @Test
    fun aSwitchSaysWhetherItIsOnAndReportsTheFlip() = runComposeUiTest {
        var on: Boolean? = null
        setContent { Sky { SkySwitch(checked = false, onCheckedChange = { on = it }, label = "Background import") } }

        onNodeWithContentDescription("Background import").assertIsOff().performClick()

        assertEquals(true, on)
    }

    /** Settings rows toggle as a whole, so the switch inside one draws its state and takes no tap. */
    @Test
    fun aSwitchWithNoHandlerLeavesTheTapToItsRow() = runComposeUiTest {
        var on = true
        setContent {
            Sky {
                Row(Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { on = it })) {
                    Text("Transparency")
                    SkySwitch(checked = on, onCheckedChange = null)
                }
            }
        }

        onNodeWithText("Transparency").assertIsOn().performClick()

        assertEquals(false, on)
    }

    @Test
    fun aCheckboxSaysWhetherItIsTickedAndReportsTheChange() = runComposeUiTest {
        var ticked: Boolean? = null
        setContent { Sky { SkyCheckbox(checked = true, onCheckedChange = { ticked = it }, label = "Screenshots") } }

        onNodeWithContentDescription("Screenshots").assertIsOn().performClick()

        assertEquals(false, ticked)
    }

    @Test
    fun theSearchFieldIsNamedByItsHintAndReportsWhatIsTyped() = runComposeUiTest {
        var typed = ""
        setContent { Sky { SkySearchField(query = "", onSearch = { typed = it }, placeholder = "Search purchases") } }

        onNode(hasSetTextAction() and hasContentDescription("Search purchases")).performTextInput("Foodland")

        assertEquals("Foodland", typed)
    }

    @Test
    fun theDashedAddRowIsReadByItsSentenceCaseLabelAndReportsATap() = runComposeUiTest {
        var taps = 0
        setContent { Sky { SkyDashedAddRow(label = "New category", onClick = { taps++ }) } }

        onNodeWithContentDescription("New category").performClick()

        assertEquals(1, taps)
    }
}

@Composable
private fun Sky(content: @Composable () -> Unit) =
    PtutTheme(content = content)

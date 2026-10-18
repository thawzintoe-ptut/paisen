package com.ptut.expense.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.domain.DEFAULT_COLOR
import com.ptut.expense.ui.confirmActionLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Screen 12a, from the finger down. The state and the view model were right for a release and the
 * screen still could not be re-iconed: the tile drew an accent badge saying "editable" and had no
 * click on it, so `chooseIcon` sat unreachable. These press the badge rather than trust it.
 */
@OptIn(ExperimentalTestApi::class)
class EditCategoryViewTest {

    private val loaded = EditCategoryUiState(
        loaded = true,
        name = "Food & drink",
        icon = "restaurant",
        subCategories = listOf(
            SubCategoryRowUiModel(id = "eating-out", name = "Eating out", purchases = "31 purchases"),
        ),
        deleteConsequence = "You will be asked where its 47 transactions should go.",
        deleteDestinations = listOf(CategoryChoiceUiModel("groceries", "Groceries", "local_grocery_store", DEFAULT_COLOR)),
        canDelete = true,
    )

    /**
     * The state a real navigation draws on its first frame, before the category has been read.
     *
     * This is the shape that took the app down on the emulator: the tile asks PtutIcon for a
     * glyph by name, the default name is empty, and PtutIcon throws on a name nobody drew rather
     * than falling back — by design. Every other test here hands the view a loaded state, which
     * is exactly why none of them saw it.
     */
    @Test
    fun theScreenSurvivesTheFrameBeforeTheCategoryHasBeenRead() = runComposeUiTest {
        setContent { EditCategoryView(uiState = EditCategoryUiState()) }

        // The header stays, so a row that is gone can still be backed out of.
        onNodeWithText("Edit category").assertIsDisplayed()
        onNodeWithText("Delete category").assertDoesNotExist()
    }

    /** FR-12's "re-iconed". The badge is the whole affordance, so tapping it has to open a grid. */
    @Test
    fun theIconTileOpensTheGridAndReportsTheChosenGlyphUp() = runComposeUiTest {
        var chosen: String? = null
        setContent { EditCategoryView(uiState = loaded, onIcon = { chosen = it }) }

        assertNull(chosen, "something chose an icon before the tile was touched")

        onNodeWithContentDescription("Change the icon").performClick()
        onNodeWithText("Choose an icon").assertIsDisplayed()
        onNodeWithContentDescription("A shop").performClick()

        assertEquals("store", chosen, "the grid reported no glyph up")
    }

    /** Backing out of the grid is not a choice, so nothing may be reported and nothing dirtied. */
    @Test
    fun keepingTheCurrentIconReportsNothing() = runComposeUiTest {
        var chosen: String? = null
        setContent { EditCategoryView(uiState = loaded, onIcon = { chosen = it }) }

        onNodeWithContentDescription("Change the icon").performClick()
        onNodeWithContentDescription("Keep this one").performClick()

        assertNull(chosen, "leaving the grid alone still changed the icon")
    }

    /**
     * Android draws Save in the top app bar and iOS draws Done in the navigation bar. This suite
     * runs on iOS, so the label it looks for is the one the platform picked rather than a literal
     * — asserting "Save" here would pass on the wrong platform and fail on the right one.
     */
    @Test
    fun theConfirmActionIsNamedTheWayThisPlatformNamesIt() = runComposeUiTest {
        setContent { EditCategoryView(uiState = loaded) }

        onNodeWithText("Done").assertIsDisplayed()
        onNodeWithText(confirmActionLabel).assertIsDisplayed()
    }

    @Test
    fun theConfirmActionIsInertUntilTheScreenIsDirty() = runComposeUiTest {
        setContent { EditCategoryView(uiState = loaded) }

        onNodeWithText(confirmActionLabel).assertIsNotEnabled()
    }

    @Test
    fun theConfirmActionWakesUpOnceSomethingHasChanged() = runComposeUiTest {
        setContent { EditCategoryView(uiState = loaded.copy(dirty = true)) }

        onNodeWithText(confirmActionLabel).assertIsEnabled()
    }

    /**
     * FR-13 and FR-18. Both destructive actions state their consequence before they are taken,
     * and the words are on screen beside the control rather than in the dialog that follows it.
     */
    @Test
    fun bothDestructiveActionsStateWhatTheyDoBeforeTheyAreTapped() = runComposeUiTest {
        setContent { EditCategoryView(uiState = loaded) }

        onNodeWithText(
            "Removing a sub-category keeps its spending in Food & drink. Nothing is deleted, " +
                "it just stops being counted separately.",
        ).assertIsDisplayed()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(loaded.deleteConsequence))
        onNodeWithText(loaded.deleteConsequence).assertIsDisplayed()
        onNodeWithContentDescription("Delete category").assertIsDisplayed()
    }

    /** FR-18: the question has no "just delete" answer, so a destination is the only way through. */
    @Test
    fun deletingAsksWhereTheTransactionsShouldGo() = runComposeUiTest {
        var movedTo: String? = null
        setContent { EditCategoryView(uiState = loaded, onDelete = { movedTo = it }) }

        onNodeWithContentDescription("Delete category").performClick()
        onNodeWithText("Where should these go?").assertIsDisplayed()

        assertNull(movedTo, "the category went before anyone said where its rows should land")

        onNodeWithText("Groceries").performClick()

        assertEquals("groceries", movedTo, "the dialog deleted without naming a destination")
    }

    /**
     * The dashed slot is an affordance, not a field. It has to turn into one that reports a name
     * up, or the screen offers a way to add sub-categories that cannot add one.
     */
    @Test
    fun theDashedSlotBecomesAFieldAndReportsTheNameUp() = runComposeUiTest {
        var added: String? = null
        setContent { EditCategoryView(uiState = loaded, onAddSubCategory = { added = it }) }

        onNodeWithText("Add sub-category").performClick()
        onNodeWithContentDescription("Name the sub-category").performTextInput("Coffee")
        onNodeWithContentDescription("Add this sub-category").performClick()

        assertEquals("Coffee", added, "the slot reported no name up")
    }

    /** p20: how many purchases it holds, the honest test of whether it earns its place. */
    @Test
    fun aSubCategoryShowsHowManyPurchasesItHolds() = runComposeUiTest {
        setContent { EditCategoryView(uiState = loaded) }

        onNodeWithText("Eating out").assertIsDisplayed()
        onNodeWithText("31 purchases").assertIsDisplayed()
    }
}

/** The callbacks a test does not care about, so each one only names the one it is about. */
@Composable
private fun EditCategoryView(
    uiState: EditCategoryUiState,
    onIcon: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onAddSubCategory: (String) -> Unit = {},
) = EditCategoryView(
    uiState = uiState,
    onBack = {},
    onRename = {},
    onIcon = onIcon,
    onDirection = {},
    onColor = {},
    onSave = {},
    onAddSubCategory = onAddSubCategory,
    onRenameSubCategory = { _, _ -> },
    onRemoveSubCategory = {},
    onDelete = onDelete,
)

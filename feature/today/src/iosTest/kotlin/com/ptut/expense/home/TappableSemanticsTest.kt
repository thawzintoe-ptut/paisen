package com.ptut.expense.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.tappable
import kotlin.test.Test
import kotlin.test.assertEquals

/** Why DailyQuestionView, GuessView, PlayStrip, HomeView, GoalJarView and SkyHeroCard tap before they clear. */
@OptIn(ExperimentalTestApi::class)
class TappableSemanticsTest {

    @Test
    fun tappableBeforeTheClearKeepsItsClickAndRoleForTheScreenReader() = runComposeUiTest {
        var taps = 0
        setContent {
            Box(
                Modifier
                    .size(SkySizes.search)
                    .tappable(shape = CircleShape, role = Role.Button) { taps += 1 }
                    .clearAndSetSemantics { contentDescription = "x" },
            )
        }

        onNodeWithContentDescription("x")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, taps)
    }

    @Test
    fun tappableAfterTheClearIsWipedByIt() = runComposeUiTest {
        setContent {
            Box(
                Modifier
                    .size(SkySizes.search)
                    .clearAndSetSemantics { contentDescription = "x" }
                    .tappable(shape = CircleShape, role = Role.Button) {},
            )
        }

        onNodeWithContentDescription("x").assertHasNoClickAction()
    }
}

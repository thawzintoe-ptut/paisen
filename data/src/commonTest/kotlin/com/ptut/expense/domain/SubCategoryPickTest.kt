package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubCategoryPickTest {

    @Test
    fun aSubCategoryStaysWhileItsParentIsPickedAgain() {
        assertEquals("sushi", "sushi".keptAfterPicking(pickedCategoryId = "food", previousCategoryId = "food"))
    }

    @Test
    fun anotherParentLeavesTheSubCategoryBehind() {
        assertNull("sushi".keptAfterPicking(pickedCategoryId = "travel", previousCategoryId = "food"))
    }

    @Test
    fun noParentKeepsNoSubCategory() {
        assertNull("sushi".keptAfterPicking(pickedCategoryId = null, previousCategoryId = null))
        assertNull("sushi".keptAfterPicking(pickedCategoryId = null, previousCategoryId = "food"))
    }
}

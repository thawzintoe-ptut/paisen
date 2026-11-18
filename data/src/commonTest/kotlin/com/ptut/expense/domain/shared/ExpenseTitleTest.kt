package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseTitleTest {

    @Test
    fun aNoteComesFirst() {
        assertEquals("Taxi to the airport", ExpenseTitle.shown("Taxi to the airport", "Transport", "Transport"))
    }

    @Test
    fun onlyTheFirstLineOfALongNoteShows() {
        assertEquals("Dinner", ExpenseTitle.shown("\n  Dinner  \nwith the team", "Food & drink", "Food & drink"))
    }

    @Test
    fun aNoteOfNothingButSpacesIsNoNote() {
        assertEquals("Lunch", ExpenseTitle.shown("   \n ", "Lunch", "Food & drink"))
    }

    @Test
    fun withNoNoteTheTypedDescriptionShows() {
        assertEquals("Lunch", ExpenseTitle.shown(null, " Lunch ", "Food & drink"))
    }

    @Test
    fun withNeitherTheCategoryNameShows() {
        assertEquals("Food & drink", ExpenseTitle.shown(null, "  ", "Food & drink"))
        assertEquals("", ExpenseTitle.shown(null, "", null))
    }

    @Test
    fun aDescriptionThatWasTheOldCategoryNameFollowsTheNewOne() {
        assertEquals("Groceries", ExpenseTitle.followed("Food & drink", TitleChange("Food & drink", "Groceries")))
    }

    @Test
    fun aTypedDescriptionStaysWhenTheCategoryChanges() {
        assertEquals("Lunch", ExpenseTitle.followed("Lunch", TitleChange("Food & drink", "Groceries")))
    }

    @Test
    fun noCategoryChangeLeavesTheDescriptionAlone() {
        assertEquals("Food & drink", ExpenseTitle.followed("Food & drink", null))
    }
}

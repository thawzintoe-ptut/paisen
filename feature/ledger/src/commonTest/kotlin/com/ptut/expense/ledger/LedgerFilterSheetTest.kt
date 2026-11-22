package com.ptut.expense.ledger

import com.ptut.expense.domain.LedgerFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerFilterSheetTest {

    @Test
    fun `the sheet opens on All then Spending then Income`() {
        assertEquals(listOf(LedgerFilter.All, LedgerFilter.Spending, LedgerFilter.Income), PrimaryLedgerFilters)
    }

    @Test
    fun `every other filter waits behind More filters`() {
        assertEquals(listOf(LedgerFilter.Auto, LedgerFilter.ByHand, LedgerFilter.Edited), MoreLedgerFilters)
        assertEquals(LedgerFilter.entries.toSet(), (PrimaryLedgerFilters + MoreLedgerFilters).toSet())
    }

    @Test
    fun `a hidden filter that is on opens the sheet with More filters shown`() {
        assertTrue(LedgerFilter.Auto.opensMoreFilters())
        assertTrue(LedgerFilter.ByHand.opensMoreFilters())
        assertTrue(LedgerFilter.Edited.opensMoreFilters())
    }

    @Test
    fun `a first-row filter opens the sheet folded`() {
        assertFalse(LedgerFilter.All.opensMoreFilters())
        assertFalse(LedgerFilter.Spending.opensMoreFilters())
        assertFalse(LedgerFilter.Income.opensMoreFilters())
    }
}

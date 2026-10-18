package com.ptut.expense.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleEngineTest {

    private val rules = listOf(
        MerchantRule("LAWSON108_P3793 THE LIVIN", "groceries"),
        MerchantRule("SHOPEEPAY", "transport"),
    )

    @Test
    fun `a remembered merchant lands in its category`() {
        assertEquals("groceries", RuleEngine.categoryFor("LAWSON108_P3793 THE LIVIN", rules))
    }

    @Test
    fun `a truncated name is keyed as the slip prints it rather than as the full one`() {
        assertNull(RuleEngine.categoryFor("LAWSON108_P3793 THE LIVING ROOM", rules))
    }

    @Test
    fun `case and surrounding space do not make a second merchant`() {
        assertEquals("transport", RuleEngine.categoryFor("  ShopeePay ", rules))
    }

    @Test
    fun `an unknown merchant has no category and is not guessed at`() {
        assertNull(RuleEngine.categoryFor("TRUE MONEY COMPANY LIMITED", rules))
    }

    @Test
    fun `no rules at all is not an error`() {
        assertNull(RuleEngine.categoryFor("SHOPEEPAY", emptyList()))
    }
}

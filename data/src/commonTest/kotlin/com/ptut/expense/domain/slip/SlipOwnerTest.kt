package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.rules.RuleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlipOwnerTest {

    private fun slip(sender: String?, recipient: String) =
        SlipRead.Complete(recipient, Satang.ofMinorUnits(50_000), 0L, "REF1", sender = sender)

    @Test
    fun `the same sender on two of your own slips becomes your name`() {
        val owner = SlipOwner().learnFrom("MR. THAW Z").learnFrom("MR. THAW Z")

        assertEquals("MR. THAW Z", owner.name)
    }

    @Test
    fun `one slip is not enough and a different sender starts the count again`() {
        val owner = SlipOwner().learnFrom("MR. THAW Z").learnFrom("MS. ANONG W")

        assertNull(owner.name)
        assertEquals("MS. ANONG W", owner.candidate)
        assertEquals(1, owner.seen)
    }

    @Test
    fun `a name set by hand is never relearned`() {
        val owner = SlipOwner(name = "MR. THAW ZIN TOE").learnFrom("MS. ANONG W").learnFrom("MS. ANONG W")

        assertEquals("MR. THAW ZIN TOE", owner.name)
    }

    @Test
    fun `a slip someone sent you is money in`() {
        assertEquals(Direction.Income, slip(sender = "MS. ANONG W", recipient = "MR. THAW ZIN TOE").direction("MR. THAW Z"))
    }

    @Test
    fun `your own payment is money out`() {
        assertEquals(Direction.Expense, slip(sender = "MR. THAW Z", recipient = "LAWSON108_P3793 THE LIVIN").direction("MR. THAW Z"))
    }

    @Test
    fun `a slip between two other people stays money out`() {
        assertEquals(Direction.Expense, slip(sender = "MS. ANONG W", recipient = "MR. SOMCHAI J").direction("MR. THAW Z"))
    }

    @Test
    fun `with no name learned every slip stays money out`() {
        assertEquals(Direction.Expense, slip(sender = "MS. ANONG W", recipient = "MR. THAW ZIN TOE").direction(null))
    }

    @Test
    fun `a slip with no sender read stays money out`() {
        assertEquals(Direction.Expense, slip(sender = null, recipient = "MR. THAW ZIN TOE").direction("MR. THAW Z"))
    }

    @Test
    fun `a surname initial matches the whole surname but a different given name does not`() {
        assertTrue(SlipNames.same("MR. THAW Z", "MR. THAW ZIN TOE"))
        assertTrue(SlipNames.same("mr thaw zin", "MR. THAW Z"))
        assertFalse(SlipNames.same("MR. THAW Z", "MR. THAN Z"))
        assertFalse(SlipNames.same("MR. THAW Z", "MR. THAW K"))
    }

    @Test
    fun `a person keeps a money out rule and a money in rule apart`() {
        val rules = listOf(
            MerchantRule("MS. ANONG W", "transport"),
            MerchantRule("MS. ANONG W", "salary", direction = Direction.Income),
        )

        assertEquals("transport", RuleEngine.categoryFor("MS. ANONG W", rules))
        assertEquals("salary", RuleEngine.categoryFor("MS. ANONG W", rules, Direction.Income))
    }
}

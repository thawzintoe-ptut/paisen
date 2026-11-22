package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Category
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatCategoryTest {
    @Test
    fun `an existing Rent repeat still files under bills`() {
        assertEquals("bills", RepeatKind.Rent.categoryIn(Category.seed()))
    }

    @Test
    fun `a subscription files under other`() {
        assertEquals("other", RepeatKind.Subscription.categoryIn(Category.seed()))
    }
}

package com.ptut.expense.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionMetaTest {

    private val bangkok = TimeZone.of("Asia/Bangkok")

    @Test
    fun drawsSpendingWithTheGlyphOfTheCategoryItIsFiledUnder() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 18, minute = 30),
            categoryId = "groceries",
            source = TransactionSource.Scan,
        )

        assertEquals("local_grocery_store", transaction.iconName(Category.seed()))
    }

    @Test
    fun drawsSpendingWithNoCategoryAsUncategorisedRatherThanAsNothing() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 8, minute = 12),
            categoryId = null,
            source = TransactionSource.Scan,
        )

        assertEquals("more_horiz", transaction.iconName(Category.seed()))
    }

    /**
     * The row the income branch exists for. A restored backup drops a category this install does
     * not hold, and money in has to keep reading as money in once its filing is gone.
     */
    @Test
    fun keepsIncomeOnThePaymentsGlyphWhenItsCategoryNoLongerResolves() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 10, hour = 9, minute = 0),
            categoryId = null,
            source = TransactionSource.Manual,
            direction = Direction.Income,
        )

        assertEquals("payments", transaction.iconName(Category.seed()))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(bangkok).toEpochMilliseconds()

    private fun transaction(
        occurredAt: Long,
        categoryId: String?,
        source: TransactionSource,
        direction: Direction = Direction.Expense,
    ) = Transaction(
        id = "t1",
        occurredAtEpochMillis = occurredAt,
        amount = Satang(64_250),
        direction = direction,
        categoryId = categoryId,
        counterparty = "Foodland Rama IV",
        source = source,
    )
}

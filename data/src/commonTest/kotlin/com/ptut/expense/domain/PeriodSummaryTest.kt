package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodSummaryTest {

    @Test
    fun netIsIncomeMinusExpense() {
        val transactions = listOf(
            transaction(Satang(450_000), Direction.Income),
            transaction(Satang(12_875), Direction.Expense),
            transaction(Satang(8_000), Direction.Expense),
        )

        val summary = transactions.summarise()

        assertEquals(Satang(450_000), summary.income)
        assertEquals(Satang(20_875), summary.expense)
        assertEquals(Satang(429_125), summary.net)
    }

    private fun transaction(amount: Satang, direction: Direction) = Transaction(
        id = "t",
        occurredAtEpochMillis = 0L,
        amount = amount,
        direction = direction,
        categoryId = null,
        counterparty = null,
    )
}

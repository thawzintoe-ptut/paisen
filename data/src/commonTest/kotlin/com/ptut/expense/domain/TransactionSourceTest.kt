package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionSourceTest {

    @Test
    fun importedTransactionsAreAutomaticAndTypedOnesAreNot() {
        assertTrue(TransactionSource.Scan.isAutomatic)
        assertTrue(TransactionSource.Share.isAutomatic)
        assertFalse(TransactionSource.Manual.isAutomatic)
    }

    @Test
    fun aTransactionIsManualUnlessAnImporterClaimsIt() {
        val typed = Transaction(
            id = "t",
            occurredAtEpochMillis = 0L,
            amount = Satang(1),
            direction = Direction.Expense,
            categoryId = null,
            counterparty = null,
        )

        assertEquals(TransactionSource.Manual, typed.source)
    }
}

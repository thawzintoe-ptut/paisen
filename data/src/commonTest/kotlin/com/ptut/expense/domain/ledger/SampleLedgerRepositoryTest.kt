package com.ptut.expense.domain.ledger

import kotlinx.coroutines.test.TestScope

/**
 * The fixture has to keep the same promises as the real ledger, or a screen developed against it
 * is developed against a lie.
 */
class SampleLedgerRepositoryTest : LedgerRepositoryContract() {
    override fun TestScope.newRepository(): LedgerRepository = SampleLedgerRepository(clock)
}

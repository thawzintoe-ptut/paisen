package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.Transaction

/**
 * The rows a snapshot holds, for a test that has already established the query answered.
 *
 * It throws on [LedgerSnapshot.Unanswered] rather than returning an empty list. Production code
 * deliberately has no such shortcut, and a test helper that quietly answered "no rows" for "not
 * asked yet" would hide exactly the confusion the type was introduced to end — a test would pass
 * against a ledger that never spoke.
 */
fun LedgerSnapshot.rows(): List<Transaction> = when (this) {
    LedgerSnapshot.Unanswered -> error("the ledger has not answered yet, so it holds no rows to read")
    is LedgerSnapshot.Rows -> transactions
}

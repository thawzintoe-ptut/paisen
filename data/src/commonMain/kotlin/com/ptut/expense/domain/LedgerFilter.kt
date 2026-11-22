package com.ptut.expense.domain

/**
 * FR-21. The six the Ledger offers, in the order the design draws them. Spending and Income read
 * the direction; the three behind "More filters" read how a row got there.
 *
 * Auto and By hand read [TransactionSource]; Edited reads [Transaction.editedAt]. They are three
 * facts and not three values of one enum, because a slip that was filed automatically and later
 * corrected is both auto and edited — collapsing them would make the reader choose which half of
 * the truth to see.
 */
enum class LedgerFilter {
    All,
    Spending,
    Auto,
    ByHand,
    Income,
    Edited,
}

fun LedgerFilter.accepts(transaction: Transaction): Boolean = when (this) {
    LedgerFilter.All -> true
    LedgerFilter.Spending -> transaction.direction == Direction.Expense
    LedgerFilter.Auto -> transaction.source.isAutomatic
    LedgerFilter.ByHand -> !transaction.source.isAutomatic
    LedgerFilter.Income -> transaction.direction == Direction.Income
    LedgerFilter.Edited -> transaction.isEdited
}

/**
 * Shop, note and amount (p15). The note is the one typed on Edit purchase (#128).
 *
 * [QueuedSlip] keeps its own `note`, which is slip meta rather than user text — it renders as
 * "14 Aug 19:41 · ref 0142K9" — so it is not searched here.
 *
 * The amount is matched against its formatted digits so "642" finds ฿642.50 the way the reader
 * would type it, and against the raw satang so nothing has to know which form they meant.
 */
fun Transaction.matches(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    if (counterparty?.contains(trimmed, ignoreCase = true) == true) return true
    if (memo?.contains(trimmed, ignoreCase = true) == true) return true
    val digits = trimmed.filter { it.isDigit() || it == '.' }
    return digits.isNotEmpty() && amount.format().contains(digits)
}

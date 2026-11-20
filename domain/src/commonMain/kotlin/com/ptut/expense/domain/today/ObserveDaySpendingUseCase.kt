package com.ptut.expense.domain.today

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.UsualDay
import com.ptut.expense.domain.expensesOn
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.spentOn
import com.ptut.expense.domain.usualDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** One category's part of a day's spending. A null [categoryId] is a row filed under none. */
data class CategoryTotal(val categoryId: String?, val total: Satang)

/** One home day's spending (#120). Income is left out, so [total] equals the day's "Spent". */
data class DaySpending(
    /** The purchases, earliest first. */
    val purchases: List<Transaction>,
    val total: Satang,
    /** A subtotal per category, biggest first. */
    val byCategory: List<CategoryTotal>,
    /** #119: the day beside a usual one of its weekday; null with too little history or nothing spent. */
    val usual: UsualDay?,
)

/** A day's spending as the ledger changes, for its receipt. */
class ObserveDaySpendingUseCase(private val ledger: LedgerRepository) {
    /** Null until the ledger answers. */
    operator fun invoke(day: LocalDate): Flow<DaySpending?> = ledger.allTransactions.map { snapshot ->
        when (snapshot) {
            LedgerSnapshot.Unanswered -> null
            is LedgerSnapshot.Rows -> snapshot.transactions.spendingOn(day)
        }
    }
}

private fun List<Transaction>.spendingOn(day: LocalDate): DaySpending {
    val purchases = expensesOn(day)
    return DaySpending(
        purchases = purchases,
        total = spentOn(day),
        byCategory = purchases
            .groupBy { it.categoryId }
            .map { (id, filed) -> CategoryTotal(id, filed.fold(Satang.Zero) { sum, row -> sum + row.amount }) }
            .sortedByDescending { it.total },
        usual = if (purchases.isEmpty()) null else usualDay(day),
    )
}

package com.ptut.expense.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.differencePhrase
import com.ptut.expense.domain.displayName
import com.ptut.expense.domain.format
import com.ptut.expense.domain.inCalendarDigits
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.spoken
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.domain.usualDay
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Clock

/** "08:12  7-Eleven Sathon ··· 128.00", read aloud as "08:12, 7-Eleven Sathon, 128.00 baht". */
data class ReceiptLineUiModel(val time: String, val shop: String, val amount: String, val spoken: String)

data class ReceiptSubtotalUiModel(val category: String, val amount: String)

sealed interface DayReceiptUiState {
    data object Loading : DayReceiptUiState

    data class Loaded(
        /** "Today", "Yesterday", or the day itself, "Thu 13 Aug". */
        val title: String,
        /** "FRI 14 AUG 2026 · BANGKOK" (p09). */
        val heading: String,
        val lines: List<ReceiptLineUiModel>,
        val subtotals: List<ReceiptSubtotalUiModel>,
        val total: String,
        /** "฿180 less than your usual Friday", or null with too little history (#119). */
        val closing: String?,
        /** Said in words when the day had no spending, rather than an empty paper. */
        val empty: String?,
    ) : DayReceiptUiState
}

/**
 * One Bangkok day as a receipt (#120, p09): each purchase with its time, a subtotal per category,
 * a ruled total. Spending only: income is not something a shop prints a receipt for, and leaving
 * it out keeps TOTAL equal to the day's "Spent".
 */
class DayReceiptViewModel(
    date: String,
    ledger: LedgerRepository,
    private val categories: List<Category> = Category.seed(),
    /** So a row typed under a sub-category is named the way Detail names it (#210). */
    private val subCategories: List<SubCategory> = emptyList(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val day = LocalDate.parse(date)

    val uiState: StateFlow<DayReceiptUiState> = ledger.allTransactions
        .map { snapshot ->
            when (snapshot) {
                LedgerSnapshot.Unanswered -> DayReceiptUiState.Loading
                is LedgerSnapshot.Rows -> receiptOf(snapshot.transactions)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayReceiptUiState.Loading)

    private fun receiptOf(rows: List<Transaction>): DayReceiptUiState.Loaded {
        val text = AppText.copy.receipt
        val spending = rows
            .filter { it.direction == Direction.Expense && it.occurredAtEpochMillis.dateIn(BangkokTime) == day }
            .sortedBy { it.occurredAtEpochMillis }
        val total = spending.fold(Satang.Zero) { sum, row -> sum + row.amount }
        return DayReceiptUiState.Loaded(
            title = title(),
            heading = "${day.dayLabel()} ${day.year.inCalendarDigits()}".uppercase() + " · ${text.city}",
            lines = spending.map { row ->
                val time = row.occurredAtEpochMillis.timeOfDayIn(BangkokTime)
                val name = row.displayName(categories, subCategories)
                ReceiptLineUiModel(
                    time = time,
                    shop = name,
                    amount = row.amount.digits(),
                    spoken = "$time, $name, ${row.amount.spoken()}",
                )
            },
            subtotals = spending
                .groupBy { it.categoryId }
                .map { (id, filed) -> (categories.firstOrNull { it.id == id }?.name ?: text.other) to filed.fold(Satang.Zero) { sum, row -> sum + row.amount } }
                .sortedByDescending { it.second }
                .map { (name, sum) -> ReceiptSubtotalUiModel(category = name, amount = sum.digits()) },
            total = total.format(),
            closing = rows.usualDay(day)?.differencePhrase(whose = "your")?.takeIf { spending.isNotEmpty() },
            empty = text.nothingSpent.takeIf { spending.isEmpty() },
        )
    }

    private fun title(): String {
        val today = clock.now().dateIn(BangkokTime)
        val common = AppText.copy.common
        return when (day) {
            today -> common.today
            today.minus(1, DateTimeUnit.DAY) -> common.yesterday
            else -> day.dayLabel()
        }
    }

    /** The receipt's lines carry no baht sign; only TOTAL does (p09). */
    private fun Satang.digits(): String = format().removePrefix("฿")
}

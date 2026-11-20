package com.ptut.expense.domain.record

import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.parseMinor

/** What still stops a typed row from being written. */
enum class DraftGap { Amount, Category, Rate }

/**
 * FR-14: the first gap, amount first, or null once the row can be written. Income needs a category too (plan §G
 * item 6), and a foreign amount must read in its own currency and is written only once [foreign] prices it.
 */
fun validateTransactionDraft(
    amount: Satang,
    typedAmount: String,
    currency: Currency,
    categoryId: String?,
    foreign: ForeignAmount?,
): DraftGap? = when {
    amount == Satang.Zero -> DraftGap.Amount
    !currency.isHome && parseMinor(typedAmount, currency) == null -> DraftGap.Amount
    categoryId == null -> DraftGap.Category
    !currency.isHome && foreign == null -> DraftGap.Rate
    else -> null
}

/**
 * #33: a foreign amount at its typed rate, or null while either is not a positive number. Home
 * money is never priced: it is what the rate is in.
 */
fun priceForeignAmount(currency: Currency, typedAmount: String, typedRate: String): ForeignAmount? {
    if (currency.isHome) return null
    val rate = Rate.parse(typedRate, currency.rateUnit) ?: return null
    val minor = parseMinor(typedAmount, currency)?.takeIf { it > 0 } ?: return null
    return ForeignAmount(currency.code, minor, rate)
}

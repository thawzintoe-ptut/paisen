package com.ptut.expense.domain.home

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.convert

/** #41: an empty ledger may change home at once; one with rows converts first (#47). */
class IsLedgerEmptyUseCase(private val book: HomeBook) {
    suspend operator fun invoke(): Result<Boolean> = runSuspendCatching { book.isEmpty() }
}

/** What choosing a new home currency did. */
sealed interface HomeCurrencyChange {
    /** The empty ledger took the new money; the shell is rebuilt in it. */
    data object Applied : HomeCurrencyChange

    /** The ledger has rows, so every amount needs a rate before the money changes (#47). */
    data object NeedsRate : HomeCurrencyChange

    /** A slip was filed between the two reads, so the book refused and nothing changed. */
    data object Refused : HomeCurrencyChange
}

/** #41: the ledger's money becomes [code]'s; the zone stays, so its days do not move. [around] wraps only the write. */
class ChangeHomeCurrencyUseCase(private val book: HomeBook) {
    suspend operator fun invoke(
        code: String,
        around: suspend (choose: suspend () -> Boolean) -> Boolean = { choose -> choose() },
    ): Result<HomeCurrencyChange> = runSuspendCatching {
        when {
            !book.isEmpty() -> HomeCurrencyChange.NeedsRate
            around { book.choose(homeIn(code)) } -> HomeCurrencyChange.Applied
            else -> HomeCurrencyChange.Refused
        }
    }
}

/**
 * #47: every amount moves to [code] at [rate] (the new money per the old's rate unit), in one
 * transaction. False when it could not be written, and then nothing changed.
 */
class ConvertHomeCurrencyUseCase(private val book: HomeBook) {
    suspend operator fun invoke(code: String, rate: Rate): Boolean =
        runSuspendCatching { book.convert(homeIn(code), rate) }.getOrDefault(false)
}

/** #47's example, in minor units: [from] of the old money becomes [to] of the new. */
data class ConversionPreview(val from: Long, val fromCurrency: Currency, val to: Long, val toCurrency: Currency)

/** #47: what 100 of the old money's rate unit becomes at [rate], so the reader can check the rate. */
class PreviewConversionUseCase {
    operator fun invoke(code: String, rate: Rate): ConversionPreview {
        val old = Currencies.of(Home.money.code)
        val sample = EXAMPLE_UNITS * old.rateUnit * minorPerWhole(Home.money)
        val new = Currencies.of(code)
        return ConversionPreview(sample, old, convert(sample, old, rate, new).toMinorUnits(), new)
    }

    private companion object {
        const val EXAMPLE_UNITS = 100L
    }
}

private fun homeIn(code: String): HomeChoice = Home.choice.value.withMoney(Currencies.of(code).toHomeMoney())

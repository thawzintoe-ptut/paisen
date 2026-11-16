package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.LedgerMetaDao
import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.entity.CurrencyRateEntity
import com.ptut.expense.data.local.entity.LedgerMetaEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.local.entity.foreignAmount
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.convert
import com.ptut.expense.domain.shared.then

/**
 * #41: the ledger_meta row is the record; the settings store holds a copy so launch never waits
 * on the database. Every change writes the row first, then the copy, then [Home].
 */
class RoomHomeBook(
    private val meta: LedgerMetaDao,
    private val transactions: TransactionDao,
    private val settings: ImportSettings,
    /** #47: where the typed rate is saved, so the next slip in the old money uses it. Null in tests. */
    private val rates: SharedDao? = null,
) : HomeBook {

    override fun atLaunch(): HomeChoice {
        val choices = settings.choices.value
        return choiceOf(choices.homeCurrency, choices.homeZone)
    }

    override suspend fun reconcile() {
        val stored = meta.read()
        if (stored == null) {
            // A ledger made before #41, or a first write that died before its row: the copy is the answer.
            val launch = atLaunch()
            meta.write(LedgerMetaEntity(currency = launch.money.code, zone = launch.zoneId))
            return
        }
        val recorded = choiceOf(stored.currency, stored.zone)
        if (recorded != Home.choice.value) {
            settings.setHome(stored.currency, stored.zone)
            Home.use(recorded)
        }
    }

    override suspend fun isEmpty(): Boolean = transactions.count() == 0

    override suspend fun choose(choice: HomeChoice): Boolean {
        if (!isEmpty()) return false
        meta.write(LedgerMetaEntity(currency = choice.money.code, zone = choice.zoneId))
        settings.setHome(choice.money.code, choice.zoneId)
        Home.use(choice)
        return true
    }

    override suspend fun convert(choice: HomeChoice, rate: Rate): Boolean {
        val old = Currencies.of(Home.money.code)
        val new = Currencies.of(choice.money.code)
        if (old.code == new.code) return choose(choice)
        val target = settings.choices.value.weeklyTarget
        try {
            meta.rehome(transactions.all().map { it.rehomed(old, new, rate) }, LedgerMetaEntity(currency = new.code, zone = choice.zoneId))
        } catch (failure: Exception) {
            return false
        }
        rates?.upsertRate(CurrencyRateEntity(old.code, rate.unscaled, rate.scale, 0L, home = new.code))
        settings.setHome(new.code, choice.zoneId)
        target?.let { settings.setWeeklyTarget(it.copy(amount = convert(it.amount.toMinorUnits(), old, rate, new))) }
        Home.use(choice)
        return true
    }

    /**
     * A row already in the new money becomes a home row again. Every other row's amount moves at
     * [rate] and keeps what it was: an old-home row is now a record in the old money, and a row in
     * a third money gets its two rates combined.
     */
    private fun TransactionEntity.rehomed(old: Currency, new: Currency, rate: Rate): TransactionEntity {
        val moved = { minor: Long -> convert(minor, old, rate, new).toMinorUnits() }
        val earlier = foreignAmount()
        return when {
            earlier != null && earlier.currency == new.code ->
                copy(amountMinor = earlier.originalMinor, slipAmountMinor = slipAmountMinor?.let(moved), currency = new.code, originalMinor = null, rateUnscaled = null, rateScale = null)

            earlier != null -> earlier.rate.then(rate).let { combined ->
                copy(amountMinor = moved(amountMinor), slipAmountMinor = slipAmountMinor?.let(moved), rateUnscaled = combined.unscaled, rateScale = combined.scale)
            }

            else ->
                copy(amountMinor = moved(amountMinor), slipAmountMinor = slipAmountMinor?.let(moved), currency = old.code, originalMinor = amountMinor, rateUnscaled = rate.unscaled, rateScale = rate.scale)
        }
    }

    private fun choiceOf(currency: String?, zone: String?): HomeChoice =
        if (currency == null) HomeChoice.Default else HomeChoice(Currencies.of(currency).toHomeMoney(), zone ?: HomeChoice.BANGKOK)
}

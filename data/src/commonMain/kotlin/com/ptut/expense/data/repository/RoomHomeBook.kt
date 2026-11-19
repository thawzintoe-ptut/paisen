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
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.convert
import com.ptut.expense.domain.shared.then
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * #41: the ledger_meta row is the record; the settings store holds a copy so launch never waits
 * on the database. Every change writes the row first, then the copy, then [Home], and nothing
 * that can fail runs after the row is committed.
 */
class RoomHomeBook(
    private val meta: LedgerMetaDao,
    private val transactions: TransactionDao,
    private val settings: AppSettings,
    /** #47: where the typed rate is saved, so the next slip in the old money uses it. Null in tests. */
    private val rates: SharedDao? = null,
    private val clock: Clock = Clock.System,
) : HomeBook {

    override fun atLaunch(): HomeChoice {
        val choices = settings.choices.value
        return choiceOf(choices.homeCurrency, choices.homeZone)
    }

    /**
     * The row wins over the copy. A copy left behind by a death between the row and the copy
     * (see [convert]) is corrected here; its weekly target, typed in the old money, is cleared.
     */
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
        if (!meta.writeIfEmpty(LedgerMetaEntity(currency = choice.money.code, zone = choice.zoneId))) return false
        settings.setHome(choice.money.code, choice.zoneId)
        Home.use(choice)
        return true
    }

    override suspend fun adopt(choice: HomeChoice) {
        meta.write(LedgerMetaEntity(currency = choice.money.code, zone = choice.zoneId))
        settings.setHome(choice.money.code, choice.zoneId)
        Home.use(choice)
    }

    /**
     * Everything that can fail — the arithmetic, the clash check, the transaction — runs before
     * the row is committed. After it, the copy and the saved rate are written on a best effort,
     * and [Home] always follows the rows. The money must differ: days never move with the rows.
     */
    override suspend fun convert(choice: HomeChoice, rate: Rate): Boolean {
        val old = Currencies.of(Home.money.code)
        val new = Currencies.of(choice.money.code)
        if (old.code == new.code) return false
        val rows = try {
            transactions.all().map { it.rehomed(old, new, rate) }
        } catch (overflow: IllegalStateException) {
            return false
        }
        // The F3 content key: two slips that round to one amount at this rate cannot both be kept.
        if (rows.filter { it.reference != null }.groupingBy { Triple(it.reference, it.amountMinor, it.occurredAtEpochMillis) }.eachCount().any { it.value > 1 }) return false
        val target = settings.choices.value.weeklyTarget?.let { it.copy(amount = convert(it.amount.toMinorUnits(), old, rate, new)) }
        try {
            meta.rehome(rows, LedgerMetaEntity(currency = new.code, zone = choice.zoneId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return false
        }
        runSuspendCatching { rates?.upsertRate(CurrencyRateEntity(old.code, rate.unscaled, rate.scale, clock.now().toEpochMilliseconds(), home = new.code)) }
        runCatching { settings.setHome(new.code, choice.zoneId, converted = target) }
        Home.use(choice)
        return true
    }

    /**
     * A row already in the new money becomes a home row again, to the minor unit it was read at.
     * Every other row's amount moves at [rate] and keeps what it was: an old-home row is now a
     * record in the old money, and a row in a third money is recomputed from its original at the
     * two rates combined, so a round trip comes back to the same figure. The slip's own figure
     * moves with the amount it was equal to, and at [rate] otherwise.
     */
    private fun TransactionEntity.rehomed(old: Currency, new: Currency, rate: Rate): TransactionEntity {
        val moved = { minor: Long -> convert(minor, old, rate, new).toMinorUnits() }
        val earlier = foreignAmount()
        val amount = when {
            earlier != null && earlier.currency == new.code -> earlier.originalMinor
            earlier != null -> convert(earlier.originalMinor, Currencies.of(earlier.currency), earlier.rate.then(rate), new).toMinorUnits()
            else -> moved(amountMinor)
        }
        val slip = slipAmountMinor?.let { if (it == amountMinor) amount else moved(it) }
        return when {
            earlier != null && earlier.currency == new.code ->
                copy(amountMinor = amount, slipAmountMinor = slip, currency = new.code, originalMinor = null, rateUnscaled = null, rateScale = null)

            earlier != null -> earlier.rate.then(rate).let { combined ->
                copy(amountMinor = amount, slipAmountMinor = slip, rateUnscaled = combined.unscaled, rateScale = combined.scale)
            }

            else ->
                copy(amountMinor = amount, slipAmountMinor = slip, currency = old.code, originalMinor = amountMinor, rateUnscaled = rate.unscaled, rateScale = rate.scale)
        }
    }

    private fun choiceOf(currency: String?, zone: String?): HomeChoice =
        if (currency == null) HomeChoice.Default else HomeChoice.of(Currencies.of(currency).toHomeMoney(), zone ?: HomeChoice.BANGKOK)
}

package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.slip.ImportStore

/** #33: the last rate saved per currency, shared by Add, slips and groups (#181). */
interface RateBook {
    suspend fun lastRate(currency: String): Rate?

    suspend fun saveRate(currency: String, rate: Rate)
}

/** [RateBook] over the importer's store, where the app keeps it. */
class ImportStoreRates(private val store: ImportStore) : RateBook {
    override suspend fun lastRate(currency: String): Rate? = store.rateFor(currency)

    override suspend fun saveRate(currency: String, rate: Rate) = store.saveRate(currency, rate)
}

package com.ptut.expense.domain.home

import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.shared.Rate

/**
 * #41: where the ledger's home currency and zone are kept, and the one thing that changes
 * [com.ptut.expense.domain.Home]. Home changes freely while the ledger is empty; with rows it
 * needs every amount converted, which is #47's job.
 */
interface HomeBook {

    /** The fast copy, read before the first frame. Baht and Bangkok on a phone set up before #41. */
    fun atLaunch(): HomeChoice

    /** Checks the fast copy against the ledger's own row, and the ledger wins. */
    suspend fun reconcile()

    suspend fun isEmpty(): Boolean

    /** Sets home on an empty ledger. False, and nothing changes, when the ledger has rows. */
    suspend fun choose(choice: HomeChoice): Boolean

    /**
     * #47: moves a ledger with rows to [choice], converting every amount at [rate] (the new home per
     * one of the old). Each row keeps what it was before, as a record in other money. False when
     * the conversion could not be written, and then nothing changed.
     */
    suspend fun convert(choice: HomeChoice, rate: Rate): Boolean
}

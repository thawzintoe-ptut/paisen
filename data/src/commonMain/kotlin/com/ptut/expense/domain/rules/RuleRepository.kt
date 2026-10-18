package com.ptut.expense.domain.rules

import kotlinx.coroutines.flow.StateFlow

/**
 * A remembered merchant, with the one number that makes the rule meaningful.
 *
 * [slipCount] is counted over the ledger rather than stored on the rule row. A stored counter
 * drifts the first time a transaction is deleted or edited, and a drifted count here is worse
 * than none: it is the figure the reader uses to decide how much automatic filing they would
 * lose by removing the rule. Same reasoning as F5 refusing a stored `lastImportAt`.
 */
data class RememberedMerchant(
    val id: String,
    val counterparty: String,
    val categoryId: String,
    val slipCount: Int,
    /** When the reader picked the category, for "You picked it for Foodland on 2 Aug" (#117). */
    val createdAtEpochMillis: Long = 0,
    /** Rows this rule filed without asking: "11 slips since went the same way" (#117). */
    val filedByRule: Int = 0,
    /** The reader's name for a shop the slip reader reads as noise; shown in its place. */
    val displayName: String? = null,
)

/**
 * FR-12. The rules seam — Room sits behind it, and the screen never sees a DAO.
 *
 * Neither operation here touches a transaction. FR-13: a rule decides where *future* slips land
 * and never rewrites what is already filed, so changing one is a promise about tomorrow rather
 * than an edit to the past.
 */
interface RuleRepository {

    val rules: StateFlow<List<RememberedMerchant>>

    /** The merchant keeps its history; only its next slip lands somewhere new. */
    suspend fun recategorise(id: String, categoryId: String)

    /** The past stays filed. The merchant is simply asked about again next time it appears. */
    suspend fun forget(id: String)

    /** A name for a shop read as noise. Nothing filed changes; every row shows the name from now on. */
    suspend fun rename(id: String, displayName: String?)
}

package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.RuleDao
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.rules.ShopNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * [RuleRepository] over Room. Mapping only — the rules about what an edit may touch live in the
 * SQL, which updates one column of one rule row and never reaches a transaction.
 */
class DefaultRuleRepository(
    private val ruleDao: RuleDao,
    scope: CoroutineScope,
) : RuleRepository {

    override val rules: StateFlow<List<RememberedMerchant>> = ruleDao.observeRemembered()
        .map { rows ->
            rows.map {
                RememberedMerchant(
                    id = it.id,
                    counterparty = it.counterparty,
                    categoryId = it.categoryId,
                    slipCount = it.slipCount,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    filedByRule = it.filedByRule,
                    displayName = it.displayName,
                )
            }
        }
        // Every screen that titles a row reads the names from here, so they follow each rename.
        .onEach(ShopNames::use)
        // Eagerly, like the ledger's flows: this is a single, so it outlives every screen reading it.
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun recategorise(id: String, categoryId: String) =
        ruleDao.recategorise(id, categoryId)

    override suspend fun forget(id: String) = ruleDao.forget(id)

    override suspend fun rename(id: String, displayName: String?) =
        ruleDao.rename(id, displayName?.trim()?.takeIf { it.isNotEmpty() })
}

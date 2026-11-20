package com.ptut.expense.domain.slip

import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.rules.forShop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * FR-12 and FR-13: one shop's rule as it changes, matched the engine's way, so a slip that printed
 * the name in another case still finds it. Null once it is forgotten, or if it never was.
 */
class ObserveShopRuleUseCase(private val rules: RuleRepository) {
    operator fun invoke(shop: String): Flow<RememberedMerchant?> = rules.rules.map { it.forShop(shop) }
}

/** FR-13: the shop's next slip lands in [categoryId]; its history stays filed. A shop with no rule is left alone. */
class RecategoriseShopUseCase(private val rules: RuleRepository) {
    suspend operator fun invoke(shop: String, categoryId: String) {
        val rule = rules.rules.value.forShop(shop) ?: return
        rules.recategorise(rule.id, categoryId)
    }
}

/** A name for a shop read as noise. Its rows show it at once; which slips it files does not change. */
class RenameShopUseCase(private val rules: RuleRepository) {
    suspend operator fun invoke(shop: String, name: String) {
        val rule = rules.rules.value.forShop(shop) ?: return
        rules.rename(rule.id, name)
    }
}

/** FR-12: the past stays filed, and the shop is asked about again next time. False when it had no rule. */
class ForgetShopUseCase(private val rules: RuleRepository) {
    suspend operator fun invoke(shop: String): Boolean {
        val rule = rules.rules.value.forShop(shop) ?: return false
        rules.forget(rule.id)
        return true
    }
}

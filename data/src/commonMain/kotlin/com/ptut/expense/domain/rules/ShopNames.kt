package com.ptut.expense.domain.rules

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's names for shops, keyed by what the slip read: noise they named, or a read name they
 * put right on Check slip. An object for the reason [com.ptut.expense.locale.AppText] is one: every
 * screen that titles a row reads it.
 */
object ShopNames {
    private val named = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Collected beside the rows, so a list redraws when a shop is named. */
    val names: StateFlow<Map<String, String>> = named.asStateFlow()

    fun use(rules: List<RememberedMerchant>) {
        named.value = rules.mapNotNull { rule -> rule.displayName?.let { rule.counterparty.key() to it } }.toMap()
    }

    /** The name for [counterparty], matched as the rule engine matches: case and edge space aside. */
    fun of(counterparty: String): String? = named.value[counterparty.key()]

    private fun String.key(): String = trim().lowercase()
}

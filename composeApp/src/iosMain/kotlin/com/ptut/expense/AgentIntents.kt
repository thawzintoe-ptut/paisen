package com.ptut.expense

import com.ptut.expense.agent.AgentEntry
import com.ptut.expense.domain.formatCompact
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * #206: what `AgentIntents.swift` calls. Siri and the Shortcuts app speak words, so each argument is
 * a String and Kotlin reads it; Swift never decides which category or group was meant.
 *
 * The two that open a sheet only leave it in `AgentInbox`, which the shell opens once it has drawn.
 */
private val intentScope = MainScope()

/** "Add expense": [amount] is the number Swift formatted with two decimals, or null. */
fun receiveAddExpense(amount: String?, category: String?, note: String?) {
    graphStarted
    intentScope.launch { entry().addExpense(amount, category, note) }
}

/** "Add shared expense": [group] is the name as spoken. */
fun receiveAddSharedExpense(group: String?, amount: String?, description: String?) {
    graphStarted
    intentScope.launch { entry().addSharedExpense(group, amount, description) }
}

/**
 * "How much did I spend": answered without opening the app. [period] is `today`, `week` or `month`;
 * [completion] gets the amount the way a sentence shows it, `฿1,250`. Swift writes the sentence
 * around it in the phone's language.
 */
fun spendSummary(period: String, completion: (String) -> Unit) {
    graphStarted
    intentScope.launch { completion(entry().spent(period).formatCompact()) }
}

private fun entry(): AgentEntry = KoinPlatform.getKoin().get()

package com.ptut.expense

import com.ptut.expense.agent.AgentEntry
import com.ptut.expense.domain.formatCompact
import org.koin.core.context.GlobalContext

/**
 * #213: what Gemini's AppFunctions call, from the debug service in `androidApp`. The Android twin
 * of `AgentIntents.kt` on iOS. `androidApp` carries no Koin, so it reaches the graph through here,
 * the way [receiveSharedSlip] does.
 *
 * The two that add only leave the action in `AgentInbox`; the shell opens the sheet that saves it.
 */
suspend fun receiveAddExpense(amount: String?, category: String?, note: String?) =
    entry().addExpense(amount, category, note)

/** [group] is the name as said, not an id. */
suspend fun receiveAddSharedExpense(group: String?, amount: String?, description: String?) =
    entry().addSharedExpense(group, amount, description)

/** [period] is `today`, `week` or `month`; the answer is the amount the way a sentence shows it, `฿1,250`. */
suspend fun spentIn(period: String): String = entry().spent(period).formatCompact()

private fun entry(): AgentEntry = GlobalContext.get().get()

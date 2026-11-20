package com.ptut.expense.domain.agent

import com.ptut.expense.agent.AgentInbox
import com.ptut.expense.agent.AgentLoop
import com.ptut.expense.agent.AgentOutcome
import com.ptut.expense.agent.AgentReader
import com.ptut.expense.agent.ProposedAction
import com.ptut.expense.agent.ReadCall
import com.ptut.expense.agent.SpendWindow
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.runSuspendCatching

/** What a Quick add sentence came to (#207). */
sealed interface QuickAddReading {
    /** A row to add: the Add form takes it. [missing] names, by tool parameter, what the sentence left out. */
    data class Fill(val action: ProposedAction.AddTransaction, val missing: Set<String>) : QuickAddReading

    /** Another screen's job. It now waits in [AgentInbox] for the shell to open that screen. */
    data object Handed : QuickAddReading

    /** A question, for [AnswerQuickAddQuestionUseCase]. */
    data class Question(val call: ReadCall) : QuickAddReading

    /** Nothing read, or the reader failed. */
    data object NotRead : QuickAddReading
}

/**
 * #207: a sentence in Burmese, Thai or English. Nothing is saved here: a row fills the Add form,
 * whose SAVE still writes it, and what another screen does goes to [AgentInbox].
 */
class InterpretQuickAddUseCase(private val loop: AgentLoop) {
    suspend operator fun invoke(text: String): QuickAddReading {
        // A reader that throws must not leave the field locked: it counts as a sentence not read.
        val outcome = runSuspendCatching { loop.run(text) }.getOrElse { return QuickAddReading.NotRead }
        val (call, missing) = when (outcome) {
            is AgentOutcome.Understood -> outcome.call to emptySet<String>()
            is AgentOutcome.Partly -> outcome.call to outcome.missing
            AgentOutcome.NotUnderstood -> return QuickAddReading.NotRead
        }
        return when (call) {
            is ProposedAction.AddTransaction -> QuickAddReading.Fill(call, missing)

            is ProposedAction -> {
                AgentInbox.offer(call)
                QuickAddReading.Handed
            }

            is ReadCall -> QuickAddReading.Question(call)
        }
    }
}

/** A Quick add question, answered from the ledger. */
sealed interface QuickAnswer {
    data class Spent(val window: SpendWindow, val amount: Satang) : QuickAnswer

    data class Found(val count: Int) : QuickAnswer
}

/**
 * #216: a question typed into Quick add, answered at once and read only. Null in a graph that
 * binds no reader, where the question goes unanswered.
 */
class AnswerQuickAddQuestionUseCase(private val reader: AgentReader? = null) {
    suspend operator fun invoke(call: ReadCall): Result<QuickAnswer>? {
        val asked = reader ?: return null
        return runSuspendCatching {
            when (call) {
                is ReadCall.SpendSummary -> QuickAnswer.Spent(call.window, asked.spent(call.window))
                is ReadCall.FindTransactions -> QuickAnswer.Found(asked.find(call).size)
            }
        }
    }
}

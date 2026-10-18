package com.ptut.expense.agent

/** What a typed sentence came to. Each one leaves the person a sheet to check or an answer to read. */
sealed interface AgentOutcome {
    data class Understood(val call: ToolCall) : AgentOutcome

    /** The sheet opens with what was read and asks for [missing]. */
    data class Partly(val call: ToolCall, val missing: Set<String>) : AgentOutcome

    data object NotUnderstood : AgentOutcome
}

/**
 * #207: normalise, then the grammar, then the model. The grammar answers most short commands with
 * no model at all; the model gets at most [MAX_MODEL_TURNS], and its second turn carries the error
 * [ToolCallCheck] gave the first. After that the form is the answer: nothing is ever saved here.
 */
class AgentLoop(
    private val normaliser: NormaliserSource,
    private val model: LanguageModelPort,
    private val context: suspend () -> AgentContext,
    private val grammar: CommandGrammar = CommandGrammar(),
    private val check: ToolCallCheck = ToolCallCheck(grammar),
) {

    suspend fun run(text: String): AgentOutcome {
        val sentence = normaliser.get().normalise(text)
        if (sentence.isEmpty()) return AgentOutcome.NotUnderstood
        val known = context()
        val reading = grammar.read(sentence, known.categories, known.groups, known.now, known.subCategories)
        if (reading is GrammarReading.Full) return AgentOutcome.Understood(reading.call)

        modelCall(sentence, known)?.let { return AgentOutcome.Understood(it) }

        return when (reading) {
            is GrammarReading.Partial -> AgentOutcome.Partly(reading.call, reading.missing)
            else -> AgentOutcome.NotUnderstood
        }
    }

    private suspend fun modelCall(sentence: String, known: AgentContext): ToolCall? {
        if (model.state.value !is ModelState.Ready) return null
        var feedback: String? = null
        repeat(MAX_MODEL_TURNS) {
            val reply = model.call(sentence, AgentTool.entries, feedback) ?: return null
            when (val checked = check.check(reply, known)) {
                is CheckedCall.Valid -> return checked.call
                is CheckedCall.Invalid -> feedback = checked.error
            }
        }
        return null
    }

    private companion object {
        const val MAX_MODEL_TURNS = 2
    }
}

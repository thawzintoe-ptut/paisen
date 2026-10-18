package com.ptut.expense.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #207: an on-device model that turns a sentence into one function call. FunctionGemma through
 * LiteRT-LM on Android (#207) and on iOS (#208); see docs/adr/0002-on-device-model.md.
 *
 * The model never touches the network. Getting it onto the phone is the store's job (Play for
 * On-device AI, Apple-hosted Background Assets), opt-in, on Wi-Fi only.
 */
interface LanguageModelPort {
    val state: StateFlow<ModelState>

    /**
     * One turn: the sentence, the tools, and on the second turn the error the first call earned.
     * Null when the model chose no tool.
     */
    suspend fun call(text: String, tools: List<AgentTool>, feedback: String? = null): ModelReply?

    /** Asks the store for the model pack. Does nothing unless [state] is [ModelState.NotInstalled]. */
    suspend fun download()

    suspend fun remove()
}

/** What the model answered, before any of it is trusted. [ToolCallCheck] turns it into a [ToolCall]. */
data class ModelReply(val function: String, val arguments: Map<String, String>)

sealed interface ModelState {
    /** This build cannot get the model at all. The app works as it did before #207. */
    data object NotOffered : ModelState

    data class NotInstalled(val sizeBytes: Long) : ModelState

    data class Downloading(val sizeBytes: Long, val doneBytes: Long) : ModelState

    data class Ready(val sizeBytes: Long) : ModelState

    /** The store refused or the phone has no room. [reason] is said to the person as it is. */
    data class Failed(val reason: String) : ModelState
}

/**
 * Both platforms until the model pack ships: neither build carries the pack or LiteRT-LM yet
 * (#207 and #208 are blocked on #205's eval numbers).
 */
class UnavailableLanguageModel : LanguageModelPort {
    private val notOffered = MutableStateFlow<ModelState>(ModelState.NotOffered)
    override val state: StateFlow<ModelState> = notOffered.asStateFlow()

    override suspend fun call(text: String, tools: List<AgentTool>, feedback: String?): ModelReply? = null

    override suspend fun download() = Unit

    override suspend fun remove() = Unit
}

/** FunctionGemma 270M as Google ships it for LiteRT-LM, 289 MB. Said on the Settings row before a download. */
const val FUNCTION_GEMMA_BYTES = 289_000_000L

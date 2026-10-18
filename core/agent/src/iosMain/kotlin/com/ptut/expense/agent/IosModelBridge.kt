package com.ptut.expense.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.resume

/**
 * #208: what only Swift can do for the model, handed over at launch the way `IosAuthBridge` is.
 * LiteRT-LM's Swift API is an early preview and the model comes as an Apple-hosted asset pack, so
 * Swift runs the model and fetches the pack; Kotlin keeps the loop and every check.
 *
 * Swift sees each `-> Unit` callback as `-> Void`.
 */
interface IosModelBridge {
    /** One of the `MODEL_*` words below, or a reason the model cannot run, said as it is. */
    fun availability(): String

    fun sizeBytes(): Long

    /** One turn. Calls back with the function name and its arguments as a JSON object, or an error. */
    fun call(prompt: String, toolsJson: String, feedback: String?, completion: (String?, String?, String?) -> Unit)

    /** Asks for the asset pack; calls back with an error, or null once it is on the phone. */
    fun download(completion: (String?) -> Unit)

    fun remove(completion: () -> Unit)
}

const val MODEL_READY = "ready"
const val MODEL_NOT_INSTALLED = "not_installed"
const val MODEL_DOWNLOADING = "downloading"

/** Set by Swift before the first frame. Null until Swift has a model to run, which is today. */
object IosModelBridges {
    var model: IosModelBridge? = null
}

/** Called from `iOSApp.init` once a bridge exists. */
fun installModelBridge(bridge: IosModelBridge) {
    IosModelBridges.model = bridge
}

/**
 * [LanguageModelPort] over the bridge. With no bridge it says [ModelState.NotOffered], so the iOS
 * app works exactly as it did after #206.
 */
class IosLanguageModel(private val bridge: () -> IosModelBridge? = { IosModelBridges.model }) : LanguageModelPort {
    private val current = MutableStateFlow(read())
    override val state: StateFlow<ModelState> = current.asStateFlow()

    override suspend fun call(text: String, tools: List<AgentTool>, feedback: String?): ModelReply? {
        val model = bridge() ?: return null
        return suspendCancellableCoroutine { done ->
            model.call(text, tools.schemaJson(), feedback) { function, arguments, error ->
                done.resume(if (error == null && function != null) ModelReply(function, argumentsOf(arguments)) else null)
            }
        }
    }

    override suspend fun download() {
        val model = bridge() ?: return
        if (current.value !is ModelState.NotInstalled) return
        current.value = ModelState.Downloading(model.sizeBytes(), doneBytes = 0)
        val error = suspendCancellableCoroutine { done -> model.download { done.resume(it) } }
        current.value = if (error == null) read() else ModelState.Failed(error)
    }

    override suspend fun remove() {
        val model = bridge() ?: return
        suspendCancellableCoroutine { done -> model.remove { done.resume(Unit) } }
        current.value = read()
    }

    private fun read(): ModelState {
        val model = bridge() ?: return ModelState.NotOffered
        return when (val said = model.availability()) {
            MODEL_READY -> ModelState.Ready(model.sizeBytes())
            MODEL_NOT_INSTALLED -> ModelState.NotInstalled(model.sizeBytes())
            MODEL_DOWNLOADING -> ModelState.Downloading(model.sizeBytes(), doneBytes = 0)
            else -> ModelState.Failed(said)
        }
    }

    /** A model's arguments are a guess; anything that is not a flat JSON object is no arguments. */
    private fun argumentsOf(json: String?): Map<String, String> {
        val parsed = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return emptyMap()
        return parsed.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.content?.let { key to it } }.toMap()
    }
}

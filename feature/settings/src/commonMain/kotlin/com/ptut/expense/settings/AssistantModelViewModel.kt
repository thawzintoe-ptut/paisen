package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.ModelState
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssistantAction { Download, Remove }

data class AssistantModelUiState(
    val status: String,
    /** What the one button does, or null while nothing can be done here. */
    val action: AssistantAction?,
)

/**
 * #207: the model's one screen. It says how big the model is before anything is fetched, and it
 * removes it again. The store does the download; the app never opens a connection for it (ADR 0002).
 */
class AssistantModelViewModel(private val model: LanguageModelPort) : ViewModel() {

    val uiState: StateFlow<AssistantModelUiState> = model.state
        .map(::uiStateOf)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), uiStateOf(model.state.value))

    fun act(action: AssistantAction) {
        viewModelScope.launch {
            when (action) {
                AssistantAction.Download -> model.download()
                AssistantAction.Remove -> model.remove()
            }
        }
    }
}

/** The Settings row's line and this screen's status, so both say the same thing. */
fun ModelState.statusLine(): String = AppText.copy.settings.let { text ->
    when (this) {
        ModelState.NotOffered -> text.modelNotOffered
        is ModelState.NotInstalled -> text.modelNotInstalled(megabytes(sizeBytes))
        is ModelState.Downloading -> text.modelDownloading(megabytes(doneBytes), megabytes(sizeBytes))
        is ModelState.Ready -> text.modelReady(megabytes(sizeBytes))
        is ModelState.Failed -> reason
    }
}

private fun uiStateOf(state: ModelState) = AssistantModelUiState(
    status = state.statusLine(),
    action = when (state) {
        is ModelState.NotInstalled, is ModelState.Failed -> AssistantAction.Download
        is ModelState.Ready -> AssistantAction.Remove
        ModelState.NotOffered, is ModelState.Downloading -> null
    },
)

private fun megabytes(bytes: Long): String = "${bytes / BYTES_PER_MB} MB"

private const val BYTES_PER_MB = 1_000_000L

package com.ptut.expense.domain.settings

import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** #207: the assistant model's state as it changes; not offered in a graph that binds no model. */
class ObserveAssistantModelUseCase(private val model: LanguageModelPort? = null) {
    operator fun invoke(): StateFlow<ModelState> = model?.state ?: MutableStateFlow(ModelState.NotOffered)
}

/** #207: asks the store for the model pack. Does nothing unless the model is not installed. */
class DownloadAssistantModelUseCase(private val model: LanguageModelPort) {
    suspend operator fun invoke() = model.download()
}

/** #207: takes the model pack off the phone. */
class RemoveAssistantModelUseCase(private val model: LanguageModelPort) {
    suspend operator fun invoke() = model.remove()
}

package com.ptut.expense.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The reader's stored choices as they change, or the defaults in a graph that binds no settings. */
class ObserveAppChoicesUseCase(private val settings: AppSettings? = null) {
    operator fun invoke(): StateFlow<AppChoices> = settings?.choices ?: MutableStateFlow(AppChoices())
}

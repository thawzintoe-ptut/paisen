package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One thing that ships inside the app. [file] is its licence under `composeResources`, or null for a notice. */
data class LicenceUiModel(
    val name: String,
    val licence: String,
    val covers: String,
    val file: String?,
    val notice: String? = null,
)

data class LicencesUiState(
    val licences: List<LicenceUiModel>,
    /** The one open, by name, and its full text once read. */
    val open: String? = null,
    val text: String? = null,
)

/**
 * #205 and #207: the terms of what ships inside the app. myanmar-tools is Apache 2.0, which asks
 * for its licence to travel with it; Gemma's terms ask for their notice beside the model.
 *
 * [readText] reads a file from the app's resources. A text is read only when its row is opened.
 */
class LicencesViewModel(
    private val readText: suspend (String) -> String,
    /** True once the model can be offered, which is when Gemma's terms apply. */
    gemma: Boolean,
) : ViewModel() {

    private val state = MutableStateFlow(LicencesUiState(licences(gemma)))
    val uiState: StateFlow<LicencesUiState> = state.asStateFlow()

    /** A second tap on the open row closes it. */
    fun toggle(licence: LicenceUiModel) {
        if (state.value.open == licence.name) {
            state.update { it.copy(open = null, text = null) }
            return
        }
        state.update { it.copy(open = licence.name, text = licence.notice) }
        val file = licence.file ?: return
        viewModelScope.launch {
            val text = readText(file)
            state.update { if (it.open == licence.name) it.copy(text = text) else it }
        }
    }

    private fun licences(gemma: Boolean) = AppText.copy.settings.let { text ->
        listOfNotNull(
            LicenceUiModel("Geist", "SIL Open Font License 1.1", text.coversTypeface, "files/geist_ofl.txt"),
            LicenceUiModel("Geist Mono", "SIL Open Font License 1.1", text.coversFigures, "files/geist_mono_ofl.txt"),
            LicenceUiModel("Heroicons", "MIT License", text.coversIcons, "files/heroicons_mit.txt"),
            LicenceUiModel("myanmar-tools", "Apache License 2.0", text.coversZawgyi, "files/myanmar_tools_apache.txt"),
            LicenceUiModel(
                name = "Gemma",
                licence = "Gemma Terms of Use",
                covers = text.coversModel,
                file = null,
                notice = "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms.",
            ).takeIf { gemma },
        )
    }
}

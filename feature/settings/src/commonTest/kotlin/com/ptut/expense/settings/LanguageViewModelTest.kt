package com.ptut.expense.settings

import com.ptut.expense.ViewModelTest
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LanguageStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageViewModelTest : ViewModelTest() {

    @AfterTest
    fun backToEnglish() {
        AppText.use(Language.English)
        AppText.settingsReopened()
    }

    @Test
    fun `a release build lists Thai after Burmese`() {
        val viewModel = LanguageViewModel(offered = Language.offered(debug = false))

        assertEquals(listOf("English", "မြန်မာ", "ไทย"), viewModel.uiState.value.options.map { it.label })
    }

    @Test
    fun `choosing Thai saves it and shows the app in Thai`() = runTest {
        val store = MemoryLanguageStore()
        val viewModel = LanguageViewModel(store = store, offered = Language.offered(debug = true))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.choose(Language.Thai)
        runCurrent()

        assertEquals(Language.Thai, store.read())
        assertEquals(Language.Thai, AppText.language.value)
        assertEquals(Language.Thai, viewModel.uiState.value.options.single { it.chosen }.language)
    }

    private class MemoryLanguageStore : LanguageStore {
        private var saved: Language? = null

        override fun read(): Language? = saved

        override fun write(language: Language) {
            saved = language
        }
    }
}

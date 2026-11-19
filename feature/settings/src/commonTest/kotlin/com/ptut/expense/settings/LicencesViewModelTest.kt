package com.ptut.expense.settings

import com.ptut.expense.ViewModelTest
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LicencesViewModelTest : ViewModelTest() {

    @Test
    fun `opening a row reads its licence text`() = runTest {
        val viewModel = LicencesViewModel(readText = { file -> "text of $file" }, gemma = false)
        val geist = viewModel.uiState.value.licences.first()

        viewModel.toggle(geist)
        runCurrent()

        assertEquals("text of ${geist.file}", viewModel.uiState.value.text)
    }

    @Test
    fun `a licence file that cannot be read says so in the open row`() = runTest {
        val viewModel = LicencesViewModel(readText = { error("missing resource") }, gemma = false)
        val geist = viewModel.uiState.value.licences.first()

        viewModel.toggle(geist)
        runCurrent()

        assertEquals(geist.name, viewModel.uiState.value.open)
        assertEquals(AppText.copy.settings.licenceNotOpened, viewModel.uiState.value.text)
    }
}

package com.ptut.expense

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * What a `ViewModel` needs from a test host. `viewModelScope` collects on `Dispatchers.Main`, and
 * no test host provides one, so every `uiState` collection would fail before the first assertion.
 *
 * Main is bound before `runTest` on purpose: `runTest` then adopts this dispatcher's scheduler
 * instead of making its own, so virtual time stays in one place and the assertions read exactly
 * as they did when the scope was handed in from outside.
 *
 * Resetting in `@AfterTest` rather than inside the test matters too — the scope outlives the test
 * body, and unbinding Main while it is still being cancelled throws from the dispatcher.
 */
abstract class ViewModelTest {

    @BeforeTest
    fun bindMainToTheTestScheduler() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun unbindMain() {
        Dispatchers.resetMain()
    }

    /** #41: a test that moved the home, or failed before it could move it back, leaves baht for the next. */
    @AfterTest
    fun backToBaht() {
        Home.use(HomeChoice.Default)
        Home.settingsReopened()
    }

    /** A test that switched the language, or failed before it could switch back, leaves English for the next. */
    @AfterTest
    fun backToEnglish() {
        AppText.use(Language.English)
        AppText.settingsReopened()
    }
}

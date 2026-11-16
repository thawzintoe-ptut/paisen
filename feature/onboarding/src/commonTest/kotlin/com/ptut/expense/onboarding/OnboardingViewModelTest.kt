package com.ptut.expense.onboarding

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.home.FakeHomeBook
import com.ptut.expense.domain.shared.Currencies
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest : ViewModelTest() {

    private val flag = FakeOnboardingFlag()
    private val onboarding = Onboarding(flag)
    private val photoAccess = FakePhotoAccessRequest()

    /**
     * The exit the issue names. §4 has no accounts to create, so first run's only job is to be
     * answered — and NFR-2's manual path does not need the folder, so declining is a whole answer.
     */
    @Test
    fun `skipping first run still finishes it`() = runTest {
        viewModel().skip()

        assertTrue(onboarding.isFinished.value)
        assertEquals(0, photoAccess.asks, "skipping raised the photo dialog anyway")
    }

    // #41

    private val kyat = HomeChoice(Currencies.of("MMK").toHomeMoney(), "Asia/Yangon")

    @Test
    fun `first run offers the phone's home and sets it on the way out`() = runTest {
        val book = FakeHomeBook()
        val viewModel = OnboardingViewModel(onboarding, photoAccess, homeBook = book, suggestedHome = kyat)
        assertEquals(kyat, viewModel.homeChoice.value)

        viewModel.chooseHome(Currencies.of("USD"))
        viewModel.skip()
        runCurrent()

        assertEquals(listOf(HomeChoice(Currencies.of("USD").toHomeMoney(), "Asia/Yangon")), book.chosen)
        assertTrue(onboarding.isFinished.value)
        Home.use(HomeChoice.Default)
    }

    @Test
    fun `a build with no home to offer shows no line and sets nothing`() = runTest {
        val book = FakeHomeBook()
        val viewModel = OnboardingViewModel(onboarding, photoAccess, homeBook = book)

        viewModel.skip()

        assertEquals(null, viewModel.homeChoice.value)
        assertTrue(book.chosen.isEmpty())
    }

    /**
     * FR-2. The dialog is raised whatever the reader then answers, and first run ends either way —
     * a refusal leaves the ledger fillable by hand rather than leaving the reader on this screen.
     */
    @Test
    fun `allowing photo access raises the dialog and then finishes first run`() = runTest {
        viewModel().requestPhotoAccess()
        runCurrent()

        assertEquals(1, photoAccess.asks)
        assertTrue(onboarding.isFinished.value)
    }

    /** The answer has to be in before Today is, or the first scan runs against a live dialog. */
    @Test
    fun `first run stays up while the dialog is open`() = runTest {
        photoAccess.holdOpen()

        viewModel().requestPhotoAccess()
        runCurrent()

        assertFalse(onboarding.isFinished.value, "first run ended before the dialog was answered")
    }

    /**
     * A double tap on the primary button. Without the guard the second ask resolves at once against
     * the dialog already showing, which finishes first run under the reader's finger.
     */
    @Test
    fun `a second tap while the dialog is up does not raise it twice`() = runTest {
        photoAccess.holdOpen()
        val viewModel = viewModel()

        viewModel.requestPhotoAccess()
        runCurrent()
        viewModel.requestPhotoAccess()
        runCurrent()

        assertEquals(1, photoAccess.asks)

        photoAccess.answer()
        runCurrent()

        assertTrue(onboarding.isFinished.value)
    }

    private fun viewModel() = OnboardingViewModel(onboarding = onboarding, photoAccess = photoAccess)
}

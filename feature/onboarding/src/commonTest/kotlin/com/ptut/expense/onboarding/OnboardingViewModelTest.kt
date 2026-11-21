package com.ptut.expense.onboarding

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.backup.BackupPreview
import com.ptut.expense.domain.backup.BackupProblem
import com.ptut.expense.domain.backup.BackupUnreadable
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.MergeReport
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.folders.ApplyWatchedFoldersUseCase
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FakeFolders
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.RefreshFolderCatalogUseCase
import com.ptut.expense.domain.home.ChooseHomeUseCase
import com.ptut.expense.domain.home.FakeHomeBook
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.MarkWelcomedUseCase
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class OnboardingViewModelTest : ViewModelTest() {

    private val flag = FakeOnboardingFlag()
    private val onboarding = Onboarding(flag)
    private val photoAccess = FakePhotoAccessRequest()

    /**
     * The exit the issue names. §4 has no accounts to create, so first run's only job is to be
     * answered — and NFR-2's manual path does not need the folder, so declining is a whole answer.
     */
    @Test
    fun `skipping photos still reaches the end of first run`() = runTest {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.skip()
        viewModel.finish()
        runCurrent()

        assertTrue(onboarding.isFinished.value)
        assertEquals(0, photoAccess.asks, "skipping raised the photo dialog anyway")
    }

    // #41

    /** What a phone set to Yangon time gets on first run: baht, in its own zone. */
    private val bahtInYangon = HomeChoice(Currencies.Thb.toHomeMoney(), "Asia/Yangon")

    @Test
    fun `first run sets the home it was given on the way out`() = runTest {
        val book = FakeHomeBook()
        val viewModel = OnboardingViewModel(onboarding, photoAccess, chooseHomeUseCase = ChooseHomeUseCase(book), firstRunHome = bahtInYangon)

        viewModel.finish()
        runCurrent()

        assertEquals(listOf(bahtInYangon), book.chosen)
        assertTrue(onboarding.isFinished.value)
    }

    @Test
    fun `a restore with the launch home as the suggestion writes no home`() = runTest {
        val book = FakeHomeBook()
        val viewModel = OnboardingViewModel(
            onboarding,
            photoAccess,
            restoreSteps = RestoreDriver(FakeArchive()),
            files = FakeFiles(),
            chooseHomeUseCase = ChooseHomeUseCase(book),
            firstRunHome = Home.choice.value,
        )

        viewModel.restore()
        runCurrent()
        viewModel.confirmRestore()
        runCurrent()

        assertTrue(book.chosen.isEmpty())
        assertTrue(onboarding.isFinished.value)
    }

    @Test
    fun `a restore with another home writes it before the file merges`() = runTest {
        val book = FakeHomeBook()
        val archive = FakeArchive()
        val viewModel = OnboardingViewModel(onboarding, photoAccess, restoreSteps = RestoreDriver(archive), files = FakeFiles(), chooseHomeUseCase = ChooseHomeUseCase(book), firstRunHome = bahtInYangon)

        viewModel.restore()
        runCurrent()
        viewModel.confirmRestore()
        runCurrent()

        assertEquals(listOf(bahtInYangon), book.chosen)
        assertEquals(bahtInYangon, archive.homeAtRestore)
    }

    /** The platform's own text is English and says nothing the reader can act on. */
    @Test
    fun `a restore that fails shows the copy and not the exception text`() = runTest {
        val viewModel = OnboardingViewModel(onboarding, photoAccess, restoreSteps = RestoreDriver(FakeArchive(fails = IllegalStateException("database is locked"))), files = FakeFiles())

        viewModel.restore()
        runCurrent()

        assertEquals(AppText.copy.onboarding.restoreFailed, viewModel.restoreProblem.value)
        assertFalse(viewModel.isRestoring.value)
    }

    @Test
    fun `a file the archive refuses keeps the refusal copy`() = runTest {
        val refusal = BackupUnreadable(BackupProblem.NotReadable)
        val viewModel = OnboardingViewModel(onboarding, photoAccess, restoreSteps = RestoreDriver(FakeArchive(fails = refusal)), files = FakeFiles())

        viewModel.restore()
        runCurrent()

        assertEquals(AppText.copy.phrases.backupNotReadable, viewModel.restoreProblem.value)
    }

    @Test
    fun `a photo dialog that throws says so and lets the reader tap again`() = runTest {
        val flaky = FlakyPhotoAccess()
        val viewModel = OnboardingViewModel(onboarding, flaky)

        viewModel.requestPhotoAccess()
        runCurrent()
        assertEquals(AppText.copy.settings.didNotWork, viewModel.restoreProblem.value)
        assertFalse(onboarding.isFinished.value)

        viewModel.requestPhotoAccess()
        runCurrent()
        assertEquals(2, flaky.asks)
        assertEquals(SlipAccess.Granted(emptyList()), viewModel.uiState.value.access)
    }

    /** Throws on the first ask, as a platform with no activity to show the dialog on would. */
    private class FlakyPhotoAccess : PhotoAccessRequest {
        var asks = 0
            private set

        override suspend fun ask() {
            asks++
            check(asks > 1) { "no activity" }
        }
    }

    private class FakeArchive(private val fails: Exception? = null) : LedgerArchive {
        var homeAtRestore: HomeChoice? = null

        override suspend fun export(): String = ""

        override suspend fun preview(json: String): BackupPreview = fails?.let { throw it } ?: BackupPreview(transactions = 1, oldestEpochMillis = null, groups = 0, categories = 0, subCategories = 0, shopRules = 0)

        override suspend fun restore(json: String): MergeReport {
            homeAtRestore = Home.choice.value
            return MergeReport(added = 1, alreadyHeld = 0)
        }
    }

    private class FakeFiles : LedgerFileStore {
        override suspend fun write(suggestedName: String, contents: String): String? = null

        override suspend fun read(): String = "{}"
    }

    @Test
    fun `a build with no home to offer sets nothing`() = runTest {
        val book = FakeHomeBook()
        val viewModel = OnboardingViewModel(onboarding, photoAccess, chooseHomeUseCase = ChooseHomeUseCase(book))

        viewModel.finish()

        assertTrue(book.chosen.isEmpty())
    }

    @Test
    fun `allowing photo access raises the dialog and does not end first run`() = runTest {
        val viewModel = viewModel()
        viewModel.requestPhotoAccess()
        runCurrent()

        assertEquals(1, photoAccess.asks)
        assertFalse(onboarding.isFinished.value)
        assertEquals(SlipAccess.Granted(emptyList()), viewModel.uiState.value.access)
    }

    /** The answer has to be in before Today is, or the first scan runs against a live dialog. */
    @Test
    fun `first run stays up while the dialog is open`() = runTest {
        photoAccess.holdOpen()

        val viewModel = viewModel()
        viewModel.requestPhotoAccess()
        runCurrent()

        assertEquals(SlipAccess.Unasked, viewModel.uiState.value.access, "the folders were read before the dialog was answered")
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

        assertEquals(SlipAccess.Granted(emptyList()), viewModel.uiState.value.access)
    }

    @Test
    fun `the steps run promise then slips then goal`() = runTest {
        val viewModel = viewModel()
        assertEquals(OnboardingStep.Promise, viewModel.uiState.value.step)

        viewModel.next()
        assertEquals(OnboardingStep.Slips, viewModel.uiState.value.step)

        viewModel.skip()
        assertEquals(OnboardingStep.Goal, viewModel.uiState.value.step)
        assertFalse(onboarding.isFinished.value, "first run ended before the goal step was answered")

        viewModel.finish()
        runCurrent()
        assertTrue(onboarding.isFinished.value)
    }

    // #102

    private val book = FakePlayBook()
    private val settings = appSettings()
    private val homeToday = GetHomeTodayUseCase(FixedClock(LocalDateTime(2026, 11, 21, 12, 0).toInstant(TimeZone.of("Asia/Bangkok"))))

    private class FixedClock(private val at: Instant) : Clock {
        override fun now(): Instant = at
    }

    private fun goalViewModel() = OnboardingViewModel(
        onboarding,
        photoAccess,
        saveGoal = SaveGoalUseCase(book, homeToday),
        markWelcomed = MarkWelcomedUseCase(settings, homeToday),
    )

    @Test
    fun `allow photos on step one asks and then shows step two`() = runTest {
        val viewModel = viewModel()

        viewModel.requestPhotoAccess()
        photoAccess.answer()
        runCurrent()

        assertEquals(OnboardingStep.Slips, viewModel.uiState.value.step)
        assertEquals(2, viewModel.uiState.value.stepNumber)
    }

    @Test
    fun `by hand on step one opens Today without asking for photos`() = runTest {
        val viewModel = goalViewModel()

        viewModel.finish()
        runCurrent()

        assertTrue(onboarding.isFinished.value)
        assertEquals(0, photoAccess.asks)
        assertTrue(book.goals.value.isEmpty())
    }

    @Test
    fun `back goes one step back and stops at the first`() = runTest {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.next()
        assertEquals(OnboardingStep.Goal, viewModel.uiState.value.step)

        viewModel.back()
        assertEquals(OnboardingStep.Slips, viewModel.uiState.value.step)
        viewModel.back()
        viewModel.back()
        assertEquals(OnboardingStep.Promise, viewModel.uiState.value.step)
    }

    @Test
    fun `start my jar saves the picked goal and opens Today`() = runTest {
        val viewModel = goalViewModel()
        viewModel.next()
        viewModel.skip()
        viewModel.chooseGoal(GoalPreset.Trip)
        viewModel.setGoalAmount("฿5,000")
        assertTrue(viewModel.uiState.value.canStartJar)

        viewModel.startJar()
        runCurrent()

        val goal = book.goals.value.single()
        assertEquals(AppText.copy.onboarding.goalTrip, goal.name)
        assertEquals(Satang(500_000), goal.target)
        assertTrue(onboarding.isFinished.value)
    }

    @Test
    fun `a goal with no amount cannot start a jar`() = runTest {
        val viewModel = goalViewModel()
        viewModel.chooseGoal(GoalPreset.Cushion)

        viewModel.startJar()
        runCurrent()

        assertFalse(viewModel.uiState.value.canStartJar)
        assertFalse(onboarding.isFinished.value)
        assertTrue(book.goals.value.isEmpty())
    }

    @Test
    fun `a second tap on the picked goal lets it go`() = runTest {
        val viewModel = viewModel()
        viewModel.chooseGoal(GoalPreset.SomethingNew)
        viewModel.chooseGoal(GoalPreset.SomethingNew)

        assertNull(viewModel.uiState.value.goal)
    }

    @Test
    fun `skipping the goal leaves no jar and marks the day first run ended`() = runTest {
        val viewModel = goalViewModel()
        viewModel.chooseGoal(GoalPreset.Trip)
        viewModel.setGoalAmount("200")

        viewModel.finish()
        runCurrent()

        assertTrue(book.goals.value.isEmpty())
        assertEquals("2026-11-21", settings.choices.value.play.welcomedOn)
    }

    @Test
    fun `next on a later step does not go back to slips`() = runTest {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.skip()
        viewModel.next()

        assertEquals(OnboardingStep.Goal, viewModel.uiState.value.step)
    }

    @Test
    fun `no folders after the dialog is a refusal with a way on`() = runTest {
        val settings = FakePhotoSettings()
        val viewModel = folderViewModel(offered = emptyList(), photoSettings = settings)
        viewModel.next()

        viewModel.requestPhotoAccess()
        runCurrent()
        assertEquals(SlipAccess.Denied, viewModel.uiState.value.access)
        assertTrue(viewModel.canOpenSettings)

        viewModel.openPhoneSettings()
        assertEquals(1, settings.opens)

        viewModel.skip()
        assertEquals(OnboardingStep.Goal, viewModel.uiState.value.step)
    }

    @Test
    fun `access given in the phone settings shows on return`() = runTest {
        val catalog = FakeCatalog(emptyList())
        val viewModel = folderViewModel(catalog = catalog)
        viewModel.requestPhotoAccess()
        runCurrent()
        assertEquals(SlipAccess.Denied, viewModel.uiState.value.access)

        catalog.offers = listOf(kplus)
        viewModel.refreshAccess()
        runCurrent()

        assertEquals(SlipAccess.Granted(listOf(kplus)), viewModel.uiState.value.access)
    }

    @Test
    fun `a chosen folder is watched and a second pick replaces it`() = runTest {
        val folders = FakeFolders()
        val viewModel = folderViewModel(offered = listOf(kplus, camera), folders = folders)
        viewModel.requestPhotoAccess()
        runCurrent()

        viewModel.chooseFolder(kplus.id)
        runCurrent()
        assertEquals(SlipAccess.Granted(listOf(kplus, camera), chosen = kplus.id), viewModel.uiState.value.access)
        assertEquals(listOf(kplus.id), folders.folders.value.filter { it.watched }.map { it.id })

        viewModel.chooseFolder(camera.id)
        runCurrent()
        assertEquals(listOf(camera.id), folders.folders.value.filter { it.watched }.map { it.id })
    }

    @Test
    fun `a folder the list does not hold is ignored`() = runTest {
        val folders = FakeFolders()
        val viewModel = folderViewModel(offered = listOf(kplus), folders = folders)
        viewModel.requestPhotoAccess()
        runCurrent()

        viewModel.chooseFolder("gone")
        runCurrent()

        assertTrue(folders.folders.value.isEmpty())
    }

    private val kplus = DeviceFolder(id = "1", name = "K PLUS", path = "Pictures/K PLUS", imageCount = 12)
    private val camera = DeviceFolder(id = "2", name = "Camera", path = "DCIM/Camera", imageCount = 300)

    private class FakeCatalog(var offers: List<DeviceFolder>) : FolderCatalog {
        override suspend fun available(): List<DeviceFolder> = offers
    }

    private fun folderViewModel(
        offered: List<DeviceFolder> = emptyList(),
        catalog: FakeCatalog = FakeCatalog(offered),
        folders: FakeFolders = FakeFolders(),
        photoSettings: FakePhotoSettings? = null,
    ) = OnboardingViewModel(
        onboarding = onboarding,
        photoAccess = photoAccess,
        refreshCatalog = RefreshFolderCatalogUseCase(catalog),
        applyWatchedFolders = ApplyWatchedFoldersUseCase(folders),
        photoSettings = photoSettings,
    )

    private fun viewModel() = OnboardingViewModel(onboarding = onboarding, photoAccess = photoAccess)
}

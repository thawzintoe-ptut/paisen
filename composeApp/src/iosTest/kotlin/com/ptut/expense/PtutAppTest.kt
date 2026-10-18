package com.ptut.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ptut.expense.di.AppBuild
import com.ptut.expense.di.viewModelModule
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.MergeReport
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.SampleLedgerRepository
import com.ptut.expense.domain.settings.FakeImportSettingsStore
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.nav.BarDestination
import com.ptut.expense.onboarding.FakeOnboardingFlag
import com.ptut.expense.onboarding.FakePhotoAccessRequest
import com.ptut.expense.onboarding.FakePhotoSettings
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.settings.NEEDS_ATTENTION
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

private const val HEALTHY = 2 * 60 * 1000L
private const val FOUR_DAYS = 4 * 24 * 60 * 60 * 1000L

@OptIn(ExperimentalTestApi::class)
class PtutAppTest {

    /**
     * `KoinApplication` is not the isolated container it reads as. Its loader starts the *global*
     * context the first time and every later composition re-attaches to that one, modules and all
     * — so without this teardown every test after the first runs against the first test's graph,
     * including the ledger the add-sheet test wrote a row into.
     */
    @AfterTest
    fun stopKoinSoTheNextTestGetsItsOwnGraph() {
        if (KoinPlatform.getKoinOrNull() != null) KoinPlatform.stopKoin()
    }

    private fun dayList() = hasScrollToNodeAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    /** The real ViewModel wiring over a populated fixture, one graph per test. */
    @Composable
    private fun TestApp(
        sinceLastImport: Long? = HEALTHY,
        firstRunAnswered: Boolean = true,
    ) = KoinApplication(
        configuration = koinConfiguration {
            modules(
                viewModelModule,
                module {
                    single<LedgerRepository> { SampleLedgerRepository(sinceLastImport = sinceLastImport) }
                    // Settings resolves all three to draw its rows, and the release build is
                    // the one under test: a debug graph would add the slip panel below them.
                    single { AppBuild(debug = false) }
                    single<LedgerArchive> { UnusedArchive() }
                    single<LedgerFileStore> { UnusedFileStore() }
                    // Every screen but one is behind first run, so the graph answers by
                    // default for a phone that has already been through it.
                    // NFR-4 and FR-3's answers. The ledger module owns the live copy,
                    // and this graph does not load it.
                    single { ImportSettings(FakeImportSettingsStore()) }
                    single<OnboardingFlag> { FakeOnboardingFlag(finished = firstRunAnswered) }
                    single<PhotoAccessRequest> { FakePhotoAccessRequest() }
                    // Today's Fix reads its steps from here when photo access is limited.
                    single<PhotoSettings> { FakePhotoSettings() }
                    // #118: the shell provides slip pictures; these screens never load one.
                    single { SlipPictures { null } }
                    // The shell sweeps evidence on start, so the graph has to answer for an
                    // importer even on a screen that never imports anything.
                    single {
                        SlipImporter(
                            source = FakeSlipSource(),
                            reader = FakeSlipTextReader(),
                            store = InMemoryImportStore(),
                        )
                    }
                },
            )
        },
    ) {
        PtutApp()
    }

    /**
     * #19: first run replaces sign-in, and it sits above the shell rather than inside it. The shell
     * draws the pill, so a first run that were one more destination would offer four tabs to
     * somebody who has not agreed to anything yet.
     */
    @Test
    fun firstRunDrawsNeitherThePillNorTheAddAction() = runComposeUiTest {
        setContent { TestApp(firstRunAnswered = false) }

        onNodeWithContentDescription("Allow photos").assertIsDisplayed()
        onNode(pillSlot("Reports")).assertDoesNotExist()
        onNodeWithContentDescription("Add an expense by hand").assertDoesNotExist()
    }

    /** Declining is a whole answer: NFR-2's manual path does not need the K PLUS folder. */
    @Test
    fun decliningPhotoAccessStillOpensTheLedger() = runComposeUiTest {
        setContent { TestApp(firstRunAnswered = false) }

        onNodeWithText("I'll add expenses by hand").performClick()

        onNode(pillSlot("Reports")).assertIsDisplayed()
        onNodeWithContentDescription("Add an expense by hand").assertIsDisplayed()
    }

    /**
     * The queue is deliberately not a bottom-bar destination: it is a job the app hands back
     * to the reader, so it is offered on Home where the totals it would change are visible.
     */
    @Test
    fun theQueueIsReachedFromHomeRatherThanFromTheBottomBar() = runComposeUiTest {
        setContent { TestApp() }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("2 slips to check"))
        // Through the click action: the card is the last thing on Home, and a scroll that stops as
        // soon as it shows leaves it under the pill, which the content scrolls beneath (#155).
        onNodeWithText("2 slips to check").performSemanticsAction(SemanticsActions.OnClick)

        onNodeWithText("MK Restaurant Rama 9").assertIsDisplayed()
    }

    /**
     * FR-21: any row opens its record. Today is the first of the two entry points the spec names,
     * and the record is where the destructive step lives — nothing deletes from a list.
     */
    @Test
    fun aRowOnTodayOpensTheWholeRecord() = runComposeUiTest {
        setContent { TestApp() }

        onNodeWithText("Foodland Rama IV").performClick()

        onNodeWithText("Purchase").assertIsDisplayed()
        onNodeWithText("From").assertIsDisplayed()
        onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    /** #155: a record is a task, so the pill steps aside for it and comes back on the list. */
    @Test
    fun thePillLeavesARecordAndComesBackOnTheList() = runComposeUiTest {
        setContent { TestApp() }

        onNodeWithText("Foodland Rama IV").performClick()
        onNode(pillSlot("Today")).assertDoesNotExist()
        onNodeWithContentDescription("Add an expense by hand").assertDoesNotExist()

        onNodeWithContentDescription("Back").performClick()
        onNode(pillSlot("Today")).assertIsSelected()
    }

    /**
     * The second entry point the spec names, and the one #10 assumed existed. A row the reader
     * found by searching has to open the same record as a row they saw on Today.
     */
    @Test
    fun aRowOnTheLedgerOpensTheSameRecord() = runComposeUiTest {
        setContent { TestApp() }

        // History is pushed from Home's Purchases header since Shared took its pill slot (#33).
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Purchases"))
        onNodeWithText("Purchases").performClick()
        // The Ledger has two scrollables — the day list and the filter chips — so the day list
        // is named by its axis rather than by "the one that scrolls".
        onNode(dayList()).performScrollToNode(hasText("Big C Extra"))
        onNodeWithText("Big C Extra").performClick()

        onNodeWithText("Purchase").assertIsDisplayed()
        onNodeWithText("Big C Extra").assertIsDisplayed()
        // FR-12, the line the spec calls required. This graph binds no rules, so no shop is sorted (#117).
        onNodeWithText("Only this one purchase is removed.").assertIsDisplayed()
    }

    /**
     * Slice 5 end to end, the way a person meets it: FAB, amount, category, Save — and a row on
     * Today that was not there before.
     *
     * The toast is not asserted here. This harness runs `delay` on virtual time, so the 2600 ms
     * dwell elapses inside the first `waitForIdle` and the line is gone before anything can look
     * at it. `AppViewModelTest` holds that clock still, and `ToastHostTest` draws the line.
     */
    @Test
    fun savingFromTheAddSheetLeavesARowOnToday() = runComposeUiTest {
        setContent { TestApp() }

        onNodeWithContentDescription("Add an expense by hand").performClick()
        onNode(hasSetTextAction() and hasContentDescription("Amount")).performTextInput("485")
        // The chip, not Home's category tile behind the sheet, which carries the same name.
        onNode(hasText("Food & drink") and hasClickAction()).performClick()
        onNodeWithContentDescription("Save").performClick()

        // The sheet is gone, so the write finished: it closes on the save, not on the tap.
        onNodeWithContentDescription("Save").assertDoesNotExist()

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("−฿485.00"))
        onNodeWithText("−฿485.00").assertIsDisplayed()
        // Named by its category, so the line under it leaves the category out (#210).
        onNodeWithText("added by hand").assertIsDisplayed()
    }

    /** The save key stays dark until a category is chosen, and says why (plan §G item 6). */
    @Test
    fun anAmountWithNoCategorySaysWhatIsMissingRatherThanRefusingSilently() = runComposeUiTest {
        setContent { TestApp() }

        onNodeWithContentDescription("Add an expense by hand").performClick()
        onNode(hasSetTextAction() and hasContentDescription("Amount")).performTextInput("485")

        onNodeWithText("Pick a category to save this").assertIsDisplayed()
        onNodeWithContentDescription("Save").assertIsNotEnabled()
    }

    /**
     * The notice's whole point, end to end: it does not drop the reader at the top of Settings,
     * it marks the lever that owns the cause. Also the only place the route argument is exercised
     * against the real navigation graph, which is where an unsupported argument type would throw.
     */
    @Test
    fun theStaleNoticeLandsOnTheLeverThatOwnsTheCause() = runComposeUiTest {
        setContent { TestApp(sinceLastImport = FOUR_DAYS) }

        onNodeWithText("Fix").performClick()

        onNodeWithText("Your spending stays on this phone", substring = true).assertIsDisplayed()
        assertMarked("Background import")
        assertNotMarked("Photo folders")
    }

    /**
     * The other half of the decision. A ledger that has never imported is not a ledger that
     * stopped importing, so the notice offers a different word and a different row.
     */
    @Test
    fun anImporterThatNeverStartedLandsOnTheSourceInstead() = runComposeUiTest {
        setContent { TestApp(sinceLastImport = null) }

        onNodeWithText("Fix").performClick()

        assertMarked("Photo folders")
        assertNotMarked("Background import")
    }

    /**
     * The debug menu is absent from a release build rather than hidden in one: the graph binds
     * nothing it needs unless the entry point said debug, and this is the only place that claim is
     * checked against the drawn screen. `SlipSeamModuleTest` checks the graph; a stray `if` removed
     * from the footer would pass that and still ship the panel.
     */
    @Test
    fun aReleaseBuildDrawsNoWayToInjectASlip() = runComposeUiTest {
        setContent { TestApp(sinceLastImport = null) }

        openSettings()

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Timezone"))
        onNodeWithText("DEBUG · INJECT A SLIP").assertDoesNotExist()
    }

    /**
     * Every Settings row draws a chevron, and a chevron over nothing reads as the app having
     * failed. Checked against the real navigation graph, which is also where a route argument the
     * navigation library cannot resolve off Android would throw.
     */
    @Test
    fun theScheduleRowOpensTheScreenThatOwnsIt() = runComposeUiTest {
        setContent { TestApp(sinceLastImport = null) }

        openSettings()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Background import"))
        onNodeWithText("Background import").performClick()

        onNodeWithContentDescription("Check for slips now").assertIsDisplayed()
    }

    /** FR-19 offers no picker, so this row had no destination at all until it got a screen. */
    @Test
    fun theTimezoneRowOpensTheScreenThatExplainsIt() = runComposeUiTest {
        setContent { TestApp(sinceLastImport = null) }

        openSettings()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Timezone"))
        onNodeWithText("Timezone").performClick()

        onNodeWithText("The day you are in").assertIsDisplayed()
    }

    /**
     * The stale notice is the shortest way in, and the only one that needs no bar coordinates.
     * It is only offered on a ledger nothing has imported into, so every caller sets one up.
     */
    private fun ComposeUiTest.openSettings() {
        onNodeWithText("Fix").performClick()
        onNodeWithText("Your spending stays on this phone", substring = true).assertIsDisplayed()
    }

    private fun ComposeUiTest.assertMarked(row: String) = markedRow(row).assertExists()

    private fun ComposeUiTest.assertNotMarked(row: String) = markedRow(row).assertDoesNotExist()

    /**
     * Unmerged: the mark sits on the row's own surface, and the merged tree folds it into the
     * clickable wrapper above it — where the row's name is no longer a descendant.
     */
    private fun ComposeUiTest.markedRow(row: String) = onNode(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, NEEDS_ATTENTION) and
            hasAnyDescendant(hasText(row)),
        useUnmergedTree = true,
    )

    /**
     * #144, N1: both platforms draw one pill, the four destinations in [BarDestination]'s order and
     * add between Reports and Shared (#33). The order is worth a test because it comes from one list,
     * and the cheap way to draw chrome is to write the slots out again by hand.
     */
    @Test
    fun thePillDrawsEveryDestinationInTheOrderTheOneListGivesThem() = runComposeUiTest {
        setContent { TestApp() }

        val slots = BarDestination.entries.map { entry ->
            onAllNodes(pillSlot(entry.label)).fetchSemanticsNodes().singleOrNull()
                ?: fail("${entry.label} is not in the pill")
        }
        val add = onNodeWithContentDescription("Add an expense by hand").fetchSemanticsNode()

        assertTrue(
            slots.zipWithNext().all { (left, right) -> left.positionInRoot.x < right.positionInRoot.x },
            "the pill draws the destinations in an order BarDestination does not give",
        )
        assertTrue(
            add.positionInRoot.x > slots[1].positionInRoot.x && add.positionInRoot.x < slots[2].positionInRoot.x,
            "add is not between Reports and Shared",
        )
    }
}

/** FR-22's two ends, present so Settings can be drawn. No test here taps either row. */
private class UnusedArchive : LedgerArchive {

    override suspend fun export(): String = error("This test never exports")

    override suspend fun restore(json: String): MergeReport = error("This test never imports")
}

private class UnusedFileStore : LedgerFileStore {

    override suspend fun write(suggestedName: String, contents: String): String? =
        error("This test never writes a file")

    override suspend fun read(): String? = error("This test never reads a file")
}

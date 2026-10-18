package com.ptut.expense.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.ptut.expense.PtutApp
import com.ptut.expense.add.AddSheet
import com.ptut.expense.add.AddUiState
import com.ptut.expense.confirm.ConfirmSlipUiState
import com.ptut.expense.confirm.ConfirmSlipView
import com.ptut.expense.debug.SkyKit
import com.ptut.expense.di.AppBuild
import com.ptut.expense.di.viewModelModule
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.MergeReport
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.SampleLedgerRepository
import com.ptut.expense.domain.settings.FakeImportSettingsStore
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.edit.EditTransactionUiState
import com.ptut.expense.edit.EditTransactionView
import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.onboarding.FakeOnboardingFlag
import com.ptut.expense.onboarding.FakePhotoAccessRequest
import com.ptut.expense.onboarding.FakePhotoSettings
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.pillSlot
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastHost
import com.ptut.expense.ui.ToastMessage
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Spec page 24: "No interactive target below 44dp / 44pt, including numpad keys, chips, and the
 * close affordances on sheets."
 *
 * Measured off the rendered tree rather than off the declared sizes, because those are not the
 * same question. The design draws 40dp ledger rows and 42dp glyph tiles, and neither is a target —
 * a rule reading every `Sizes` entry would flag half the design and be switched off within a week.
 * What is asked here is narrower and is the actual promise: of the things a finger can hit, is any
 * of them too small?
 *
 * The laid-out bounds of an *interactive* node, which is a narrower question than "is any declared
 * size under 44". [SemanticsNode.touchBoundsInRoot] looked like the right reading and is not: Compose
 * expands every clickable to the platform minimum before reporting it, so a 22dp control comes back
 * as 48 and the check can never fail. That is what [aTargetUnderTheFloorIsReportedRatherThanMissed]
 * is for — it drew a 22dp target and the first version of this test waved it through.
 *
 * There is no allowlist. #44 recorded what the app drew under the floor and left the choice open;
 * the choice went to 44. Five of those were Material defaults nobody had overridden, one was the
 * 38dp filter pill, and one was the sheet drag handle, which takes a `width` after all. Putting an
 * exception back here is a design decision, not a test edit.
 */
/** The pill's four destinations and its add slot, which every screen in this test draws. */
private const val ALWAYS_ON_SCREEN = 5

/** First run draws no chrome. "Allow photo access" and the button that declines are all of it. */
private const val FIRST_RUN_EXITS = 2

/**
 * Every control in `SkyKit` (#151), every tappable surface (#152), the empty state's button (#153)
 * and the three bars' six actions (#154). Text nodes are not targets.
 */
private const val SKY_KIT_TARGETS = 38

private data class TargetSize(val width: Dp, val height: Dp)

@OptIn(ExperimentalTestApi::class)
class TouchTargetFloorTest {

    /**
     * `KoinApplication` starts the *global* context, so a container left running is inherited by
     * whatever test runs next — along with any row an earlier test wrote into the ledger.
     */
    @AfterTest
    fun stopTheGraph() {
        stopKoin()
    }

    /**
     * The sweep proving itself. Every other test here passes by finding nothing, which is also
     * what a broken sweep does — so one undersized control is drawn on purpose and the same check
     * has to report it.
     */
    @Test
    fun aTargetUnderTheFloorIsReportedRatherThanMissed() = runComposeUiTest {
        setContent {
            Box(
                Modifier
                    .size(Sizes.settingsGlyph)
                    .clickable {},
            )
        }

        val failure = assertFailsWith<AssertionError> {
            assertEveryTargetClearsTheFloor(on = "a deliberately small control", expectAtLeast = 1)
        }

        assertTrue(failure.message.orEmpty().contains("are under 44.0.dp"), failure.message.orEmpty())
    }

    /**
     * Sky paints several controls under 44 (#151); each pads its tap area out to the floor instead.
     * Scrolled because the kit outgrows the window, and a squeezed row would measure 0 tall.
     */
    @Test
    fun everySkyControlClearsTheFloor() = runComposeUiTest {
        setContent { PtutTheme { SkyKit(Modifier.verticalScroll(rememberScrollState())) } }

        assertEveryTargetClearsTheFloor(on = "the Sky kit", expectAtLeast = SKY_KIT_TARGETS)
    }

    @Test
    fun everyTargetOnTodayClearsTheFloor() = runComposeUiTest {
        setContent { TestApp() }

        assertEveryTargetClearsTheFloor(on = "Today")
    }

    /**
     * First run is the one screen outside the shell, so it carries no bar and no add action — its
     * own two exits are the whole sweep. They are the tallest and second-tallest buttons in the app
     * and still worth measuring: the design's 60 and 52 only clear the floor while nobody rounds
     * them down to a Material default.
     */
    @Test
    fun everyTargetOnFirstRunClearsTheFloor() = runComposeUiTest {
        setContent { TestApp(firstRunAnswered = false) }

        assertEveryTargetClearsTheFloor(on = "first run", expectAtLeast = FIRST_RUN_EXITS)
    }

    @Test
    fun everyTargetOnTheLedgerClearsTheFloor() = runComposeUiTest {
        setContent { TestApp() }

        // History is pushed from Home's Purchases header since Shared took its pill slot (#33).
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Purchases"))
        onNodeWithText("Purchases").performClick()

        assertEveryTargetClearsTheFloor(on = "History")
    }

    @Test
    fun everyTargetOnTheAddSheetClearsTheFloor() = runComposeUiTest {
        setContent { TestApp() }

        onNodeWithContentDescription("Add an expense by hand").performClick()

        assertEveryTargetClearsTheFloor(on = "the add sheet")
    }

    @Test
    fun everyTargetOnSettingsClearsTheFloor() = runComposeUiTest {
        setContent { TestApp() }

        onNode(pillSlot("Settings")).performClick()

        assertEveryTargetClearsTheFloor(on = "Settings")
    }

    /** #179: the toast is the only home of FR-21's Undo, and no screen above draws one. */
    @Test
    fun theToastsUndoClearsTheFloor() = runComposeUiTest {
        setContent {
            PtutTheme { ToastHost(message = ToastMessage("Deleted Foodland Rama IV", action = ToastAction("Undo") {})) }
        }

        assertEveryTargetClearsTheFloor(on = "the toast", expectAtLeast = 1)
    }

    /** #111: the sub-chips on the three real screens, not only in the kit. */
    @Test
    fun everyTargetOnTheAddSheetWithSubCategoriesClearsTheFloor() = runComposeUiTest {
        setContent {
            PtutTheme {
                AddSheet(
                    uiState = AddUiState(category = groceries, subCategories = subs, subCategory = subs.first()),
                    onType = {},
                    onKind = {},
                    onCategory = {},
                    onSubCategory = {},
                    onTime = {},
                    onOpenMemo = {},
                    onMemo = {},
                    onCloseMemo = {},
                    onSave = {},
                )
            }
        }

        onNodeWithText("Market").assertExists()
        assertEveryTargetClearsTheFloor(on = "the add sheet with sub-categories", expectAtLeast = subs.size)
    }

    @Test
    fun everyTargetOnCheckSlipWithSubCategoriesClearsTheFloor() = runComposeUiTest {
        setContent {
            PtutTheme {
                ConfirmSlipView(
                    uiState = ConfirmSlipUiState(
                        merchant = "Foodland Rama IV",
                        date = "Fri 14 Aug, 18:30",
                        reference = "0138K4",
                        entry = AmountEntry.of(Satang(64_250)),
                        amountUnread = false,
                        lowConfidence = emptySet(),
                        category = groceries,
                        categories = Category.seed().forDirection(Direction.Expense),
                        subCategories = subs,
                        subCategory = subs.first(),
                        saving = false,
                        saved = false,
                    ),
                    onType = {},
                    onCategory = {},
                    onSubCategory = {},
                    onSave = {},
                    onNotMine = {},
                    onClose = {},
                )
            }
        }

        onNodeWithText("Market").assertExists()
        assertEveryTargetClearsTheFloor(on = "Check slip with sub-categories", expectAtLeast = subs.size)
    }

    @Test
    fun everyTargetOnEditPurchaseWithSubCategoriesClearsTheFloor() = runComposeUiTest {
        setContent {
            PtutTheme {
                EditTransactionView(
                    uiState = EditTransactionUiState.Loaded(
                        entry = AmountEntry.of(Satang(64_250)),
                        slipRead = null,
                        categories = Category.seed().forDirection(Direction.Expense),
                        categoryId = groceries.id,
                        subCategories = subs,
                        subCategoryId = subs.first().id,
                        memo = null,
                        evidence = emptyList(),
                        notice = "Nothing here came from a slip.",
                        canSave = false,
                    ),
                    onClose = {},
                    onType = {},
                    onCategory = {},
                    onSubCategory = {},
                    onTime = {},
                    onOpenMemo = {},
                    onMemo = {},
                    onCloseMemo = {},
                    onSave = {},
                )
            }
        }

        onNodeWithText("Market").assertExists()
        assertEveryTargetClearsTheFloor(on = "Edit purchase with sub-categories", expectAtLeast = subs.size)
    }

    private fun ComposeUiTest.assertEveryTargetClearsTheFloor(on: String, expectAtLeast: Int = ALWAYS_ON_SCREEN) {
        val targets = onAllNodes(hasClickAction()).fetchSemanticsNodes()

        // A sweep that found nothing also finds nothing too small. The four bar destinations and
        // the FAB are drawn over every screen here, so anything under five means the navigation
        // went somewhere this test cannot see and the pass below would be worth nothing.
        assertTrue(
            targets.size >= expectAtLeast,
            "only ${targets.size} target(s) on $on — the sweep looked at the wrong screen",
        )

        val tooSmall = targets
            .filterNot { it.isATextFieldRatherThanATarget() }
            .map { it.describedBy() to it.touchTargetSize(density) }
            .filter { (_, size) -> size.isUnder(Sizes.minTouchTarget) }

        if (tooSmall.isEmpty()) return

        fail(
            tooSmall.joinToString(
                prefix = "${tooSmall.size} target(s) on $on are under ${Sizes.minTouchTarget}:\n",
                separator = "\n",
            ) { (name, size) -> "  $name is ${size.width} by ${size.height}" },
        )
    }

    /**
     * [SemanticsNode.size] is the node's own laid-out size. [SemanticsNode.boundsInRoot] was the
     * first reading and is clipped by whatever scrolls it: a row half off the bottom of a list
     * reported the sliver still on screen — 992 by 10 for a review card two lines tall — and that
     * looked exactly like a target drawn too small. Scroll position is not the question. How big
     * the control is drawn is.
     */
    private fun SemanticsNode.touchTargetSize(density: Density): TargetSize = with(density) {
        TargetSize(width = size.width.toDp(), height = size.height.toDp())
    }

    /**
     * A text field's editable node carries a click action and reports the text's own bounds, not
     * the field's. The field around it is a target and is measured; the run of text inside is not.
     */
    private fun SemanticsNode.isATextFieldRatherThanATarget(): Boolean =
        config.getOrNull(SemanticsActions.SetText) != null

    private fun TargetSize.isUnder(floor: Dp): Boolean = width < floor || height < floor

    /** Whatever a reader would call it, so a failure names the control and not a node id. */
    private fun SemanticsNode.describedBy(): String =
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: "an unlabelled target at ${positionInRoot.x} by ${positionInRoot.y}"

    /**
     * Settings resolves an archive and a file store to draw at all, and this test never taps
     * either row — so they answer, and answering is the whole of what they are here for.
     */
    private object UnusedArchive : LedgerArchive {
        override suspend fun export(): String = ""

        override suspend fun restore(json: String) = MergeReport(added = 0, alreadyHeld = 0)
    }

    private object UnusedFiles : LedgerFileStore {
        override suspend fun write(suggestedName: String, contents: String): String? = null

        override suspend fun read(): String? = null
    }

    @Composable
    private fun TestApp(firstRunAnswered: Boolean = true) = KoinApplication(
        configuration = koinConfiguration {
            modules(
                viewModelModule,
                module {
                    single<LedgerRepository> { SampleLedgerRepository() }
                    // Settings resolves this to decide whether the debug slip panel is drawn.
                    // Unbound, the sweep never reaches the screen it is sweeping.
                    single { AppBuild(debug = false) }
                    single<LedgerArchive> { UnusedArchive }
                    single<LedgerFileStore> { UnusedFiles }
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
}

private val groceries = Category.seed().first { it.id == "groceries" }

private val subs = listOf(
    SubCategory("supermarket", "groceries", "Supermarket"),
    SubCategory("market", "groceries", "Market"),
)

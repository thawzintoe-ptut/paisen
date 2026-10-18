package com.ptut.expense.di

import com.ptut.expense.AppViewModel
import com.ptut.expense.activity.ActivityViewModel
import com.ptut.expense.add.AddViewModel
import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.ModelState
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.agent.agentModule
import com.ptut.expense.categories.CategoriesViewModel
import com.ptut.expense.categories.EditCategoryViewModel
import com.ptut.expense.confirm.ConfirmSlipViewModel
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.data.local.ptutDatabaseBuilder
import com.ptut.expense.data.repository.DefaultLedgerRepository
import com.ptut.expense.data.repository.DefaultRuleRepository
import com.ptut.expense.data.repository.RoomCategoryRepository
import com.ptut.expense.data.repository.RoomImportStore
import com.ptut.expense.data.repository.RoomLedgerArchive
import com.ptut.expense.data.repository.RoomSharedRepository
import com.ptut.expense.data.repository.RoomWatchedFolderRepository
import com.ptut.expense.debug.DebugSlipViewModel
import com.ptut.expense.design.PtutMotion
import com.ptut.expense.detail.TransactionDetailViewModel
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.folders.FirstRunFolders
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.FolderReadTally
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.inAppLanguage
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.slip.ImportStore
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.edit.EditTransactionViewModel
import com.ptut.expense.folders.PhotoFoldersViewModel
import com.ptut.expense.home.HomeViewModel
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.ledger.LedgerScope
import com.ptut.expense.ledger.LedgerViewModel
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.onboarding.OnboardingViewModel
import com.ptut.expense.platform.UnavailableSlipSource
import com.ptut.expense.platform.UnavailableSlipTextReader
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.notify.repeatReminders
import com.ptut.expense.platform.ocr.RoutingSlipTextReader
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.CompositeSlipSource
import com.ptut.expense.platform.photos.InMemorySlipSource
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.SlipSource
import com.ptut.expense.platform.work.BackgroundImport
import com.ptut.expense.receipt.DayReceiptViewModel
import com.ptut.expense.reports.ReportsViewModel
import com.ptut.expense.resources.Res
import com.ptut.expense.review.ReviewViewModel
import com.ptut.expense.settings.AssistantModelViewModel
import com.ptut.expense.settings.BackgroundImportViewModel
import com.ptut.expense.settings.KeepFailedSlipsViewModel
import com.ptut.expense.settings.LanguageViewModel
import com.ptut.expense.settings.LedgerBackupViewModel
import com.ptut.expense.settings.LicencesViewModel
import com.ptut.expense.settings.SettingsViewModel
import com.ptut.expense.settings.SlipImportCadence
import com.ptut.expense.settings.TimezoneViewModel
import com.ptut.expense.settings.slipImportCadence
import com.ptut.expense.shared.AccountViewModel
import com.ptut.expense.shared.AddExpenseViewModel
import com.ptut.expense.shared.GroupDetailViewModel
import com.ptut.expense.shared.JoinViewModel
import com.ptut.expense.shared.PeopleViewModel
import com.ptut.expense.shared.RecordPaymentViewModel
import com.ptut.expense.shared.RepeatExpenseViewModel
import com.ptut.expense.shared.RepeatRunner
import com.ptut.expense.shared.SharedActivityViewModel
import com.ptut.expense.shared.SharedViewModel
import com.ptut.expense.shared.SignInViewModel
import com.ptut.expense.shared.WhoPaysWhomViewModel
import com.ptut.expense.shoprule.ShopRuleViewModel
import com.ptut.expense.sync.syncModule
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ScreenReader
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.screenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The graph. It replaces `AppContainer`, which held this same wiring by hand for one slice —
 * the container's constructor was already the thing that would grow with every new seam.
 *
 * [databaseContext] is the one value the graph cannot make for itself: Android resolves its data
 * directory from a `Context` and iOS resolves its own, so the platform entry point passes it in
 * rather than the module reaching for a platform API it cannot see from common code.
 *
 * The DAOs get no bindings of their own. Only the repository asks for them, and three `single`s
 * nothing else resolves would be graph nobody reads.
 */
/**
 * The screens' half of the graph. It names no platform type, so a test can take these bindings
 * as they ship and swap only the seam underneath — the wiring under test is then the real one.
 *
 * [ToastMessages] is a `single` and not a ViewModel because two state holders share it: the sheet
 * that raises a toast and the shell that renders it. It is the seam that lets them do that without
 * one reaching for the other.
 */
/**
 * The categories as the reader has them, for a screen that turns an id into a name.
 *
 * Falls back to the seed twice over: while the flow has not emitted — the repository shares
 * eagerly, but a ViewModel built in the same frame as the graph can still arrive first — and where
 * a screen test's graph binds no category table at all. A screen of unnamed rows would be worse
 * than one naming them as installed.
 *
 * Used by the screens that offer a choice as well as the ones that only name. A category carries
 * its own direction now, so `forDirection` filters this list rather than a compiled-in pair — which
 * is what lets a category the reader made reach the sheet that files into it.
 */
private fun Scope.storedCategories(): List<Category> =
    getOrNull<CategoryRepository>()?.categories?.value.orEmpty().ifEmpty { Category.seed().map(Category::inAppLanguage) }

/** The sub-categories as stored, read when a screen is built, like [storedCategories] (#111). */
private fun Scope.storedSubCategories(): List<SubCategory> =
    getOrNull<CategoryRepository>()?.subCategories?.value.orEmpty()

/** [storedSubCategories] as they change, for the tabs, like [liveCategories] (#112). */
private fun Scope.liveSubCategories(): Flow<List<SubCategory>> =
    getOrNull<CategoryRepository>()?.subCategories ?: flowOf(emptyList())

/**
 * [storedCategories] as it changes (#115), for the tabs: they keep their holder while the reader
 * is away, so a list read once would miss a rename or a new category until a restart.
 */
private fun Scope.liveCategories(): Flow<List<Category>> =
    getOrNull<CategoryRepository>()?.categories?.map { it.ifEmpty { Category.seed().map(Category::inAppLanguage) } } ?: flowOf(Category.seed().map(Category::inAppLanguage))

val viewModelModule: Module = module {
    single { ToastMessages() }
    // #114: raised where a save or delete lands, played by the shell like a toast.
    single { Haptics() }

    // #219: a discarded slip waits until its Undo leaves the screen, however long that is (research finding 8).
    single {
        SlipDiscards(
            importer = get(),
            toasts = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            window = { Duration.INFINITE },
        )
    }

    /**
     * C-4: what Settings may claim about importing is the platform's to say, like the pickers. A
     * factory, because its lines are words and a single would keep the language it was made in.
     */
    factory { slipImportCadence() }

    /**
     * A `single` for the reason [ToastMessages] is one: three callers share the same answer and
     * one of them writes it — the shell picks the screen, [OnboardingViewModel] finishes first run,
     * and each platform's import trigger waits for it before raising FR-2's dialog.
     */
    single { Onboarding(flag = get()) }

    // getOrNull: a screen test builds a graph with no folder seams in it, and the shell still
    // has to start. On a device the binding is always there.
    viewModel { AppViewModel(toasts = get(), importer = get(), firstRun = getOrNull()) }
    // FR-12: a renamed category has to reach every screen that names one, so these read the
    // table rather than the compiled-in seed. Read at construction, which is when a screen is
    // built — a rename shows the next time the screen is opened, not while it is on top.
    viewModel {
        HomeViewModel(
            ledger = get(),
            // The fact the freshness chip needs, taken off the one object that knows it.
            isScanning = get<SlipImporter>().isScanning,
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            watchedFolders = getOrNull<WatchedFolderRepository>()?.folders ?: flowOf(emptyList()),
            moodByTimeOfDay = getOrNull<ImportSettings>()?.choices?.map { it.moodByTimeOfDay } ?: flowOf(false),
            pendingDiscards = get<SlipDiscards>().pending,
        )
    }
    // #220: Reports passes the slice it opened; Home's History passes nothing and gets the whole table.
    viewModel { params ->
        LedgerViewModel(
            ledger = get(),
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            scope = params.getOrNull<LedgerScope>(),
        )
    }
    viewModel {
        SettingsViewModel(
            ledger = get(),
            settings = get(),
            cadence = get<SlipImportCadence>(),
            folders = getOrNull<WatchedFolderRepository>()?.folders ?: flowOf(emptyList()),
            categoryCounts = getOrNull<CategoryRepository>()?.let { repository ->
                repository.categories.combine(repository.subCategories) { all, subs -> all.size to subs.size }
            } ?: flowOf(0 to 0),
            assistant = getOrNull<LanguageModelPort>()?.state ?: flowOf(ModelState.NotOffered),
            showAssistant = getOrNull<AppBuild>()?.debug == true,
        )
    }

    // getOrNull on the schedule: C-4 leaves iOS with no periodic job, so nothing binds one there
    // and the screen draws no switch. On Android the binding is always present.
    viewModel {
        BackgroundImportViewModel(
            ledger = get(),
            settings = get(),
            importer = get(),
            toasts = get(),
            cadence = get<SlipImportCadence>(),
            background = getOrNull<BackgroundImport>(),
        )
    }
    viewModel { KeepFailedSlipsViewModel(ledger = get(), settings = get()) }
    viewModel { LedgerBackupViewModel(ledger = get(), archive = get(), files = get(), toasts = get()) }
    viewModel { TimezoneViewModel() }
    // getOrNull: a screen test's graph binds no store; the choice then lasts until the process ends.
    viewModel { LanguageViewModel(store = getOrNull()) }
    viewModel { AssistantModelViewModel(model = get()) }
    viewModel {
        val model = getOrNull<LanguageModelPort>()?.state?.value
        LicencesViewModel(
            readText = { file -> Res.readBytes(file).decodeToString() },
            // Gemma's terms apply once the model can be offered; a debug build shows its row already.
            gemma = getOrNull<AppBuild>()?.debug == true || (model != null && model != ModelState.NotOffered),
        )
    }
    viewModel { ActivityViewModel(ledger = get(), pendingDiscards = get<SlipDiscards>().pending) }
    viewModel { SharedViewModel(repository = get(), sync = getOrNull(), runner = getOrNull()) }
    viewModel { PeopleViewModel(repository = get()) }
    // Koin's parameters do not carry a null, so a new repeat travels as "".
    viewModel { (groupId: String, ruleId: String) ->
        RepeatExpenseViewModel(
            groupId = groupId,
            ruleId = ruleId.ifEmpty { null },
            repository = get(),
            categories = storedCategories().forDirection(Direction.Expense),
            toasts = get(),
            runner = getOrNull(),
        )
    }
    viewModel { SignInViewModel(auth = getOrNull()) }
    viewModel { AccountViewModel(auth = getOrNull(), sync = getOrNull()) }
    viewModel { (code: String) -> JoinViewModel(code = code, sync = getOrNull(), auth = getOrNull()) }
    // One screen per group, like the record screens: a holder that outlived the wrong group would
    // show one group's balance under another's name.
    viewModel { (groupId: String) ->
        GroupDetailViewModel(groupId = groupId, repository = get(), categories = storedCategories(), sync = getOrNull(), runner = getOrNull(), toasts = get(), haptics = get())
    }
    viewModel { (groupId: String, expenseId: String, transactionId: String, amount: String, description: String) ->
        AddExpenseViewModel(
            groupId = groupId,
            expenseId = expenseId.ifEmpty { null },
            repository = get(),
            categories = storedCategories().forDirection(Direction.Expense),
            transactionId = transactionId.ifEmpty { null },
            toasts = get(),
            haptics = get(),
            prefillAmount = amount.ifEmpty { null },
            prefillDescription = description.ifEmpty { null },
        )
    }
    viewModel { (groupId: String) -> WhoPaysWhomViewModel(groupId = groupId, repository = get()) }
    viewModel { (groupId: String, fromMemberId: String, toMemberId: String) ->
        RecordPaymentViewModel(groupId = groupId, fromMemberId = fromMemberId, toMemberId = toMemberId, repository = get(), toasts = get(), haptics = get())
    }
    viewModel { (groupId: String) ->
        SharedActivityViewModel(groupId = groupId.ifEmpty { null }, repository = get())
    }
    viewModel {
        // A provider, not the value: the factory below reads the permission each time it resolves (#217).
        PhotoFoldersViewModel(catalog = get(), folders = get(), access = { get() }, chooser = get())
    }
    viewModel { CategoriesViewModel(ledger = get(), categories = get()) }
    viewModel { (categoryId: String, isNew: Boolean) ->
        EditCategoryViewModel(categoryId = categoryId, ledger = get(), categories = get(), isNew = isNew, haptics = get())
    }
    viewModel {
        ReportsViewModel(
            ledger = get(),
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
        )
    }
    // The retention is read when the screen is built, like the categories above: a change made
    // while the queue is on top shows the next time it is opened, not under the reader's thumb.
    viewModel {
        ReviewViewModel(
            ledger = get(),
            discards = get(),
            retention = get<ImportSettings>().choices.value.retention,
            haptics = get(),
        )
    }

    // Takes the slip it is confirming as a parameter: one screen per slip, and a holder that
    // outlived the wrong one would offer the reader a different slip's amount.
    viewModel { (slipId: String) ->
        ConfirmSlipViewModel(
            slipId = slipId,
            ledger = get(),
            importer = get(),
            toasts = get(),
            discards = get(),
            categories = storedCategories().forDirection(Direction.Expense),
            subCategories = storedSubCategories(),
            haptics = get(),
        )
    }
    // Takes its row as a parameter for the same reason the confirm screen does: one screen per
    // record, and a holder that outlived the wrong one would offer the wrong row to delete.
    // One day's receipt (#120), by its ISO date: one screen per day.
    viewModel { (date: String) ->
        DayReceiptViewModel(date = date, ledger = get(), categories = storedCategories(), subCategories = storedSubCategories())
    }
    viewModel { (transactionId: String) ->
        TransactionDetailViewModel(
            transactionId = transactionId,
            ledger = get(),
            toasts = get(),
            // getOrNull, like the shell's first-run seam: a screen test builds a graph with
            // no rules table in it, and the record still has to draw.
            categories = storedCategories(),
            subCategories = storedSubCategories(),
            rules = getOrNull(),
            haptics = get(),
            shared = getOrNull(),
        )
    }
    viewModel { (transactionId: String) ->
        EditTransactionViewModel(
            transactionId = transactionId,
            ledger = get(),
            known = storedCategories(),
            subCategories = storedSubCategories(),
            haptics = get(),
            shared = getOrNull(),
            toasts = get(),
        )
    }

    viewModel {
        // A lambda, not a list: the sheet's Expense/Income toggle re-asks on every switch, and the
        // holder lives as long as the shell, so each ask reads the table as it is now (#115).
        val scope = this
        AddViewModel(
            ledger = get(),
            toasts = get(),
            categories = { scope.storedCategories().forDirection(it) },
            subCategories = { scope.storedSubCategories() },
            haptics = get(),
            agent = getOrNull(),
            reader = getOrNull(),
            // #207: once a model is on the phone; a debug build shows it for the grammar alone.
            offerQuickAdd = { scope.getOrNull<AppBuild>()?.debug == true || scope.getOrNull<LanguageModelPort>()?.state?.value is ModelState.Ready },
            speech = getOrNull(),
        )
    }

    viewModel { (shop: String) ->
        ShopRuleViewModel(shop = shop, rules = get(), categories = storedCategories())
    }

    viewModel {
        OnboardingViewModel(
            onboarding = get(),
            photoAccess = get(),
            archive = getOrNull(),
            files = getOrNull(),
            toasts = get(),
        )
    }
}

/**
 * Everything the ledger is above the database: the repositories, the importer, and the two things
 * a first run seeds. It names no platform type — only [PtutDatabase] under it — which is the rule
 * [viewModelModule] already follows, for the same reason. A test can take these bindings as they
 * ship and stand in only the database, so what it resolves is the wiring that launches.
 *
 * That split is what makes the graph checkable at all. Every `get()` below is a launch-time crash
 * waiting on a type nobody bound, and neither the compiler nor a screen test can see one — only
 * resolving the real definitions can. See `AppGraphTest`.
 *
 * The DAOs get no bindings of their own. Only the repository asks for them, and three `single`s
 * nothing else resolves would be graph nobody reads.
 */
val ledgerModule: Module = module {

    single<LedgerRepository> {
        val database = get<PtutDatabase>()
        DefaultLedgerRepository(
            transactionDao = database.transactionDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            // Its own scope rather than any viewModelScope: these flows are eagerly shared and
            // the repository is a single, so they have to outlive every screen that reads them.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    single<ImportStore> {
        val database = get<PtutDatabase>()
        RoomImportStore(
            transactionDao = database.transactionDao(),
            importedSlipDao = database.importedSlipDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            ruleDao = database.ruleDao(),
            slipConfirmationDao = database.slipConfirmationDao(),
        )
    }

    /**
     * NFR-4 is the reader's now, so the sweep asks for the answer rather than holding one. A
     * lambda and not a value: this is a `single`, and a retention read once at construction would
     * keep deleting on the old schedule until the process restarted.
     */
    single { ImportSettings(store = get()) }

    single {
        val settings = get<ImportSettings>()
        SlipImporter(
            source = get(),
            reader = get(),
            store = get(),
            folders = get(),
            retention = { settings.choices.value.retention },
        )
    }

    single<LedgerArchive> {
        val database = get<PtutDatabase>()
        RoomLedgerArchive(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            mergeDao = database.ledgerMergeDao(),
            sharedDao = database.sharedDao(),
            catalogDao = database.catalogBackupDao(),
        )
    }

    single { FirstRunFolders(catalog = get(), folders = get()) }

    single<CategoryRepository> {
        val database = get<PtutDatabase>()
        RoomCategoryRepository(
            categoryDao = database.categoryDao(),
            subCategoryDao = database.subCategoryDao(),
            // Its own scope, like the rules' was: a single whose flow is eagerly shared has to
            // outlive every screen reading it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    // Bound under the narrow seam as well: the importer asks for [FolderReadTally], and one
    // registration under the repository's own type is not a definition Koin will find for it.
    single<WatchedFolderRepository> {
        RoomWatchedFolderRepository(
            dao = get<PtutDatabase>().watchedFolderDao(),
            // Its own scope, like the rules': a single whose flow is eagerly shared has to outlive
            // every screen reading it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    } bind FolderReadTally::class

    single<RuleRepository> {
        DefaultRuleRepository(
            ruleDao = get<PtutDatabase>().ruleDao(),
            // Its own scope, like the ledger's: this is a single and its flow is eagerly shared,
            // so it has to outlive every screen that reads it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    // v2's shared costs (#133). Its own scope for the reason every repository here has one.
    single<SharedRepository> {
        val database = get<PtutDatabase>()
        RoomSharedRepository(
            dao = database.sharedDao(),
            transactionDao = database.transactionDao(),
            ledger = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}

/**
 * The database itself, and the seams only the platform on top of it can fill.
 *
 * [databaseContext] is the one value the graph cannot make for itself: Android resolves its data
 * directory from a `Context` and iOS resolves its own, so the platform entry point passes it in
 * rather than common code reaching for an API it cannot see.
 *
 * Every definition here takes that handle and nothing else. None of them reads another binding,
 * which is why the half that does — [ledgerModule] — could be lifted out and held to resolving.
 */
fun dataModule(databaseContext: PtutDatabaseContext): Module = module {
    includes(ledgerModule)

    single<PtutDatabase> { databaseContext.ptutDatabaseBuilder().openPtutDatabase() }

    /** FR-22 at both ends is a system picker, so the platform supplies it. */
    single<LedgerFileStore> { ledgerFileStore(databaseContext) }

    /**
     * First run's two platform halves: where the answer survives, and how the photo dialog is
     * raised. They sit here rather than in [viewModelModule] for the same reason the pickers do —
     * that module names no platform type, so a test can take it as it ships and fake only these.
     */
    /** NFR-4 and FR-3's answers, where they survive a process death. */
    single<ImportSettingsStore> { importSettingsStore(databaseContext) }
    single<LanguageStore> { languageStore(databaseContext) }
    single { repeatReminders(databaseContext) }
    single<SpeechInput> { speechInput(databaseContext) }
    single { screenReader(databaseContext) }
    single { RepeatRunner(repository = get(), reminders = get()) }

    single<OnboardingFlag> { onboardingFlag(databaseContext) }
    single<PhotoAccessRequest> { photoAccessRequest(databaseContext) }

    /** FR-5: what the platform can offer, and what the reader chose out of it. */
    single<FolderCatalog> { folderCatalog(databaseContext) }

    /**
     * C-4 again: only the platform can say what is left to offer, so only it writes the copy. A
     * factory, so each Photo folders screen reads the permission and the language as they are now.
     */
    factory { folderAccess(databaseContext) }
    single { folderChooser(databaseContext) }
    single { photoSettings(databaseContext) }
}

/**
 * What a platform entry point starts Koin with. It replaces `AppContainer`, which held this same
 * wiring by hand for one slice — that container's constructor was already the thing that would
 * grow with every new seam.
 */
fun appModules(databaseContext: PtutDatabaseContext, build: AppBuild): List<Module> = listOf(
    dataModule(databaseContext),
    platformSlipModule(databaseContext),
    slipSeamModule(build),
    syncModule(databaseContext),
    agentModule,
    viewModelModule,
)

/**
 * Where slips come from, and who reads them. Split out from [dataModule] so the release-versus-debug
 * decision can be checked on its own — "the debug menu is unreachable in a release build" is a
 * claim about this function, and a test can hold it to that without a database.
 *
 * The real pair is [platformSlipModule]'s to bind, and it is absent on a platform that has none
 * yet. What is left then throws rather than returning nothing — US-8 counts a scan that found no
 * slips as a success, so a stub returning an empty list would make every run look healthy.
 */
fun slipSeamModule(build: AppBuild): Module = module {
    single { build }

    /**
     * A debug build takes the platform's real pair *and* the injectable one, because it has to do
     * two jobs: read real slips off a real phone, and drive the whole pipeline from the debug menu
     * on a machine with no photo library. Each seam knows how to hold both — the source routes on
     * the id it handed out, the reader on whether the bytes are a picture.
     */
    if (build.debug) {
        single { InMemorySlipSource() }

        single<SlipSource> {
            val injected = get<InMemorySlipSource>()
            val platform = getOrNull<SlipSeams>()?.source ?: return@single injected
            CompositeSlipSource(mapOf(PLATFORM_SLIPS to platform, INJECTED_SLIPS to injected))
        }

        single<SlipTextReader> {
            val transcripts = FakeSlipTextReader()
            val platform = getOrNull<SlipSeams>()?.reader ?: return@single transcripts
            RoutingSlipTextReader(image = platform, transcript = transcripts)
        }

        viewModel { DebugSlipViewModel(source = get<InMemorySlipSource>(), importer = get()) }
    } else {
        single<SlipSource> { getOrNull<SlipSeams>()?.source ?: UnavailableSlipSource() }
        single<SlipTextReader> { getOrNull<SlipSeams>()?.reader ?: UnavailableSlipTextReader() }
    }

    // #118: screens read a queued slip's picture through this, and never get the source itself.
    single<SlipPictures> {
        val source = get<SlipSource>()
        SlipPictures { id -> source.bytesOf(id) }
    }
}

/** See [CompositeSlipSource]: a name here is written into every queued slip's id, so it is fixed. */
private const val PLATFORM_SLIPS = "platform"
private const val INJECTED_SLIPS = "injected"

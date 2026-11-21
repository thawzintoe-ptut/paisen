package com.ptut.expense.di

import com.ptut.expense.AppViewModel
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.agent.agentModule
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.data.local.ptutDatabaseBuilder
import com.ptut.expense.data.repository.RoomCategoryRepository
import com.ptut.expense.data.repository.RoomHomeBook
import com.ptut.expense.data.repository.RoomImportStore
import com.ptut.expense.data.repository.RoomLedgerArchive
import com.ptut.expense.data.repository.RoomLedgerRepository
import com.ptut.expense.data.repository.RoomPlayBook
import com.ptut.expense.data.repository.RoomRuleRepository
import com.ptut.expense.data.repository.RoomSharedRepository
import com.ptut.expense.data.repository.RoomWatchedFolderRepository
import com.ptut.expense.debug.DebugSlipViewModel
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.domainModule
import com.ptut.expense.domain.folders.FirstRunFolders
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.FolderReadTally
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.play.PlanPlayReminderUseCase
import com.ptut.expense.domain.play.PlayBook
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.domain.shared.RunDueRepeatsUseCase
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.slip.ImportStore
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.importer.SlipOwnerBook
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.platform.UnavailableSlipSource
import com.ptut.expense.platform.UnavailableSlipTextReader
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.lock.AppLock
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.platform.notify.ReminderKind
import com.ptut.expense.platform.notify.repeatReminders
import com.ptut.expense.platform.ocr.RoutingSlipTextReader
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.CompositeSlipSource
import com.ptut.expense.platform.photos.InMemorySlipSource
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.SlipSource
import com.ptut.expense.play.PlayReminderCopyWords
import com.ptut.expense.shared.RepeatReminderWords
import com.ptut.expense.sync.syncModule
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.screenReader
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * The screens' half of the graph. It names no platform type, so a test can take these bindings
 * as they ship and swap only the seam underneath — the wiring under test is then the real one.
 * Each feature area's screens live in their own module beside this one; this holds the shell's.
 *
 * [ToastMessages] is a `single` and not a ViewModel because two state holders share it: the sheet
 * that raises a toast and the shell that renders it. It is the seam that lets them do that without
 * one reaching for the other.
 */
val viewModelModule: Module = module {
    includes(appScopeModule, onboardingModule, todayModule, recordModule, settingsModule, sharedModule, playModule)

    single { ToastMessages() }
    // #114: raised where a save or delete lands, played by the shell like a toast.
    single { Haptics() }

    // Each restore screen holds its own chosen file, so a factory.
    factory { RestoreDriver(archive = get()) }

    // #219: a discarded slip waits until its Undo leaves the screen, however long that is (research finding 8).
    single {
        SlipDiscards(
            importer = get(),
            toasts = get(),
            scope = appScope(),
            window = { Duration.INFINITE },
        )
    }

    viewModel { AppViewModel(toasts = get(), runStartupTasks = get(), screenReader = get()) }
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
    includes(appScopeModule)

    single<LedgerRepository> {
        val database = get<PtutDatabase>()
        RoomLedgerRepository(
            transactionDao = database.transactionDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            // The app scope rather than any viewModelScope: these flows are eagerly shared and
            // the repository is a single, so they have to outlive every screen that reads them.
            scope = appScope(),
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
            sharedDao = database.sharedDao(),
        )
    }

    /**
     * NFR-4 is the reader's now, so the sweep asks for the answer rather than holding one. A
     * lambda and not a value: this is a `single`, and a retention read once at construction would
     * keep deleting on the old schedule until the process restarted.
     */
    single { AppSettings(store = get()) }
    single<HomeBook> {
        val database = get<PtutDatabase>()
        RoomHomeBook(
            meta = database.ledgerMetaDao(),
            transactions = database.transactionDao(),
            settings = get(),
            rates = database.sharedDao(),
        )
    }

    // #82: play's facts, beside the ledger they are worked out from.
    single<PlayBook> { RoomPlayBook(play = get<PtutDatabase>().playDao()) }

    single {
        val settings = get<AppSettings>()
        SlipImporter(
            source = get(),
            reader = get(),
            store = get(),
            folders = get(),
            retention = { settings.choices.value.retention },
            owner = object : SlipOwnerBook {
                override fun name(): String? = settings.choices.value.slipOwner.name

                override fun learnFrom(sender: String) = settings.learnSlipOwner(sender)
            },
            readsWallets = isDebug(),
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
            homeBook = getOrNull(),
            playDao = database.playDao(),
        )
    }

    single { FirstRunFolders(catalog = get(), folders = get()) }

    single<CategoryRepository> {
        val database = get<PtutDatabase>()
        RoomCategoryRepository(
            categoryDao = database.categoryDao(),
            subCategoryDao = database.subCategoryDao(),
            scope = appScope(),
            language = AppText.language,
            seedNames = { AppText.copy.categories.seedNames },
        )
    }

    // Bound under the narrow seam as well: the importer asks for [FolderReadTally], and one
    // registration under the repository's own type is not a definition Koin will find for it.
    single<WatchedFolderRepository> {
        RoomWatchedFolderRepository(
            dao = get<PtutDatabase>().watchedFolderDao(),
            scope = appScope(),
        )
    } bind FolderReadTally::class

    single<RuleRepository> {
        RoomRuleRepository(
            ruleDao = get<PtutDatabase>().ruleDao(),
            scope = appScope(),
        )
    }

    // v2's shared costs (#133). The app scope for the reason every repository here takes it.
    single<SharedRepository> {
        val database = get<PtutDatabase>()
        RoomSharedRepository(
            dao = database.sharedDao(),
            transactionDao = database.transactionDao(),
            ledger = get(),
            scope = appScope(),
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

    /** NFR-4 and FR-3's answers, where they survive a process death. */
    single<AppSettingsStore> { appSettingsStore(databaseContext) }
    single<LanguageStore> { languageStore(databaseContext) }
    single { repeatReminders(databaseContext) }
    single<SpeechInput> { speechInput(databaseContext) }
    single { screenReader(databaseContext) }
    // A single: its lock keeps two runs from adding the same repeat twice.
    single { RunDueRepeatsUseCase(repository = get(), reminders = get(), words = RepeatReminderWords) }
    // #83 (D15): its own set of reminders, so it and the repeats never cancel each other. A single for its lock.
    single {
        PlanPlayReminderUseCase(
            reminders = repeatReminders(databaseContext, ReminderKind.Play),
            playOn = get(),
            transactions = get(),
            book = get(),
            words = PlayReminderCopyWords,
        )
    }

    /**
     * First run's two platform halves: where the answer survives, and how the photo dialog is
     * raised. They sit here rather than in [viewModelModule] for the same reason the pickers do —
     * that module names no platform type, so a test can take it as it ships and fake only these.
     */
    single<OnboardingFlag> { onboardingFlag(databaseContext) }
    single<PhotoAccessRequest> { photoAccessRequest(databaseContext) }

    /** #25: the lock reads the switch each time, so turning it off in Settings takes effect at once. */
    single<DeviceAuth> { deviceAuth(databaseContext) }
    single { AppLock(enabled = { get<AppSettings>().choices.value.appLock }, nowMillis = { Clock.System.now().toEpochMilliseconds() }) }

    /** FR-5: what the platform can offer, and what the reader chose out of it. */
    single<FolderCatalog> { folderCatalog(databaseContext) }

    /**
     * C-4 again: only the platform can say what is left to offer, so only it writes the copy. A
     * factory, so each Photo folders screen reads the permission and the language as they are now.
     */
    factory { folderAccess(databaseContext) }
    single { folderChooser(databaseContext, get()) }
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
    domainModule,
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

        viewModel { DebugSlipViewModel(source = get<InMemorySlipSource>(), runImport = get()) }
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

package com.ptut.expense.di

import com.ptut.expense.data.local.LedgerGate
import com.ptut.expense.data.local.LedgerOpen
import com.ptut.expense.data.local.PTUT_SCHEMA_VERSION
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.usage.UsageEvent
import com.ptut.expense.domain.usage.UsageLog
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.platform.crash.CrashHeader
import com.ptut.expense.platform.crash.CrashJournal
import com.ptut.expense.platform.crash.crashFiles
import com.ptut.expense.platform.crash.install
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.sync.flags.Flag
import com.ptut.expense.sync.flags.FlagRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Starts the graph. It lives here rather than in each platform entry point so `androidApp` stays
 * a shell that names an Activity and an Application and nothing else — it needs no Koin on its
 * own classpath, and there is one definition of what "started" means for both platforms.
 *
 * Call it exactly once per process. A second call throws.
 */
fun initKoin(databaseContext: PtutDatabaseContext, build: AppBuild) {
    val crashes = CrashJournal(
        files = crashFiles(databaseContext),
        header = CrashHeader(appVersion = build.version, schemaVersion = PTUT_SCHEMA_VERSION, device = build.device),
        now = { Clock.System.now().toEpochMilliseconds() },
    )
    // First, so a crash anywhere below still leaves a report.
    crashes.install()
    val koin = startKoin {
        modules(appModules(databaseContext, build) + module { single { crashes } })
    }.koin
    // Before the first frame, so nothing is drawn in a language about to change.
    val thai = koin.featureFlags().isOn(Flag.ThaiLanguage, debug = build.debug)
    AppText.use(Language.atLaunch(saved = koin.get<LanguageStore>().read(), phoneTag = phoneLanguageTag(), offersUnreleased = thai))
    // On its own background scope, so the first frame does not wait; what it fetches applies at the next launch.
    koin.getOrNull<FlagRefresh>()?.start(appVersion = build.version, languageCode = AppText.language.value.tag)
    // #25: made before the first Activity starts, or Android's prompt has no Activity to draw over.
    koin.getOrNull<DeviceAuth>()
    // A refused ledger shows the recovery screen; nothing below may touch the database.
    if (!koin.ledgerOpened()) return
    // #41: from the fast copy before the first frame; the ledger's own row is checked behind it.
    koin.getOrNull<HomeBook>()?.let { book ->
        Home.use(book.atLaunch())
        // On Main like every other writer of the settings copy, so the two cannot cross; Room does its
        // own reads off the thread. A check that fails must not take the launch down with it.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { runSuspendCatching { book.reconcile() } }
    }
    // Made now rather than when a rules screen first asks: it fills ShopNames, which every row title reads.
    koin.getOrNull<RuleRepository>()
    countLaunch(koin, crashes)
}

/** Opens the ledger now, at launch, and says whether it opened. */
fun Koin.ledgerOpened(): Boolean = get<LedgerGate<PtutDatabase>>().state.value is LedgerOpen.Ready

/** #115: one session, and one app_crashed per crash report written since the last session. */
private fun countLaunch(koin: Koin, crashes: CrashJournal) {
    val usage = koin.getOrNull<UsageLog>() ?: return
    val settings = koin.getOrNull<AppSettings>() ?: return
    repeat(crashes.countSince(settings.choices.value.usageSessionAt)) { usage.log(UsageEvent.AppCrashed) }
    usage.log(UsageEvent.AppStarted)
    settings.setUsageSessionAt(Clock.System.now().toEpochMilliseconds())
}

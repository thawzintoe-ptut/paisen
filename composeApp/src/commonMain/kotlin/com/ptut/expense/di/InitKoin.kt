package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.sync.flags.Flag
import com.ptut.expense.sync.flags.FlagRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

/**
 * Starts the graph. It lives here rather than in each platform entry point so `androidApp` stays
 * a shell that names an Activity and an Application and nothing else — it needs no Koin on its
 * own classpath, and there is one definition of what "started" means for both platforms.
 *
 * Call it exactly once per process. A second call throws.
 */
fun initKoin(databaseContext: PtutDatabaseContext, build: AppBuild) {
    val koin = startKoin {
        modules(appModules(databaseContext, build))
    }.koin
    // Before the first frame, so nothing is drawn in a language about to change.
    val thai = koin.featureFlags().isOn(Flag.ThaiLanguage, debug = build.debug)
    AppText.use(Language.atLaunch(saved = koin.get<LanguageStore>().read(), phoneTag = phoneLanguageTag(), offersUnreleased = thai))
    // On its own background scope, so the first frame does not wait; what it fetches applies at the next launch.
    koin.getOrNull<FlagRefresh>()?.start(appVersion = build.version, languageCode = AppText.language.value.tag)
    // #41: from the fast copy before the first frame; the ledger's own row is checked behind it.
    koin.getOrNull<HomeBook>()?.let { book ->
        Home.use(book.atLaunch())
        // On Main like every other writer of the settings copy, so the two cannot cross; Room does its
        // own reads off the thread. A check that fails must not take the launch down with it.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { runSuspendCatching { book.reconcile() } }
    }
    // Made now rather than when a rules screen first asks: it fills ShopNames, which every row title reads.
    koin.getOrNull<RuleRepository>()
    // #25: made before the first Activity starts, or Android's prompt has no Activity to draw over.
    koin.getOrNull<DeviceAuth>()
}

package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LanguageStore
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
    AppText.use(koin.get<LanguageStore>().read() ?: Language.of(phoneLanguageTag()) ?: Language.English)
    // Made now rather than when a rules screen first asks: it fills ShopNames, which every row title reads.
    koin.getOrNull<RuleRepository>()
}

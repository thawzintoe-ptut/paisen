package com.ptut.expense.di

import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.folders.ApplyWatchedFoldersUseCase
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.RefreshFolderCatalogUseCase
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.home.ChooseHomeUseCase
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.play.PlayBook
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.onboarding.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** First run: the answer it keeps, and the screens that ask for it. */
val onboardingModule: Module = module {
    /**
     * A `single` for the reason [com.ptut.expense.ui.ToastMessages] is one: three callers share the
     * same answer and one of them writes it — the shell picks the screen, [OnboardingViewModel]
     * finishes first run, and each platform's import trigger waits for it before raising FR-2's dialog.
     */
    single { Onboarding(flag = get()) }

    viewModel {
        OnboardingViewModel(
            onboarding = get(),
            photoAccess = get(),
            // getOrNull on both: a screen test may bind no archive or home book; RESTORE or the home then does nothing.
            restoreSteps = getOrNull<LedgerArchive>()?.let { get<RestoreDriver>() },
            files = getOrNull(),
            toasts = get(),
            chooseHomeUseCase = getOrNull<HomeBook>()?.let { get<ChooseHomeUseCase>() },
            // #41: first run starts every ledger in baht, in this phone's zone; Settings changes the money.
            firstRunHome = if (currenciesOn()) HomeChoice(Currencies.Thb.toHomeMoney(), phoneZone()) else null,
            // getOrNull: a screen test's graph has no folder seams; step two then skips the folder list.
            refreshCatalog = getOrNull<FolderCatalog>()?.let { get<RefreshFolderCatalogUseCase>() },
            applyWatchedFolders = getOrNull<WatchedFolderRepository>()?.let { get<ApplyWatchedFoldersUseCase>() },
            photoSettings = getOrNull(),
            // getOrNull: a screen test's graph has no play book; step three then saves no goal.
            saveGoal = getOrNull<PlayBook>()?.let { get<SaveGoalUseCase>() },
            markWelcomed = getOrNull(),
            offersFolders = v2On(),
        )
    }
}

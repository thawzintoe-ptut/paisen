package com.ptut.expense.domain.settings

import com.ptut.expense.domain.app.RunStartupTasksUseCase
import com.ptut.expense.domain.backup.BuildLedgerExportUseCase
import com.ptut.expense.domain.backup.ObserveLedgerHoldingUseCase
import com.ptut.expense.domain.categories.ClearCategoryTargetUseCase
import com.ptut.expense.domain.categories.DeleteCategoryUseCase
import com.ptut.expense.domain.categories.GetCategoryStatsUseCase
import com.ptut.expense.domain.categories.GetDeleteOptionsUseCase
import com.ptut.expense.domain.categories.MoveCategoryUseCase
import com.ptut.expense.domain.categories.ObserveCategoriesUseCase
import com.ptut.expense.domain.categories.ObserveCategoryCountsUseCase
import com.ptut.expense.domain.categories.ObserveMonthlySpendByCategoryUseCase
import com.ptut.expense.domain.categories.ObserveSubCategoriesUseCase
import com.ptut.expense.domain.categories.SaveCategoryUseCase
import com.ptut.expense.domain.folders.ApplyWatchedFoldersUseCase
import com.ptut.expense.domain.folders.GetFolderAccessUseCase
import com.ptut.expense.domain.folders.RefreshFolderCatalogUseCase
import com.ptut.expense.domain.home.ChangeHomeCurrencyUseCase
import com.ptut.expense.domain.home.ChooseHomeUseCase
import com.ptut.expense.domain.home.ConvertHomeCurrencyUseCase
import com.ptut.expense.domain.home.IsLedgerEmptyUseCase
import com.ptut.expense.domain.home.PreviewConversionUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases behind Settings, categories, first run and the app shell. */
val settingsUseCases: Module = module {
    factory { SetMoodByTimeOfDayUseCase(get()) }
    factory { SetTransparencyUseCase(get()) }
    factory { SetWeeklyCheckInUseCase(get()) }
    factory { MarkExportedUseCase(get()) }
    factory { SetSlipOwnerUseCase(get()) }
    factory { EnableAppLockUseCase(get()) }
    factory { DisableAppLockUseCase(get()) }
    // getOrNull: C-4 leaves iOS with no periodic job, so nothing binds a schedule there.
    factory { SetBackgroundImportUseCase(get(), background = getOrNull()) }
    factory { RunImportNowUseCase(get()) }
    factory { ObserveKeptSlipsUseCase(get()) }
    factory { SetSlipRetentionUseCase(get()) }
    factory { ObserveAssistantModelUseCase(getOrNull()) }
    factory { DownloadAssistantModelUseCase(get()) }
    factory { RemoveAssistantModelUseCase(get()) }

    // A provider, not the value: the access factory reads the permission each time it resolves (#217).
    factory { GetFolderAccessUseCase { get() } }
    factory { RefreshFolderCatalogUseCase(get()) }
    factory { ApplyWatchedFoldersUseCase(get()) }

    factory { IsLedgerEmptyUseCase(get()) }
    factory { ChangeHomeCurrencyUseCase(get()) }
    factory { ConvertHomeCurrencyUseCase(get()) }
    factory { PreviewConversionUseCase() }

    factory { ObserveLedgerHoldingUseCase(get()) }

    factory { ObserveCategoriesUseCase(get()) }
    factory { ObserveSubCategoriesUseCase(get()) }
    // getOrNull: a screen test's graph may bind no category table; the row then counts none.
    factory { ObserveCategoryCountsUseCase(getOrNull()) }
    factory { ObserveMonthlySpendByCategoryUseCase(get(), get(), get()) }
    factory { MoveCategoryUseCase(get()) }
    factory { GetDeleteOptionsUseCase(get()) }
    factory { GetCategoryStatsUseCase(get()) }

    // getOrNull: a screen test's graph has no folder seams, and the shell still has to start.
    factory { RunStartupTasksUseCase(get(), firstRun = getOrNull()) }
    // getOrNull: a screen test's graph binds no store; the choice then lasts until the process ends.
    factory { SaveLanguageUseCase(getOrNull()) }
    factory { SwitchNoteToneUseCase(get()) }

    factory { SaveCategoryUseCase(get(), settings = getOrNull()) }
    factory { DeleteCategoryUseCase(get(), clearTarget = getOrNull<AppSettings>()?.let(::ClearCategoryTargetUseCase)) }
    factory { BuildLedgerExportUseCase(get(), get()) }
    factory { ChooseHomeUseCase(getOrNull()) }
}

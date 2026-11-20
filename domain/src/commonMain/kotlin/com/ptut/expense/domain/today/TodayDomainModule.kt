package com.ptut.expense.domain.today

import com.ptut.expense.domain.reports.CompareCategorySpendUseCase
import com.ptut.expense.domain.reports.GetPeriodReportUseCase
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.platform.photos.PhotoSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases behind Today, Activity, the day receipt, History and Reports. */
val todayUseCases: Module = module {
    factory { GetHomeSummaryUseCase() }
    factory {
        val photos = getOrNull<PhotoSettings>()
        GetImportHealthUseCase(onlyPickedPhotos = { photos?.onlyPickedPhotos == true })
    }
    factory { ObserveScanningUseCase(get<SlipImporter>().isScanning) }
    factory { GetMoodRecapUseCase() }
    factory { GetWeeklyCheckInUseCase() }
    factory { HideWeeklyNoteUseCase(settings = getOrNull(), homeToday = get()) }
    factory { GetWeeklyWrapsUseCase(get()) }
    factory { FindRunOnlyShopUseCase() }
    factory { ObserveDaySpendingUseCase(get()) }
    factory { FilterLedgerUseCase(get()) }
    factory { CompareCategorySpendUseCase() }
    factory { GetPeriodReportUseCase(get(), homeToday = get(), compareCategorySpend = get()) }
}

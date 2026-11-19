package com.ptut.expense.di

import com.ptut.expense.activity.ActivityViewModel
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.home.HomeViewModel
import com.ptut.expense.home.WeeklyNoteSettings
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.ledger.LedgerScope
import com.ptut.expense.ledger.LedgerViewModel
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.receipt.DayReceiptViewModel
import com.ptut.expense.reports.ReportsViewModel
import com.ptut.expense.sync.flags.Flag
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Today, History, Reports and the screens they open: the ledger read back. */
val todayModule: Module = module {
    // FR-12: a renamed category has to reach every screen that names one, so these read the
    // table rather than the compiled-in seed. Read at construction, which is when a screen is
    // built — a rename shows the next time the screen is opened, not while it is on top.
    viewModel {
        val photos = getOrNull<PhotoSettings>()
        val noteOn = flagOn(Flag.NoteTone)
        HomeViewModel(
            ledger = get(),
            // The fact the freshness chip needs, taken off the one object that knows it.
            isScanning = get<SlipImporter>().isScanning,
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            watchedFolders = getOrNull<WatchedFolderRepository>()?.folders ?: flowOf(emptyList()),
            moodByTimeOfDay = choice(false) { it.moodByTimeOfDay },
            weeklyCheckIn = choice(false) { it.weeklyCheckIn },
            weeklyTarget = choice(null) { it.weeklyTarget },
            // #22: the note and its tone row in Settings share one switch.
            weeklyNote = choice(WeeklyNoteSettings()) {
                WeeklyNoteSettings(on = noteOn, tone = it.noteTone, hiddenWeek = it.hiddenNoteWeek)
            },
            hideNoteFor = { monday -> getOrNull<AppSettings>()?.hideNoteWeek(monday) },
            pendingDiscards = get<SlipDiscards>().pending,
            onlyPickedPhotos = { photos?.onlyPickedPhotos == true },
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
    viewModel { ActivityViewModel(ledger = get(), pendingDiscards = get<SlipDiscards>().pending) }
    viewModel {
        ReportsViewModel(
            ledger = get(),
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            weeklyTarget = choice(null) { it.weeklyTarget },
        )
    }
    // One day's receipt (#120), by its ISO date: one screen per day.
    viewModel { (date: String) ->
        DayReceiptViewModel(date = date, ledger = get(), categories = storedCategories(), subCategories = storedSubCategories())
    }
}

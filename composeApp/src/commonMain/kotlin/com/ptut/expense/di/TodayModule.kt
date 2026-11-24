package com.ptut.expense.di

import com.ptut.expense.activity.ActivityViewModel
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.ObserveSealedMonthsUseCase
import com.ptut.expense.domain.play.PlayBook
import com.ptut.expense.domain.play.PlayPart
import com.ptut.expense.home.HomeViewModel
import com.ptut.expense.home.WeeklyNoteSettings
import com.ptut.expense.ledger.LedgerScope
import com.ptut.expense.ledger.LedgerViewModel
import com.ptut.expense.receipt.DayReceiptViewModel
import com.ptut.expense.reports.ReportsViewModel
import com.ptut.expense.sync.flags.Flag
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Today, History, Reports and the screens they open: the ledger read back. */
val todayModule: Module = module {
    // FR-12: a renamed category has to reach every screen that names one, so these read the
    // table rather than the compiled-in seed. Read at construction, which is when a screen is
    // built — a rename shows the next time the screen is opened, not while it is on top.
    viewModel {
        val noteOn = flagOn(Flag.NoteTone)
        HomeViewModel(
            observeTransactions = get(),
            observeLastImport = get(),
            observeImportRuns = get(),
            observeQueuedSlips = get(),
            observeScanning = get(),
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            observeWatchedFolders = get(),
            moodByTimeOfDay = choice(false) { it.moodByTimeOfDay },
            weeklyCheckIn = choice(false) { it.weeklyCheckIn },
            weeklyTarget = choice(null) { it.weeklyTarget },
            // #22: the note and its tone row in Settings share one switch.
            weeklyNote = choice(WeeklyNoteSettings()) {
                WeeklyNoteSettings(on = noteOn, tone = it.noteTone, hiddenWeek = it.hiddenNoteWeek)
            },
            hideWeeklyNote = get(),
            getImportHealth = get(),
            getHomeSummary = get(),
            getMoodRecap = get(),
            getWeeklyCheckIn = get(),
            observeTodayPlay = get(),
            deposits = get<PlayBook>().deposits,
            goals = get<PlayBook>().goals,
            // #103: the press and roll are play's motion, so they follow the switch that turns play off.
            // #116: with play held back, a caught slip and a filled jar still press and roll (M01, J05).
            playMotion = get<ObservePlayOnUseCase>()(if (laterOn()) PlayPart.Stamps else PlayPart.GoalJar),
            toasts = get(),
        )
    }
    // #220: Reports passes the slice it opened; Home's History passes nothing and gets the whole table.
    viewModel { params ->
        LedgerViewModel(
            filterLedger = get(),
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            scope = params.getOrNull<LedgerScope>(),
            homeToday = get(),
            sealedMonths = get<ObserveSealedMonthsUseCase>()(),
            offersMonthIndex = v2On(),
        )
    }
    viewModel {
        ActivityViewModel(
            observeImportRuns = get(),
            observeQueuedSlips = get(),
            observeTransactions = get(),
            getWeeklyWraps = get(),
            findRunOnlyShop = get(),
        )
    }
    viewModel {
        ReportsViewModel(
            getPeriodReport = get(),
            categories = storedCategories(),
            categoryUpdates = liveCategories(),
            subCategoryUpdates = liveSubCategories(),
            weeklyTarget = choice(null) { it.weeklyTarget },
        )
    }
    // One day's receipt (#120), by its ISO date: one screen per day.
    viewModel { (date: String) ->
        DayReceiptViewModel(
            date = date,
            observeDaySpending = get(),
            categories = storedCategories(),
            subCategories = storedSubCategories(),
            homeToday = get(),
        )
    }
}

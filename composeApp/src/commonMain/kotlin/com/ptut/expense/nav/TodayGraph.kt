package com.ptut.expense.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ptut.expense.activity.ActivityView
import com.ptut.expense.activity.ActivityViewModel
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.domain.play.PlayTip
import com.ptut.expense.home.AllowAllPhotosSheet
import com.ptut.expense.home.HomeView
import com.ptut.expense.home.HomeViewModel
import com.ptut.expense.home.ShelfEntry
import com.ptut.expense.ledger.LedgerView
import com.ptut.expense.ledger.LedgerViewModel
import com.ptut.expense.ledger.scope
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.receipt.DayReceiptView
import com.ptut.expense.receipt.DayReceiptViewModel
import com.ptut.expense.reports.ReportsView
import com.ptut.expense.reports.ReportsViewModel
import com.ptut.expense.settings.settingsRoute
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Today, History, Reports and Activity: the ledger read back. */
internal fun NavGraphBuilder.todayRoutes(nav: NavHostController, openAddSheet: () -> Unit) {
    composable<PtutRoute.Today> {
        val viewModel = koinViewModel<HomeViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val photoSettings = koinInject<PhotoSettings>()
        val pickScope = rememberCoroutineScope()
        var askingAllowAll by remember { mutableStateOf(false) }
        // #103: the caught toast's Change.
        val editing by viewModel.editRequests.collectAsStateWithLifecycle()
        LaunchedEffect(editing) {
            editing?.let {
                viewModel.editOpened()
                nav.editTransaction(it)
            }
        }
        HomeView(
            runTip = playTip(PlayTip.Run),
            questionTip = playTip(PlayTip.Question),
            uiState = state,
            onChoosePeriod = viewModel::show,
            onOpenReview = { nav.navigate(PtutRoute.Review) },
            // History is pushed since Shared took its pill slot (#33), and lights Today.
            onOpenLedger = { nav.navigate(PtutRoute.Ledger) { launchSingleTop = true } },
            onAddByHand = openAddSheet,
            // The stale notice does not land on Settings, it lands on the lever
            // that owns the cause — a different one depending on whether the
            // importer never started or stopped after working. Failing scans need the
            // system's photo page, which only the note in front of it can explain.
            onFixImport = { fix ->
                if (fix == ImportFix.AllowAllPhotos) askingAllowAll = true else nav.openTabAt(fix.settingsRoute())
            },
            onOpenTransaction = { nav.openTransaction(it) },
            onOpenReceipt = { nav.navigate(PtutRoute.DayReceipt(it)) },
            onOpenWeek = { nav.navigate(PtutRoute.LedgerSlice(it.from, it.to, it.categoryId)) { launchSingleTop = true } },
            onHideNoteWeek = viewModel::hideNoteWeek,
            onOpenStamps = { nav.navigate(PtutRoute.StampBook) },
            onTurnStamp = { nav.navigate(PtutRoute.StampReveal(it)) },
            onOpenQuestion = { nav.navigate(PtutRoute.DailyQuestion) },
            onOpenCoach = { nav.navigate(PtutRoute.LedgerSlice(it.from, it.to, it.categoryId)) { launchSingleTop = true } },
            onOpenGuess = { nav.navigate(PtutRoute.Guess(it)) },
            onOpenChallenge = { nav.navigate(PtutRoute.WeekChallenge) },
            onOpenShelf = { item ->
                nav.navigate(
                    when (item.entry) {
                        ShelfEntry.Challenge -> PtutRoute.WeekChallenge
                        ShelfEntry.Jar -> PtutRoute.GoalJar(goalId = item.arg)
                        ShelfEntry.Story -> PtutRoute.MonthStory(item.arg.orEmpty())
                        ShelfEntry.Close -> PtutRoute.CloseMonth(item.arg.orEmpty())
                        ShelfEntry.Score -> PtutRoute.MoneyScore
                    },
                )
            },
            onAnswerDay = viewModel::answerDay,
        )
        if (askingAllowAll) {
            // Asked for only under "Select photos", the one case that offers Android's picker.
            val chooser = if (photoSettings.onlyPickedPhotos) koinInject<FolderChooser>() else null
            AllowAllPhotosSheet(
                steps = photoSettings.steps,
                onOpenSettings = {
                    askingAllowAll = false
                    photoSettings.open()
                },
                onDismiss = { askingAllowAll = false },
                onPickPhotos = chooser?.let { picker ->
                    {
                        askingAllowAll = false
                        pickScope.launch { picker.chooseMore() }
                    }
                },
            )
        }
        // The morning card leaves at noon: the clock is read again whenever Home comes back.
        LifecycleResumeEffect(viewModel) {
            viewModel.refreshClock()
            onPauseOrDispose { }
        }
    }

    composable<PtutRoute.DayReceipt> { entry ->
        val date = entry.toRoute<PtutRoute.DayReceipt>().date
        val receipt = koinViewModel<DayReceiptViewModel> { parametersOf(date) }
        val state by receipt.uiState.collectAsStateWithLifecycle()
        DayReceiptView(uiState = state, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.Reports> {
        val viewModel = koinViewModel<ReportsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        ReportsView(
            uiState = state,
            onChoosePeriod = viewModel::show,
            onSelectBar = viewModel::select,
            onOpenHistory = { from, to, categoryId ->
                nav.navigate(PtutRoute.LedgerSlice(from, to, categoryId)) { launchSingleTop = true }
            },
        )
    }

    composable<PtutRoute.Ledger> {
        val ledger = koinViewModel<LedgerViewModel>()
        val state by ledger.uiState.collectAsStateWithLifecycle()
        LedgerView(
            uiState = state,
            onSearch = ledger::search,
            onFilter = ledger::filter,
            onOpenTransaction = { nav.openTransaction(it) },
            onBack = { nav.popBackStack() },
        )
    }

    // #220: the same History, holding what a Reports bar or category row counted.
    composable<PtutRoute.LedgerSlice> { entry ->
        val slice = entry.toRoute<PtutRoute.LedgerSlice>()
        val ledger = koinViewModel<LedgerViewModel> { parametersOf(slice.scope()) }
        val state by ledger.uiState.collectAsStateWithLifecycle()
        LedgerView(
            uiState = state,
            onSearch = ledger::search,
            onFilter = ledger::filter,
            onOpenTransaction = { nav.openTransaction(it) },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.Activity> {
        val activity = koinViewModel<ActivityViewModel>()
        val state by activity.uiState.collectAsStateWithLifecycle()
        ActivityView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onOpenReview = { nav.navigate(PtutRoute.Review) },
            onOpenSlip = { nav.navigate(PtutRoute.ConfirmSlip(it)) },
            onOpenFolders = { nav.navigate(PtutRoute.PhotoFolders) },
        )
    }
}

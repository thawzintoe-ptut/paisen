package com.ptut.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ptut.expense.activity.ActivityView
import com.ptut.expense.activity.ActivityViewModel
import com.ptut.expense.add.AddSheet
import com.ptut.expense.add.AddViewModel
import com.ptut.expense.agent.AgentInbox
import com.ptut.expense.agent.ProposedAction
import com.ptut.expense.categories.CategoriesView
import com.ptut.expense.categories.CategoriesViewModel
import com.ptut.expense.categories.EditCategoryView
import com.ptut.expense.categories.EditCategoryViewModel
import com.ptut.expense.confirm.ConfirmSlipView
import com.ptut.expense.confirm.ConfirmSlipViewModel
import com.ptut.expense.debug.DebugSlipPanel
import com.ptut.expense.debug.DebugSlipViewModel
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.systemAllowsFrost
import com.ptut.expense.detail.TransactionDetailView
import com.ptut.expense.detail.TransactionDetailViewModel
import com.ptut.expense.di.AppBuild
import com.ptut.expense.domain.DayPart
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.dayPartAt
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.edit.EditTransactionView
import com.ptut.expense.edit.EditTransactionViewModel
import com.ptut.expense.folders.PhotoFoldersView
import com.ptut.expense.folders.PhotoFoldersViewModel
import com.ptut.expense.home.AllowAllPhotosSheet
import com.ptut.expense.home.HomeView
import com.ptut.expense.home.HomeViewModel
import com.ptut.expense.ledger.LedgerView
import com.ptut.expense.ledger.LedgerViewModel
import com.ptut.expense.ledger.scope
import com.ptut.expense.locale.AppText
import com.ptut.expense.nav.BarDestination
import com.ptut.expense.nav.PtutNavHost
import com.ptut.expense.nav.PtutRoute
import com.ptut.expense.nav.PtutShell
import com.ptut.expense.nav.editTransaction
import com.ptut.expense.nav.openProposed
import com.ptut.expense.nav.openTabAt
import com.ptut.expense.nav.openTransaction
import com.ptut.expense.nav.pillSlot
import com.ptut.expense.nav.selectTab
import com.ptut.expense.nav.switchTab
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.onboarding.OnboardingView
import com.ptut.expense.onboarding.OnboardingViewModel
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.receipt.DayReceiptView
import com.ptut.expense.receipt.DayReceiptViewModel
import com.ptut.expense.reports.ReportsView
import com.ptut.expense.reports.ReportsViewModel
import com.ptut.expense.review.ReviewView
import com.ptut.expense.review.ReviewViewModel
import com.ptut.expense.settings.AssistantModelView
import com.ptut.expense.settings.AssistantModelViewModel
import com.ptut.expense.settings.BackgroundImportView
import com.ptut.expense.settings.BackgroundImportViewModel
import com.ptut.expense.settings.ExportDataView
import com.ptut.expense.settings.ImportDataView
import com.ptut.expense.settings.KeepFailedSlipsView
import com.ptut.expense.settings.KeepFailedSlipsViewModel
import com.ptut.expense.settings.LanguageView
import com.ptut.expense.settings.LanguageViewModel
import com.ptut.expense.settings.LedgerBackupViewModel
import com.ptut.expense.settings.LicencesView
import com.ptut.expense.settings.LicencesViewModel
import com.ptut.expense.settings.SettingsAction
import com.ptut.expense.settings.SettingsView
import com.ptut.expense.settings.SettingsViewModel
import com.ptut.expense.settings.TimezoneView
import com.ptut.expense.settings.TimezoneViewModel
import com.ptut.expense.settings.destination
import com.ptut.expense.settings.focusedRow
import com.ptut.expense.settings.settingsRoute
import com.ptut.expense.shared.AccountCard
import com.ptut.expense.shared.AddExpenseActions
import com.ptut.expense.shared.AddExpenseView
import com.ptut.expense.shared.AddExpenseViewModel
import com.ptut.expense.shared.GroupDetailView
import com.ptut.expense.shared.GroupDetailViewModel
import com.ptut.expense.shared.GroupInvite
import com.ptut.expense.shared.GroupManage
import com.ptut.expense.shared.JoinView
import com.ptut.expense.shared.JoinViewModel
import com.ptut.expense.shared.PeopleView
import com.ptut.expense.shared.PeopleViewModel
import com.ptut.expense.shared.RecordPaymentView
import com.ptut.expense.shared.RecordPaymentViewModel
import com.ptut.expense.shared.RepeatExpenseActions
import com.ptut.expense.shared.RepeatExpenseView
import com.ptut.expense.shared.RepeatExpenseViewModel
import com.ptut.expense.shared.RepeatRunner
import com.ptut.expense.shared.SharedActivityView
import com.ptut.expense.shared.SharedActivityViewModel
import com.ptut.expense.shared.SharedView
import com.ptut.expense.shared.SharedViewModel
import com.ptut.expense.shared.SignedOutElsewhereSheet
import com.ptut.expense.shared.WhoPaysWhomView
import com.ptut.expense.shared.WhoPaysWhomViewModel
import com.ptut.expense.shoprule.ShopRuleView
import com.ptut.expense.shoprule.ShopRuleViewModel
import com.ptut.expense.sync.InviteLinks
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.push.GroupLinks
import com.ptut.expense.sync.push.PushEvents
import com.ptut.expense.ui.HapticEvent
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.LocalSlipPictures
import com.ptut.expense.ui.ToastHost
import com.ptut.expense.ui.sky.AskBeforeLeaving
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyDiscardSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock

/**
 * What the app opens on, and the one branch above the navigation graph.
 *
 * First run is not a route. #8 hangs the navigation bar and the add action on the `Scaffold`
 * inside [PtutShell], so every destination in the graph draws both — and a screen that replaces
 * sign-in cannot offer four tabs and a Today total to somebody who has not agreed to anything yet.
 * So the choice is made here, above the shell, and the graph below it never learns of it.
 */
@Composable
fun PtutApp() {
    PtutTheme(evening = rememberEveningMood()) {
        val onboarding = koinInject<Onboarding>()
        val firstRunAnswered by onboarding.isFinished.collectAsStateWithLifecycle()

        CompositionLocalProvider(LocalSlipPictures provides koinInject<SlipPictures>()) {
            if (firstRunAnswered) LedgerShell() else FirstRun()
        }
    }
}

/** #122: evening on Bangkok's clock while Mood by time of day is on, read again whenever the app comes back. */
@Composable
private fun rememberEveningMood(): Boolean {
    val choices by koinInject<ImportSettings>().choices.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Clock.System.now()) }
    LifecycleResumeEffect(Unit) {
        now = Clock.System.now()
        onPauseOrDispose { }
    }
    return choices.moodByTimeOfDay && dayPartAt(now) == DayPart.Evening
}

@Composable
private fun FirstRun() {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val restoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val problem by viewModel.restoreProblem.collectAsStateWithLifecycle()
    val preview by viewModel.restorePreview.collectAsStateWithLifecycle()
    OnboardingView(
        onAllowPhotoAccess = viewModel::requestPhotoAccess,
        onSkip = viewModel::skip,
        onRestore = viewModel::restore,
        restoring = restoring,
        restoreProblem = problem,
        restorePreview = preview,
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
    )
}

/**
 * The shell: chrome, an add sheet and a [PtutNavHost]. It owns neither the destinations nor the
 * chrome — those live in [PtutRoute] and [PtutShell] — so adding a screen means one route and one
 * `composable` block.
 *
 * The transitions live in [PtutNavHost], which is where the two platforms are allowed to
 * disagree about how a screen arrives — see its own note for why iOS keeps the platform's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerShell() {
    val navController = rememberNavController()

    // A new language rebuilt the shell from Settings' Language screen, so go back to Settings.
    LaunchedEffect(Unit) {
        if (AppText.reopenSettings) {
            AppText.settingsReopened()
            navController.switchTab(PtutRoute.Settings())
        }
    }

    val app = koinViewModel<AppViewModel>()
    val toast by app.toast.collectAsStateWithLifecycle()

    // #114: Confirm for a save, Reject for a delete. Compose plays them through the platform's
    // own haptics (View on Android, UINotificationFeedbackGenerator on iOS) and its system setting.
    val haptics = koinInject<Haptics>()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(haptics) {
        haptics.events.collect { event ->
            hapticFeedback.performHapticFeedback(
                if (event == HapticEvent.Saved) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
            )
        }
    }

    // The add flow is a sheet over whatever is on screen, not a place you can navigate
    // back to, so it stays local state rather than becoming a route.
    var addOpen by remember { mutableStateOf(false) }
    // Resolved here rather than inside the sheet: the write outlives the sheet that started
    // it, so closing on save cannot cancel the row half-written.
    val addViewModel = koinViewModel<AddViewModel>()
    val addState by addViewModel.uiState.collectAsStateWithLifecycle()

    // The design opens the sheet cleared every time, and this holder outlives it, so the
    // clearing is said out loud rather than left to whoever calls next.
    val openAddSheet: () -> Unit = {
        addViewModel.reset()
        addOpen = true
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    // Research finding 8: an Undo stays until the reader moves to another screen.
    LaunchedEffect(currentEntry?.id) { app.screenChanged() }

    // #190: an invite link can arrive before the graph exists, so it waits in InviteLinks.
    val inviteCode by InviteLinks.pendingCode.collectAsStateWithLifecycle()
    LaunchedEffect(inviteCode, currentEntry == null) {
        val code = inviteCode ?: return@LaunchedEffect
        if (currentEntry == null) return@LaunchedEffect
        InviteLinks.consume()
        navController.navigate(PtutRoute.Join(code)) { launchSingleTop = true }
    }

    // A tapped push opens its group; like an invite link it can arrive before the graph exists.
    val pushedGroup by GroupLinks.pendingGroupId.collectAsStateWithLifecycle()
    LaunchedEffect(pushedGroup, currentEntry == null) {
        val groupId = pushedGroup ?: return@LaunchedEffect
        if (currentEntry == null) return@LaunchedEffect
        GroupLinks.consume()
        navController.navigate(PtutRoute.SharedGroup(groupId)) { launchSingleTop = true }
    }

    // #205: what Siri, a Shortcut, the Android icon or Quick add left, waiting the same way. A row
    // to add opens this sheet filled; anything else closes it and opens the screen that saves it.
    val agentAction by AgentInbox.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(agentAction, currentEntry == null) {
        val action = agentAction ?: return@LaunchedEffect
        if (currentEntry == null) return@LaunchedEffect
        AgentInbox.consume()
        if (action is ProposedAction.AddTransaction) {
            addViewModel.prefill(action)
            addOpen = true
        } else {
            addOpen = false
            navController.openProposed(action)
        }
    }

    // #189: what other members added arrives whenever the app comes back.
    // Not in every graph: the iOS UI tests build the app without a sync module.
    val koin = getKoin()
    val sharedSync = remember(koin) { koin.getOrNull<SharedSync>() }
    val syncScope = rememberCoroutineScope()
    // #182: repeats due today add themselves after the pull, so another phone's copy arrives first.
    val repeatRunner = remember(koin) { koin.getOrNull<RepeatRunner>() }
    LifecycleResumeEffect(sharedSync, repeatRunner) {
        val running = syncScope.launch {
            sharedSync?.let { sync -> runCatching { sync.syncAll() } }
            repeatRunner?.let { runner -> runCatching { runner.run() } }
        }
        // Only while on screen, so a push that finds nobody listening becomes a notification.
        val pushes = syncScope.launch {
            PushEvents.groupIds.collect { sharedSync?.let { sync -> runCatching { sync.syncAll() } } }
        }
        onPauseOrDispose {
            running.cancel()
            pushes.cancel()
        }
    }

    // #123: the reader's Transparency switch, and whether this device can blur at all.
    val choices by koinInject<ImportSettings>().choices.collectAsStateWithLifecycle()
    val frosted = choices.transparency && systemAllowsFrost()

    PtutShell(
        // Before the graph has drawn its first entry it is on its start destination, so the pill
        // shows from the first frame rather than sliding in.
        slot = if (currentEntry == null) BarDestination.Today else currentDestination.pillSlot(),
        // The screens whose top is not the grey page carry their colour up under the status bar.
        statusBarTint = when {
            currentDestination?.hasRoute(PtutRoute.Reports::class) == true -> MaterialTheme.ptut.heroBg
            currentDestination?.hasRoute(PtutRoute.Today::class) == true -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.background
        },
        onSelect = { navController.selectTab(it) },
        onAdd = openAddSheet,
        // Handed to the shell, which floats it above the pill.
        toast = {
            ToastHost(
                message = toast,
                onAction = app::runToastAction,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        },
        frosted = frosted,
    ) { padding ->
        PtutNavHost(
            navController = navController,
            startDestination = PtutRoute.Today,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable<PtutRoute.Today> {
                val viewModel = koinViewModel<HomeViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val photoSettings = koinInject<PhotoSettings>()
                var askingAllowAll by remember { mutableStateOf(false) }
                HomeView(
                    uiState = state,
                    onChoosePeriod = viewModel::show,
                    onOpenReview = { navController.navigate(PtutRoute.Review) },
                    // History is pushed since Shared took its pill slot (#33), and lights Today.
                    onOpenLedger = { navController.navigate(PtutRoute.Ledger) { launchSingleTop = true } },
                    onAddByHand = openAddSheet,
                    // The stale notice does not land on Settings, it lands on the lever
                    // that owns the cause — a different one depending on whether the
                    // importer never started or stopped after working. Failing scans need the
                    // system's photo page, which only the note in front of it can explain.
                    onFixImport = { fix ->
                        if (fix == ImportFix.AllowAllPhotos) askingAllowAll = true else navController.openTabAt(fix.settingsRoute())
                    },
                    onOpenTransaction = { navController.openTransaction(it) },
                    onOpenReceipt = { navController.navigate(PtutRoute.DayReceipt(it)) },
                )
                if (askingAllowAll) {
                    AllowAllPhotosSheet(
                        steps = photoSettings.steps,
                        onOpenSettings = {
                            askingAllowAll = false
                            photoSettings.open()
                        },
                        onDismiss = { askingAllowAll = false },
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
                DayReceiptView(uiState = state, onBack = { navController.popBackStack() })
            }

            composable<PtutRoute.Reports> {
                val viewModel = koinViewModel<ReportsViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ReportsView(
                    uiState = state,
                    onChoosePeriod = viewModel::show,
                    onSelectBar = viewModel::select,
                    onOpenHistory = { from, to, categoryId ->
                        navController.navigate(PtutRoute.LedgerSlice(from, to, categoryId)) { launchSingleTop = true }
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
                    onOpenTransaction = { navController.openTransaction(it) },
                    onBack = { navController.popBackStack() },
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
                    onOpenTransaction = { navController.openTransaction(it) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable<PtutRoute.Shared> {
                val shared = koinViewModel<SharedViewModel>()
                val state by shared.uiState.collectAsStateWithLifecycle()
                SharedView(
                    uiState = state,
                    onSearch = shared::search,
                    onCreateGroup = { name, type, icon, members ->
                        shared.createGroup(name, type, icon, members) { navController.navigate(PtutRoute.SharedGroup(it, invite = true)) }
                    },
                    onAddPerson = { name, personId ->
                        shared.addPerson(name, personId) { navController.navigate(PtutRoute.SharedGroup(it, invite = true)) }
                    },
                    onOpenGroup = { navController.navigate(PtutRoute.SharedGroup(it)) { launchSingleTop = true } },
                    onOpenActivity = { navController.navigate(PtutRoute.SharedActivity()) { launchSingleTop = true } },
                    onJoinCode = { navController.navigate(PtutRoute.Join(it)) { launchSingleTop = true } },
                    onOpenPeople = { navController.navigate(PtutRoute.People) { launchSingleTop = true } },
                )
            }

            composable<PtutRoute.RepeatExpense> { entry ->
                val route = entry.toRoute<PtutRoute.RepeatExpense>()
                val repeat = koinViewModel<RepeatExpenseViewModel> { parametersOf(route.groupId, route.ruleId.orEmpty()) }
                val state by repeat.uiState.collectAsStateWithLifecycle()
                val done by repeat.isDone.collectAsStateWithLifecycle()
                LaunchedEffect(done) { if (done) navController.popBackStack() }
                RepeatExpenseView(
                    uiState = state,
                    actions = RepeatExpenseActions(
                        onBack = { navController.popBackStack() },
                        onKind = repeat::setKind,
                        onAmount = repeat::setAmount,
                        onFrequency = repeat::setFrequency,
                        onStart = repeat::setStart,
                        onEnd = repeat::setEnd,
                        onRemind = repeat::setRemind,
                        onPayer = repeat::setPayer,
                        onSave = repeat::save,
                        onStop = repeat::stop,
                    ),
                )
            }

            composable<PtutRoute.People> {
                val people = koinViewModel<PeopleViewModel>()
                val state by people.uiState.collectAsStateWithLifecycle()
                PeopleView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onSearch = people::search,
                    onSetArchived = people::setArchived,
                    onAddPerson = { name, personId ->
                        people.addPerson(name, personId) { navController.navigate(PtutRoute.SharedGroup(it, invite = true)) }
                    },
                )
            }

            composable<PtutRoute.Join> { entry ->
                val code = entry.toRoute<PtutRoute.Join>().code
                val join = koinViewModel<JoinViewModel> { parametersOf(code) }
                val state by join.uiState.collectAsStateWithLifecycle()
                JoinView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onPick = join::pick,
                    onOpenGroup = join::openGroup,
                    onRetry = join::retry,
                    onJoined = { groupId ->
                        navController.navigate(PtutRoute.SharedGroup(groupId)) {
                            popUpTo<PtutRoute.Join> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<PtutRoute.SharedGroup> { entry ->
                val route = entry.toRoute<PtutRoute.SharedGroup>()
                val groupId = route.groupId
                val group = koinViewModel<GroupDetailViewModel> { parametersOf(groupId) }
                val state by group.uiState.collectAsStateWithLifecycle()
                val signedIn by group.signedIn.collectAsStateWithLifecycle()
                val inviteAction by group.inviteAction.collectAsStateWithLifecycle()
                val groupAction by group.groupActionState.collectAsStateWithLifecycle()
                val groupRemoved by group.isRemoved.collectAsStateWithLifecycle()
                LaunchedEffect(groupRemoved) { if (groupRemoved) navController.popBackStack() }
                GroupDetailView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onSettleUp = { from, to -> navController.navigate(PtutRoute.RecordPayment(groupId, from, to)) { launchSingleTop = true } },
                    onBalances = { navController.navigate(PtutRoute.WhoPaysWhom(groupId)) { launchSingleTop = true } },
                    onAddExpense = { navController.navigate(PtutRoute.AddSharedExpense(groupId)) { launchSingleTop = true } },
                    onOpenExpense = { navController.navigate(PtutRoute.AddSharedExpense(groupId, it)) { launchSingleTop = true } },
                    onActivity = { navController.navigate(PtutRoute.SharedActivity(groupId)) { launchSingleTop = true } },
                    onRepeat = { navController.navigate(PtutRoute.RepeatExpense(groupId)) { launchSingleTop = true } },
                    onOpenRepeat = { navController.navigate(PtutRoute.RepeatExpense(groupId, it)) { launchSingleTop = true } },
                    manage = GroupManage(
                        action = groupAction,
                        onSetPromptPay = group::setPromptPay,
                        onEdit = group::editGroup,
                        onRemove = group::removeGroup,
                        onDismissError = group::dismissGroupError,
                        onAddPeople = group::addPeople,
                        onRemoveMember = group::removeMember,
                        onRemovePayment = group::removePayment,
                    ),
                    invite = GroupInvite(
                        openOnStart = route.invite,
                        signedIn = signedIn,
                        action = inviteAction,
                        onInvite = group::invite,
                        onNewLink = group::newLink,
                        onSwitchOff = group::switchOff,
                    ),
                )
            }

            composable<PtutRoute.AddSharedExpense> { entry ->
                val route = entry.toRoute<PtutRoute.AddSharedExpense>()
                // Koin's parameters do not carry a null, so a new expense travels as "".
                val add = koinViewModel<AddExpenseViewModel> {
                    parametersOf(route.groupId, route.expenseId.orEmpty(), route.transactionId.orEmpty(), route.amount.orEmpty(), route.description.orEmpty())
                }
                val state by add.uiState.collectAsStateWithLifecycle()
                val close: () -> Unit = { navController.popBackStack() }
                AddExpenseView(
                    uiState = state,
                    actions = AddExpenseActions(
                        onClose = close,
                        onAmount = add::setAmount,
                        onRate = add::setRate,
                        onCurrency = add::setCurrency,
                        onDescription = add::setDescription,
                        onNote = add::setNote,
                        onTime = add::setTime,
                        onCategory = add::setCategory,
                        onPayer = add::setPayer,
                        onOpenSplit = add::openSplit,
                        onSplitMode = add::setSplitMode,
                        onWeight = add::setWeight,
                        onGiveLeft = add::giveLeftToLastEdited,
                        onSave = { add.save(onSaved = close) },
                        onRemove = { add.remove(onRemoved = close) },
                    ),
                )
            }

            composable<PtutRoute.WhoPaysWhom> { entry ->
                val groupId = entry.toRoute<PtutRoute.WhoPaysWhom>().groupId
                val who = koinViewModel<WhoPaysWhomViewModel> { parametersOf(groupId) }
                val state by who.uiState.collectAsStateWithLifecycle()
                WhoPaysWhomView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onSimplify = who::setSimplify,
                    onSettle = { from, to -> navController.navigate(PtutRoute.RecordPayment(groupId, from, to)) { launchSingleTop = true } },
                )
            }

            composable<PtutRoute.RecordPayment> { entry ->
                val route = entry.toRoute<PtutRoute.RecordPayment>()
                val payment = koinViewModel<RecordPaymentViewModel> { parametersOf(route.groupId, route.fromMemberId, route.toMemberId) }
                val state by payment.uiState.collectAsStateWithLifecycle()
                val close: () -> Unit = { navController.popBackStack() }
                RecordPaymentView(
                    uiState = state,
                    onClose = close,
                    onAmount = payment::setAmount,
                    onMethod = payment::setMethod,
                    onTime = payment::setTime,
                    onRecord = { payment.record(onDone = close) },
                )
            }

            composable<PtutRoute.SharedActivity> { entry ->
                val groupId = entry.toRoute<PtutRoute.SharedActivity>().groupId
                // Koin's parameters do not carry a null, so every group's timeline travels as "".
                val activity = koinViewModel<SharedActivityViewModel> { parametersOf(groupId.orEmpty()) }
                val state by activity.uiState.collectAsStateWithLifecycle()
                SharedActivityView(uiState = state, onBack = { navController.popBackStack() }, onFilter = activity::show)
            }

            composable<PtutRoute.Settings> { entry ->
                val settings = koinViewModel<SettingsViewModel>()
                val state by settings.uiState.collectAsStateWithLifecycle()
                // The switch in the background import row is that screen's own lever (#168).
                val background = koinViewModel<BackgroundImportViewModel>()
                SettingsView(
                    uiState = state,
                    // Every row is a destination, so the screen navigates and the state holder
                    // holds no action — an operation started here would report its result over
                    // whichever screen the reader had moved on to.
                    onAction = { action -> action.destination()?.let { navController.navigate(it) } },
                    focusRow = entry.toRoute<PtutRoute.Settings>().focusedRow(),
                    onSwitch = { action, on ->
                        when (action) {
                            SettingsAction.MoodByTimeOfDay -> settings.setMoodByTimeOfDay(on)
                            SettingsAction.Transparency -> settings.setTransparency(on)
                            else -> background.setRunning(on)
                        }
                    },
                    account = { AccountCard(Modifier.padding(horizontal = Spacing.sm)) },
                    footer = {
                        Column {
                            // Absent from a release build rather than hidden in one: the
                            // graph binds nothing this needs unless the entry point said
                            // debug.
                            if (koinInject<AppBuild>().debug) {
                                val debug = koinViewModel<DebugSlipViewModel>()
                                val summary by debug.summary.collectAsStateWithLifecycle()
                                DebugSlipPanel(
                                    summary = summary,
                                    onInjectReadable = debug::injectReadable,
                                    onInjectSameMerchant = debug::injectSameMerchant,
                                    onInjectUnreadable = debug::injectUnreadable,
                                    modifier = Modifier.padding(Spacing.sm),
                                )
                            }
                        }
                    },
                )
            }

            composable<PtutRoute.BackgroundImport> {
                val background = koinViewModel<BackgroundImportViewModel>()
                val state by background.uiState.collectAsStateWithLifecycle()
                BackgroundImportView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onSetRunning = background::setRunning,
                    onCheckNow = background::checkNow,
                )
            }

            composable<PtutRoute.KeepFailedSlips> {
                val keep = koinViewModel<KeepFailedSlipsViewModel>()
                val state by keep.uiState.collectAsStateWithLifecycle()
                KeepFailedSlipsView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onChoose = keep::choose,
                )
            }

            // One class for both ends of FR-22. Each screen resolves its own instance,
            // which is fine — they are separate destinations, so only one is ever on top.
            composable<PtutRoute.ExportData> {
                val backup = koinViewModel<LedgerBackupViewModel>()
                val state by backup.uiState.collectAsStateWithLifecycle()
                ExportDataView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onExport = backup::export,
                )
            }

            composable<PtutRoute.ImportData> {
                val backup = koinViewModel<LedgerBackupViewModel>()
                val state by backup.uiState.collectAsStateWithLifecycle()
                ImportDataView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onChooseFile = backup::chooseFile,
                    onImport = backup::restore,
                    onCancel = backup::cancelRestore,
                )
            }

            composable<PtutRoute.Language> {
                val language = koinViewModel<LanguageViewModel>()
                val state by language.uiState.collectAsStateWithLifecycle()
                LanguageView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onChoose = language::choose,
                )
            }

            composable<PtutRoute.Timezone> {
                TimezoneView(
                    uiState = koinViewModel<TimezoneViewModel>().uiState,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<PtutRoute.AssistantModel> {
                val assistant = koinViewModel<AssistantModelViewModel>()
                val state by assistant.uiState.collectAsStateWithLifecycle()
                AssistantModelView(
                    uiState = state,
                    onAct = assistant::act,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<PtutRoute.Licences> {
                val licences = koinViewModel<LicencesViewModel>()
                val state by licences.uiState.collectAsStateWithLifecycle()
                LicencesView(
                    uiState = state,
                    onToggle = licences::toggle,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<PtutRoute.Review> {
                val review = koinViewModel<ReviewViewModel>()
                val state by review.uiState.collectAsStateWithLifecycle()
                ReviewView(
                    state = state,
                    onOpenSlip = { navController.navigate(PtutRoute.ConfirmSlip(it)) },
                    onDiscard = review::discard,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<PtutRoute.Categories> {
                val categories = koinViewModel<CategoriesViewModel>()
                val state by categories.uiState.collectAsStateWithLifecycle()
                val created by categories.justCreated.collectAsStateWithLifecycle()

                // A new category is opened for editing at once: naming it is the first thing
                // screen 12a is for, so asking in a dialog first would ask twice.
                LaunchedEffect(created) {
                    created?.let {
                        categories.openedCreated()
                        navController.navigate(PtutRoute.EditCategory(it, isNew = true))
                    }
                }

                CategoriesView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(PtutRoute.EditCategory(it)) },
                    onNew = { categories.newCategory() },
                    onMove = { id, by -> categories.move(id, by) },
                )
            }

            composable<PtutRoute.EditCategory> { entry ->
                val route = entry.toRoute<PtutRoute.EditCategory>()
                val edit = koinViewModel<EditCategoryViewModel> { parametersOf(route.categoryId, route.isNew) }
                val state by edit.uiState.collectAsStateWithLifecycle()
                val deleted by edit.isDeleted.collectAsStateWithLifecycle()

                // The category is gone before the screen is, so leaving cannot cancel the
                // delete — the order the record screen already uses.
                LaunchedEffect(deleted) { if (deleted) navController.popBackStack() }

                EditCategoryView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onRename = edit::rename,
                    onIcon = edit::chooseIcon,
                    onDirection = edit::chooseDirection,
                    onColor = edit::chooseColor,
                    onSave = { edit.save() },
                    onAddSubCategory = { edit.addSubCategory(it) },
                    onRenameSubCategory = { id, name -> edit.renameSubCategory(id, name) },
                    onRemoveSubCategory = { edit.removeSubCategory(it) },
                    onDelete = { edit.delete(it) },
                )
            }

            composable<PtutRoute.ConfirmSlip> { entry ->
                val slipId = entry.toRoute<PtutRoute.ConfirmSlip>().slipId
                val confirm = koinViewModel<ConfirmSlipViewModel> { parametersOf(slipId) }
                val state by confirm.uiState.collectAsStateWithLifecycle()

                // The row is durable, or the slip gone, before the screen goes, so leaving cannot cancel it.
                // A save lands on Today, where the row it made shows; Not mine goes back to the list.
                LaunchedEffect(state.saved, state.dismissed) {
                    when {
                        state.saved -> if (!navController.popBackStack<PtutRoute.Today>(inclusive = false)) {
                            navController.openTabAt(PtutRoute.Today)
                        }

                        state.dismissed -> navController.popBackStack()
                    }
                }

                ConfirmSlipView(
                    uiState = state,
                    onType = confirm::type,
                    onCategory = confirm::choose,
                    onSubCategory = confirm::chooseSubCategory,
                    onSave = confirm::save,
                    onNotMine = confirm::notMine,
                    onClose = { navController.popBackStack() },
                    onShopName = confirm::nameShop,
                )
            }

            composable<PtutRoute.PhotoFolders> {
                val folders = koinViewModel<PhotoFoldersViewModel>()
                val state by folders.uiState.collectAsStateWithLifecycle()

                PhotoFoldersView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onToggle = folders::onToggle,
                    onSave = { folders.save() },
                    onChooseAnother = { folders.chooseMore() },
                )
                // Access granted in the system settings shows on return, not after a restart (#217).
                LifecycleResumeEffect(folders) {
                    folders.refresh()
                    onPauseOrDispose { }
                }
            }

            composable<PtutRoute.TransactionDetail> { entry ->
                val id = entry.toRoute<PtutRoute.TransactionDetail>().transactionId
                val detail =
                    koinViewModel<TransactionDetailViewModel> { parametersOf(id) }
                val state by detail.uiState.collectAsStateWithLifecycle()
                val deleted by detail.isDeleted.collectAsStateWithLifecycle()

                // The row is out of the ledger before the screen goes, so leaving cannot
                // cancel the delete — the same order the confirm slip screen saves in.
                LaunchedEffect(deleted) { if (deleted) navController.popBackStack() }

                TransactionDetailView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.editTransaction(id) },
                    onAskToDelete = detail::askToDelete,
                    onKeepIt = detail::keepIt,
                    onConfirmDelete = detail::confirmDelete,
                    onOpenShopRule = { navController.navigate(PtutRoute.ShopRule(it)) },
                    onShare = { groupId -> navController.navigate(PtutRoute.AddSharedExpense(groupId, transactionId = id)) { launchSingleTop = true } },
                    onOpenSharedGroup = { navController.navigate(PtutRoute.SharedGroup(it)) { launchSingleTop = true } },
                    onSetAlsoRemove = detail::setAlsoRemove,
                )
            }

            composable<PtutRoute.ShopRule> { entry ->
                val shop = entry.toRoute<PtutRoute.ShopRule>().shop
                val rule = koinViewModel<ShopRuleViewModel> { parametersOf(shop) }
                val state by rule.uiState.collectAsStateWithLifecycle()
                val forgotten by rule.isForgotten.collectAsStateWithLifecycle()

                // The rule is gone before the screen is, so leaving cannot cancel it.
                LaunchedEffect(forgotten) { if (forgotten) navController.popBackStack() }

                ShopRuleView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onCategory = { rule.recategorise(it) },
                    onForget = { rule.forget() },
                    onRename = { rule.rename(it) },
                )
            }

            composable<PtutRoute.EditTransaction> { entry ->
                val id = entry.toRoute<PtutRoute.EditTransaction>().transactionId
                val edit = koinViewModel<EditTransactionViewModel> { parametersOf(id) }
                val state by edit.uiState.collectAsStateWithLifecycle()
                val saved by edit.isSaved.collectAsStateWithLifecycle()

                // The correction is durable before the screen goes, so leaving cannot
                // cancel it — the same order the record screen deletes in.
                LaunchedEffect(saved) { if (saved) navController.popBackStack() }

                EditTransactionView(
                    uiState = state,
                    onClose = { navController.popBackStack() },
                    onType = edit::type,
                    onCategory = edit::choose,
                    onSubCategory = edit::chooseSubCategory,
                    onTime = edit::setTime,
                    onOpenMemo = edit::openMemo,
                    onMemo = edit::setMemo,
                    onCloseMemo = edit::closeMemo,
                    onSave = edit::save,
                )
            }

            composable<PtutRoute.Activity> {
                val activity = koinViewModel<ActivityViewModel>()
                val state by activity.uiState.collectAsStateWithLifecycle()
                ActivityView(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onOpenReview = { navController.navigate(PtutRoute.Review) },
                    onOpenSlip = { navController.navigate(PtutRoute.ConfirmSlip(it)) },
                    onOpenFolders = { navController.navigate(PtutRoute.PhotoFolders) },
                )
            }
        }
    }

    // The sheet closes on the write finishing, not on the tap: a save that failed must leave
    // the amount where the person can see it.
    LaunchedEffect(addState.saved) {
        if (addState.saved) addOpen = false
    }

    if (addOpen) {
        // #214: a changed draft asks before a swipe, a scrim tap, back or the iOS edge swipe drops it.
        var askDiscard by remember { mutableStateOf(false) }
        SkyBottomSheet(
            onDismissRequest = { addOpen = false },
            confirmDismiss = {
                if (addState.changed) askDiscard = true
                !addState.changed
            },
        ) {
            AskBeforeLeaving(changed = addState.changed, onAsk = { askDiscard = true })
            AddSheet(
                uiState = addState,
                onType = addViewModel::type,
                onKind = addViewModel::setKind,
                onCategory = addViewModel::choose,
                onSubCategory = addViewModel::chooseSubCategory,
                onTime = addViewModel::setTime,
                onOpenMemo = addViewModel::openMemo,
                onMemo = addViewModel::setMemo,
                onCloseMemo = addViewModel::closeMemo,
                onSave = addViewModel::save,
                onQuickAdd = addViewModel::quickAdd,
                onAskText = addViewModel::typeAsk,
                onOpenVoice = addViewModel::openVoice,
                onCloseVoice = addViewModel::closeVoice,
                onListen = addViewModel::listen,
                onStopListening = addViewModel::stopListening,
                onVoiceLanguage = addViewModel::chooseVoiceLanguage,
            )
        }
        if (askDiscard) {
            SkyDiscardSheet(
                onKeep = { askDiscard = false },
                onDiscard = {
                    askDiscard = false
                    addOpen = false
                },
            )
        }
    }

    // One phone at a time: a newer sign-in on another phone signed this one out at the last sync.
    val signedOutElsewhere by remember(sharedSync) { sharedSync?.signedOutElsewhere ?: MutableStateFlow(false) }.collectAsStateWithLifecycle()
    if (signedOutElsewhere) {
        SignedOutElsewhereSheet(onDismiss = { sharedSync?.acknowledgeSignedOutElsewhere() })
    }
}

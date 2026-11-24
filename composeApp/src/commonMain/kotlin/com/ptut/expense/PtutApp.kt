package com.ptut.expense

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ptut.expense.add.AddViewModel
import com.ptut.expense.agent.AgentInbox
import com.ptut.expense.agent.ProposedAction
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.systemAllowsFrost
import com.ptut.expense.domain.DayPart
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.dayPartAt
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.locale.AppText
import com.ptut.expense.nav.BarDestination
import com.ptut.expense.nav.PtutNavHost
import com.ptut.expense.nav.PtutRoute
import com.ptut.expense.nav.PtutShell
import com.ptut.expense.nav.openProposed
import com.ptut.expense.nav.pillSlot
import com.ptut.expense.nav.playRoutes
import com.ptut.expense.nav.recordRoutes
import com.ptut.expense.nav.selectTab
import com.ptut.expense.nav.settingsRoutes
import com.ptut.expense.nav.sharedRoutes
import com.ptut.expense.nav.switchTab
import com.ptut.expense.nav.todayRoutes
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.onboarding.OnboardingView
import com.ptut.expense.onboarding.OnboardingViewModel
import com.ptut.expense.recovery.RestoreAfterRecovery
import com.ptut.expense.shared.SignedOutElsewhereSheet
import com.ptut.expense.sync.InviteLinks
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.push.GroupLinks
import com.ptut.expense.ui.HapticEvent
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.LocalSlipPictures
import com.ptut.expense.ui.ToastHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
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
            if (firstRunAnswered) AppLockGate { LedgerShell() } else FirstRun()
        }
    }
}

/** #122: evening on Bangkok's clock while Mood by time of day is on, read again whenever the app comes back. */
@Composable
private fun rememberEveningMood(): Boolean {
    val choices by koinInject<AppSettings>().choices.collectAsStateWithLifecycle()
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Access granted in the phone's settings shows when the reader comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }
    OnboardingView(
        state = state,
        onAllowPhotoAccess = viewModel::requestPhotoAccess,
        onChooseFolder = viewModel::chooseFolder,
        onSkip = viewModel::skip,
        onFinish = viewModel::finish,
        onOpenSettings = viewModel::openPhoneSettings.takeIf { viewModel.canOpenSettings },
        onRestore = viewModel::restore,
        restoring = restoring,
        restoreProblem = problem,
        restorePreview = preview,
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
        onBack = viewModel::back,
        onChooseGoal = viewModel::chooseGoal,
        onGoalAmount = viewModel::setGoalAmount,
        onStartJar = viewModel::startJar,
    )
}

/** Takes what waits in an inbox once [ready], consumes it, then hands it to [onTake]. */
@Composable
private fun <T : Any> TakeFromInbox(pending: StateFlow<T?>, ready: Boolean, consume: () -> Unit, onTake: (T) -> Unit) {
    val waiting by pending.collectAsStateWithLifecycle()
    LaunchedEffect(waiting, ready) {
        val value = waiting ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        consume()
        onTake(value)
    }
}

/**
 * The shell: chrome, an add sheet and a [PtutNavHost]. It owns neither the destinations nor the
 * chrome — those live in [PtutRoute] and [PtutShell] — so adding a screen means one route and one
 * `composable` block in its area's routes, such as [settingsRoutes].
 *
 * The transitions live in [PtutNavHost], which is where the two platforms are allowed to
 * disagree about how a screen arrives — see its own note for why iOS keeps the platform's.
 */
@Composable
private fun LedgerShell() {
    val navController = rememberNavController()

    // A new language or home (#41) rebuilt the shell from a Settings screen, so go back to Settings.
    LaunchedEffect(Unit) {
        if (AppText.reopenSettings || Home.reopenSettings) {
            AppText.settingsReopened()
            Home.settingsReopened()
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
    var addOpen by rememberSaveable { mutableStateOf(false) }
    // Resolved here rather than inside the sheet: the write outlives the sheet that started
    // it, so closing on save cannot cancel the row half-written.
    val addViewModel = koinViewModel<AddViewModel>()

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

    val graphReady = currentEntry != null
    // #190: an invite link can arrive before the graph exists, so it waits in InviteLinks.
    TakeFromInbox(InviteLinks.pendingCode, graphReady, InviteLinks::consume) { code ->
        navController.navigate(PtutRoute.Join(code)) { launchSingleTop = true }
    }

    // "Restore from a backup" on the recovery screen opened an empty ledger first; Import follows.
    TakeFromInbox(RestoreAfterRecovery.pendingRestore, graphReady, RestoreAfterRecovery::consume) {
        navController.navigate(PtutRoute.ImportData) { launchSingleTop = true }
    }

    // A tapped push opens its group; like an invite link it can arrive before the graph exists.
    TakeFromInbox(GroupLinks.pendingGroupId, graphReady, GroupLinks::consume) { groupId ->
        navController.navigate(PtutRoute.SharedGroup(groupId)) { launchSingleTop = true }
    }

    // #205: what Siri, a Shortcut, the Android icon or Quick add left, waiting the same way. A row
    // to add opens this sheet filled; anything else closes it and opens the screen that saves it.
    TakeFromInbox(AgentInbox.pendingAction, graphReady, AgentInbox::consume) { action ->
        if (action is ProposedAction.AddTransaction) {
            addViewModel.prefill(action)
            addOpen = true
        } else {
            addOpen = false
            navController.openProposed(action)
        }
    }

    // Not in every graph: the iOS UI tests build the app without a sync module.
    val koin = getKoin()
    val sharedSync = remember(koin) { koin.getOrNull<SharedSync>() }
    SyncOnResume(sharedSync)
    PlayOnResume()

    // #123: the reader's Transparency switch, and whether this device can blur at all.
    val choices by koinInject<AppSettings>().choices.collectAsStateWithLifecycle()
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
        // #107: with Play off the bar moves as it does under reduce motion.
        lively = choices.play.on,
    ) { padding ->
        PtutNavHost(
            navController = navController,
            startDestination = PtutRoute.Today,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            todayRoutes(navController, openAddSheet)
            sharedRoutes(navController)
            settingsRoutes(navController)
            playRoutes(navController)
            recordRoutes(navController)
        }
    }

    AddSheetHost(addViewModel = addViewModel, open = addOpen, onClose = { addOpen = false })

    // One phone at a time: a newer sign-in on another phone signed this one out at the last sync.
    val signedOutElsewhere by remember(sharedSync) { sharedSync?.signedOutElsewhere ?: MutableStateFlow(false) }.collectAsStateWithLifecycle()
    if (signedOutElsewhere) {
        SignedOutElsewhereSheet(onDismiss = { sharedSync?.acknowledgeSignedOutElsewhere() })
    }
}

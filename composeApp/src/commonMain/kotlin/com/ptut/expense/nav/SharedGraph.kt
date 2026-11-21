package com.ptut.expense.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ptut.expense.di.AppBuild
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.PlayPart
import com.ptut.expense.shared.AddExpenseActions
import com.ptut.expense.shared.AddExpenseView
import com.ptut.expense.shared.AddExpenseViewModel
import com.ptut.expense.shared.GroupAwardsView
import com.ptut.expense.shared.GroupAwardsViewModel
import com.ptut.expense.shared.GroupDetailView
import com.ptut.expense.shared.GroupDetailViewModel
import com.ptut.expense.shared.GroupInvite
import com.ptut.expense.shared.GroupManage
import com.ptut.expense.shared.GroupPlayEntries
import com.ptut.expense.shared.GroupPlayViewModel
import com.ptut.expense.shared.GroupSettingsViewModel
import com.ptut.expense.shared.GroupTab
import com.ptut.expense.shared.GroupTabsState
import com.ptut.expense.shared.JoinView
import com.ptut.expense.shared.JoinViewModel
import com.ptut.expense.shared.PeopleView
import com.ptut.expense.shared.PeopleViewModel
import com.ptut.expense.shared.RecordPaymentView
import com.ptut.expense.shared.RecordPaymentViewModel
import com.ptut.expense.shared.RepeatExpenseActions
import com.ptut.expense.shared.RepeatExpenseView
import com.ptut.expense.shared.RepeatExpenseViewModel
import com.ptut.expense.shared.SharedActivityView
import com.ptut.expense.shared.SharedActivityViewModel
import com.ptut.expense.shared.SharedView
import com.ptut.expense.shared.SharedViewModel
import com.ptut.expense.shared.TripRecapView
import com.ptut.expense.shared.TripRecapViewModel
import com.ptut.expense.shared.WhoPaysWheelView
import com.ptut.expense.shared.WhoPaysWheelViewModel
import com.ptut.expense.shared.WhoPaysWhomUiState
import com.ptut.expense.shared.WhoPaysWhomView
import com.ptut.expense.shared.WhoPaysWhomViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** v2's shared groups: the list, one group, its expenses, repeats and payments. */
internal fun NavGraphBuilder.sharedRoutes(nav: NavHostController) {
    composable<PtutRoute.Shared> {
        val shared = koinViewModel<SharedViewModel>()
        val state by shared.uiState.collectAsStateWithLifecycle()
        SharedView(
            uiState = state,
            onSearch = shared::search,
            onCreateGroup = { name, type, icon, members, currency ->
                shared.createGroup(name, type, icon, members, currency) { nav.navigate(PtutRoute.SharedGroup(it, invite = true)) }
            },
            onAddPerson = { name, personId ->
                shared.addPerson(name, personId) { nav.navigate(PtutRoute.SharedGroup(it, invite = true)) }
            },
            onOpenGroup = { nav.navigate(PtutRoute.SharedGroup(it)) { launchSingleTop = true } },
            onOpenActivity = { nav.navigate(PtutRoute.SharedActivity) { launchSingleTop = true } },
            onJoinCode = { nav.navigate(PtutRoute.Join(it)) { launchSingleTop = true } },
            onOpenPeople = { nav.navigate(PtutRoute.People) { launchSingleTop = true } },
        )
    }

    composable<PtutRoute.RepeatExpense> { entry ->
        val route = entry.toRoute<PtutRoute.RepeatExpense>()
        val repeat = koinViewModel<RepeatExpenseViewModel> { parametersOf(route.groupId, route.ruleId.orEmpty()) }
        val state by repeat.uiState.collectAsStateWithLifecycle()
        val done by repeat.isDone.collectAsStateWithLifecycle()
        LaunchedEffect(done) { if (done) nav.popBackStack() }
        RepeatExpenseView(
            uiState = state,
            actions = RepeatExpenseActions(
                onBack = { nav.popBackStack() },
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
            onBack = { nav.popBackStack() },
            onSearch = people::search,
            onSetArchived = people::setArchived,
            onAddPerson = { name, personId ->
                people.addPerson(name, personId) { nav.navigate(PtutRoute.SharedGroup(it, invite = true)) }
            },
        )
    }

    composable<PtutRoute.Join> { entry ->
        val code = entry.toRoute<PtutRoute.Join>().code
        val join = koinViewModel<JoinViewModel> { parametersOf(code) }
        val state by join.uiState.collectAsStateWithLifecycle()
        JoinView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onPick = join::pick,
            onOpenGroup = join::openGroup,
            onRetry = join::retry,
            onJoined = { groupId ->
                nav.navigate(PtutRoute.SharedGroup(groupId)) {
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
        val groupSettings = koinViewModel<GroupSettingsViewModel> { parametersOf(groupId) }
        val state by group.uiState.collectAsStateWithLifecycle()
        val signedIn by group.signedIn.collectAsStateWithLifecycle()
        val inviteAction by groupSettings.inviteAction.collectAsStateWithLifecycle()
        val groupAction by groupSettings.groupActionState.collectAsStateWithLifecycle()
        val groupRemoved by groupSettings.isRemoved.collectAsStateWithLifecycle()
        val refreshing by group.isRefreshing.collectAsStateWithLifecycle()
        val tab by group.tab.collectAsStateWithLifecycle()
        val who = koinViewModel<WhoPaysWhomViewModel> { parametersOf(groupId) }
        // Mapped only while Balances shows. Activity stays on: its tab counts what is new.
        val balances = if (tab == GroupTab.Balances) who.uiState.collectAsStateWithLifecycle().value else WhoPaysWhomUiState.Missing
        val activity = koinViewModel<SharedActivityViewModel> { parametersOf(groupId) }
        val activityState by activity.uiState.collectAsStateWithLifecycle()
        val activitySince by group.activitySince.collectAsStateWithLifecycle()
        // #94–#97: read when the screen is built, as a flag is.
        val playOn = koinInject<ObservePlayOnUseCase>()
        val groupPlay = koinViewModel<GroupPlayViewModel> { parametersOf(groupId) }
        val playState by groupPlay.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(activitySince) { activity.since(activitySince) }
        LaunchedEffect(groupRemoved) { if (groupRemoved) nav.popBackStack() }
        GroupDetailView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onSettleUp = { from, to -> nav.navigate(PtutRoute.RecordPayment(groupId, from, to)) { launchSingleTop = true } },
            onBalances = { nav.navigate(PtutRoute.WhoPaysWhom(groupId)) { launchSingleTop = true } },
            onAddExpense = { nav.navigate(PtutRoute.AddSharedExpense(groupId)) { launchSingleTop = true } },
            onOpenExpense = { nav.navigate(PtutRoute.AddSharedExpense(groupId, it)) { launchSingleTop = true } },
            onRepeat = { nav.navigate(PtutRoute.RepeatExpense(groupId)) { launchSingleTop = true } },
            onOpenRepeat = { nav.navigate(PtutRoute.RepeatExpense(groupId, it)) { launchSingleTop = true } },
            manage = GroupManage(
                action = groupAction,
                onSetPromptPay = group::setPromptPay,
                onEdit = groupSettings::editGroup,
                onRemove = groupSettings::removeGroup,
                onDismissError = groupSettings::dismissGroupError,
                onAddPeople = groupSettings::addPeople,
                onRemoveMember = groupSettings::removeMember,
                onRemovePayment = group::removePayment,
            ),
            invite = GroupInvite(
                openOnStart = route.invite,
                signedIn = signedIn,
                action = inviteAction,
                onInvite = groupSettings::invite,
                onNewLink = groupSettings::newLink,
                onSwitchOff = groupSettings::switchOff,
            ),
            refreshing = refreshing,
            onRefresh = group::refresh,
            play = GroupPlayEntries(
                onWheel = { nav.navigate(PtutRoute.WhoPaysWheel(groupId)) { launchSingleTop = true } }.takeIf { playOn.now(PlayPart.Wheel) },
                onAwards = { nav.navigate(PtutRoute.GroupAwards(groupId)) { launchSingleTop = true } }.takeIf { playOn.now(PlayPart.Awards) },
                onRecap = { nav.navigate(PtutRoute.TripRecap(groupId)) { launchSingleTop = true } }.takeIf { playState.tripEnded == true },
                tripEnded = playState.tripEnded,
                onTripEnded = groupPlay::endTrip,
                allSquare = playState.allSquare,
                reactions = playState.reactions,
                onReact = groupPlay::react,
            ),
            tabs = GroupTabsState(
                selected = tab,
                onSelect = group::showTab,
                balances = balances,
                onSimplify = who::setSimplify,
                activity = activityState,
            ),
        )
    }

    composable<PtutRoute.AddSharedExpense> { entry ->
        val route = entry.toRoute<PtutRoute.AddSharedExpense>()
        // Koin's parameters do not carry a null, so a new expense travels as "".
        val add = koinViewModel<AddExpenseViewModel> {
            parametersOf(route.groupId, route.expenseId.orEmpty(), route.transactionId.orEmpty(), route.amount.orEmpty(), route.description.orEmpty(), route.payerMemberId.orEmpty())
        }
        val state by add.uiState.collectAsStateWithLifecycle()
        val saved by add.isSaved.collectAsStateWithLifecycle()
        val close: () -> Unit = { nav.popBackStack() }
        LaunchedEffect(saved) { if (saved) close() }
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
                onQuickSplit = add::setQuickSplit,
                onQuickPayer = add::setQuickPayer,
                onOpenSplit = add::openSplit,
                onSplitMode = add::setSplitMode,
                onWeight = add::setWeight,
                onGiveLeft = add::giveLeftToLastEdited,
                onSave = add::save,
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
            onBack = { nav.popBackStack() },
            onSimplify = who::setSimplify,
            onSettle = { from, to -> nav.navigate(PtutRoute.RecordPayment(groupId, from, to)) { launchSingleTop = true } },
            onSettleMine = { from, to, amount -> nav.navigate(PtutRoute.RecordPayment(groupId, from, to, amount)) { launchSingleTop = true } },
            // Debug builds only until the card has been scanned by a real bank app.
            offerPayMe = koinInject<AppBuild>().debug,
            onPromptPay = who::setPromptPay,
        )
    }

    composable<PtutRoute.RecordPayment> { entry ->
        val route = entry.toRoute<PtutRoute.RecordPayment>()
        val payment = koinViewModel<RecordPaymentViewModel> { parametersOf(route.groupId, route.fromMemberId, route.toMemberId, route.amount.orEmpty()) }
        val state by payment.uiState.collectAsStateWithLifecycle()
        val saved by payment.isSaved.collectAsStateWithLifecycle()
        val close: () -> Unit = { nav.popBackStack() }
        LaunchedEffect(saved) { if (saved) close() }
        RecordPaymentView(
            uiState = state,
            onClose = close,
            onAmount = payment::setAmount,
            onMethod = payment::setMethod,
            onTime = payment::setTime,
            onRecord = payment::record,
        )
    }

    composable<PtutRoute.WhoPaysWheel> { entry ->
        val groupId = entry.toRoute<PtutRoute.WhoPaysWheel>().groupId
        val wheel = koinViewModel<WhoPaysWheelViewModel> { parametersOf(groupId) }
        val state by wheel.uiState.collectAsStateWithLifecycle()
        WhoPaysWheelView(
            uiState = state,
            onSpin = wheel::spin,
            onFair = wheel::setFair,
            onAdd = { payer -> nav.navigate(PtutRoute.AddSharedExpense(groupId, payerMemberId = payer)) { launchSingleTop = true } },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.GroupAwards> { entry ->
        val groupId = entry.toRoute<PtutRoute.GroupAwards>().groupId
        val awards = koinViewModel<GroupAwardsViewModel> { parametersOf(groupId) }
        val state by awards.uiState.collectAsStateWithLifecycle()
        GroupAwardsView(uiState = state, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.TripRecap> { entry ->
        val groupId = entry.toRoute<PtutRoute.TripRecap>().groupId
        val recap = koinViewModel<TripRecapViewModel> { parametersOf(groupId) }
        val state by recap.uiState.collectAsStateWithLifecycle()
        TripRecapView(
            uiState = state,
            onStartJar = { name, target -> nav.navigate(PtutRoute.GoalJar(name = name, target = target)) { launchSingleTop = true } },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.SharedActivity> {
        // Koin's parameters do not carry a null, so every group's timeline travels as "".
        val activity = koinViewModel<SharedActivityViewModel> { parametersOf("") }
        val state by activity.uiState.collectAsStateWithLifecycle()
        SharedActivityView(uiState = state, onBack = { nav.popBackStack() }, onFilter = activity::show)
    }
}

package com.ptut.expense.nav

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ptut.expense.agent.ProposedAction
import com.ptut.expense.ui.AmountEntry
import kotlin.reflect.KClass

/**
 * Taps on the bar move between tabs; they do not stack. Without this, tapping Today four times
 * would leave four Todays on the back stack and system back would walk through them.
 *
 * `saveState` and `restoreState` are what make a tab remember its scroll position, so switching
 * away and back is not the same as a fresh open.
 */
fun NavController.switchTab(route: PtutRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * A tap on the bar (#217). The lit tab, tapped again, goes back to its own first screen: History
 * lights Today and Activity lights Settings, so [switchTab]'s restore would only bring them back.
 */
fun NavController.selectTab(tab: BarDestination) {
    if (currentDestination.pillSlot() != tab) return switchTab(tab.route)
    if (currentDestination?.hasRoute(tab.route::class) == true) return
    // By type, so Settings opened at a row still matches.
    val popped = when (tab) {
        BarDestination.Today -> popBackStack<PtutRoute.Today>(inclusive = false)
        BarDestination.Reports -> popBackStack<PtutRoute.Reports>(inclusive = false)
        BarDestination.Shared -> popBackStack<PtutRoute.Shared>(inclusive = false)
        BarDestination.Settings -> popBackStack<PtutRoute.Settings>(inclusive = false)
    }
    // Not on the stack, which a tab reached another way can be: open it fresh instead.
    if (!popped) openTabAt(tab.route)
}

/**
 * A tab opened for a reason rather than resumed, so it still behaves as a tab — one entry, no
 * stack — but arrives carrying its argument.
 *
 * [switchTab]'s `restoreState` is what makes this a separate function: restoring the copy the
 * reader last left would put back the arguments saved with it and drop the ones asked for here.
 */
fun NavController.openTabAt(route: PtutRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/**
 * FR-21's record, pushed. `launchSingleTop` guards the double tap: a list row is easy to hit
 * twice, and without it the reader would have to press back twice to leave one screen.
 */
fun NavController.openTransaction(transactionId: String) {
    navigate(PtutRoute.TransactionDetail(transactionId)) { launchSingleTop = true }
}

/**
 * FR-21's correction, pushed on top of the record it corrects. `launchSingleTop` guards the double
 * tap the same way [openTransaction] does — two edit screens over one row would let the second
 * save what the first had already changed.
 */
fun NavController.editTransaction(transactionId: String) {
    navigate(PtutRoute.EditTransaction(transactionId)) { launchSingleTop = true }
}

/**
 * #206: each [ProposedAction] opens the screen a person would have used by hand, and that screen
 * saves. The Add sheet is not a route, so the shell opens it itself. A recategorise opens the row's
 * Edit screen, where the category is picked again.
 */
fun NavController.openProposed(action: ProposedAction) {
    when (action) {
        is ProposedAction.AddTransaction -> Unit

        is ProposedAction.Recategorise -> editTransaction(action.transactionId)

        is ProposedAction.DeleteTransaction -> openTransaction(action.transactionId)

        is ProposedAction.AddGroupExpense -> {
            val groupId = action.groupId ?: return switchTab(PtutRoute.Shared)
            val amount = action.amount?.let { AmountEntry.of(it).typed }
            navigate(PtutRoute.AddSharedExpense(groupId, amount = amount, description = action.description)) { launchSingleTop = true }
        }

        is ProposedAction.RecordPayment -> {
            val from = action.fromMemberId
            val to = action.toMemberId
            val route = if (from != null && to != null) PtutRoute.RecordPayment(action.groupId, from, to) else PtutRoute.SharedGroup(action.groupId)
            navigate(route) { launchSingleTop = true }
        }

        is ProposedAction.AddRepeat -> navigate(PtutRoute.RepeatExpense(action.groupId)) { launchSingleTop = true }

        ProposedAction.ScanSlips -> navigate(PtutRoute.Review) { launchSingleTop = true }
    }
}

/**
 * The pill's slot that owns each screen it shows over (#144, N4). Categories and Activity are
 * reached from Settings, so they light Settings. History is pushed from Home since Shared took its
 * slot (#33), so it lights Today; opened from Reports it lights Reports (#220). The shared timeline
 * lights Shared (#140). Every other pushed screen hides the pill (#155): a record, a form or a queue
 * is a task, and the pill would sit over the controls that finish it.
 */
internal val PillSlots: Map<KClass<out PtutRoute>, BarDestination> = mapOf(
    PtutRoute.Today::class to BarDestination.Today,
    PtutRoute.Reports::class to BarDestination.Reports,
    PtutRoute.Ledger::class to BarDestination.Today,
    PtutRoute.LedgerSlice::class to BarDestination.Reports,
    PtutRoute.Shared::class to BarDestination.Shared,
    PtutRoute.SharedActivity::class to BarDestination.Shared,
    PtutRoute.Settings::class to BarDestination.Settings,
    PtutRoute.Categories::class to BarDestination.Settings,
    PtutRoute.Activity::class to BarDestination.Settings,
)

/**
 * The slot to light for this screen, or null where the pill is hidden.
 *
 * It walks [hierarchy] — the graph's parents — rather than comparing the leaf, so it keeps working
 * if a tab ever becomes a nested graph.
 */
fun NavDestination?.pillSlot(): BarDestination? = PillSlots.entries
    .firstOrNull { (route, _) -> this?.hierarchy?.any { it.hasRoute(route) } == true }
    ?.value

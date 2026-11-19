package com.ptut.expense.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ptut.expense.confirm.ConfirmSlipView
import com.ptut.expense.confirm.ConfirmSlipViewModel
import com.ptut.expense.detail.TransactionDetailView
import com.ptut.expense.detail.TransactionDetailViewModel
import com.ptut.expense.edit.EditTransactionView
import com.ptut.expense.edit.EditTransactionViewModel
import com.ptut.expense.review.ReviewView
import com.ptut.expense.review.ReviewViewModel
import com.ptut.expense.shoprule.ShopRuleView
import com.ptut.expense.shoprule.ShopRuleViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The slip queue, one slip, one record, its shop rule and its edit. */
internal fun NavGraphBuilder.recordRoutes(nav: NavHostController) {
    composable<PtutRoute.Review> {
        val review = koinViewModel<ReviewViewModel>()
        val state by review.uiState.collectAsStateWithLifecycle()
        ReviewView(
            state = state,
            onOpenSlip = { nav.navigate(PtutRoute.ConfirmSlip(it)) },
            onDiscard = review::discard,
            onBack = { nav.popBackStack() },
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
                state.saved -> if (!nav.popBackStack<PtutRoute.Today>(inclusive = false)) {
                    nav.openTabAt(PtutRoute.Today)
                }

                state.dismissed -> nav.popBackStack()
            }
        }

        ConfirmSlipView(
            uiState = state,
            onType = confirm::type,
            onCategory = confirm::choose,
            onSubCategory = confirm::chooseSubCategory,
            onSave = confirm::save,
            onNotMine = confirm::notMine,
            onClose = { nav.popBackStack() },
            onShopName = confirm::nameShop,
            onChooseDebt = confirm::chooseDebt,
            onPayDebt = confirm::payDebt,
            onNotThisDebt = confirm::notThisDebt,
            onPlanned = confirm::markPlanned,
            onRate = confirm::typeRate,
        )
    }

    composable<PtutRoute.TransactionDetail> { entry ->
        val id = entry.toRoute<PtutRoute.TransactionDetail>().transactionId
        val detail =
            koinViewModel<TransactionDetailViewModel> { parametersOf(id) }
        val state by detail.uiState.collectAsStateWithLifecycle()
        val deleted by detail.isDeleted.collectAsStateWithLifecycle()

        // The row is out of the ledger before the screen goes, so leaving cannot
        // cancel the delete — the same order the confirm slip screen saves in.
        LaunchedEffect(deleted) { if (deleted) nav.popBackStack() }

        TransactionDetailView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onEdit = { nav.editTransaction(id) },
            onAskToDelete = detail::askToDelete,
            onKeepIt = detail::keepIt,
            onConfirmDelete = detail::confirmDelete,
            onOpenShopRule = { nav.navigate(PtutRoute.ShopRule(it)) },
            onShare = { groupId -> nav.navigate(PtutRoute.AddSharedExpense(groupId, transactionId = id)) { launchSingleTop = true } },
            onOpenSharedGroup = { nav.navigate(PtutRoute.SharedGroup(it)) { launchSingleTop = true } },
            onSetAlsoRemove = detail::setAlsoRemove,
            onSetPlanned = detail::setPlanned,
        )
    }

    composable<PtutRoute.ShopRule> { entry ->
        val shop = entry.toRoute<PtutRoute.ShopRule>().shop
        val rule = koinViewModel<ShopRuleViewModel> { parametersOf(shop) }
        val state by rule.uiState.collectAsStateWithLifecycle()
        val forgotten by rule.isForgotten.collectAsStateWithLifecycle()

        // The rule is gone before the screen is, so leaving cannot cancel it.
        LaunchedEffect(forgotten) { if (forgotten) nav.popBackStack() }

        ShopRuleView(
            uiState = state,
            onBack = { nav.popBackStack() },
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
        LaunchedEffect(saved) { if (saved) nav.popBackStack() }

        EditTransactionView(
            uiState = state,
            onClose = { nav.popBackStack() },
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
}

package com.ptut.expense.di

import com.ptut.expense.add.AddViewModel
import com.ptut.expense.agent.AgentLoop
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.confirm.ConfirmSlipViewModel
import com.ptut.expense.detail.TransactionDetailViewModel
import com.ptut.expense.domain.agent.InterpretQuickAddUseCase
import com.ptut.expense.domain.categories.categoryUsage
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.rules.forShop
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.slip.FindSlipDebtsUseCase
import com.ptut.expense.domain.usage.UsageLog
import com.ptut.expense.edit.EditTransactionViewModel
import com.ptut.expense.review.ReviewViewModel
import com.ptut.expense.shoprule.ShopRuleViewModel
import com.ptut.expense.sync.flags.Flag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

// Above the module: a top-level val is read in file order, and the module reads this one.
private val CATEGORY_USAGE = named("categoryUsage")

/** Every way a row is written or changed: the Add sheet, the slip queue, one record and its edit. */
val recordModule: Module = module {
    // Counted once per ledger change, off the main thread, and shared by every category picker.
    single(CATEGORY_USAGE) {
        get<LedgerRepository>().allTransactions
            .map { snapshot -> (snapshot as? LedgerSnapshot.Rows)?.transactions.orEmpty().categoryUsage() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(appScope(), SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    // "A slip closes the debt", behind its own switch until it has been tried with real groups.
    factory { FindSlipDebtsUseCase(enabled = flagOn(Flag.SlipGroupDebt), shared = getOrNull()) }

    // The retention is read when the screen is built, like the categories: a change made
    // while the queue is on top shows the next time it is opened, not under the reader's thumb.
    viewModel {
        ReviewViewModel(
            observeQueuedSlips = get(),
            observeTransactions = get(),
            discardSlip = get(),
            retention = get<ObserveAppChoicesUseCase>().invoke().value.retention,
            haptics = get(),
            soonestExpiry = get(),
        )
    }

    // Takes the slip it is confirming as a parameter: one screen per slip, and a holder that
    // outlived the wrong one would offer the reader a different slip's amount.
    viewModel { (slipId: String) ->
        ConfirmSlipViewModel(
            slipId = slipId,
            getSlip = get(),
            observeSlip = get(),
            toasts = get(),
            categories = storedCategories(),
            categoryUsage = categoryUsage(),
            suggestCategory = { shop -> get<RuleRepository>().rules.value.forShop(shop)?.let { rule -> storedCategories().firstOrNull { it.id == rule.categoryId } } },
            offersPlanned = flagOn(Flag.Planned),
            subCategories = storedSubCategories(),
            findDebts = get(),
            haptics = get(),
            getLastRate = get(),
            confirmSlip = get(),
            paySlipDebt = get(),
            discardSlip = get(),
            discardWithUndo = get(),
            usageLog = getOrNull() ?: UsageLog.None,
        )
    }
    // Takes its row as a parameter for the same reason the confirm screen does: one screen per
    // record, and a holder that outlived the wrong one would offer the wrong row to delete.
    viewModel { (transactionId: String) ->
        TransactionDetailViewModel(
            transactionId = transactionId,
            observeTransactions = get(),
            toasts = get(),
            categories = storedCategories(),
            subCategories = storedSubCategories(),
            filingRules = get(),
            haptics = get(),
            shareOptions = get(),
            offersPlanned = flagOn(Flag.Planned),
            deleteImpact = get(),
            setPlannedUseCase = get(),
            deleteTransaction = get(),
            undoDelete = get(),
        )
    }
    viewModel { (transactionId: String) ->
        EditTransactionViewModel(
            transactionId = transactionId,
            observeTransactions = get(),
            known = storedCategories(),
            subCategories = storedSubCategories(),
            haptics = get(),
            shareOptions = get(),
            toasts = get(),
            editTransaction = get(),
            sharedUsage = categoryUsage(),
            savedState = get(),
        )
    }

    viewModel {
        // A lambda, not a list: the sheet's Expense/Income toggle re-asks on every switch, and the
        // holder lives as long as the shell, so each ask reads the table as it is now (#115).
        val scope = this
        AddViewModel(
            toasts = get(),
            categories = { scope.storedCategories().forDirection(it) },
            categoryUsage = categoryUsage(),
            subCategories = { scope.storedSubCategories() },
            haptics = get(),
            // getOrNull, as before: a graph that binds no agent draws no Quick add.
            interpretQuickAdd = getOrNull<AgentLoop>()?.let(::InterpretQuickAddUseCase),
            answerQuestion = get(),
            // #116: voice add is v2; without a speech input the sheet draws no mic.
            speech = getOrNull<SpeechInput>().takeIf { v2On() },
            offersCurrencies = currenciesOn(),
            getLastRate = get(),
            recordTransaction = get(),
            savedState = get(),
            usageLog = getOrNull() ?: UsageLog.None,
        )
    }

    viewModel { (shop: String) ->
        ShopRuleViewModel(
            shop = shop,
            observeRule = get(),
            recategoriseShop = get(),
            renameShop = get(),
            forgetShop = get(),
            categories = storedCategories(),
            categoryUsage = categoryUsage(),
        )
    }
}

/** How often each category was used, for the three chips a picker shows first (Hick's law pass). */
private fun Scope.categoryUsage(): StateFlow<Map<String, Int>> = get(CATEGORY_USAGE)

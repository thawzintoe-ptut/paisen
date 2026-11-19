package com.ptut.expense.di

import com.ptut.expense.add.AddViewModel
import com.ptut.expense.confirm.ConfirmSlipViewModel
import com.ptut.expense.detail.TransactionDetailViewModel
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.debtsPaidBy
import com.ptut.expense.domain.slip.ImportStore
import com.ptut.expense.edit.EditTransactionViewModel
import com.ptut.expense.review.ReviewViewModel
import com.ptut.expense.shoprule.ShopRuleViewModel
import com.ptut.expense.sync.flags.Flag
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Every way a row is written or changed: the Add sheet, the slip queue, one record and its edit. */
val recordModule: Module = module {
    // The retention is read when the screen is built, like the categories: a change made
    // while the queue is on top shows the next time it is opened, not under the reader's thumb.
    viewModel {
        ReviewViewModel(
            ledger = get(),
            discards = get(),
            retention = get<AppSettings>().choices.value.retention,
            haptics = get(),
        )
    }

    // Takes the slip it is confirming as a parameter: one screen per slip, and a holder that
    // outlived the wrong one would offer the reader a different slip's amount.
    viewModel { (slipId: String) ->
        ConfirmSlipViewModel(
            slipId = slipId,
            ledger = get(),
            importer = get(),
            toasts = get(),
            discards = get(),
            categories = storedCategories(),
            offersPlanned = flagOn(Flag.Planned),
            subCategories = storedSubCategories(),
            // "A slip closes the debt", behind its own switch until it has been tried with real groups.
            debtsFor = { amount, payee ->
                if (flagOn(Flag.SlipGroupDebt)) getOrNull<SharedRepository>()?.snapshot?.value?.debtsPaidBy(amount, payee).orEmpty() else emptyList()
            },
            recordPayment = { draft -> getOrNull<SharedRepository>()?.recordPayment(draft) },
            haptics = get(),
        )
    }
    // Takes its row as a parameter for the same reason the confirm screen does: one screen per
    // record, and a holder that outlived the wrong one would offer the wrong row to delete.
    viewModel { (transactionId: String) ->
        TransactionDetailViewModel(
            transactionId = transactionId,
            ledger = get(),
            toasts = get(),
            categories = storedCategories(),
            subCategories = storedSubCategories(),
            // getOrNull, like the shell's first-run seam: a screen test builds a graph with
            // no rules table in it, and the record still has to draw.
            rules = getOrNull(),
            haptics = get(),
            shared = getOrNull(),
            offersPlanned = flagOn(Flag.Planned),
        )
    }
    viewModel { (transactionId: String) ->
        EditTransactionViewModel(
            transactionId = transactionId,
            ledger = get(),
            known = storedCategories(),
            subCategories = storedSubCategories(),
            haptics = get(),
            shared = getOrNull(),
            toasts = get(),
        )
    }

    viewModel {
        // A lambda, not a list: the sheet's Expense/Income toggle re-asks on every switch, and the
        // holder lives as long as the shell, so each ask reads the table as it is now (#115).
        val scope = this
        AddViewModel(
            ledger = get(),
            toasts = get(),
            categories = { scope.storedCategories().forDirection(it) },
            subCategories = { scope.storedSubCategories() },
            haptics = get(),
            agent = getOrNull(),
            reader = getOrNull(),
            speech = getOrNull(),
            offersCurrencies = currenciesOn(),
            startingCurrency = { get<AppSettings>().choices.value.addCurrency?.let(Currencies::of) ?: phoneCurrency() },
            lastRate = { code -> getOrNull<ImportStore>()?.rateFor(code) },
            saveRate = { code, rate -> getOrNull<ImportStore>()?.saveRate(code, rate) },
        )
    }

    viewModel { (shop: String) ->
        ShopRuleViewModel(shop = shop, rules = get(), categories = storedCategories())
    }
}

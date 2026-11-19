package com.ptut.expense.di

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.forDirection
import com.ptut.expense.shared.AccountViewModel
import com.ptut.expense.shared.AddExpenseViewModel
import com.ptut.expense.shared.GroupDetailViewModel
import com.ptut.expense.shared.GroupSettingsViewModel
import com.ptut.expense.shared.JoinViewModel
import com.ptut.expense.shared.PeopleViewModel
import com.ptut.expense.shared.RecordPaymentViewModel
import com.ptut.expense.shared.RepeatExpenseViewModel
import com.ptut.expense.shared.SharedActivityViewModel
import com.ptut.expense.shared.SharedViewModel
import com.ptut.expense.shared.SignInViewModel
import com.ptut.expense.shared.WhoPaysWhomViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** v2's shared groups: the list, one group's screens, and signing in to share them. */
val sharedModule: Module = module {
    viewModel { SharedViewModel(repository = get(), sync = getOrNull(), runner = getOrNull()) }
    viewModel { PeopleViewModel(repository = get()) }
    // Koin's parameters do not carry a null, so a new repeat travels as "".
    viewModel { (groupId: String, ruleId: String) ->
        RepeatExpenseViewModel(
            groupId = groupId,
            ruleId = ruleId.ifEmpty { null },
            repository = get(),
            categories = storedCategories().forDirection(Direction.Expense),
            toasts = get(),
            runner = getOrNull(),
        )
    }
    viewModel { SignInViewModel(auth = getOrNull()) }
    viewModel { AccountViewModel(auth = getOrNull(), sync = getOrNull()) }
    viewModel { (code: String) -> JoinViewModel(code = code, sync = getOrNull(), auth = getOrNull()) }
    // One screen per group, like the record screens: a holder that outlived the wrong group would
    // show one group's balance under another's name.
    viewModel { (groupId: String) ->
        GroupDetailViewModel(groupId = groupId, repository = get(), categories = storedCategories(), sync = getOrNull(), runner = getOrNull(), toasts = get(), haptics = get())
    }
    viewModel { (groupId: String) -> GroupSettingsViewModel(groupId = groupId, repository = get(), sync = getOrNull(), runner = getOrNull()) }
    viewModel { (groupId: String, expenseId: String, transactionId: String, amount: String, description: String) ->
        AddExpenseViewModel(
            groupId = groupId,
            expenseId = expenseId.ifEmpty { null },
            repository = get(),
            categories = storedCategories().forDirection(Direction.Expense),
            transactionId = transactionId.ifEmpty { null },
            toasts = get(),
            haptics = get(),
            prefillAmount = amount.ifEmpty { null },
            prefillDescription = description.ifEmpty { null },
        )
    }
    viewModel { (groupId: String) -> WhoPaysWhomViewModel(groupId = groupId, repository = get()) }
    viewModel { (groupId: String, fromMemberId: String, toMemberId: String) ->
        RecordPaymentViewModel(groupId = groupId, fromMemberId = fromMemberId, toMemberId = toMemberId, repository = get(), toasts = get(), haptics = get())
    }
    viewModel { (groupId: String) ->
        SharedActivityViewModel(groupId = groupId.ifEmpty { null }, repository = get())
    }
}

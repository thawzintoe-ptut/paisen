package com.ptut.expense.di

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.PlayPart
import com.ptut.expense.shared.AccountViewModel
import com.ptut.expense.shared.AddExpenseViewModel
import com.ptut.expense.shared.GroupAwardsViewModel
import com.ptut.expense.shared.GroupDetailViewModel
import com.ptut.expense.shared.GroupPlayViewModel
import com.ptut.expense.shared.GroupSettingsViewModel
import com.ptut.expense.shared.JoinViewModel
import com.ptut.expense.shared.PeopleViewModel
import com.ptut.expense.shared.RecordPaymentViewModel
import com.ptut.expense.shared.RepeatExpenseViewModel
import com.ptut.expense.shared.SharedActivityViewModel
import com.ptut.expense.shared.SharedViewModel
import com.ptut.expense.shared.SignInViewModel
import com.ptut.expense.shared.TripRecapViewModel
import com.ptut.expense.shared.WhoPaysWheelViewModel
import com.ptut.expense.shared.WhoPaysWhomViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** v2's shared groups: the list, one group's screens, and signing in to share them. */
val sharedModule: Module = module {
    viewModel {
        SharedViewModel(
            observeSnapshot = get(),
            observeSyncStatus = get(),
            pollSync = get(),
            getSharedHome = get(),
            createGroupUseCase = get(),
            createPersonGroup = get(),
            toasts = get(),
        )
    }
    viewModel { PeopleViewModel(observeSnapshot = get(), getPeopleBalances = get(), setPersonArchived = get(), createPersonGroup = get(), toasts = get()) }
    // Koin's parameters do not carry a null, so a new repeat travels as "".
    viewModel { (groupId: String, ruleId: String) ->
        RepeatExpenseViewModel(
            groupId = groupId,
            ruleId = ruleId.ifEmpty { null },
            categories = storedCategories().forDirection(Direction.Expense),
            toasts = get(),
            observeSnapshot = get(),
            loadDraft = get(),
            homeToday = get(),
            saveRepeat = get(),
            stopRepeat = get(),
        )
    }
    viewModel { SignInViewModel(signInUseCase = get()) }
    viewModel { AccountViewModel(observeAccount = get(), signOutUseCase = get(), deleteAccountUseCase = get(), syncAllNow = get()) }
    viewModel { (code: String) -> JoinViewModel(code = code, getSyncAccess = get(), openInvite = get(), joinGroup = get()) }
    // One screen per group, like the record screens: a holder that outlived the wrong group would
    // show one group's balance under another's name.
    viewModel { (groupId: String) ->
        GroupDetailViewModel(
            groupId = groupId,
            categories = storedCategories(),
            observeSnapshot = get(),
            observeSyncStatus = get(),
            getSyncAccess = get(),
            pollSync = get(),
            trackSeen = get(),
            getGroupDetail = get(),
            syncGroupNow = get(),
            acknowledgeRefused = get(),
            savePromptPay = get(),
            removePaymentUseCase = get(),
            putBackPayment = get(),
            toasts = get(),
            haptics = get(),
        )
    }
    viewModel { (groupId: String) ->
        GroupSettingsViewModel(
            groupId = groupId,
            editGroupUseCase = get(),
            removeGroupUseCase = get(),
            addMembers = get(),
            removeMemberUseCase = get(),
            shareGroup = get(),
            renewInvite = get(),
            switchOffInvite = get(),
            toasts = get(),
            haptics = get(),
        )
    }
    viewModel { params ->
        val (groupId: String, expenseId: String, transactionId: String, amount: String, description: String) = params
        AddExpenseViewModel(
            groupId = groupId,
            expenseId = expenseId.ifEmpty { null },
            categories = storedCategories().forDirection(Direction.Expense),
            observeSnapshot = get(),
            loadDraft = get(),
            getLastRate = get(),
            saveExpense = get(),
            removeExpense = get(),
            restoreExpense = get(),
            transactionId = transactionId.ifEmpty { null },
            toasts = get(),
            haptics = get(),
            prefillAmount = amount.ifEmpty { null },
            prefillDescription = description.ifEmpty { null },
            // #94: the sixth parameter, past what Koin's destructuring reaches.
            prefillPayer = (params.values.getOrNull(PAYER_PARAMETER) as? String)?.ifEmpty { null },
            savedState = get(),
        )
    }
    viewModel { (groupId: String) ->
        WhoPaysWhomViewModel(
            groupId = groupId,
            observeSnapshot = get(),
            getSettlement = get(),
            savePromptPay = get(),
            setSimplifyDebts = get(),
            observeSyncStatus = get(),
            planOn = get<ObservePlayOnUseCase>().now(PlayPart.SettlePlan),
        )
    }
    viewModel { (groupId: String, fromMemberId: String, toMemberId: String, amount: String) ->
        RecordPaymentViewModel(
            groupId = groupId,
            fromMemberId = fromMemberId,
            toMemberId = toMemberId,
            observeSnapshot = get(),
            getPaymentBetween = get(),
            recordPayment = get(),
            removePayment = get(),
            toasts = get(),
            haptics = get(),
            prefillAmount = amount.ifEmpty { null },
            savedState = get(),
        )
    }
    viewModel { (groupId: String) -> WhoPaysWheelViewModel(groupId = groupId, observeSnapshot = get(), getWheel = get()) }
    viewModel { (groupId: String) ->
        GroupAwardsViewModel(groupId = groupId, observeSnapshot = get(), observeSyncStatus = get(), getAwards = get(), homeToday = get())
    }
    viewModel { (groupId: String) ->
        TripRecapViewModel(groupId = groupId, observeSnapshot = get(), getRecap = get(), shareAmounts = choice(false) { it.play.shareAmounts })
    }
    viewModel { (groupId: String) ->
        val playOn = get<ObservePlayOnUseCase>()
        GroupPlayViewModel(
            groupId = groupId,
            observeSnapshot = get(),
            observeSyncStatus = get(),
            playOn = playOn::now,
            earnAllSquare = get(),
            reactToPayment = get(),
            setTripEnded = get(),
        )
    }
    viewModel { (groupId: String) ->
        SharedActivityViewModel(groupId = groupId.ifEmpty { null }, observeSnapshot = get(), getActivity = get())
    }
}

/** Where Add expense's payer sits among its route parameters. */
private const val PAYER_PARAMETER = 5

package com.ptut.expense.di

import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.onboarding.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** First run: the answer it keeps, and the screens that ask for it. */
val onboardingModule: Module = module {
    /**
     * A `single` for the reason [com.ptut.expense.ui.ToastMessages] is one: three callers share the
     * same answer and one of them writes it — the shell picks the screen, [OnboardingViewModel]
     * finishes first run, and each platform's import trigger waits for it before raising FR-2's dialog.
     */
    single { Onboarding(flag = get()) }

    viewModel {
        OnboardingViewModel(
            onboarding = get(),
            photoAccess = get(),
            archive = getOrNull(),
            files = getOrNull(),
            toasts = get(),
            homeBook = getOrNull(),
            // #41: first run suggests the phone's money and zone.
            suggestedHome = if (currenciesOn()) {
                HomeChoice(phoneCurrency().toHomeMoney(), phoneZone())
            } else {
                null
            },
        )
    }
}

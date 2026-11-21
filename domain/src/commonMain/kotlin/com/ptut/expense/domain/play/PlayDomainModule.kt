package com.ptut.expense.domain.play

import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases behind play (#82). The gate is the app's to bind, since only it reads the flags. */
val playUseCases: Module = module {
    factory { ObservePlayChoicesUseCase(get()) }
    factory { SetPlayChoicesUseCase(get()) }
    factory { MarkWelcomedUseCase(get(), get()) }
    factory { ObservePlayOnUseCase(get(), gate = getOrNull() ?: PlayGate.Off) }
    factory { ObserveTodayPlayUseCase(get(), get(), get(), transactions = get()) }
    factory { GetDailyQuestionUseCase(get(), get(), get()) }
    factory { AnswerDailyQuestionUseCase(get()) }
    factory { StartGuessUseCase(get(), get()) }
    factory { RevealGuessUseCase(get(), get(), get()) }
    factory { GetWheelUseCase(get()) }
    factory { ReactToPaymentUseCase(get()) }
    factory { EarnAllSquareUseCase(get(), get()) }
    // getOrNull: a screen test's graph binds no shared groups; the opt-out then has nowhere to go.
    factory { SetStampsOffUseCase(getOrNull(), get()) }
    factory { SetTripEndedUseCase(get(), get()) }
    factory { GetGroupAwardsUseCase() }
    factory { GetTripRecapUseCase() }
    factory { GetChallengeChoicesUseCase(get(), get(), get()) }
    factory { PickChallengeUseCase(get(), get(), get()) }
    factory { SettleChallengeUseCase(get(), get(), get(), get()) }
    factory { ObserveGoalJarUseCase(get(), get(), get()) }
    factory { SaveGoalUseCase(get(), get()) }
    factory { ArchiveGoalUseCase(get()) }
    factory { AnswerGoalDayUseCase(get(), get(), get()) }
    factory { GetMonthStoryUseCase(get(), get(), get()) }
    factory { MarkStorySeenUseCase(get()) }
    factory { GetMonthCloseUseCase(get(), get(), get()) }
    factory { SealMonthUseCase(get(), get()) }
    factory { GetMoneyScoreUseCase(get(), get(), get()) }
    factory { ObserveSealedMonthsUseCase(get(), get()) }
    factory { ObserveStampUseCase(get()) }
    factory { RevealStampUseCase(get()) }
    factory { ObserveStampBookUseCase(get(), get(), get()) }
}

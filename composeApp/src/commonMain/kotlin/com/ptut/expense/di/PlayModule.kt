package com.ptut.expense.di

import com.ptut.expense.domain.play.PlayGate
import com.ptut.expense.domain.play.WatchTrayUseCase
import com.ptut.expense.play.ChallengeViewModel
import com.ptut.expense.play.CloseMonthViewModel
import com.ptut.expense.play.DailyQuestionViewModel
import com.ptut.expense.play.GoalJarViewModel
import com.ptut.expense.play.GuessViewModel
import com.ptut.expense.play.MoneyScoreViewModel
import com.ptut.expense.play.MonthStoryViewModel
import com.ptut.expense.play.PlaySettingsViewModel
import com.ptut.expense.play.StampBookViewModel
import com.ptut.expense.play.StampRevealViewModel
import com.ptut.expense.sync.flags.Flag
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Play (#82): the gate the flags set at launch, and play's screens. */
val playModule: Module = module {
    // D8, D17: the Remote Config switch and the build, read once per launch.
    single { PlayGate(flagOn = flagOn(Flag.Play), debug = isDebug()) }

    // #85: one watcher, so one write marks a day.
    single { WatchTrayUseCase(queued = get(), scanning = get(), playOn = get(), transactions = get(), book = get()) }

    viewModel { PlaySettingsViewModel(observeChoices = get(), setChoices = get(), playOn = get(), setStampsOff = get(), planReminder = getOrNull()) }
    viewModel { (stampId: String) -> StampRevealViewModel(stampId = stampId, observeStamp = get(), revealStamp = get()) }
    viewModel { StampBookViewModel(observeStampBook = get()) }
    viewModel { DailyQuestionViewModel(getDailyQuestion = get(), answer = get(), categories = liveCategories()) }
    viewModel { (monday: String) -> GuessViewModel(monday = monday, startGuess = get(), revealGuess = get()) }
    viewModel { ChallengeViewModel(getChoices = get(), pickChallenge = get(), categories = liveCategories()) }
    viewModel { (goalId: String, name: String, target: String) ->
        GoalJarViewModel(
            goalId = goalId.ifEmpty { null },
            name = name.ifEmpty { null },
            target = target.ifEmpty { null },
            observeJar = get(),
            saveGoal = get(),
            archiveGoal = get(),
            categories = liveCategories(),
        )
    }
    viewModel { (month: String) ->
        MonthStoryViewModel(month = month, getStory = get(), markSeen = get(), categories = liveCategories(), shareAmounts = choice(false) { it.play.shareAmounts })
    }
    viewModel { (month: String) -> CloseMonthViewModel(month = month, getClose = get(), sealMonth = get(), categories = liveCategories()) }
    viewModel { MoneyScoreViewModel(getScore = get()) }
}

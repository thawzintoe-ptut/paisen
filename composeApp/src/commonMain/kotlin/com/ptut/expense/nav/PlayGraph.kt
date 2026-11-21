package com.ptut.expense.nav

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ptut.expense.play.ChallengeView
import com.ptut.expense.play.ChallengeViewModel
import com.ptut.expense.play.CloseMonthView
import com.ptut.expense.play.CloseMonthViewModel
import com.ptut.expense.play.DailyQuestionView
import com.ptut.expense.play.DailyQuestionViewModel
import com.ptut.expense.play.GoalJarView
import com.ptut.expense.play.GoalJarViewModel
import com.ptut.expense.play.GuessView
import com.ptut.expense.play.GuessViewModel
import com.ptut.expense.play.MoneyScoreView
import com.ptut.expense.play.MoneyScoreViewModel
import com.ptut.expense.play.MonthStoryView
import com.ptut.expense.play.MonthStoryViewModel
import com.ptut.expense.play.PlaySettingsView
import com.ptut.expense.play.PlaySettingsViewModel
import com.ptut.expense.play.StampBookView
import com.ptut.expense.play.StampBookViewModel
import com.ptut.expense.play.StampRevealView
import com.ptut.expense.play.StampRevealViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Play's screens (#82). */
internal fun NavGraphBuilder.playRoutes(nav: NavHostController) {
    composable<PtutRoute.PlaySettings> {
        val play = koinViewModel<PlaySettingsViewModel>()
        val state by play.uiState.collectAsStateWithLifecycle()
        PlaySettingsView(uiState = state, onSet = play::set, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.StampReveal> { entry ->
        val stampId = entry.toRoute<PtutRoute.StampReveal>().stampId
        val reveal = koinViewModel<StampRevealViewModel> { parametersOf(stampId) }
        val state by reveal.uiState.collectAsStateWithLifecycle()
        StampRevealView(uiState = state, onTurn = reveal::turn, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.StampBook> {
        val book = koinViewModel<StampBookViewModel>()
        val state by book.uiState.collectAsStateWithLifecycle()
        StampBookView(uiState = state, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.DailyQuestion> {
        val question = koinViewModel<DailyQuestionViewModel>()
        val state by question.uiState.collectAsStateWithLifecycle()
        DailyQuestionView(
            uiState = state,
            onPick = question::pick,
            onOpenStamp = { nav.navigate(PtutRoute.StampReveal(it)) },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.Guess> { entry ->
        val monday = entry.toRoute<PtutRoute.Guess>().monday
        val guess = koinViewModel<GuessViewModel> { parametersOf(monday) }
        val state by guess.uiState.collectAsStateWithLifecycle()
        GuessView(
            uiState = state,
            onNudge = guess::nudge,
            onSet = guess::set,
            onReveal = guess::reveal,
            onSeeWeek = { from, to -> nav.navigate(PtutRoute.LedgerSlice(from, to)) { launchSingleTop = true } },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.WeekChallenge> {
        val challenge = koinViewModel<ChallengeViewModel>()
        val state by challenge.uiState.collectAsStateWithLifecycle()
        ChallengeView(uiState = state, onPick = challenge::pick, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.GoalJar> { entry ->
        val route = entry.toRoute<PtutRoute.GoalJar>()
        // Koin's parameters do not carry a null, so a missing one travels as "".
        val jar = koinViewModel<GoalJarViewModel> { parametersOf(route.goalId.orEmpty(), route.name.orEmpty(), route.target.orEmpty()) }
        val state by jar.uiState.collectAsStateWithLifecycle()
        GoalJarView(
            uiState = state,
            onName = jar::setName,
            onTarget = jar::setTarget,
            onSave = jar::save,
            onWhatIf = jar::whatIf,
            onArchive = { jar.archive { nav.popBackStack() } },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.MonthStory> { entry ->
        val month = entry.toRoute<PtutRoute.MonthStory>().month
        val story = koinViewModel<MonthStoryViewModel> { parametersOf(month) }
        val state by story.uiState.collectAsStateWithLifecycle()
        MonthStoryView(uiState = state, onNext = { if (!story.next()) nav.popBackStack() }, onBack = { nav.popBackStack() })
    }

    composable<PtutRoute.CloseMonth> { entry ->
        val month = entry.toRoute<PtutRoute.CloseMonth>().month
        val close = koinViewModel<CloseMonthViewModel> { parametersOf(month) }
        val state by close.uiState.collectAsStateWithLifecycle()
        CloseMonthView(
            uiState = state,
            onNext = close::next,
            onSeal = close::seal,
            onOpenSlips = { nav.navigate(PtutRoute.Review) { launchSingleTop = true } },
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.MoneyScore> {
        val score = koinViewModel<MoneyScoreViewModel>()
        val state by score.uiState.collectAsStateWithLifecycle()
        MoneyScoreView(uiState = state, onBack = { nav.popBackStack() })
    }
}

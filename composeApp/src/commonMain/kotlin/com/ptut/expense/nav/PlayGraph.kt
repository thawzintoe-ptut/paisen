package com.ptut.expense.nav

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ptut.expense.domain.play.PlayTip
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
import com.ptut.expense.play.JarSheetActions
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
import com.ptut.expense.shared.BankApp
import com.ptut.expense.shared.rememberBankApps
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Play's screens (#82). */
internal fun NavGraphBuilder.playRoutes(nav: NavHostController) {
    composable<PtutRoute.PlaySettings> {
        val play = koinViewModel<PlaySettingsViewModel>()
        val state by play.uiState.collectAsStateWithLifecycle()
        PlaySettingsView(uiState = state, onSet = play::set, onBack = { nav.popBackStack() }, onShowTipsAgain = play::showTipsAgain)
    }

    composable<PtutRoute.StampReveal> { entry ->
        val stampId = entry.toRoute<PtutRoute.StampReveal>().stampId
        val reveal = koinViewModel<StampRevealViewModel> { parametersOf(stampId) }
        val state by reveal.uiState.collectAsStateWithLifecycle()
        StampRevealView(uiState = state, onTurn = reveal::turn, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() }, tip = playTip(PlayTip.Stamp))
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
            tip = playTip(PlayTip.Guess),
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
        val jar = koinViewModel<GoalJarViewModel> { parametersOf(route.goalId.orEmpty(), route.name.orEmpty(), route.target.orEmpty(), route.putIn) }
        val state by jar.uiState.collectAsStateWithLifecycle()
        val bankApps = rememberBankApps()
        GoalJarView(
            tip = playTip(PlayTip.Jar),
            uiState = state,
            onName = jar::setName,
            onTarget = jar::setTarget,
            onSave = jar::save,
            onEdit = jar::edit,
            onPutIn = jar::openPutIn,
            onChangeAccount = jar::changeAccount,
            onRemoveDeposit = jar::remove,
            onArchive = { jar.archive { nav.popBackStack() } },
            onBack = { nav.popBackStack() },
            sheet = JarSheetActions(
                onDismiss = jar::dismissSheet,
                onPick = jar::pick,
                // J03: K PLUS where the phone can name it; elsewhere the reader opens it, and the sheet goes either way.
                onOpenBank = {
                    // iOS cannot list apps, so it is handed K PLUS by name and asks by URL (#113).
                    val kPlus = if (bankApps.canList) bankApps.installed().firstOrNull { it.name == K_PLUS } else BankApp(id = K_PLUS, name = K_PLUS)
                    kPlus?.let(bankApps::open)
                    jar.bankOpened()
                },
                onMoved = jar::moved,
                onPickAccount = jar::pickAccount,
                onUseAccount = jar::useAccount,
            ),
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

/** The bank app whose slips the jar reads, as [com.ptut.expense.shared.BankApp.name] has it. */
private const val K_PLUS = "K PLUS"

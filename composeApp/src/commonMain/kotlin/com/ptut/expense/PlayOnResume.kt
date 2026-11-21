package com.ptut.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ptut.expense.domain.play.PlanPlayReminderUseCase
import com.ptut.expense.domain.play.SetStampsOffUseCase
import com.ptut.expense.domain.play.SettleChallengeUseCase
import com.ptut.expense.domain.play.WatchTrayUseCase
import com.ptut.expense.domain.runSuspendCatching
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

/** #85 (D2): the tray is watched only while the app is on screen, beside [SyncOnResume]. */
@Composable
internal fun PlayOnResume() {
    val koin = getKoin()
    val scope = rememberCoroutineScope()
    val watchTray = remember(koin) { koin.getOrNull<WatchTrayUseCase>() }
    // D15: the weekly reminder is planned again each time the app comes back, as repeats are.
    val planReminder = remember(koin) { koin.getOrNull<PlanPlayReminderUseCase>() }
    // D5: a group joined since the switch moved takes the same choice.
    val stampsOff = remember(koin) { koin.getOrNull<SetStampsOffUseCase>() }
    // #91: last week's challenge earns its stamp on the first open after the week.
    val settleChallenge = remember(koin) { koin.getOrNull<SettleChallengeUseCase>() }
    LifecycleResumeEffect(watchTray, planReminder, stampsOff, settleChallenge) {
        val watching = scope.launch { watchTray?.let { watch -> runSuspendCatching { watch() } } }
        val planning = scope.launch {
            planReminder?.let { plan -> runSuspendCatching { plan() } }
            stampsOff?.let { apply -> runSuspendCatching { apply() } }
            settleChallenge?.let { settle -> runSuspendCatching { settle() } }
        }
        onPauseOrDispose {
            watching.cancel()
            planning.cancel()
        }
    }
}

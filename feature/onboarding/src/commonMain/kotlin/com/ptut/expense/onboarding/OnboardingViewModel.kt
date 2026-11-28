package com.ptut.expense.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.backup.RestoreStep
import com.ptut.expense.domain.folders.ApplyWatchedFoldersUseCase
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.RefreshFolderCatalogUseCase
import com.ptut.expense.domain.home.ChooseHomeUseCase
import com.ptut.expense.domain.play.MarkWelcomedUseCase
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.phrase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** First run's three steps, in order (#102): what PaiSeN does with slips, the slip folder, then an optional goal. */
enum class OnboardingStep { Promise, Slips, Goal }

/** #102: step three's ready-made goals. The reader names none of them; the copy does. */
enum class GoalPreset { Trip, SomethingNew, Cushion }

/** Step two's answer. [Unasked] until the dialog closes; [Denied] still has a way on. */
sealed interface SlipAccess {
    data object Unasked : SlipAccess

    data object Denied : SlipAccess

    /** [chosen] is the folder id the reader picked, or null before they pick. */
    data class Granted(val folders: List<DeviceFolder>, val chosen: String? = null) : SlipAccess
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Promise,
    val access: SlipAccess = SlipAccess.Unasked,
    /** Step three (S03): a trip is picked from the start. Nothing is saved until Start my jar. */
    val goal: GoalPreset = GoalPreset.Trip,
    val goalAmount: String = "",
) {
    /** "Step N of 3". */
    val stepNumber: Int get() = step.ordinal + 1

    val canStartJar: Boolean get() = goalTarget(goalAmount) != null
}

internal fun goalTarget(typed: String): Satang? = AmountEntry.ofText(typed)?.toSatang()?.takeIf { it > Satang.Zero }

/**
 * First run, the only screen that asks for anything (§4: one user, no accounts): the promise,
 * photo access with the slip folder, then done.
 *
 * Refusing photos is not a dead end. NFR-2's manual path needs none, so [SlipAccess.Denied] says
 * what still works and lets the reader go on or try again.
 */
class OnboardingViewModel(
    private val onboarding: Onboarding,
    private val photoAccess: PhotoAccessRequest,
    /** RESTORE's two seams (#171). Null where a screen test binds no archive; RESTORE then does nothing. */
    private val restoreSteps: RestoreDriver? = null,
    private val files: LedgerFileStore? = null,
    private val toasts: ToastMessages? = null,
    /** #41: commits the home. Null where no home book is bound; first run then sets nothing. */
    private val chooseHomeUseCase: ChooseHomeUseCase? = null,
    /** #41: what first run sets, or null where the build does not offer a home. Settings changes it later. */
    private val firstRunHome: HomeChoice? = null,
    /** Photo folders' own seams. Null where no catalogue is bound; access then reads as granted with no folders. */
    private val refreshCatalog: RefreshFolderCatalogUseCase? = null,
    private val applyWatchedFolders: ApplyWatchedFoldersUseCase? = null,
    /** The system page for a reader who refused; null where none is bound. */
    private val photoSettings: PhotoSettings? = null,
    /** #102: Start my jar. Null where no play book is bound; step three then saves nothing. */
    private val saveGoal: SaveGoalUseCase? = null,
    /** #102: the day first run ended, which starts Today's first week. Null where no settings are bound. */
    private val markWelcomed: MarkWelcomedUseCase? = null,
    /** #116: picking the slip folder is v2. Without it step two shows the K PLUS setting alone. */
    private val offersFolders: Boolean = true,
    /** The step and the typed goal, kept through Android killing the process. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // S03 fills in a baht amount; another home money starts empty, since 12,000 means nothing there.
    private val state = MutableStateFlow(
        OnboardingUiState(
            step = savedState.get<String>(STEP_KEY)?.let { saved -> OnboardingStep.entries.firstOrNull { it.name == saved } } ?: OnboardingStep.Promise,
            goalAmount = savedState.get<String>(GOAL_AMOUNT_KEY)
                ?: if ((firstRunHome?.money ?: Home.money).code == BAHT) SUGGESTED_BAHT_GOAL else "",
        ),
    )

    init {
        viewModelScope.launch {
            state.collect {
                savedState[STEP_KEY] = it.step.name
                savedState[GOAL_AMOUNT_KEY] = it.goalAmount
            }
        }
    }

    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    val canOpenSettings: Boolean get() = photoSettings != null

    /** Written before first run ends, while the ledger is still empty. */
    private suspend fun commitHome() {
        chooseHomeUseCase?.invoke(firstRunHome)
    }

    private val restoring = MutableStateFlow(false)

    /** True while a restore is reading or merging, so RESTORE cannot be pressed twice. */
    val isRestoring: StateFlow<Boolean> = restoring.asStateFlow()

    private val step: StateFlow<RestoreStep> = restoreSteps?.step ?: MutableStateFlow(RestoreStep.Idle)

    private val problem = MutableStateFlow<String?>(null)

    /**
     * Why the last restore did not work, said on first run itself: toasts only draw inside the
     * shell, and the shell is not there yet.
     */
    val restoreProblem: StateFlow<String?> = combine(problem, step) { said, now -> (now as? RestoreStep.Refused)?.reason?.phrase() ?: said }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Guards the second tap while the system dialog is up. Without it a double tap queues a second
     * ask, which on Android resolves instantly against the dialog already showing.
     */
    private var asking = false

    /** One step on: Promise to Slips, Slips to Goal. The goal step ends with Start my jar or Skip. */
    fun next() {
        state.update {
            when (it.step) {
                OnboardingStep.Promise -> it.copy(step = OnboardingStep.Slips)
                OnboardingStep.Slips -> it.copy(step = OnboardingStep.Goal)
                OnboardingStep.Goal -> it
            }
        }
    }

    /** #102: Back on every step after the first. What was answered stays answered. */
    fun back() {
        state.update {
            when (it.step) {
                OnboardingStep.Promise -> it
                OnboardingStep.Slips -> it.copy(step = OnboardingStep.Promise)
                OnboardingStep.Goal -> it.copy(step = OnboardingStep.Slips)
            }
        }
    }

    /**
     * FR-2, and Try again. The folders are read after the dialog closes, so the answer is in. From
     * step one, Allow photos moves on to step two, which shows the answer.
     */
    fun requestPhotoAccess() {
        if (asking) return
        asking = true
        clearProblem()
        viewModelScope.launch {
            try {
                photoAccess.ask()
                readAccess()
                state.update { if (it.step == OnboardingStep.Promise) it.copy(step = OnboardingStep.Slips) else it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problem.value = AppText.copy.settings.didNotWork
            } finally {
                asking = false
            }
        }
    }

    /** Access granted in the system settings shows when the reader comes back. */
    fun refreshAccess() {
        if (state.value.access != SlipAccess.Denied) return
        viewModelScope.launch { readAccess() }
    }

    fun openPhoneSettings() {
        photoSettings?.open()
    }

    /** No folders is a refusal only when the phone does not say photos are allowed: a new phone has none yet. */
    private suspend fun readAccess() {
        val catalog = refreshCatalog ?: run {
            state.update { it.copy(access = SlipAccess.Granted(emptyList())) }
            return
        }
        val folders = catalog()
        val refused = folders.isEmpty() && photoSettings?.photosAllowed != true
        state.update { it.copy(access = if (refused) SlipAccess.Denied else SlipAccess.Granted(folders.takeIf { offersFolders }.orEmpty())) }
    }

    /** FR-5: the picked folder is watched; a second pick replaces the first. */
    fun chooseFolder(id: String) {
        val granted = state.value.access as? SlipAccess.Granted ?: return
        val folder = granted.folders.firstOrNull { it.id == id } ?: return
        if (granted.chosen == id) return
        state.update { it.copy(access = granted.copy(chosen = id)) }
        val apply = applyWatchedFolders ?: return
        viewModelScope.launch {
            if (!apply(stop = listOfNotNull(granted.chosen), watch = listOf(folder))) {
                problem.value = AppText.copy.settings.didNotWork
            }
        }
    }

    /** Slips to Goal, whatever the answer was: NFR-2's path for a reader who would rather type. */
    fun skip() {
        state.update { it.copy(step = OnboardingStep.Goal) }
    }

    /** S03: one of the three is always picked; Not now is how a reader says none. */
    fun chooseGoal(preset: GoalPreset) {
        state.update { it.copy(goal = preset) }
    }

    fun setGoalAmount(text: String) {
        state.update { it.copy(goalAmount = text.filter { c -> c.isDigit() || c == '.' }) }
    }

    /** #102: Start my jar saves the goal, then opens Today. A goal that cannot be read saves nothing. */
    fun startJar() {
        val now = state.value
        val preset = now.goal
        val target = goalTarget(now.goalAmount) ?: return
        val save = saveGoal ?: return finish()
        whileFinishing { save(goalId = null, name = preset.goalName(), target = target) }
    }

    /**
     * Open Today, with no goal: Skip on step three, and step one's Skip and I'll add by hand. A
     * skipped goal leaves no jar.
     */
    fun finish() {
        whileFinishing()
    }

    private var finishing = false

    /** The home is written while the ledger is still empty, then first run ends and Today's first week starts. */
    private fun whileFinishing(goal: (suspend () -> Unit)? = null) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            try {
                // Without its home the ledger would start in the wrong money, so first run stays.
                if (runSuspendCatching { commitHome() }.isFailure) {
                    problem.value = AppText.copy.settings.didNotWork
                    return@launch
                }
                // A goal that failed to save is not a reason to keep the reader on first run.
                goal?.let { save -> runSuspendCatching { save() }.onFailure { toasts?.show(ToastMessage(AppText.copy.settings.didNotWork)) } }
                markWelcomed?.invoke()
                onboarding.finish()
            } finally {
                finishing = false
            }
        }
    }

    /** #220: what the chosen file holds, asked about before anything merges. Null while no file waits. */
    val restorePreview: StateFlow<String?> = step.map { (it as? RestoreStep.Preview)?.holds?.phrase() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * RESTORE (#171, S5), first half: read the file and say what it holds (#220), as Import my data
     * does. Backing out of the picker stays on first run and says nothing.
     */
    fun restore() {
        val restoreSteps = restoreSteps ?: return
        val files = files ?: return
        whileRestoring {
            val text = files.read() ?: return@whileRestoring
            restoreSteps.read(text)
        }
    }

    /**
     * Import on the preview. A restore that merged ends first run, because the reader now has a
     * ledger to look at; the merge sentence waits as a toast for the shell.
     */
    fun confirmRestore() {
        val restoreSteps = restoreSteps ?: return
        if (step.value !is RestoreStep.Preview) return
        whileRestoring {
            val merged = restoreSteps.import { merge ->
                // Before the restore, so a file with no home of its own lands in the chosen one; a file
                // that carries one takes it over this. The launch home as chosen needs no rebuild.
                if (firstRunHome != Home.choice.value) commitHome()
                merge()
            } ?: return@whileRestoring
            toasts?.show(ToastMessage(merged.phrase()))
            onboarding.finish()
        }
    }

    /** Cancel on the preview: the file is let go and nothing has changed. */
    fun cancelRestore() {
        restoreSteps?.cancel()
    }

    /** A refusal is cleared with the rest, so a new attempt starts with no line under the buttons. */
    private fun clearProblem() {
        problem.value = null
        if (step.value is RestoreStep.Refused) restoreSteps?.cancel()
    }

    private fun whileRestoring(work: suspend () -> Unit) {
        if (restoring.value) return
        restoring.value = true
        clearProblem()
        viewModelScope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                restoreSteps?.cancel()
                // A refused file is RestoreStep.Refused; any other text is the platform's English.
                problem.value = AppText.copy.onboarding.restoreFailed
            } finally {
                restoring.value = false
            }
        }
    }
}

private const val BAHT = "THB"
private const val STEP_KEY = "onboarding.step"
private const val GOAL_AMOUNT_KEY = "onboarding.goalAmount"
private const val SUGGESTED_BAHT_GOAL = "12000"

/** The goal's name as the reader's language says it; the jar keeps it as saved. */
private fun GoalPreset.goalName(): String = AppText.copy.onboarding.let { text ->
    when (this) {
        GoalPreset.Trip -> text.goalTrip
        GoalPreset.SomethingNew -> text.goalNew
        GoalPreset.Cushion -> text.goalCushion
    }
}

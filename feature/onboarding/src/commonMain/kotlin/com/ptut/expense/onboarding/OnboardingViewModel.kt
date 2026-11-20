package com.ptut.expense.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.backup.RestoreStep
import com.ptut.expense.domain.home.ChooseHomeUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
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
import kotlinx.coroutines.launch

/**
 * First run's two exits. §4: the spec has one user and no accounts, so this screen replaces sign-in
 * and is the only place the app asks for anything.
 *
 * Neither exit can fail, and that is deliberate. FR-2's photo access is what makes the ledger fill
 * itself, but NFR-2's manual path does not depend on it — so a refusal ends first run exactly as an
 * allow does, and the reader lands on Today either way.
 *
 * There is no `uiState`: nothing on this screen changes with what the app knows. The copy is fixed
 * and the two buttons are always live, so a state flow here would carry a constant.
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
) : ViewModel() {

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
     * ask, which on Android resolves instantly against the dialog already showing and finishes first
     * run under the reader's finger before they have answered it.
     */
    private var asking = false

    /** FR-2. Finishes on the dialog closing, not on the tap, so the answer is in before Today is. */
    fun requestPhotoAccess() {
        if (asking) return
        asking = true
        clearProblem()
        viewModelScope.launch {
            try {
                photoAccess.ask()
                commitHome()
                onboarding.finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The line under the buttons, as for a restore: toasts only draw inside the shell.
                problem.value = AppText.copy.settings.didNotWork
            } finally {
                asking = false
            }
        }
    }

    /** NFR-2's path for a reader who would rather type. The folder simply stays unread. */
    fun skip() {
        if (firstRunHome == null || chooseHomeUseCase == null) {
            onboarding.finish()
            return
        }
        viewModelScope.launch {
            commitHome()
            onboarding.finish()
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

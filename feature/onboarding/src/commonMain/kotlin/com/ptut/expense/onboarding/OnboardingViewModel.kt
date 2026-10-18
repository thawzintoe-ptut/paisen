package com.ptut.expense.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.phrase
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val archive: LedgerArchive? = null,
    private val files: LedgerFileStore? = null,
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    private val restoring = MutableStateFlow(false)

    /** True while a restore is reading or merging, so RESTORE cannot be pressed twice. */
    val isRestoring: StateFlow<Boolean> = restoring.asStateFlow()

    private val problem = MutableStateFlow<String?>(null)

    /**
     * Why the last restore did not work, said on first run itself: toasts only draw inside the
     * shell, and the shell is not there yet.
     */
    val restoreProblem: StateFlow<String?> = problem.asStateFlow()

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
        viewModelScope.launch {
            photoAccess.ask()
            asking = false
            onboarding.finish()
        }
    }

    /** NFR-2's path for a reader who would rather type. The folder simply stays unread. */
    fun skip() = onboarding.finish()

    private var chosenText: String? = null
    private val preview = MutableStateFlow<String?>(null)

    /** #220: what the chosen file holds, asked about before anything merges. Null while no file waits. */
    val restorePreview: StateFlow<String?> = preview.asStateFlow()

    /**
     * RESTORE (#171, S5), first half: read the file and say what it holds (#220), as Import my data
     * does. Backing out of the picker stays on first run and says nothing.
     */
    fun restore() {
        val archive = archive ?: return
        val files = files ?: return
        whileRestoring {
            val text = files.read() ?: return@whileRestoring
            preview.value = archive.preview(text).phrase()
            chosenText = text
        }
    }

    /**
     * Import on the preview. A restore that merged ends first run, because the reader now has a
     * ledger to look at; the merge sentence waits as a toast for the shell.
     */
    fun confirmRestore() {
        val archive = archive ?: return
        val text = chosenText ?: return
        whileRestoring {
            letGo()
            toasts?.show(ToastMessage(archive.restore(text).phrase()))
            onboarding.finish()
        }
    }

    /** Cancel on the preview: the file is let go and nothing has changed. */
    fun cancelRestore() = letGo()

    private fun letGo() {
        chosenText = null
        preview.value = null
    }

    private fun whileRestoring(work: suspend () -> Unit) {
        if (restoring.value) return
        restoring.value = true
        problem.value = null
        viewModelScope.launch {
            try {
                work()
            } catch (failure: Exception) {
                letGo()
                problem.value = failure.message ?: AppText.copy.onboarding.restoreFailed
            } finally {
                restoring.value = false
            }
        }
    }
}

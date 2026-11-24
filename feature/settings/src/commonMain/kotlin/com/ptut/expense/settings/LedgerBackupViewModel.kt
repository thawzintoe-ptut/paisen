package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.backup.BuildLedgerExportUseCase
import com.ptut.expense.domain.backup.LedgerHolding
import com.ptut.expense.domain.backup.ObserveLedgerHoldingUseCase
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.backup.RestoreStep
import com.ptut.expense.domain.backup.ledgerFileName
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.phrase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerBackupUiState(
    /** What the file will hold, counted rather than promised: "412 transactions, back to 3 Jun". */
    val holding: String,
    /** The name the picker opens with, shown so the reader knows what to look for afterwards. */
    val fileName: String,
    /** True while a picker is open, so neither screen can start a second one. */
    val busy: Boolean,
    /**
     * False while the ledger is unanswered and on one holding nothing. Export is offered against
     * a count, so a file name promised over "nothing to export" is a promise about a file the
     * reader would open to find empty — and importing it back would be a no-op they had to chase.
     */
    val exportable: Boolean,
    /**
     * #220: what the chosen file holds, "412 transactions, back to 3 Jun · 2 shared groups". Null
     * until a file is chosen, and again once it is imported or let go. Nothing is merged before Import.
     */
    val preview: String? = null,
    /**
     * #73: why the chosen file was refused, said on the screen with the ledger untouched and a way
     * to pick again. Null until a file is refused, and again once another file reads.
     */
    val refused: String? = null,
    /** What the last import added, kept on the import screen until the reader leaves it. */
    val merged: String? = null,
)

/**
 * FR-22, both directions, behind the two Settings rows that name them.
 *
 * One class for both screens because they are one seam read twice: the same archive, the same
 * picker, the same count and the same guard against a second tap. Each screen still resolves its
 * own instance, which is fine — they are separate destinations, so only one is ever on top.
 */
class LedgerBackupViewModel(
    observeHolding: ObserveLedgerHoldingUseCase,
    /** Read, preview, then import or cancel; one per screen, since it holds the chosen file. */
    private val restoring: RestoreDriver,
    private val files: LedgerFileStore,
    private val toasts: ToastMessages,
    private val homeToday: GetHomeTodayUseCase,
    private val buildExport: BuildLedgerExportUseCase,
) : ViewModel() {

    /**
     * One file operation at a time. Both ends open a system picker, and a second tap while one is
     * open would either stack two pickers or import the same file twice — harmless to the ledger,
     * which dedupes, but it reports two results for one action the reader took once.
     */
    private val busy = MutableStateFlow(false)

    private val lastMerge = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LedgerBackupUiState> = combine(
        observeHolding(),
        busy,
        restoring.step,
        lastMerge,
    ) { holding, working, step, merged -> state(holding, working, step.preview(), step.refusal()).copy(merged = merged) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = state(holding = null, working = false, preview = null, refused = null),
        )

    /** FR-22 out. Nothing is said when the picker is cancelled — backing out is not a failure. */
    fun export() = whileFree {
        val export = buildExport()
        val landedAt = files.write(export.fileName, export.contents)
        landedAt?.let { toasts.show(ToastMessage(AppText.copy.settings.savedTo(it))) }
    }

    /**
     * FR-22 in, first half (#220): read the file and say what it holds. Nothing is merged yet, and
     * a file this build cannot place is refused here, before the reader is asked anything.
     * Backing out of the picker keeps a refusal on screen, since nothing new was chosen.
     */
    fun chooseFile() = whileFree {
        val text = files.read() ?: return@whileFree
        lastMerge.value = null
        restoring.read(text)
    }

    /**
     * FR-22 in. A merge, never a replace: the rows already here are not what was asked about.
     * #46: a file an empty ledger takes its home from rebuilds the shell, which comes back here.
     */
    fun restore() = whileFree {
        // #73: a file the archive refuses gets the refused state, not a toast; it wrote nothing.
        val merged = restoring.import { merge -> Home.fromSettings { merge() } } ?: return@whileFree
        val line = merged.phrase()
        lastMerge.value = line
        toasts.show(ToastMessage(line))
    }

    /** Cancel on the preview: the file is let go and nothing has changed. */
    fun cancelRestore() = restoring.cancel()

    private fun whileFree(work: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Copy only: RestoreDriver keeps a refused file on screen, and any other message is the platform's English.
                toasts.show(ToastMessage(text = AppText.copy.settings.didNotWork, tone = ToastTone.Error))
            } finally {
                busy.value = false
            }
        }
    }

    private fun state(holding: LedgerHolding?, working: Boolean, preview: String?, refused: String?) = LedgerBackupUiState(
        holding = holding.phrase(),
        fileName = ledgerFileName(homeToday()),
        busy = working,
        exportable = holding != null && holding.transactions > 0,
        preview = preview,
        refused = refused,
    )

    /**
     * "Not counted yet" rather than a zero while the query is out. A reader told the file will hold
     * nothing does not tap export, and the ledger they are looking at is not empty.
     */
    private fun LedgerHolding?.phrase(): String {
        val oldest = this?.oldest
        return when {
            this == null -> AppText.copy.settings.counting
            oldest == null -> AppText.copy.settings.nothingToExport
            else -> AppText.copy.settings.holding(transactions, oldest.dayAndMonth())
        }
    }

    private fun RestoreStep.preview(): String? = (this as? RestoreStep.Preview)?.holds?.phrase()

    private fun RestoreStep.refusal(): String? = (this as? RestoreStep.Refused)?.reason?.phrase()
}

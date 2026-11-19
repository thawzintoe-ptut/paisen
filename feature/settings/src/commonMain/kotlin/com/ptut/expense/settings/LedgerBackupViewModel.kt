package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.RestoreDriver
import com.ptut.expense.domain.backup.RestoreStep
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
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
import kotlin.time.Clock

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
)

/**
 * FR-22, both directions, behind the two Settings rows that name them.
 *
 * One class for both screens because they are one seam read twice: the same archive, the same
 * picker, the same count and the same guard against a second tap. Each screen still resolves its
 * own instance, which is fine — they are separate destinations, so only one is ever on top.
 */
class LedgerBackupViewModel(
    ledger: LedgerRepository,
    private val archive: LedgerArchive,
    private val files: LedgerFileStore,
    private val toasts: ToastMessages,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /**
     * One file operation at a time. Both ends open a system picker, and a second tap while one is
     * open would either stack two pickers or import the same file twice — harmless to the ledger,
     * which dedupes, but it reports two results for one action the reader took once.
     */
    private val busy = MutableStateFlow(false)

    private val restoring = RestoreDriver(archive)

    val uiState: StateFlow<LedgerBackupUiState> = combine(
        ledger.allTransactions,
        busy,
        restoring.step,
    ) { snapshot, working, step -> state(snapshot, working, step.preview(), step.refusal()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = state(LedgerSnapshot.Unanswered, working = false, preview = null, refused = null),
        )

    /** FR-22 out. Nothing is said when the picker is cancelled — backing out is not a failure. */
    fun export() = whileFree {
        val landedAt = files.write(fileName(), archive.export())
        landedAt?.let { toasts.show(ToastMessage(AppText.copy.settings.savedTo(it))) }
    }

    /**
     * FR-22 in, first half (#220): read the file and say what it holds. Nothing is merged yet, and
     * a file this build cannot place is refused here, before the reader is asked anything.
     * Backing out of the picker keeps a refusal on screen, since nothing new was chosen.
     */
    fun chooseFile() = whileFree {
        val text = files.read() ?: return@whileFree
        restoring.read(text)
    }

    /**
     * FR-22 in. A merge, never a replace: the rows already here are not what was asked about.
     * #46: a file an empty ledger takes its home from rebuilds the shell, which comes back here.
     */
    fun restore() = whileFree {
        // #73: a file the archive refuses gets the refused state, not a toast; it wrote nothing.
        val merged = restoring.import { merge -> Home.fromSettings { merge() } } ?: return@whileFree
        toasts.show(ToastMessage(merged.phrase()))
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

    /** The home date, so two exports on the same evening in two zones do not disagree. */
    private fun fileName(): String {
        val today = clock.now().dateIn(HomeTime)
        return "ptut-ledger-$today.json"
    }

    private fun state(snapshot: LedgerSnapshot, working: Boolean, preview: String?, refused: String?) = LedgerBackupUiState(
        holding = snapshot.holding(),
        fileName = fileName(),
        busy = working,
        exportable = snapshot is LedgerSnapshot.Rows && snapshot.transactions.isNotEmpty(),
        preview = preview,
        refused = refused,
    )

    /**
     * "Not counted yet" rather than a zero while the query is out. A reader told the file will hold
     * nothing does not tap export, and the ledger they are looking at is not empty.
     */
    private fun LedgerSnapshot.holding(): String = when (this) {
        LedgerSnapshot.Unanswered -> AppText.copy.settings.counting

        is LedgerSnapshot.Rows -> when {
            transactions.isEmpty() -> AppText.copy.settings.nothingToExport

            else -> {
                val oldest = transactions.minOf { it.occurredAtEpochMillis }
                    .dateIn(HomeTime)
                    .dayAndMonth()
                AppText.copy.settings.holding(transactions.size, oldest)
            }
        }
    }

    private fun RestoreStep.preview(): String? = (this as? RestoreStep.Preview)?.holds?.phrase()

    private fun RestoreStep.refusal(): String? = (this as? RestoreStep.Refused)?.reason?.phrase()
}

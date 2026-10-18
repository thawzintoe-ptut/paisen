package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.phrase
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
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
)

/** A file read and counted, held until the reader says Import or Cancel (#220). */
private data class ChosenFile(val text: String, val holds: String)

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

    private val chosen = MutableStateFlow<ChosenFile?>(null)

    val uiState: StateFlow<LedgerBackupUiState> = combine(
        ledger.allTransactions,
        busy,
        chosen,
    ) { snapshot, working, file -> state(snapshot, working, file?.holds) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = state(LedgerSnapshot.Unanswered, working = false, preview = null),
        )

    /** FR-22 out. Nothing is said when the picker is cancelled — backing out is not a failure. */
    fun export() = whileFree {
        val landedAt = files.write(fileName(), archive.export())
        landedAt?.let { toasts.show(ToastMessage(AppText.copy.settings.savedTo(it))) }
    }

    /**
     * FR-22 in, first half (#220): read the file and say what it holds. Nothing is merged yet, and
     * a file this build cannot place is refused here, before the reader is asked anything.
     */
    fun chooseFile() = whileFree {
        val text = files.read() ?: return@whileFree
        chosen.value = ChosenFile(text = text, holds = archive.preview(text).phrase())
    }

    /** FR-22 in. A merge, never a replace: the rows already here are not what was asked about. */
    fun restore() = whileFree {
        val file = chosen.value ?: return@whileFree
        chosen.value = null
        toasts.show(ToastMessage(archive.restore(file.text).phrase()))
    }

    /** Cancel on the preview: the file is let go and nothing has changed. */
    fun cancelRestore() {
        chosen.value = null
    }

    private fun whileFree(work: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                work()
            } catch (failure: Exception) {
                // The message is written for the reader — the file format and the unwired seam
                // both say what is wrong in words. A bare class name would not.
                toasts.show(
                    ToastMessage(
                        text = failure.message ?: AppText.copy.settings.didNotWork,
                        tone = ToastTone.Error,
                    ),
                )
            } finally {
                busy.value = false
            }
        }
    }

    /** The Bangkok date, so two exports on the same evening in two zones do not disagree. */
    private fun fileName(): String {
        val today = clock.now().toLocalDateTime(BangkokTime).date
        return "ptut-ledger-$today.json"
    }

    private fun state(snapshot: LedgerSnapshot, working: Boolean, preview: String?) = LedgerBackupUiState(
        holding = snapshot.holding(),
        fileName = fileName(),
        busy = working,
        exportable = snapshot is LedgerSnapshot.Rows && snapshot.transactions.isNotEmpty(),
        preview = preview,
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
                    .dateIn(BangkokTime)
                    .dayAndMonth()
                AppText.copy.settings.holding(transactions.size, oldest)
            }
        }
    }
}

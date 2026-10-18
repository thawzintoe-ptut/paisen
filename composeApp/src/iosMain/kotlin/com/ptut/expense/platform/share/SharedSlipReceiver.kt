package com.ptut.expense.platform.share

import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.importer.pictureAddedBy
import com.ptut.expense.importer.tellWhereSharedSlipWent
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.photos.SharedSlipInbox
import com.ptut.expense.platform.work.SlipImportSchedule
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSURL

/**
 * FR-1 and US-4 on iOS: a slip handed over by the share sheet.
 *
 * It copies the picture into [inbox] and then asks for a run, which is the whole of it — the slip
 * goes through the same hashing, dedupe, parsing and classification as one the album scan found,
 * so a slip shared after it was already imported lands as "already seen" without a rule of its own.
 *
 * Its own scope rather than a screen's, for the same reason the repositories have one: the copy has
 * to finish even if the reader shares the slip and immediately leaves.
 *
 * #219: it says so when the share works too, and where the slip went once [importer] has read it.
 */
class SharedSlipReceiver(
    private val inbox: SharedSlipInbox,
    private val imports: SlipImportSchedule,
    private val importer: SlipImporter,
    private val toasts: ToastMessages,
    private val scope: CoroutineScope,
) {

    /** One copy at a time, so each share can tell its own picture from another share's. */
    private val copying = Mutex()

    /** Ignores anything that is not a file, because every URL this app is opened with lands here. */
    fun accept(picture: NSURL) {
        if (!picture.isFileURL()) return

        scope.launch {
            val added = try {
                copying.withLock { inbox.pictureAddedBy { inbox.accept(picture) } }
            } catch (failure: Exception) {
                // The share sheet has closed by now, so a toast is the only place left to say it.
                toasts.show(
                    ToastMessage(
                        text = failure.message ?: AppText.copy.slips.shareFailed,
                        tone = ToastTone.Error,
                    ),
                )
                return@launch
            }
            importer.tellWhereSharedSlipWent(added, toasts, scan = imports::scanNow)
        }
    }
}

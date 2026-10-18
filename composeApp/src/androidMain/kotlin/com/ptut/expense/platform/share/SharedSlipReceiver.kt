package com.ptut.expense.platform.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.importer.pictureAddedBy
import com.ptut.expense.importer.tellWhereSharedSlipWent
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.photos.SharedSlipInbox
import com.ptut.expense.platform.work.scanForSlipsNow
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val ANY_IMAGE = "image/"

/**
 * FR-1 and US-4: a slip handed over by the share sheet.
 *
 * It copies the picture into [inbox] and then asks for a run, which is the whole of it — the slip
 * goes through the same hashing, dedupe, parsing and classification as one the folder scan found,
 * so a slip shared after it was already imported lands as "already seen" without a rule of its own.
 *
 * Its own scope rather than an Activity's, for the same reason the repositories have one: the copy
 * has to finish even if the reader shares the slip and immediately leaves.
 *
 * #219: it says so when the share works too, and where the slip went once [importer] has read it.
 */
class SharedSlipReceiver(
    context: Context,
    private val inbox: SharedSlipInbox,
    private val importer: SlipImporter,
    private val toasts: ToastMessages,
    private val scope: CoroutineScope,
) {

    private val context = context.applicationContext

    /** One copy at a time, so each share can tell its own picture from another share's. */
    private val copying = Mutex()

    /** Ignores anything that is not a shared image, because every launch intent comes through here. */
    fun accept(intent: Intent) {
        val slip = intent.sharedImage() ?: return
        scope.launch {
            val added = try {
                copying.withLock { inbox.pictureAddedBy { inbox.accept(slip) } }
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
            importer.tellWhereSharedSlipWent(added, toasts, scan = { context.scanForSlipsNow() })
        }
    }
}

private fun Intent.sharedImage(): Uri? = when {
    action != Intent.ACTION_SEND -> null
    type?.startsWith(ANY_IMAGE) != true -> null
    else -> attachment()
}

@Suppress("DEPRECATION")
private fun Intent.attachment(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
